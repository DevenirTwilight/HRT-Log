package net.plainnotes.app.data

import java.time.*
import net.plainnotes.app.domain.TherapyStandard

/** Only test fixtures: produce the old append-only format to verify build-24/schema-7 compatibility. */
suspend fun NotesRepository.confirmHistoryPeriod(periodKey:String?,medicationId:Long,standard:TherapyStandard,from:LocalDate,until:LocalDate?,
    zone:ZoneId,identityJson:String,evidenceJson:String,now:Instant=Instant.now()):String=transaction{dao->
    val key=periodKey ?: java.util.UUID.randomUUID().toString();val all=dao.historyPeriods()
    val row=HistoryPeriodEntity(period_key=key,revision=(all.filter{it.period_key==key}.maxOfOrNull{it.revision} ?: 0)+1,state=HistoryPeriods.CONFIRMED,
        medication_id=medicationId,identity_json=identityJson,standard_json=HistoryPeriods.standardJson(standard),from_date=from.toString(),until_date=until?.toString(),zone=zone.id,evidence_json=evidenceJson,created_utc=now.toEpochMilli())
    HistoryPeriods.validate(row)
    require(HistoryPeriods.confirmed(all).none{it.period_key!=key && it.medication_id==medicationId && (until==null || LocalDate.parse(it.from_date)<until) && (it.until_date==null || from<LocalDate.parse(it.until_date))})
    dao.insertHistoryPeriod(row);key
}
suspend fun NotesRepository.revokeHistoryPeriod(key:String,now:Instant=Instant.now())=transaction{dao->
    val r=dao.historyPeriods().filter{it.period_key==key}.maxBy{it.revision};dao.insertHistoryPeriod(r.copy(id=0,revision=r.revision+1,state=HistoryPeriods.REVOKED,created_utc=now.toEpochMilli()))
}
suspend fun NotesRepository.splitHistoryPeriod(key:String,day:LocalDate,now:Instant=Instant.now()):String=transaction{dao->
    val r=HistoryPeriods.confirmed(dao.historyPeriods()).single{it.period_key==key}
    require(day>LocalDate.parse(r.from_date) && (r.until_date==null || day<LocalDate.parse(r.until_date)))
    confirmHistoryPeriod(key,r.medication_id,HistoryPeriods.readStandard(r.standard_json),LocalDate.parse(r.from_date),day,ZoneId.of(r.zone),r.identity_json,r.evidence_json,now)
    confirmHistoryPeriod(null,r.medication_id,HistoryPeriods.readStandard(r.standard_json),day,r.until_date?.let(LocalDate::parse),ZoneId.of(r.zone),r.identity_json,r.evidence_json,now)
}
suspend fun NotesRepository.mergeHistoryPeriods(first:String,second:String,now:Instant=Instant.now())=transaction{dao->
    val rows=HistoryPeriods.confirmed(dao.historyPeriods());val a=rows.single{it.period_key==first};val b=rows.single{it.period_key==second}
    require(a.medication_id==b.medication_id && a.standard_json==b.standard_json)
    revokeHistoryPeriod(second,now)
    confirmHistoryPeriod(first,a.medication_id,HistoryPeriods.readStandard(a.standard_json),LocalDate.parse(a.from_date),b.until_date?.let(LocalDate::parse),ZoneId.of(a.zone),a.identity_json,a.evidence_json,now)
}
