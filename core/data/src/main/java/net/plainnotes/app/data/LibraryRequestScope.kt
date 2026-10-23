package net.plainnotes.app.data

/** Stale-request identity, not an authentication credential. Capture with the displayed facts,
 * carry through queues/saved state, never derive from an old integer at execution time. */
data class LibraryRequestScope(val generation:String,val dataset:String,val epoch:String)
