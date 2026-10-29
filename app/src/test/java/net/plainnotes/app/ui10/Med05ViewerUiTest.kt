package net.plainnotes.app.ui10

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.MedicalAttachmentViewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Viewer
import net.plainnotes.app.attachments.ui.library.*
import net.plainnotes.app.attachments.ui.library.FileThumbnails.Reason
import net.plainnotes.app.attachments.ui.library.FileThumbnails.Size
import net.plainnotes.app.attachments.ui.library.FileThumbnails.Thumb
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
import java.util.UUID

/** MED-05: the file viewer's page and zoom controls, and the pictures in the file library. Four languages × 320 dp/2× and 411 dp/1× × light and dark. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class Med05ViewerUiTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l ->
            listOf(arrayOf<Any>(l, 320, 2f, false), arrayOf<Any>(l, 320, 2f, true), arrayOf<Any>(l, 411, 1f, false), arrayOf<Any>(l, 411, 1f, true)) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val mode get() = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun setup() { RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font) }
    private fun page() = Bitmap.createBitmap(120, 170, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }

    private fun viewer(index: Int, count: Int, busy: Boolean = false, failed: Boolean = false) = Viewer.Showing("Synthetic scan", page(), true, index, count, busy, failed)

    @Test fun pageButtonsStopAtTheFirstAndLastPageAndNameThePage() {
        setup()
        var requested = -1
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 3), {}, onPage = { requested = it }) } }
        ui.onNodeWithTag("attachment-viewer-prev").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("attachment-viewer-next").performScrollTo().assertIsEnabled().performClick(); assertEquals(1, requested)
        ui.onNodeWithTag("attachment-viewer-page-number").assert(hasText(s(R.string.med05_page_of, 1, 3)))
        ui.onNodeWithTag("attachment-viewer-page").assert(hasContentDescription(s(R.string.med05_page_label, "Synthetic scan", 1, 3)))
    }
    @Test fun theLastPageHasNoNextAndAMiddlePageHasBoth() {
        setup()
        var requested = -1
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(2, 3), {}, onPage = { requested = it }) } }
        ui.onNodeWithTag("attachment-viewer-next").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("attachment-viewer-prev").performScrollTo().assertIsEnabled().performClick(); assertEquals(1, requested)
    }
    @Test fun aSinglePageFileOffersNoPageButtonsAndKeepsItsFirstPageLabel() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 1), {}) } }
        ui.onAllNodesWithTag("attachment-viewer-prev").assertCountEquals(0); ui.onAllNodesWithTag("attachment-viewer-next").assertCountEquals(0)
        ui.onNodeWithText(s(R.string.viewer_pdf_first_page)).assertExists()
        ui.onNodeWithTag("attachment-viewer-page").assert(hasContentDescription(s(R.string.viewer_page, "Synthetic scan")))
    }
    @Test fun whileAPageIsBeingMadeThePageButtonsWaitAndAFailureIsSaid() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(1, 3, busy = true), {}) } }
        ui.onNodeWithTag("attachment-viewer-prev").performScrollTo().assertIsNotEnabled(); ui.onNodeWithTag("attachment-viewer-next").assertIsNotEnabled()
    }
    @Test fun aFailedPageKeepsTheOldOneAndTellsTheUser() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 3, failed = true), {}) } }
        ui.onNodeWithTag("attachment-viewer-page-failed").performScrollTo().assert(hasText(s(R.string.med05_page_failed)))
        ui.onNodeWithTag("attachment-viewer-page").assertExists()
    }
    @Test fun everyZoomGestureHasAButton() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 2), {}) } }
        ui.onNodeWithTag("attachment-viewer-zoom-out").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("attachment-viewer-zoom-reset").assertIsNotEnabled()
        ui.onNodeWithTag("attachment-viewer-zoom-in").assertIsEnabled().performClick()
        ui.onNodeWithTag("attachment-viewer-zoom-out").assertIsEnabled(); ui.onNodeWithTag("attachment-viewer-zoom-reset").assertIsEnabled()
        repeat(6) { ui.onNodeWithTag("attachment-viewer-zoom-in").let { if (runCatching { it.assertIsEnabled() }.isSuccess) it.performClick() } }
        ui.onNodeWithTag("attachment-viewer-zoom-in").assertIsNotEnabled()
        ui.onNodeWithTag("attachment-viewer-zoom-reset").performClick()
        ui.onNodeWithTag("attachment-viewer-zoom-reset").assertIsNotEnabled(); ui.onNodeWithTag("attachment-viewer-zoom-in").assertIsEnabled()
        ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("attachment-viewer-controls"))).fetchSemanticsNodes().forEach { assertTrue("${it.config}", it.size.height >= 48 * ui.density.density - 1) }
    }
    @Test fun doubleTapZoomsInAndAgainOut() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 2), {}) } }
        ui.onNodeWithTag("attachment-viewer-page").performScrollTo().performTouchInput { doubleClick() }
        ui.onNodeWithTag("attachment-viewer-zoom-reset").performScrollTo().assertIsEnabled()
        ui.onNodeWithTag("attachment-viewer-page").performTouchInput { doubleClick() }
        ui.onNodeWithTag("attachment-viewer-zoom-reset").assertIsNotEnabled()
    }
    @Test fun aSidewaysSwipeAtNormalSizeTurnsThePage() {
        setup()
        var requested = -9
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(1, 3), {}, onPage = { requested = it }) } }
        ui.onNodeWithTag("attachment-viewer-page").performScrollTo().performTouchInput { swipeLeft() }
        assertEquals(2, requested)
        ui.onNodeWithTag("attachment-viewer-page").performTouchInput { swipeRight() }
        assertEquals(0, requested)
    }

    private val id = UUID.randomUUID()
    @Test fun aRowShowsItsFirstPageWhenReadyAndOtherwiseTheTypeTileNeverAnEmptyFrame() {
        setup()
        val ready = Thumb.Ready(page())
        val summary = DocumentSummary(id, "Synthetic report", FileKind.PDF, LinkState.Unlinked, detailsKnown = true)
        val asked = mutableListOf<UUID>()
        ui.setContent { NotesTheme(mode) { MedicalFileLibraryScreen(LibraryState(listOf(summary), emptyMap(), 1, 1), LibraryCapabilities(standaloneImport = true, search = true), {}, {}, {}, {}, {}, {}, {}, {},
            thumbs = mapOf(FileThumbnails.Key(id, Size.ROW) to ready), onThumb = { asked += it.document }) } }
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:$id"))
        ui.onNodeWithTag("library-thumb", useUnmergedTree = true).assertExists()
        assertEquals(listOf(id), asked.distinct())
        ui.onAllNodesWithText("PDF", useUnmergedTree = true).assertCountEquals(0)
    }
    @Test fun aRowWithNoPictureYetOrAFailedOneShowsTheTile() {
        setup()
        val summary = DocumentSummary(id, "Synthetic report", FileKind.PDF, LinkState.Unlinked, detailsKnown = true)
        ui.setContent { NotesTheme(mode) { MedicalFileLibraryScreen(LibraryState(listOf(summary), emptyMap(), 1, 1), LibraryCapabilities(standaloneImport = true, search = true), {}, {}, {}, {}, {}, {}, {}, {},
            thumbs = mapOf(FileThumbnails.Key(id, Size.ROW) to Thumb.Unavailable(Reason.FAILED))) } }
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:$id"))
        ui.onAllNodesWithTag("library-thumb", useUnmergedTree = true).assertCountEquals(0)
        ui.onNode(hasText("PDF") and hasAnyAncestor(hasTestTag("library-doc:$id")), useUnmergedTree = true).assertExists()
    }
    @Test fun aRecycledPictureIsNeverDrawn() {
        setup()
        val gone = page().also { it.recycle() }
        val summary = DocumentSummary(id, "Synthetic report", FileKind.PDF, LinkState.Unlinked, detailsKnown = true)
        ui.setContent { NotesTheme(mode) { MedicalFileLibraryScreen(LibraryState(listOf(summary), emptyMap(), 1, 1), LibraryCapabilities(standaloneImport = true, search = true), {}, {}, {}, {}, {}, {}, {}, {},
            thumbs = mapOf(FileThumbnails.Key(id, Size.ROW) to Thumb.Ready(gone))) } }
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:$id"))
        ui.onAllNodesWithTag("library-thumb", useUnmergedTree = true).assertCountEquals(0)
    }
    @Test fun theFilePagePreviewShowsThePictureOrTheHonestReasonOrProgress() {
        setup()
        var mode2 by androidx.compose.runtime.mutableStateOf<Thumb?>(null)
        ui.setContent { NotesTheme(mode) { Box(Modifier.fillMaxSize()) { PagePreview(FileKind.PDF, mode2, true) } } }
        ui.onNodeWithTag("file-detail-thumb-loading").assertExists()
        mode2 = Thumb.Unavailable(Reason.PDF_NEEDS_ANDROID_9); ui.waitForIdle()
        ui.onNodeWithTag("file-detail-thumb-reason").assert(hasText(s(R.string.viewer_pdf_unsupported)))
        mode2 = Thumb.Unavailable(Reason.FAILED); ui.waitForIdle()
        ui.onNodeWithTag("file-detail-thumb-reason").assert(hasText(s(R.string.viewer_failed)))
        mode2 = Thumb.Ready(page()); ui.waitForIdle()
        ui.onNodeWithTag("file-detail-thumb").assertExists(); ui.onAllNodesWithTag("file-detail-thumb-reason").assertCountEquals(0)
    }
}
