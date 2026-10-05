package net.plainnotes.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.domain.*
import org.json.JSONObject
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class NotesRepository @Inject constructor(private val access: DatabaseAccess) {
    suspend fun <T> transaction(block: suspend (NotesDao) -> T): T = withContext(Dispatchers.IO) {
        val db=access.get();db.withTransaction { block(db.dao()) }
    }
    suspend fun medications()=withContext(Dispatchers.IO){access.get().dao().medications()}
    suspend fun appointments()=withContext(Dispatchers.IO){access.get().dao().appointments()}
    suspend fun profile(id:Long)=withContext(Dispatchers.IO){access.get().dao().profile(id)}
    suspend fun rules()=withContext(Dispatchers.IO){access.get().dao().rules()}
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
    suspend fun saveMedication(value:MedicationEntity,ester:String?,kind:RuleKind,interval:Int,times:List<LocalTime>,weekdays:Set<DayOfWeek>,now:Instant=Instant.now()):Long=transaction { dao ->
        require(value.name.isNotBlank() && value.dose_per_intake.isFinite() && value.dose_per_intake>0)
        val zone=ZoneId.systemDefault();reconcile(dao,now,zone)
        val id=if(value.id==0L)dao.insertMedication(value) else {dao.updateMedication(value);value.id}
        val cut=now.toEpochMilli()
        // Preserve pending/overridden slots born before the cutover without persisting future PENDING rows.
        val old=dao.rules().filter { it.medication_id==id && it.effective_until_utc==null }
        if(old.isNotEmpty()) {
            val earliest=old.minOf{it.effective_from_utc}
            val keep=timeline(dao,now,Instant.ofEpochMilli(earliest),now.plusSeconds(86400*14),zone)
                .filter{it.slot.medicationId==id && it.slot.original<now && it.state in listOf(SlotState.PENDING,SlotState.SOON,SlotState.OVERDUE)}
            keep.forEach{dao.retain(it.slot.retained())}
            val models=ruleModels(dao).filter{r->old.any{it.id==r.id}}
            dao.overrides().filter{it.medication_id==id}.forEach { change ->
                models.firstNotNullOfOrNull{ScheduleEngine.resolve(it,change.slot_key,zone)}?.takeIf{it.original<now}?.let{dao.retain(it.retained())}
            }
            old.forEach{require(cut>it.effective_from_utc);dao.updateRule(it.copy(effective_until_utc=cut))}
        }
        if(value.molecule=="E2" && value.route!=null && ester!=null)
            dao.profile(ProfileEntity(id,ester,when(value.route){"ORAL"->"oral";"SUBLINGUAL"->"sublingual";"GEL"->"gel";"PATCH"->"patchApply";"INJECTION"->"injection";else->error("Unsupported estradiol route")}))
        if(value.active) {
            val soon=requireNotNull(value.soon_alert_minutes);val late=requireNotNull(value.late_after_minutes)
            val anchorDate=if(kind==RuleKind.EVERY_N_HOURS)null else now.atZone(zone).toLocalDate().toString()
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
    suspend fun complete(slot:Slot,taken:Instant,dose:Double)=transaction { dao ->
        val existing=dao.records().singleOrNull{it.slot_key==slot.key}
        if(existing?.status in listOf("ON_TIME","LATE"))return@transaction
        require(existing?.status!="SKIPPED");require(dose.isFinite()&&dose>0)
        val status=ScheduleEngine.complete(slot,taken,ZoneId.systemDefault(),dose).status.name
        val result=recordFor(dao,slot,status,taken,dose).copy(unallocated_supply_amount=dose)
        if(existing==null)dao.record(result) else dao.updateRecord(result.copy(id=existing.id,revision=existing.revision+1,config_snapshot=existing.config_snapshot))
    }
    suspend fun unscheduled(id:Long,taken:Instant,dose:Double)=transaction { dao ->
        require(dose.isFinite()&&dose>0);val m=dao.medication(id)
        val snap=JSONObject().put("name",m.name).put("molecule",m.molecule).put("route",m.route).put("unit",m.unit).put("ester",dao.profile(id)?.ester).toString()
        dao.record(RecordEntity(medication_id=id,taken_utc=taken.toEpochMilli(),taken_zone=ZoneId.systemDefault().id,actual_dose=dose,unallocated_supply_amount=dose,
            status="ON_TIME",origin="APP",revision=1,config_snapshot=snap))
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
    suspend fun currentOverride(key:String)=withContext(Dispatchers.IO){access.get().dao().overrides().singleOrNull{it.slot_key==key}?.model() ?: SlotOverride(key)}
    suspend fun appointment(value:AppointmentEntity)=transaction { it.appointment(value) }
}
