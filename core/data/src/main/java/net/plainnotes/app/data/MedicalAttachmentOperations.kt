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
    enum class Kind { APPOINTMENT, LAB, LAB_PANEL }
    data class Target(val kind:Kind,val id:Long,val expectedUuid:UUID?=null,val memberLabId:Long?=null) {
        init {require(id>0);require(kind!=Kind.LAB_PANEL || expectedUuid!=null);require(memberLabId==null || kind==Kind.LAB_PANEL && memberLabId>0)}
    }
    data class Import(val operation:UUID,val document:UUID,val target:Target?,val title:String,val revision:Long=0)
    data class Receipt(val operation:UUID,val document:UUID,val version:UUID,val revision:Long)
    data class Document(val document:UUID,val revision:Long,val title:String,val mime:String,val state:String,val linkState:String,
        val linkCount:Int=0,val bytes:Long?=null,val importedUtc:Long?=null)
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
    /** Explicit idempotent activation in the existing authorized native transaction. */
    suspend fun activate(cap:AttachmentSession.Capability)=work(cap) {db,s->
        AttachmentTransaction.commit(db,s,cap) {permission();AttachmentActivationSchema.activate(db.openHelper.writableDatabase,s)}
    }
    private fun verifyTarget(db:NotesDatabase,t:Target?) {
        if(t==null) {check(db.openHelper.writableDatabase.version>=MedicalFilesSchema.VERSION);return}
        val raw=db.openHelper.writableDatabase
        if(t.kind!=Kind.LAB_PANEL && t.expectedUuid!=null) {
            val s=requireNotNull(AttachmentIdentity.current(raw))
            raw.query("SELECT 1 FROM attachment_record_identity WHERE store_uuid=? AND record_epoch=? AND target_kind=? AND legacy_id=? AND record_uuid=? AND state='ACTIVE'",
                arrayOf(s.dataset,s.epoch,t.kind.name,t.id,t.expectedUuid.toString())).use {
                check(it.moveToFirst()) {"Selected typed record identity changed"}
            }
        }
        if(t.kind==Kind.LAB_PANEL) {
            raw.query("SELECT uuid FROM lab_panel WHERE id=?",arrayOf(t.id)).use {
                check(it.moveToFirst() && it.getString(0)==t.expectedUuid.toString()) {"Blood draw no longer exists with the selected identity"}
            }
            t.memberLabId?.let {member->raw.query("SELECT 1 FROM lab_value WHERE id=? AND panel_id=?",arrayOf(member,t.id)).use {
                check(it.moveToFirst()) {"Result no longer belongs to this draw"}
            }}
        }
    }
    private fun bindTarget(db:NotesDatabase,s:AttachmentIdentity.Scope,t:Target?):String? {
        verifyTarget(db,t)
        return t?.let {AttachmentIdentity.bind(db.openHelper.writableDatabase,s,it.kind.name,it.id)}
    }
    private fun record(db:NotesDatabase,s:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,t:Target?)=
        AttachmentTransaction.commit(db,s,cap) {
            permission()
            // Refuse unsupported retained-generation imports BEFORE binding a new target or
            // opening a hostile provider. This is an early refusal, not a transferable permit:
            // save() independently rechecks under its own actual PREPARE transaction.
            DatabaseAccess.requireUnpartitionedStorage(db)
            bindTarget(db,s,t)
        }
    private fun AttachmentPersistentStore.Committed.receipt()=Receipt(operation,document,version,revision)

    /** One provider read, bounded memory only; ignore its declared size, name and MIME entirely.
     * Reusing an operation calls reconcile explicitly, never silently reopens a changed URI.
     */
    suspend fun import(cap:AttachmentSession.Capability,request:Import,uri:Uri):Receipt=work(cap) {db,s->
        require(uri.scheme=="content") {"SAF content URI required"}
        require(request.title.isNotBlank() && request.title.length<=256)
        val store=AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget)
        if(store.operationState(s,cap,request.operation)!=null)throw ExistingOperation()
        val joint=DatabaseAccess.managedGeneration(db)
        val admission=LibraryAttachmentAdmission(access)
        if(joint)admission.preflight(db,s,cap)
        val record=if(joint)null else record(db,s,cap,request.target)
        val maximum=AttachmentObjectStreams.MAXIMUM_BYTES.toInt()
        val scratch=ByteArray(maximum)
        cap.ownBytes(scratch).use {
            var used=0
            AttachmentInputStream.open(cap,AttachmentProviderStreams.input(access.applicationContext,uri,cap),maximum.toLong()).use {input->
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
                fun value(record:String?)=AttachmentPersistentStore.Request(request.operation,request.document,record?.let(UUID::fromString),request.revision,request.title,"REPORT",mime,used.toLong(),hash)
                val prepared=if(joint)admission.prepare(db,s,cap) {
                    permission();value(bindTarget(db,s,request.target))
                } else null
                val saver=if(prepared==null)store else AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget,allocation=prepared.second)
                val saved=saver.save(s,cap,prepared?.first ?: value(record)) {ByteArrayInputStream(bytes)}
                // The per-object directory lease has ended. STARTED still blocks competing
                // restore/import until the complete root inventory is checked and committed.
                prepared?.second?.finish(cap)
                // The store returned only after real COMMIT and independent full authentication.
                // Do not turn later list/preview/backup failure into a false save failure.
                saved.receipt()
            }
        }
    }
    suspend fun reconcile(cap:AttachmentSession.Capability,operation:UUID,target:Target?=null,document:UUID?=null):Status=work(cap) {db,s->
        val store=AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget)
        val state=store.operationState(s,cap,operation)
        if(state!=null && document!=null)AttachmentTransaction.commit(db,s,cap) {
            permission()
            db.openHelper.writableDatabase.query("""SELECT r.target_kind,r.legacy_id,j.document_uuid,j.record_uuid FROM attachment_journal j
                LEFT JOIN attachment_record_identity r ON r.store_uuid=j.store_uuid AND r.record_epoch=j.record_epoch AND r.record_uuid=j.record_uuid
                WHERE j.store_uuid=? AND j.record_epoch=? AND j.operation_uuid=?""",arrayOf(s.dataset,s.epoch,operation.toString())).use {
                check(it.moveToFirst() && (if(target==null)it.isNull(3) else it.getString(0)==target.kind.name && it.getLong(1)==target.id && (target.expectedUuid==null || it.getString(3)==target.expectedUuid.toString())) && it.getString(2)==document.toString()) {"Conflicting operation identity"}
            }
        }
        val reserved=if(DatabaseAccess.managedGeneration(db))LibraryAttachmentAdmission(access).reconcile(db,s,cap,operation) else null
        Status(when {state==null && reserved!=null->Phase.PENDING;state==null->Phase.NOT_FOUND;state.committed!=null->Phase.COMMITTED;state.phase=="ABORTED"->Phase.ABORTED;else->Phase.PENDING},state?.committed?.receipt())
    }
    suspend fun recover(cap:AttachmentSession.Capability):Int=work(cap) {db,s->AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).recover(s,cap)}
    suspend fun documents(cap:AttachmentSession.Capability,target:Target):List<Document> =work(cap) {db,s->
        verifyTarget(db,target)
        AttachmentTransaction.commit(db,s,cap) {
            permission()
            db.openHelper.writableDatabase.query("""SELECT d.document_uuid,d.revision,d.title,v.mime,d.state,l.state FROM attachment_record_identity r
                JOIN attachment_link l ON l.store_uuid=r.store_uuid AND l.record_epoch=r.record_epoch AND l.record_uuid=r.record_uuid
                JOIN attachment_document d ON d.store_uuid=l.store_uuid AND d.document_uuid=l.document_uuid
                JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid
                WHERE r.store_uuid=? AND r.record_epoch=? AND r.target_kind=? AND r.legacy_id=? AND r.state='ACTIVE' AND l.state='ACTIVE' AND d.state='ACTIVE' ORDER BY d.document_uuid""",arrayOf(s.dataset,s.epoch,target.kind.name,target.id)).use {c->
                buildList {while(c.moveToNext()) {permission();add(Document(UUID.fromString(c.getString(0)),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5)))}}
            }
        }
    }

    /** Retract one exact association; retain all ciphertext, keys, quota and other links. */
    suspend fun unlink(cap:AttachmentSession.Capability,target:Target,document:UUID):Boolean=work(cap) {db,s->
        verifyTarget(db,target)
        AttachmentTransaction.commit(db,s,cap) {
            permission()
            val raw=db.openHelper.writableDatabase
            val link=raw.query("""SELECT l.link_uuid FROM attachment_record_identity r
                JOIN attachment_link l ON l.store_uuid=r.store_uuid AND l.record_epoch=r.record_epoch AND l.record_uuid=r.record_uuid
                JOIN attachment_document d ON d.store_uuid=l.store_uuid AND d.document_uuid=l.document_uuid
                WHERE r.store_uuid=? AND r.record_epoch=? AND r.target_kind=? AND r.legacy_id=? AND r.state='ACTIVE'
                  AND l.document_uuid=? AND l.state='ACTIVE' AND d.state='ACTIVE'""",
                arrayOf(s.dataset,s.epoch,target.kind.name,target.id,document.toString())).use {c->
                if(!c.moveToFirst())null else c.getString(0).also {check(!c.moveToNext()) {"Ambiguous attachment relationship"}}
            } ?: return@commit false
            val update=raw.compileStatement("UPDATE attachment_link SET state='RETRACTED',revision=revision+1 WHERE store_uuid=? AND record_epoch=? AND link_uuid=? AND state='ACTIVE'")
            update.bindString(1,s.dataset);update.bindString(2,s.epoch);update.bindString(3,link)
            check(update.executeUpdateDelete()==1) {"Attachment relationship changed during unlink"}
            true
        }
    }

    /** Private library and record views share the same reader, held generation and revocation checks. */
    private fun authorizedVersion(db:NotesDatabase,s:AttachmentIdentity.Scope,target:Target?,document:UUID,expectedVersion:String?=null):String {
        verifyTarget(db,target)
        val args=mutableListOf<Any>(s.dataset,document.toString())
        val relationship=if(target==null) "" else {
            args.addAll(listOf(s.epoch,target.kind.name,target.id))
            """ AND EXISTS(SELECT 1 FROM attachment_link l JOIN attachment_record_identity r
                ON r.store_uuid=l.store_uuid AND r.record_epoch=l.record_epoch AND r.record_uuid=l.record_uuid
                WHERE l.store_uuid=d.store_uuid AND l.document_uuid=d.document_uuid AND l.state='ACTIVE'
                AND r.record_epoch=? AND r.target_kind=? AND r.legacy_id=? AND r.state='ACTIVE')"""
        }
        return db.openHelper.writableDatabase.query("""SELECT d.active_version_uuid FROM attachment_document d
            JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid
            WHERE d.store_uuid=? AND d.document_uuid=? AND d.state='ACTIVE' AND v.status='VERIFIED'$relationship""",args.toTypedArray()).use {
            check(it.moveToFirst()) {"No authorized document/relationship"}
            it.getString(0).also {value->check(expectedVersion==null || value==expectedVersion) {"Document version revoked"}}
        }
    }
    suspend fun open(cap:AttachmentSession.Capability,target:Target?,document:UUID):Read=work(cap) {db,s->
        val owner=cap.fork()
        try {
            val version=AttachmentTransaction.commit(db,s,owner) {permission();authorizedVersion(db,s,target,document)}
            val reader=AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).open(s,owner,document)
            val lease=owner.own(reader)
            object:Read {
                private fun authorized() {
                    permission();check(access.current==Space.PRIMARY);owner.check()
                    check(AttachmentIdentity.current(db.openHelper.writableDatabase)==s);DatabaseAccess.assertActiveHandle(db)
                    authorizedVersion(db,s,target,document,version)
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
    /** All current-library documents, including unassociated originals. No retained library is silently imported here. */
    suspend fun libraryDocuments(cap:AttachmentSession.Capability,includeTrash:Boolean=false):List<Document> =work(cap) {db,s->
        AttachmentTransaction.commit(db,s,cap) {
            permission()
            db.openHelper.writableDatabase.query("""SELECT d.document_uuid,d.revision,d.title,v.mime,d.state,
                (SELECT COUNT(*) FROM attachment_link l JOIN attachment_record_identity r ON r.store_uuid=l.store_uuid AND r.record_epoch=l.record_epoch AND r.record_uuid=l.record_uuid
                 WHERE l.store_uuid=d.store_uuid AND l.document_uuid=d.document_uuid AND l.record_epoch=? AND l.state='ACTIVE' AND r.state='ACTIVE'),v.plaintext_bytes,
                (SELECT MIN(o.updated_utc) FROM attachment_operation o WHERE o.store_uuid=d.store_uuid AND o.document_uuid=d.document_uuid AND o.kind='IMPORT' AND o.phase IN('COMMITTED','CLEANED')) AS imported
                FROM attachment_document d
                JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid
                WHERE d.store_uuid=? AND (d.state='ACTIVE' OR (?=1 AND d.state='TRASHED')) AND v.status='VERIFIED' ORDER BY imported DESC,d.rowid DESC""",arrayOf(s.epoch,s.dataset,if(includeTrash)1 else 0)).use {c->
                buildList {while(c.moveToNext()){permission();add(Document(UUID.fromString(c.getString(0)),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),"ACTIVE",c.getInt(5),c.getLong(6),if(c.isNull(7))null else c.getLong(7)))}}
            }
        }
    }
    /** MED-04: what the user typed about a file. Every field optional; nothing here is inferred from the file, its name or the clock. */
    data class Detail(val category:String?=null,val date:String?=null,val institution:String?=null,val revision:Long=0) {
        init {
            require(category==null || category in MedicalFileDetailSchema.categories)
            require(date==null || runCatching {java.time.LocalDate.parse(date)}.getOrNull()?.toString()==date)
            require(institution==null || institution.isNotBlank() && institution==institution.trim() && institution.length<=MedicalFileDetailSchema.INSTITUTION_MAX && institution.none {it.code<32 || it.code==127})
        }
        val empty get()=category==null && date==null && institution==null
        companion object {
            const val INSTITUTION_MAX=MedicalFileDetailSchema.INSTITUTION_MAX
            /** User text to a stored value: trimmed, blank means not entered, over-long input is rejected rather than cut. */
            fun institutionOf(text:String):String? {val t=text.trim();require(t.length<=MedicalFileDetailSchema.INSTITUTION_MAX && t.none {it.code<32 || it.code==127});return t.ifEmpty {null}}
        }
    }
    suspend fun details(cap:AttachmentSession.Capability):Map<UUID,Detail> =work(cap) {db,s->
        AttachmentTransaction.commit(db,s,cap) {
            permission()
            db.openHelper.writableDatabase.query("SELECT document_uuid,file_category,document_date,institution,detail_revision FROM ${MedicalFileDetailSchema.TABLE} WHERE store_uuid=?",arrayOf(s.dataset)).use {c->
                buildMap {while(c.moveToNext()){permission();put(UUID.fromString(c.getString(0)),Detail(c.getString(1),c.getString(2),c.getString(3),c.getLong(4)))}}
            }
        }
    }
    /** Compare-and-set on the detail's own revision (0 = none saved yet). Only an active file can be edited; saving never touches the original, its key or its links. */
    suspend fun saveDetail(cap:AttachmentSession.Capability,document:UUID,expectedRevision:Long,detail:Detail):Long=work(cap) {db,s->
        require(expectedRevision>=0)
        AttachmentTransaction.commit(db,s,cap) {
            permission();val raw=db.openHelper.writableDatabase
            raw.query("SELECT 1 FROM attachment_document WHERE store_uuid=? AND document_uuid=? AND state='ACTIVE'",arrayOf(s.dataset,document.toString())).use {check(it.moveToFirst()) {"Only an active file can be edited"}}
            val now=System.currentTimeMillis()
            if(expectedRevision==0L) raw.compileStatement("INSERT INTO ${MedicalFileDetailSchema.TABLE}(store_uuid,document_uuid,file_category,document_date,institution,detail_revision,updated_utc) VALUES(?,?,?,?,?,1,?)").use {st->
                st.bindString(1,s.dataset);st.bindString(2,document.toString())
                detail.category?.let {st.bindString(3,it)} ?: st.bindNull(3);detail.date?.let {st.bindString(4,it)} ?: st.bindNull(4);detail.institution?.let {st.bindString(5,it)} ?: st.bindNull(5)
                st.bindLong(6,now);check(st.executeInsert()>=0)
            } else raw.compileStatement("UPDATE ${MedicalFileDetailSchema.TABLE} SET file_category=?,document_date=?,institution=?,detail_revision=detail_revision+1,updated_utc=? WHERE store_uuid=? AND document_uuid=? AND detail_revision=?").use {st->
                detail.category?.let {st.bindString(1,it)} ?: st.bindNull(1);detail.date?.let {st.bindString(2,it)} ?: st.bindNull(2);detail.institution?.let {st.bindString(3,it)} ?: st.bindNull(3)
                st.bindLong(4,now);st.bindString(5,s.dataset);st.bindString(6,document.toString());st.bindLong(7,expectedRevision);check(st.executeUpdateDelete()==1) {"File details changed elsewhere"}
            }
            expectedRevision+1
        }
    }
    /** The document display name and optional user information are one CAS transaction. The original
     * import request/journal, versions, keys and links are immutable inputs, never rewritten here. */
    data class FileInfoReceipt(val document:UUID,val documentRevision:Long,val detailRevision:Long,val title:String,val detail:Detail)
    class FileInfoConflict:IllegalStateException("File information changed elsewhere")
    suspend fun saveFileInfo(cap:AttachmentSession.Capability,document:UUID,expectedDocumentRevision:Long,expectedDetailRevision:Long,
        title:String,detail:Detail):FileInfoReceipt=work(cap) {db,s->
        require(validTitle(title));require(expectedDocumentRevision>=1 && expectedDocumentRevision<Long.MAX_VALUE)
        require(expectedDetailRevision>=0 && expectedDetailRevision<Long.MAX_VALUE)
        AttachmentTransaction.commit(db,s,cap) {
            permission();val raw=db.openHelper.writableDatabase
            val current=raw.query("SELECT revision,state,title FROM attachment_document WHERE store_uuid=? AND document_uuid=?",arrayOf(s.dataset,document.toString())).use {c->
                if(c.moveToFirst())Triple(c.getLong(0),c.getString(1),c.getString(2)) else null
            }
            if(current==null || current.first!=expectedDocumentRevision || current.second!="ACTIVE")throw FileInfoConflict()
            val renamed=current.third!=title
            val currentDetail=raw.query("SELECT detail_revision FROM ${MedicalFileDetailSchema.TABLE} WHERE store_uuid=? AND document_uuid=?",arrayOf(s.dataset,document.toString())).use {c->if(c.moveToFirst())c.getLong(0) else 0L}
            if(currentDetail!=expectedDetailRevision)throw FileInfoConflict()
            if(renamed)raw.compileStatement("UPDATE attachment_document SET title=?,revision=revision+1 WHERE store_uuid=? AND document_uuid=? AND revision=? AND state='ACTIVE'").use {st->
                st.bindString(1,title);st.bindString(2,s.dataset);st.bindString(3,document.toString());st.bindLong(4,expectedDocumentRevision)
                if(st.executeUpdateDelete()!=1)throw FileInfoConflict()
            }
            val now=System.currentTimeMillis()
            if(expectedDetailRevision==0L)raw.compileStatement("INSERT INTO ${MedicalFileDetailSchema.TABLE}(store_uuid,document_uuid,file_category,document_date,institution,detail_revision,updated_utc) VALUES(?,?,?,?,?,1,?)").use {st->
                st.bindString(1,s.dataset);st.bindString(2,document.toString())
                detail.category?.let {st.bindString(3,it)} ?: st.bindNull(3);detail.date?.let {st.bindString(4,it)} ?: st.bindNull(4);detail.institution?.let {st.bindString(5,it)} ?: st.bindNull(5)
                st.bindLong(6,now);check(st.executeInsert()>=0)
            } else raw.compileStatement("UPDATE ${MedicalFileDetailSchema.TABLE} SET file_category=?,document_date=?,institution=?,detail_revision=detail_revision+1,updated_utc=? WHERE store_uuid=? AND document_uuid=? AND detail_revision=?").use {st->
                detail.category?.let {st.bindString(1,it)} ?: st.bindNull(1);detail.date?.let {st.bindString(2,it)} ?: st.bindNull(2);detail.institution?.let {st.bindString(3,it)} ?: st.bindNull(3)
                st.bindLong(4,now);st.bindString(5,s.dataset);st.bindString(6,document.toString());st.bindLong(7,expectedDetailRevision)
                if(st.executeUpdateDelete()!=1)throw FileInfoConflict()
            }
            permission();FileInfoReceipt(document,expectedDocumentRevision+(if(renamed)1L else 0L),expectedDetailRevision+1,title,detail.copy(revision=expectedDetailRevision+1))
        }
    }
    companion object {
        const val TITLE_MAX=256
        /** Display text only, never a filesystem path. Invalid input is rejected, never truncated. */
        fun validTitle(title:String)=title.isNotBlank() && title.length<=TITLE_MAX && title.none {Character.isISOControl(it)}
    }
    /** Freeze a real typed identity when the user selects a relationship target; never infer one. */
    suspend fun resolveTarget(cap:AttachmentSession.Capability,target:Target):Target=work(cap) {db,s->
        AttachmentTransaction.commit(db,s,cap) {permission();target.copy(expectedUuid=UUID.fromString(requireNotNull(bindTarget(db,s,target))))}
    }
    data class Relationship(val link:UUID,val revision:Long,val target:Target)
    suspend fun relationships(cap:AttachmentSession.Capability,document:UUID):List<Relationship> =work(cap) {db,s->
        AttachmentTransaction.commit(db,s,cap) {
            permission();authorizedVersion(db,s,null,document)
            db.openHelper.writableDatabase.query("""SELECT l.link_uuid,l.revision,r.target_kind,r.legacy_id,r.record_uuid FROM attachment_link l
                JOIN attachment_record_identity r ON r.store_uuid=l.store_uuid AND r.record_epoch=l.record_epoch AND r.record_uuid=l.record_uuid
                WHERE l.store_uuid=? AND l.record_epoch=? AND l.document_uuid=? AND l.state='ACTIVE' AND r.state='ACTIVE' ORDER BY l.link_uuid""",arrayOf(s.dataset,s.epoch,document.toString())).use {c->
                buildList {while(c.moveToNext()) {
                    val kind=Kind.entries.firstOrNull {it.name==c.getString(2)} ?: error("Unsupported clinical association")
                    val t=Target(kind,c.getLong(3),UUID.fromString(c.getString(4)));verifyTarget(db,t)
                    add(Relationship(UUID.fromString(c.getString(0)),c.getLong(1),t))
                }}
            }
        }
    }
    data class RelationReceipt(val operation:UUID,val phase:Phase,val link:UUID?,val revision:Long?)
    private fun AttachmentRelations.Fact.receipt()=RelationReceipt(operation,when(phase) {"COMMITTED"->Phase.COMMITTED;"ABORTED"->Phase.ABORTED;else->Phase.PENDING},link,revision)
    suspend fun relationStatus(cap:AttachmentSession.Capability,operation:UUID,document:UUID?=null):RelationReceipt?=work(cap) {db,s->
        AttachmentTransaction.commit(db,s,cap) {
            permission()
            val fact=AttachmentRelations.status(db.openHelper.writableDatabase,s,operation)
            if(fact!=null && document!=null)db.openHelper.writableDatabase.query("SELECT document_uuid FROM attachment_relation_operation WHERE store_uuid=? AND record_epoch=? AND operation_uuid=?",arrayOf(s.dataset,s.epoch,operation.toString())).use {
                check(it.moveToFirst() && it.getString(0)==document.toString()) {"Conflicting relation reconciliation identity"}
            }
            fact?.receipt()
        }
    }
    suspend fun cancelRelation(cap:AttachmentSession.Capability,operation:UUID):RelationReceipt?=work(cap) {db,s->
        AttachmentTransaction.commit(db,s,cap) {permission();AttachmentRelations.abort(db.openHelper.writableDatabase,s,operation)?.receipt()}
    }
    /** Authenticate the complete existing original before creating a fresh relationship. No new cipher/key. */
    suspend fun associate(cap:AttachmentSession.Capability,operation:UUID,target:Target,document:UUID,documentRevision:Long,
        unlink:Relationship?=null):RelationReceipt=work(cap) {db,s->
        val raw=db.openHelper.writableDatabase
        val (intent,existing)=AttachmentTransaction.commit(db,s,cap) {
            permission();val record=requireNotNull(bindTarget(db,s,target))
            if(target.expectedUuid!=null)check(record==target.expectedUuid.toString()) {"Selected identity changed"}
            if(unlink!=null)check(unlink.target.kind==target.kind && unlink.target.id==target.id && unlink.target.expectedUuid==UUID.fromString(record))
            val intent=AttachmentRelations.Intent(operation,document,documentRevision,target,record,unlink)
            intent to AttachmentRelations.prepare(raw,s,intent)
        }
        if(existing!=null)return@work existing.receipt() // No replay after UNKNOWN/ABORTED, no repeated IO.
        try {
            val version=AttachmentTransaction.commit(db,s,cap) {permission();authorizedVersion(db,s,null,document)}
            if(unlink==null) {
                val hash=raw.query("SELECT plaintext_sha256 FROM attachment_version WHERE store_uuid=? AND document_uuid=? AND version_uuid=?",arrayOf(s.dataset,document.toString(),version)).use {check(it.moveToFirst());it.getBlob(0)}
                val buffer=ByteArray(65536);cap.ownBytes(buffer).use {
                    val digest=MessageDigest.getInstance("SHA-256")
                    AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).open(s,cap,document).use {reader->
                        var offset=0L;val size=reader.size
                        while(offset<size) {permission();cap.check();val n=reader.readAt(offset,buffer,0,minOf(buffer.size.toLong(),size-offset).toInt());check(n>0);digest.update(buffer,0,n);offset+=n}
                    }
                    check(MessageDigest.isEqual(digest.digest(),hash)) {"Original content authentication failed"}
                }
            }
            AttachmentTransaction.commit(db,s,cap) {
                permission();verifyTarget(db,target);authorizedVersion(db,s,null,document,version)
                AttachmentRelations.commit(raw,s,intent).receipt()
            }
        }catch(failure:Throwable) {
            // A thrown post-COMMIT callback cannot turn a true commit into an aborted/replayed write.
            runCatching {AttachmentTransaction.commit(db,s,cap) {AttachmentRelations.abort(raw,s,operation)}}
            throw failure
        }
    }
    suspend fun trash(cap:AttachmentSession.Capability,document:UUID,revision:Long)=work(cap) {db,s->AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).trash(s,cap,document,revision)}
    suspend fun restore(cap:AttachmentSession.Capability,document:UUID,revision:Long)=work(cap) {db,s->AttachmentPersistentStore(access.applicationContext,db,checkpoint=::boundary,storageBudget=storageBudget).restore(s,cap,document,revision)}
    suspend fun writeArchive(cap:AttachmentSession.Capability,password:CharArray,output:java.io.OutputStream) {
        try {work(cap) {_,s->LibraryLifecycle(access).writeArchive(s,cap,UUID.randomUUID(),password,output)}}finally {password.fill('\u0000')}
    }
    /** Complete authenticated collection, including every retained library. Internal OFF caller
     * must keep a failed output private; only normal return acknowledges the complete set. */
    suspend fun writeCollection(cap:AttachmentSession.Capability,password:CharArray,output:java.io.OutputStream) {
        try {work(cap) {_,s->LibraryCollectionCapture(access).write(s,cap,UUID.randomUUID(),password,output)}}finally {password.fill('\u0000')}
    }
    suspend fun exportEligibility(cap:AttachmentSession.Capability):Export=work(cap) {_,s->
        Export.valueOf(LibraryLifecycle(access).exportEligibility(s,cap,UUID.randomUUID()).eligibility.name)
    }
}
