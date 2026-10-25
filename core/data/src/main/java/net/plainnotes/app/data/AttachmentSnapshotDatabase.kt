package net.plainnotes.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Callable

/** Read boundary only. A collection's fence is an existing native root transaction, not a
 * second routing authority. No retained database handle is exposed to Repository/UI writers. */
internal class AttachmentSnapshotDatabase private constructor(
    val context:Context,val raw:SupportSQLiteDatabase,val ownedTransaction:Boolean,
    private val notes:NotesDatabase?,private val authority:()->Unit,
) {
    fun checkAuthority() {check(raw.isOpen);authority()}
    fun <T> guard(block:()->T):T {
        checkAuthority()
        return if(notes!=null)DatabaseAccess.withActiveHandle(notes,block)
        else block().also {checkAuthority()}
    }
    fun <T> transaction(block:Callable<T>):T {
        checkAuthority()
        if(notes!=null)return notes.runInTransaction(block)
        if(raw.inTransaction()) {check(ownedTransaction);return block.call()}
        // Even READONLY sources must establish a real SQLite transaction: the frozen full
        // audit checks inTransaction and total_changes. Never fake that oracle precondition.
        raw.beginTransaction()
        try {val result=block.call();checkAuthority();raw.setTransactionSuccessful();return result}
        finally {raw.endTransaction()}
    }
    companion object {
        fun current(context:Context,db:NotesDatabase)=AttachmentSnapshotDatabase(
            DatabaseAccess.storageContext(db,context),db.openHelper.writableDatabase,false,db,
            {DatabaseAccess.assertActiveHandle(db)})
        /** Caller must assert root.inTransaction, same owning thread, current authorization,
         * complete generation membership and the held directory identity on every invocation. */
        fun fenced(context:Context,raw:SupportSQLiteDatabase,authority:()->Unit)=
            AttachmentSnapshotDatabase(context,raw,true,null,authority)
    }
}
