package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.ui.MedicalRecordsScreen
import net.plainnotes.app.ui.ReferenceCenterScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Skeleton UI keeps real routes functional but must not issue writes or imply official verification.
 * Does not need live records, external data, file access, network access, or PK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CenterSkeletonUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private val med = MedicationEntity(
        id = 501, name = "Synthetic example", molecule = "OTHER", unit = "MG",
        dose_per_intake = 1.0, container_capacity = 20.0, site_rotation = false,
        notifications_on = false, active = true, sort_order = 1
    )

    @Test fun medicineCenterUsesExistingRoutes() {
        var medRoute = 0
        var stockRoute = 0
        var referenceRoute = 0
        ui.setContent {
            MaterialTheme {
                ReferenceCenterScreen(listOf(med), { medRoute++ }, { referenceRoute++ }, { stockRoute++ }, PaddingValues())
            }
        }
        ui.onNodeWithTag("reference-center").performScrollToNode(hasTestTag("reference-center-medications"))
        ui.onNodeWithTag("reference-center-medications").performClick()
        ui.onNodeWithTag("reference-center").performScrollToNode(hasTestTag("reference-center-stock"))
        ui.onNodeWithTag("reference-center-stock").performClick()
        ui.onNodeWithTag("reference-center").performScrollToNode(hasTestTag("reference-center-item:501"))
        ui.onNodeWithTag("reference-center-item:501").performClick()
        ui.runOnIdle {
            assertEquals(1, medRoute)
            assertEquals(1, stockRoute)
            assertEquals(1, referenceRoute)
        }
    }

    @Test fun plannedMedicineModuleOpensOnlyAnExplanation() {
        var actions = 0
        ui.setContent {
            MaterialTheme {
                ReferenceCenterScreen(emptyList(), { actions++ }, { actions++ }, { actions++ }, PaddingValues())
            }
        }
        ui.onNodeWithTag("reference-center").performScrollToNode(hasTestTag("reference-center-documents"))
        ui.onNodeWithTag("reference-center-documents").performClick()
        ui.onNodeWithTag("skeleton-unavailable-dialog").assertExists()
        ui.onNodeWithTag("skeleton-dialog-close").performClick()
        ui.onNodeWithTag("skeleton-unavailable-dialog").assertDoesNotExist()
        ui.runOnIdle { assertEquals(0, actions) }
    }

    @Test fun medicalCenterPreservesVisitsAndKeepsOriginalFilesUnavailable() {
        var visits = 0
        var others = 0
        ui.setContent {
            MaterialTheme {
                MedicalRecordsScreen(
                    emptyList(), {}, {}, { others++ }, { others++ }, { others++ },
                    PaddingValues(), onVisits = { visits++ }
                )
            }
        }
        ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-visit-list"))
        ui.onNodeWithTag("medical-visit-list").performClick()
        ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-future-documents"))
        ui.onNodeWithTag("medical-future-documents").performClick()
        ui.onNodeWithTag("skeleton-unavailable-dialog").assertExists()
        ui.onNodeWithTag("skeleton-dialog-close").performClick()
        ui.runOnIdle {
            assertEquals(1, visits)
            assertEquals(0, others)
        }
    }
}
