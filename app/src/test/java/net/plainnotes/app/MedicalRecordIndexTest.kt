package net.plainnotes.app
import net.plainnotes.app.medical.*
import net.plainnotes.app.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*
class MedicalRecordIndexTest {
 private val now=Instant.parse("2026-04-01T00:00:00Z")
 private fun visit(id:Long,at:Instant=now,confirmed:Long?=null)=AppointmentEntity(id=id,type="ENDO",at_utc=at.toEpochMilli(),at_zone="UTC",remind_minutes_before=0,completed_utc=confirmed)
 @Test fun pastDoesNotInferConfirmationAndSortsEqualTimesByStableId(){val past=MedicalRecordIndex.past(listOf(visit(1),visit(2,confirmed=1),visit(3,now.plusSeconds(1)),visit(4,now.minusSeconds(1))),now);assertEquals(listOf(2L,1L,4L),past.map{it.id});assertNull(past[1].completed_utc)}
 @Test fun rangeUsesExplicitVisitZoneAndExclusiveNextDayIncludingDST(){val zone=ZoneId.of("Europe/Paris");val d=LocalDate.parse("2026-03-29");val start=d.atStartOfDay(zone).toInstant();val end=d.plusDays(1).atStartOfDay(zone).toInstant();fun lab(id:Long,t:Instant)=LabValueEntity(id=id,analyte_code="E2",value=1.0,unit="pg/mL",sampled_utc=t.toEpochMilli(),sampled_zone="Pacific/Kiritimati")
assertEquals(23,Duration.between(start,end).toHours());val a=lab(1,start);val b=lab(2,end.minusMillis(1));assertEquals(listOf(b,a),MedicalRecordIndex.labs(listOf(lab(0,start.minusMillis(1)),a,b,lab(3,end)),d,d,zone))}
 @Test fun rangeDoesNotPersistOrInferAssociationsAndEqualTimesAreStable(){val a=LabValueEntity(id=1,analyte_code="E2",value=1.0,unit="pg/mL",sampled_utc=now.toEpochMilli(),sampled_zone="UTC");val b=a.copy(id=2);assertEquals(listOf(b,a),MedicalRecordIndex.labs(listOf(a,b),now.atZone(ZoneId.of("UTC")).toLocalDate(),now.atZone(ZoneId.of("UTC")).toLocalDate(),ZoneId.of("UTC")))}
 @Test fun unchangedLaterDSTOffsetKeepsExactInstant(){val zone=ZoneId.of("Europe/Paris");val t=Instant.parse("2026-10-25T01:30:00.123Z");val a=visit(1,t).copy(at_zone=zone.id);assertEquals(t,MedicalVisitTime.resolve(t.atZone(zone).toLocalDateTime(),zone,a));assertEquals(t.minusSeconds(3600),MedicalVisitTime.resolve(t.atZone(zone).toLocalDateTime(),zone,a,ZoneOffset.ofHours(2)))}
 @Test fun gapIsRejectedAndNewOverlapRequiresExplicitOffset(){val z=ZoneId.of("Europe/Paris");assertNull(MedicalVisitTime.resolve(LocalDateTime.parse("2026-03-29T02:30:00"),z));val overlap=LocalDateTime.parse("2026-10-25T02:30:00");assertNull(MedicalVisitTime.resolve(overlap,z));assertEquals(Instant.parse("2026-10-25T00:30:00Z"),MedicalVisitTime.resolve(overlap,z,chosen=ZoneOffset.ofHours(2)));assertEquals(Instant.parse("2026-10-25T01:30:00Z"),MedicalVisitTime.resolve(overlap,z,chosen=ZoneOffset.ofHours(1)))}
 @Test fun recordedZoneCrossesDateWithoutChangingOccurrence(){val a=visit(1,Instant.parse("2026-03-01T23:30:00Z")).copy(at_zone="Pacific/Kiritimati");val z=ZoneId.of(a.at_zone);assertEquals(LocalDate.parse("2026-03-02"),Instant.ofEpochMilli(a.at_utc).atZone(z).toLocalDate());assertEquals(Instant.ofEpochMilli(a.at_utc),MedicalVisitTime.resolve(Instant.ofEpochMilli(a.at_utc).atZone(z).toLocalDateTime(),z,a))}
 @Test fun changingVisitZoneExplicitlyReinterpretsOnlySelectedLocalClock(){val v=visit(1,Instant.parse("2025-01-01T23:30:00Z")).copy(at_zone="Pacific/Kiritimati");val local=Instant.ofEpochMilli(v.at_utc).atZone(ZoneId.of(v.at_zone)).toLocalDateTime();assertEquals(Instant.parse("2025-01-02T13:30:00Z"),MedicalVisitTime.resolve(local,ZoneId.of("UTC"),v))}
}
