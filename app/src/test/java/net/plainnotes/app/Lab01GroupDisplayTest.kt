package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * LAB-01 C/D, synthetic results only: one card per draw (identical sampled_utc and identical trimmed laboratory, both empty
 * counts as equal), never merged by nearby dates or different laboratories; a single result now uses the same card; a row reads
 * "test, value, unit" and says high/low in words; editing the time or laboratory of a grouped result shows the split hint.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Lab01GroupDisplayTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private val t1 = Instant.parse("2026-03-10T08:30:00Z").toEpochMilli()
    private val t2 = Instant.parse("2026-02-01T09:00:00Z").toEpochMilli()
    private fun lab(id: Long, code: String, v: Double, t: Long, lab: String?, lo: Double? = null, hi: Double? = null, unit: String = analyte(code).units.first().first, refUnit: String? = if (lo != null || hi != null) unit else null) =
        LabValueEntity(id, code, v, unit, t, "UTC", lo, hi, refUnit, lab)
    private val labs = listOf(
        lab(1, "E2", 250.0, t1, "Synthetic lab A", 100.0, 200.0), lab(2, "T", 20.0, t1, " Synthetic lab A "), lab(3, "PRL", 3.0, t1, "Synthetic lab A", 4.0, 25.0),
        lab(4, "E2", 140.0, t1, "Synthetic lab B"), // same time, other laboratory: its own (single) row
        lab(5, "E2", 120.0, t2, null), lab(6, "T", 25.0, t2, "  "), // both without laboratory: one card
        lab(7, "E2", 130.0, t2 + 60_000, null), // one minute later: not merged
    )

    @Test fun groupingRule() {
        val groups = labGroups(labs).map { g -> g.map { it.id } }
        assertEquals(listOf(listOf(4L), listOf(1L, 2L, 3L), listOf(7L), listOf(5L, 6L)), groups) // newest first; same time, other laboratory stays apart
    }

    @Test fun cardsForDrawsSingleRowsOtherwise() {
        var opened: LabValueEntity? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(labs, emptyList(), { opened = it }, {}, PaddingValues()) } }
        ui.onNodeWithTag("lab-filter-all").assertIsSelected()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group:$t1:Synthetic lab A"))
        ui.onNodeWithTag("lab-group:$t1:Synthetic lab A").assertExists()
        ui.onNode(hasTestTag("lab-group-title") and hasAnyAncestor(hasTestTag("lab-group:$t1:Synthetic lab A")))
            .assertTextContains(s(R.string.lab_group_count, 3), substring = true).assertTextContains("Synthetic lab A", substring = true)
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group-row:4"))
        ui.onNodeWithTag("lab-group-row:4").assertContentDescriptionEquals(s(R.string.lab_row_spoken, s(R.string.lab_e2), "140", "pg/mL")) // original value remains accessible in the unified singleton card
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group:$t2:"))
        ui.onNode(hasTestTag("lab-group-title") and hasAnyAncestor(hasTestTag("lab-group:$t2:"))).assertTextContains(s(R.string.lab_group_count, 2), substring = true)
        assertTrue(labDraws(labs, emptyList()).all { it.panel == null }) // rendering never creates formal panels
        assertEquals(2, labGroups(labs).count { it.size > 1 })
    }

    @Test fun rowsReadTestValueUnitAndSayHighOrLowInWords() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(labs, emptyList(), {}, {}, PaddingValues()) } }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group-row:1"))
        ui.onNodeWithTag("lab-group-row:1").assertContentDescriptionEquals(s(R.string.lab_row_spoken, s(R.string.lab_e2), "250", "pg/mL") + s(R.string.lab_row_spoken_flag, s(R.string.lab_flag_high)))
            .assertHasClickAction()
        ui.onNodeWithTag("lab-group-row:2").assertContentDescriptionEquals(s(R.string.lab_row_spoken, s(R.string.lab_t), "20", "ng/dL"))
        ui.onNodeWithTag("lab-flag:1", useUnmergedTree = true).assertTextEquals(s(R.string.lab_flag_high))
        ui.onNodeWithTag("lab-flag:3", useUnmergedTree = true).assertTextEquals(s(R.string.lab_flag_low))
        ui.onNodeWithTag("lab-flag:2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun flagsCompareInOneUnit() {
        assertEquals(LabFlag.HIGH, labFlag(lab(1, "E2", 800.0, t1, null, 100.0, 200.0, unit = "pmol/L", refUnit = "pg/mL"))) // 800 pmol/L ≈ 218 pg/mL
        assertNull(labFlag(lab(1, "E2", 600.0, t1, null, 100.0, 200.0, unit = "pmol/L", refUnit = "pg/mL"))) // ≈ 163 pg/mL
        assertNull(labFlag(lab(1, "E2", 600.0, t1, null, 100.0, 200.0, unit = "pmol/L", refUnit = "unknown")))
        assertEquals(LabFlag.LOW, labFlag(lab(1, "E2", 50.0, t1, null, 100.0, null)))
    }

    @Test fun aRowOpensTheExistingDetailThenEdit() {
        var edited: LabValueEntity? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(labs, emptyList(), { edited = it }, {}, PaddingValues()) } }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group-row:2"))
        ui.onNodeWithTag("lab-group-row:2").performClick()
        ui.onNode(isDialog()).assertExists()
        ui.onNode(hasClickAction() and hasText(s(R.string.edit)) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(labs[1], edited)
        ui.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun anAnalyteChipStillShowsItsTrendAndRows() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(labs, emptyList(), {}, {}, PaddingValues()) } }
        ui.onNodeWithText(s(R.string.lab_e2)).performClick()
        ui.onNodeWithText(s(R.string.lab_trend, s(R.string.lab_e2))).assertExists()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group:$t1:Synthetic lab A"))
        ui.onNodeWithTag("lab-group:$t1:Synthetic lab A").assertExists() // metric filters retain unified cards
    }

    @Test fun editingTimeOrLabOfAGroupedResultShowsTheHintAndItSplitsOut() {
        var inGroup by mutableStateOf(true)
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabDialog(labs[1], {}, {}, inGroup = inGroup) } }
        ui.onNodeWithTag("lab-split-hint").assertDoesNotExist()
        ui.onNode(hasSetTextAction() and hasText(s(R.string.lab_laboratory))).performScrollTo().performTextClearance()
        ui.onNodeWithTag("lab-split-hint").assertTextEquals(s(R.string.lab_group_split_hint))
        ui.onNode(hasClickAction() and hasText(s(R.string.save))).assertIsEnabled() // a hint, not a block
        inGroup = false; ui.waitForIdle()
        ui.onNodeWithTag("lab-split-hint").assertDoesNotExist()
        // Saved with another time, it is shown on its own.
        val moved = labs.map { if (it.id == 2L) it.copy(sampled_utc = t1 + 3_600_000) else it }
        assertEquals(listOf(1L, 3L), labGroups(moved).first { g -> g.any { it.id == 1L } }.map { it.id })
        assertTrue(labGroups(moved).any { it.map { l -> l.id } == listOf(2L) })
    }
}
