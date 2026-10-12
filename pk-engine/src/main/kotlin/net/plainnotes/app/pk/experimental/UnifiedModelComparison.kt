package net.plainnotes.app.pk.experimental

import net.plainnotes.app.pk.DoseEvent
import net.plainnotes.app.pk.DoseExtras
import net.plainnotes.app.pk.Ester
import net.plainnotes.app.pk.Route
import net.plainnotes.app.pk.Pk

enum class ResearchDisplay { CONDITIONAL, STUDY, RELATIVE }
enum class ResearchModel { M2, M1, LEGACY }

/** Shared, explicit calibration. Never inferred from personal labs or dosage. */
data class CommonCalibration(val backgroundPgMl: Double, val amplitudePgMl: Double) {
    init { require(backgroundPgMl.isFinite() && backgroundPgMl >= 0 && amplitudePgMl.isFinite() && amplitudePgMl > 0) }
    fun concentration(response: Double): Double = (backgroundPgMl + amplitudePgMl * response).also {
        require(it.isFinite() && it >= 0 && (it * Pk.PMOL_PER_PG).isFinite())
    }
}

class UnifiedComparison(
    val base: ModelComparisonResult,
    val m1Candidate: ExperimentalCandidate,
    val display: ResearchDisplay,
    val calibration: CommonCalibration,
    val series: Map<ResearchModel, DoubleArray>,
    val metrics: Map<ResearchModel, ModelMetrics>,
    val m2Range: Pair<DoubleArray, DoubleArray>,
    internal val events: List<DoseEvent>,
) {
    val unit: String get() = if (display == ResearchDisplay.RELATIVE) "relative" else "pg/mL"
    fun factor(pmol: Boolean) = if (pmol && display != ResearchDisplay.RELATIVE) Pk.PMOL_PER_PG else 1.0
}

/** One common dose/time/1mg-at-1h protocol for all three models. Legacy mathematics stays in Engine. */
object UnifiedModelComparison {
    const val calibrationVersion = "common-1mg-1h-v1"
    private fun value(h: Double, display: ResearchDisplay, calibration: CommonCalibration) =
        if (display == ResearchDisplay.RELATIVE) h else calibration.concentration(h)

    private fun convert(m: ModelMetrics, display: ResearchDisplay, c: CommonCalibration): ModelMetrics {
        if (display == ResearchDisplay.RELATIVE) return m
        fun area(h: Double, hours: Double) = (c.backgroundPgMl * hours + c.amplitudePgMl * h).also {
            require(it.isFinite() && it >= 0 && (it * Pk.PMOL_PER_PG).isFinite())
        }
        return ModelMetrics(c.concentration(m.peak), m.peakHoursAfterOrigin,
            area(m.auc0to4, 4.0), area(m.auc0to8, 8.0), area(m.auc0to24, 24.0),
            c.concentration(m.at8h), c.concentration(m.at12h), c.concentration(m.at24h), m.singleDoseTmaxHours)
    }

    private fun m1Metrics(origin: Double, doses: List<ExperimentalDose>, candidate: ExperimentalCandidate): ModelMetrics {
        val grid = DoubleArray(1441) { origin + it / 60.0 }
        val y = grid.map { ResearchSublingualV01.relativeHistory(it, doses, candidate) }
        fun area(end: Int): Double = (1..end).sumOf { (grid[it] - grid[it-1]) * (y[it] + y[it-1]) / 2 }
        val peak = y.indices.maxBy { y[it] }
        // Match the shared M2/Legacy single-dose Tmax search: 0–12 h, one-minute grid.
        val single = (0..720).maxBy { ResearchSublingualV01.relativeIncrement(it / 60.0, candidate) } / 60.0
        return ModelMetrics(y[peak], grid[peak] - origin, area(240), area(480), area(1440), y[480], y[720], y[1440], single)
    }

    fun compute(events: List<DoseEvent>, start: Double, end: Double, origin: Double, m2Id: String, m1Id: String,
                display: ResearchDisplay, calibration: CommonCalibration = CommonCalibration(25.0, 100.0)): UnifiedComparison? {
        val m1 = requireNotNull(ResearchM1Catalog.candidate(m1Id)) { "Unknown M1 version/candidate" }
        val m2 = requireNotNull(ResearchSublingualV01.candidates.singleOrNull { it.id == m2Id }) { "Unknown M2 candidate" }
        // Study mode is a separate cohort scenario. Personal events/origin NEVER enter its kernel or chart.
        val input = if (display == ResearchDisplay.STUDY) listOf(DoseEvent("Price1997-1mg", Route.SUBLINGUAL, 0.0, 1.0, Ester.E2, 70.0, DoseExtras(sublingualTier = 2.0))) else events.toList()
        val studyCalibration = if (display == ResearchDisplay.STUDY)
            CommonCalibration(m2.assumedPriceBaselinePgMl, PriceFigure1997.means[0] - m2.assumedPriceBaselinePgMl) else calibration
        val from = if (display == ResearchDisplay.STUDY) 0.0 else start
        val to = if (display == ResearchDisplay.STUDY) end - start else end
        val at = if (display == ResearchDisplay.STUDY) 0.0 else origin
        val base = ConcentrationModelComparison.compute(input, from, to, at, m2Id) ?: return null
        val doses = base.doses.map { ExperimentalDose(it.first, it.second) }
        val m1Relative = DoubleArray(base.timeHours.size) { ResearchSublingualV01.relativeHistory(base.timeHours[it], doses, m1) }
        fun values(v: DoubleArray) = DoubleArray(v.size) { value(v[it], display, studyCalibration) }
        val series = mapOf(ResearchModel.M2 to values(base.m2), ResearchModel.M1 to values(m1Relative), ResearchModel.LEGACY to values(base.legacy))
        val metrics = mapOf(ResearchModel.M2 to convert(base.m2Metrics, display, studyCalibration),
            ResearchModel.M1 to convert(m1Metrics(at, doses, m1), display, studyCalibration),
            ResearchModel.LEGACY to convert(base.legacyMetrics, display, studyCalibration))
        return UnifiedComparison(base, m1, display, studyCalibration, series, metrics,
            values(base.m2Minimum) to values(base.m2Maximum), input)
    }

    /** Exact kernels, identical immutable input selection and calibration; no chart interpolation. */
    fun valuesAt(result: UnifiedComparison, hour: Double): Map<ResearchModel, Double> {
        require(hour.isFinite())
        val v = requireNotNull(ConcentrationModelComparison.valuesAt(result.events, result.base, hour))
        val doses = result.base.doses.map { ExperimentalDose(it.first, it.second) }
        return mapOf(ResearchModel.M2 to value(v.m2, result.display, result.calibration),
            ResearchModel.M1 to value(ResearchSublingualV01.relativeHistory(hour, doses, result.m1Candidate), result.display, result.calibration),
            ResearchModel.LEGACY to value(v.legacy, result.display, result.calibration))
    }
}
