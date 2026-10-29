package net.plainnotes.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import net.plainnotes.app.domain.AttachmentSession
import org.json.JSONArray
import java.security.MessageDigest
import java.util.UUID

/** Fresh target-only protocol. Copies existing authenticated inner ciphertext, not plaintext.
 * Preserves crypto/history identity, but performs REAL new local provisioning, reservations,
 * wraps, inode capture and independent authentication through the existing journal transitions. */
internal class LibraryRestoreObjects(private val context:LibraryGenerationContext,private val db:NotesDatabase,
    private val p:AttachmentRestorePreflight.Plan,private val cap:AttachmentSession.Capability,
    private val slots:List<Slot>,private val checkpoint:()->Unit) {
    data class Slot(val version:UUID,val operation:UUID,val staging:UUID,val active:UUID)
    private val raw get()=db.openHelper.writableDatabase
    private val scope=AttachmentIdentity.Scope(p.namespace.dataset.toString(),p.manifest.logicalVault.toString(),p.namespace.epoch.toString())
    private val keys=AttachmentDeviceKeys()
    private fun tx(block:()->Unit)=AttachmentTransaction.commit(db,scope,cap,block)
    private fun insert(table:String,vararg fields:Pair<String,Any?>) {
        val values=ContentValues()
        fields.forEach {(k,v)->when(v) {null->values.putNull(k);is String->values.put(k,v);is Long->values.put(k,v);is Int->values.put(k,v);is ByteArray->values.put(k,v);else->error("Invalid owned SQL scalar")}}
        check(raw.insert(table,SQLiteDatabase.CONFLICT_ABORT,values)!=-1L)
    }
    fun install(source:AttachmentRestoreStaging.CiphertextSource) {
        require(slots.map {it.version}.toSet()==p.manifest.versions.map {it.context.version}.toSet())
        require(slots.flatMap {listOf(it.operation,it.staging,it.active)}.toSet().size==slots.size*3)
        check(!raw.inTransaction());raw.execSQL("PRAGMA synchronous=FULL")
        db.runInTransaction {
            cap.check()
            LibraryRestoreSequences.reserve(raw,p.manifest.records) {cap.check();checkpoint()}
            insert("attachment_store","store_uuid" to scope.dataset,"logical_vault_uuid" to scope.logicalVault,"store_state" to "CURRENT","space" to "PRIMARY","record_epoch" to scope.epoch,"generation" to 1L,"feature_enabled" to 0)
            insert("attachment_generation","store_uuid" to scope.dataset,"generation_uuid" to p.namespace.generation.toString(),"state" to "STAGING","created_utc" to System.currentTimeMillis())
            p.manifest.records.forEach {r->cap.check();insert("attachment_record_identity","store_uuid" to scope.dataset,"record_epoch" to scope.epoch,"record_uuid" to r.uuid.toString(),"target_kind" to r.kind.name,"legacy_id" to r.sourceId,"state" to r.state,"trash_id" to r.trashId,"evidence_digest" to MedicalArchiveCrypto.unhex(r.evidenceSha256))}
            p.manifest.documents.forEach {d->cap.check();insert("attachment_document","store_uuid" to scope.dataset,"document_uuid" to d.uuid.toString(),"revision" to d.revision,"title" to d.title,"category" to d.category,"state" to d.state,"deleted_utc" to d.deletedUtc)
                d.detail?.let {x->insert("attachment_document_detail","store_uuid" to scope.dataset,"document_uuid" to d.uuid.toString(),"file_category" to x.category,"document_date" to x.date,"institution" to x.institution,"detail_revision" to x.revision,"updated_utc" to System.currentTimeMillis())}}
            cap.check()
        }
        AttachmentOwnedFiles(context).locked().use {directory->
            for(v in p.manifest.versions.sortedWith(compareBy<AttachmentBackupManifest.Version> {it.document.toString()}.thenBy {it.ordinal})) {
                cap.check();checkpoint();val s=slots.single {it.version==v.context.version}
                val related=p.manifest.links.firstOrNull {it.document==v.document}
                val op=s.operation.toString();val version=v.context.version.toString()
                fun binding(role:AttachmentDeviceKeys.Role)=AttachmentDeviceKeys.Binding(v.context,p.namespace.dataset,p.namespace.generation,s.operation,1,role)
                val staging=AttachmentDeviceKeys.Alias(s.staging,AttachmentDeviceKeys.Role.STAGING)
                val active=AttachmentDeviceKeys.Alias(s.active,AttachmentDeviceKeys.Role.ACTIVE)
                val commitment=MessageDigest.getInstance("SHA-256").digest(v.context.encode())
                tx {
                    insert("attachment_operation","store_uuid" to scope.dataset,"operation_uuid" to op,"idempotency_uuid" to op,"kind" to "FULL_RESTORE","phase" to "PREPARED","expected_generation" to 1L,"expected_document_revision" to p.manifest.documents.single {it.uuid==v.document}.revision,
                        "document_uuid" to v.document.toString(),"resource_generation_uuid" to p.namespace.generation.toString(),"object_uuid" to v.context.objectId.toString(),"version_uuid" to version,"dek_handle_uuid" to v.context.dekHandle.toString(),"reserved_bytes" to v.manifest.ciphertextBytes,"reserved_objects" to 1,"updated_utc" to System.currentTimeMillis())
                    insert("attachment_journal","store_uuid" to scope.dataset,"operation_uuid" to op,"record_epoch" to scope.epoch,"physical_generation" to p.namespace.generation.toString(),"document_uuid" to v.document.toString(),"record_uuid" to related?.record?.toString(),"request_sha256" to commitment,
                        "attempt_uuid" to v.context.attempt.toString(),"object_uuid" to v.context.objectId.toString(),"version_uuid" to version,"dek_handle_uuid" to v.context.dekHandle.toString(),"stage_alias_uuid" to s.staging.toString(),"active_alias_uuid" to s.active.toString(),"stage_ready" to 0,"active_ready" to 0,"stage_uses" to 0,"active_uses" to 0,"proof" to "RESERVED")
                }
                // All identities were also reserved in the ROOT journal before any provisioning.
                keys.provisionNew(staging);keys.provisionNew(active)
                tx {raw.execSQL("UPDATE attachment_journal SET stage_ready=1,active_ready=1,stage_uses=1 WHERE store_uuid=? AND operation_uuid=?",arrayOf(scope.dataset,op))}
                val wrapped=keys.wrapExisting(staging,binding(AttachmentDeviceKeys.Role.STAGING),source.takeDek(v.context.version))
                tx {raw.execSQL("UPDATE attachment_operation SET phase='WRITING',staged_wrapped_dek=?,staged_wrapped_dek_sha256=? WHERE store_uuid=? AND operation_uuid=?",arrayOf(wrapped,MessageDigest.getInstance("SHA-256").digest(wrapped),scope.dataset,op))}
                val identity=directory.create(v.context.objectId).use {created->
                    val i=created.identity
                    tx {raw.execSQL("UPDATE attachment_journal SET proof='OWNED',parent_device=?,parent_inode=?,file_device=?,file_inode=? WHERE store_uuid=? AND operation_uuid=?",arrayOf(i.parentDevice,i.parentInode,i.device,i.inode,scope.dataset,op))}
                    val buffer=ByteArray(32768);val lease=cap.ownBytes(buffer)
                    try {source.open(v.context.version).use {input->created.sink().use {output->
                        var count=0L
                        while(count<v.manifest.ciphertextBytes) {cap.check();checkpoint();val n=input.read(buffer,0,minOf(buffer.size.toLong(),v.manifest.ciphertextBytes-count).toInt());require(n>0);output.write(buffer,0,n);count+=n}
                        require(input.read()==-1) {"Extra required-object bytes"}
                    }}}finally {lease.close()}
                    created.seal();i
                }
                val dek=keys.unwrapExisting(staging,binding(AttachmentDeviceKeys.Role.STAGING),wrapped)
                val input=try {directory.open(v.context.objectId,identity)}catch(e:Throwable) {dek.fill(0);throw e}
                AttachmentObjectStreams.open(cap,v.manifest,dek,input.channel).close()
                tx {raw.execSQL("UPDATE attachment_journal SET active_uses=1 WHERE store_uuid=? AND operation_uuid=?",arrayOf(scope.dataset,op))}
                val local=keys.wrapExisting(active,binding(AttachmentDeviceKeys.Role.ACTIVE),keys.unwrapExisting(staging,binding(AttachmentDeviceKeys.Role.STAGING),wrapped))
                keys.unwrapExisting(active,binding(AttachmentDeviceKeys.Role.ACTIVE),local).fill(0)
                tx {
                    raw.execSQL("UPDATE attachment_journal SET proof='AUTHENTICATED',plaintext_sha256=?,ciphertext_sha256=?,plaintext_bytes=?,ciphertext_bytes=? WHERE store_uuid=? AND operation_uuid=?",arrayOf(MedicalArchiveCrypto.unhex(v.manifest.plaintextSha256),MedicalArchiveCrypto.unhex(v.manifest.ciphertextSha256),v.manifest.plaintextBytes,v.manifest.ciphertextBytes,scope.dataset,op))
                    raw.execSQL("UPDATE attachment_operation SET phase='AUTHENTICATED' WHERE store_uuid=? AND operation_uuid=?",arrayOf(scope.dataset,op))
                    insert("attachment_version","store_uuid" to scope.dataset,"document_uuid" to v.document.toString(),"version_uuid" to version,"ordinal" to v.ordinal,"object_uuid" to v.context.objectId.toString(),"protocol_revision" to 1L,"dek_handle_uuid" to v.context.dekHandle.toString(),"plaintext_sha256" to MedicalArchiveCrypto.unhex(v.manifest.plaintextSha256),"ciphertext_sha256" to MedicalArchiveCrypto.unhex(v.manifest.ciphertextSha256),"plaintext_bytes" to v.manifest.plaintextBytes,"ciphertext_bytes" to v.manifest.ciphertextBytes,"mime" to v.mime,"status" to "VERIFIED")
                    insert("attachment_key_envelope","store_uuid" to scope.dataset,"document_uuid" to v.document.toString(),"version_uuid" to version,"envelope_uuid" to UUID.randomUUID().toString(),"dek_handle_uuid" to v.context.dekHandle.toString(),"wrap_epoch" to 1L,"wrapping_key_alias" to active.value,"wrapped_dek" to local,"created_utc" to System.currentTimeMillis(),"state" to "AVAILABLE")
                    raw.execSQL("UPDATE attachment_document SET active_version_uuid=? WHERE store_uuid=? AND document_uuid=?",arrayOf(version,scope.dataset,v.document.toString()))
                    raw.execSQL("UPDATE attachment_journal SET proof='COMMITTED' WHERE store_uuid=? AND operation_uuid=?",arrayOf(scope.dataset,op))
                    raw.execSQL("UPDATE attachment_operation SET phase='COMMITTED' WHERE store_uuid=? AND operation_uuid=?",arrayOf(scope.dataset,op))
                }
            }
            tx {p.manifest.links.forEach {l->insert("attachment_link","store_uuid" to scope.dataset,"link_uuid" to l.uuid.toString(),"record_epoch" to scope.epoch,"record_uuid" to l.record.toString(),"document_uuid" to l.document.toString(),"revision" to l.revision,"state" to l.state)}}
        }
    }
    companion object {
        fun fresh(m:AttachmentBackupManifest)=m.versions.map {Slot(it.context.version,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())}
        fun encode(slots:List<Slot>)=JSONArray().also {a->slots.forEach {s->a.put(JSONArray(listOf(s.version.toString(),s.operation.toString(),s.staging.toString(),s.active.toString())))}}.toString().toByteArray(Charsets.UTF_8)
        fun decode(bytes:ByteArray):List<Slot> {
            require(bytes.size<=131072);val a=JSONArray(String(bytes,Charsets.UTF_8));require(a.length()<=255)
            return (0 until a.length()).map {i->val row=a.getJSONArray(i);require(row.length()==4)
                val ids=(0..3).map {n->val s=row.getString(n);UUID.fromString(s).also {require(it.toString()==s)}}
                Slot(ids[0],ids[1],ids[2],ids[3])}
        }
    }
}
