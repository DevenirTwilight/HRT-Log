package net.plainnotes.app.data

import android.content.Context
import android.os.Looper
import net.plainnotes.app.domain.AttachmentInputStream
import net.plainnotes.app.domain.AttachmentSession
import java.io.InputStream
import java.util.UUID

/** Quarantine-only preparation. No portable parser/KDF, SQL writes, wrapping, resume or activation.
 * New process has no receipts/capabilities: all remaining files stay charged UNKNOWN and unopened.
 * A future approved outer reader and durable activation journal are still required.
 */
internal class AttachmentRestoreStaging(context:Context,private val db:NotesDatabase) {
    private val files=AttachmentOwnedFiles(context,AttachmentOwnedFiles.Namespace.RESTORE_QUARANTINE)
    interface CiphertextSource {
        /** Immutable complete index supplied by a future archive decoder, still independently checked. */
        fun members():List<AttachmentObjectStreams.Manifest>
        /** Sole ownership transfers. No plaintext/provider URI/filepath is accepted. */
        fun open(version:UUID):InputStream
        /** Sole ownership transfers, even on authentication failure. Never persisted by staging. */
        fun takeDek(version:UUID):ByteArray
    }
    data class StagedObject(val version:UUID,val physicalFile:UUID,val identity:AttachmentOwnedFiles.Identity)
    class Receipt internal constructor(val attempt:UUID,val namespace:AttachmentRestorePreflight.Namespace,
        objects:List<StagedObject>) {
        val objects:List<StagedObject> = java.util.Collections.unmodifiableList(ArrayList(objects))
        // Explicitly not a full-file receipt, database commit, durable restore or portable backup.
        val wholeArchiveAuthenticated=false
        val activatable=false
    }
    private val raw get()=db.openHelper.writableDatabase
    private fun current(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability) {
        check(Looper.myLooper()!=Looper.getMainLooper()) {"Restore staging requires a worker"}
        cap.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
            check(db.isOpen && !raw.inTransaction()) {"Cannot prepare from a caller SQL snapshot"}
            DatabaseAccess.assertActiveHandle(db);check(AttachmentIdentity.current(raw)==scope) {"Restore destination changed"}
        }
    }
    /** Bounded inventory is also the restart policy: retain/charge every leaf, never auto-resume,
     * authenticate/adopt an unknown file, regenerate a key, unlink or activate anything.
     */
    fun retained(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,
        maximumEntries:Int=4096):AttachmentOwnedFiles.Inventory {
        current(scope,cap)
        return files.locked().use {directory->directory.inventory(maximumEntries) {current(scope,cap)}}
    }
    fun stage(scope:AttachmentIdentity.Scope,cap:AttachmentSession.Capability,
        plan:AttachmentRestorePreflight.Plan,source:CiphertextSource):Receipt {
        current(scope,cap)
        DatabaseAccess.requireUnpartitionedStorage(db)
        val owner=cap.fork()
        try {
            val index=source.members().toList()
            require(index.size==plan.manifest.versions.size && index.map {it.context.version}.toSet().size==index.size &&
                index.associateBy {it.context.version}==plan.manifest.versions.associate {it.context.version to it.manifest}) {"Restore source changed membership"}
            return files.locked().use {directory->
                fun budget(additional:List<AttachmentObjectStreams.Manifest>) {
                    val i=directory.inventory(plan.limits.maximumStagingEntries) {current(scope,owner)}
                    require(i.entries.all {it.privateRegular}) {"Unknown staging ownership"}
                    var charged=i.entries.fold(i.overheadBytes) {sum,e->Math.addExact(sum,e.chargedBytes)}
                    var reserve=0L
                    additional.forEach {m->reserve=Math.addExact(reserve,
                        Math.multiplyExact(Math.addExact(m.ciphertextBytes,i.allocationUnit-1)/i.allocationUnit,i.allocationUnit))}
                    charged=Math.addExact(charged,reserve)
                    require(charged<=plan.limits.maximumStagingBytes && i.entries.size.toLong()+additional.size<=plan.limits.maximumStagingEntries) {"Staging capacity exhausted"}
                    require(Math.addExact(reserve,Math.addExact(plan.structuredBytes.toLong(),plan.limits.minimumFreeBytes))<=i.freeBytes) {"Insufficient staging headroom"}
                }
                // Namespace collision checks are read-only. No new store/generation is published.
                val names=listOf(plan.namespace.dataset,plan.namespace.epoch,plan.namespace.generation).map(UUID::toString)
                raw.query("SELECT store_uuid,record_epoch,logical_vault_uuid FROM attachment_store").use {c->while(c.moveToNext()) {
                    current(scope,owner);require((0..2).none {c.getString(it) in names}) {"Restore namespace already exists"}
                }}
                raw.query("SELECT generation_uuid FROM attachment_generation").use {c->while(c.moveToNext()) {
                    current(scope,owner);require(c.getString(0) !in names) {"Restore generation already exists"}
                }}
                budget(index)
                val staged=ArrayList<StagedObject>();val attempt=UUID.randomUUID()
                for((position,v) in plan.manifest.versions.withIndex()) {
                    current(scope,owner)
                    val physical=UUID.randomUUID()
                    val created=directory.create(physical)
                    val createdLease=owner.own(created)
                    try {
                        val input=AttachmentInputStream.open(owner,source.open(v.context.version),v.manifest.ciphertextBytes)
                        val buffer=ByteArray(65536)
                        try {
                            created.sink().use {sink->val lease=owner.own(sink)
                                try {input.use {while(true) {
                                    val n=input.read(buffer);if(n<0)break
                                    current(scope,owner);sink.write(buffer,0,n)
                                }};require(input.bytesRead==v.manifest.ciphertextBytes) {"Missing ciphertext bytes"}}
                                finally {lease.close()}
                            }
                            current(scope,owner);created.seal()
                        } finally {buffer.fill(0);input.close()}
                        val key=source.takeDek(v.context.version)
                        val stream=try {directory.open(physical,created.identity)} catch(failure:Throwable) {key.fill(0);throw failure}
                        AttachmentObjectStreams.open(owner,v.manifest,key,stream.channel).close()
                        current(scope,owner)
                        staged+=StagedObject(v.context.version,physical,created.identity)
                        // Actual occupancy may exceed logical lengths; retain failures without publication.
                        budget(plan.manifest.versions.drop(position+1).map {it.manifest})
                    } finally {createdLease.close()}
                }
                current(scope,owner)
                owner.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
                    DatabaseAccess.assertActiveHandle(db);check(AttachmentIdentity.current(raw)==scope && !raw.inTransaction())
                    Receipt(attempt,plan.namespace,staged)
                }
            }
        } finally {owner.close()}
    }
}
