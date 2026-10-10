package net.plainnotes.app.pk.experimental

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.max

/**
 * P2-X exposed-study M2 (transit + slow absorption) candidates, research-only.
 * NO production activation, personal calibration, or validated coverage probability.
 * All curves are dimensionless relative to a single 1 mg dose's own 1-hour response.
 */
data class ExperimentalCandidate(
    val id: String,
    val transitStages: Int,
    val fastRatePerHour: Double,
    val eliminationRatePerHour: Double,
    val slowRatePerHour: Double,
    val slowWeight: Double,
    val exposedDataPseudoLoss: Double,
    val assumedPriceBaselinePgMl: Double,
    val rosanoBaselineCapPmolL: Double,
) {
    init {
        require(id.isNotBlank())
        require(transitStages in 1..20)
        require(fastRatePerHour.isFinite() && fastRatePerHour > 0.0)
        require(eliminationRatePerHour.isFinite() && eliminationRatePerHour > 0.0)
        require(fastRatePerHour > eliminationRatePerHour)
        require(slowRatePerHour.isFinite() && slowRatePerHour > 0.0)
        require(slowWeight.isFinite() && slowWeight in 0.0..1.0)
        require(exposedDataPseudoLoss.isFinite() && exposedDataPseudoLoss >= 0.0)
        require(assumedPriceBaselinePgMl.isFinite() && assumedPriceBaselinePgMl >= 0.0)
        require(rosanoBaselineCapPmolL.isFinite() && rosanoBaselineCapPmolL >= 0.0)
    }
}

/** Hours relative to a caller-provided common reference; no inferred q6/q12 schedule. */
data class ExperimentalDose(val atHour: Double, val mg: Double) {
    init { require(atHour.isFinite() && mg.isFinite() && mg > 0.0) }
}

/** NOT a confidence, credible, or patient coverage interval. */
data class ExploratoryModelSpread(val minimum: Double, val median: Double, val maximum: Double) {
    init { require(minimum.isFinite() && median.isFinite() && maximum.isFinite()) }
}

/** Descriptive range within ONE hypothetical Price baseline stratum. Not a clinical interval. */
data class ExperimentalBaselineStratum(
    val assumedPriceBaselinePgMl: Double,
    val candidateCount: Int,
    val minimum: Double,
    val maximum: Double,
) {
    init {
        require(assumedPriceBaselinePgMl.isFinite() && assumedPriceBaselinePgMl >= 0.0)
        require(candidateCount > 0)
        require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum)
    }
}

/** A coherent whole time-series from ONE frozen candidate; not a patient trajectory. */
data class ExperimentalCandidateSeries(val candidateId: String, val relativeValues: List<Double>) {
    init {
        require(candidateId.isNotBlank())
        require(relativeValues.all { it.isFinite() && it >= 0.0 })
    }
}

/** A deliberate, explicit, study-specific amplitude — NEVER an individual prediction. */
data class ExperimentalStudyAnchor(
    val studyId: String,
    val concentrationUnit: String,
    val background: Double,
    val oneMgIncrementAtOneHour: Double,
) {
    init {
        require(studyId.isNotBlank() && concentrationUnit.isNotBlank())
        require(background.isFinite() && background >= 0.0)
        require(oneMgIncrementAtOneHour.isFinite() && oneMgIncrementAtOneHour >= 0.0)
    }
}

