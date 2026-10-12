package net.plainnotes.app.pk.experimental

import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.abs
import kotlin.math.max

/** Immutable evaluator for the frozen research kernel. No interpolation or discarded doses.
 * Once (ka-ke)*age >= 100, the reference implementation's Poisson tail is exactly 1.
 * Those contributions are sums of exponentials and can be accumulated at dose timestamps.
 * Recent contributions retain the same Poisson series; constants are prepared once.
 */
class PreparedResearchHistory(doses: List<ExperimentalDose>, private val candidate: ExperimentalCandidate) {
    private val history = doses.sortedBy { it.atHour }
    private val ke = candidate.eliminationRatePerHour
    private val ks = candidate.slowRatePerHour
    private val delta = candidate.fastRatePerHour - ke
    private val fastLog = candidate.transitStages * ln(candidate.fastRatePerHour / delta)
    private val factorialLog = (2..candidate.transitStages).sumOf { ln(it.toDouble()) }
    private val reference = raw(1.0)
    private val elimination = DoubleArray(history.size)
    private val absorption = DoubleArray(history.size)
    // Near-equal rates use the original stable Bateman expression for each dose.
    private val aggregate = abs(ks-ke) >= 1e-9 * max(ks,ke)

    init {
        require(history.size <= 10_000)
        require(reference.isFinite() && reference > 0)
        history.forEachIndexed { i, dose ->
            val gap = if(i==0) 0.0 else dose.atHour-history[i-1].atHour
            elimination[i] = dose.mg + if(i==0) 0.0 else elimination[i-1]*exp(-ke*gap)
            absorption[i] = dose.mg + if(i==0) 0.0 else absorption[i-1]*exp(-ks*gap)
        }
    }

    private fun raw(t: Double): Double {
        if(t==0.0) return 0.0
        val z=delta*t
        val tail = if(z>=100.0) 1.0 else {
            var term=exp(candidate.transitStages*ln(z)-z-factorialLog)
            var sum=term
            for(i in candidate.transitStages+1..candidate.transitStages+500) {
                term *= z/i
                sum += term
                if(term<sum*1e-15) break
            }
            sum.coerceIn(0.0,1.0)
        }
        val difference=abs(ks-ke)
        val slow = if(difference<1e-9*max(ks,ke)) ks*t*exp(-ke*t)
            else ks*exp(-minOf(ks,ke)*t)*(-expm1(-difference*t))/difference
        return (1-candidate.slowWeight)*exp(fastLog-ke*t)*tail + candidate.slowWeight*slow
    }

    fun relativeAt(hour: Double): Double {
        require(hour.isFinite())
        // Locate the complete prefix with exactly the same saturation test as the frozen kernel.
        var low=0;var high=history.size
        if(aggregate) while(low<high) {
            val mid=(low+high) ushr 1
            if(delta*(hour-history[mid].atHour)>=100.0) low=mid+1 else high=mid
        }
        val oldCount=low
        var sum=0.0
        if(oldCount>0) {
            val i=oldCount-1
            val age=hour-history[i].atHour
            val e=elimination[i]*exp(-ke*age)
            val s=absorption[i]*exp(-ks*age)
            sum=((1-candidate.slowWeight)*exp(fastLog)*e +
                candidate.slowWeight*ks/(ke-ks)*(s-e))/reference
        }
        for(i in oldCount until history.size) {
            val dose=history[i]
            if(dose.atHour>hour) break
            sum += dose.mg*(raw(hour-dose.atHour)/reference)
        }
        require(sum.isFinite() && sum>=0)
        return sum
    }

    fun studyAt(hour: Double, anchor: ExperimentalStudyAnchor): Double =
        anchor.background + anchor.oneMgIncrementAtOneHour*relativeAt(hour)
}
