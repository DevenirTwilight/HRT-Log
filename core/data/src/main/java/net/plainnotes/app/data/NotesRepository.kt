package net.plainnotes.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.domain.*
import org.json.JSONObject
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

/** Built-in well-being items, in display order. */
val CHECKIN_DEFAULTS=DAILY_KEYS
const val BACKFILL_MAX_DAYS=731L

@Singleton class NotesRepository(private val access: DatabaseAccess, private val pinned: Space?) {
    @Inject constructor(access: DatabaseAccess) : this(access, null)
    /** A repository that always works on [space], whatever the UI has selected (used by background reminders). */
    fun pinnedTo(space: Space) = NotesRepository(access, space)
    val space get() = pinned ?: access.current
    fun select(space: Space) = access.select(space)
    private fun db() = access.get(space)
    suspend fun <T> transaction(block: suspend (NotesDao) -> T): T = withContext(Dispatchers.IO) {
        val db=db();db.withTransaction { block(db.dao()) }
    }
    suspend fun medications()=withContext(Dispatchers.IO){db().dao().medications()}
    suspend fun saveMilestone(value:MilestoneEntity)=transaction{dao->
        LocalDate.parse(value.date);require(value.kind in MILESTONE_KINDS)
        require(value.kind!="CUSTOM" || !value.title.isNullOrBlank())
        val normalized=value.copy(title=value.title?.trim()?.takeIf{it.isNotEmpty()},note=value.note?.trim()?.takeIf{it.isNotEmpty()})
        val inserted=dao.milestone(normalized)
        normalized.copy(id=if(value.id==0L)inserted else value.id)
    }
    suspend fun deleteMilestone(id:Long)=transaction{it.deleteMilestone(id)}
    suspend fun appointments()=withContext(Dispatchers.IO){db().dao().appointments()}
    suspend fun profile(id:Long)=withContext(Dispatchers.IO){db().dao().profile(id)}
    suspend fun rules()=withContext(Dispatchers.IO){db().dao().rules()}
    private suspend fun ruleModels(dao:NotesDao)=dao.rules().map { r ->
        ScheduleRule(r.id,r.medication_id,RuleKind.valueOf(r.kind),r.interval,r.anchor_local?.let(LocalDate::parse),r.anchor_utc?.let(Instant::ofEpochMilli),ZoneId.of(r.anchor_zone),
            Instant.ofEpochMilli(r.effective_from_utc),r.effective_until_utc?.let(Instant::ofEpochMilli),Instant.ofEpochMilli(r.missed_tracking_from_utc),
            dao.times(r.id).map{RuleTime(LocalTime.parse(it.local_time),it.dose_override)},DayOfWeek.entries.filter{r.weekday_mask and (1 shl (it.value-1))!=0}.toSet(),r.dose_snapshot,r.soon_snapshot,r.late_snapshot)
    }
    private fun OverrideEntity.model()=SlotOverride(slot_key,rescheduled_utc?.let(Instant::ofEpochMilli),rescheduled_zone?.let(ZoneId::of),dose_override,skipped)
    private fun RecordEntity.model()=DoseRecord(slot_key,medication_id,if(unconfirmed) DoseStatus.UNCONFIRMED else DoseStatus.valueOf(status),taken_utc?.let(Instant::ofEpochMilli),taken_zone?.let(ZoneId::of),actual_dose,site,origin.startsWith("IMPORT_"),deleted_at_utc!=null,scheduled_utc?.let(Instant::ofEpochMilli),scheduled_zone?.let(ZoneId::of),planned_dose,late_after_minutes_snapshot,rule_version_id)
    private fun RetainedEntity.model()=Slot(slot_key,rule_id,medication_id,Instant.ofEpochMilli(original_utc),Instant.ofEpochMilli(at_utc),ZoneId.of(zone),dose,soon_minutes,late_minutes,Instant.ofEpochMilli(tracking_from_utc))
    private suspend fun timeline(dao:NotesDao,now:Instant,from:Instant,to:Instant,zone:ZoneId):List<TimelineEntry> {
        val meds=dao.medications().associateBy{it.id}
        val rules=ruleModels(dao).filter { meds[it.medicationId]?.needs_review==null }
        return Timeline.build(rules,dao.overrides().map{it.model()},dao.records().map{it.model()},from,to,now,zone,dao.retained().map{it.model()})
    }
    private suspend fun reconcile(dao:NotesDao,now:Instant,zone:ZoneId) {
        val from=dao.rules().minOfOrNull{it.missed_tracking_from_utc}?.let(Instant::ofEpochMilli) ?: now
        if(from>now)return
        timeline(dao,now,from,now.plusMillis(1),zone).filter{it.state==SlotState.UNCONFIRMED}.forEach { entry ->
            if(dao.records().none{it.slot_key==entry.slot.key}) dao.record(recordFor(dao,entry.slot,"MISSED",origin="AUTO_MISSED"))
        }
    }
    suspend fun calendar(now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault(),displayFrom:LocalDate=LocalDate.now(zone)):List<TimelineEntry> = transaction { dao ->
        reconcile(dao,now,zone)
        val from=dao.rules().minOfOrNull{it.missed_tracking_from_utc}?.let(Instant::ofEpochMilli) ?: now
        val window=timeline(dao,now,displayFrom.atStartOfDay(zone).toInstant(),displayFrom.plusDays(14).atStartOfDay(zone).toInstant(),zone)
        val unfinished=timeline(dao,now,minOf(from,now),now.plusMillis(1),zone)
            .filter{it.state in listOf(SlotState.PENDING,SlotState.SOON,SlotState.OVERDUE)}
        (window+unfinished).distinctBy{it.slot.key}.sortedWith(compareBy<TimelineEntry>{it.slot.at}.thenBy{it.slot.key})
    }
    /** Pending delays made before first unlock are reconciled without domain data in DPS. */
    suspend fun reconcileCache(cache:AlarmCache)=transaction { dao ->
        cache.alarms.forEach { alarm ->
            val source=dao.mappings().firstOrNull{it.opaque_id==alarm.opaqueId && it.generation==cache.generation} ?: return@forEach
            if(alarm.consumed)dao.markSent(source.opaque_id)
            else if(alarm.triggerMillis!=source.trigger_utc) {
                dao.markSent(source.opaque_id)
                val identity="snooze:${source.opaque_id}"
                if(dao.mappings().none{it.identity==identity})dao.mapping(ReminderMappingEntity(java.util.UUID.randomUUID().toString(),cache.generation,identity,alarm.triggerMillis,false))
            }
        }
    }
    /** Resolve only in credential storage; a stale notification never follows a changed schedule. */
    suspend fun reminderSlot(id:String,generation:String?=null):Slot? {
        val mappings=transaction{it.mappings()}
        var source=mappings.firstOrNull{it.opaque_id==id && (generation==null || it.generation==generation)} ?: return null
        val visited=mutableSetOf<String>()
        while(source.identity.startsWith("snooze:")) {
            if(!visited.add(source.opaque_id))return null
            source=mappings.firstOrNull{it.opaque_id==source.identity.removePrefix("snooze:")} ?: return null
        }
        val type=source.identity.substringAfterLast(':')
        if(type !in listOf("SOON","DUE","LATE"))return null
        val key=source.identity.substringBeforeLast(':')
        val entry=calendar().firstOrNull{it.slot.key==key} ?: return null
        if(entry.state in listOf(SlotState.SKIPPED,SlotState.ON_TIME,SlotState.LATE))return null
        val trigger=when(type){"SOON"->entry.slot.at.minusSeconds(entry.slot.soonMinutes.toLong()*60);"LATE"->entry.slot.at.plusSeconds(entry.slot.lateMinutes.toLong()*60+1);else->entry.slot.at}
        return entry.slot.takeIf{trigger.toEpochMilli()==source.trigger_utc}
    }
    suspend fun saveMedication(value:MedicationEntity,ester:String?,kind:RuleKind,interval:Int,times:List<LocalTime>,weekdays:Set<DayOfWeek>,now:Instant=Instant.now(),pk:ProfileEntity?=null,resizeContainers:Boolean=false,timeDoses:List<Double?>?=null):Long=transaction { dao ->
        RegimenHistory.seed(db().openHelper.writableDatabase)
        val effectiveNow=Instant.ofEpochMilli(now.toEpochMilli())
        require(value.name.isNotBlank() && value.dose_per_intake.isFinite() && value.dose_per_intake>0)
        val zone=ZoneId.systemDefault();reconcile(dao,effectiveNow,zone)
        val id=if(value.id==0L)dao.insertMedication(value) else {dao.updateMedication(value);value.id}
        if(resizeContainers) resizableContainers(dao.containers(),id,value.container_capacity).forEach{dao.setContainerCapacity(it.id,value.container_capacity)}
        val cut=effectiveNow.toEpochMilli()
        val newProfile=profileFor(id,value,ester,pk)
        val snapshot=MedicationSnapshot.encode(value,newProfile)
        // Editing only metadata (name, stock, notifications, profile) keeps the plan: no new version, so no slot can appear twice.
        val current=dao.rules().filter { it.medication_id==id && it.effective_until_utc==null }.singleOrNull()
        val oldTimes=current?.let{dao.times(it.id)}.orEmpty()
        require(timeDoses==null || timeDoses.size==times.size)
        require(timeDoses.orEmpty().all{it==null || it.isFinite() && it>0})
        fun clock(t:LocalTime)=t.withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        if(kind!=RuleKind.EVERY_N_HOURS && timeDoses==null && oldTimes.any{it.dose_override!=null})
            require(times.map(::clock).toSet()==oldTimes.map{it.local_time}.toSet()){"Dose correspondence is required for a non-uniform plan edit"}
        val wantedDoses=timeDoses ?: times.map{t->oldTimes.firstOrNull{it.local_time==clock(t)}?.dose_override}
        require(times.distinct().size==times.size)
        val desiredTimes=times.mapIndexed{i,t->TimeEntity(rule_id=0,local_time=clock(t),dose_override=wantedDoses[i])}
        if(value.active && current!=null && samePlan(dao,current,value,kind,interval,times,weekdays,zone,snapshot,wantedDoses)) {
            if(current.config_snapshot!=snapshot) dao.updateRule(current.copy(config_snapshot=snapshot))
            saveProfile(dao,id,value,ester,pk)
            RegimenHistory.changed(dao,id,cut)
            return@transaction id
        }
        val cadenceUnchanged=current?.let{r->
            if(r.kind!=kind.name || r.interval!=interval || r.effective_zone!=zone.id) false else {
                val proposed=r.copy(weekday_mask=weekdays.sumOf{1 shl (it.value-1)},dose_snapshot=value.dose_per_intake,config_snapshot=snapshot)
                val newTimes=if(kind==RuleKind.EVERY_N_HOURS)emptyList() else desiredTimes.map{it.copy(rule_id=r.id)}
                RegimenDefinition.from(r,dao.times(r.id)).signature()==RegimenDefinition.from(proposed,newTimes).signature()
            }
        } ?: false
        // Preserve pending/overridden slots born before the cutover without persisting future PENDING rows.
        val old=dao.rules().filter { it.medication_id==id && it.effective_until_utc==null }
        if(old.isNotEmpty()) {
            val earliest=old.minOf{it.effective_from_utc}
            val keep=timeline(dao,effectiveNow,Instant.ofEpochMilli(earliest),effectiveNow.plusSeconds(86400*14),zone)
                .filter{it.slot.medicationId==id && it.slot.original<effectiveNow && it.state in listOf(SlotState.PENDING,SlotState.SOON,SlotState.OVERDUE)}
            keep.forEach{dao.retain(it.slot.retained())}
            val models=ruleModels(dao).filter{r->old.any{it.id==r.id}}
            dao.overrides().filter{it.medication_id==id}.forEach { change ->
                models.firstNotNullOfOrNull{ScheduleEngine.resolve(it,change.slot_key,zone)}?.takeIf{it.original<effectiveNow}?.let{dao.retain(it.retained())}
            }
            old.forEach{require(cut>it.effective_from_utc);dao.updateRule(it.copy(effective_until_utc=cut))}
        }
        saveProfile(dao,id,value,ester,pk)
        if(value.active) {
            val soon=requireNotNull(value.soon_alert_minutes);val late=requireNotNull(value.late_after_minutes)
            val anchorDate=if(cadenceUnchanged)current!!.anchor_local else if(kind==RuleKind.EVERY_N_HOURS)null else effectiveNow.atZone(zone).toLocalDate().toString()
            val mask=weekdays.sumOf{1 shl (it.value-1)}
            val r=RuleEntity(medication_id=id,kind=kind.name,interval=interval,weekday_mask=mask,anchor_local=anchorDate,anchor_zone=zone.id,
                anchor_utc=if(cadenceUnchanged)current!!.anchor_utc else if(kind==RuleKind.EVERY_N_HOURS)cut else null,effective_from_utc=cut,effective_until_utc=null,effective_zone=zone.id,
                missed_tracking_from_utc=cut,dose_snapshot=value.dose_per_intake,soon_snapshot=soon,late_snapshot=late,
                config_snapshot=snapshot)
            require(interval in 1..36500); require(kind!=RuleKind.WEEKLY || mask>0)
            require(kind==RuleKind.EVERY_N_HOURS || times.isNotEmpty())
            val rid=dao.rule(r)
            if(kind!=RuleKind.EVERY_N_HOURS)desiredTimes.forEach{dao.time(it.copy(rule_id=rid))}
        }
        RegimenHistory.changed(dao,id,cut);id
    }
    private fun profileFor(id:Long,value:MedicationEntity,ester:String?,pk:ProfileEntity?):ProfileEntity? =
        if(value.molecule=="E2" && value.route!=null && ester!=null)
            ProfileEntity(id,ester,when(value.route){"ORAL"->"oral";"SUBLINGUAL"->"sublingual";"GEL"->"gel";"PATCH"->"patchApply";"INJECTION"->"injection";else->error("Unsupported estradiol route")},
                pk?.sl_tier,pk?.gel_product_id,pk?.gel_site,pk?.gel_area_cm2,pk?.patch_release_ug_day)
        else null
    private suspend fun saveProfile(dao:NotesDao,id:Long,value:MedicationEntity,ester:String?,pk:ProfileEntity?) {
        profileFor(id,value,ester,pk)?.let{dao.profile(it)}
    }
    /** True when the edit leaves the plan as it is: same kind, interval, weekdays, times, dose, alert windows, zone and compound (the name may differ). */
    private suspend fun samePlan(dao:NotesDao,r:RuleEntity,value:MedicationEntity,kind:RuleKind,interval:Int,times:List<LocalTime>,weekdays:Set<DayOfWeek>,zone:ZoneId,snapshot:String,timeDoses:List<Double?>):Boolean {
        val mask=weekdays.sumOf{1 shl (it.value-1)}
        val ruleTimes=dao.times(r.id)
        val wanted=if(kind==RuleKind.EVERY_N_HOURS) emptySet() else times.map{it.withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))}.toSet()
        fun compound(json:String)=JSONObject(json).let{o->listOf("molecule","route","unit","ester").map{k->if(o.isNull(k))null else o.opt(k)?.toString()}}
        return r.kind==kind.name && r.interval==interval && r.weekday_mask==mask && r.effective_zone==zone.id &&
            r.dose_snapshot==value.dose_per_intake && r.soon_snapshot==value.soon_alert_minutes && r.late_snapshot==value.late_after_minutes &&
            (kind==RuleKind.EVERY_N_HOURS || ruleTimes.associate{it.local_time to it.dose_override}==times.mapIndexed{i,t->t.withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) to timeDoses[i]}.toMap()) && ruleTimes.map{it.local_time}.toSet()==wanted && ruleTimes.size==wanted.size &&
            compound(r.config_snapshot)==compound(snapshot) &&
            MedicationSnapshot.decode(r.config_snapshot,r.medication_id)?.profile==MedicationSnapshot.decode(snapshot,r.medication_id)?.profile
    }
    /** Explicit user confirmation, restricted to missing and compatible saved fields. No inventory mutation. */
    suspend fun confirmHistoricalContext(medication:MedicationEntity,profile:ProfileEntity?,from:LocalDate,to:LocalDate,now:Instant=Instant.now()):Int=transaction { dao ->
        require(!to.isBefore(from));val candidate=MedicationSnapshot.encode(medication,profile);val zone=ZoneId.systemDefault()
        val rows=dao.records().filter{it.medication_id==medication.id && it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc?.let{t->Instant.ofEpochMilli(t).atZone(zone).toLocalDate() in from..to}==true && HistoricalContext.incomplete(it)}
        val snapshots=dao.rules().associate{it.id to it.config_snapshot}
        require(rows.isNotEmpty());val contexts=rows.map{r->r.id to HistoricalContext.confirmed(r.config_snapshot,candidate,"USER_CONFIRMED",now,HistoricalContext.resolved(r,snapshots))}
        contexts.forEach{(id,json)->dao.recordContext(id,json)};contexts.size
    }
    suspend fun removeMedication(id:Long,now:Instant=Instant.now())=transaction { dao ->
        RegimenHistory.seed(db().openHelper.writableDatabase)
        reconcile(dao,now,ZoneId.systemDefault())
        val m=dao.medication(id);dao.updateMedication(m.copy(active=false,notifications_on=false))
        dao.rules().filter{it.medication_id==id && it.effective_until_utc==null}.forEach{dao.updateRule(it.copy(effective_until_utc=maxOf(now.toEpochMilli(),it.effective_from_utc+1)))}
        RegimenHistory.changed(dao,id,dao.rules().filter{it.medication_id==id}.maxOfOrNull{it.effective_until_utc ?: now.toEpochMilli()} ?: now.toEpochMilli())
    }
    private fun Slot.retained()=RetainedEntity(key,ruleId,medicationId,original.toEpochMilli(),original.toEpochMilli(),originalZone.id,originalDose,soonMinutes,lateMinutes,trackingFrom.toEpochMilli())
    private suspend fun recordFor(dao:NotesDao,slot:Slot,status:String,taken:Instant?=null,dose:Double?=null,origin:String="APP"):RecordEntity {
        val m=dao.medication(slot.medicationId)
        val snapshot=dao.rules().single { it.id==slot.ruleId }.config_snapshot
        return RecordEntity(medication_id=m.id,rule_version_id=slot.ruleId,slot_key=slot.key,scheduled_utc=slot.at.toEpochMilli(),scheduled_zone=slot.zone.id,
            planned_dose=slot.dose,late_after_minutes_snapshot=slot.lateMinutes,taken_utc=taken?.toEpochMilli(),taken_zone=if(taken==null)null else ZoneId.systemDefault().id,
            actual_dose=dose,status=status,origin=origin,revision=1,config_snapshot=snapshot)
    }
    /** No inferred matching: the user chooses an unfinished same-medication slot on the intake's local day. */
    suspend fun importedCandidates(id:Long,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):List<TimelineEntry> = transaction { dao ->
        val record=dao.recordById(id) ?: error("Record missing")
        require(record.deleted_at_utc==null && record.origin.startsWith("IMPORT_") && record.slot_key==null && record.scheduled_utc==null && record.taken_utc!=null)
        val day=Instant.ofEpochMilli(record.taken_utc).atZone(zone).toLocalDate()
        reconcile(dao,now,zone)
        timeline(dao,now,day.atStartOfDay(zone).toInstant(),day.plusDays(1).atStartOfDay(zone).toInstant(),zone)
            .filter{it.slot.medicationId==record.medication_id && it.state !in listOf(SlotState.ON_TIME,SlotState.LATE,SlotState.SKIPPED)}
    }
    /** Attach schedule metadata only. Imported intake data and the stock ledger remain untouched. */
    suspend fun linkImported(id:Long,key:String,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transaction { dao ->
        val record=dao.recordById(id) ?: error("Record missing")
        require(record.deleted_at_utc==null && record.origin.startsWith("IMPORT_") && record.taken_utc!=null)
        if(record.slot_key==key)return@transaction
        val slot=importedCandidates(id,now,zone).single{it.slot.key==key}.slot
        val existing=dao.records().singleOrNull{it.slot_key==key}
        require(existing==null || existing.status=="MISSED")
        if(existing!=null)dao.updateRecord(existing.copy(deleted_at_utc=now.toEpochMilli(),revision=existing.revision+1))
        val taken=Instant.ofEpochMilli(record.taken_utc)
        val status=if(taken>slot.at.plusSeconds(slot.lateMinutes.toLong()*60)) "LATE" else "ON_TIME"
        dao.updateRecord(record.copy(rule_version_id=slot.ruleId,slot_key=key,scheduled_utc=slot.at.toEpochMilli(),scheduled_zone=slot.zone.id,
            planned_dose=slot.dose,late_after_minutes_snapshot=slot.lateMinutes,status=status,revision=record.revision+1))
    }
    suspend fun complete(slot:Slot,taken:Instant,dose:Double,site:String?=null)=transaction { dao ->
        val existing=dao.records().singleOrNull{it.slot_key==slot.key}
        if(existing?.status in listOf("ON_TIME","LATE"))return@transaction
        require(existing?.status!="SKIPPED");require(dose.isFinite()&&dose>0)
        val status=ScheduleEngine.complete(slot,taken,ZoneId.systemDefault(),dose).status.name
        val result=recordFor(dao,slot,status,taken,dose).copy(unallocated_supply_amount=dose,site=site?.takeIf{it.isNotBlank()})
        val id=if(existing==null)dao.record(result) else {dao.updateRecord(result.copy(id=existing.id,revision=existing.revision+1,config_snapshot=existing.config_snapshot));existing.id}
        SupplyLedger.allocate(dao,dao.recordById(id)!!)
    }
    /**
     * Batch backfill ("batch add" in HRT tracker): one intake per day in [from]..[to] at each of [times], past instants only.
     * An open scheduled slot at exactly that instant is completed; otherwise an unscheduled intake is added, unless the
     * medication already has an intake within an hour of it. History only: no stock is deducted. Returns the count added.
     */
    suspend fun backfill(medicationId:Long,from:LocalDate,to:LocalDate,times:List<LocalTime>,dose:Double,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transaction { dao ->
        require(dose.isFinite()&&dose>0&&!to.isBefore(from)&&times.isNotEmpty()&&java.time.temporal.ChronoUnit.DAYS.between(from,to)<=BACKFILL_MAX_DAYS)
        reconcile(dao,now,zone)
        val m=dao.medication(medicationId)
        val instants=generateSequence(from){it.plusDays(1)}.takeWhile{!it.isAfter(to)}
            .flatMap{d->times.distinct().sorted().map{ScheduleEngine.wallInstant(d.atTime(it),zone)}}.filter{it.isBefore(now)}.toList()
        if(instants.isEmpty())return@transaction 0
        val slots=timeline(dao,now,instants.first(),now.plusMillis(1),zone).filter{it.slot.medicationId==medicationId}.associateBy{it.slot.at}
        val records=dao.records().filter{it.medication_id==medicationId&&it.deleted_at_utc==null}.toMutableList()
        val taken=records.mapNotNull{it.taken_utc}.toMutableList()
        val snap=MedicationSnapshot.encode(m,dao.profile(medicationId))
        var added=0
        instants.forEach { t ->
            val slot=slots[t]?.slot
            val existing=slot?.let{s->records.singleOrNull{it.slot_key==s.key}}
            if(slot!=null&&existing?.status!in listOf("ON_TIME","LATE","SKIPPED")) {
                val status=ScheduleEngine.complete(slot,t,zone,dose).status.name
                val r=recordFor(dao,slot,status,t,dose)
                if(existing==null)dao.record(r) else dao.updateRecord(r.copy(id=existing.id,revision=existing.revision+1,config_snapshot=existing.config_snapshot))
            } else if(slot==null&&taken.none{kotlin.math.abs(it-t.toEpochMilli())<3_600_000}) {
                dao.record(RecordEntity(medication_id=medicationId,taken_utc=t.toEpochMilli(),taken_zone=zone.id,actual_dose=dose,status="ON_TIME",origin="APP",revision=1,config_snapshot=snap))
            } else return@forEach
            taken+=t.toEpochMilli(); added++
        }
        added
    }
    suspend fun unscheduled(id:Long,taken:Instant,dose:Double,site:String?=null)=transaction { dao ->
        require(dose.isFinite()&&dose>0);val m=dao.medication(id)
        val snap=MedicationSnapshot.encode(m,dao.profile(id))
        val rid=dao.record(RecordEntity(medication_id=id,taken_utc=taken.toEpochMilli(),taken_zone=ZoneId.systemDefault().id,actual_dose=dose,unallocated_supply_amount=dose,site=site?.takeIf{it.isNotBlank()},
            status="ON_TIME",origin="APP",revision=1,config_snapshot=snap))
        SupplyLedger.allocate(dao,dao.recordById(rid)!!)
    }
    suspend fun override(slot:Slot,value:SlotOverride,now:Instant=Instant.now())=transaction { dao ->
        require(slot.key==value.key)
        val record=dao.records().singleOrNull{it.slot_key==slot.key}
        require(record?.status !in listOf("ON_TIME","LATE","MISSED")) { "Edit recorded history instead" }
        val previous=dao.overrides().singleOrNull{it.slot_key==slot.key}
        if(value.isDefault)dao.deleteOverride(slot.key) else {
            val next=OverrideEntity(previous?.id ?: 0,slot.medicationId,slot.ruleId,slot.key,value.rescheduled?.toEpochMilli(),value.zone?.id,value.dose,value.skipped)
            if(previous==null)dao.insertOverride(next)else dao.updateOverride(next)
        }
        if(record?.status=="SKIPPED" && !value.skipped)dao.updateRecord(record.copy(deleted_at_utc=now.toEpochMilli(),revision=record.revision+1))
        if(value.skipped && record==null)dao.record(recordFor(dao,value.apply(slot),"SKIPPED"))
        if(value.skipped && record?.status=="SKIPPED")dao.updateRecord(recordFor(dao,value.apply(slot),"SKIPPED").copy(id=record.id,revision=record.revision+1))
        reconcile(dao,now,ZoneId.systemDefault())
    }
    suspend fun currentOverride(key:String)=withContext(Dispatchers.IO){db().dao().overrides().singleOrNull{it.slot_key==key}?.model() ?: SlotOverride(key)}
    suspend fun appointment(value:AppointmentEntity)=saveAppointment(value)
    /** Inserts or edits an appointment; the confirmed-visit time is kept as stored unless changed through [setVisitCompleted]. */
    suspend fun saveAppointment(value:AppointmentEntity)=transaction { dao ->
        ZoneId.of(value.at_zone);require(value.remind_minutes_before>=0)
        val clean=value.copy(location=value.location?.trim()?.takeIf{it.isNotEmpty()},practitioner=value.practitioner?.trim()?.takeIf{it.isNotEmpty()},note=value.note?.trim()?.takeIf{it.isNotEmpty()})
        if(value.id==0L)dao.appointment(clean.copy(completed_utc=null)) else {
            val old=requireNotNull(dao.appointmentById(value.id));dao.updateAppointment(clean.copy(completed_utc=old.completed_utc));value.id
        }
    }
    suspend fun setVisitCompleted(id:Long,completed:Boolean,now:Instant=Instant.now())=transaction { dao ->
        val old=requireNotNull(dao.appointmentById(id));dao.updateAppointment(old.copy(completed_utc=if(completed)now.toEpochMilli() else null))
    }
    /** Also removes the appointment's questions and visit-pack records; files already exported are not touched. */
    suspend fun deleteAppointment(id:Long)=transaction { dao -> dao.deleteVisitQuestions(id);dao.deleteVisitPacks(id);dao.deleteAppointment(id) }
    suspend fun visitQuestions()=withContext(Dispatchers.IO){db().dao().visitQuestions()}
    suspend fun saveVisitQuestion(value:VisitQuestionEntity)=transaction { dao ->
        val text=value.text.trim();require(text.isNotEmpty() && value.status in listOf("OPEN","ASKED"))
        val answer=value.answer_note?.trim()?.takeIf{it.isNotEmpty()}
        if(value.id==0L){requireNotNull(dao.appointmentById(value.appointment_id))
            val next=(dao.visitQuestions().filter{it.appointment_id==value.appointment_id}.maxOfOrNull{it.sort_order} ?: -1)+1
            dao.insertVisitQuestion(value.copy(text=text,answer_note=answer,sort_order=next))
        } else { val old=dao.visitQuestions().single{it.id==value.id};dao.updateVisitQuestion(old.copy(text=text,status=value.status,answer_note=answer));value.id }
    }
    suspend fun deleteVisitQuestion(id:Long)=transaction{it.deleteVisitQuestion(id)}
    /** Swaps a question with its neighbour; order is kept dense from 0. */
    suspend fun moveVisitQuestion(id:Long,up:Boolean)=transaction { dao ->
        val all=dao.visitQuestions();val q=all.single{it.id==id}
        val list=all.filter{it.appointment_id==q.appointment_id}.sortedWith(compareBy({it.sort_order},{it.id})).toMutableList()
        val i=list.indexOf(q);val j=if(up)i-1 else i+1
        if(j in list.indices){java.util.Collections.swap(list,i,j)}
        list.forEachIndexed{n,v->if(v.sort_order!=n)dao.updateVisitQuestion(v.copy(sort_order=n))}
    }
    suspend fun visitPacks()=withContext(Dispatchers.IO){db().dao().visitPacks()}
    suspend fun recordVisitPack(value:VisitPackEntity)=transaction { dao ->
        requireNotNull(dao.appointmentById(value.appointment_id));VisitSection.parse(value.sections);ZoneId.of(value.zone)
        require(LocalDate.parse(value.range_from)<=LocalDate.parse(value.range_to));JSONObject(value.facts_json)
        dao.insertVisitPack(value.copy(id=0))
    }
    suspend fun records()=withContext(Dispatchers.IO){db().dao().records()}

    // --- Import / backup / wipe ---
    suspend fun importTransMemo(plan:net.plainnotes.app.importer.TransMemo.Plan,overwrite:Boolean,zone:ZoneId=ZoneId.systemDefault())=withContext(Dispatchers.IO) {
        val db=db();db.withTransaction { TransMemoWriter.write(db.dao(),db.openHelper.writableDatabase,plan,overwrite,zone) }
    }
    suspend fun importHrtTracker(plan:net.plainnotes.app.importer.HrtTracker.Plan,targets:Map<net.plainnotes.app.importer.HrtTracker.Group,Long?>,
                                 names:Map<net.plainnotes.app.importer.HrtTracker.Group,String>,weightKg:Double?,zone:ZoneId=ZoneId.systemDefault())=transaction { dao ->
        HrtTrackerWriter.write(dao,plan,targets,names,weightKg,zone)
    }
    suspend fun exportBackup(password:CharArray):ByteArray=withContext(Dispatchers.IO) {
        val db=db()
        val json=db.withTransaction { RegimenHistory.seed(db.openHelper.writableDatabase);JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",db.openHelper.writableDatabase.version)
            .put("created",Instant.now().toString()).put("tables",RawData.dump(db.openHelper.writableDatabase)) }
        val plain=json.toString().toByteArray(Charsets.UTF_8)
        try{BackupCodec.encrypt(plain,password)}finally{plain.fill(0)}
    }
    /** Replaces all data with the backup; throws [BackupCodec.WrongPassword] or [BackupCodec.BadFile] without touching anything. */
    /**
     * Restores a backup from this schema or an older one (older ones are upgraded right after the rows are written, with the
     * same steps as the Room migration). A backup from a newer schema is refused.
     */
    suspend fun restoreBackup(data:ByteArray,password:CharArray,today:LocalDate=LocalDate.now())=withContext(Dispatchers.IO) {
        val plain=BackupCodec.decrypt(data,password)
        val json=try{String(plain,Charsets.UTF_8).let{BackupLimits.checkJson(it);JSONObject(it)}}finally{plain.fill(0)}
        val db=db(); val current=db.openHelper.writableDatabase.version; val schema=json.optInt("schema")
        if(json.optInt("format")!=BackupCodec.FORMAT_VERSION||schema<1) throw BackupCodec.BadFile("incompatible version")
        if(schema>current) throw BackupCodec.NewerBackup()
        val tables=json.getJSONObject("tables")
        val required=DOMAIN_TABLES.filterNot{(schema<2 && it in listOf("stage_review","symptom_check","review_effect")) || (schema<4 && it in listOf("regimen_version","regimen_rule_link","milestone")) || (schema<5 && it=="lab_context_revision") || (schema<6 && it in listOf("visit_question","visit_pack"))}
        require(required.all{tables.has(it)}) { "Incomplete backup" }
        db.withTransaction {
            val sql=db.openHelper.writableDatabase
            RawData.restore(sql,tables,upgradeRegimens=schema<4)
            if(schema<2) WellbeingUpgrade.apply(sql,today)
        }
    }
    /** Irreversibly deletes the database, its key file and Keystore key. */
    fun destroyAll()=access.destroy(space)
    fun destroy(space: Space)=access.destroy(space)

    // --- History edits (stock is corrected through REVERSE + new CONSUME entries) ---
    /** Changes time and/or amount of a recorded intake, or turns a missed slot into a backfilled intake. */
    suspend fun editRecord(id:Long,taken:Instant,dose:Double,now:Instant=Instant.now())=transaction { dao ->
        require(dose.isFinite()&&dose>0&&!taken.isAfter(now.plusSeconds(60)))
        val r=requireNotNull(dao.recordById(id)).also{require(it.deleted_at_utc==null&&it.status!="SKIPPED")}
        SupplyLedger.reverse(dao,id)
        val late=r.scheduled_utc?.let{s->r.late_after_minutes_snapshot?.let{l->taken.toEpochMilli()>s+l*60_000L}} ?: false
        val next=r.copy(taken_utc=taken.toEpochMilli(),taken_zone=ZoneId.systemDefault().id,actual_dose=dose,status=if(late)"LATE" else "ON_TIME",revision=r.revision+1)
        dao.updateRecord(next);SupplyLedger.allocate(dao,dao.recordById(id)!!)
    }
    /** Soft-deletes a taken intake; a scheduled slot becomes due again and may later be marked missed. */
    suspend fun deleteRecord(id:Long,now:Instant=Instant.now())=transaction { dao ->
        val r=requireNotNull(dao.recordById(id));require(r.status in listOf("ON_TIME","LATE"))
        SupplyLedger.reverse(dao,id)
        dao.updateRecord(r.copy(deleted_at_utc=now.toEpochMilli(),revision=r.revision+1))
    }

    // --- Stock ---
    suspend fun containers()=withContext(Dispatchers.IO){db().dao().containers()}
    /** Adds [count] containers; the first one is opened right away when nothing is in use. */
    suspend fun addContainers(medicationId:Long,capacity:Double,count:Int,openFirst:Boolean,source:String?=null,batch:String?=null)=transaction { dao ->
        require(capacity.isFinite()&&capacity>0&&count in 1..50)
        repeat(count){i-> dao.insertContainer(ContainerEntity(medication_id=medicationId,capacity=capacity,initial_used_amount=0.0,used_amount=0.0,
            opened_on=if(openFirst&&i==0)LocalDate.now().toString() else null,state=if(openFirst&&i==0)"IN_USE" else "SEALED",
            source_note=source?.trim()?.takeIf{it.isNotEmpty()},batch=batch?.trim()?.takeIf{it.isNotEmpty()})) }
    }
    /** Closes the open container(s) of a medication and opens a sealed one (or a new one with [capacity]). */
    suspend fun replaceContainer(medicationId:Long,capacity:Double)=transaction { dao ->
        val all=dao.containers().filter{it.medication_id==medicationId}
        all.filter{it.state=="IN_USE"}.forEach{dao.setContainerState(it.id,if(it.capacity-it.used_amount<=1e-9)"EMPTY" else "DISCARDED",it.opened_on)}
        val sealed=all.filter{it.state=="SEALED"}.minByOrNull{it.id}
        if(sealed!=null)dao.setContainerState(sealed.id,"IN_USE",LocalDate.now().toString())
        else dao.insertContainer(ContainerEntity(medication_id=medicationId,capacity=capacity,initial_used_amount=0.0,used_amount=0.0,opened_on=LocalDate.now().toString(),state="IN_USE"))
    }
    suspend fun setRemaining(containerId:Long,remaining:Double)=transaction { SupplyLedger.setRemaining(it,containerId,remaining) }
    suspend fun discardContainer(containerId:Long)=transaction { dao -> val c=dao.container(containerId);dao.setContainerState(c.id,"DISCARDED",c.opened_on) }

    // --- Well-being ---
    suspend fun checkinItems()=transaction { dao ->
        if(dao.checkinItems().isEmpty()) CHECKIN_DEFAULTS.forEachIndexed{i,k->dao.insertCheckinItem(CheckinItemEntity(builtin_key=k,enabled=true,sort_order=i))}
        dao.checkinItems()
    }
    suspend fun saveCheckinItem(v:CheckinItemEntity)=transaction { dao -> if(v.id==0L)dao.insertCheckinItem(v.copy(sort_order=(dao.checkinItems().maxOfOrNull{it.sort_order} ?: 0)+1)) else {dao.updateCheckinItem(v);v.id} }
    suspend fun reorderCheckinItems(ids:List<Long>)=transaction { dao ->
        val items=dao.checkinItems().associateBy{it.id};require(ids.size==items.size && ids.toSet()==items.keys)
        ids.forEachIndexed{i,id->dao.updateCheckinItem(items.getValue(id).copy(sort_order=i))}
    }
    suspend fun scores(from:LocalDate,to:LocalDate)=withContext(Dispatchers.IO){db().dao().scores(from.toString(),to.toString())}
    suspend fun setScore(date:LocalDate,item:Long,value:Int?)=transaction { dao -> if(value==null)dao.deleteScore(date.toString(),item) else {require(value in 1..5);dao.score(CheckinScoreEntity(date.toString(),item,value))} }
    // --- Stage reviews, symptom checks, effect visibility (REQUIREMENTS 15) ---
    suspend fun stageReviews()=withContext(Dispatchers.IO){db().dao().stageReviews()}
    suspend fun saveStageReview(v:StageReviewEntity)=transaction { dao ->
        LocalDate.parse(v.date);require(v.satisfaction==null||v.satisfaction in 1..5)
        require(v.smoking==null||v.smoking in listOf("YES","NO"))
        require(v.systolic==null||v.systolic in 1..999);require(v.diastolic==null||v.diastolic in 1..999)
        require(v.weight_kg==null||v.weight_kg.isFinite()&&v.weight_kg>0)
        val effects=JSONObject(v.effects_json)
        effects.keys().forEach{key->val value=effects.get(key);require(value is String && (key.endsWith(":note")||value in listOf("NOT_YET","NOTICED","UNSURE")))}
        val id=dao.stageReview(v);if(v.id!=0L)v.id else id
    }
    suspend fun deleteStageReview(id:Long)=transaction { it.deleteStageReview(id) }
    suspend fun symptomChecks(from:LocalDate,to:LocalDate)=withContext(Dispatchers.IO){db().dao().symptomChecks(from.toString(),to.toString())}
    suspend fun setSymptomCheck(date:LocalDate,group:String,checked:Boolean,note:String?=null,
        contextProvider:((List<MedicationEntity>,Map<Long,ProfileEntity>)->String?)?=null)=transaction { dao ->
        if(checked) {
            val previous=dao.symptomChecks(date.toString(),date.toString()).firstOrNull{it.group_id==group}
            val meds=dao.medications()
            val context=if(previous!=null)previous.context_snapshot else contextProvider?.invoke(meds,meds.mapNotNull{m->dao.profile(m.id)?.let{m.id to it}}.toMap())
            dao.symptomCheck(SymptomCheckEntity(date.toString(),group,note?.takeIf{it.isNotBlank()},context))
        } else dao.deleteSymptomCheck(date.toString(),group)
    }
    suspend fun confirmMissed(id:Long)=transaction { dao ->
        val r=requireNotNull(dao.recordById(id));require(r.unconfirmed && r.deleted_at_utc==null)
        dao.updateRecord(r.copy(origin="APP",revision=r.revision+1))
    }
    suspend fun reviewEffects()=withContext(Dispatchers.IO){db().dao().reviewEffects()}
    suspend fun setReviewEffect(id:String,enabled:Boolean)=transaction { it.reviewEffect(ReviewEffectEntity(id,enabled)) }
    suspend fun setContainerInfo(id:Long,source:String?,batch:String?)=transaction { it.setContainerInfo(id,source?.trim()?.takeIf{s->s.isNotEmpty()},batch?.trim()?.takeIf{s->s.isNotEmpty()}) }
    suspend fun notes(from:LocalDate,to:LocalDate)=withContext(Dispatchers.IO){db().dao().notes(from.toString(),to.toString())}
    suspend fun setNote(date:LocalDate,text:String)=transaction { dao -> if(text.isBlank())dao.deleteNote(date.toString()) else dao.note(DayNoteEntity(date.toString(),text)) }
    /** Planned slots in [from, to) for forecasting; reconciles first so past slots carry their final state. */
    suspend fun planned(from:Instant,to:Instant,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transaction { dao -> timeline(dao,now,from,to,zone) }
    suspend fun labs()=withContext(Dispatchers.IO){db().dao().labs()}
    suspend fun labContexts()=withContext(Dispatchers.IO){db().dao().labContexts()}
    private suspend fun captureLab(dao:NotesDao,lab:LabValueEntity,origin:String,now:Instant,estimate:String?) {
        val old=dao.labContexts().filter{it.lab_id==lab.id}.maxByOrNull{it.revision}
        val captured=maxOf(now.toEpochMilli(),old?.captured_utc ?: Long.MIN_VALUE)
        val json=LabContext.build(lab,dao.records(),dao.regimens(),dao.rules().associate{it.id to it.config_snapshot},estimate)
        dao.labContext(LabContextEntity(lab_id=lab.id,revision=(old?.revision ?: 0)+1,captured_utc=captured,origin=origin,context_json=json))
    }
    suspend fun saveLab(value:LabValueEntity,now:Instant=Instant.now(),recapture:Boolean=false,estimate:suspend(NotesDao,LabValueEntity)->String?={_,_->null})=transaction { dao ->
        require(value.value.isFinite() && value.value>0 && value.analyte_code.isNotBlank() && value.unit.isNotBlank())
        ZoneId.of(value.sampled_zone)
        val old=if(value.id==0L)null else requireNotNull(dao.labs().singleOrNull{it.id==value.id})
        dao.analyte(AnalyteEntity(value.analyte_code,value.unit))
        val saved=if(value.id==0L)value.copy(id=dao.insertLab(value)) else value.also{dao.updateLab(it)}
        val sampleChanged=old!=null && (old.sampled_utc!=value.sampled_utc || old.sampled_zone!=value.sampled_zone || old.analyte_code!=value.analyte_code)
        if(old==null || sampleChanged || recapture)
            captureLab(dao,saved,if(old==null)"AT_ENTRY" else if(sampleChanged)"SAMPLE_CHANGED" else "RECONSTRUCTED",now,estimate(dao,saved))
    }
    suspend fun rebuildLabContext(id:Long,now:Instant=Instant.now(),estimate:suspend(NotesDao,LabValueEntity)->String?={_,_->null})=transaction { dao ->
        val lab=requireNotNull(dao.labs().singleOrNull{it.id==id})
        captureLab(dao,lab,"RECONSTRUCTED",now,estimate(dao,lab))
    }
    suspend fun deleteLab(id:Long)=transaction { it.deleteLab(id) }
    /** Body weight is a single current PK parameter (no history in V1). */
    suspend fun weight()=withContext(Dispatchers.IO){db().dao().pkSettings()?.current_weight_kg}
    suspend fun setWeight(kg:Double)=transaction { require(kg.isFinite() && kg>0 && kg<1000); it.pkSettings(PkSettingsEntity(1,kg)) }
}
