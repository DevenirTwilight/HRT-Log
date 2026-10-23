package net.plainnotes.app.data

import android.content.Context
import android.os.Looper
import androidx.room.Room
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable

/** Actual isolated SQL reconstruction, not a portable decoder, restore success or activation.
 * No caller-provided destination handle/path. Only a new RAM SQLCipher database is ever written.
 */
internal class AttachmentBusinessRestore(private val context:Context,private val currentDb:NotesDatabase) {
    private fun checkCurrent(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability) {
        check(Looper.myLooper()!=Looper.getMainLooper()) {"Candidate reconstruction requires a worker"}
        cap.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
            check(currentDb.isOpen && !currentDb.openHelper.writableDatabase.inTransaction())
            check(AttachmentIdentity.current(currentDb.openHelper.writableDatabase)==scope) {"Current library changed"}
        }
    }
    inner class Candidate internal constructor(private val db:NotesDatabase,private val cap:AttachmentSession.Capability,
        private val scope:AttachmentIdentity.Scope,val namespace:AttachmentRestorePreflight.Namespace,
        val objects:AttachmentRestoreStaging.Receipt):AutoCloseable {
        val activatable=false
        private fun <T> guarded(block:()->T):T = try {
            checkCurrent(scope,cap);val result=block();checkCurrent(scope,cap);cap.publish {result}
        } catch(failure:Throwable) {try {close()}catch(e:Throwable) {failure.addSuppressed(e)};throw failure}
        fun manifest():AttachmentBackupManifest=guarded {AttachmentCandidateMetadata.read(db.openHelper.writableDatabase)}
        /** Borrowed bounded RAM bytes, wiped on return/revocation. No SQL handle escapes. */
        fun <T> records(consume:(ByteArray)->T):T = guarded {
            val bytes=db.runInTransaction(Callable {MeasuredBackupJson.encode(db.openHelper.writableDatabase,Instant.EPOCH)})
            val lease=cap.ownBytes(bytes)
            try {consume(bytes)} finally {lease.close()}
        }
        override fun close()=cap.close()
    }
    /** Takes sole ownership of structuredBytes, including early refusal. No new aliases or wrapping.
     * The complete business graph keeps its original integers in a NEW dataset. An explicit plan
     * requesting other integers is refused; arbitrary graph/hash/trash remapping is not invented.
     */
    fun reconstruct(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,
        plan:AttachmentRestorePreflight.Plan,structuredBytes:ByteArray,
        source:AttachmentRestoreStaging.CiphertextSource):Candidate {
        var db:NotesDatabase?=null;var child:AttachmentSession.Capability?=null
        try {
            checkCurrent(scope,cap)
            val owner=cap.fork().also {child=it};owner.ownBytes(structuredBytes)
            require(structuredBytes.size==plan.structuredBytes && sha(structuredBytes)==plan.structuredSha256) {"Snapshot bytes do not match admitted plan"}
            plan.manifest.records.forEach {r->require(plan.mapping.getValue(r.uuid).localId==if(r.state=="PURGED")null else r.sourceId) {"Whole business graph renumbering is not supported"}}
            val key=ByteArray(32).also(SecureRandom()::nextBytes)
            val isolated=try {
                Room.inMemoryDatabaseBuilder(context.applicationContext,NotesDatabase::class.java)
                    .openHelperFactory(SupportOpenHelperFactory(key)).addCallback(SchemaGuards).build().also {
                        db=it;it.openHelper.writableDatabase.execSQL("PRAGMA temp_store=MEMORY")
                    }
            } finally {key.fill(0)}
            BackupJsonReader.indexed(structuredBytes,checkActive={checkCurrent(scope,owner)}).use {input->
                val version=RecordBackupCompatibility.readVersion(input.metadata)
                require(version in 12..RecordBackupCompatibility.SCHEMA)
                RecordBackupCompatibility.validateInventoryVersion(version,input.tables)
                RecordBackupCompatibility.validateReferenceVersion(version,input.tables)
                isolated.runInTransaction {
                    val raw=isolated.openHelper.writableDatabase
                    AttachmentCandidateMetadata.reconstruct(raw,input.tables,plan) {checkCurrent(scope,owner)}
                    checkCurrent(scope,owner)
                }
            }
            // No object is accepted on an unvalidated business candidate. Existing staging performs
            // the complete inner authentication/index/ownership checks and never publishes SQL.
            val objects=AttachmentRestoreStaging(context,currentDb).stage(scope,owner,plan,source)
            checkCurrent(scope,owner)
            owner.own(AutoCloseable {isolated.close()})
            return owner.publish {Candidate(isolated,owner,scope,plan.namespace,objects)}
        } catch(failure:Throwable) {
            try {child?.close()}catch(e:Throwable) {failure.addSuppressed(e)}
            try {db?.close()}catch(e:Throwable) {failure.addSuppressed(e)}
            throw failure
        } finally {structuredBytes.fill(0)}
    }
    private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString("") {"%02x".format(it.toInt() and 255)}
}
