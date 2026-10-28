package net.plainnotes.app.data

import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

/** Format-independent admission, NOT outer-file authentication or authority to activate a dataset.
 * Owns and wipes structuredBytes. Never opens a destination DB, provisions keys or writes records.
 * A future approved decoder must authenticate the archive independently of this closure check.
 */
internal object AttachmentRestorePreflight {
    data class Namespace(val dataset:UUID,val epoch:UUID,val generation:UUID)
    data class TargetRow(val record:UUID,val kind:AttachmentBackupManifest.Kind,val localId:Long,val state:String)
    data class Resources(val freeBytes:Long,val allocationUnit:Long,val availableAliases:Int,
        val availableWrapInvocations:Int,val retainedStagingBytes:Long=0,val retainedStagingEntries:Int=0) {
        init {require(freeBytes>=0 && allocationUnit>0 && availableAliases>=0 && availableWrapInvocations>=0 &&
            retainedStagingBytes>=0 && retainedStagingEntries>=0)}
    }
    data class Limits(val maximumCiphertextBytes:Long=256L*1024*1024,val maximumVersions:Int=1000,
        val maximumRows:Int=100000,val maximumStagingBytes:Long=512L*1024*1024,
        val maximumStagingEntries:Int=4096,val minimumFreeBytes:Long=32L*1024*1024) {
        init {require(maximumCiphertextBytes in 0..512L*1024*1024 && maximumVersions in 1..1000 &&
            maximumRows in 1..100000 && maximumStagingBytes>=0 && maximumStagingEntries in 1..100000 && minimumFreeBytes>=0)}
    }
    class Plan private constructor(val manifest:AttachmentBackupManifest,val namespace:Namespace,
        val mapping:Map<UUID,AttachmentBackupManifest.Target>,val ciphertextBytes:Long,
        val additionalSpaceBytes:Long,val plannedAliases:Int,val plannedWrapInvocations:Int,
        val structuredSha256:String,val structuredBytes:Int,val limits:Limits) {
        companion object {
            internal fun create(manifest:AttachmentBackupManifest,namespace:Namespace,mapping:Map<UUID,AttachmentBackupManifest.Target>,
                ciphertextBytes:Long,space:Long,aliases:Int,hash:String,bytes:Int,limits:Limits)=
                Plan(manifest,namespace,Collections.unmodifiableMap(HashMap(mapping)),ciphertextBytes,space,aliases,aliases,hash,bytes,limits)
        }
    }
    fun inspect(manifest:AttachmentBackupManifest,structuredBytes:ByteArray,
        members:List<AttachmentObjectStreams.Manifest>,namespace:Namespace,
        targets:List<AttachmentBackupManifest.Target>,targetRows:List<TargetRow>,
        forbiddenNamespaces:Set<UUID>,resources:Resources,limits:Limits=Limits(),checkActive:()->Unit={}):Plan {
        try {
            checkActive()
            require(structuredBytes.size<=BackupLimits.MAX_JSON_BYTES)
            // Freeze caller collections: later provider mutation must not change the admitted set.
            val m=manifest.copy(records=immutable(manifest.records),documents=immutable(manifest.documents),
                versions=immutable(manifest.versions),links=immutable(manifest.links))
            val ml=AttachmentBackupManifest.Limits(limits.maximumRows,limits.maximumVersions,limits.maximumCiphertextBytes)
            m.validateComplete(ml)
            val fresh=listOf(namespace.dataset,namespace.epoch,namespace.generation)
            require(fresh.toSet().size==3 && fresh.none {it in forbiddenNamespaces || it==m.dataset || it==m.epoch || it==m.logicalVault}) {"Restore namespace is not fresh"}
            fun <T> unique(v:List<T>) {require(v.size==v.toSet().size) {"Duplicate restore identity"}}
            require(members.size==m.versions.size && members.map {it.context.version}.toSet().size==members.size)
            require(members.associateBy {it.context.version}==m.versions.associate {it.context.version to it.manifest}) {"Missing, extra or substituted original"}
            // mapTo uses the same invariants with this caller's explicit lower resource limits.
            val mapping=m.mapTo(namespace.dataset,namespace.epoch,targets,ml)
            unique(targetRows.map {it.record});unique(targetRows.map {it.kind to it.localId})
            val expected=m.records.filter {it.state!="PURGED"}.associateBy {it.uuid}
            require(targetRows.size==expected.size)
            targetRows.forEach {t->val r=requireNotNull(expected[t.record]) {"Unknown destination row"}
                val mapped=mapping.getValue(t.record)
                require(t.kind==r.kind && t.localId>0 && t.localId==mapped.localId && t.state==r.state) {"Typed destination row does not match explicit mapping"}}
            val hash=MessageDigest.getInstance("SHA-256").digest(structuredBytes).joinToString("") {"%02x".format(it.toInt() and 255)}
            val size=structuredBytes.size
            BackupJsonReader.indexed(structuredBytes,checkActive).use {input->
                val schema=RecordBackupCompatibility.readVersion(input.metadata)
                require(schema in 12..RecordBackupCompatibility.SCHEMA) {"Full restore needs current structured record semantics"}
                val tables=input.tables
                require(tables.keys()==RecordBackupCompatibility.tables(schema).toSet()) {"Incomplete or unknown structured table set"}
                RecordBackupCompatibility.validateInventoryVersion(schema,tables)
                RecordBackupCompatibility.validateReferenceVersion(schema,tables)
                var rows=m.records.size.toLong()+m.documents.size+m.versions.size+m.links.size
                tables.keys().forEach {table->checkActive();rows=Math.addExact(rows,tables.getJSONArray(table).length().toLong());require(rows<=limits.maximumRows)}
                fun exactId(row:org.json.JSONObject,key:String):Long {
                    val v=row.get(key);require(v is Int || v is Long);return (v as Number).toLong().also {require(it>0)}
                }
                val byKind=AttachmentBackupManifest.Kind.entries.associateWith {kind->
                    val ids=HashSet<Long>();val a=tables.getJSONArray(table(kind))
                    for(i in 0 until a.length()) {checkActive();require(ids.add(exactId(a.getJSONObject(i),"id"))) {"Duplicate clinical row"}}
                    ids
                }
                val deletedDose=HashSet<Long>();val doses=tables.getJSONArray("dose_record")
                for(i in 0 until doses.length()) {checkActive();val r=doses.getJSONObject(i)
                    if(!r.isNull("deleted_at_utc"))deletedDose+=exactId(r,"id")}
                val trash=HashMap<Long,org.json.JSONObject>();val a=tables.getJSONArray("trash_item")
                for(i in 0 until a.length()) {checkActive();val row=a.getJSONObject(i);require(trash.put(exactId(row,"id"),row)==null)}
                m.records.forEach {r->
                    checkActive();val live=r.sourceId in byKind.getValue(r.kind)
                    if(live && r.kind==AttachmentBackupManifest.Kind.LAB_PANEL) {
                        val panels=tables.getJSONArray("lab_panel")
                        require((0 until panels.length()).any {panels.getJSONObject(it).let {row->exactId(row,"id")==r.sourceId && row.getString("uuid")==r.uuid.toString()}}) {"Blood draw identity mismatch"}
                    }
                    when(r.state) {
                        "ACTIVE"->require(live && (r.kind!=AttachmentBackupManifest.Kind.DOSE_RECORD || r.sourceId !in deletedDose)) {"Missing active clinical relationship target"}
                        "PURGED"->require(!live) {"Tombstone conflicts with a live clinical fact"}
                        "TRASHED"->{
                            val reference=r.kind==AttachmentBackupManifest.Kind.DOSE_RECORD
                            require(if(reference)live && r.sourceId in deletedDose else !live)
                            val item=requireNotNull(trash[r.trashId]) {"Missing retained clinical trash"}
                            val allowed=when(r.kind) {AttachmentBackupManifest.Kind.APPOINTMENT->setOf("APPOINTMENT");AttachmentBackupManifest.Kind.LAB->setOf("LAB","LAB_PANEL");AttachmentBackupManifest.Kind.LAB_PANEL->setOf("LAB","LAB_PANEL");AttachmentBackupManifest.Kind.DOSE_RECORD->setOf("RECORD")}
                            require(item.getString("kind") in allowed)
                            val payload=item.getString("payload_json");val bytes=payload.toByteArray(Charsets.UTF_8)
                            try {
                                require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it.toInt() and 255)}==r.evidenceSha256) {"Clinical trash evidence mismatch"}
                                val payloadJson=BackupJsonReader.read(bytes)
                                if(reference)require(item.getString("ref")==r.sourceId.toString()) {"Wrong soft-deleted record reference"}
                                else {
                                    val retained=payloadJson.getJSONObject("tables").getJSONArray(table(r.kind))
                                    require((0 until retained.length()).count {retained.getJSONObject(it).let {row->exactId(row,"id")==r.sourceId && (r.kind!=AttachmentBackupManifest.Kind.LAB_PANEL || row.getString("uuid")==r.uuid.toString())}}==1) {"Missing or duplicate retained clinical target"}
                                }
                            } finally {bytes.fill(0)}
                        }
                    }
                }
            }
            var cipher=0L;var allocated=0L
            m.versions.forEach {checkActive();val b=it.manifest.ciphertextBytes;cipher=Math.addExact(cipher,b)
                allocated=Math.addExact(allocated,Math.multiplyExact(Math.addExact(b,resources.allocationUnit-1)/resources.allocationUnit,resources.allocationUnit))}
            require(cipher<=limits.maximumCiphertextBytes)
            // Two aliases/one-use wraps per retained version is conservative planning, not a reservation.
            val aliases=Math.multiplyExact(m.versions.size,2)
            require(aliases<=resources.availableAliases && aliases<=resources.availableWrapInvocations) {"Restore key budget exhausted"}
            require(resources.retainedStagingEntries.toLong()+m.versions.size<=limits.maximumStagingEntries)
            require(Math.addExact(resources.retainedStagingBytes,allocated)<=limits.maximumStagingBytes)
            // Reserve engineering headroom for encrypted metadata/SQL staging; future activation must
            // separately budget its full database/WAL/rollback cost. This is NOT total restore space.
            val space=Math.addExact(allocated,Math.addExact(size.toLong(),limits.minimumFreeBytes))
            require(space<=resources.freeBytes) {"Insufficient space for isolated preparation"}
            checkActive()
            return Plan.create(m,namespace,mapping,cipher,space,aliases,hash,size,limits)
        } finally {structuredBytes.fill(0)}
    }
    private fun table(kind:AttachmentBackupManifest.Kind)=when(kind) {
        AttachmentBackupManifest.Kind.APPOINTMENT->"appointment";AttachmentBackupManifest.Kind.LAB->"lab_value";AttachmentBackupManifest.Kind.LAB_PANEL->"lab_panel";AttachmentBackupManifest.Kind.DOSE_RECORD->"dose_record"
    }
    private fun <T> immutable(values:List<T>):List<T> = Collections.unmodifiableList(ArrayList(values))
}