object ResearchSublingualV01 {
    const val version = "p2x-m2-research-v0.1"
    const val humanCoverageValidated = false
    const val individualPredictionValidated = false
    const val productionEnabled = false
    // Exposed studies: Rosano1997, Komesaroff1998, manually read Price1997 Figure 1.
    // P2-X: retain loss <= minimum loss + 0.10, NOT a confidence/likelihood threshold.
    val candidates: List<ExperimentalCandidate> = listOf(
        ExperimentalCandidate("p2x-14", 8, 12.31990565827258, 1.449638446625054, 0.1937751830647134, 0.5699014723612454, 0.1048814342880041, 18.0, 100.0),
        ExperimentalCandidate("p2x-15", 10, 16.19341597908542, 1.45556127551109, 0.2073393143756696, 0.5861988628441387, 0.1079030392691311, 18.0, 100.0),
        ExperimentalCandidate("p2x-17", 6, 8.442506014907703, 1.432004437963414, 0.2249514752563691, 0.4909015000840863, 0.1069463935787754, 24.0, 100.0),
        ExperimentalCandidate("p2x-18", 8, 12.37383166832238, 1.409836844353237, 0.2501259165784843, 0.5208549288611282, 0.04581601665129708, 24.0, 100.0),
        ExperimentalCandidate("p2x-19", 10, 16.27818112769727, 1.407424177322464, 0.2653785366528232, 0.5387401387787566, 0.03392468836151121, 24.0, 100.0),
        ExperimentalCandidate("p2x-22", 8, 13.3385853971448, 0.9461482105774807, 0.05166645972007171, 0.6551641590341148, 0.131810790875526, 0.0, 225.0),
        ExperimentalCandidate("p2x-26", 8, 13.29414633337248, 0.9748574564223598, 0.06655377705846031, 0.5966671642673793, 0.125738137893984, 6.0, 225.0),
        ExperimentalCandidate("p2x-29", 6, 8.940271683485772, 1.092054668906148, 0.09001601293581857, 0.5390070802199933, 0.1317058788388756, 12.0, 225.0),
        ExperimentalCandidate("p2x-30", 8, 13.16876504571997, 1.035207204008955, 0.09413851267693957, 0.5373186436898519, 0.1111597492861835, 12.0, 225.0),
        ExperimentalCandidate("p2x-33", 6, 8.769479678643199, 1.200759959391944, 0.1379367095252026, 0.489458786204062, 0.1069346949243851, 18.0, 225.0),
        ExperimentalCandidate("p2x-34", 8, 12.79748494900687, 1.195923605608473, 0.1570236617311228, 0.5081950854757262, 0.07517298188608514, 18.0, 225.0),
        ExperimentalCandidate("p2x-35", 10, 16.64806722816503, 1.26524274227937, 0.1804007436385261, 0.5382395291965313, 0.09283110714197557, 18.0, 225.0),
        ExperimentalCandidate("p2x-37", 6, 8.62110796512553, 1.298550682919995, 0.2053396623803762, 0.4553208156967385, 0.09666113750122174, 24.0, 225.0),
        ExperimentalCandidate("p2x-38", 8, 12.52402502985342, 1.322517679767703, 0.2366333278722356, 0.4949066212395075, 0.04090347614412098, 24.0, 225.0),
        ExperimentalCandidate("p2x-39", 10, 16.33057668138752, 1.382325006479187, 0.2616420808381402, 0.5311770316375933, 0.03348696307362355, 24.0, 225.0),
    )

    private fun bateman(t: Double, absorption: Double, elimination: Double): Double {
        if (t == 0.0) return 0.0
        val delta = abs(absorption - elimination)
        if (delta < 1e-9 * max(absorption, elimination)) return absorption * t * exp(-elimination * t)
        return absorption * exp(-minOf(absorption, elimination) * t) * (-expm1(-delta * t)) / delta
    }

    // Regularized lower incomplete gamma for integer n, as Poisson upper tail.
    // n<=20; z>=100 is safely indistinguishable from 1 at Double precision.
    private fun poissonUpperTail(n: Int, z: Double): Double {
        if (z == 0.0) return 0.0
        if (z >= 100.0) return 1.0
        var factorialLog = 0.0
        for (i in 2..n) factorialLog += ln(i.toDouble())
        var term = exp(n * ln(z) - z - factorialLog)
        var sum = term
        for (i in n + 1..n + 500) {
            term *= z / i
            sum += term
            if (term < sum * 1e-15) break
        }
        return sum.coerceIn(0.0, 1.0)
    }

