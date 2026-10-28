package net.plainnotes.app.reference

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.plainnotes.app.data.*
import java.util.UUID

/** Dedicated reference writes: no medication save, reminders, global refresh or PK callback. */
data class MedicationReferenceUiState(
    val target:Long?=null,val current:MedicationReferenceState?=null,
    val products:List<SavedReferenceProduct> = emptyList(),val limited:Boolean=false,
    val loading:Boolean=false,val busy:Boolean=false,val failed:Boolean=false,val stale:Boolean=false,val saved:Boolean=false,
)
class MedicationReferenceController(private val repo:NotesRepository,private val scope:CoroutineScope) {
    private val mutable=MutableStateFlow(MedicationReferenceUiState())
    val state=mutable.asStateFlow()
    private var load:Job?=null
    private val changeCounter=MutableStateFlow(0)
    /** Bumps after each saved link/unlink so read-only views (the drug centre) can refresh their statuses. */
    val changes=changeCounter.asStateFlow()
    private var generation=0L
    private data class Attempt(val target:Long,val current:MedicationReferenceState,val product:UserReferenceProduct?,val uuid:String)
    private var attempt:Attempt?=null
    fun open(target:Long?):Job? {
        // A recreated screen may outlive its dialog while the original write is still pending.
        if(mutable.value.busy)return null
        load?.cancel();val token=++generation;attempt=null
        mutable.value=MedicationReferenceUiState(target=target,loading=true)
        return scope.launch {
            try {
                val current=target?.let {repo.medicationReferenceState(it)}
                val page=repo.savedReferenceProducts()
                if(token==generation)mutable.value=mutable.value.copy(current=current,products=page.entries,limited=page.limited,loading=false)
            } catch(e:CancellationException){throw e}
            catch(_:Exception){if(token==generation)mutable.value=mutable.value.copy(loading=false,failed=true)}
        }.also {load=it}
    }
    fun search(query:String,form:String,route:String,strength:String=""):Job? {
        if(mutable.value.busy || mutable.value.loading || (mutable.value.target!=null && mutable.value.current==null))return null
        load?.cancel();val token=++generation
        mutable.value=mutable.value.copy(loading=true,failed=false)
        return scope.launch {
            try {
                val page=repo.savedReferenceProducts(query,form,route,strength)
                if(token==generation)mutable.value=mutable.value.copy(products=page.entries,limited=page.limited,loading=false)
            }catch(e:CancellationException){throw e}
            catch(_:Exception){if(token==generation)mutable.value=mutable.value.copy(loading=false,failed=true)}
        }.also {load=it}
    }
    fun confirm(product:UserReferenceProduct?):Job? {
        val before=mutable.value
        if(before.busy || before.loading || before.current==null || before.target==null || before.stale)return null
        if(product==null && before.current.reference?.product==null)return null
        val frozen=product?.copy(ingredients=product.ingredients.toList(),routes=product.routes.toList())
        val pending=attempt?.takeIf {it.target==before.target && it.current==before.current && it.product==frozen}
            ?: Attempt(before.target,before.current,frozen,UUID.randomUUID().toString()).also {attempt=it}
        mutable.value=before.copy(busy=true,failed=false,saved=false)
        return scope.launch {
            try {
                val capturedScope=pending.current.scope
                val current=if(capturedScope==null) {
                    requireNotNull(pending.product)
                    repo.associateInitialMedication(pending.target,pending.uuid,pending.product,pending.current.previewEpoch)
                } else MedicationReferenceState(capturedScope,repo.associateMedication(capturedScope,pending.target,
                    pending.current.reference?.revision ?: 0,pending.uuid,pending.product),pending.current.previewEpoch)
                // An idempotent replay returns its original event; display the actual latest association instead.
                val fresh=repo.medicationReferenceState(pending.target)
                if(fresh.previewEpoch!=current.previewEpoch)throw ReferenceFailure(ReferenceError.STALE_SCOPE)
                mutable.value=mutable.value.copy(current=fresh,busy=false,saved=true)
                changeCounter.value++
                attempt=null
            }catch(e:CancellationException){throw e}
            catch(e:Exception){mutable.value=mutable.value.copy(busy=false,failed=true,stale=e is ReferenceFailure && e.code in setOf(ReferenceError.STALE_SCOPE,ReferenceError.STALE_REVISION))}
        }
    }
    /** Read-only: the current association of each listed medication; touches neither the dialog state nor any write path. */
    suspend fun links(medications:List<Long>):Map<Long,MedicationReference?> = medications.associateWith {repo.medicationReferenceState(it).reference}
    /** Read-only: how many user-entered snapshots are saved, and whether the list was cut at its page limit. */
    suspend fun savedSourceCount():Pair<Int,Boolean> = repo.savedReferenceProducts().let {it.entries.size to it.limited}
    fun close():Boolean {
        if(mutable.value.busy)return false
        load?.cancel();generation++;attempt=null;mutable.value=MedicationReferenceUiState()
        return true
    }
}
