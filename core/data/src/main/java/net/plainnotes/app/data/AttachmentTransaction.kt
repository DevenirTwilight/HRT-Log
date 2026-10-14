package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import java.util.UUID
import java.util.concurrent.Callable

/** Entire synchronous Room transaction, INCLUDING SQLite commit, is inside capability publication.
 * Perform preparation/crypto/filesystem IO first. B1 has no real attachment writer.
 */
internal object AttachmentTransaction {
    fun <T> commit(db:NotesDatabase,scope:AttachmentIdentity.Scope,capability:AttachmentSession.Capability,block:()->T):T =
        capability.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
            db.runInTransaction(Callable {
                check(AttachmentIdentity.current(db.openHelper.writableDatabase)==scope){"Dataset changed"}
                block()
            })
        }
}
