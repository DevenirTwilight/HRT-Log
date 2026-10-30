package net.plainnotes.app.pk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * French ANSM §5.2 label anchors; these are GROUP means, not individual model accuracy claims.
 * Estreva anchors are used for fitting, so the assertions are fit-reproduction tests, NOT
 * independent scientific validation. See docs/pk-research/france-official-rcp-coverage-2026-10-30.md.
 */
class FrenchRcpModelTest {
    private val estreva = "https://base-donnees-publique.medicaments.gouv.fr/medicament/63216075/extrait"

    @Test fun estrevaSourceAndStatusAreTraceable() {
        val model = PkParams.model("E2_GEL_ESTREVA")
        assertEquals("pg/mL", model.unit)
        assertTrue(model.illustrative, "Sparse, baseline-unknown RCP mean anchors must be visibly illustrative")
        assertFalse(model.noProductData)
        assertTrue(model.basis.any { it.contains("ESTREVA") })
        assertEquals(estreva, PkParams.reference("fr_ANSMS_20260706_ESTREVA")?.getString("url"))
    }

    @Test fun estrevaRcpSummaryAnchorsUnderStatedZeroBaselineAssumption() {
        val m = PkParams.model("E2_GEL_ESTREVA")
        val dose = 1.5
        val weight = 70.0
        // Fitted to official label group-mean single peak ≈ 40 pg/mL.
        val single = Curves.peak(72.0) { m.response(it, dose, weight) }.first
        assertTrue(abs(single - 40.0) < 3.0, "Estreva group-mean single 1.5 mg peak (ANSM §5.2)")
        val last = 21 * 24.0
        fun repeated(t: Double) = (0..21).sumOf { k -> m.response(t - k * 24.0, dose, weight) }
        // The official RCP calls day-22 peak ~70 and 24h after last daily dose ~40.
        val peak = Curves.peak(24.0, step = 0.1) { repeated(last + it) }.first
        assertTrue(abs(peak - 70.0) < 6.0, "Estreva day-22 peak; source is a fit target")
        assertTrue(abs(repeated(last + 24.0) - 40.0) < 6.0, "Estreva 24h concentration; fit target")
        // Four days is approximately at steady-state, not a new independent data point.
        fun atDay(n: Int, t: Double) = (0 until n).sumOf { k -> m.response((n - 1 - k) * 24.0 + t, dose, weight) }
        val day4 = Curves.peak(24.0, step = 0.1) { atDay(4, it) }.first
        assertTrue(day4 / peak > 0.90, "Label reports steady state reached by day four")
    }

    @Test fun dispatchAndUnknownProductsNeverShareFalseEvidence() {
        fun event(id: Int): DoseEvent = DoseEvent(
            "gel", Route.GEL, 0.0, 1.5, Ester.E2, 70.0, DoseExtras(gelProductId = id.toDouble())
        )
        val known = Engine.choose(event(2)) as ModelChoice.Use
        assertEquals("E2_GEL_ESTREVA", known.parts.single().second.key)
        assertTrue(CurveFlag.ILLUSTRATIVE in known.flags)
        assertFalse(CurveFlag.NO_PRODUCT_DATA in known.flags)
        val unknown = Engine.choose(event(5)) as ModelChoice.Use
        assertEquals("E2_GEL_OTHER", unknown.parts.single().second.key)
        assertTrue(CurveFlag.NO_PRODUCT_DATA in unknown.flags)
    }

    @Test fun estrogelAndDivigelRemainUnchanged() {
        for ((id, key) in listOf(1 to "E2_GEL_ESTROGEL", 3 to "E2_GEL_ESTROGEL", 4 to "E2_GEL_DIVIGEL")) {
            val ev = DoseEvent("$id", Route.GEL, 0.0, 1.5, Ester.E2, 70.0, DoseExtras(gelProductId = id.toDouble()))
            val choice = Engine.choose(ev) as ModelChoice.Use
            assertEquals(key, choice.parts.single().second.key)
        }
    }
}
