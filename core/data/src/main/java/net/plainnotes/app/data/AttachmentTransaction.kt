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
        capability.publishFor(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch)) {
            check(!db.openHelper.writableDatabase.inTransaction()) {"Attachment commit requires its own native transaction"}
            db.runInTransaction(Callable {
                check(AttachmentIdentity.current(db.openHelper.writableDatabase)==scope){"Dataset changed"}
                block()
            })
        }
}
