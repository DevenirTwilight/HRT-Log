package net.plainnotes.app.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.*

class TreatmentPeriodsTest {
    private val zone=ZoneId.of("UTC")
    private val a=Instant.parse("2026-03-01T08:00:00Z")
    private val b=a.plusSeconds(86400)
    private val c=b.plusSeconds(86400)
    private val standard=TherapyStandard("E2","EV","ORAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0))
    private fun raw(id:Long,from:Instant,until:Instant?,s:TherapyStandard=standard,med:Long=1)=RawTreatmentInterval(RegimenSpan(id,med,from,until),s)
    @Test fun contiguousEquivalentRawVersionsMergeAndKeepEveryReference() {
        val view=TreatmentPeriods.build(listOf(raw(1,a,b),raw(2,b,null)),zone)
        assertEquals(1,view.periods.size);assertEquals(listOf(1L,2L),view.standards.single().rawVersionIds)
        assertEquals(setOf(1L),view.exactAt(b.minusNanos(1)).map{it.span.id}.toSet())
        assertEquals(setOf(2L),view.exactAt(b).map{it.span.id}.toSet())
        val before=TreatmentPeriods.build(listOf(raw(1,a,null)),zone)
        assertEquals(before.periods.single().key,view.periods.single().key)
    }
    @Test fun sameStandardAcrossAShortGapIsOnePeriodButThirtyDaysSplit() {
        // REQUIREMENTS §36: a gap shorter than 30 days with the same standard does not end the period.
        val view=TreatmentPeriods.build(listOf(raw(1,a,b),raw(2,c,null)),zone)
        assertEquals(1,view.periods.size);assertEquals(listOf(1L,2L),view.standards.single().rawVersionIds)
        assertTrue(view.exactAt(b).isEmpty()) // no actual-history input exists in this builder
        val later=b.plus(Duration.ofDays(30))
        val split=TreatmentPeriods.build(listOf(raw(1,a,b),raw(2,later,null)),zone)
        assertEquals(3,split.periods.size);assertTrue(split.segments[1].standardSpanKeys.isEmpty())
        assertEquals(1,TreatmentPeriods.build(listOf(raw(1,a,b),raw(2,later.minusSeconds(1),null)),zone).periods.size)
    }
    @Test fun differentMedicationEntriesOfTheSameMedicineContinueEachOther() {
        // Import-created entry before, the app's own entry after; missing ester on one side is compatible.
        val imported=standard.copy(ester=null)
        val view=TreatmentPeriods.build(listOf(raw(1,a,b,imported,med=2),raw(2,b.plusSeconds(3600),null,med=1)),zone)
        assertEquals(1,view.periods.size);assertEquals(listOf(1L,2L),view.standards.single().rawVersionIds);assertEquals("EV",view.standards.single().standard.ester)
        // Entries used at the same time stay separate; a different medicine never joins.
        assertEquals(2,TreatmentPeriods.build(listOf(raw(1,a,c,med=2),raw(2,b,null,med=1)),zone).standards.size)
        assertEquals(2,TreatmentPeriods.build(listOf(raw(1,a,b,standard.copy(compound="CPA"),med=2),raw(2,b,null,med=1)),zone).periods.size)
        assertEquals(2,TreatmentPeriods.build(listOf(raw(1,a,b,standard.copy(ester="EEn"),med=2),raw(2,b,null,med=1)),zone).periods.size)
    }
    @Test fun standardChangesCutButMultisetPermutationRemainsExplicitlyUnknown() {
        val changes=listOf(standard.copy(doses=listOf(3.0)),standard.copy(doses=listOf(2.0,2.0)),
            standard.copy(route="SUBLINGUAL"),standard.copy(ester="E2"),
            standard.copy(interval=2),standard.copy(kind="EVERY_N_HOURS"),standard.copy(kind="WEEKLY",weeklyCount=2))
        changes.forEach{changed->assertEquals(2,TreatmentPeriods.build(listOf(raw(1,a,b),raw(2,b,null,changed)),zone).periods.size)}
        val product=standard.copy(formulation="product-a")
        assertEquals(2,TreatmentPeriods.build(listOf(raw(1,a,b,product),raw(2,b,null,product.copy(formulation="product-b"))),zone).periods.size)
        // §36: a field missing on one side is compatible, not a change.
        assertEquals(1,TreatmentPeriods.build(listOf(raw(1,a,b),raw(2,b,null,product)),zone).periods.size)
        val uneven=standard.copy(doses=listOf(1.0,2.0));val permutation=uneven.copy(doses=listOf(2.0,1.0))
        assertEquals(uneven.therapySignatureV2(),permutation.therapySignatureV2());assertTrue(uneven.slotIdentityUnknown)
        assertNotEquals(standard.copy(doses=listOf(1.0,3.0)).therapySignatureV2(),standard.copy(doses=listOf(2.0,2.0)).therapySignatureV2())
    }
    @Test fun sameDayMultiMedicationChangesAndABAHaveOneDisplayTransitionAndAllExactFacts() {
        val afternoon=b.plusSeconds(7*3600);val evening=b.plusSeconds(12*3600)
        val view=TreatmentPeriods.build(listOf(raw(1,a,b),raw(2,b,evening,standard.copy(doses=listOf(3.0))),raw(3,evening,null),
            raw(4,a,afternoon,med=2),raw(5,afternoon,null,standard.copy(doses=listOf(4.0)),2)),zone)
        assertEquals(2,view.periods.size);assertEquals(3,view.periods.last().segments.size)
        assertEquals(setOf(2L,4L),view.exactAt(b.plusSeconds(3600)).map{it.span.id}.toSet())
        assertEquals(setOf(3L,5L),view.exactAt(evening).map{it.span.id}.toSet())
        assertEquals(view.periods.last(),view.periodOn(b.atZone(zone).toLocalDate()))
    }
    @Test fun monthBoundaryDSTAndZonePolicyUseExactInstants() {
        val berlin=ZoneId.of("Europe/Berlin")
        val from=Instant.parse("2026-03-28T23:00:00Z");val cut=Instant.parse("2026-03-29T13:00:00Z");val next=Instant.parse("2026-03-31T22:00:00Z")
        val view=TreatmentPeriods.build(listOf(raw(1,from,cut),raw(2,cut,next,standard.copy(doses=listOf(3.0))),raw(3,next,null)),berlin)
        assertEquals(2,view.periods.size);assertEquals(from,view.periods.first().from)
        assertEquals(1L,view.exactAt(cut.minusNanos(1)).single().span.id);assertEquals(2L,view.exactAt(cut).single().span.id)
        assertEquals(LocalDate.of(2026,4,1),view.periods.last().from.atZone(berlin).toLocalDate())
    }
    @Test fun additionReplacementAndStoppingOneOfTwoMedicationPlansChangeTheCombination() {
        val view=TreatmentPeriods.build(listOf(raw(1,a,c),raw(2,b,c,med=2),raw(3,c,null,med=3)),zone)
        assertEquals(3,view.periods.size);assertEquals(2,view.segments[1].standardSpanKeys.size)
        assertEquals(setOf(3L),view.exactAt(c).map{it.span.medicationId}.toSet())
    }
}
