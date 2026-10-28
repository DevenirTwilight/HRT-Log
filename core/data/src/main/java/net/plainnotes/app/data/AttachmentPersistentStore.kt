package net.plainnotes.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.sqlite.db.SupportSQLiteDatabase
import net.plainnotes.app.domain.AttachmentSession
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Default-OFF production coordinator. No picker, application DI, original-file import or backup API.
 * Synchronous worker API: slow crypto/IO is outside SQL; capability covers the real SQLite COMMIT.
 * Each version uses fresh DEK/attempt and two fresh, single-use device aliases. No resumable encryption.
 */
internal class AttachmentPersistentStore(context: Context, private val db: NotesDatabase,
    private val keys: AttachmentDeviceKeys = AttachmentDeviceKeys(),
    private val storageBudget: AttachmentStorageBudget = AttachmentStorageBudget(),
    private val checkpoint: (Boundary) -> Unit = {},
    private val allocation:LibraryAttachmentAdmission.Permit?=null) {
    enum class Boundary { PREPARED, STAGED, AUTHENTICATED, IN_COMMIT, COMMITTED }
    data class Request(val operation: UUID, val document: UUID, val record: UUID?,
        val expectedRevision: Long, val title: String, val category: String,
        val mime: String, val plaintextBytes: Long, val plaintextSha256: String) {
        init {
            require(expectedRevision >= 0 && expectedRevision < Long.MAX_VALUE)
            require(title.isNotBlank() && title.length <= 256)
            require(category in setOf("REPORT", "PRESCRIPTION", "CERTIFICATE", "RECORD", "OTHER"))
            require(mime in setOf("application/pdf", "image/png", "image/jpeg", "application/octet-stream"))
            require(plaintextBytes in 0..AttachmentObjectStreams.MAXIMUM_BYTES)
            require(plaintextSha256.matches(Regex("[0-9a-f]{64}")))
        }
        fun digest(scope: AttachmentIdentity.Scope): ByteArray = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                listOf("notes.attachment.request.v1", scope.dataset, scope.logicalVault, scope.epoch,
                    operation.toString(), document.toString(), record.toString(), expectedRevision.toString(),
                    title, category, mime, plaintextBytes.toString(), plaintextSha256).forEach { value ->
                    val encoded=value.toByteArray(Charsets.UTF_8); out.writeInt(encoded.size); out.write(encoded)
                }
            }
        }.toByteArray().let(::sha)
    }
    data class Committed(val operation: UUID, val document: UUID, val version: UUID, val revision: Long)
    private val files = AttachmentOwnedFiles(DatabaseAccess.storageContext(db,context))
    private val raw: SupportSQLiteDatabase get() = db.openHelper.writableDatabase
    private fun worker() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Attachment IO requires a worker thread" }
        check(!raw.inTransaction()) { "Attachment work cannot nest inside an outer transaction" }
        // Default-OFF coordinator only: reservations require crash-durable WAL commits, not NORMAL.
        raw.execSQL("PRAGMA synchronous=FULL")
    }
    private fun <T> transaction(scope: AttachmentIdentity.Scope, cap: AttachmentSession.Capability, block: () -> T) =
        AttachmentTransaction.commit(db, scope, cap) {
            raw.query("PRAGMA synchronous").use { check(it.moveToFirst() && it.getInt(0)>=2) { "Durable journal requires FULL synchronization" } }
            allocation?.check()
            val result=block(); allocation?.check(); cap.check(); result
        }
    private fun uuid() = UUID.randomUUID().toString()
    private fun insert(table: String, values: ContentValues) { check(raw.insert(table, SQLiteDatabase.CONFLICT_ABORT, values) != -1L) }
    private fun update(sql: String, vararg args: Any?) { raw.execSQL(sql, args) }
    private fun scopeCheck(scope: AttachmentIdentity.Scope, cap: AttachmentSession.Capability) {
        DatabaseAccess.assertActiveHandle(db)
        val current=AttachmentIdentity.current(raw)
        cap.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {check(current==scope) {"Dataset changed"}}
    }
    private data class Journal(val operation: String, val document: String, val record: String?,
        val generation: String, val context: AttachmentObjectContext, val stageAlias: String,
        val activeAlias: String, val proof: String, val phase: String, val request: ByteArray,
        val identity: AttachmentOwnedFiles.Identity?, val manifest: AttachmentObjectStreams.Manifest?,
        val staged: ByteArray?, val stageReady: Boolean, val activeReady: Boolean) {
        fun alias(role: AttachmentDeviceKeys.Role) = AttachmentDeviceKeys.Alias(
            UUID.fromString(if(role == AttachmentDeviceKeys.Role.STAGING) stageAlias else activeAlias), role)
        fun binding(scope: AttachmentIdentity.Scope, role: AttachmentDeviceKeys.Role) = AttachmentDeviceKeys.Binding(
            context, UUID.fromString(scope.dataset), UUID.fromString(generation), UUID.fromString(operation), 1, role)
    }
    private fun journal(scope: AttachmentIdentity.Scope, operation: String): Journal? = raw.query("""SELECT j.*,
        o.phase,o.staged_wrapped_dek,s.logical_vault_uuid FROM attachment_journal j
        JOIN attachment_operation o ON o.store_uuid=j.store_uuid AND o.operation_uuid=j.operation_uuid
        JOIN attachment_store s ON s.store_uuid=j.store_uuid
        WHERE j.store_uuid=? AND j.operation_uuid=? AND j.record_epoch=?""",
        arrayOf(scope.dataset, operation, scope.epoch)).use { c ->
        if (!c.moveToFirst()) return@use null
        fun text(n: String)=c.getString(c.getColumnIndexOrThrow(n))
        fun number(n: String)=c.getLong(c.getColumnIndexOrThrow(n))
        fun bytes(n: String)=c.getBlob(c.getColumnIndexOrThrow(n))
        fun present(n: String)=!c.isNull(c.getColumnIndexOrThrow(n))
        val content=AttachmentObjectContext(UUID.fromString(text("logical_vault_uuid")), UUID.fromString(text("object_uuid")),
            UUID.fromString(text("version_uuid")), UUID.fromString(text("attempt_uuid")), UUID.fromString(text("dek_handle_uuid")))
        Journal(operation,text("document_uuid"),if(present("record_uuid"))text("record_uuid") else null,text("physical_generation"),content,
            text("stage_alias_uuid"),text("active_alias_uuid"),text("proof"),text("phase"),bytes("request_sha256"),
            if(present("file_inode")) AttachmentOwnedFiles.Identity(number("parent_device"),number("parent_inode"),number("file_device"),number("file_inode")) else null,
            if(present("plaintext_sha256")) AttachmentObjectStreams.Manifest(content,number("plaintext_bytes"),number("ciphertext_bytes"),bytes("plaintext_sha256").hex(),bytes("ciphertext_sha256").hex()) else null,
            if(present("staged_wrapped_dek")) bytes("staged_wrapped_dek") else null,number("stage_ready")==1L,number("active_ready")==1L)
    }

    /** Source opens only for a newly prepared attempt. Caller commits a bounded input identity first;
     * actual bytes must match it. UUID replay never opens input, creates keys or consumes another wrap.
     */
    fun save(scope: AttachmentIdentity.Scope, cap: AttachmentSession.Capability, request: Request,
        source: () -> InputStream): Committed {
        worker(); scopeCheck(scope, cap);allocation?.request(request)
        files.locked().use { directory ->
            recoverLocked(scope,cap)
            val digest=request.digest(scope)
            var replay: Committed? = null
            val row=transaction(scope, cap) {
                val prior=journal(scope, request.operation.toString())
                if (prior != null) {
                    check(MessageDigest.isEqual(prior.request,digest)) { "Conflicting operation UUID" }
                    check(prior.proof=="COMMITTED" && prior.phase=="COMMITTED") { "Incomplete operation: recover; retry with a new UUID" }
                    replay=Committed(request.operation,request.document,prior.context.version,request.expectedRevision+1)
                    prior
                } else {
                    if(allocation==null)DatabaseAccess.requireUnpartitionedStorage(db) else allocation.check()
                    // A late retry must not reuse an old UUID under a restored dataset/epoch.
                    raw.query("SELECT 1 FROM attachment_operation WHERE operation_uuid=? OR idempotency_uuid=? LIMIT 1",arrayOf(request.operation.toString(),request.operation.toString())).use {
                        check(!it.moveToFirst()) {"Operation UUID belongs to another request or dataset"}
                    }
                    checkRecord(scope,request.record?.toString())
                    checkRevision(scope,request)
                    // Under the same writer lock and SQL snapshot, BEFORE prepare/provision/source.
                    val usage=AttachmentStorageAccounting.inspect(raw,directory,keys,storageBudget,cap::check)
                    when(val decision=storageBudget.evaluate(usage)) {
                        is AttachmentStorageBudget.Decision.Denied -> throw AttachmentStorageBudget.Rejected(decision.reason)
                        is AttachmentStorageBudget.Decision.Allowed -> Unit
                    }
                    val gen=raw.query("SELECT generation,generation_uuid FROM attachment_store s JOIN attachment_generation g ON g.store_uuid=s.store_uuid WHERE s.store_uuid=? AND s.store_state='CURRENT' AND g.state='STAGING' ORDER BY g.created_utc,g.generation_uuid LIMIT 1",arrayOf(scope.dataset)).use {
                        check(it.moveToFirst()) { "No local physical namespace" }; it.getLong(0) to it.getString(1)
                    }
                    val objectId=uuid(); val version=uuid(); val handle=uuid()
                    insert("attachment_operation",ContentValues().apply {
                        put("store_uuid",scope.dataset);put("operation_uuid",request.operation.toString());put("idempotency_uuid",request.operation.toString())
                        put("kind",if(request.expectedRevision==0L) "IMPORT" else "REPLACE");put("phase","PREPARED");put("expected_generation",gen.first)
                        put("expected_document_revision",request.expectedRevision);if(request.expectedRevision>0)put("document_uuid",request.document.toString())
                        put("resource_generation_uuid",gen.second);put("object_uuid",objectId);put("version_uuid",version);put("dek_handle_uuid",handle)
                        put("reserved_bytes",AttachmentObjectStreams.MAXIMUM_BYTES+65536);put("reserved_objects",1);put("updated_utc",System.currentTimeMillis())
                    })
                    insert("attachment_journal",ContentValues().apply {
                        put("store_uuid",scope.dataset);put("operation_uuid",request.operation.toString());put("record_epoch",scope.epoch)
                        put("physical_generation",gen.second);put("document_uuid",request.document.toString());put("record_uuid",request.record?.toString())
                        put("request_sha256",digest);put("attempt_uuid",uuid());put("object_uuid",objectId);put("version_uuid",version);put("dek_handle_uuid",handle)
                        put("stage_alias_uuid",uuid());put("active_alias_uuid",uuid());put("stage_ready",0);put("active_ready",0);put("stage_uses",0);put("active_uses",0);put("proof","RESERVED")
                    })
                    requireNotNull(journal(scope,request.operation.toString()))
                }
            }
            replay?.let { return it }
            checkpoint(Boundary.PREPARED)
            try {
                provision(scope,cap,row,AttachmentDeviceKeys.Role.STAGING)
                provision(scope,cap,row,AttachmentDeviceKeys.Role.ACTIVE)
                val dek=ByteArray(32).also(SecureRandom()::nextBytes)
                try {
                    reserve(scope,cap,row,AttachmentDeviceKeys.Role.STAGING)
                    val staged=keys.wrapExisting(row.alias(AttachmentDeviceKeys.Role.STAGING),row.binding(scope,AttachmentDeviceKeys.Role.STAGING),dek.copyOf())
                    transaction(scope,cap) {
                        update("UPDATE attachment_operation SET phase='WRITING',staged_wrapped_dek=?,staged_wrapped_dek_sha256=? WHERE store_uuid=? AND operation_uuid=? AND phase='PREPARED'",staged,sha(staged),scope.dataset,row.operation)
                    }
                    val manifest=directory.create(row.context.objectId).use { created ->
                        transaction(scope,cap) { val i=created.identity
                            update("UPDATE attachment_journal SET proof='OWNED',parent_device=?,parent_inode=?,file_device=?,file_inode=? WHERE store_uuid=? AND operation_uuid=? AND proof='RESERVED'",i.parentDevice,i.parentInode,i.device,i.inode,scope.dataset,row.operation)
                        }
                        // If opening the source fails, no descriptor or raw DEK escapes.
                        val input=source()
                        val target=try { created.sink() } catch(failure:Throwable) { input.close();throw failure }
                        val result=AttachmentObjectStreams.write(cap,row.context,dek,input,target,AttachmentObjectStreams.MAXIMUM_BYTES)
                        created.seal(); result
                    }
                    check(manifest.plaintextBytes==request.plaintextBytes && manifest.plaintextSha256==request.plaintextSha256) { "Input identity changed" }
                    checkpoint(Boundary.STAGED)
                    val owned=requireNotNull(journal(scope,row.operation))
                    val key=keys.unwrapExisting(row.alias(AttachmentDeviceKeys.Role.STAGING),row.binding(scope,AttachmentDeviceKeys.Role.STAGING),staged)
                    val pinned=try { directory.open(row.context.objectId,requireNotNull(owned.identity)) } catch(failure:Throwable) { key.fill(0);throw failure }
                    AttachmentObjectStreams.open(cap,manifest,key,pinned.channel).use {
                        reserve(scope,cap,row,AttachmentDeviceKeys.Role.ACTIVE)
                        val activeRaw=keys.unwrapExisting(row.alias(AttachmentDeviceKeys.Role.STAGING),row.binding(scope,AttachmentDeviceKeys.Role.STAGING),staged)
                        val active=keys.wrapExisting(row.alias(AttachmentDeviceKeys.Role.ACTIVE),row.binding(scope,AttachmentDeviceKeys.Role.ACTIVE),activeRaw)
                        // Verify the actual active envelope as well, not just its creation receipt.
                        keys.unwrapExisting(row.alias(AttachmentDeviceKeys.Role.ACTIVE),row.binding(scope,AttachmentDeviceKeys.Role.ACTIVE),active).fill(0)
                        transaction(scope,cap) {
                            update("UPDATE attachment_journal SET proof='AUTHENTICATED',plaintext_sha256=?,ciphertext_sha256=?,plaintext_bytes=?,ciphertext_bytes=? WHERE store_uuid=? AND operation_uuid=? AND proof='OWNED'",manifest.plaintextSha256.unhex(),manifest.ciphertextSha256.unhex(),manifest.plaintextBytes,manifest.ciphertextBytes,scope.dataset,row.operation)
                            update("UPDATE attachment_operation SET phase='AUTHENTICATED' WHERE store_uuid=? AND operation_uuid=? AND phase='WRITING'",scope.dataset,row.operation)
                        }
                        checkpoint(Boundary.AUTHENTICATED)
                        // Recheck the path and directory while the fully authenticated original FD remains pinned.
                        directory.open(row.context.objectId,requireNotNull(owned.identity)).close()
                        transaction(scope,cap) {
                            val actual=AttachmentStorageAccounting.inspect(raw,directory,keys,storageBudget,cap::check)
                            val decision=storageBudget.retained(actual)
                            if(decision is AttachmentStorageBudget.Decision.Denied) throw AttachmentStorageBudget.Rejected(decision.reason)
                            publish(scope,request,row,manifest,active); checkpoint(Boundary.IN_COMMIT)
                        }
                    }
                } finally { dek.fill(0) }
            } catch(failure:Throwable) {
                // Authority can be gone. Recovery performs durable abort under a fresh authorized session.
                // Never guess-delete a file or recreate an alias here. A committed transaction is never undone.
                throw failure
            }
            checkpoint(Boundary.COMMITTED)
            return Committed(request.operation,request.document,row.context.version,request.expectedRevision+1)
        }
    }

    private fun provision(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,row:Journal,role:AttachmentDeviceKeys.Role) {
        scopeCheck(scope,cap)
        // Alias identity was durably reserved in PREPARE. A crash here NEVER adopts/recreates it.
        keys.provisionNew(row.alias(role))
        transaction(scope,cap) {
            update("UPDATE attachment_journal SET ${if(role==AttachmentDeviceKeys.Role.STAGING) "stage_ready" else "active_ready"}=1 WHERE store_uuid=? AND operation_uuid=? AND proof='RESERVED'",scope.dataset,row.operation)
        }
    }
    private fun reserve(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,row:Journal,role:AttachmentDeviceKeys.Role) {
        transaction(scope,cap) {
            val column=if(role==AttachmentDeviceKeys.Role.STAGING) "stage_uses" else "active_uses"
            raw.compileStatement("UPDATE attachment_journal SET $column=1 WHERE store_uuid=? AND operation_uuid=? AND $column=0").use {
                it.bindString(1,scope.dataset);it.bindString(2,row.operation);check(it.executeUpdateDelete()==1) { "Wrap budget already consumed" }
            }
        } // Native COMMIT completes BEFORE Cipher.init/doFinal; crash never refunds this one-use epoch.
    }
    private fun checkRecord(scope:AttachmentIdentity.Scope,record:String?) {
        if(record==null) {check(raw.version>=MedicalFilesSchema.VERSION) {"Independent import requires medical files schema"};return}
        raw.query("SELECT 1 FROM attachment_record_identity WHERE store_uuid=? AND record_epoch=? AND record_uuid=? AND state='ACTIVE'",arrayOf(scope.dataset,scope.epoch,record)).use { check(it.moveToFirst()) { "Missing active typed record" } }
    }
    private fun checkRevision(scope:AttachmentIdentity.Scope,r:Request) {
        raw.query("SELECT revision,state FROM attachment_document WHERE store_uuid=? AND document_uuid=?",arrayOf(scope.dataset,r.document.toString())).use {
            if(r.expectedRevision==0L) check(!it.moveToFirst()) { "Document already exists" }
            else check(it.moveToFirst() && it.getLong(0)==r.expectedRevision && it.getString(1)=="ACTIVE") { "Document CAS conflict" }
        }
    }
    private fun publish(scope:AttachmentIdentity.Scope,r:Request,j:Journal,m:AttachmentObjectStreams.Manifest,envelope:ByteArray) {
        checkRecord(scope,j.record);checkRevision(scope,r)
        val ready=requireNotNull(journal(scope,j.operation));check(ready.phase=="AUTHENTICATED" && ready.proof=="AUTHENTICATED")
        check(MessageDigest.isEqual(ready.request,r.digest(scope)))
        raw.query("SELECT 1 FROM attachment_store s JOIN attachment_operation o ON o.store_uuid=s.store_uuid WHERE s.store_uuid=? AND o.operation_uuid=? AND s.generation=o.expected_generation",arrayOf(scope.dataset,j.operation)).use {check(it.moveToFirst()) {"Physical generation CAS conflict"}}
        if(r.expectedRevision==0L) insert("attachment_document",ContentValues().apply {
            put("store_uuid",scope.dataset);put("document_uuid",j.document);put("revision",1);put("title",r.title);put("category",r.category);put("state","ACTIVE")
        })
        val ordinal=raw.query("SELECT COALESCE(MAX(ordinal),0)+1 FROM attachment_version WHERE store_uuid=? AND document_uuid=?",arrayOf(scope.dataset,j.document)).use {check(it.moveToFirst());it.getLong(0)}
        insert("attachment_version",ContentValues().apply {
            put("store_uuid",scope.dataset);put("document_uuid",j.document);put("version_uuid",j.context.version.toString());put("ordinal",ordinal)
            put("object_uuid",j.context.objectId.toString());put("protocol_revision",1);put("dek_handle_uuid",j.context.dekHandle.toString())
            put("plaintext_sha256",m.plaintextSha256.unhex());put("ciphertext_sha256",m.ciphertextSha256.unhex());put("plaintext_bytes",m.plaintextBytes);put("ciphertext_bytes",m.ciphertextBytes);put("mime",r.mime);put("status","VERIFIED")
        })
        insert("attachment_key_envelope",ContentValues().apply {
            put("store_uuid",scope.dataset);put("document_uuid",j.document);put("version_uuid",j.context.version.toString());put("envelope_uuid",uuid())
            put("dek_handle_uuid",j.context.dekHandle.toString());put("wrap_epoch",1);put("wrapping_key_alias",j.alias(AttachmentDeviceKeys.Role.ACTIVE).value)
            put("wrapped_dek",envelope);put("created_utc",System.currentTimeMillis());put("state","AVAILABLE")
        })
        raw.compileStatement("UPDATE attachment_document SET active_version_uuid=?,revision=?,title=?,category=? WHERE store_uuid=? AND document_uuid=? AND revision=? AND state='ACTIVE'").use {
            listOf(j.context.version.toString(),(r.expectedRevision+1).toString(),r.title,r.category,scope.dataset,j.document,maxOf(1,r.expectedRevision).toString()).forEachIndexed { i,v -> it.bindString(i+1,v) }
            check(it.executeUpdateDelete()==1) { "Document pointer CAS conflict" }
        }
        if(j.record!=null)raw.query("SELECT 1 FROM attachment_link WHERE store_uuid=? AND document_uuid=? AND record_epoch=? AND record_uuid=? AND state='ACTIVE'",arrayOf(scope.dataset,j.document,scope.epoch,j.record)).use {
            if(!it.moveToFirst()) insert("attachment_link",ContentValues().apply {
                put("store_uuid",scope.dataset);put("link_uuid",uuid());put("record_epoch",scope.epoch);put("record_uuid",j.record);put("document_uuid",j.document);put("revision",1);put("state","ACTIVE")
            })
        }
        update("UPDATE attachment_journal SET proof='COMMITTED' WHERE store_uuid=? AND operation_uuid=? AND proof='AUTHENTICATED'",scope.dataset,j.operation)
        update("UPDATE attachment_operation SET phase='COMMITTED',updated_utc=? WHERE store_uuid=? AND operation_uuid=? AND phase='AUTHENTICATED'",System.currentTimeMillis(),scope.dataset,j.operation)
    }

    /** No implicit publication, key creation or deletion on restart. Cross-process lock excludes a live writer.
     * Scope retirement is handled too: structured restore must never revive the old epoch by integer ID.
     */
    fun recover(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability):Int {
        worker();scopeCheck(scope,cap)
        files.locked().use { return recoverLocked(scope,cap) }
    }
    private fun recoverLocked(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability):Int = transaction(scope,cap) {
        val count=raw.query("SELECT COUNT(*) FROM attachment_journal WHERE proof NOT IN('COMMITTED','QUARANTINED')").use {c->check(c.moveToFirst());c.getInt(0)}
        update("UPDATE attachment_journal SET proof='QUARANTINED' WHERE proof NOT IN('COMMITTED','QUARANTINED')")
        update("UPDATE attachment_operation SET phase='ABORTED',staged_wrapped_dek=NULL,updated_utc=? WHERE phase IN('PREPARED','WRITING','AUTHENTICATED') AND EXISTS(SELECT 1 FROM attachment_journal j WHERE j.store_uuid=attachment_operation.store_uuid AND j.operation_uuid=attachment_operation.operation_uuid AND j.proof='QUARANTINED')",System.currentTimeMillis())
        count
    }
    data class OperationState(val phase:String,val committed:Committed?)
    /** Reconcile a lost response after restart without reopening a provider URI or inventing a new save. */
    fun operationState(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,operation:UUID):OperationState? {
        worker()
        return transaction(scope,cap) {
            val j=journal(scope,operation.toString()) ?: return@transaction null
            val receipt=if(j.proof=="COMMITTED" && j.phase=="COMMITTED") {
                val revision=raw.query("SELECT expected_document_revision FROM attachment_operation WHERE store_uuid=? AND operation_uuid=?",arrayOf(scope.dataset,j.operation)).use {check(it.moveToFirst());it.getLong(0)+1}
                Committed(operation,UUID.fromString(j.document),j.context.version,revision)
            } else null
            OperationState(j.phase,receipt)
        }
    }

    fun open(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,document:UUID):AttachmentScopedReader {
        val crypto=openChecked(scope,cap,document,false)
        try {return AttachmentScopedReader(db,scope,cap,crypto)}
        catch(failure:Throwable) {try {crypto.close()} catch(cleanup:Throwable) {failure.addSuppressed(cleanup)};throw failure}
    }
    /** Aggregate metadata only. All known historical objects/aliases remain retained. */
    fun storageUsage(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability):AttachmentStorageBudget.Report {
        worker()
        return files.locked().use { directory -> transaction(scope,cap) {
            AttachmentStorageAccounting.inspect(raw,directory,keys,storageBudget,cap::check)
        } }
    }
    private fun openChecked(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,document:UUID,trashed:Boolean):AttachmentObjectStreams.Reader {
        worker();scopeCheck(scope,cap)
        // Pin/verify inode and full content under the writer lock. Returned read-only descriptor remains pinned.
        var issued:AttachmentObjectStreams.Reader?=null
        try { files.locked().use { directory ->
            val operation=raw.query("""SELECT j.operation_uuid FROM attachment_document d JOIN attachment_version v ON v.store_uuid=d.store_uuid AND v.document_uuid=d.document_uuid AND v.version_uuid=d.active_version_uuid
                JOIN attachment_journal j ON j.store_uuid=v.store_uuid AND j.version_uuid=v.version_uuid AND j.document_uuid=v.document_uuid
                WHERE d.store_uuid=? AND d.document_uuid=? AND d.state=? AND v.status='VERIFIED' AND v.protocol_revision=1 AND j.proof='COMMITTED'""",arrayOf(scope.dataset,document.toString(),if(trashed) "TRASHED" else "ACTIVE")).use {check(it.moveToFirst()) {"No authenticated active attachment"};it.getString(0)}
            val j=requireNotNull(journal(scope,operation));check(j.phase=="COMMITTED")
            val envelope=raw.query("SELECT wrapped_dek,wrapping_key_alias FROM attachment_key_envelope WHERE store_uuid=? AND document_uuid=? AND version_uuid=? AND wrap_epoch=1 AND state='AVAILABLE'",arrayOf(scope.dataset,j.document,j.context.version.toString())).use {
                check(it.moveToFirst());check(it.getString(1)==j.alias(AttachmentDeviceKeys.Role.ACTIVE).value);it.getBlob(0)
            }
            try {
            val key=keys.unwrapExisting(j.alias(AttachmentDeviceKeys.Role.ACTIVE),j.binding(scope,AttachmentDeviceKeys.Role.ACTIVE),envelope)
            val input=try { directory.open(j.context.objectId,requireNotNull(j.identity)) } catch(failure:Throwable) {key.fill(0);throw failure}
            return AttachmentObjectStreams.open(cap,requireNotNull(j.manifest),key,input.channel).also {
                issued=it
                try { scopeCheck(scope,cap) } catch(failure:Throwable) {it.close();throw failure}
            }
            } catch(failure:Exception) {
                // Keep ciphertext, immutable commitments and historical association. Deny further publication.
                // If authority/scope was revoked, this transaction refuses too; cancellation is not corruption.
                try { transaction(scope,cap) {
                    update("UPDATE attachment_version SET status='CORRUPT' WHERE store_uuid=? AND document_uuid=? AND version_uuid=? AND status='VERIFIED'",scope.dataset,j.document,j.context.version.toString())
                } } catch(cleanup:Exception) { failure.addSuppressed(cleanup) }
                throw failure
            }
        } } catch(failure:Throwable) {
            try {issued?.close()} catch(cleanup:Throwable) {failure.addSuppressed(cleanup)}
            throw failure
        }
    }

    /** Logical trash retains all originals/versions and quarantines links; restore never relinks automatically. */
    fun trash(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,document:UUID,expectedRevision:Long) {
        worker();files.locked().use { transaction(scope,cap) {
            raw.compileStatement("UPDATE attachment_document SET state='TRASHED',deleted_utc=?,revision=revision+1 WHERE store_uuid=? AND document_uuid=? AND revision=? AND state='ACTIVE'").use {
                it.bindLong(1,System.currentTimeMillis());it.bindString(2,scope.dataset);it.bindString(3,document.toString());it.bindLong(4,expectedRevision);check(it.executeUpdateDelete()==1)
            }
            update("UPDATE attachment_link SET state='QUARANTINED',revision=revision+1 WHERE store_uuid=? AND document_uuid=? AND state='ACTIVE'",scope.dataset,document.toString())
        } }
    }
    fun restore(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,document:UUID,expectedRevision:Long) {
        // Full file/key authentication before exposing a trashed original. Recheck CAS after acquiring writer lock.
        openChecked(scope,cap,document,true).use {
            files.locked().use { transaction(scope,cap) {
                raw.compileStatement("UPDATE attachment_document SET state='ACTIVE',deleted_utc=NULL,revision=revision+1 WHERE store_uuid=? AND document_uuid=? AND revision=? AND state='TRASHED'").use {
                    it.bindString(1,scope.dataset);it.bindString(2,document.toString());it.bindLong(3,expectedRevision);check(it.executeUpdateDelete()==1)
                }
            } }
        }
    }
    companion object {
        private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun ByteArray.hex()=joinToString("") {"%02x".format(it.toInt() and 255)}
        private fun String.unhex()=chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    }
}
