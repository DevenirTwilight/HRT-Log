package net.plainnotes.app.conc

import net.plainnotes.app.pk.Curve
import org.json.JSONArray
import org.json.JSONObject

/**
 * Owner decision D-2 (REQUIREMENTS §67 item 5, option D2-2): new PDFs and newly saved lab estimates never carry the
 * retired Legacy sublingual E2 prediction. The individual sublingual E2 estimate is unavailable and says why;
 * other-route-only E2 and the other compounds keep their existing, separately supported estimates, and mixed routes
 * never produce a combined total E2. Frozen estimates (calculator_version 1/2) and saved records are never rewritten.
 *
 * The stored estimate keeps its existing JSON format: only the E2 entry is left out, which the validator already
 * accepts, so no schema or backup format changes.
 */
object SublingualEstimatePolicy {
    /** An input record that is, or cannot be ruled out as, sublingual E2: unknown context never counts as "not SL". */
    private fun sublingualOrUnknownE2(input: JSONObject): Boolean {
        val snapshot = input.optJSONObject("input_snapshot") ?: return true
        val molecule = snapshot.optString("molecule", "").takeUnless { snapshot.isNull("molecule") } ?: ""
        if (molecule.isEmpty()) return true
        if (molecule != "E2") return false
        val route = snapshot.optString("route", "").takeUnless { snapshot.isNull("route") } ?: ""
        val pkRoute = snapshot.optJSONObject("pk_profile")?.let { if (it.isNull("pk_route")) "" else it.optString("pk_route", "") } ?: ""
        return route.isEmpty() || route == "SUBLINGUAL" || pkRoute == "sublingual"
    }

    /** True when the estimate's own inputs contain sublingual E2 or an E2-relevant record of unknown route. */
    fun withholdsE2(estimate: JSONObject): Boolean {
        val inputs = estimate.optJSONArray("inputs") ?: return false
        return (0 until inputs.length()).any { sublingualOrUnknownE2(inputs.getJSONObject(it)) }
    }

    /** New captures only: drops the total-E2 entry when [withholdsE2]; every other field and value is kept byte-for-byte. */
    fun apply(captured: String): String {
        val estimate = JSONObject(captured)
        if (!withholdsE2(estimate)) return captured
        val values = estimate.getJSONArray("values")
        val kept = JSONArray()
        for (i in 0 until values.length()) values.getJSONObject(i).let { if (it.getString("curve") != Curve.E2.name) kept.put(it) }
        return estimate.put("values", kept).toString()
    }

    /**
     * Display: a saved estimate without E2 whose inputs need the retired sublingual model shows why. Frozen estimates
     * that contain their E2 value are shown as recorded. (A missing input such as weight for another compound does
     * not explain a missing E2, so it never hides this line.)
     */
    fun showsUnavailable(estimate: JSONObject): Boolean {
        val values = estimate.optJSONArray("values") ?: return false
        val hasE2 = (0 until values.length()).any { values.getJSONObject(it).optString("curve") == Curve.E2.name }
        return !hasE2 && withholdsE2(estimate)
    }

    enum class PdfE2 { DRAW, SUBLINGUAL_UNAVAILABLE, MIXED_UNAVAILABLE, NONE }

    /**
     * New PDF: the Legacy E2 chart is drawn only when no sublingual E2 model contributes to it. "Mixed" is read from the
     * records (an E2 dose with a known non-sublingual route), not from model keys: the Legacy sublingual model itself
     * includes a swallowed oral fraction, so E2_ORAL appears even for sublingual-only dosing.
     */
    fun pdfE2(result: ConcentrationResult?, records: List<net.plainnotes.app.data.RecordEntity> = emptyList()): PdfE2 {
        if (result == null || result.timeH.isEmpty()) return PdfE2.NONE
        val e2 = result.models[Curve.E2].orEmpty()
        if (e2.isEmpty()) return PdfE2.NONE
        if (e2.none { it.key == "E2_SL" }) return PdfE2.DRAW
        val otherRoute = records.any { r -> runCatching { JSONObject(r.config_snapshot) }.getOrNull()?.let { s ->
            s.optString("molecule") == "E2" && !s.isNull("route") && s.optString("route").let { it.isNotEmpty() && it != "SUBLINGUAL" } } == true }
        return if (otherRoute) PdfE2.MIXED_UNAVAILABLE else PdfE2.SUBLINGUAL_UNAVAILABLE
    }
}
