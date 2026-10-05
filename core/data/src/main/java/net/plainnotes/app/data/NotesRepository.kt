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
val CHECKIN_DEFAULTS=listOf("OVERALL","MOOD","EMO_STABILITY","ENERGY","AGGRESSIVENESS","LIBIDO","PAIN","PERIOD_LIKE","APPETITE","SLEEP_QUALITY","SKIN_QUALITY")
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
    suspend fun appointments()=withContext(Dispatchers.IO){db().dao().appointments()}
    suspend fun profile(id:Long)=withContext(Dispatchers.IO){db().dao().profile(id)}
    suspend fun rules()=withContext(Dispatchers.IO){db().dao().rules()}
    private suspend fun ruleModels(dao:NotesDao)=dao.rules().map { r ->
        ScheduleRule(r.id,r.medication_id,RuleKind.valueOf(r.kind),r.interval,r.anchor_local?.let(LocalDate::parse),r.anchor_utc?.let(Instant::ofEpochMilli),ZoneId.of(r.anchor_zone),
            Instant.ofEpochMilli(r.effective_from_utc),r.effective_until_utc?.let(Instant::ofEpochMilli),Instant.ofEpochMilli(r.missed_tracking_from_utc),
            dao.times(r.id).map{RuleTime(LocalTime.parse(it.local_time),it.dose_override)},DayOfWeek.entries.filter{r.weekday_mask and (1 shl (it.value-1))!=0}.toSet(),r.dose_snapshot,r.soon_snapshot,r.late_snapshot)
    }
    private fun OverrideEntity.model()=SlotOverride(slot_key,rescheduled_utc?.let(Instant::ofEpochMilli),rescheduled_zone?.let(ZoneId::of),dose_override,skipped)
    private fun RecordEntity.model()=DoseRecord(slot_key,medication_id,DoseStatus.valueOf(status),taken_utc?.let(Instant::ofEpochMilli),taken_zone?.let(ZoneId::of),actual_dose,site,origin=="IMPORT_TM",deleted_at_utc!=null,scheduled_utc?.let(Instant::ofEpochMilli),scheduled_zone?.let(ZoneId::of),planned_dose,late_after_minutes_snapshot,rule_version_id)
    private fun RetainedEntity.model()=Slot(slot_key,rule_id,medication_id,Instant.ofEpochMilli(original_utc),Instant.ofEpochMilli(at_utc),ZoneId.of(zone),dose,soon_minutes,late_minutes,Instant.ofEpochMilli(tracking_from_utc))
    private suspend fun timeline(dao:NotesDao,now:Instant,from:Instant,to:Instant,zone:ZoneId):List<TimelineEntry> {
        val meds=dao.medications().associateBy{it.id}
        val rules=ruleModels(dao).filter { meds[it.medicationId]?.needs_review==null }
        return Timeline.build(rules,dao.overrides().map{it.model()},dao.records().map{it.model()},from,to,now,zone,dao.retained().map{it.model()})
    }
    private suspend fun reconcile(dao:NotesDao,now:Instant,zone:ZoneId) {
        val from=dao.rules().minOfOrNull{it.missed_tracking_from_utc}?.let(Instant::ofEpochMilli) ?: now
        if(from>now)return
        timeline(dao,now,from,now.plusMillis(1),zone).filter{it.state==SlotState.MISSED}.forEach { entry ->
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
    suspend fun saveMedication(value:MedicationEntity,ester:String?,kind:RuleKind,interval:Int,times:List<LocalTime>,weekdays:Set<DayOfWeek>,now:Instant=Instant.now(),pk:ProfileEntity?=null):Long=transaction { dao ->
        val effectiveNow=Instant.ofEpochMilli(now.toEpochMilli())
        require(value.name.isNotBlank() && value.dose_per_intake.isFinite() && value.dose_per_intake>0)
        val zone=ZoneId.systemDefault();reconcile(dao,effectiveNow,zone)
        val id=if(value.id==0L)dao.insertMedication(value) else {dao.updateMedication(value);value.id}
        val cut=effectiveNow.toEpochMilli()
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
        if(value.molecule=="E2" && value.route!=null && ester!=null)
            dao.profile(ProfileEntity(id,ester,when(value.route){"ORAL"->"oral";"SUBLINGUAL"->"sublingual";"GEL"->"gel";"PATCH"->"patchApply";"INJECTION"->"injection";else->error("Unsupported estradiol route")},
                pk?.sl_tier,pk?.gel_product_id,pk?.gel_site,pk?.gel_area_cm2,pk?.patch_release_ug_day))
        if(value.active) {
            val soon=requireNotNull(value.soon_alert_minutes);val late=requireNotNull(value.late_after_minutes)
            val anchorDate=if(kind==RuleKind.EVERY_N_HOURS)null else effectiveNow.atZone(zone).toLocalDate().toString()
            val mask=weekdays.sumOf{1 shl (it.value-1)}
            val r=RuleEntity(medication_id=id,kind=kind.name,interval=interval,weekday_mask=mask,anchor_local=anchorDate,anchor_zone=zone.id,
                anchor_utc=if(kind==RuleKind.EVERY_N_HOURS)cut else null,effective_from_utc=cut,effective_until_utc=null,effective_zone=zone.id,
                missed_tracking_from_utc=cut,dose_snapshot=value.dose_per_intake,soon_snapshot=soon,late_snapshot=late,
                config_snapshot=JSONObject().put("name",value.name).put("molecule",value.molecule).put("route",value.route).put("unit",value.unit).put("ester",ester).toString())
            require(interval in 1..36500); require(kind!=RuleKind.WEEKLY || mask>0)
            require(kind==RuleKind.EVERY_N_HOURS || times.isNotEmpty())
            val rid=dao.rule(r)
            if(kind!=RuleKind.EVERY_N_HOURS)times.distinct().forEach{dao.time(TimeEntity(rule_id=rid,local_time=it.withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),dose_override=null))}
        };id
    }
    suspend fun removeMedication(id:Long,now:Instant=Instant.now())=transaction { dao ->
        reconcile(dao,now,ZoneId.systemDefault())
        val m=dao.medication(id);dao.updateMedication(m.copy(active=false,notifications_on=false))
        dao.rules().filter{it.medication_id==id && it.effective_until_utc==null}.forEach{dao.updateRule(it.copy(effective_until_utc=maxOf(now.toEpochMilli(),it.effective_from_utc+1)))}
    }
    private fun Slot.retained()=RetainedEntity(key,ruleId,medicationId,original.toEpochMilli(),original.toEpochMilli(),originalZone.id,originalDose,soonMinutes,lateMinutes,trackingFrom.toEpochMilli())
    private suspend fun recordFor(dao:NotesDao,slot:Slot,status:String,taken:Instant?=null,dose:Double?=null,origin:String="APP"):RecordEntity {
        val m=dao.medication(slot.medicationId)
        val snapshot=dao.rules().single { it.id==slot.ruleId }.config_snapshot
        return RecordEntity(medication_id=m.id,rule_version_id=slot.ruleId,slot_key=slot.key,scheduled_utc=slot.at.toEpochMilli(),scheduled_zone=slot.zone.id,
            planned_dose=slot.dose,late_after_minutes_snapshot=slot.lateMinutes,taken_utc=taken?.toEpochMilli(),taken_zone=if(taken==null)null else ZoneId.systemDefault().id,
            actual_dose=dose,status=status,origin=origin,revision=1,config_snapshot=snapshot)
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
    suspend fun unscheduled(id:Long,taken:Instant,dose:Double,site:String?=null)=transaction { dao ->
        require(dose.isFinite()&&dose>0);val m=dao.medication(id)
        val snap=JSONObject().put("name",m.name).put("molecule",m.molecule).put("route",m.route).put("unit",m.unit).put("ester",dao.profile(id)?.ester).toString()
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
    suspend fun appointment(value:AppointmentEntity)=transaction { it.appointment(value) }
    suspend fun records()=withContext(Dispatchers.IO){db().dao().records()}

    // --- Import / backup / wipe ---
    suspend fun importTransMemo(plan:net.plainnotes.app.importer.TransMemo.Plan,overwrite:Boolean,zone:ZoneId=ZoneId.systemDefault())=withContext(Dispatchers.IO) {
        val db=db();db.withTransaction { TransMemoWriter.write(db.dao(),db.openHelper.writableDatabase,plan,overwrite,zone) }
    }
    suspend fun exportBackup(password:CharArray):ByteArray=withContext(Dispatchers.IO) {
        val db=db()
        val json=db.withTransaction { JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",db.openHelper.writableDatabase.version)
            .put("created",Instant.now().toString()).put("tables",RawData.dump(db.openHelper.writableDatabase)) }
        BackupCodec.encrypt(json.toString().toByteArray(Charsets.UTF_8),password)
    }
    /** Replaces all data with the backup; throws [BackupCodec.WrongPassword] or [BackupCodec.BadFile] without touching anything. */
    suspend fun restoreBackup(data:ByteArray,password:CharArray)=withContext(Dispatchers.IO) {
        val json=JSONObject(String(BackupCodec.decrypt(data,password),Charsets.UTF_8))
        val db=db()
        if(json.optInt("format")!=BackupCodec.FORMAT_VERSION||json.optInt("schema")!=db.openHelper.writableDatabase.version) throw BackupCodec.BadFile("incompatible version")
        db.withTransaction { RawData.restore(db.openHelper.writableDatabase,json.getJSONObject("tables")) }
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
    suspend fun addContainers(medicationId:Long,capacity:Double,count:Int,openFirst:Boolean)=transaction { dao ->
        require(capacity.isFinite()&&capacity>0&&count in 1..50)
        repeat(count){i-> dao.insertContainer(ContainerEntity(medication_id=medicationId,capacity=capacity,initial_used_amount=0.0,used_amount=0.0,
            opened_on=if(openFirst&&i==0)LocalDate.now().toString() else null,state=if(openFirst&&i==0)"IN_USE" else "SEALED")) }
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
    suspend fun scores(from:LocalDate,to:LocalDate)=withContext(Dispatchers.IO){db().dao().scores(from.toString(),to.toString())}
    suspend fun setScore(date:LocalDate,item:Long,value:Int?)=transaction { dao -> if(value==null)dao.deleteScore(date.toString(),item) else {require(value in 1..5);dao.score(CheckinScoreEntity(date.toString(),item,value))} }
    suspend fun notes(from:LocalDate,to:LocalDate)=withContext(Dispatchers.IO){db().dao().notes(from.toString(),to.toString())}
    suspend fun setNote(date:LocalDate,text:String)=transaction { dao -> if(text.isBlank())dao.deleteNote(date.toString()) else dao.note(DayNoteEntity(date.toString(),text)) }
    /** Planned slots in [from, to) for forecasting; reconciles first so past slots carry their final state. */
    suspend fun planned(from:Instant,to:Instant,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault())=transaction { dao -> timeline(dao,now,from,to,zone) }
    suspend fun labs()=withContext(Dispatchers.IO){db().dao().labs()}
    suspend fun saveLab(value:LabValueEntity)=transaction { dao ->
        require(value.value.isFinite() && value.value>0 && value.analyte_code.isNotBlank() && value.unit.isNotBlank())
        dao.analyte(AnalyteEntity(value.analyte_code,value.unit))
        if(value.id==0L)dao.insertLab(value) else dao.updateLab(value)
    }
    suspend fun deleteLab(id:Long)=transaction { it.deleteLab(id) }
    /** Body weight is a single current PK parameter (no history in V1). */
    suspend fun weight()=withContext(Dispatchers.IO){db().dao().pkSettings()?.current_weight_kg}
    suspend fun setWeight(kg:Double)=transaction { require(kg.isFinite() && kg>0 && kg<1000); it.pkSettings(PkSettingsEntity(1,kg)) }
}
