package net.plainnotes.app.attachments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.result.ActivityResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.*
import net.plainnotes.app.data.*
import net.plainnotes.app.debug.attachments.AttachmentOperationTestActivity
import net.plainnotes.app.debug.attachments.SyntheticAttachmentFixture
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import net.plainnotes.app.domain.AttachmentSession

/** Real DocumentsUI selection, real application controller, SQLCipher, Keystore and authorized viewer. */
class AttachmentOperationAndroidTest {
    @get:org.junit.Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun waitFor(label:String,timeout:Long=30000,condition:()->Boolean) {
        val end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end) {if(condition())return;Thread.sleep(100)}
        val root=instrumentation.uiAutomation.rootInActiveWindow
        val snapshot=StringBuilder()
        fun describe(node:AccessibilityNodeInfo?,depth:Int) {
            if(node==null || depth>20 || snapshot.length>6000)return
            snapshot.append("\n").append(" ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                .append(" clickable=").append(node.isClickable).append(" visible=").append(node.isVisibleToUser)
            for(i in 0 until node.childCount)describe(node.getChild(i),depth+1)
        }
        describe(root,0)
        fail("Synthetic timeout: $label; window=${root?.packageName}; $snapshot")
    }
    private fun click(text:String) {
        if(!text.startsWith("SYNTHETIC sample.")) {
            ui.waitUntil(30000) {ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText(text).performScrollTo().assertIsEnabled().performClick()
            return
        }
        // The picker is the actual system DocumentsUI, not a Compose test fixture. Dispatch
        // exactly one real selection; the caller asserts the actual returned SAF result.
        var selected:Rect?=null
        waitFor(text) {
            val root=instrumentation.uiAutomation.rootInActiveWindow ?: return@waitFor false
            if(!root.packageName.toString().endsWith(".documentsui"))return@waitFor false
            if(root.findAccessibilityNodeInfosByText(text).isEmpty())return@waitFor false
            // A cancelled picker may leave nodes from a directory loader/window transition.
            // Settle real accessibility events, then reacquire live geometry before ONE touch.
            try {instrumentation.uiAutomation.waitForIdle(300,3000)}catch(_:java.util.concurrent.TimeoutException) {return@waitFor false}
            val current=instrumentation.uiAutomation.rootInActiveWindow ?: return@waitFor false
            if(!current.refresh() || !current.packageName.toString().endsWith(".documentsui"))return@waitFor false
            val found=current.findAccessibilityNodeInfosByText(text).orEmpty()
            for(n in found) {
                if(!n.refresh())continue
                val bounds=Rect();n.getBoundsInScreen(bounds)
                // DocumentsUI RecyclerView selection can be handled by a touch listener;
                // a visible filename need not expose a clickable accessibility ancestor.
                if(n.text?.toString()==text && n.isVisibleToUser && !bounds.isEmpty) {selected=bounds;return@waitFor true}
            }
            false
        }
        val bounds=requireNotNull(selected);val down=SystemClock.uptimeMillis()
        val pointer=MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_FINGER}
        val coordinates=MotionEvent.PointerCoords().apply {x=bounds.exactCenterX();y=bounds.exactCenterY();pressure=1f;size=1f}
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            // The convenience obtain(x,y) leaves TOOL_TYPE_UNKNOWN even after setSource.
            // Modern DocumentsUI selection routes finger events by tool type as well as source.
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,arrayOf(pointer),arrayOf(coordinates),
                0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
            try {assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true))}finally {event.recycle()}
        }
    }
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).toList()
    private fun cleanup(f:SyntheticAttachmentFixture) {
        val keys=mutableSetOf("notes.local.${f.id}.notes.wrap")
        try {
            f.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT stage_alias_uuid,active_alias_uuid FROM attachment_journal").use {c->while(c.moveToNext()) {
                keys+="notes.obj.v1.s.${c.getString(0)}";keys+="notes.obj.v1.a.${c.getString(1)}"
            }}
        }finally {
            f.close();val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            keys.forEach {store.deleteEntry(it)} // Exact synthetic fixture ownership; no production deletion API.
            check(f.directory.name=="synthetic-medflow-${f.id}");f.directory.deleteRecursively()
        }
    }
    @Test fun realSafPdfPngBinaryCommitReopenAndViewWithExistingAssociations()=runBlocking {
        assertFalse(AttachmentFlowController.PUBLIC_ENTRY_ENABLED)
        for(kind in listOf("pdf","png","binary")) {
            var activity:AttachmentOperationTestActivity?=null
            val scenario=ActivityScenario.launch(AttachmentOperationTestActivity::class.java)
            try {
                scenario.onActivity {activity=it}
                waitFor("fixture") {activity?.message=="SYNTHETIC ready"}
                val lab=kind=="png"
                click(if(lab)"Use synthetic lab result" else "Use synthetic appointment")
                if(kind=="pdf") {
                    click("Choose synthetic attachment")
                    waitFor("real DocumentsUI") {instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("SYNTHETIC sample.pdf").orEmpty().isNotEmpty()}
                    assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                    waitFor("actual SAF cancellation") {activity?.message=="SYNTHETIC cancelled or refused"}
                    val cancelled=requireNotNull(activity?.flow)
                    waitFor("resumed cancellation owner") {runCatching {requireNotNull(activity).fixture.runtime.checkRequest()}.isSuccess}
                    requireNotNull(activity).fixture.runtime.attachments(cancelled.request.target,"SYNTHETIC cancelled request",cancelled.request.operation,cancelled.request.document).use {
                        assertTrue(it.documents().isEmpty())
                    }
                }
                click("Choose synthetic attachment")
                click("SYNTHETIC sample.${if(kind=="binary")"bin" else kind}")
                waitFor("selected") {activity?.message=="SYNTHETIC selected; confirm"}
                click("Confirm synthetic import")
                waitFor("real commit") {activity?.flow?.state?.value is AttachmentFlowController.State.Saved}
                val a=requireNotNull(activity);val flow=requireNotNull(a.flow)
                val receipt=(flow.state.value as AttachmentFlowController.State.Saved).receipt
                assertEquals(if(lab)MedicalAttachmentOperations.Kind.LAB else MedicalAttachmentOperations.Kind.APPOINTMENT,flow.request.target.kind)
                val entries=flow.documents();assertEquals(1,entries.size);assertEquals(receipt.document,entries.single().document)
                assertEquals("ACTIVE",entries.single().linkState)
                val otherTarget=if(lab)a.fixture.target else a.fixture.labTarget
                a.fixture.runtime.attachments(otherTarget,"SYNTHETIC other record").use {assertTrue("Equal timestamps must not auto-associate",it.documents().isEmpty())}
                val expected=SyntheticDocumentsProvider.sample(kind)
                withContext(Dispatchers.IO) {
                    flow.open(receipt.document).use {r->
                        val actual=ByteArray(r.size.toInt());var p=0;while(p<actual.size) {val n=r.readAt(p.toLong(),actual,p,actual.size-p);assertTrue(n>0);p+=n}
                        assertEquals(hash(expected),hash(actual));actual.fill(0)
                    }
                }
                expected.fill(0)
                click("Read saved attachment");waitFor("authorized view") {a.message=="SYNTHETIC read verified"}
                assertEquals(MedicalAttachmentOperations.Export.COMPLETE,flow.backupEligibility())
                // Actually destroy/recreate the page and close/reopen its encrypted connection.
                // Only neutral scope/operation/typed target survive; no URI/provider replay.
                val oldRead=flow.open(receipt.document)
                try {
                    scenario.recreate();scenario.onActivity {activity=it}
                    val next=requireNotNull(activity)
                    assertNotSame(a,next);waitFor("reopened fixture") {next.message=="SYNTHETIC ready"}
                    val revoked=ByteArray(16) {7}
                    try {oldRead.readAt(0,revoked,0,revoked.size);fail("Closed page retained read access")}catch(_:IllegalStateException){}
                    assertTrue(revoked.all {it==0.toByte()})
                    click("Reconcile operation");waitFor("read-only committed reconciliation") {next.flow?.state?.value is AttachmentFlowController.State.Saved}
                    val reopened=requireNotNull(next.flow)
                    assertEquals(receipt,(reopened.state.value as AttachmentFlowController.State.Saved).receipt)
                    assertEquals(flow.request.target,reopened.request.target);assertEquals(1,reopened.documents().size)
                    click("Read saved attachment");waitFor("view after database reopen") {next.message=="SYNTHETIC read verified"}
                    assertEquals(0,next.fixture.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {it.moveToFirst();it.getInt(0)})
                }finally {oldRead.close()}
            }finally {scenario.close();activity?.fixture?.let(::cleanup)}
        }
    }
    @Test fun cancelledFailureWrongRecordAndRevokedViewNeverCreateFalseSuccess()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<android.content.Context>();val f=SyntheticAttachmentFixture(app)
        try {
            f.initialize()
            val cancel=f.runtime.attachments(f.target,"SYNTHETIC");cancel.selected(ActivityResult(Activity.RESULT_CANCELED,null));assertEquals(AttachmentFlowController.State.Cancelled,cancel.state.value);assertTrue(cancel.documents().isEmpty());cancel.close()
            val failed=f.runtime.attachments(f.target,"SYNTHETIC")
            failed.selected(ActivityResult(Activity.RESULT_OK,Intent().setData(android.net.Uri.parse("content://net.plainnotes.app.test.synthetic-attachments/failure"))))
            assertEquals(AttachmentFlowController.State.Failed,failed.state.value);assertTrue(failed.documents().isEmpty());failed.close()
            // Freeze an actual worker before journal preparation. A concurrent status/recovery
            // action must not call NOT_FOUND a failed attempt or recover a still-live write.
            val scope=f.repository.currentGenerationScope();val armed=AtomicBoolean(false)
            val entered=CountDownLatch(1);val release=CountDownLatch(1)
            val backend=f.repository.forGeneration(scope) {
                f.runtime.checkRequest()
                if(armed.compareAndSet(true,false)) {entered.countDown();check(release.await(30,TimeUnit.SECONDS))}
            }.medicalAttachments()
            val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
            val pending=AttachmentFlowController(backend,session,AutoCloseable {session.revoke()},
                MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),f.target,"SYNTHETIC live attempt"),f.runtime::checkRequest)
            armed.set(true)
            val worker=async(Dispatchers.Default) {pending.selected(ActivityResult(Activity.RESULT_OK,Intent().setData(android.net.Uri.parse("content://net.plainnotes.app.test.synthetic-attachments/failure"))))}
            try {
                assertTrue(entered.await(30,TimeUnit.SECONDS));assertEquals(AttachmentFlowController.State.Pending,pending.state.value)
                pending.reconcile();assertEquals(AttachmentFlowController.State.ReconcileRequired,pending.state.value)
                pending.recover();assertEquals(AttachmentFlowController.State.ReconcileRequired,pending.state.value)
                release.countDown();worker.await();assertEquals(AttachmentFlowController.State.Failed,pending.state.value)
                assertTrue(pending.documents().isEmpty())
            }finally {release.countDown();worker.cancelAndJoin();pending.close()}
            val flow=f.runtime.attachments(f.target,"SYNTHETIC")
            flow.selected(ActivityResult(Activity.RESULT_OK,Intent().setData(android.net.Uri.parse("content://net.plainnotes.app.test.synthetic-attachments/mime"))))
            val saved=(flow.state.value as AttachmentFlowController.State.Saved).receipt
            assertEquals("application/octet-stream",flow.documents().single().mime) // Lying PNG provider, opaque bytes.
            val conflict=f.runtime.attachments(f.target,"SYNTHETIC",saved.operation,saved.document)
            conflict.selected(ActivityResult(Activity.RESULT_OK,Intent().setData(android.net.Uri.parse("content://net.plainnotes.app.test.synthetic-attachments/failure"))))
            assertEquals(AttachmentFlowController.State.ReconcileRequired,conflict.state.value)
            assertEquals(1,conflict.documents().size);conflict.close()
            val read=flow.open(saved.document)
            val viewer=withContext(Dispatchers.IO) {AttachmentViewer(app,read)}
            val bytes=withContext(Dispatchers.IO) {viewer.prefix()};assertTrue(bytes.any {it!=0.toByte()})
            val wrong=f.runtime.attachments(f.target,"SYNTHETIC",saved.operation,UUID.randomUUID());wrong.reconcile();assertEquals(AttachmentFlowController.State.ReconcileRequired,wrong.state.value);wrong.close()
            f.runtime.pause();assertTrue(bytes.all {it==0.toByte()})
            try {withContext(Dispatchers.IO) {viewer.prefix()};fail("Revoked view published bytes")}catch(_:IllegalStateException){}
            viewer.close();flow.close()
        }finally {cleanup(f)}
    }
}
