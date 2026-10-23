package net.plainnotes.app.data

import java.util.UUID

/** Logical membership contract, NOT an archive wire format or a restore authorization. */
internal data class AttachmentBackupManifest(
    val dataset:UUID,val epoch:UUID,val logicalVault:UUID,
    val records:List<Record>,val documents:List<Document>,val versions:List<Version>,val links:List<Link>
) {
    enum class Kind { APPOINTMENT, LAB, DOSE_RECORD }
    data class Record(val uuid:UUID,val kind:Kind,val sourceId:Long,val state:String,val trashId:Long?,val evidenceSha256:String)
    data class Document(val uuid:UUID,val revision:Long,val title:String,val category:String,val state:String,
        val activeVersion:UUID,val deletedUtc:Long?)
    data class Version(val document:UUID,val ordinal:Long,val context:AttachmentObjectContext,
        val manifest:AttachmentObjectStreams.Manifest,val mime:String)
    data class Link(val uuid:UUID,val record:UUID,val document:UUID,val revision:Long,val state:String)
    data class Limits(val maximumRows:Int=100000,val maximumVersions:Int=1000,
        val maximumCiphertextBytes:Long=256L*1024*1024) {
        init {require(maximumRows in 1..100000 && maximumVersions in 1..100000 && maximumCiphertextBytes>=0)}
    }
    /** Exact retained closure; no name/age/integer-ID heuristics and no dropping trash/history. */
    fun validate(limits:Limits=Limits()) {
        require(records.size.toLong()+documents.size+versions.size+links.size<=limits.maximumRows)
        require(versions.size<=limits.maximumVersions)
        fun <T> unique(values:List<T>) {require(values.size==values.toSet().size) {"Duplicate stable identity"}}
        unique(records.map {it.uuid});unique(documents.map {it.uuid});unique(versions.map {it.context.version})
        unique(versions.map {it.context.objectId});unique(links.map {it.uuid})
        val rs=records.associateBy {it.uuid};val ds=documents.associateBy {it.uuid};val vs=versions.associateBy {it.context.version}
        val hash=Regex("[0-9a-f]{64}")
        records.forEach {require(it.sourceId>0 && it.state in setOf("ACTIVE","TRASHED","PURGED") && hash.matches(it.evidenceSha256))
            require((it.state=="TRASHED")== (it.trashId!=null));require(it.trashId==null || it.trashId>0)}
        documents.forEach {d->require(d.revision>0 && d.title.isNotBlank() && d.title.length<=256)
            require(d.category in setOf("REPORT","PRESCRIPTION","CERTIFICATE","RECORD","OTHER"))
            require(d.state in setOf("ACTIVE","TRASHED","PURGE_PENDING","PURGED"))
            require((d.state=="ACTIVE")== (d.deletedUtc==null))
            require(vs[d.activeVersion]?.document==d.uuid) {"Missing active retained version"}}
        var bytes=0L
        versions.forEach {v->require(v.document in ds && v.ordinal>0 && v.context.logicalVault==logicalVault)
            require(v.manifest.context==v.context && v.mime in setOf("application/pdf","image/png","image/jpeg","application/octet-stream"))
            bytes=Math.addExact(bytes,v.manifest.ciphertextBytes);require(bytes<=limits.maximumCiphertextBytes)}
        unique(versions.map {it.document to it.ordinal})
        links.forEach {require(it.record in rs && it.document in ds && it.revision>0 && it.state in setOf("ACTIVE","RETRACTED","QUARANTINED"))
            if(it.state=="ACTIVE")require(rs.getValue(it.record).state=="ACTIVE" && ds.getValue(it.document).state=="ACTIVE")}
    }
    /** Explicit, exhaustive typed mapping for a NEW local identity. Never infer from source integers. */
    data class Target(val record:UUID,val kind:Kind,val localId:Long?)
    fun mapTo(newDataset:UUID,newEpoch:UUID,targets:List<Target>):Map<UUID,Target> {
        validate();require(newDataset!=dataset && newEpoch!=epoch) {"Restore needs fresh local identities"}
        require(targets.size==records.size && targets.map {it.record}.toSet().size==targets.size)
        val live=targets.filter {it.localId!=null}
        require(live.map {it.kind to it.localId}.toSet().size==live.size)
        val mapped=targets.associateBy {it.record}
        records.forEach {r->val t=requireNotNull(mapped[r.uuid]);require(t.kind==r.kind)
            if(r.state=="PURGED")require(t.localId==null) {"Never resurrect a purged clinical row"}
            else require(t.localId!=null && t.localId>0)}
        return mapped
    }
}
