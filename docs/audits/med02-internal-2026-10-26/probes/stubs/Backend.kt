package net.plainnotes.app.data
import android.net.Uri
import net.plainnotes.app.domain.AttachmentSession
import java.util.UUID
// In-memory commit-fact boundary only; this is NOT a Room/SQLCipher test.
class MedicalAttachmentOperations {
    class ExistingOperation:IllegalStateException()
    enum class Kind { APPOINTMENT, LAB }
    data class Target(val kind:Kind,val id:Long)
    data class Import(val operation:UUID,val document:UUID,val target:Target,val title:String,val revision:Long=0)
    data class Receipt(val operation:UUID,val document:UUID,val version:UUID,val revision:Long)
    data class Document(val document:UUID,val revision:Long,val title:String,val mime:String,val state:String,val linkState:String)
    enum class Export {COMPLETE, REFUSED, UNKNOWN}
    enum class Phase {NOT_FOUND, PENDING, ABORTED, COMMITTED}
    data class Status(val phase:Phase,val receipt:Receipt?)
    interface Read:AutoCloseable
    val commits=linkedMapOf<UUID,Receipt>()
    var loseFirstReply=true
    var reconciles=0
    suspend fun import(cap:AttachmentSession.Capability,r:Import,uri:Uri):Receipt {
        cap.check();val receipt=Receipt(r.operation,r.document,UUID.randomUUID(),1)
        commits[r.operation]=receipt
        if(loseFirstReply) {loseFirstReply=false;throw java.io.IOException("synthetic lost reply after COMMIT")}
        return receipt
    }
    suspend fun reconcile(cap:AttachmentSession.Capability,op:UUID,target:Target,document:UUID):Status {
        cap.check();reconciles++;return Status(Phase.COMMITTED,commits[op])
    }
    suspend fun recover(cap:AttachmentSession.Capability) {cap.check()}
    suspend fun documents(cap:AttachmentSession.Capability,target:Target)=commits.values.map {Document(it.document,1,"synthetic","application/pdf","ACTIVE","ACTIVE")}
    suspend fun open(cap:AttachmentSession.Capability,target:Target,document:UUID):Read=object:Read {override fun close(){}}
    suspend fun exportEligibility(cap:AttachmentSession.Capability)=Export.COMPLETE
}