    private fun raw(t: Double, c: ExperimentalCandidate): Double {
        if (t == 0.0) return 0.0
        val d = c.fastRatePerHour - c.eliminationRatePerHour
        val fast = exp(c.transitStages * ln(c.fastRatePerHour / d) - c.eliminationRatePerHour * t) *
            poissonUpperTail(c.transitStages, d * t)
        val slow = bateman(t, c.slowRatePerHour, c.eliminationRatePerHour)
        return (1.0 - c.slowWeight) * fast + c.slowWeight * slow
    }

    /** h(t)/h(1 hour); positive model shape, dimensionless. */
    fun relativeIncrement(tHours: Double, candidate: ExperimentalCandidate): Double {
        require(tHours.isFinite() && tHours >= 0.0)
        val reference = raw(1.0, candidate)
        require(reference.isFinite() && reference > 0.0)
        val result = raw(tHours, candidate) / reference
        require(result.isFinite() && result >= 0.0) { "Out of supported numeric range" }
        return result
    }

    /** Analytic area of h(t)/h(1) from zero to infinity, expressed in hours.
     * Both unit-mass input paths have area 1/k_elim before 1h normalization.
     * This is a mathematical integral, NOT a measured systemic exposure or human AUC.
     */
    fun aucInfinityRelativeHours(candidate: ExperimentalCandidate): Double {
        val hOne = raw(1.0, candidate)
        require(hOne.isFinite() && hOne > 0.0)
        val area = 1.0 / (candidate.eliminationRatePerHour * hOne)
        require(area.isFinite() && area > 0.0)
        return area
    }

    /** Sum of dose-mg-weighted normalized shape increments, NOT pg/mL. */
    fun relativeHistory(atHour: Double, doses: List<ExperimentalDose>, candidate: ExperimentalCandidate): Double {
        require(atHour.isFinite() && doses.size <= 10_000)
        var sum = 0.0
        for (dose in doses) if (dose.atHour <= atHour) {
            sum += dose.mg * relativeIncrement(atHour - dose.atHour, candidate)
        }
        require(sum.isFinite())
        return sum
    }

    /** Returns observed-candidate min/median/max (NOT a probability interval). */
    fun spread(atHour: Double, doses: List<ExperimentalDose>): ExploratoryModelSpread {
        val values = candidates.map { relativeHistory(atHour, doses, it) }.sorted()
        return ExploratoryModelSpread(values.first(), values[values.size / 2], values.last())
    }

    /** Prevent the arbitrary 15-row grid median from hiding Price-baseline sensitivity.
     * These strata are NOT weighted by any real baseline probability distribution.
     */
    fun spreadByAssumedPriceBaseline(atHour: Double, doses: List<ExperimentalDose>): List<ExperimentalBaselineStratum> =
        candidates.groupBy { it.assumedPriceBaselinePgMl }.toSortedMap().map { (baseline, models) ->
            val values = models.map { relativeHistory(atHour, doses, it) }
            ExperimentalBaselineStratum(baseline, models.size, values.min(), values.max())
        }

    /** Each series uses ONE consistent candidate across the whole time axis.
     * Do not join pointwise min/median/max into a purported model trajectory.
     */
    fun coherentCandidateSeries(timeHours: List<Double>, doses: List<ExperimentalDose>): List<ExperimentalCandidateSeries> {
        require(timeHours.size <= 4096 && timeHours.all { it.isFinite() })
        require(timeHours.zipWithNext().all { (earlier, later) -> earlier <= later })
        return candidates.map { candidate ->
            ExperimentalCandidateSeries(candidate.id, timeHours.map { t -> relativeHistory(t, doses, candidate) })
        }
    }

    /** Explicit study-only reconstruction with caller-controlled anchor and units. */
    fun studyScenario(
        atHour: Double,
        doses: List<ExperimentalDose>,
        candidate: ExperimentalCandidate,
        anchor: ExperimentalStudyAnchor,
    ): Double = anchor.background + anchor.oneMgIncrementAtOneHour * relativeHistory(atHour, doses, candidate)
}
