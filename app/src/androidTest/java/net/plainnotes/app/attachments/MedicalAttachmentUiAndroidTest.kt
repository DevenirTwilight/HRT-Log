package net.plainnotes.app.attachments

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController
import net.plainnotes.app.data.*
import net.plainnotes.app.debug.attachments.MedicalAttachmentUiTestActivity
import net.plainnotes.app.debug.attachments.SyntheticAttachmentFixture
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyStore
import java.security.MessageDigest

/**
 * MED-02 attachment UI end to end (REQUIREMENTS §149), synthetic only: the production section/controller/gateway
 * over real DocumentsUI selection, explicit confirmation, actual SQLCipher COMMIT, authenticated reads, the
 * production viewer, release on close, lock revocation and a data switch. Emulator-guarded fixture.
 */
class MedicalAttachmentUiAndroidTest {
    @get:org.junit.Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun s(id:Int,vararg args:Any)=context.getString(id,*args)
    private fun waitFor(label:String,timeout:Long=30000,condition:()->Boolean) {
        val end=SystemClock.uptimeMillis()+timeout
        while(SystemClock.uptimeMillis()<end) {if(condition())return;Thread.sleep(100)}
        fail("Synthetic timeout: $label")
    }
    /** Dialog buttons, the viewer and the fixed test controls have no scrolling parent; only scroll where there is one. */
    private fun press(matcher:SemanticsMatcher) {
        ui.waitUntil(30000) {ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()}
        val node=ui.onNode(matcher)
        if(ui.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty())node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun tap(tag:String)=press(hasTestTag(tag))
    private fun button(text:String)=press(hasText(text))
    /** One real touch on the actual system DocumentsUI entry (same technique as AttachmentOperationAndroidTest). */
    private fun pick(name:String) {
        var selected:Rect?=null
        waitFor(name) {
            val root=instrumentation.uiAutomation.rootInActiveWindow ?: return@waitFor false
            if(!root.packageName.toString().endsWith(".documentsui"))return@waitFor false
            try {instrumentation.uiAutomation.waitForIdle(300,3000)}catch(_:java.util.concurrent.TimeoutException) {return@waitFor false}
            val current=instrumentation.uiAutomation.rootInActiveWindow ?: return@waitFor false
            if(!current.refresh() || !current.packageName.toString().endsWith(".documentsui"))return@waitFor false
            for(n in current.findAccessibilityNodeInfosByText(name).orEmpty()) {
                if(!n.refresh())continue
                val bounds=Rect();n.getBoundsInScreen(bounds)
                if(n.text?.toString()==name && n.isVisibleToUser && !bounds.isEmpty) {selected=bounds;return@waitFor true}
            }
            false
        }
        val bounds=requireNotNull(selected);val down=SystemClock.uptimeMillis()
        val pointer=MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_FINGER}
        val coordinates=MotionEvent.PointerCoords().apply {x=bounds.exactCenterX();y=bounds.exactCenterY();pressure=1f;size=1f}
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,arrayOf(pointer),arrayOf(coordinates),0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
            try {assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true))}finally {event.recycle()}
        }
    }
    private fun cleanup(f:SyntheticAttachmentFixture) {
        val keys=mutableSetOf("notes.local.${f.id}.notes.wrap")
        try {
            f.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT stage_alias_uuid,active_alias_uuid FROM attachment_journal").use {c->while(c.moveToNext()) {
                keys+="notes.obj.v1.s.${c.getString(0)}";keys+="notes.obj.v1.a.${c.getString(1)}"
            }}
        }catch(_:Exception) {}finally {
            f.close();val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            keys.forEach {store.deleteEntry(it)}
            check(f.directory.name=="synthetic-medflow-${f.id}");f.directory.deleteRecursively()
        }
    }
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).toList()
    private fun ui(a:MedicalAttachmentUiTestActivity)=requireNotNull(a.controller).state.value

    @Test fun pngAndPdfImportCommitListViewReleaseLockAndDataSwitch()=runBlocking {
        for(kind in listOf("png","pdf")) {
            var activity:MedicalAttachmentUiTestActivity?=null
            val scenario=ActivityScenario.launch(MedicalAttachmentUiTestActivity::class.java)
            try {
                scenario.onActivity {activity=it}
                waitFor("fixture") {activity?.message=="SYNTHETIC ready"}
                val a=requireNotNull(activity)
                button(if(kind=="png")"Synthetic lab" else "Synthetic visit")
                val target=requireNotNull(a.target)
                waitFor("empty list") {(a.controller?.state?.value?.files as? MedicalAttachmentsController.Files.Ready)?.items?.isEmpty()==true}
                ui.onNodeWithText(s(R.string.attach_empty)).assertExists()

                // Real DocumentsUI → explicit confirmation naming the existing record → actual COMMIT.
                tap("attachments-add");pick("SYNTHETIC sample.$kind")
                waitFor("confirmation") {ui(a).import is MedicalAttachmentsController.Import.Confirming}
                val label="SYNTHETIC ${if(kind=="png")"lab result" else "visit"} #${target.id}"
                ui.onNodeWithText(s(R.string.attach_confirm_target,label)).assertExists()
                ui.onNodeWithTag("attachments-name").performTextClearance()
                ui.onNodeWithTag("attachments-name").performTextInput("SYNTHETIC $kind")
                tap("attachments-confirm-attach")
                waitFor("actual commit") {ui(a).import==MedicalAttachmentsController.Import.Saved(listStale=false)}
                ui.onNodeWithText(s(R.string.attach_saved)).assertExists()
                // Independent check through the scoped facade: committed, linked to THIS record only, same bytes.
                val other=if(target.kind==MedicalAttachmentOperations.Kind.LAB)a.fixture.target else a.fixture.labTarget
                a.fixture.runtime.attachments(other,"SYNTHETIC other").use {assertTrue(it.documents().isEmpty())}
                val saved=a.fixture.runtime.attachments(target,"SYNTHETIC check").use {flow->
                    val d=flow.documents().single();assertEquals("SYNTHETIC $kind",d.title);assertEquals("ACTIVE",d.state);assertEquals("ACTIVE",d.linkState)
                    val expected=SyntheticDocumentsProvider.sample(kind)
                    withContext(Dispatchers.IO) {flow.open(d.document).use {r->
                        val actual=ByteArray(r.size.toInt());var p=0;while(p<actual.size) {val n=r.readAt(p.toLong(),actual,p,actual.size-p);assertTrue(n>0);p+=n}
                        assertEquals(hash(expected),hash(actual));actual.fill(0)
                    }}
                    expected.fill(0);d
                }
                val item=(ui(a).files as MedicalAttachmentsController.Files.Ready).items.single()
                assertEquals(saved.document,item.document);assertTrue(item.available)

                // Open: PNG everywhere, PDF page on Android 9+, and the honest message on 8.x.
                val rowLabel=s(R.string.attach_item_description,item.title,s(if(kind=="png")R.string.attach_kind_image else R.string.attach_kind_pdf),s(R.string.attach_status_saved))
                ui.onNodeWithContentDescription(rowLabel).performScrollTo().performClick()
                val previewable=kind=="png" || Build.VERSION.SDK_INT>=Build.VERSION_CODES.P
                if(!previewable) {
                    waitFor("8.x PDF message") {(ui(a).viewer as? MedicalAttachmentsController.Viewer.Message)?.reason==MedicalAttachmentsController.Reason.PDF_NEEDS_ANDROID_9}
                    ui.onNodeWithText(s(R.string.viewer_pdf_unsupported)).assertExists()
                    tap("attachment-viewer-close")
                }else {
                    waitFor("authenticated page") {ui(a).viewer is MedicalAttachmentsController.Viewer.Showing}
                    val first=(ui(a).viewer as MedicalAttachmentsController.Viewer.Showing).page
                    ui.onNodeWithTag("attachment-viewer-page").assertExists()
                    // Leaving the viewer releases the page (and closes the reader with it).
                    tap("attachment-viewer-close")
                    waitFor("viewer closed") {ui(a).viewer==MedicalAttachmentsController.Viewer.Closed}
                    assertTrue(first.isRecycled);ui.onNodeWithTag("attachment-viewer").assertDoesNotExist()
                    // Reopen, then lock: the page disappears from the screen and its pixels are released at once.
                    ui.onNodeWithContentDescription(rowLabel).performScrollTo().performClick()
                    waitFor("reopened") {ui(a).viewer is MedicalAttachmentsController.Viewer.Showing}
                    val second=(ui(a).viewer as MedicalAttachmentsController.Viewer.Showing).page
                    button("Lock")
                    waitFor("revoked") {ui(a).revoked}
                    assertEquals(MedicalAttachmentsController.Viewer.Closed,ui(a).viewer);assertTrue(second.isRecycled)
                    ui.onNodeWithTag("attachment-viewer-page").assertDoesNotExist();ui.onNodeWithText(s(R.string.attach_hidden)).assertExists()
                    ui.onNodeWithText("SYNTHETIC $kind",substring=true).assertDoesNotExist()
                    // An old reader of the locked page refuses and zeroes output.
                    try {a.fixture.runtime.checkRequest();fail("Locked request still allowed")}catch(_:Exception) {}
                    button("Unlock")
                    waitFor("unlocked list") {!ui(a).revoked && ui(a).files is MedicalAttachmentsController.Files.Ready}
                    assertEquals("Unlock never reopens a preview by itself",MedicalAttachmentsController.Viewer.Closed,ui(a).viewer)
                }

                // Data switch: this page can never read again, even the same document identity.
                val staleFlow=a.fixture.runtime.attachments(target,"SYNTHETIC stale")
                val staleRead=withContext(Dispatchers.IO) {staleFlow.open(saved.document)}
                button("Switch data")
                waitFor("data changed") {ui(a).ended}
                ui.onNodeWithText(s(R.string.attach_data_changed)).assertExists()
                val zeros=ByteArray(8) {7}
                try {withContext(Dispatchers.IO) {staleRead.readAt(0,zeros,0,zeros.size)};fail("Old page read after data switch")}catch(_:IllegalStateException) {}
                assertTrue(zeros.all {it==0.toByte()})
                staleRead.close();staleFlow.close()
                assertEquals(0,a.fixture.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {it.moveToFirst();it.getInt(0)})
            }finally {scenario.close();activity?.fixture?.let(::cleanup)}
        }
    }

    @Test fun cancellingLeavesNoOperationAndRecreationKeepsNoPickerResultOrPreview()=runBlocking {
        var activity:MedicalAttachmentUiTestActivity?=null
        val scenario=ActivityScenario.launch(MedicalAttachmentUiTestActivity::class.java)
        try {
            scenario.onActivity {activity=it}
            waitFor("fixture") {activity?.message=="SYNTHETIC ready"}
            button("Synthetic visit")
            val a=requireNotNull(activity)
            waitFor("list") {a.controller?.state?.value?.files is MedicalAttachmentsController.Files.Ready}
            // System picker back: no file.
            tap("attachments-add")
            waitFor("real DocumentsUI") {instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("SYNTHETIC sample.png").orEmpty().isNotEmpty()}
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            waitFor("picker cancelled") {ui(a).import==MedicalAttachmentsController.Import.Cancelled}
            // Chosen but not confirmed: cancel in the dialog.
            tap("attachments-add");pick("SYNTHETIC sample.png")
            waitFor("confirmation") {ui(a).import is MedicalAttachmentsController.Import.Confirming}
            tap("attachments-confirm-cancel")
            waitFor("dialog cancelled") {ui(a).import==MedicalAttachmentsController.Import.Cancelled}
            val target=requireNotNull(a.target)
            a.fixture.runtime.attachments(target,"SYNTHETIC check").use {assertTrue("No attachment and no pending write",it.documents().isEmpty())}
            assertEquals(0,a.fixture.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT COUNT(*) FROM attachment_journal").use {it.moveToFirst();it.getInt(0)})
            // Chosen, then the page is recreated before confirming: the choice is gone, nothing is written.
            tap("attachments-add");pick("SYNTHETIC sample.png")
            waitFor("confirmation again") {ui(a).import is MedicalAttachmentsController.Import.Confirming}
            scenario.recreate();scenario.onActivity {activity=it}
            val next=requireNotNull(activity);assertNotSame(a,next)
            waitFor("recreated") {next.message=="SYNTHETIC ready" && next.controller?.state?.value?.files is MedicalAttachmentsController.Files.Ready}
            assertEquals(target,next.target)
            assertEquals(MedicalAttachmentsController.Import.Idle,ui(next).import);assertEquals(MedicalAttachmentsController.Viewer.Closed,ui(next).viewer)
            assertEquals(0,next.fixture.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT COUNT(*) FROM attachment_journal").use {it.moveToFirst();it.getInt(0)})
        }finally {scenario.close();activity?.fixture?.let(::cleanup)}
    }
}
