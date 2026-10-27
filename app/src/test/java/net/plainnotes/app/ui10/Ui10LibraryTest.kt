package net.plainnotes.app.ui10

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Files
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Item
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.State as UiState
import net.plainnotes.app.attachments.ui.MedicalAttachmentsSection
import net.plainnotes.app.attachments.ui.library.*
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/**
 * UI-10: the file library, file page, file-first add flow and record-first outcome as reusable Compose, with synthetic data and
 * every capability on or off. These screens are not reachable from release navigation; they wait for the MED-03 backend contract
 * (docs/medical-attachments/2026-10-27/ui10/INTERFACE_CONTRACT.md). Four languages × 320 dp/2× and 411 dp/1× × light and dark.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class Ui10LibraryTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l ->
            listOf(arrayOf<Any>(l, 320, 2f, false), arrayOf<Any>(l, 320, 2f, true), arrayOf<Any>(l, 411, 1f, false), arrayOf<Any>(l, 411, 1f, true)) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val mode get() = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun setup() { RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font) }
    private fun shot(name: String) {
        val dir = System.getenv("UI10_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        ui.waitForIdle()
        val view = ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        File("build/ui10-shots/$dir").apply { mkdirs() }.resolve("${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val report = DocumentSummary(UUID.randomUUID(), "Synthetic hormone report", FileKind.PDF, LinkState.Linked(2), FileCategory.LAB_REPORT, "Synthetic date")
    private val photo = DocumentSummary(UUID.randomUUID(), "Synthetic prescription photo", FileKind.IMAGE, LinkState.Linked(1), FileCategory.PRESCRIPTION)
    private val loose = DocumentSummary(UUID.randomUUID(), "Synthetic loose file", FileKind.PDF, LinkState.Unlinked)
    private val library = LibraryState(listOf(report, photo, loose), mapOf(FileCategory.LAB_REPORT to 5, FileCategory.PRESCRIPTION to 3, FileCategory.VISIT_NOTE to 2, FileCategory.CERTIFICATE to 1, FileCategory.OTHER to 1), unlinked = 3, total = 12)
    private val on = LibraryCapabilities(standaloneImport = true, search = true, categories = true, bidirectionalLinks = true, panelLinks = true, chooseExisting = true, recycle = true)
    private val off = LibraryCapabilities()

    @Test fun libraryWithTheBackendShowsEveryAreaOfSample06() {
        setup()
        var added = 0; var opened: DocumentSummary? = null; var category: FileCategory? = null
        ui.setContent { NotesTheme(mode) { MedicalFileLibraryScreen(library, on, { added++ }, {}, { opened = it }, {}, { category = it }, {}, {}, {}) } }
        ui.onNodeWithTag("library-search").assertIsEnabled()
        ui.onNodeWithTag("library-add").assertIsEnabled().performClick(); assertEquals(1, added)
        ui.onNodeWithTag("library-unsorted").assert(hasText(s(R.string.lib_unsorted_title)))
        shot("library_top")
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-category:LAB_REPORT"))
        ui.onNodeWithTag("library-category:LAB_REPORT").assertHasClickAction().performClick(); assertEquals(FileCategory.LAB_REPORT, category)
        // A category with no files is not a button.
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-category:IMAGING"))
        ui.onNodeWithTag("library-category:IMAGING").assertIsNotEnabled()
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${report.document}"))
        ui.onNodeWithTag("library-doc:${report.document}").assertHasClickAction().performClick(); assertEquals(report, opened)
        // The link state is real data: linked count for two, an honest "not linked" for the loose one.
        fun inRow(d: DocumentSummary, text: String) = ui.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("library-doc:${d.document}")))
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${loose.document}"))
        inRow(loose, s(R.string.lib_not_linked)).assertCountEquals(1)
        inRow(report, ui.activity.resources.getQuantityString(R.plurals.lib_linked_n, 2, 2)).assertCountEquals(1)
        // Date only when the DTO supplies one; the loose file has none and shows none.
        inRow(loose, "Synthetic date").assertCountEquals(0)
        shot("library_middle")
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-backup"))
        ui.onNodeWithTag("library-backup").assertHasClickAction(); shot("library_bottom")
    }

    @Test fun libraryWithoutTheBackendDisablesWithReasonsAndInventsNothing() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalFileLibraryScreen(LibraryState(emptyList(), emptyMap(), 0, 0), off, {}, {}, {}, {}, {}, {}, {}, {}) } }
        ui.onNodeWithTag("library-search").assertIsNotEnabled()
        ui.onNodeWithTag("library-search-reason", useUnmergedTree = true).assertExists()
        ui.onNodeWithTag("library-add").assertIsNotEnabled()
        ui.onNodeWithTag("library-add-reason").assertExists()
        ui.onAllNodesWithTag("library-unsorted").assertCountEquals(0)
        ui.onAllNodesWithTag("library-category:LAB_REPORT").assertCountEquals(0)
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-empty")); ui.onNodeWithTag("library-empty").assertExists()
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-bin")); ui.onNodeWithTag("library-bin").assertIsNotEnabled()
        shot("library_off")
    }

    private val visitLink = DocumentLink(RecordTarget.Visit(1, "Synthetic visit"))
    private val panelLink = DocumentLink(RecordTarget.LabPanel(7, "Synthetic blood test", 5))
    private val valueLink = DocumentLink(RecordTarget.LabValue(9, "Synthetic result"))
    private fun detail(links: List<DocumentLink>, inBin: Boolean = false, size: String? = null) = DocumentDetail(report, links, size, inBin)
    private fun preview(): @androidx.compose.runtime.Composable () -> Unit = { Box(Modifier.fillMaxSize().background(Color(0xFFE3E7E5))) }

    @Test fun filePageNamesEachLinkWithItsScopeAndOnlyShowsKnownFacts() {
        setup()
        val removed = mutableListOf<DocumentLink>(); var opened: RecordTarget? = null
        ui.setContent { NotesTheme(mode) { MedicalFileDetailPage(detail(listOf(visitLink, panelLink, valueLink)), on, preview(), {}, {}, {}, { removed += it }, { opened = it }, {}) } }
        ui.onNodeWithTag("file-link:Synthetic blood test").performScrollTo().assert(hasText(s(R.string.lib_scope_report), substring = true))
        ui.onNodeWithTag("file-link:Synthetic result").assert(hasText(s(R.string.lib_scope_item)))
        ui.onNodeWithTag("file-link:Synthetic visit").performClick(); assertEquals(visitLink.target, opened)
        ui.onNodeWithTag("file-unlink:Synthetic blood test").assertHasClickAction().performClick(); assertEquals(listOf(panelLink), removed)
        // The size is not known here, so no size row exists; the date and category are.
        ui.onNodeWithTag("file-detail-facts").performScrollTo()
        ui.onAllNodesWithText(s(R.string.lib_info_size)).assertCountEquals(0)
        ui.onNode(hasText(s(R.string.lib_info_category)) and hasAnyAncestor(hasTestTag("file-detail-facts"))).assertExists()
        ui.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { assertTrue("${it.config}", it.size.height >= 48 * ui.density.density - 1) }
        shot("file_detail")
    }

    @Test fun filePageWithoutLinkSupportOffersNoLinkChanges() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalFileDetailPage(detail(listOf(visitLink), size = "340 KB"), off, preview(), {}, {}, {}, {}, {}, {}) } }
        ui.onAllNodesWithTag("file-detail-add-link").assertCountEquals(0)
        ui.onAllNodesWithTag("file-unlink:Synthetic visit").assertCountEquals(0)
        ui.onNodeWithTag("file-detail-view").assertHasClickAction()
        ui.onNodeWithTag("file-detail-facts").performScrollTo(); ui.onNode(hasText("340 KB")).assertExists()
    }

    @Test fun filePageOfABinnedFileOnlyOffersRestoreAndNoLinkOrViewActions() {
        setup()
        var restored = 0
        ui.setContent { NotesTheme(mode) { MedicalFileDetailPage(detail(emptyList(), inBin = true), on, preview(), {}, {}, {}, {}, {}, { restored++ }) } }
        ui.onNodeWithTag("file-detail-bin").assertExists()
        ui.onNodeWithTag("file-detail-restore").performScrollTo().assertIsEnabled().performClick(); assertEquals(1, restored)
        ui.onAllNodesWithTag("file-detail-add-link").assertCountEquals(0)
        ui.onAllNodesWithTag("file-detail-view").assertCountEquals(0)
        ui.onNodeWithTag("file-detail-no-links").assertExists()
    }

    @Test fun addDocumentNeedsAnExplicitRecordBeforeAnExistingLinkCanBeConfirmed() {
        setup()
        var draft by mutableStateOf(ImportDraft("synthetic-report.pdf", "Synthetic report"))
        var confirmed = 0; var chosen = 0
        ui.setContent { NotesTheme(mode) { AddDocumentDetails(draft, on, { draft = it }, { chosen++; draft = draft.copy(chosen = RecordTarget.Visit(1, "Synthetic visit")) }, { confirmed++ }, {}) } }
        // Default: keep it without a record, a real choice that can be confirmed at once.
        ui.onNodeWithTag("add-document-link:LATER").assertIsSelected()
        ui.onNodeWithTag("add-document-confirm").assertIsEnabled()
        ui.onNodeWithTag("add-document-link:EXISTING_VISIT").performScrollTo().performClick()
        ui.onNodeWithTag("add-document-confirm").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("add-document-choose").performScrollTo().performClick(); assertEquals(1, chosen)
        ui.onNodeWithTag("add-document-chosen").assert(hasText(s(R.string.lib_import_chosen, "Synthetic visit")))
        ui.onNodeWithTag("add-document-confirm").performScrollTo().assertIsEnabled().performClick(); assertEquals(1, confirmed)
        // Changing the choice forgets the chosen record: nothing is linked by leftover state.
        ui.onNodeWithTag("add-document-link:LATER").performScrollTo().performClick()
        assertNull(draft.chosen)
        // A blank title cannot be confirmed.
        ui.runOnIdle { draft = draft.copy(title = " ") }
        ui.onNodeWithTag("add-document-confirm").performScrollTo().assertIsNotEnabled()
        shot("add_document")
    }

    @Test fun addDocumentWithoutTheBackendExplainsEachDisabledChoice() {
        setup()
        ui.setContent { NotesTheme(mode) { AddDocumentDetails(ImportDraft("synthetic.png", "Synthetic"), off, {}, {}, {}, {}) } }
        for (choice in LinkChoice.entries) ui.onNodeWithTag("add-document-link:${choice.name}").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("add-document-confirm").performScrollTo().assertIsNotEnabled()
        ui.onAllNodesWithText(s(R.string.lib_import_needs)).assertCountEquals(LinkChoice.entries.size)
        shot("add_document_off")
    }

    @Test fun recordFirstOutcomeKeepsTheRecordAndTheFileSeparateAndNeverRetriesAnUnknown() {
        setup()
        var outcome by mutableStateOf(RecordFileOutcome.NONE)
        var adds = 0; var checks = 0; var later = 0
        ui.setContent { NotesTheme(mode) { RecordFileOutcomeCard("Synthetic result", outcome, { adds++ }, { later++ }, { checks++ }) } }
        ui.onNodeWithTag("record-file-add").assertHasClickAction().performClick(); assertEquals(1, adds)
        ui.onNodeWithTag("record-file-later").performClick(); assertEquals(1, later)
        shot("record_outcome_offer")
        ui.runOnIdle { outcome = RecordFileOutcome.NOT_SAVED }
        ui.onNodeWithTag("record-file-outcome-text").assert(hasText(s(R.string.lib_record_file_failed)))
        ui.onNodeWithTag("record-file-add").assert(hasText(s(R.string.lib_record_again)))
        shot("record_outcome_failed")
        ui.runOnIdle { outcome = RecordFileOutcome.UNKNOWN }
        ui.onNodeWithTag("record-file-outcome-text").assert(hasText(s(R.string.lib_record_file_unknown)))
        // Only a read-only check: no second write is offered while the outcome is unknown.
        ui.onAllNodesWithTag("record-file-add").assertCountEquals(0)
        ui.onNodeWithTag("record-file-check").performClick(); assertEquals(1, checks)
        shot("record_outcome_unknown")
        ui.runOnIdle { outcome = RecordFileOutcome.ADDED }
        ui.onNodeWithTag("record-file-outcome-text").assert(hasText(s(R.string.lib_record_file_added)))
        ui.onAllNodesWithTag("record-file-add").assertCountEquals(0)
    }

    @Test fun relatedFilesOffersChooseExistingOnlyWhenTheLibraryCanLinkIt() {
        setup()
        var offered by mutableStateOf(false); var chosen = 0
        ui.setContent { NotesTheme(mode) { MedicalAttachmentsSection(UiState(files = Files.Ready(listOf(Item(UUID.randomUUID(), "Synthetic file", FileKind.PDF, true)))), "Synthetic result",
            {}, {}, {}, {}, {}, {}, {}, onChooseExisting = if (offered) ({ chosen++ }) else null) } }
        ui.onAllNodesWithTag("attachments-choose-existing").assertCountEquals(0)
        ui.runOnIdle { offered = true }
        ui.onNodeWithTag("attachments-choose-existing").assertHasClickAction().performClick(); assertEquals(1, chosen)
    }
}
