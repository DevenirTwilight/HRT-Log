package net.plainnotes.app.runtime

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.plainnotes.app.R
import net.plainnotes.app.data.*

/** One screen lifetime, one immutable request identity. A wake-up never retags displayed integers.
 * SavedState contains neutral scope metadata only, never an unlock grant or pending mutation.
 */
class RuntimeLibrarySession(private val source:NotesRepository,private val saved:SavedStateHandle) {
    sealed interface State {
        data object Loading:State
        data class Ready(val scope:LibraryRequestScope):State
        data class Unavailable(val message:Int):State
    }
    private val mutable=MutableStateFlow<State>(State.Loading)
    val state=mutable.asStateFlow()
    private val initialized=CompletableDeferred<NotesRepository>()
    @Volatile private var closed=false
    @Volatile private var allowed=true
    private var frozen:LibraryRequestScope?=null
    private var bound:NotesRepository?=null
    internal val ended get()=closed
    fun currentRepository():NotesRepository {checkRequest();return bound ?: throw LibraryRequestRevoked()}
    fun checkRequest() {if(closed || !allowed)throw LibraryRequestRevoked()}
    suspend fun initialize(onBound:(NotesRepository)->Unit={}):NotesRepository {
        try {
            val raw=runCatching {saved.get<ArrayList<String>>(SAVED_SCOPE)}.getOrNull()
            val restored=LibraryRequestScope.decode(raw)
            if((saved.contains(SAVED_SCOPE) && restored==null) || (saved.get<Boolean>(RESTORED)==true && restored==null)) {
                invalidate(R.string.library_scope_unknown);throw LibraryScopeMismatch()
            }
            val current=source.currentGenerationScope()
            if(restored!=null && restored!=current)throw LibraryScopeMismatch()
            checkRequest();frozen=current
            val repo=source.forGeneration(current,::checkRequest).pinnedTo(Space.PRIMARY)
            saved[SAVED_SCOPE]=current.encoded()
            bound=repo;onBound(repo)
            mutable.value=State.Ready(current);initialized.complete(repo)
            return repo
        } catch(e:Exception) {
            if(mutable.value !is State.Unavailable)invalidate(if(e is LibraryScopeMismatch)R.string.library_scope_changed else R.string.data_error)
            initialized.completeExceptionally(e);throw e
        }
    }
    suspend fun repository()=initialized.await().also {checkRequest()}
    /** Compare only. Even explicit resume cannot bind this owner to a different library. */
    suspend fun verify() {
        if(closed)throw LibraryScopeMismatch()
        val expected=frozen ?: return
        if(source.currentGenerationScope()!=expected) {invalidate(R.string.library_scope_changed);throw LibraryScopeMismatch()}
    }
    // Presentation invalidation is notification only, never an unlock grant or another authority.
    private val hiddenListeners=mutableSetOf<()->Unit>()
    internal fun observePresentationHidden(listener:()->Unit):AutoCloseable {
        synchronized(hiddenListeners) {checkRequest();hiddenListeners+=listener}
        return AutoCloseable {synchronized(hiddenListeners) {hiddenListeners-=listener}}
    }
    fun hideSensitivePresentation() {
        synchronized(hiddenListeners) {hiddenListeners.toList()}.forEach {runCatching {it()}}
    }
    private val protectedResources=mutableSetOf<AutoCloseable>()
    private fun releaseProtected() {
        val owned=synchronized(protectedResources) {protectedResources.toList().also {protectedResources.clear()}}
        owned.forEach {runCatching {it.close()}} // Permission is already revoked even if cleanup reports a fault.
    }
    /** Close retained encrypted snapshots on lock/dataset revocation, without a persistent grant. */
    internal fun protect(resource:AutoCloseable):AutoCloseable {
        try{synchronized(protectedResources){checkRequest();protectedResources+=resource}}
        catch(t:Throwable){resource.close();throw t}
        return AutoCloseable{synchronized(protectedResources){protectedResources-=resource};resource.close()}
    }
    internal suspend fun attachments(target:net.plainnotes.app.data.MedicalAttachmentOperations.Target?,
        title:String,operation:java.util.UUID=java.util.UUID.randomUUID(),document:java.util.UUID=java.util.UUID.randomUUID()):net.plainnotes.app.attachments.AttachmentFlowController {
        val repo=repository();val scope=requireNotNull(frozen)
        val session=net.plainnotes.app.domain.AttachmentSession(java.util.UUID.fromString(scope.dataset),java.util.UUID.fromString(scope.epoch))
        val revoke=AutoCloseable {session.revoke()}
        try {
            synchronized(protectedResources) {checkRequest();protectedResources+=revoke}
            val registration=AutoCloseable {synchronized(protectedResources) {protectedResources-=revoke};session.revoke()}
            return net.plainnotes.app.attachments.AttachmentFlowController(repo.medicalAttachments(),session,registration,
                net.plainnotes.app.data.MedicalAttachmentOperations.Import(operation,document,target,title),::checkRequest)
        }catch(e:Throwable) {synchronized(protectedResources) {protectedResources-=revoke};session.revoke();throw e}
    }
    fun pause() {allowed=false;hideSensitivePresentation();releaseProtected()}
    suspend fun resume() {initialized.await();verify();if(!closed)allowed=true}
    fun invalidate(message:Int=R.string.library_scope_changed) {
        closed=true;allowed=false;hideSensitivePresentation();releaseProtected()
        // A late suspended verify must not downgrade acknowledgement of a real COMMIT.
        // This only preserves its terminal explanation; permissions remain revoked.
        mutable.update {old->if((old as? State.Unavailable)?.message==R.string.library_change_committed)old else State.Unavailable(message)}
    }
    fun close() {closed=true;allowed=false;hideSensitivePresentation();releaseProtected()}
    companion object {
        const val RESTORE_OPERATION="medical.restore.operation.v1"
        const val SAVED_SCOPE="library.request.scope"
        const val RESTORED="library.request.restored"
        const val INTENT_SCOPE=LibraryRequestScope.INTENT_EXTRA
    }
}
