package net.plainnotes.poc

import java.util.UUID

/** Deterministic MODEL, not Room migration/backup implementation. Pointer is committed last. */
class RecoveryModel(val store:UUID=UUID.randomUUID()) {
    data class Link(val recordId:Long,val recordEpoch:UUID)
    data class Entry(val id:UUID,val hash:String,val link:Link?,val trashed:Boolean=false,val revision:Int=0)
    private var rows=mapOf<UUID,Entry>()
    var generation:UUID=UUID.randomUUID();private set
    var recordEpoch:UUID=UUID.randomUUID();private set
    fun all()=rows.values.sortedBy{it.id.toString()}
    fun page(offset:Int,limit:Int=20)=all().drop(offset).take(limit)
    fun add(e:Entry):UUID {
        require(e.link==null || e.link.recordEpoch==recordEpoch)
        // Deduplicate repeated submission for the same association only. Never discard another explicit link.
        val duplicate=rows.values.firstOrNull{it.hash==e.hash && it.link==e.link && !it.trashed}
        if(duplicate!=null)return duplicate.id
        require(e.id !in rows);rows=rows+(e.id to e);return e.id
    }
    fun revise(expected:Entry,trashed:Boolean):Entry {
        check(rows[expected.id]==expected)
        val next=expected.copy(trashed=trashed,revision=expected.revision+1);rows=rows+(next.id to next);return next
    }
    fun restore(input:List<Entry>,incomingStore:UUID,epoch:UUID,fault:(Int)->Unit={}) {
        require(incomingStore==store && input.map{it.id}.distinct().size==input.size)
        val staged=mutableMapOf<UUID,Entry>()
        input.forEachIndexed{i,e->require(e.link==null || e.link.recordEpoch==epoch);staged[e.id]=e;fault(i)}
        fault(input.size) // Every failure before this commit preserves old generation/rows.
        rows=staged.toMap();recordEpoch=epoch;generation=UUID.randomUUID()
    }
    fun oldRecordOnlyRestore() {
        recordEpoch=UUID.randomUUID();rows=rows.mapValues{(_,e)->e.copy(link=null,revision=e.revision+1)}
        // Cipher objects remain quarantined; the same numeric ID cannot establish a relationship.
    }
}
