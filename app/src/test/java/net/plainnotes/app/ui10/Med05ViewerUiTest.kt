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
    /** A synthetic page (a made-up report heading and rows), so the screenshots show what a thumbnail and a page look like. Never real data. */
    private fun page(n: Int = 1): Bitmap = Bitmap.createBitmap(420, 594, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.WHITE)
        val c = android.graphics.Canvas(this); val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF00695C.toInt(); c.drawRect(0f, 0f, 420f, 70f, p)
        p.color = android.graphics.Color.WHITE; p.textSize = 30f; c.drawText("SYNTHETIC REPORT  p.$n", 24f, 46f, p)
        p.color = 0xFF37474F.toInt(); p.textSize = 22f
        for (i in 0 until 14) { c.drawText("Synthetic test ${i + 1}", 24f, 120f + i * 34f, p); c.drawText("${10 + i * 3}.${i}", 300f, 120f + i * 34f, p)
            p.color = 0xFFCFD8DC.toInt(); c.drawRect(24f, 128f + i * 34f, 396f, 129f + i * 34f, p); p.color = 0xFF37474F.toInt() }
    }
    private fun shot(name: String) {
        val dir = System.getenv("MED05_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        ui.waitForIdle()
        val dialog=ui.onAllNodesWithTag("attachment-viewer").fetchSemanticsNodes().isNotEmpty()
        ui.runOnUiThread {
            val view=if(dialog)requireNotNull(org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull {it.isShowing}?.window).decorView else ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/med05-shots/$dir").apply { mkdirs() }.resolve("${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun viewer(index: Int, count: Int, busy: Boolean = false, failed: Boolean = false) = Viewer.Showing("Synthetic scan", page(), true, index, count, busy, failed)

    @Test fun pageButtonsStopAtTheFirstAndLastPageAndNameThePage() {
        setup()
        var requested = -1
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 3), {}, onPage = { requested = it }) } }
        ui.onNodeWithTag("attachment-viewer-prev").assertIsNotEnabled()
        ui.onNodeWithTag("attachment-viewer-next").assertIsEnabled().performClick(); assertEquals(1, requested)
        ui.onNodeWithTag("attachment-viewer-page-number").assert(hasText(s(R.string.med05_page_of, 1, 3)))
        ui.onNodeWithTag("attachment-viewer-page").assert(hasContentDescription(s(R.string.med05_page_label, "Synthetic scan", 1, 3)))
        // The reader names the real page count; detail metadata stays on the underlying file-detail page.
        ui.onAllNodesWithText(s(R.string.viewer_pdf_first_page)).assertCountEquals(0)
        ui.onAllNodesWithTag("attachment-viewer-details").assertCountEquals(0)
        ui.onAllNodesWithTag("attachment-viewer-linked").assertCountEquals(0)
        assertReadingAreaFillsAvailableSpace()
        shot("viewer_page1")
    }
    @Test fun theLastPageHasNoNextAndAMiddlePageHasBoth() {
        setup()
        var requested = -1
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(2, 3), {}, onPage = { requested = it }) } }
        ui.onNodeWithTag("attachment-viewer-next").assertIsNotEnabled()
        ui.onNodeWithTag("attachment-viewer-prev").assertIsEnabled().performClick(); assertEquals(1, requested)
    }
    @Test fun aSinglePageFileOffersNoPageButtonsAndNamesItsRealPageCount() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 1), {}) } }
        ui.onAllNodesWithTag("attachment-viewer-prev").assertCountEquals(0); ui.onAllNodesWithTag("attachment-viewer-next").assertCountEquals(0)
        ui.onNodeWithTag("attachment-viewer-page-number").assert(hasText(s(R.string.med05_page_of,1,1)))
        ui.onNodeWithTag("attachment-viewer-page").assert(hasContentDescription(s(R.string.viewer_page, "Synthetic scan")))
    }
    @Test fun whileAPageIsBeingMadeThePageButtonsWaitAndAFailureIsSaid() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(1, 3, busy = true), {}) } }
        ui.onNodeWithTag("attachment-viewer-prev").assertIsNotEnabled(); ui.onNodeWithTag("attachment-viewer-next").assertIsNotEnabled()
    }
    @Test fun aFailedPageKeepsTheOldOneAndTellsTheUser() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 3, failed = true), {}) } }
        ui.onNodeWithTag("attachment-viewer-page-failed").assert(hasText(s(R.string.med05_page_failed)))
        ui.onNodeWithTag("attachment-viewer-page").assertExists()
    }
    @Test fun everyZoomGestureHasAButton() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0, 2), {}) } }
        ui.onNodeWithTag("attachment-viewer-zoom-out").assertIsNotEnabled()
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
        ui.onNodeWithTag("attachment-viewer-page").performTouchInput { doubleClick() }
        ui.onNodeWithTag("attachment-viewer-zoom-reset").assertIsEnabled()
        ui.onNodeWithTag("attachment-viewer-page").performTouchInput { doubleClick() }
        ui.onNodeWithTag("attachment-viewer-zoom-reset").assertIsNotEnabled()
    }
    @Test fun aSidewaysSwipeAtNormalSizeTurnsThePage() {
        setup()
        var requested = -9
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(1, 3), {}, onPage = { requested = it }) } }
        ui.onNodeWithTag("attachment-viewer-page").performTouchInput { swipeLeft() }
        assertEquals(2, requested)
        ui.onNodeWithTag("attachment-viewer-page").performTouchInput { swipeRight() }
        assertEquals(0, requested)
    }

    private fun assertReadingAreaFillsAvailableSpace() {
        val safe=ui.onNodeWithTag("attachment-viewer-safe-area").fetchSemanticsNode().boundsInRoot
        val viewport=ui.onNodeWithTag("attachment-viewer-viewport").fetchSemanticsNode().boundsInRoot
        val toolbar=ui.onNodeWithTag("attachment-viewer-toolbar").fetchSemanticsNode().boundsInRoot
        val controls=ui.onNodeWithTag("attachment-viewer-controls").fetchSemanticsNode().boundsInRoot
        val page=ui.onNodeWithTag("attachment-viewer-page").fetchSemanticsNode().boundsInRoot
        assertEquals(safe.width,viewport.width,1f)
        assertEquals(toolbar.bottom,viewport.top,1f)
        assertEquals(controls.top,viewport.bottom,1f)
        assertEquals(viewport,page)
        assertTrue("The reader must not have a scrolling preview card",ui.onAllNodes(hasScrollAction() and hasAnyAncestor(hasTestTag("attachment-viewer"))).fetchSemanticsNodes().isEmpty())
    }
    @Test fun aTapHidesBothToolbarsAndUsesTheWholeSafeDisplayThenRestoresThem() {
        setup()
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0,3), {},targetLabel="Synthetic record metadata") } }
        assertReadingAreaFillsAvailableSpace()
        val before=ui.onNodeWithTag("attachment-viewer-viewport").fetchSemanticsNode().boundsInRoot.height
        ui.onNodeWithTag("attachment-viewer-page").performClick()
        ui.onAllNodesWithTag("attachment-viewer-toolbar").assertCountEquals(0)
        ui.onAllNodesWithTag("attachment-viewer-controls").assertCountEquals(0)
        val safe=ui.onNodeWithTag("attachment-viewer-safe-area").fetchSemanticsNode().boundsInRoot
        val full=ui.onNodeWithTag("attachment-viewer-viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(safe,full);assertTrue(full.height>before)
        ui.onAllNodesWithText("Synthetic record metadata").assertCountEquals(0)
        ui.onNodeWithTag("attachment-viewer-page").performClick()
        ui.onNodeWithTag("attachment-viewer-close").assertIsDisplayed()
        assertReadingAreaFillsAvailableSpace()
    }
    @Test fun pinchZoomAndPanStayInTheReaderAndResetReturnsToFit() {
        setup()
        var requested=-1
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(viewer(0,3), {},onPage={requested=it}) } }
        ui.onNodeWithTag("attachment-viewer-page").performTouchInput {
            val near=androidx.compose.ui.geometry.Offset(width*.05f,0f)
            val far=androidx.compose.ui.geometry.Offset(width*.3f,0f)
            pinch(center-near,center-far,center+near,center+far)
        }
        ui.onNodeWithTag("attachment-viewer-zoom-reset").assertIsEnabled()
        ui.onNodeWithTag("attachment-viewer-page").performTouchInput {swipeLeft()}
        assertEquals("A zoomed drag pans; it must not turn to another page",-1,requested)
        ui.onNodeWithTag("attachment-viewer-zoom-reset").performClick().assertIsNotEnabled()
        assertReadingAreaFillsAvailableSpace()
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
        shot("library_row_thumb")
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
        mode2 = Thumb.Ready(page(2)); ui.waitForIdle()
        ui.onNodeWithTag("file-detail-thumb").assertExists(); ui.onAllNodesWithTag("file-detail-thumb-reason").assertCountEquals(0)
        shot("file_page_preview")
    }
}
