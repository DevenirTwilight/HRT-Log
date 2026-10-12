package net.plainnotes.app.export

import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.SublingualEstimatePolicy
import net.plainnotes.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * D-2 on Android's native PDF writer (Robolectric has none), synthetic data only: a new report for pure sublingual,
 * mixed, other-route-only E2 and no doses is a valid multi-page-capable PDF, and the E2 chart decision matches the rule.
 */
class SublingualPdfAndroidTest {
    private val now = Instant.parse("2026-03-10T12:00:00Z")
    private fun med(id: Long, route: String, pkRoute: String) = MedicationEntity(id, "Synthetic $route", "E2", route, "MG", 2.0, 40.0, null, 15, 60, false, null, true, true, 0) to
        ProfileEntity(id, "E2", pkRoute, sl_tier = 2)
    private fun record(id: Long, med: Pair<MedicationEntity, ProfileEntity>, hoursBefore: Long) = RecordEntity(id, med.first.id, taken_utc = now.minusSeconds(hoursBefore * 3600).toEpochMilli(),
        taken_zone = "UTC", actual_dose = 2.0, status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = MedicationSnapshot.encode(med.first, med.second))

    @Test fun newReportsForEveryRouteMixAreValidAndFollowTheRule() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sl = med(1, "SUBLINGUAL", "sublingual"); val oral = med(2, "ORAL", "oral")
        val cases = listOf(
            listOf(sl) to SublingualEstimatePolicy.PdfE2.SUBLINGUAL_UNAVAILABLE,
            listOf(sl, oral) to SublingualEstimatePolicy.PdfE2.MIXED_UNAVAILABLE,
            listOf(oral) to SublingualEstimatePolicy.PdfE2.DRAW,
            emptyList<Pair<MedicationEntity, ProfileEntity>>() to SublingualEstimatePolicy.PdfE2.NONE)
        for ((meds, expected) in cases) {
            val records = meds.mapIndexed { i, m -> record(i + 1L, m, 3L + 2 * i) }
            val result = ConcentrationCalculator.compute(meds.map { it.first }, meds.associate { it.first.id to it.second }, records, emptyList(), emptyList(), 70.0, now, calibrate = false)
            assertEquals(expected, SublingualEstimatePolicy.pdfE2(result, records))
            val data = ExportData(meds.map { it.first }, meds.associate { it.first.id to it.second }, records, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), { "" })
            val out = ByteArrayOutputStream(); PdfReport.write(context, data, 30, result, out)
            assertEquals("%PDF-", String(out.toByteArray().copyOfRange(0, 5)))
        }
    }
}
