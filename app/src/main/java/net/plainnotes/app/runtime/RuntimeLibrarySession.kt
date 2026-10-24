package net.plainnotes.app.runtime

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    fun pause() {allowed=false}
    suspend fun resume() {initialized.await();verify();if(!closed)allowed=true}
    fun invalidate(message:Int=R.string.library_scope_changed) {closed=true;allowed=false;mutable.value=State.Unavailable(message)}
    fun close() {closed=true;allowed=false}
    companion object {
        const val SAVED_SCOPE="library.request.scope"
        const val RESTORED="library.request.restored"
        const val INTENT_SCOPE=LibraryRequestScope.INTENT_EXTRA
    }
}
