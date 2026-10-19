package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.LabPanelEntity
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
 * LAB-02 (docs/design/lab-panel-adr.md §10), synthetic data only: real lab panels are cards (one result included, Q7) that
 * open their own detail; results without a panel keep the LAB-01 grouping and offer "save as one blood test" only after a
 * confirmation; the panel detail edits the whole draw, takes results out, adds new ones (no repeated test) and deletes it all.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Lab02PanelUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private val t1 = Instant.parse("2026-03-10T08:30:00Z").toEpochMilli(); private val t2 = Instant.parse("2026-02-01T09:00:00Z").toEpochMilli()
    private val panel = LabPanelEntity(5, "00000000-0000-4000-8000-000000000005", t1, "UTC", "Synthetic lab", "Synthetic fasting note", 1)
    private val solo = LabPanelEntity(6, "00000000-0000-4000-8000-000000000006", t1 - 86_400_000, "UTC", null, null, 1)
    private fun lab(id: Long, code: String, v: Double, t: Long, place: String?, p: Long? = null) =
        LabValueEntity(id, code, v, analyte(code).units.first().first, t, "UTC", laboratory = place, panel_id = p)
    private val labs = listOf(lab(1, "E2", 150.0, t1, "Synthetic lab", 5), lab(2, "T", 30.0, t1, "Synthetic lab", 5), lab(3, "PRL", 12.0, t1 - 86_400_000, null, 6),
        lab(4, "E2", 120.0, t2, "Old lab"), lab(5, "T", 25.0, t2, "Old lab"), lab(6, "E2", 99.0, t2 - 1, null))

    @Test fun panelsAreCardsAndLooseResultsKeepTheLab01Grouping() {
        var opened: LabPanelEntity? = null; var saveAs: List<LabValueEntity>? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabsScreen(labs, emptyList(), {}, {}, PaddingValues(), panels = listOf(panel, solo), onPanel = { opened = it }, onSaveAsPanel = { saveAs = it }) } }
        ui.onNodeWithTag("lab-panel:5").assertExists()
        ui.onNode(hasTestTag("lab-panel-note") and hasAnyAncestor(hasTestTag("lab-panel:5"))).assertTextEquals("Synthetic fasting note")
        ui.onAllNodes(hasTestTag("lab-save-as-panel") and hasAnyAncestor(hasTestTag("lab-panel:5"))).assertCountEquals(0)
        ui.onNode(hasTestTag("lab-panel-open") and hasAnyAncestor(hasTestTag("lab-panel:5"))).performClick()
        assertEquals(panel, opened)
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-panel:6"))
        ui.onNode(hasTestTag("lab-group-title") and hasAnyAncestor(hasTestTag("lab-panel:6")), useUnmergedTree = true).assertTextContains(s(R.string.lab_group_count, 1), substring = true)
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("lab-group:$t2:Old lab"))
        ui.onNode(hasTestTag("lab-save-as-panel") and hasAnyAncestor(hasTestTag("lab-group:$t2:Old lab"))).performScrollTo().performClick()
        assertEquals(listOf(4L, 5L), saveAs?.map { it.id })
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("99 pg/mL"))
        ui.onAllNodes(hasTestTag("lab-group:${t2 - 1}:")).assertCountEquals(0) // a lone result without panel stays a plain row
    }

    @Test fun saveAsOneBloodTestNeedsTheConfirmation() {
        var confirmed = 0; var dismissed = 0
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabSaveAsPanelDialog(labs.subList(3, 5), { dismissed++ }) { confirmed++ } } }
        ui.onNodeWithText(s(R.string.lab_panel_save_as_body)).assertExists()
        ui.onNodeWithText("${s(R.string.lab_e2)} · 120 pg/mL").assertExists()
        assertEquals(0, confirmed)
        ui.onNodeWithTag("lab-save-as-panel-confirm").performClick()
        assertEquals(1, confirmed); assertEquals(0, dismissed)
    }

    @Test fun panelDetailEditsTheWholeDrawTakesOutAddsAndDeletes() {
        val saved = mutableListOf<Triple<java.time.LocalDateTime, String, String>>(); val removed = mutableListOf<LabValueEntity>()
        val added = mutableListOf<Pair<List<LabValueEntity>, Boolean>>(); var deleted = 0; var opened: LabValueEntity? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { LabPanelDialog(panel, labs.take(2), {}, { a, b, c -> saved += Triple(a, b, c) }, { opened = it }, { removed += it },
            { v, e -> added += v to e }, { deleted++ }) } }
        ui.onNodeWithTag("lab-panel-save").assertIsNotEnabled()
        ui.onNodeWithTag("lab-panel-note-field").performScrollTo().performTextClearance()
        ui.onNodeWithTag("lab-panel-note-field").performTextInput("Synthetic new note")
        ui.onNodeWithTag("lab-panel-save").assertIsEnabled().performClick()
        assertEquals("Synthetic new note", saved.single().third); assertEquals("Synthetic lab", saved.single().second)
        ui.onNodeWithTag("lab-group-row:2").performScrollTo().performClick(); assertEquals(labs[1], opened)
        ui.onNodeWithTag("lab-panel-remove:2").performScrollTo().assertContentDescriptionEquals(s(R.string.lab_panel_remove_member, s(R.string.lab_t))).performClick()
        assertEquals(listOf(labs[1]), removed)
        ui.onNodeWithTag("lab-panel-add-row").performScrollTo().performClick() // a new E2 row: E2 is already in this blood test
        ui.onNodeWithTag("lab-batch-value:0").performScrollTo().performTextInput("160")
        ui.onNodeWithTag("lab-batch-duplicate:0").assertExists()
        ui.onNodeWithTag("lab-panel-save-added").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("lab-batch-remove:0").performScrollTo().performClick()
        ui.onNodeWithTag("lab-panel-add-row").performScrollTo().performClick()
        ui.onNodeWithTag("lab-preset:HORMONES").assertDoesNotExist() // presets belong to the new-entry form only
        assertTrue(added.isEmpty())
        ui.onNodeWithTag("lab-panel-delete").performScrollTo().performClick()
        ui.onNodeWithText(s(R.string.lab_panel_delete_confirm, 2)).assertExists()
        ui.onNodeWithTag("lab-panel-delete-confirm").performClick()
        assertEquals(1, deleted)
    }

    @Test fun drawsOrderNewestFirstAndPanelsWinOverTheLab01Rule() {
        val draws = labDraws(labs, listOf(panel, solo))
        assertEquals(listOf(5L, 6L, null, null), draws.map { it.panel?.id })
        assertEquals(listOf(listOf(1L, 2L), listOf(3L), listOf(4L, 5L), listOf(6L)), draws.map { d -> d.labs.map { it.id } })
        // A result naming a panel that is not loaded is shown by the LAB-01 rule, never dropped.
        assertEquals(6, labDraws(labs, emptyList()).sumOf { it.labs.size })
    }
}
