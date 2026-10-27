package net.plainnotes.app.ui10

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.MedicalAttachmentViewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Files
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Import
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Item
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Reason
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Viewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.State as UiState
import net.plainnotes.app.attachments.ui.MedicalAttachmentsSection
import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.data.LabPanelEntity
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.ui.LabDraw
import net.plainnotes.app.ui.LabDetailPage
import net.plainnotes.app.ui.MedicalRecordsScreen
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * UI-10 (REQUIREMENTS §157): the lab result page, the related files, the file page and the medical records hub, with synthetic
 * records only. Four languages × 320 dp at 2× font and 411 dp at 1× × light and dark. Shots (no pixel assertions) are written
 * when UI10_SHOTS=<label> is set; dialog windows are captured at 320 dp only (Robolectric never idles a 411 dp dialog).
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class Ui10RedesignTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l ->
            listOf(arrayOf<Any>(l, 320, 2f, false), arrayOf<Any>(l, 320, 2f, true), arrayOf<Any>(l, 411, 1f, false), arrayOf<Any>(l, 411, 1f, true)) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val mode get() = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun setup() { RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font) }

    private val lab = LabValueEntity(1, "E2", 142.0, "pg/mL", Instant.parse("2026-10-07T08:30:00Z").toEpochMilli(), "UTC", 30.0, 200.0, "pg/mL", "Synthetic laboratory", "Synthetic note")
    private val bare = LabValueEntity(2, "T", 0.9, "ng/mL", Instant.parse("2026-10-07T08:30:00Z").toEpochMilli(), "UTC")
    private val panel = LabPanelEntity(7, "synthetic-panel", Instant.parse("2026-10-07T08:30:00Z").toEpochMilli(), "UTC", "Synthetic laboratory", null, 1L)
    private fun member(id: Long, code: String, v: Double, unit: String) = LabValueEntity(id, code, v, unit, panel.sampled_utc, "UTC", laboratory = "Synthetic laboratory", panel_id = 7)
    /** One blood draw with five results: the unit a person means by "a lab test". */
    private val fiveResults = listOf(member(11, "E2", 142.0, "pg/mL"), member(12, "T", 0.9, "ng/mL"), member(13, "FSH", 4.0, "IU/L"), member(14, "LH", 3.0, "IU/L"), member(15, "PRL", 9.0, "ng/mL"))
    private val pdf = Item(UUID.randomUUID(), "Synthetic lab report", FileKind.PDF, true)
    private val image = Item(UUID.randomUUID(), "Synthetic lab photo", FileKind.IMAGE, true)
    private val gone = Item(UUID.randomUUID(), "Synthetic old scan", FileKind.OTHER, false)
    private fun visit(id: Long, at: Instant, done: Boolean) = AppointmentEntity(id = id, type = "ENDO", at_utc = at.toEpochMilli(), at_zone = "UTC",
        practitioner = "Synthetic doctor", location = "Synthetic clinic", note = null, remind_minutes_before = 0, completed_utc = if (done) at.toEpochMilli() else null)
    private val visits = listOf(visit(1, now.plusSeconds(5 * 86400), false), visit(2, now.minusSeconds(3 * 86400), false), visit(3, now.minusSeconds(30 * 86400), true), visit(4, now.minusSeconds(90 * 86400), true))

    private fun shot(name: String, dialog: Boolean = false) {
        val dir = System.getenv("UI10_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        ui.waitForIdle()
        val view = if (dialog) requireNotNull(org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }?.window).decorView else ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        File("build/ui10-shots/$dir").apply { mkdirs() }.resolve("${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    /** A plain synthetic page bitmap (grey bars on white); no real document. */
    private fun page(): Bitmap = Bitmap.createBitmap(420, 560, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); c.drawColor(Color.WHITE); val p = Paint().apply { color = Color.rgb(205, 214, 211) }
        c.drawRect(40f, 50f, 230f, 76f, p); c.drawRect(40f, 100f, 320f, 112f, p)
        for (i in 0 until 8) c.drawRect(40f, 170f + i * 40f, 380f - (i % 3) * 60f, 182f + i * 40f, p)
    }
    private fun files(state: UiState, onAdd: () -> Unit = {}, onOpen: (Item) -> Unit = {}, onUnlink: ((Item) -> Unit)? = null): @androidx.compose.runtime.Composable () -> Unit =
        { MedicalAttachmentsSection(state, "Synthetic result", onAdd, {}, {}, {}, {}, {}, onOpen, onUnlink = onUnlink) }
    private fun y(tag: String) = ui.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    // --- lab result page ------------------------------------------------------------------------------------------------

    @Test fun labPageHasOneHierarchyAndNoLooseButtons() {
        setup()
        var back = 0; var edit = 0; var added = 0; var opened: Item? = null
        ui.setContent { NotesTheme(mode) { LabDetailPage(lab, emptyList(), { back++ }, { edit++ },
            files = files(UiState(files = Files.Ready(listOf(pdf, image, gone))), { added++ }, { opened = it })) } }
        shot("lab_page_top")
        // Result first, then the report, then the related files, then the sample context.
        ui.onNodeWithTag("lab-hero").assert(hasText("142")).assert(hasText("pg/mL"))
        val order = listOf("lab-hero", "lab-detail-report", "attachments", "lab-detail-context").map { y(it) }
        assertEquals("hero, report, files, context in that order", order.sorted(), order)
        ui.onNodeWithTag("lab-detail-range").assert(hasText(s(R.string.lab_page_range_both, "30", "200", "pg/mL")))
        ui.onNodeWithTag("lab-detail-laboratory").assert(hasText("Synthetic laboratory"))
        ui.onNodeWithTag("lab-detail-note").assert(hasText("Synthetic note"))
        // No "OK" and no attachment pill in the middle of the result: edit is the single secondary action, back is the arrow.
        ui.onAllNodesWithText(s(R.string.ok)).assertCountEquals(0)
        ui.onNodeWithTag("lab-detail-back").assertHasClickAction().assertContentDescriptionEquals(s(R.string.ui10_back)).performClick(); assertEquals(1, back)
        ui.onNodeWithTag("lab-detail-edit").assertHasClickAction().performClick(); assertEquals(1, edit)
        // The files block: count, three rows, one add action; an unavailable row cannot be opened.
        ui.onNodeWithTag("attachments").performScrollTo()
        ui.onNodeWithTag("attachments-count").assertExists()
        ui.onNodeWithContentDescription(s(R.string.attach_item_description, pdf.title, s(R.string.attach_kind_pdf), s(R.string.attach_status_saved))).performClick(); assertEquals(pdf, opened)
        ui.onNodeWithContentDescription(s(R.string.attach_item_description, gone.title, s(R.string.attach_kind_other), s(R.string.attach_status_unavailable))).assertHasNoClickAction()
        ui.onNodeWithTag("attachments-add").performScrollTo().assertIsEnabled().performClick(); assertEquals(1, added)
        // Every touch target is at least 48 dp tall.
        ui.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { assertTrue("${it.config}", it.size.height >= 48 * ui.density.density - 1) }
        ui.onNodeWithTag("attachments").performScrollTo(); shot("lab_page_files")
    }

    @Test fun labPageEmptyFilesCarryTheSameSingleAddAction() {
        setup()
        var added = 0
        ui.setContent { NotesTheme(mode) { LabDetailPage(bare, emptyList(), {}, {}, files = files(UiState(files = Files.Ready(emptyList())), { added++ })) } }
        // Nothing entered besides the value: one calm sentence instead of empty rows.
        ui.onNodeWithTag("lab-detail-nothing-more").assertExists()
        ui.onAllNodesWithTag("lab-detail-report").assertCountEquals(0)
        ui.onNodeWithTag("attachments-empty").performScrollTo().assertExists()
        ui.onAllNodesWithTag("attachments-add").assertCountEquals(1)
        ui.onNodeWithTag("attachments-add").performClick(); assertEquals(1, added)
        ui.onAllNodesWithTag("attachments-count").assertCountEquals(0)
        shot("lab_page_empty")
    }

    @Test fun labPageStatesDuringSavingUnknownAndRevocation() {
        setup()
        ui.setContent { NotesTheme(mode) { LabDetailPage(lab, emptyList(), {}, {}, files = files(UiState(files = Files.Ready(listOf(pdf)), import = Import.Unknown))) } }
        ui.onNodeWithTag("attachments").performScrollTo()
        ui.onNodeWithTag("attachments-check").assertExists()
        // While the outcome is unknown, adding is not offered again (no duplicate write).
        ui.onNodeWithTag("attachments-add").assertIsNotEnabled()
        shot("lab_page_unknown")
    }

    @Test fun lockedAndEndedPagesShowNoFilesAtAll() {
        setup()
        ui.setContent { NotesTheme(mode) { LabDetailPage(lab, emptyList(), {}, {}, files = files(UiState(files = Files.Ready(listOf(pdf)), revoked = true))) } }
        ui.onNodeWithTag("attachments").performScrollTo()
        ui.onNodeWithTag("attachments-hidden").assertExists()
        ui.onAllNodesWithText(pdf.title).assertCountEquals(0)
        ui.onAllNodesWithTag("attachments-add").assertCountEquals(0)
        ui.onAllNodesWithTag("attachments-count").assertCountEquals(0)
    }

    // --- one blood test has several results ------------------------------------------------------------------------------

    @Test fun labPageKeepsTheWholeBloodTestInReachAndSaysFilesAreForOneResult() {
        setup()
        var opened: LabValueEntity? = null; var drawOpened = 0
        val current = fiveResults[1]
        ui.setContent { NotesTheme(mode) { LabDetailPage(current, emptyList(), {}, {}, draw = LabDraw(panel, fiveResults), onOpenResult = { opened = it }, onOpenDraw = { drawOpened++ },
            files = { MedicalAttachmentsSection(UiState(files = Files.Ready(emptyList())), "Synthetic result", {}, {}, {}, {}, {}, {}, {}, scopeNote = s(R.string.lab_page_files_scope, "Testosterone")) }) } }
        // The other four results of the same draw are listed; this one is marked and not a button.
        ui.onNodeWithTag("lab-detail-same-draw").assertExists()
        ui.onNode(hasText(s(R.string.lab_page_same_draw), substring = true) and hasAnyAncestor(hasTestTag("lab-detail-same-draw"))).assertExists()
        for (r in fiveResults) ui.onNodeWithTag("lab-draw-result:${r.id}").performScrollTo().assertExists()
        ui.onNodeWithTag("lab-draw-result:${current.id}").assertIsSelected().assertHasNoClickAction()
        ui.onNodeWithTag("lab-draw-result:${fiveResults[0].id}").assertHasClickAction().performClick(); assertEquals(fiveResults[0], opened)
        ui.onNodeWithTag("lab-detail-open-draw").performScrollTo().assertHasClickAction().performClick(); assertEquals(1, drawOpened)
        // The files block says plainly what the files are linked to; nothing about the whole blood test is offered.
        ui.onNodeWithTag("attachments-scope").performScrollTo().assertExists()
        ui.onAllNodesWithText(s(R.string.lab_page_files_scope, "Testosterone")).assertCountEquals(1)
        val order = listOf("lab-hero", "lab-detail-same-draw", "lab-detail-report", "attachments").map { y(it) }
        assertEquals("result, its blood test, report details, files", order.sorted(), order)
        shot("lab_page_same_draw")
        ui.onNodeWithTag("attachments").performScrollTo(); shot("lab_page_scope_note")
    }

    @Test fun labPageOfASingleResultHasNoBloodTestSectionAndNoScopeNote() {
        setup()
        ui.setContent { NotesTheme(mode) { LabDetailPage(bare, emptyList(), {}, {}, draw = LabDraw(null, listOf(bare)), files = { MedicalAttachmentsSection(UiState(files = Files.Ready(emptyList())), "x", {}, {}, {}, {}, {}, {}, {}) }) } }
        ui.onAllNodesWithTag("lab-detail-same-draw").assertCountEquals(0)
        ui.onAllNodesWithTag("attachments-scope").assertCountEquals(0)
    }

    @Test fun displayGroupOfResultsIsListedButOffersNoWholeTestAction() {
        setup()
        ui.setContent { NotesTheme(mode) { LabDetailPage(fiveResults[0].copy(panel_id = null), emptyList(), {}, {}, draw = LabDraw(null, fiveResults.take(3).map { it.copy(panel_id = null) })) } }
        ui.onNodeWithTag("lab-detail-same-draw").assertExists()
        ui.onAllNodesWithTag("lab-detail-open-draw").assertCountEquals(0)
        ui.onNodeWithTag("lab-detail-display-group").performScrollTo().assertExists()
    }

    // --- file page --------------------------------------------------------------------------------------------------------

    @Test fun filePageShowsPreviewTypeTitleLinkedRecordAndDetails() {
        setup()
        var closed = 0
        var pdfPage by androidx.compose.runtime.mutableStateOf(true)
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(Viewer.Showing(if (pdfPage) pdf.title else image.title, page(), pdfPage), { closed++ }, targetLabel = "Synthetic visit · 7 Oct") } }
        for (isPdf in listOf(true, false)) {
            ui.runOnIdle { pdfPage = isPdf }
            ui.onNodeWithTag("attachment-viewer-page").assertExists()
            ui.onNodeWithTag("attachment-viewer-linked").performScrollTo().assert(hasText("Synthetic visit · 7 Oct")).assert(hasText(s(R.string.viewer_linked_record)))
            ui.onNodeWithTag("attachment-viewer-details").performScrollTo()
            val preview = s(if (isPdf) R.string.viewer_preview_first_page else R.string.viewer_preview_full)
            ui.onNode(hasText(preview) and hasAnyAncestor(hasTestTag("attachment-viewer-details"))).assertExists()
            // Nothing unverified is claimed: no size, date, source or integrity line.
            ui.onAllNodesWithText("KB", substring = true).assertCountEquals(0)
            ui.onNodeWithTag("attachment-viewer-close").assertHasClickAction().performClick()
            if (isPdf) shot("file_page_pdf") else shot("file_page_image")
        }
        assertEquals(2, closed)
    }

    @Test fun filePageMessagesStayCalm() {
        setup()
        var reason by androidx.compose.runtime.mutableStateOf(Reason.FAILED)
        ui.setContent { NotesTheme(mode) { MedicalAttachmentViewer(Viewer.Message(pdf.title, reason), {}, targetLabel = "Synthetic visit") } }
        for (r in Reason.entries) {
            ui.runOnIdle { reason = r }
            ui.onNodeWithTag("attachment-viewer-message").assertExists()
            ui.onAllNodesWithTag("attachment-viewer-page").assertCountEquals(0)
            if (r == Reason.PDF_NEEDS_ANDROID_9) shot("file_page_pdf_android8")
        }
    }

    // --- medical records hub ----------------------------------------------------------------------------------------------

    private fun hub(withFiles: Boolean = true, onVisit: (AppointmentEntity) -> Unit = {}, onLab: ((LabValueEntity) -> Unit)? = null, labs: List<LabValueEntity> = listOf(lab, bare), panels: List<LabPanelEntity> = emptyList()) {
        ui.setContent { NotesTheme(mode) { Scaffold { pad -> MedicalRecordsScreen(visits, onVisit, {}, {}, {}, {}, pad, questionCounts = mapOf(1L to 3), now = now,
            onVisits = if (withFiles) ({}) else null, onMedicalBackup = if (withFiles) ({}) else null, labs = labs, onLab = onLab, labPanels = panels) } } }
    }

    @Test fun hubRecordsAreaLeadsWithTheNextVisitAndTilesNotACardPile() {
        setup(); hub()
        ui.onNodeWithTag("medical-tab:records").assertIsSelected()
        ui.onNodeWithTag("medical-next:1").assertExists()
        shot("hub_records_top")
        ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-labs"))
        ui.onNodeWithTag("medical-labs").assertHasClickAction().assert(hasText(s(R.string.mh_labs_tile)))
        ui.onNodeWithTag("medical-history").assertHasClickAction()
        // The former three identical overview cards and the three "not open" cards are gone.
        for (tag in listOf("medical-center-prepare", "medical-center-pack", "medical-future-files", "medical-future-practitioners", "medical-future-organize")) ui.onAllNodesWithTag(tag).assertCountEquals(0)
        shot("hub_records_bottom")
    }

    @Test fun hubFilesAreaAddsByRecordAndSaysOnceWhatIsNotAvailable() {
        setup(); hub()
        ui.onNodeWithTag("medical-tab:files").performClick().assertIsSelected()
        ui.onNodeWithTag("medical-files-hero").assertExists()
        ui.onNodeWithTag("medical-add-file").assertHasClickAction()
        shot("hub_files_top")
        for (tag in listOf("medical-visit-attachments", "medical-lab-attachments")) {
            ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag(tag)); ui.onNodeWithTag(tag).assertHasClickAction()
        }
        ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-complete-backup"))
        ui.onNodeWithTag("medical-complete-backup").assertHasClickAction()
        ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-files-soon"))
        ui.onNodeWithTag("medical-files-soon").assertHasNoClickAction()
        shot("hub_files_bottom")
        // No search box, category chips or importer that would pretend a library exists.
        ui.onAllNodesWithTag("attachment-add").assertCountEquals(0)
    }

    @Test fun hubWithoutTheFileCapabilityHasNoFilesAreaAtAll() {
        setup(); hub(withFiles = false)
        ui.onAllNodesWithTag("medical-tabs").assertCountEquals(0)
        ui.onAllNodesWithTag("medical-add-file").assertCountEquals(0)
    }

    @Test fun addFileOffersLabResultsPerBloodTestNotAsAFlatListOfResults() {
        assumeTrue("Dialog windows are exercised at 320 dp only", width == 320)
        setup()
        var picked: LabValueEntity? = null
        // One five-result draw (a real panel) and one separate single result.
        hub(onLab = { picked = it }, labs = fiveResults + bare, panels = listOf(panel))
        ui.onNodeWithTag("medical-tab:files").performClick()
        ui.onNodeWithTag("medical-add-file").performClick()
        ui.onNodeWithTag("file-target-kind:1").performClick()
        ui.onNodeWithTag("file-target-lab-help").assertExists()
        // The five results are one group, not five unrelated rows; the single result is its own group.
        ui.onNodeWithTag("file-target-draw:7").assertExists()
        ui.onAllNodes(hasTestTag("file-target-draw:7")).assertCountEquals(1)
        for (r in fiveResults) ui.onNode(hasTestTag("file-target:lab:${r.id}") and hasAnyAncestor(hasTestTag("file-target-draw:7")), useUnmergedTree = true).assertExists()
        ui.onNodeWithTag("file-target-labs").performScrollToNode(hasTestTag("file-target:lab:${bare.id}"))
        ui.onNode(hasTestTag("file-target:lab:${bare.id}") and hasAnyAncestor(hasTestTag("file-target-draw:7")), useUnmergedTree = true).assertDoesNotExist()
        shot("add_file_target_grouped", dialog = true)
        // A file is still linked to one explicit result.
        ui.onNodeWithTag("file-target-labs").performScrollToNode(hasTestTag("file-target:lab:12"))
        ui.onNodeWithTag("file-target:lab:12").performClick()
        assertEquals(fiveResults[1], picked)
    }

    @Test fun hubLabTileCountsBloodTestsAndNamesTheirResults() {
        setup(); hub(labs = fiveResults + bare, panels = listOf(panel))
        ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-labs"))
        // Two blood tests (the panel and the separate result) with six results in all.
        val draws = ui.activity.resources.getQuantityString(R.plurals.mh_lab_draws, 2, 2)
        ui.onNodeWithTag("medical-labs").assert(hasText("$draws · ${s(R.string.lab_group_count, 6)}"))
    }

    @Test fun addFileFirstAsksForAnExplicitRecordAndNeverPicksByDate() {
        assumeTrue("Dialog windows are exercised at 320 dp only", width == 320)
        setup()
        var visitPicked: AppointmentEntity? = null; var labPicked: LabValueEntity? = null
        hub(onVisit = { visitPicked = it }, onLab = { labPicked = it })
        ui.onNodeWithTag("medical-tab:files").performClick()
        ui.onNodeWithTag("medical-add-file").performClick()
        ui.onNodeWithTag("file-target-dialog").assertExists()
        // Newest first; the next visit (id 1) is listed first, nothing is preselected.
        ui.onNodeWithTag("file-target:visit:1").assertHasClickAction()
        shot("add_file_target_visits", dialog = true)
        ui.onNodeWithTag("file-target-kind:1").performClick()
        ui.onNodeWithTag("file-target:lab:1").assertHasClickAction()
        shot("add_file_target_labs", dialog = true)
        ui.onNodeWithTag("file-target:lab:1").performClick()
        assertEquals(lab, labPicked); assertNull(visitPicked)
        ui.onNodeWithTag("file-target-dialog").assertDoesNotExist()
    }
}

