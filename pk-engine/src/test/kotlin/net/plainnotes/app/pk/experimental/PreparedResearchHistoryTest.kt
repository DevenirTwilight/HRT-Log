package net.plainnotes.app.pk.experimental

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class PreparedResearchHistoryTest {
    @Test fun allFrozenCandidatesMatchOriginalAcrossIrregularHistoryAndSaturationBoundaries() {
        val random=Random(81012)
        val origin=600000.0
        val doses=(0 until 360).map { ExperimentalDose(origin-it*12.0-random.nextDouble(), .1+random.nextDouble()*3) }
            .plus(listOf(ExperimentalDose(origin,1.0),ExperimentalDose(origin,2.0)))
        for(c in ResearchSublingualV01.candidates+ResearchM1Catalog.candidates) {
            val prepared=PreparedResearchHistory(doses.reversed(),c)
            val boundary=100/(c.fastRatePerHour-c.eliminationRatePerHour)
            val times=listOf(origin-5000,origin-4300,origin,origin+1,origin+8,origin+24,origin+720)+
                (0 until 80).map { origin-4320+random.nextDouble()*5040 }+
                listOf(-1e-8,0.0,1e-8).map { origin+boundary+it }
            for(t in times) {
                val expected=ResearchSublingualV01.relativeHistory(t,doses,c)
                assertEquals(expected,prepared.relativeAt(t),1e-11*maxOf(1.0,expected),"${c.id} at $t")
            }
        }
    }

    @Test fun closeRatesEmptyHistoryAndLongSlowTailRemainExact() {
        val base=ResearchSublingualV01.candidates.first()
        for(ks in listOf(base.eliminationRatePerHour,base.eliminationRatePerHour*(1+1e-10),.0001,2.0)) {
            val c=base.copy(slowRatePerHour=ks)
            val doses=listOf(ExperimentalDose(-4000.0,2.0),ExperimentalDose(-2000.0,1.0),ExperimentalDose(0.0,1.5))
            val p=PreparedResearchHistory(doses,c)
            for(t in listOf(-5000.0,-4000.0,-3999.9,-1.0,0.0,1.0,24.0,10000.0)) {
                val expected=ResearchSublingualV01.relativeHistory(t,doses,c)
                assertEquals(expected,p.relativeAt(t),1e-11*maxOf(1.0,expected))
            }
        }
        val anchor=ExperimentalStudyAnchor("synthetic","pg/mL",24.0,400.0)
        assertEquals(24.0,PreparedResearchHistory(emptyList(),base).studyAt(0.0,anchor),0.0)
        assertThrows(IllegalArgumentException::class.java) { PreparedResearchHistory(emptyList(),base).relativeAt(Double.NaN) }
    }

    @Test fun longHistoryBenchmarkUsesIdenticalQueriesAndChecksEveryValue() {
        val doses=(0 until 360).map { ExperimentalDose(600000.0-3-it*12.0,1.0) }
        val times=DoubleArray(10443) { 600000.0-4320+it*5040.0/10442 }
        for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {
            val c=ResearchDisplayProtocol.candidate(model)
            val anchor=ResearchDisplayProtocol.anchor(model,c)
            val p=PreparedResearchHistory(doses,c)
            repeat(2) { times.take(100).forEach { p.studyAt(it,anchor);ResearchSublingualV01.studyScenario(it,doses,c,anchor) } }
            var start=System.nanoTime()
            val expected=times.map { ResearchSublingualV01.studyScenario(it,doses,c,anchor) }
            val oldMs=(System.nanoTime()-start)/1e6
            start=System.nanoTime()
            val actual=times.map { p.studyAt(it,anchor) }
            val newMs=(System.nanoTime()-start)/1e6
            val maxError=expected.indices.maxOf { abs(expected[it]-actual[it]) }
            expected.indices.forEach { assertEquals(expected[it],actual[it],1e-8) }
            println("PREVIEW_HISTORY_BENCHMARK model=$model doses=360 points=${times.size} old_ms=$oldMs prepared_ms=$newMs max_abs_error=$maxError")
        }
    }
}
