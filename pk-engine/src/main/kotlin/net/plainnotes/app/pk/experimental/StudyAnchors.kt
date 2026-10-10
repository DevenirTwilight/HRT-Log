package net.plainnotes.app.pk.experimental

/**
 * Literature-anchored concentration SCENARIOS for M2 (REQUIREMENTS §57): C(t) = B + A · Σ Dᵢ · f(t − tᵢ).
 * The pg/mL unit comes from a verified study anchor, never from the normalized curve. These are reconstructions of a
 * published group mean under stated assumptions, NOT a personal serum estradiol prediction.
 */
enum class AnchorStatus { AVAILABLE, INSUFFICIENT_EVIDENCE }

/** Why an anchor exists or does not, for display and audit. */
class StudyAnchorEvidence(
    val studyId: String,
    val status: AnchorStatus,
    val citation: String,
    val population: String,
    val unit: String?,
    /** What is missing when [status] is INSUFFICIENT_EVIDENCE. */
    val missing: String?,
)

/**
 * Price TM et al. Obstet Gynecol 1997;89:340–345 (DOI 10.1016/S0029-7844(96)00513-3), Figure 1, 1 mg micronized 17β-E2
 * sublingual, n = 6 postmenopausal women (crossover). Group means manually digitized in P2-U/P2-I (not author data).
 *
 * Amplitude: for a candidate's fixed assumed background B, the frozen P2-X rule
 * A = Hᵀ P (y − B) / (Hᵀ P H), P = diag(1 / max(readWidthᵢ, 0.15·yᵢ)²), clipped at 0
 * (tools/pk-research/p2x_reproduce_standalone.py `best_local_amplitude`). Tests check that A·∫₀²⁴H reproduces the frozen
 * `Price_model_auc` column of docs/pk-research/p2/p2x-frozen-40-candidates.csv.
 */
object PriceFigure1997 {
    const val STUDY_ID = "Price1997_Figure1_1mgSL"
    const val UNIT = "pg/mL"
    val evidence = StudyAnchorEvidence(
        STUDY_ID, AnchorStatus.AVAILABLE,
        "Price TM et al., Obstet Gynecol 1997;89:340–345, Figure 1 (1 mg sublingual micronized estradiol)",
        "6 postmenopausal women, group mean, manually digitized from the printed figure", UNIT, null,
    )
    /** Hours after the 1 mg dose of the digitized Figure 1 points (0 h is not plotted in the figure). */
    val hours = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 18.0, 24.0)
    /** Digitized group means, pg/mL (docs/pk-research/p2/p2i-price-figure-points.json, P2-X SOURCES). */
    val means = doubleArrayOf(450.0, 225.0, 116.0, 85.0, 56.0, 45.0, 34.0, 25.0, 24.0)
    /** Reading widths used by the frozen fit, pg/mL; a reading-uncertainty assumption, not the figure's SD. */
    val readWidths = doubleArrayOf(15.0, 32.5, 12.0, 8.5, 7.0, 6.5, 6.0, 5.5, 5.5)
    const val DISCREPANCY_FLOOR = 0.15
    /** Table 1, 1 mg SL, per-subject predose-subtracted trapezoid AUC 0–24 h, mean ± SD (pg·h/mL). */
    const val TABLE1_AUC_0_24 = 2109.0
    const val TABLE1_AUC_0_24_SD = 1031.0
    /** Table 1, 1 mg SL Cmax mean ± SD (pg/mL). */
    const val TABLE1_CMAX = 451.0

    /** Frozen-rule amplitude: the candidate's 1 mg, 1 h increment above its assumed background, pg/mL. */
    fun amplitude(candidate: ExperimentalCandidate): Double {
        val b = candidate.assumedPriceBaselinePgMl
        var numerator = 0.0; var denominator = 0.0
        for (i in hours.indices) {
            val h = ResearchSublingualV01.relativeIncrement(hours[i], candidate)
            val sigma = maxOf(readWidths[i], DISCREPANCY_FLOOR * kotlin.math.abs(means[i]))
            val w = 1.0 / (sigma * sigma)
            numerator += w * h * (means[i] - b); denominator += w * h * h
        }
        require(denominator > 0.0)
        return maxOf(0.0, numerator / denominator)
    }

    fun anchor(candidate: ExperimentalCandidate) = ExperimentalStudyAnchor(STUDY_ID, UNIT, candidate.assumedPriceBaselinePgMl, amplitude(candidate))

    /** Model area above the assumed background for one 1 mg dose, 0–24 h (1201-point trapezoid, as the frozen P2-X fit). */
    fun incrementalAuc0to24(candidate: ExperimentalCandidate): Double {
        val a = amplitude(candidate); var sum = 0.0; var previous = 0.0
        for (i in 1..1200) {
            val t = 24.0 * i / 1200
            val v = a * ResearchSublingualV01.relativeIncrement(t, candidate)
            sum += (v + previous) / 2.0 * (24.0 / 1200); previous = v
        }
        return sum
    }

    /** Trapezoid of the digitized means over 0–24 h with an ASSUMED 0 pg/mL at 0 h (the figure does not plot 0 h). */
    fun figureAuc0to24AssumingZeroPredose(): Double {
        var sum = (0.0 + means[0]) / 2.0 * hours[0]
        for (i in 1 until hours.size) sum += (means[i] + means[i - 1]) / 2.0 * (hours[i] - hours[i - 1])
        return sum
    }
}

object StudyAnchors {
    /** Anchors that were checked and why the others are refused. No anchor is invented for refused studies. */
    val catalog: List<StudyAnchorEvidence> = listOf(
        PriceFigure1997.evidence,
        StudyAnchorEvidence("Doll2022_1mg_1h", AnchorStatus.INSUFFICIENT_EVIDENCE,
            "Doll et al. 2022, Endocr Pract (DOI 10.1016/j.eprac.2021.11.081), sublingual, 1 mg value at 1 h",
            "10 transgender women, single time point", "pg/mL",
            "One time point with unknown pre-dose baseline: background and amplitude cannot be separated, and the frozen M2 candidates were not fitted to it."),
        StudyAnchorEvidence("Rosano1997_PK25", AnchorStatus.INSUFFICIENT_EVIDENCE,
            "Rosano et al. 1997 (DOI 10.1161/01.cir.96.9.2837), sublingual, 10–60 min",
            "25-person PK group, group means", "pmol/L",
            "Only 0–1 h after dosing and an unknown baseline (profiled up to a cap), so no verified amplitude or background for later times."),
        StudyAnchorEvidence("Komesaroff1998_n10", AnchorStatus.INSUFFICIENT_EVIDENCE,
            "Komesaroff et al. 1998 (DOI 10.1210/jcem.83.7.4945), sublingual, 0–30 min",
            "10 participants, group means", "pmol/L",
            "Only 0–30 min after dosing; amplitude at 1 h is an extrapolation and the baseline is profiled, not measured."),
    )

    /** Verified anchor for [studyId] and [candidate], or null when the evidence is insufficient (no absolute values then). */
    fun anchor(studyId: String, candidate: ExperimentalCandidate): ExperimentalStudyAnchor? =
        if (studyId == PriceFigure1997.STUDY_ID) PriceFigure1997.anchor(candidate) else null

    /** C(t) = B + A · relativeHistory(t) for a verified anchor; null for any study without one. */
    fun scenario(studyId: String, atHour: Double, doses: List<ExperimentalDose>, candidate: ExperimentalCandidate): Double? =
        anchor(studyId, candidate)?.let { ResearchSublingualV01.studyScenario(atHour, doses, candidate, it) }
}
