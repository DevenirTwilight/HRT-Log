package net.plainnotes.app

import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.experimental.ConcentrationPreview
import net.plainnotes.app.pk.*
import net.plainnotes.app.pk.experimental.*
import org.junit.Assert.*
import org.junit.Test

class ConcentrationPreviewTest {
    private val now=600000.0
    private val events=listOf(-30.0,-20.0,-3.0).mapIndexed { i,h -> DoseEvent("synthetic-$i",Route.SUBLINGUAL,now+h,1.0,Ester.E2,70.0,DoseExtras(sublingualTier=2.0)) }
    private val audit=SlAdapterAudit(events,emptyMap())
    @Test fun originalCalendarPreviewAndExactReadoutUseFrozenKernelsAndSharedConditions() {
        val doses=events.map{ExperimentalDose(it.timeH,it.doseMG)}
        for(model in listOf(ResearchModel.M2,ResearchModel.M1)) {
            val p=ConcentrationPreview.compute(audit,model,now)
            val c=if(model==ResearchModel.M2) ResearchSublingualV01.candidates.first() else ResearchM1Catalog.candidates.first()
            assertEquals(c.id,p.candidateId)
            for(h in listOf(now-31,now-30,now-29.3,now-3,now-.123,now,now+24)) {
                val expected=25.0+100.0*ResearchSublingualV01.relativeHistory(h,doses,c)
                assertEquals(expected,p.valueAt(h)!!,0.0)
                assertEquals(expected*Pk.PMOL_PER_PG,p.valueAt(h)!!*Pk.PMOL_PER_PG,0.0)
            }
            assertEquals(p.valueAt(now),p.currentPgMl)
            assertEquals(25.0,p.valueAt(now-31)!!,0.0)
            assertTrue(p.timeH.toList().zipWithNext().all{it.first<it.second})
            for(i in p.timeH.indices) assertEquals(p.valueAt(p.timeH[i])!!,p.values[i],0.0)
        }
        assertEquals("p2az-m1-grid-v1",ResearchM1Catalog.version)
        assertEquals(32,ResearchM1Catalog.candidates.size)
        assertEquals(15,ResearchSublingualV01.candidates.size)
    }
    @Test fun repeatDosesAddResponsesAndBackgroundOnlyOnce() {
        val full=ConcentrationPreview.compute(audit,ResearchModel.M2,now)
        val sums=events.sumOf { ConcentrationPreview.compute(SlAdapterAudit(listOf(it),emptyMap()),ResearchModel.M2,now).currentPgMl!!-25.0 }
        assertEquals(25.0+sums,full.currentPgMl!!,1e-12)
        assertEquals(events,audit.events)
    }
    @Test fun emptyOrPendingResearchNeverFallsBackToLegacyOrInventsMeasurement() {
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val p=ConcentrationPreview.compute(SlAdapterAudit(emptyList(),emptyMap()),model,now)
            assertTrue(p.values.isEmpty());assertNull(p.currentPgMl);assertNull(p.valueAt(now))
        }
        assertThrows(IllegalStateException::class.java){ConcentrationPreview.pending(ResearchModel.LEGACY)}
    }
    @Test fun invalidTimesAreRejectedWithoutNonFiniteCurves() {
        for(t in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java){ConcentrationPreview.compute(audit,ResearchModel.M2,t)}
            assertThrows(IllegalArgumentException::class.java){ConcentrationPreview.compute(audit,ResearchModel.M2,now).valueAt(t)}
        }
    }
}
