package net.plainnotes.app.pk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Product-specific RCP mean *increments* of serum T, not total T, not independent
 * clinical validation. No application UI is yet authorized to display these models
 * as if they were absolute measured hormone levels.
 */
class OfficialTestosteroneGelModelsTest {
    private fun meanDaily(m: FittedModel, doseMg: Double): Double =
        Curves.area(0.0, 24.0, step = 0.05) { hour ->
            (0 until 90).sumOf { day -> m.response(hour + day * 24.0, doseMg, 70.0) }
        } / 24.0

    @Test fun separateProductsKeepTheirOwnReferenceAndUnit() {
        val a = PkParams.model("T_GEL_ANDROGEL_1PCT")
        val b = PkParams.model("T_GEL_ANDROGEL_1P62")
        assertEquals("ng/mL", a.unit)
        assertEquals("ng/mL", b.unit)
        assertTrue(a.illustrative && b.illustrative)
        assertFalse(a.noProductData || b.noProductData)
        assertTrue(a.basis.any { it.contains("fr_ANSM_20260904_ANDROGEL_50") })
        assertTrue(b.basis.any { it.contains("fr_ANSM_20260204_ANDROGEL_162") })
        assertTrue(a.assumption.contains("NOT total serum T"))
        assertTrue(b.assumption.contains("NOT total serum T"))
        assertEquals(
            "https://base-donnees-publique.medicaments.gouv.fr/medicament/64205548/extrait",
            PkParams.reference("fr_ANSM_20260904_ANDROGEL_50")?.getString("url")
        )
    }

    @Test fun fittedDailyMeanIncrementMatchesProductSpecificFrenchRcp() {
        // Fit targets, not independently held-out observations or dose advice.
        val original = PkParams.model("T_GEL_ANDROGEL_1PCT")
        val concentrated = PkParams.model("T_GEL_ANDROGEL_1P62")
        assertTrue(abs(meanDaily(original, 50.0) - 2.5) < 0.03)
        assertTrue(abs(meanDaily(concentrated, 40.5) - 2.2) < 0.03)
        assertTrue(abs(meanDaily(original, 50.0) - meanDaily(concentrated, 50.0)) > 0.1)
    }

    @Test fun washoutAndFiniteDomainAreScenarioAssumptions() {
        for (key in listOf("T_GEL_ANDROGEL_1PCT", "T_GEL_ANDROGEL_1P62")) {
            val m = PkParams.model(key)
            val peak = Curves.peak(24.0) { m.response(it, 50.0, 70.0) }.first
            assertTrue(peak > 0)
            assertTrue(m.response(96.0, 50.0, 70.0) / peak < 0.01)
            assertTrue(m.response(10_000.0, 50.0, 70.0).let { it.isFinite() && it >= 0 })
        }
    }
}
