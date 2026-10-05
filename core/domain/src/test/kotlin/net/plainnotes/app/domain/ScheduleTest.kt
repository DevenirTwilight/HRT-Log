package net.plainnotes.app.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.*

class ScheduleTest {
    private val paris=ZoneId.of("Europe/Paris")
    private fun day(s:String)=LocalDate.parse(s)
    private fun utc(s:String)=Instant.parse(s)
    private fun rule(id:Long=1,med:Long=1,date:String="2026-01-01",times:List<String> = listOf("12:00"),n:Int=1)=ScheduleRule(id,med,RuleKind.EVERY_N_DAYS,n,day(date),null,paris,
        day(date).atStartOfDay(paris).toInstant(),times=times.map { RuleTime(LocalTime.parse(it)) },dose=2.0,soonMinutes=15,lateMinutes=120)
    private fun span(r:ScheduleRule,d:String)=ScheduleEngine.expand(r,day(d).atStartOfDay(paris).toInstant(),day(d).plusDays(1).atStartOfDay(paris).toInstant(),paris)
    @Test fun `spring gap moves to first valid instant and preserves nominal identity`() {
        val s=span(rule(times=listOf("02:30")),"2026-03-29").single()
        assertEquals(LocalTime.of(3,0),s.at.atZone(paris).toLocalTime()); assertTrue(s.key.endsWith("02:30:00"))
    }
    @Test fun `autumn overlap occurs once at earlier offset`() {
        val s=span(rule(times=listOf("02:30")),"2026-10-25").single()
        assertEquals(ZoneOffset.ofHours(2),s.at.atZone(paris).offset)
    }
    @Test fun `distinct nominal slots collapsed by spring gap stay distinct`() {
        val s=span(rule(times=listOf("02:10","02:30")),"2026-03-29")
        assertEquals(2,s.size); assertEquals(s[0].at,s[1].at); assertNotEquals(s[0].key,s[1].key)
    }
    @Test fun `wall schedule follows current zone with stable keys`() {
        val r=rule(); val keys= mutableSetOf<String>()
        listOf("Europe/Paris","Asia/Tokyo","America/New_York").forEach { z ->
            val zone=ZoneId.of(z); val lo=day("2026-02-02").atStartOfDay(zone).toInstant()
            val s=ScheduleEngine.expand(r,lo,lo.plusSeconds(86400),zone).single()
            assertEquals(LocalTime.NOON,s.at.atZone(zone).toLocalTime()); keys+=s.key
        };assertEquals(1,keys.size)
    }
    @Test fun `daily noon remains noon with 23 hour spring interval`() {
        val r=rule(); assertEquals(23,Duration.between(span(r,"2026-03-28").single().at,span(r,"2026-03-29").single().at).toHours())
    }
    @Test fun `elapsed identity and spacing survive DST and zone changes`() {
        val a=utc("2026-03-28T12:00:00Z")
        val r=ScheduleRule(2,1,RuleKind.EVERY_N_HOURS,12,null,a,paris,a,dose=1.0,soonMinutes=0,lateMinutes=5)
        val s=ScheduleEngine.expand(r,a,a.plusSeconds(86400*3),paris)
        assertTrue(s.zipWithNext().all { (x,y)->Duration.between(x.at,y.at).toHours()==12L })
        assertEquals(s.map{it.key},ScheduleEngine.expand(r,a,a.plusSeconds(86400*3),ZoneId.of("Asia/Tokyo")).map{it.key})
        val o=SlotOverride(s[1].key,a.plusSeconds(3600),paris,3.0,true)
        assertEquals(3.0,o.apply(ScheduleEngine.resolve(r,s[1].key,ZoneId.of("UTC"))!!).dose)
    }
    @Test fun `UTC version cutover is half open with no overlap`() {
        val cut=utc("2026-01-02T14:00:00Z")
        val a=rule(times=listOf("12:00","15:00")).copy(until=cut)
        val b=rule(id=2,times=listOf("15:00","18:00")).copy(from=cut,trackingFrom=cut)
        ScheduleEngine.validateVersions(listOf(a,b))
        val slots=span(a,"2026-01-02")+span(b,"2026-01-02")
        assertEquals(3,slots.size);assertEquals(1,slots.count{it.at==cut});assertEquals(2,slots.first{it.at==cut}.ruleId)
        assertThrows(IllegalArgumentException::class.java){ScheduleEngine.validateVersions(listOf(a.copy(until=cut.plusSeconds(1)),b))}
    }
    @Test fun `every N days and every N weeks with weekday mask`() {
        assertEquals(0,span(rule(n=3),"2026-01-02").size);assertEquals(1,span(rule(n=3),"2026-01-04").size)
        val w=rule(date="2026-01-05").copy(kind=RuleKind.WEEKLY,interval=2,weekdays=setOf(DayOfWeek.MONDAY,DayOfWeek.FRIDAY))
        assertEquals(1,span(w,"2026-01-09").size);assertEquals(0,span(w,"2026-01-12").size);assertEquals(1,span(w,"2026-01-19").size)
    }
    @Test fun `composable override supports partial undo and empty deletion signal`() {
        val s=span(rule(),"2026-01-02").single(); val o=SlotOverride(s.key,s.at.plusSeconds(3600),paris,3.0,true)
        assertTrue(o.apply(s).skipped);assertEquals(3.0,o.apply(s).dose)
        val undo=o.copy(skipped=false);assertEquals(o.rescheduled,undo.apply(s).at)
        assertEquals(3.0,undo.copy(rescheduled=null,zone=null).apply(s).dose)
        assertTrue(undo.copy(rescheduled=null,zone=null,dose=null).isDefault)
        assertThrows(IllegalArgumentException::class.java){SlotOverride(s.key,s.at,null)}
    }
    private fun state(r:ScheduleRule,at:Instant,now:Instant,os:List<SlotOverride> = emptyList(),rs:List<DoseRecord> = emptyList()):SlotState =
        Timeline.build(listOf(r),os,rs,at,at.plusSeconds(1),now,paris).single().state
    @Test fun `late boundary is inclusive for on time and exclusive for overdue`() {
        val r=rule();val s=span(r,"2026-01-02").single();val edge=s.at.plusSeconds(7200)
        assertEquals(SlotState.PENDING,state(r,s.at,edge));assertEquals(SlotState.OVERDUE,state(r,s.at,edge.plusSeconds(1)))
        assertEquals(DoseStatus.ON_TIME,ScheduleEngine.complete(s,edge,paris,2.0).status)
        assertEquals(DoseStatus.LATE,ScheduleEngine.complete(s,edge.plusSeconds(1),paris,2.0).status)
    }
    @Test fun `missed only when next same medication slot arrives`() {
        val r=rule();val s=span(r,"2026-01-02").single();val next=s.at.plusSeconds(86400)
        assertEquals(SlotState.OVERDUE,state(r,s.at,next.minusSeconds(1)));assertEquals(SlotState.MISSED,state(r,s.at,next))
    }
    @Test fun `long interval has no 24 hour missed threshold`() {
        val r=rule(n=7);val s=span(r,"2026-01-01").single()
        assertEquals(SlotState.OVERDUE,state(r,s.at,s.at.plusSeconds(86400*6)))
        assertEquals(SlotState.MISSED,state(r,s.at,s.at.plusSeconds(86400*7)))
    }
    @Test fun `ended rule without successor stays overdue`() {
        val r=rule().copy(until=utc("2026-01-02T00:00:00Z"));val s=span(r,"2026-01-01").single()
        assertEquals(SlotState.OVERDUE,state(r,s.at,s.at.plusSeconds(86400*100)))
    }
    @Test fun `late larger than interval does not delay missed`() {
        val r=rule().copy(lateMinutes=4000);val s=span(r,"2026-01-02").single()
        assertEquals(SlotState.MISSED,state(r,s.at,s.at.plusSeconds(86400)))
    }
    @Test fun `skipped successor remains a boundary and skipped current is not missed`() {
        val r=rule();val s=span(r,"2026-01-02").single();val n=span(r,"2026-01-03").single()
        assertEquals(SlotState.MISSED,state(r,s.at,n.at,listOf(SlotOverride(n.key,skipped=true))))
        assertEquals(SlotState.SKIPPED,state(r,s.at,n.at,listOf(SlotOverride(s.key,skipped=true))))
    }
    @Test fun `backfill replaces missed and actual time controls classification`() {
        val r=rule();val s=span(r,"2026-01-02").single();val record=ScheduleEngine.complete(s,s.at.plusSeconds(60),paris,1.5)
        assertEquals(SlotState.ON_TIME,state(r,s.at,s.at.plusSeconds(86400*2),rs=listOf(record)))
    }
    @Test fun `import tracking boundary prevents retroactive missed`() {
        val r=rule().copy(trackingFrom=utc("2026-02-01T00:00:00Z"));val s=span(r,"2026-01-02").single()
        assertEquals(SlotState.OVERDUE,state(r,s.at,s.at.plusSeconds(86400*2)))
    }
    @Test fun `reschedule outside original display range stays discoverable`() {
        val r=rule();val s=span(r,"2026-01-02").single();val newAt=s.at.plusSeconds(86400*10+3600)
        val entries=Timeline.build(listOf(r),listOf(SlotOverride(s.key,newAt,paris,4.0)),emptyList(),newAt,newAt.plusSeconds(1),newAt,paris)
        assertEquals(s.key,entries.single().slot.key);assertEquals(4.0,entries.single().slot.dose)
    }
    @Test fun `other medication does not cause missed`() {
        val r=rule(n=7);val s=span(r,"2026-01-01").single()
        val t=Timeline.build(listOf(r,rule(id=2,med=2)),emptyList(),emptyList(),s.at,s.at.plusSeconds(1),s.at.plusSeconds(86400),paris)
        assertEquals(SlotState.OVERDUE,t.first{it.slot.medicationId==1L}.state)
    }
    @Test fun `partial override undo restores nominal values after composed application`() {
        val original=span(rule(),"2026-01-02").single()
        val changed=SlotOverride(original.key,original.at.plusSeconds(3600),ZoneId.of("Asia/Tokyo"),3.0,true).apply(original)
        val restored=SlotOverride(original.key).apply(changed)
        assertEquals(original.at,restored.at);assertEquals(original.zone,restored.zone);assertEquals(original.dose,restored.dose)
    }
    @Test fun `pending slot retained across rule replacement uses new version successor`() {
        val originalRule=rule();val s=span(originalRule,"2026-01-02").single()
        val cut=s.at.plusSeconds(3600)
        val old=originalRule.copy(until=cut);val next=rule(id=2,times=listOf("18:00")).copy(from=cut,trackingFrom=cut)
        val now=s.at.plusSeconds(21600)
        val result=Timeline.build(listOf(old,next),emptyList(),emptyList(),s.at,s.at.plusSeconds(1),now,paris,listOf(s))
        assertEquals(SlotState.MISSED,result.single().state)
    }
    @Test fun `frozen missed record is not reinterpreted by a later reschedule`() {
        val r=rule();val s=span(r,"2026-01-02").single()
        val record=DoseRecord(s.key,1,DoseStatus.MISSED)
        val newAt=s.at.plusSeconds(86400*10)
        val result=Timeline.build(listOf(r),listOf(SlotOverride(s.key,newAt,paris)),listOf(record),newAt,newAt.plusSeconds(1),newAt,paris)
        assertEquals(SlotState.MISSED,result.single{it.slot.key==s.key}.state)
    }
    @Test fun `recorded planned timestamp and dose do not move with timezone or new configuration`() {
        val r=rule();val s=span(r,"2026-01-02").single()
        val record=ScheduleEngine.complete(s,s.at,paris,2.0).copy(scheduled=s.at,scheduledZone=paris,plannedDose=s.dose,lateSnapshot=s.lateMinutes,ruleVersionId=r.id)
        val changed=r.copy(dose=99.0,lateMinutes=1)
        val result=Timeline.build(listOf(changed),emptyList(),listOf(record),s.at,s.at.plusSeconds(1),s.at,ZoneId.of("Asia/Tokyo"))
        assertEquals(s.at,result.single().slot.at);assertEquals(paris,result.single().slot.zone);assertEquals(2.0,result.single().slot.dose)
    }
    @Test fun `non taken states require NULL actual values`() {
        assertThrows(IllegalArgumentException::class.java){DoseRecord("x",1,DoseStatus.MISSED,actualDose=2.0)}
        assertThrows(IllegalArgumentException::class.java){DoseRecord("x",1,DoseStatus.SKIPPED,taken=Instant.EPOCH,takenZone=paris)}
        assertThrows(IllegalArgumentException::class.java){DoseRecord(null,1,DoseStatus.ON_TIME,Instant.EPOCH,paris,null)}
        assertNull(DoseRecord(null,1,DoseStatus.ON_TIME,Instant.EPOCH,paris,null,imported=true).actualDose)
    }
    @Test fun `alarm consumption rejects old generations and duplicate delivery`() {
        val c=AlarmCache("new",5000,listOf(CachedAlarm("opaque",4000,"NORMAL")))
        assertNull(c.consume("opaque","old"));val consumed=c.consume("opaque","new")!!
        assertNull(consumed.consume("opaque","new"));assertNull(consumed.next(4500));assertNull(c.next(6000))
    }
    @Test fun `actual timestamp input preserves second DST overlap and subseconds`() {
        val at=Instant.parse("2026-11-01T06:30:00.001Z");val zone=ZoneId.of("America/New_York")
        val text=TimestampInput.format(at,zone)
        assertTrue(text.endsWith("-05:00"))
        assertEquals(at,TimestampInput.parse(text,ZoneId.of("Asia/Tokyo")))
    }
    @Test fun `unqualified timestamp input retains established wall DST policy`() {
        val zone=ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"),TimestampInput.parse("2026-03-08T02:30:00",zone))
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"),TimestampInput.parse("2026-11-01T01:30:00",zone))
    }

}
