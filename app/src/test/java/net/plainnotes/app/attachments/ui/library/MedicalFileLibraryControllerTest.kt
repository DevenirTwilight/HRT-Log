package net.plainnotes.app.attachments.ui.library

import android.content.Intent
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.*
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.attachments.ui.*
import net.plainnotes.app.data.MedicalAttachmentOperations as Ops
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class MedicalFileLibraryControllerTest {
    private val document=Ops.Document(UUID.randomUUID(),1,"synthetic","application/pdf","ACTIVE","ACTIVE")
    private val target=Ops.Target(Ops.Kind.LAB_PANEL,17,UUID.randomUUID())
    internal class Gateway:MedicalFileGateway {
        override val target:Ops.Target?=null
        override var scopeEnded=false
        var docs=listOf<Ops.Document>();var failList=false;var failCommitResponse=false
        var writes=0;var reads=0;var afterCommit:(()->Unit)?=null
        var relation:Pair<UUID,UUID>?=null;var life:Pair<UUID,UUID>?=null;var fact:Ops.RelationReceipt?=null
        val revoked=mutableListOf<()->Unit>()
        override fun pending():Pair<UUID,UUID>?=null
        override suspend fun documents():List<Ops.Document> {if(failList)error("synthetic refresh failure");return docs}
        override suspend fun pickerIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT)
        override suspend fun preview(document:UUID):MedicalAttachmentGateway.Preview=error("not used")
        override suspend fun newImport(title:String):MedicalAttachmentGateway.Import=error("not used")
        override suspend fun reconcile(operation:UUID,document:UUID)=AttachmentFlowController.State.ReconcileRequired
        override fun onRevoked(listener:()->Unit):AutoCloseable {revoked+=listener;return AutoCloseable{revoked-=listener}}
        override suspend fun relationships(document:UUID)=emptyList<Ops.Relationship>()
        override suspend fun resolveTarget(target:Ops.Target)=target
        override suspend fun associate(operation:UUID,target:Ops.Target,document:UUID,revision:Long,unlink:Ops.Relationship?):Ops.RelationReceipt {
            writes++;val receipt=Ops.RelationReceipt(operation,Ops.Phase.COMMITTED,UUID.randomUUID(),1)
            fact=receipt;afterCommit?.invoke();if(failCommitResponse)error("lost reply");return receipt
        }
        override suspend fun relationStatus(operation:UUID,document:UUID):Ops.RelationReceipt? {reads++;check(relation==operation to document);return fact}
        override suspend fun trash(document:UUID,revision:Long) {writes++;docs=docs.map {it.copy(state="TRASHED",revision=it.revision+1)}}
        override suspend fun restore(document:UUID,revision:Long) {writes++;docs=docs.map {it.copy(state="ACTIVE",revision=it.revision+1)}}
        override fun relationPending()=relation
        override fun relationRetain(operation:UUID,document:UUID) {check(relation==null);relation=operation to document}
        override fun relationResolved(operation:UUID,document:UUID) {check(relation==operation to document);relation=null}
        override fun lifecyclePending()=life
        override fun lifecycleRetain(operation:UUID,document:UUID) {check(life==null);life=operation to document}
        override fun lifecycleResolved(operation:UUID,document:UUID) {check(life==operation to document);life=null}
    }
    private fun controller(g:Gateway)=MedicalFileLibraryController(g,CoroutineScope(Dispatchers.Unconfined),Dispatchers.Unconfined).also {it.refresh()}
    @Test fun realCommitIsNotReclassifiedWhenListRefreshFails() {
        val g=Gateway().apply {docs=listOf(document);afterCommit={failList=true}}
        controller(g).use {c->c.relate(document,target);assertEquals(1,g.writes);assertEquals(MedicalFileLibraryController.Outcome.LIST_STALE,c.state.value.outcome);assertNull(g.relation)}
    }
    @Test fun lostResponseOnlyReadsTheSameReceiptAndNeverReplays() {
        val g=Gateway().apply {docs=listOf(document);failCommitResponse=true}
        controller(g).use {c->c.relate(document,target);assertEquals(MedicalFileLibraryController.Outcome.UNKNOWN,c.state.value.outcome)
            c.relate(document,target);assertEquals(1,g.writes);c.checkStatus();assertEquals(1,g.reads);assertEquals(1,g.writes)
            assertEquals(MedicalFileLibraryController.Outcome.COMMITTED,c.state.value.outcome);assertNull(g.relation)}
    }
    @Test fun pendingRelationshipRemainsUnknownAcrossNewControllerAndNoWritesAreAllowed() {
        val op=UUID.randomUUID();val g=Gateway().apply {docs=listOf(document);relation=op to document.document;fact=Ops.RelationReceipt(op,Ops.Phase.PENDING,null,null)}
        controller(g).use {c->assertFalse(c.canWrite());c.checkStatus();assertEquals(MedicalFileLibraryController.Outcome.UNKNOWN,c.state.value.outcome);assertNotNull(g.relation);assertEquals(0,g.writes)}
    }
    @Test fun revokedScopeClearsMetadataAndRefusesLateActions() {
        val g=Gateway().apply {docs=listOf(document)}
        controller(g).use {c->c.select(document.document);g.scopeEnded=true;g.revoked.toList().forEach {it()}
            assertTrue(c.state.value.revoked);assertTrue(c.state.value.documents.isEmpty());c.relate(document,target);c.recycle(document,false);assertEquals(0,g.writes)}
    }
    @Test fun interruptedLifecycleChecksPresentStateWithoutClaimingItsHistoricalCommit() {
        val g=Gateway().apply {docs=listOf(document.copy(state="TRASHED",revision=2));life=UUID.randomUUID() to document.document}
        controller(g).use {c->assertFalse(c.canWrite());c.checkStatus();assertEquals(MedicalFileLibraryController.Outcome.CHECKED,c.state.value.outcome);assertNull(g.life);assertEquals(0,g.writes)}
    }
}
