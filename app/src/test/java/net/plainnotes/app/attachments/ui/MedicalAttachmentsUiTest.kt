package net.plainnotes.app.attachments.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.FileKind
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Files
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Import
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Item
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Reason
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.Viewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController.State as UiState
import net.plainnotes.app.ui.NotesTheme
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

/** MED-02 attachment UI (REQUIREMENTS §149): every state in four languages at 320 dp/2× font and 411 dp/1×,
 * with TalkBack labels and the revocation blanking contract. Synthetic titles only. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class MedicalAttachmentsUiTest(private val locale:String,private val width:Int,private val font:Float) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr").flatMap {l->listOf(arrayOf<Any>(l,320,2f),arrayOf<Any>(l,411,1f))}
    }
    @get:Rule val ui=createComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun s(id:Int,vararg args:Any)=context.getString(id,*args)
    private val pdf=Item(UUID.randomUUID(),"Synthetic visit letter",FileKind.PDF,true)
    private val image=Item(UUID.randomUUID(),"Synthetic lab photo",FileKind.IMAGE,true)
    private val trashed=Item(UUID.randomUUID(),"Synthetic old scan",FileKind.OTHER,false)
    private fun setup() {RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if(width==320)640 else 891}dp-xxhdpi");RuntimeEnvironment.setFontScale(font)}
    private fun shot(name:String) {
        val dir=System.getenv("MED02_UI_SHOTS")?.takeIf {it.isNotBlank()} ?: return
        val out=File("build/med02-ui-shots/$dir").apply {mkdirs()}
        val bitmap=ui.onRoot().captureToImage().let {android.graphics.Bitmap.createBitmap(it.width,it.height,Bitmap.Config.ARGB_8888).also {b->it.readPixelsInto(b)}}
        File(out,"${name}_${locale}_${width}dp_${if(font==1f)"1x" else "2x"}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun androidx.compose.ui.graphics.ImageBitmap.readPixelsInto(b:Bitmap) {val px=IntArray(width*height);readPixels(px);b.setPixels(px,0,width,0,0,width,height)}

    @Test fun listRowsAreLabelledButtonsAndUnavailableRowsAreNotClickable() {
        setup()
        var opened:Item?=null
        ui.setContent {NotesTheme {MedicalAttachmentsSection(UiState(files=Files.Ready(listOf(pdf,image,trashed))),"Synthetic visit",{},{},{},{},{},{},{opened=it})}}
        ui.onNodeWithText(s(R.string.attach_section)).assertIsDisplayed()
        val pdfLabel=s(R.string.attach_item_description,pdf.title,s(R.string.attach_kind_pdf),s(R.string.attach_status_saved))
        ui.onNodeWithContentDescription(pdfLabel).assertHasClickAction().performClick()
        assertEquals(pdf,opened)
        val trashedLabel=s(R.string.attach_item_description,trashed.title,s(R.string.attach_kind_other),s(R.string.attach_status_unavailable))
        ui.onNodeWithContentDescription(trashedLabel).assertHasNoClickAction()
        ui.onNodeWithTag("attachments-add").assertIsEnabled().assertHasClickAction().assertTextContains(s(R.string.attach_add))
        // Every touch target is at least 48 dp tall.
        ui.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {assertTrue("${it.config}",it.size.height>=48*ui.density.density-1)}
        shot("1_list")
    }

    @Test fun confirmDialogNamesTheExplicitRecordAndCanBeCancelled() {
        // Robolectric: an AlertDialog window at 411 dp never goes idle (same limit as UI-08); the emulator
        // end-to-end test drives the real dialog. Here the dialog is checked at 320 dp with 2× font.
        org.junit.Assume.assumeTrue(width==320)
        setup()
        var confirmed:String?=null;var cancelled=false
        ui.setContent {NotesTheme {MedicalAttachmentsSection(UiState(files=Files.Ready(emptyList()),import=Import.Confirming(s(R.string.attach_default_visit))),"Synthetic visit 12",
            {},{confirmed=it},{cancelled=true},{},{},{},{})}}
        ui.onNodeWithText(s(R.string.attach_empty)).assertExists()
        ui.onNodeWithText(s(R.string.attach_confirm_target,"Synthetic visit 12")).assertIsDisplayed()
        ui.onNodeWithTag("attachments-add").assertIsNotEnabled()
        shot("2_confirm")
        ui.onNodeWithTag("attachments-name").performTextClearance()
        ui.onNodeWithTag("attachments-confirm-attach").assertIsNotEnabled()
        ui.onNodeWithTag("attachments-name").performTextInput("Synthetic letter")
        ui.onNodeWithTag("attachments-confirm-attach").performClick()
        assertEquals("Synthetic letter",confirmed)
        ui.onNodeWithTag("attachments-confirm-cancel").performClick();assertTrue(cancelled)
    }

    @Test fun importStatesAreAnnouncedAndUnknownOffersOnlyAStatusCheck() {
        setup()
        var state by mutableStateOf(UiState(files=Files.Ready(listOf(pdf)),import=Import.Saving))
        var checks=0
        ui.setContent {NotesTheme {MedicalAttachmentsSection(state,"Synthetic visit",{},{},{},{checks++},{},{},{})}}
        fun live()=ui.onNodeWithTag("attachments-status").fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion)
        ui.onNodeWithText(s(R.string.attach_saving)).assertIsDisplayed();assertNotNull(live())
        ui.onNodeWithTag("attachments-add").assertIsNotEnabled()
        shot("3_saving")
        for((import,text) in listOf(Import.Saved(false) to R.string.attach_saved,Import.Saved(true) to R.string.attach_saved_list_stale,
            Import.NotSaved to R.string.attach_not_saved,Import.Cancelled to R.string.attach_cancelled)) {
            state=state.copy(import=import);ui.waitForIdle()
            ui.onNodeWithText(s(text)).assertIsDisplayed();assertNotNull(live())
        }
        state=state.copy(import=Import.Unknown);ui.waitForIdle()
        ui.onNodeWithText(s(R.string.attach_unknown)).assertIsDisplayed()
        ui.onNodeWithTag("attachments-check").performClick();assertEquals(1,checks)
        shot("4_unknown")
    }

    @Test fun lockedAndChangedDataHideEverything() {
        setup()
        // The open confirmation dialog is included where Robolectric can idle a dialog window (320 dp).
        var state by mutableStateOf(UiState(files=Files.Ready(listOf(pdf,image)),import=if(width==320)Import.Confirming("x") else Import.Idle))
        ui.setContent {NotesTheme {MedicalAttachmentsSection(state,"Synthetic visit",{},{},{},{},{},{},{})}}
        state=UiState(revoked=true);ui.waitForIdle()
        ui.onNodeWithText(s(R.string.attach_hidden)).assertIsDisplayed()
        ui.onNodeWithText(pdf.title,substring=true).assertDoesNotExist()
        ui.onNodeWithTag("attachments-confirm").assertDoesNotExist();ui.onNodeWithTag("attachments-add").assertDoesNotExist()
        shot("5_locked")
        state=UiState(revoked=true,ended=true);ui.waitForIdle()
        ui.onNodeWithText(s(R.string.attach_data_changed)).assertIsDisplayed()
    }

    @Test fun viewerShowsAPageAndBlanksImmediatelyOnRevocation() {
        setup()
        val page=Bitmap.createBitmap(64,90,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.WHITE)}
        var viewer by mutableStateOf<Viewer>(Viewer.Showing(pdf.title,page,pdf=true))
        var closes=0
        ui.setContent {NotesTheme {Box {MedicalAttachmentViewer(viewer,{closes++})}}}
        ui.onNodeWithContentDescription(s(R.string.viewer_page,pdf.title)).assertIsDisplayed()
        ui.onNodeWithText(s(R.string.viewer_pdf_first_page)).assertIsDisplayed()
        ui.onNodeWithContentDescription(s(R.string.viewer_close)).assertHasClickAction()
        shot("6_viewer")
        // Revocation: the controller replaces the page with Closed before recycling it.
        viewer=Viewer.Closed;page.recycle();ui.waitForIdle()
        ui.onNodeWithTag("attachment-viewer").assertDoesNotExist();ui.onNodeWithTag("attachment-viewer-page").assertDoesNotExist()
        for((reason,text) in listOf(Reason.PDF_NEEDS_ANDROID_9 to R.string.viewer_pdf_unsupported,Reason.NO_PREVIEW_FOR_TYPE to R.string.viewer_no_preview,Reason.FAILED to R.string.viewer_failed)) {
            viewer=Viewer.Message(pdf.title,reason);ui.waitForIdle()
            ui.onNodeWithText(s(text)).assertIsDisplayed()
        }
        ui.onNodeWithContentDescription(s(R.string.viewer_close)).performClick();assertEquals(1,closes)
        viewer=Viewer.Opening(image.title);ui.waitForIdle()
        ui.onNodeWithText(s(R.string.viewer_opening)).assertIsDisplayed()
    }

    @Test fun roleIsButtonOnlyForOpenableRows() {
        setup()
        ui.setContent {NotesTheme {MedicalAttachmentsSection(UiState(files=Files.Ready(listOf(image,trashed))),"Synthetic lab",{},{},{},{},{},{},{})}}
        val roles=ui.onAllNodes(hasTestTag("attachment:${image.document}") or hasTestTag("attachment:${trashed.document}"),useUnmergedTree=true)
            .fetchSemanticsNodes().map {it.config.getOrNull(SemanticsProperties.Role)}
        assertEquals(listOf(Role.Button,null),roles)
    }
}
