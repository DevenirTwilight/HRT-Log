package net.plainnotes.app.data

/** Stale-request identity, not an authentication credential. Capture with the displayed facts,
 * carry through queues/saved state, never derive from an old integer at execution time. */
data class LibraryRequestScope(val generation:String,val dataset:String,val epoch:String) {
    init {
        require(generation.length in 1..256)
        require(dataset.length==36 && epoch.length==36)
        require(java.util.UUID.fromString(dataset).toString()==dataset && java.util.UUID.fromString(epoch).toString()==epoch)
    }
    /** Only neutral identity metadata for SavedState/Intent. Never a credential or a medical ID. */
    fun encoded()=arrayListOf(generation,dataset,epoch)
    companion object {
        const val INTENT_EXTRA="library_request_scope"
        const val REJECTED_EXTRA="library_request_rejected"
        fun decode(values:List<*>?):LibraryRequestScope?=try {
            if(values?.size!=3 || values.any {it !is String})null else LibraryRequestScope(values[0] as String,values[1] as String,values[2] as String)
        } catch(_:IllegalArgumentException) {null}
    }
}

class LibraryScopeMismatch:IllegalStateException("This request belongs to another library")
class LibraryRequestRevoked:IllegalStateException("This library request is no longer authorized")
class LibraryRestorePending:IllegalStateException("A library restore requires completion or authorized abort")
