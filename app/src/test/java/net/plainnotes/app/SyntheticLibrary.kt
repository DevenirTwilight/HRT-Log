package net.plainnotes.app

import net.plainnotes.app.data.NotesDatabase
import java.util.UUID

/** The real shell now has an asynchronous identity boundary. Drive the paused test main looper
 * before interacting; do not delete the Loading branch or weaken navigation assertions. */
internal fun awaitRuntimeLibrary(model:NotesViewModel) {
    val deadline=System.nanoTime()+20_000_000_000L
    while(model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Loading && System.nanoTime()<deadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)
    }
    check(model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready) {"Synthetic library failed initialization: ${model.librarySession.value}"}
}

/** Fake DatabaseAccess overrides skip production's initialize step. Establish only an UNKNOWN/OFF
 * synthetic PRIMARY namespace, never VERIFIED objects or a fabricated active library authority. */
private val initializedLibraries=java.util.WeakHashMap<NotesDatabase,Boolean>()
internal fun syntheticLibrary(db:NotesDatabase):NotesDatabase = synchronized(initializedLibraries) {
    if(initializedLibraries.containsKey(db))return@synchronized db
    db.runInTransaction {
        val raw=db.openHelper.writableDatabase
        val exists=raw.query("SELECT 1 FROM attachment_store WHERE store_state='CURRENT'").use {it.moveToFirst()}
        if(!exists) {
            val dataset=UUID.randomUUID().toString()
            raw.execSQL("INSERT INTO attachment_store(store_uuid,logical_vault_uuid,store_state,space,record_epoch,generation,active_generation_uuid,feature_enabled) VALUES(?,?,'CURRENT','PRIMARY',?,1,NULL,0)",
                arrayOf(dataset,UUID.randomUUID().toString(),UUID.randomUUID().toString()))
            raw.execSQL("INSERT INTO attachment_generation(store_uuid,generation_uuid,state,manifest_digest,created_utc) VALUES(?,?,'STAGING',NULL,?)",arrayOf(dataset,UUID.randomUUID().toString(),System.currentTimeMillis()))
        }
    }
    initializedLibraries[db]=true
    db
}
