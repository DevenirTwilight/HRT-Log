package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * B4 check stock and packages UI (synthetic data), in four languages at 320 dp with 2× font: a used-up package can be
 * corrected without adding one; several candidates are never preselected; the amount is in the stock unit; package
 * history is grouped and collapsed, shows no internal ids, and only unused packages offer undo.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35])
class StockCheckUiTest(private val locale: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun locales() = listOf("en", "zh-rCN", "zh-rTW", "fr").map { arrayOf<Any>(it) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private val med = MedicationEntity(1, "Synthetic E2", "E2", "ORAL", "MG", 2.0, 28.0, null, 15, 60, false, null, true, true, 0)
    private val usedUp = ContainerEntity(5, 1, 28.0, 0.0, 28.0, "2026-02-01", "EMPTY", source_note = "Synthetic pharmacy", batch = "B-17")
    private val olderUsedUp = ContainerEntity(4, 1, 28.0, 0.0, 28.0, "2026-01-01", "EMPTY")
    private val discarded = ContainerEntity(3, 1, 28.0, 0.0, 10.0, "2025-12-01", "DISCARDED")
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)
    private fun setUp() { RuntimeEnvironment.setQualifiers("$locale-w320dp-h1800dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f) }

    @Test fun usedUpOnlyStillOffersCheckStockNotJustAdd() {
        setUp()
        var checked = 0; var added = 0
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            StockScreen(NotesState(listOf(med), loading = false), listOf(usedUp), emptyList(), {}, { checked++ }, { added++ }, PaddingValues())
        } } }
        ui.onNodeWithText(s(R.string.stock_no_active)).assertExists()
        ui.onNodeWithText(s(R.string.stock_not_tracked)).assertDoesNotExist()
        ui.onNodeWithTag("stock-check-1").assertHasClickAction().performClick()
        assertEquals(1, checked); assertEquals(0, added)
        ui.onNodeWithTag("stock-manage-1").assertExists()
        ui.onAllNodesWithText("#", substring = true).assertCountEquals(0)
    }

    @Test fun severalCandidatesAreNotPreselectedAndTheAmountIsInTheStockUnit() {
        setUp()
        var saved: Pair<Long, Double>? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { CheckStockDialog(med, listOf(olderUsedUp, usedUp, discarded), {}) { id, v -> saved = id to v } } }
        ui.onNodeWithTag("check-package-3").assertDoesNotExist()                       // discarded is never offered
        listOf(4L, 5L).forEach { ui.onNodeWithTag("check-package-$it").assertExists().assertIsNotSelected() }
        ui.onNodeWithText(s(R.string.save)).assertIsNotEnabled()
        ui.onNodeWithTag("check-package-5").performClick()
        ui.onNodeWithTag("check-package-5").assertIsSelected(); ui.onNodeWithTag("check-package-4").assertIsNotSelected()
        ui.onNodeWithText(s(R.string.stock_check_amount)).performTextReplacement("2")
        // Shown in doses of the planned amount, so a tablet count cannot be mistaken for milligrams.
        ui.onNodeWithText(s(R.string.stock_check_doses, "1", "\u0000").substringBefore("\u0000"), substring = true).assertExists()
        ui.onNodeWithText(s(R.string.save)).performClick()
        assertEquals(5L to 2.0, saved)                                                   // one 2 mg tablet = 2.0 mg, not 1
    }

    @Test fun packagesAreGroupedHistoryCollapsedAndOnlyUnusedOnesCanBeUndone() {
        setUp()
        val open = ContainerEntity(6, 1, 28.0, 0.0, 6.0, "2026-03-01", "IN_USE")
        val mistaken = ContainerEntity(7, 1, 28.0, 0.0, 0.0, null, "SEALED")
        var undone: Long? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) {
            PackagesDialog(med, listOf(open, mistaken, usedUp, olderUsedUp, discarded), withHistory = setOf(6L, 5L, 4L, 3L), {}, {}) { undone = it.id }
        } }
        ui.onNodeWithText(s(R.string.pkg_in_use) + " (1)").assertExists(); ui.onNodeWithText(s(R.string.pkg_sealed) + " (1)").assertExists()
        ui.onNodeWithText(s(R.string.pkg_empty) + " (2)").assertExists(); ui.onNodeWithText(s(R.string.pkg_discarded) + " (1)").assertExists()
        ui.onAllNodesWithText("Synthetic pharmacy", substring = true).assertCountEquals(0)  // used-up group collapsed
        ui.onAllNodesWithText(s(R.string.pkg_show, 2)).onFirst().performClick()
        ui.onNodeWithText("Synthetic pharmacy", substring = true).assertExists()
        ui.onAllNodesWithText("#", substring = true).assertCountEquals(0)
        listOf(6L, 5L, 4L).forEach { ui.onNodeWithTag("undo-package-$it").assertDoesNotExist() }
        ui.onNodeWithText(s(R.string.pkg_undo_locked)).assertExists()
        ui.onNodeWithTag("undo-package-7").performScrollTo().performClick()
        assertEquals(7L, undone)
    }
}
