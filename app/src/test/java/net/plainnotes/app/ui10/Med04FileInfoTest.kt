package net.plainnotes.app.ui10

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.attachments.ui.library.*
import net.plainnotes.app.data.MedicalAttachmentOperations as Ops
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
import java.time.LocalDate
import java.util.UUID

/**
 * MED-04 (REQUIREMENTS §164): what the user types about a file — a category, the date on the document and the institution — and how
 * the file library shows it (sample 06: category tiles with counts, per-row category · date · institution). Nothing is inferred:
 * a file without the information shows "date unknown" and no category, and a category tile with no files is not a button.
 * Four languages × 320 dp/2× and 411 dp/1× × light and dark, synthetic data only.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class Med04FileInfoTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
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
        val dir = System.getenv("MED04_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        ui.waitForIdle()
        val view = ui.activity.window.decorView
        ui.runOnUiThread {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
            File("build/med04-shots/$dir").apply { mkdirs() }.resolve("${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val on = LibraryCapabilities(standaloneImport = true, search = true, categories = true, bidirectionalLinks = true, panelLinks = true, chooseExisting = true, recycle = true)
    private val report = DocumentSummary(UUID.randomUUID(), "Synthetic hormone report", FileKind.PDF, LinkState.Linked(2), FileCategory.LAB_REPORT, null,
        "Oct 7, 2026", "Synthetic Clinic", true)
    private val photo = DocumentSummary(UUID.randomUUID(), "Synthetic prescription photo", FileKind.IMAGE, LinkState.Linked(1), FileCategory.PRESCRIPTION, null, "Sep 22, 2026", null, true)
    private val loose = DocumentSummary(UUID.randomUUID(), "Synthetic loose file", FileKind.PDF, LinkState.Unlinked, null, "Oct 14, 2026", null, null, true)
    private val counts = mapOf(FileCategory.LAB_REPORT to 1, FileCategory.PRESCRIPTION to 1)
    private val state = LibraryState(listOf(report, photo, loose), counts, unlinked = 1, total = 3, toSort = 2)

    private fun inRow(d: DocumentSummary, text: String) = ui.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("library-doc:${d.document}")))

    @Test fun libraryShowsCategoryTilesCountsAndOnlyWhatTheUserEntered() {
        setup()
        var category: FileCategory? = null; var sorted = 0; var unlinkedTaps = 0
        ui.setContent { NotesTheme(mode) { MedicalFileLibraryScreen(state, on, {}, {}, {}, { sorted++ }, { category = it }, {}, {}, {}, categoryFilter = FileCategory.PRESCRIPTION,
            onUnlinked = { unlinkedTaps++ }) } }
        // The card counts files still without a category or a date, in its own words, not files without a record.
        ui.onNodeWithTag("library-unsorted").assert(hasText(ui.activity.resources.getQuantityString(R.plurals.med04_to_sort_body, 2, 2), substring = true)).performClick()
        assertEquals(1, sorted)
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-category:LAB_REPORT"))
        ui.onNodeWithTag("library-category:LAB_REPORT").assertIsEnabled().assertHasClickAction()
        ui.onNode(hasText("1") and hasAnyAncestor(hasTestTag("library-category:LAB_REPORT")), useUnmergedTree = true).assertExists()
        ui.onNodeWithTag("library-category:LAB_REPORT").performClick()
        assertEquals(FileCategory.LAB_REPORT, category)
        ui.onNodeWithTag("library-category:PRESCRIPTION").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        ui.onNodeWithTag("library-category:LAB_REPORT").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        for (empty in listOf(FileCategory.VISIT_NOTE, FileCategory.CERTIFICATE, FileCategory.IMAGING, FileCategory.OTHER)) {
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-category:${empty.name}"))
            ui.onNodeWithTag("library-category:${empty.name}").assertIsNotEnabled()
        }
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${report.document}"))
        inRow(report, s(R.string.lib_cat_lab)).assertCountEquals(1)
        inRow(report, "Oct 7, 2026").assertCountEquals(1)
        inRow(report, "Synthetic Clinic").assertCountEquals(1)
        inRow(photo, "Sep 22, 2026").assertCountEquals(1)
        inRow(photo, "Synthetic Clinic").assertCountEquals(0)
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${loose.document}"))
        inRow(loose, s(R.string.med04_date_unknown)).assertCountEquals(1)
        inRow(loose, s(R.string.med04_imported_on, "Oct 14, 2026")).assertCountEquals(1)
        for (category in FileCategory.entries) inRow(loose, s(category.label())).assertCountEquals(0)
        shot("library_info_middle")
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-unlinked"))
        ui.onNodeWithTag("library-unlinked").performClick()
        assertEquals(1, unlinkedTaps); assertEquals(1, sorted)
        ui.onNodeWithTag("library-unlinked").assert(hasText(s(R.string.lib_unlinked_files, 1), substring = true))
    }

    @Test fun withoutDetailsTheLibraryKeepsItsEarlierWording() {
        setup()
        val legacy = LibraryState(listOf(DocumentSummary(UUID.randomUUID(), "Synthetic old file", FileKind.PDF, LinkState.Unlinked)), emptyMap(), unlinked = 4, total = 4)
        ui.setContent { NotesTheme(mode) { MedicalFileLibraryScreen(legacy, on, {}, {}, {}, {}, {}, {}, {}, {}) } }
        ui.onNodeWithTag("library-unsorted").assert(hasText(ui.activity.resources.getQuantityString(R.plurals.lib_unsorted_body, 4, 4), substring = true))
        ui.onAllNodesWithText(s(R.string.med04_date_unknown), substring = true).assertCountEquals(0)
    }

    @Test fun editorReturnsExactlyWhatWasChosenAndNothingElse() {
        setup()
        var saved: Ops.Detail? = null; var cancelled = 0
        ui.setContent { NotesTheme(mode) { FileInfoEditor(Ops.Detail("LAB_REPORT", "2026-10-07", "Synthetic Clinic", 3), { saved = it }, { cancelled++ }) } }
        shot("editor")
        ui.onNodeWithTag("file-info-category:LAB_REPORT").assertIsSelected()
        ui.onNodeWithTag("file-info-category:PRESCRIPTION").performScrollTo().performClick()
        ui.onNodeWithTag("file-info-category:LAB_REPORT").assertIsNotSelected()
        ui.onNodeWithTag("file-info-institution").performScrollTo().performTextReplacement("  New Clinic  ")
        ui.onNodeWithTag("file-info-save").performScrollTo().performClick()
        assertEquals(Ops.Detail("PRESCRIPTION", "2026-10-07", "New Clinic", 3), saved)
        ui.onNodeWithTag("file-info-date-clear").performScrollTo().performClick()
        ui.onNodeWithTag("file-info-category:PRESCRIPTION").performScrollTo().performClick()   // tap again clears the category
        ui.onNodeWithTag("file-info-institution").performTextReplacement("   ")
        ui.onNodeWithTag("file-info-save").performScrollTo().performClick()
        assertEquals(Ops.Detail(null, null, null, 3), saved)
        ui.onNodeWithTag("file-info-cancel").performScrollTo().performClick(); assertEquals(1, cancelled)
        ui.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { assertTrue("${it.config}", it.size.height >= 48 * ui.density.density - 1) }
    }

    @Test fun institutionIsCappedAndNeverContainsControlCharacters() {
        setup()
        var saved: Ops.Detail? = null
        ui.setContent { NotesTheme(mode) { FileInfoEditor(null, { saved = it }, {}) } }
        ui.onNodeWithTag("file-info-institution").performScrollTo().performTextReplacement("x".repeat(120))
        ui.onNodeWithTag("file-info-save").performScrollTo().performClick()
        assertEquals(Ops.Detail.INSTITUTION_MAX, saved!!.institution!!.length)
        ui.onNodeWithTag("file-info-institution").performTextReplacement("two\nlines")
        ui.onNodeWithTag("file-info-save").performScrollTo().performClick()
        assertEquals("twolines", saved!!.institution)
        assertEquals(0L, saved!!.revision)
    }

    @Test fun theAddFlowCollectsTheSameInformationBeforeTheFileIsSaved() {
        setup()
        var draft = ImportDraft("synthetic.pdf", "Synthetic title", verifiedKind = FileKind.PDF)
        ui.setContent { NotesTheme(mode) { AddDocumentDetails(draft, on, { draft = it }, {}, {}, {}) } }
        ui.onNodeWithTag("add-document-category:CERTIFICATE").performScrollTo().performClick()
        assertEquals(FileCategory.CERTIFICATE, draft.category)
        ui.onNodeWithTag("add-document-institution").performScrollTo().performTextReplacement("Synthetic Clinic")
        assertEquals("Synthetic Clinic", draft.institution)
        assertNull(draft.date)
        shot("add_flow_info")
    }

    @Test fun theAddFlowOffersNoInformationFieldsWithoutTheCategoriesCapability() {
        setup()
        ui.setContent { NotesTheme(mode) { AddDocumentDetails(ImportDraft("synthetic.pdf", "Synthetic title"), LibraryCapabilities(standaloneImport = true), {}, {}, {}, {}) } }
        ui.onAllNodesWithTag("add-document-institution").assertCountEquals(0)
        ui.onAllNodesWithTag("add-document-date").assertCountEquals(0)
    }

    @Test fun filePageShowsTheEnteredFactsAndOffersEditingOnlyWhenAllowed() {
        setup()
        var edits = 0
        val detail = DocumentDetail(report, emptyList(), "340 KB")
        ui.setContent { NotesTheme(mode) { MedicalFileDetailPage(detail, on, { Box(Modifier.fillMaxSize().background(Color(0xFFE3E7E5))) }, {}, {}, {}, {}, {}, {}, onEditInfo = { edits++ }) } }
        ui.onNodeWithTag("file-detail-edit-info").performScrollTo().assertIsEnabled().performClick(); assertEquals(1, edits)
        ui.onNodeWithTag("file-detail-facts").performScrollTo()
        ui.onNode(hasText("Synthetic Clinic") and hasAnyAncestor(hasTestTag("file-detail-facts"))).assertExists()
        ui.onNode(hasText("Oct 7, 2026") and hasAnyAncestor(hasTestTag("file-detail-facts"))).assertExists()
        shot("file_page_info")
    }

    @Test fun aFileInTheBinOffersNoEditing() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalFileDetailPage(DocumentDetail(report, emptyList(), inBin = true), on, { Box(Modifier.fillMaxSize()) }, {}, {}, {}, {}, {}, {}, onEditInfo = {}) } }
        ui.onAllNodesWithTag("file-detail-edit-info").assertCountEquals(0)
    }
}
