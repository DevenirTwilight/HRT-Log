package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import java.util.UUID
import java.util.concurrent.Callable

/** Entire synchronous Room transaction, INCLUDING SQLite commit, is inside capability publication.
 * Perform preparation/crypto/filesystem IO first. Never borrow a caller's transaction/savepoint:
 * returning from a nested Room transaction would not prove a durable native COMMIT.
 */
internal object AttachmentTransaction {
    fun <T> commit(db:NotesDatabase,scope:AttachmentIdentity.Scope,capability:AttachmentSession.Capability,block:()->T):T =
        DatabaseAccess.commitActive(db,scope,capability,block)
}
