package net.plainnotes.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.experimental.ExperimentalPkScreen
import net.plainnotes.app.ui.NotesTheme
import java.time.Instant

/**
 * DEBUG APK ONLY: hosts the real opt-in experimental page with SYNTHETIC records (never the database),
 * so the emulator can exercise it while the release entry point waits for a P2 protocol decision.
 */
class ExperimentalPkPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val records = syntheticRecords(Instant.now())
        setContent { NotesTheme { Scaffold { pad -> ExperimentalPkScreen(records, emptyMap(), pad, onBackToConcentration = { finish() }) } } }
    }

    companion object {
        /** Irregular sublingual E2 doses plus one oral E2 dose that the page must ignore. */
        fun syntheticRecords(now: Instant): List<RecordEntity> {
            val sl = MedicationEntity(1, "Synthetic SL E2", "E2", "SUBLINGUAL", "MG", 1.0, 30.0, null, 15, 120, false, null, true, true, 0)
            val oral = sl.copy(id = 2, name = "Synthetic oral E2", route = "ORAL")
            val slSnap = MedicationSnapshot.encode(sl, ProfileEntity(1, "E2", "sublingual", sl_tier = 2))
            val oralSnap = MedicationSnapshot.encode(oral, ProfileEntity(2, "E2", "oral"))
            fun r(id: Long, hoursAgo: Double, dose: Double, snap: String = slSnap, med: Long = 1) = RecordEntity(id, med,
                taken_utc = now.toEpochMilli() - (hoursAgo * 3_600_000).toLong(), taken_zone = "UTC", actual_dose = dose,
                status = "ON_TIME", origin = "APP", revision = 1, config_snapshot = snap)
            return listOf(r(1, 46.0, 1.0), r(2, 38.5, 0.5), r(3, 30.0, 1.0), r(4, 22.0, 1.0), r(5, 13.5, 0.5), r(6, 6.0, 1.0), r(7, 2.0, 2.0, oralSnap, 2))
        }
    }
}
