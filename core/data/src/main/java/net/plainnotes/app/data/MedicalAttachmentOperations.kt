package net.plainnotes.app.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.domain.AttachmentInputStream
import net.plainnotes.app.domain.AttachmentSession
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID

/** Application-facing, scoped worker boundary. No UI entry, URI retention, paths or key access.
 * A file is opaque content: signature detection is NOT a claim that PDF/image parsing is safe.
 */
class MedicalAttachmentOperations internal constructor(private val access:DatabaseAccess,
    private val expected:LibraryRequestScope,private val permission:()->Unit,
    private val checkpoint:(AttachmentPersistentStore.Boundary)->Unit={},
    private val storageBudget:AttachmentStorageBudget=AttachmentStorageBudget()) {
    class ExistingOperation:IllegalStateException("Operation already exists; explicitly reconcile before a new selection")
    enum class Kind { APPOINTMENT, LAB }
    data class Target(val kind:Kind,val id:Long) {init {require(id>0)}}
    data class Import(val operation:UUID,val document:UUID,val target:Target,val title:String,val revision:Long=0)
    data class Receipt(val operation:UUID,val document:UUID,val version:UUID,val revision:Long)
    data class Document(val document:UUID,val revision:Long,val title:String,val mime:String,val state:String,val linkState:String)
    enum class Export { COMPLETE, REFUSED, UNKNOWN }
    enum class Phase { NOT_FOUND, PENDING, ABORTED, COMMITTED }
    data class Status(val phase:Phase,val receipt:Receipt?)
    interface Read:AutoCloseable {
        val size:Long
        fun readAt(position:Long,destination:ByteArray,offset:Int,length:Int):Int
        fun check()
        fun <T> publish(block:()->T):T
        fun own(resource:AutoCloseable):AutoCloseable
    }
    private fun boundary(at:AttachmentPersistentStore.Boundary) {
        if(at!=AttachmentPersistentStore.Boundary.COMMITTED) {permission();check(access.current==Space.PRIMARY)}
        checkpoint(at) // Real post-COMMIT receipts must not be turned into a permission/refresh failure.
    }
    private suspend fun <T> work(cap:AttachmentSession.Capability,block:(NotesDatabase,AttachmentIdentity.Scope)->T):T=withContext(Dispatchers.IO) {
        access.holdGeneration().use {
            permission();check(access.current==Space.PRIMARY)
            val db=access.get(Space.PRIMARY);val s=requireNotNull(AttachmentIdentity.current(db.openHelper.writableDatabase))
            check(expected==LibraryRequestScope(DatabaseAccess.handleIdentity(db),s.dataset,s.epoch)) {"Stale attachment request"}
            cap.publishFor(UUID.fromString(s.dataset),UUID.fromString(s.epoch)) {permission()}
            block(db,s)
        }
    }
    private fun record(db:NotesDatabase,s:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,t:Target)=
        AttachmentTransaction.commit(db,s,cap) {permission();AttachmentIdentity.bind(db.openHelper.writableDatabase,s,t.kind.name,t.id)}
    private fun AttachmentPersistentStore.Committed.receipt()=Receipt(operation,document,version,revision)

    /** One provider read, bounded memory only; ignore its declared size, name and MIME entirely.
     * Reusing an operation calls reconcile explicitly, never silently reopens a changed URI.
     */
    suspend fun import(cap:AttachmentSession.Capability,request:Import,uri:Uri):Receipt=work(cap) {db,s->
        require(uri.scheme=="content") {"SAF content URI required"}
        require(request.title.isNotBlank() && request.title.length<=256)
        val store=AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget)
        if(store.operationState(s,cap,request.operation)!=null)throw ExistingOperation()
        val record=record(db,s,cap,request.target)
        val maximum=AttachmentObjectStreams.MAXIMUM_BYTES.toInt()
        val scratch=ByteArray(maximum)
        cap.ownBytes(scratch).use {
            var used=0
            AttachmentInputStream.open(cap,requireNotNull(access.applicationContext.contentResolver.openInputStream(uri)),maximum.toLong()).use {input->
                while(used<scratch.size) {
                    permission();val n=input.read(scratch,used,scratch.size-used);if(n<0)break;used+=n
                }
                if(used==maximum)check(input.read()==-1) {"Attachment too large"}
            }
            require(used>0) {"Empty attachment"}
            permission();cap.check()
            val bytes=scratch.copyOf(used)
            cap.ownBytes(bytes).use {
                val mime=when {
                    used>=5 && bytes.copyOfRange(0,5).contentEquals("%PDF-".toByteArray())->"application/pdf"
                    used>=8 && bytes.copyOfRange(0,8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))->"image/png"
                    used>=3 && bytes[0]==(-1).toByte() && bytes[1]==(-40).toByte() && bytes[2]==(-1).toByte()->"image/jpeg"
                    else->"application/octet-stream"
                }
                val hash=MedicalArchiveCrypto.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
                permission()
                val saved=store.save(s,cap,AttachmentPersistentStore.Request(request.operation,request.document,UUID.fromString(record),request.revision,request.title,"REPORT",mime,used.toLong(),hash)) {ByteArrayInputStream(bytes)}
                // The store returned only after real COMMIT and independent full authentication.
                // Do not turn later list/preview/backup failure into a false save failure.
                saved.receipt()
            }
        }
    }
    suspend fun reconcile(cap:AttachmentSession.Capability,operation:UUID,target:Target?=null,document:UUID?=null):Status=work(cap) {db,s->
        val store=AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget)
        val state=store.operationState(s,cap,operation)
        if(state!=null && target!=null && document!=null)AttachmentTransaction.commit(db,s,cap) {
            permission()
            db.openHelper.writableDatabase.query("""SELECT r.target_kind,r.legacy_id,j.document_uuid FROM attachment_journal j
                JOIN attachment_record_identity r ON r.store_uuid=j.store_uuid AND r.record_epoch=j.record_epoch AND r.record_uuid=j.record_uuid
                WHERE j.store_uuid=? AND j.record_epoch=? AND j.operation_uuid=?""",arrayOf(s.dataset,s.epoch,operation.toString())).use {
                check(it.moveToFirst() && it.getString(0)==target.kind.name && it.getLong(1)==target.id && it.getString(2)==document.toString()) {"Conflicting operation identity"}
            }
        }
        Status(when {state==null->Phase.NOT_FOUND;state.committed!=null->Phase.COMMITTED;state.phase=="ABORTED"->Phase.ABORTED;else->Phase.PENDING},state?.committed?.receipt())
    }
    suspend fun recover(cap:AttachmentSession.Capability):Int=work(cap) {db,s->AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).recover(s,cap)}
    suspend fun documents(cap:AttachmentSession.Capability,target:Target):List<Document> =work(cap) {db,s->
        AttachmentTransaction.commit(db,s,cap) {
            permission()
            db.openHelper.writableDatabase.query("""SELECT d.document_uuid,d.revision,d.title,v.mime,d.state,l.state FROM attachment_record_identity r
                JOIN attachment_link l ON l.store_uuid=r.store_uuid AND l.record_epoch=r.record_epoch AND l.record_uuid=r.record_uuid
                JOIN attachment_document d ON d.store_uuid=l.store_uuid AND d.document_uuid=l.document_uuid
                JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid
                WHERE r.store_uuid=? AND r.record_epoch=? AND r.target_kind=? AND r.legacy_id=? AND r.state='ACTIVE' ORDER BY d.document_uuid""",arrayOf(s.dataset,s.epoch,target.kind.name,target.id)).use {c->
                buildList {while(c.moveToNext()) {permission();add(Document(UUID.fromString(c.getString(0)),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5)))}}
            }
        }
    }
    suspend fun open(cap:AttachmentSession.Capability,target:Target,document:UUID):Read=work(cap) {db,s->
        val owner=cap.fork()
        try {
            val version=AttachmentTransaction.commit(db,s,owner) {
                permission()
                db.openHelper.writableDatabase.query("""SELECT d.active_version_uuid FROM attachment_record_identity r JOIN attachment_link l
                    ON l.store_uuid=r.store_uuid AND l.record_epoch=r.record_epoch AND l.record_uuid=r.record_uuid
                    JOIN attachment_document d ON d.store_uuid=l.store_uuid AND d.document_uuid=l.document_uuid
                    WHERE d.state='ACTIVE' AND r.store_uuid=? AND r.record_epoch=? AND r.target_kind=? AND r.legacy_id=? AND r.state='ACTIVE' AND l.document_uuid=? AND l.state='ACTIVE'""",
                    arrayOf(s.dataset,s.epoch,target.kind.name,target.id,document.toString())).use {check(it.moveToFirst()) {"No authorized explicit relationship"};it.getString(0)}
            }
            val reader=AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).open(s,owner,document)
            val lease=owner.own(reader)
            object:Read {
                private fun authorized() {
                    permission();check(access.current==Space.PRIMARY);owner.check();check(AttachmentIdentity.current(db.openHelper.writableDatabase)==s);DatabaseAccess.assertActiveHandle(db)
                    db.openHelper.writableDatabase.query("""SELECT 1 FROM attachment_record_identity r JOIN attachment_link l
                        ON l.store_uuid=r.store_uuid AND l.record_epoch=r.record_epoch AND l.record_uuid=r.record_uuid
                        JOIN attachment_document d ON d.store_uuid=l.store_uuid AND d.document_uuid=l.document_uuid
                        WHERE r.store_uuid=? AND r.record_epoch=? AND r.target_kind=? AND r.legacy_id=? AND r.state='ACTIVE'
                        AND l.document_uuid=? AND l.state='ACTIVE' AND d.state='ACTIVE' AND d.active_version_uuid=?
                        AND EXISTS(SELECT 1 FROM attachment_version v WHERE v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid AND v.status='VERIFIED')""",
                        arrayOf(s.dataset,s.epoch,target.kind.name,target.id,document.toString(),version)).use {check(it.moveToFirst()) {"Attachment relationship/version revoked"}}
                }
                override fun check()=publish {Unit}
                override fun <T> publish(block:()->T):T=DatabaseAccess.withActiveHandle(db) {owner.publish {authorized();block()}}
                override fun own(resource:AutoCloseable):AutoCloseable=try {publish {owner.own(resource)}}catch(e:Throwable) {resource.close();throw e}
                override val size get()=run {check();val n=reader.size;publish {n}}
                override fun readAt(position:Long,destination:ByteArray,offset:Int,length:Int):Int {
                    require(offset>=0 && length>=0 && length<=destination.size-offset)
                    try {check();val n=reader.readAt(position,destination,offset,length);return publish {n}}
                    catch(e:Throwable) {destination.fill(0,offset,offset+length);throw e}
                }
                override fun close() {try {lease.close()}finally {owner.close()}}
            }
        }catch(e:Throwable) {owner.close();throw e}
    }
    suspend fun trash(cap:AttachmentSession.Capability,document:UUID,revision:Long)=work(cap) {db,s->AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).trash(s,cap,document,revision)}
    suspend fun restore(cap:AttachmentSession.Capability,document:UUID,revision:Long)=work(cap) {db,s->AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).restore(s,cap,document,revision)}
    suspend fun writeArchive(cap:AttachmentSession.Capability,password:CharArray,output:java.io.OutputStream) {
        try {work(cap) {_,s->LibraryLifecycle(access).writeArchive(s,cap,UUID.randomUUID(),password,output)}}finally {password.fill('\u0000')}
    }
    suspend fun exportEligibility(cap:AttachmentSession.Capability):Export=work(cap) {_,s->
        Export.valueOf(LibraryLifecycle(access).exportEligibility(s,cap,UUID.randomUUID()).eligibility.name)
    }
}
