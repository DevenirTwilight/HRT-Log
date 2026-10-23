package net.plainnotes.app.data

import android.content.Context
import android.database.Cursor
import android.os.Looper
import net.plainnotes.app.domain.AttachmentSession
import java.time.Instant
import java.util.UUID
import java.security.MessageDigest
import org.json.JSONObject
import java.util.concurrent.Callable

/** Read-only preparation. No portable cipher, archive writer, restore SQL or application entry. */
internal class AttachmentBackupPreparation(context:Context,private val db:NotesDatabase,
    private val keys:AttachmentDeviceKeys=AttachmentDeviceKeys(),private val beforeRecordTransfer:()->Unit={}) {
    private val files=AttachmentOwnedFiles(context)
    private val raw get()=db.openHelper.writableDatabase
    class Snapshot internal constructor(private val db:NotesDatabase,private val scope:AttachmentIdentity.Scope,
        private val cap:AttachmentSession.Capability,private val owner:BackupPlaintextOwner,
        private val value:AttachmentBackupManifest):AutoCloseable {
        private fun checkCurrent() {
            check(Looper.myLooper()!=Looper.getMainLooper()) {"Backup snapshot requires a worker"}
            check(db.isOpen && !db.openHelper.writableDatabase.inTransaction())
            check(AttachmentIdentity.current(db.openHelper.writableDatabase)==scope) {"Snapshot dataset changed"}
        }
        private fun <T> current(block:()->T):T = try {
            cap.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                checkCurrent();val result=block();checkCurrent();result
            }
        } catch(failure:Throwable) {try {close()}catch(cleanup:Throwable) {failure.addSuppressed(cleanup)};throw failure}
        fun manifest()=current {value}
        /** Borrowed bytes may be cleared asynchronously. Consume only into private bounded staging;
         * never retain/publish before return. Revocation must not wait for an arbitrary consumer. */
        fun <T> records(block:(ByteArray)->T):T = try {
            current {Unit};val result=block(owner.bytes());current {result}
        } catch(failure:Throwable) {try {close()}catch(cleanup:Throwable) {failure.addSuppressed(cleanup)};throw failure}
        override fun close()=cap.close()
    }
    private data class Source(val version:AttachmentBackupManifest.Version,val identity:AttachmentOwnedFiles.Identity,
        val alias:AttachmentDeviceKeys.Alias,val binding:AttachmentDeviceKeys.Binding,val wrapped:ByteArray)

    fun capture(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,
        limits:AttachmentBackupManifest.Limits=AttachmentBackupManifest.Limits(),created:Instant=Instant.now()):Snapshot {
        check(Looper.myLooper()!=Looper.getMainLooper()) {"Backup preparation requires a worker"}
        check(!raw.inTransaction()) {"Cannot borrow a stale caller snapshot"}
        val child=cap.fork();val owner=BackupPlaintextOwner();val sources=ArrayList<Source>()
        try {
            child.own(owner)
            return files.locked().use {directory->
                // This transaction is READ ONLY, with no result visible until final publication.
                // Do not hold the write-COMMIT authorization monitor across full audit/encoding.
                val manifest=db.runInTransaction(Callable {
                    child.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                        check(AttachmentIdentity.current(raw)==scope) {"Dataset changed"}
                    }
                    // A single-current-dataset preparation must never silently omit retired originals.
                    raw.query("SELECT 1 FROM attachment_version WHERE store_uuid!=? LIMIT 1",arrayOf(scope.dataset)).use {
                        check(!it.moveToFirst()) {"Retained other dataset requires multi-namespace format review"}
                    }
                    var rows=0L
                    DOMAIN_TABLES.forEach {table->child.check();raw.query("SELECT COUNT(*) FROM $table").use {
                        check(it.moveToFirst());rows=Math.addExact(rows,it.getLong(0));require(rows<=limits.maximumRows)
                    }}
                    MeasuredConsumptionStore.auditStagedDataset(raw) // Existing independent full records oracle.
                    child.check()
                    val encoded=MeasuredBackupJson.encode(raw,created) // Existing bounded JSON, not a new wire format.
                    try {
                        child.check();beforeRecordTransfer()
                        child.publish {owner.capture(encoded)}
                    } catch(failure:Throwable) {encoded.fill(0);throw failure}
                    fun <T> read(sql:String,make:(Cursor)->T):List<T> = raw.query(sql,arrayOf(scope.dataset)).use {c->
                        buildList {while(c.moveToNext()) {child.check();rows=Math.addExact(rows,1);require(rows<=limits.maximumRows);add(make(c))}}
                    }
                    fun Cursor.uuid(i:Int)=UUID.fromString(getString(i))
                    fun Cursor.optionalLong(i:Int)=if(isNull(i))null else getLong(i)
                    fun Cursor.hex(i:Int)=getBlob(i).joinToString("") {"%02x".format(it.toInt() and 255)}
                    val records=read("SELECT record_uuid,target_kind,legacy_id,state,trash_id,evidence_digest FROM attachment_record_identity WHERE store_uuid=? ORDER BY record_uuid") {c->
                        AttachmentBackupManifest.Record(c.uuid(0),AttachmentBackupManifest.Kind.valueOf(c.getString(1)),c.getLong(2),c.getString(3),c.optionalLong(4),c.hex(5))}
                    for(record in records) {
                        child.check()
                        val table=when(record.kind) {AttachmentBackupManifest.Kind.APPOINTMENT->"appointment";AttachmentBackupManifest.Kind.LAB->"lab_value";AttachmentBackupManifest.Kind.DOSE_RECORD->"dose_record"}
                        if(record.state=="ACTIVE")raw.query("SELECT 1 FROM $table WHERE id=?",arrayOf(record.sourceId)).use {check(it.moveToFirst()) {"Missing typed source row"}}
                        if(record.state=="TRASHED")raw.query("SELECT payload_json,kind,ref FROM trash_item WHERE id=?",arrayOf(record.trashId)).use {c->
                            check(c.moveToFirst());val payload=c.getString(0)
                            val digest=MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it.toInt() and 255)}
                            check(digest==record.evidenceSha256) {"Trash evidence changed"}
                            if(record.kind==AttachmentBackupManifest.Kind.DOSE_RECORD) {
                                check(c.getString(1)==Trash.RECORD && c.getString(2)==record.sourceId.toString()) {"Wrong retained dose reference"}
                                raw.query("SELECT deleted_at_utc FROM dose_record WHERE id=?",arrayOf(record.sourceId)).use {dose->
                                    check(dose.moveToFirst() && !dose.isNull(0)) {"Missing soft-deleted dose fact"}
                                }
                            } else {
                                check(c.getString(1) in if(record.kind==AttachmentBackupManifest.Kind.LAB)setOf(Trash.LAB,Trash.LAB_PANEL) else setOf(Trash.APPOINTMENT))
                                val retained=JSONObject(payload).getJSONObject("tables").getJSONArray(table)
                                check((0 until retained.length()).count {retained.getJSONObject(it).getLong("id")==record.sourceId}==1) {"Wrong typed trash row"}
                            }
                        }
                    }
                    val documents=read("SELECT document_uuid,revision,title,category,state,active_version_uuid,deleted_utc FROM attachment_document WHERE store_uuid=? ORDER BY document_uuid") {c->
                        check(!c.isNull(5)) {"Unproven retained document"}
                        AttachmentBackupManifest.Document(c.uuid(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.uuid(5),c.optionalLong(6))}
                    val versions=read("""SELECT v.document_uuid,v.ordinal,v.version_uuid,v.object_uuid,v.dek_handle_uuid,
                        v.plaintext_bytes,v.ciphertext_bytes,v.plaintext_sha256,v.ciphertext_sha256,v.mime,v.status,v.protocol_revision,
                        j.attempt_uuid,j.parent_device,j.parent_inode,j.file_device,j.file_inode,j.operation_uuid,j.physical_generation,j.active_alias_uuid,j.proof,o.phase
                        FROM attachment_version v LEFT JOIN attachment_journal j ON j.store_uuid=v.store_uuid AND j.document_uuid=v.document_uuid AND j.version_uuid=v.version_uuid
                        LEFT JOIN attachment_operation o ON o.store_uuid=j.store_uuid AND o.operation_uuid=j.operation_uuid
                        WHERE v.store_uuid=? ORDER BY v.document_uuid,v.ordinal""") {c->
                        require(sources.size<limits.maximumVersions)
                        check(c.getString(10)=="VERIFIED" && c.getLong(11)==1L && !c.isNull(12) && c.getString(20)=="COMMITTED" && c.getString(21)=="COMMITTED") {"Required original lacks authenticated evidence"}
                        val context=AttachmentObjectContext(UUID.fromString(scope.logicalVault),c.uuid(3),c.uuid(2),c.uuid(12),c.uuid(4))
                        val v=AttachmentBackupManifest.Version(c.uuid(0),c.getLong(1),context,
                            AttachmentObjectStreams.Manifest(context,c.getLong(5),c.getLong(6),c.hex(7),c.hex(8)),c.getString(9))
                        val alias=AttachmentDeviceKeys.Alias(c.uuid(19),AttachmentDeviceKeys.Role.ACTIVE)
                        val wrapped=raw.query("SELECT wrapped_dek,wrapping_key_alias FROM attachment_key_envelope WHERE store_uuid=? AND document_uuid=? AND version_uuid=? AND state='AVAILABLE' AND wrap_epoch=1",
                            arrayOf(scope.dataset,c.getString(0),c.getString(2))).use {e->
                            check(e.moveToFirst() && e.getString(1)==alias.value);val bytes=e.getBlob(0);check(!e.moveToNext());bytes}
                        sources+=Source(v,AttachmentOwnedFiles.Identity(c.getLong(13),c.getLong(14),c.getLong(15),c.getLong(16)),alias,
                            AttachmentDeviceKeys.Binding(context,UUID.fromString(scope.dataset),c.uuid(18),c.uuid(17),1,AttachmentDeviceKeys.Role.ACTIVE),wrapped)
                        v
                    }
                    val links=read("SELECT link_uuid,record_uuid,document_uuid,revision,state FROM attachment_link WHERE store_uuid=? ORDER BY link_uuid") {c->
                        AttachmentBackupManifest.Link(c.uuid(0),c.uuid(1),c.uuid(2),c.getLong(3),c.getString(4))}
                    AttachmentBackupManifest(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch),UUID.fromString(scope.logicalVault),records,documents,versions,links).also {it.validate(limits)}
                })
                for(source in sources) {
                    child.check()
                    val key=keys.unwrapExisting(source.alias,source.binding,source.wrapped)
                    val input=try {directory.open(source.version.context.objectId,source.identity)} catch(failure:Throwable) {key.fill(0);throw failure}
                    AttachmentObjectStreams.open(child,source.version.manifest,key,input.channel).close()
                }
                child.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                    check(db.isOpen && AttachmentIdentity.current(raw)==scope) {"Dataset changed during snapshot authentication"}
                    Snapshot(db,scope,child,owner,manifest)
                }
            }
        } catch(failure:Throwable) {
            try {child.close()}catch(cleanup:Throwable) {failure.addSuppressed(cleanup)}
            throw failure
        } finally {sources.forEach {it.wrapped.fill(0)}}
    }
}
