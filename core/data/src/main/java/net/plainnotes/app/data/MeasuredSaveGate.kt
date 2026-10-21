package net.plainnotes.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** A conflicting mutation was rejected before entry; this is not a queued write. */
class MeasuredSaveBusyException : IllegalStateException("A measured save requires result confirmation")

/** Process coordination only. SQLite transactions, original tickets and the complete audit remain
 * the authority. One gate belongs to DatabaseAccess, so pinned/ordinary repositories share it. */
internal class MeasuredSaveGate {
    internal class Lease(val space:Space)
    internal class Permit(val gate:MeasuredSaveGate,val lease:Lease):AbstractCoroutineContextElement(Key) {
        companion object Key:CoroutineContext.Key<Permit>
    }
    private val lock=Any()
    private val leases=HashMap<Space,Lease>()
    private val mutations=HashMap<Space,Int>()
    fun reserve(space:Space):Lease=synchronized(lock) {
        if(leases.containsKey(space) || mutations.getOrDefault(space,0)!=0)throw MeasuredSaveBusyException()
        Lease(space).also{leases[space]=it}
    }
    fun release(lease:Lease)=synchronized(lock) {
        check(leases[lease.space]===lease && mutations.getOrDefault(lease.space,0)==0)
        leases.remove(lease.space);Unit
    }
    private fun enter(space:Space,permit:Permit?)=synchronized(lock) {
        val active=leases[space]
        if(active!=null && (permit?.gate!==this || permit.lease!==active))throw MeasuredSaveBusyException()
        if(permit?.gate===this && (permit.lease.space!=space || active!==permit.lease))throw MeasuredSaveBusyException()
        mutations[space]=mutations.getOrDefault(space,0)+1
    }
    private fun leave(space:Space)=synchronized(lock) {
        val count=mutations.getValue(space);check(count>0)
        if(count==1)mutations.remove(space) else mutations[space]=count-1
    }
    suspend fun <T> mutation(space:Space,block:suspend()->T):T {
        enter(space,currentCoroutineContext()[Permit])
        try{return block()}finally{leave(space)}
    }
    /** Close/destroy cannot race reservation, even between their initial check and actual IO. */
    fun <T> synchronousMutation(space:Space,block:()->T):T {
        enter(space,null)
        try{return block()}finally{leave(space)}
    }
}
