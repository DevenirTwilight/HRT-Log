package net.plainnotes.app.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.*

class TreatmentEpochsTest {
    private fun at(s:String)=Instant.parse(s)
    @Test fun simultaneousCombinationChangesHaveOneBoundaryAndEmptyIntervalsAreRetained() {
        val a=at("2026-01-01T00:00:00Z");val b=a.plusSeconds(86400);val c=b.plusSeconds(86400)
        val epochs=TreatmentEpochs.build(listOf(RegimenSpan(1,1,a,b),RegimenSpan(2,2,a,b),RegimenSpan(3,1,b,c)))
        assertEquals(3,epochs.size);assertEquals(setOf(1L,2L),epochs[0].regimenIds);assertEquals(setOf(3L),epochs[1].regimenIds)
        assertTrue(epochs[2].regimenIds.isEmpty());assertTrue(epochs.all{it.until==null || it.until>it.from})
        assertEquals(setOf(epochs[1].key),TreatmentEpochs.at(epochs,b).keys)
        assertTrue(TreatmentEpochs.at(epochs,a.minusSeconds(1)).unknownPortion)
    }
    @Test fun dateOnlyCrossesChangesAndUnknownBeginningWithoutMidnightInference() {
        val from=at("2026-03-10T09:00:00Z");val cut=from.plusSeconds(3600)
        val epochs=TreatmentEpochs.build(listOf(RegimenSpan(1,1,from,cut,true),RegimenSpan(2,1,cut,null)))
        val date=TreatmentEpochs.onDate(epochs,LocalDate.of(2026,3,10),ZoneId.of("UTC"))
        assertEquals(2,date.keys.size);assertTrue(date.uncertain);assertTrue(date.unknownPortion)
        assertFalse(TreatmentEpochs.onDate(epochs,LocalDate.of(2026,3,11),ZoneId.of("UTC")).uncertain)
    }
    @Test fun civilDayUsesDstBoundariesRatherThanFixed24Hours() {
        val zone=ZoneId.of("Europe/Paris");val date=LocalDate.of(2026,3,29)
        val start=date.atStartOfDay(zone).toInstant();val next=date.plusDays(1).atStartOfDay(zone).toInstant()
        assertEquals(23L,Duration.between(start,next).toHours())
        val epochs=TreatmentEpochs.build(listOf(RegimenSpan(1,1,start.minusSeconds(86400),next),RegimenSpan(2,1,next,null)))
        val result=TreatmentEpochs.onDate(epochs,date,zone)
        assertEquals(setOf(epochs.first().key),result.keys);assertFalse(result.uncertain)
    }
}
