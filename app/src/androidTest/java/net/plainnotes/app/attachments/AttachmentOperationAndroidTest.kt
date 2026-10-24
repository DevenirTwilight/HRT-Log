package net.plainnotes.app.attachments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.result.ActivityResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import net.plainnotes.app.data.*
import net.plainnotes.app.debug.attachments.AttachmentOperationTestActivity
import net.plainnotes.app.debug.attachments.SyntheticAttachmentFixture
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

/** Real DocumentsUI selection, real application controller, SQLCipher, Keystore and authorized viewer. */
class AttachmentOperationAndroidTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun waitFor(label:String,timeout:Long=30000,condition:()->Boolean) {
        val end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end) {if(condition())return;Thread.sleep(100)}
        fail("Synthetic timeout: $label")
    }
    private fun click(text:String) {
        waitFor(text) {
            val found=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text).orEmpty()
            for(n in found) {
                var p:AccessibilityNodeInfo?=n
                repeat(5) {val node=p;if(node!=null && node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return@waitFor true;p=node?.parent}
            }
            false
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
                click("Choose synthetic attachment")
                click("SYNTHETIC sample.${if(kind=="binary")"bin" else kind}")
                waitFor("selected") {activity?.message=="SYNTHETIC selected; confirm"}
                click("Confirm synthetic import")
                waitFor("real commit") {activity?.flow?.state?.value is AttachmentFlowController.State.Saved}
                val a=requireNotNull(activity);val flow=requireNotNull(a.flow)
                val receipt=(flow.state.value as AttachmentFlowController.State.Saved).receipt
                val entries=flow.documents();assertEquals(1,entries.size);assertEquals(receipt.document,entries.single().document)
                assertEquals("ACTIVE",entries.single().linkState)
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
                // A new page/controller reads committed facts rather than replaying the provider.
                val reopened=a.fixture.runtime.attachments(a.fixture.target,"SYNTHETIC report",receipt.operation,receipt.document)
                reopened.reconcile();assertTrue(reopened.state.value is AttachmentFlowController.State.Saved)
                assertEquals(1,reopened.documents().size);reopened.close()
                assertEquals(0,a.fixture.access.get(Space.PRIMARY).openHelper.writableDatabase.query("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'").use {it.moveToFirst();it.getInt(0)})
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
