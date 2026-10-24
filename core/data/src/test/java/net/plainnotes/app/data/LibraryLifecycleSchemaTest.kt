package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** SQL state-machine tests only; filesystem/Keystore absence is proved by native controller tests. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class LibraryLifecycleSchemaTest {
    @Test fun markerIsIrreversibleAndOnlyNeverStartedAbortedOwnerCanReceiveCredit() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val op=UUID.randomUUID().toString();val gen=UUID.randomUUID().toString()
        try {
            val r=db.openHelper.writableDatabase
            fun candidate(operation:String,generation:String) {
                r.execSQL("INSERT INTO library_candidate(operation_uuid,generation_uuid,dataset_uuid,epoch_uuid,state,archive_sha256,manifest,structured_sha256,structured_source,alias_plan,database_alias,reserved_aliases,reserved_ciphertext,created_utc) VALUES(?,?,?,?,'PREPARED',zeroblob(32),X'7b7d',zeroblob(32),X'7b7d',X'5b5d',?,1,0,1)",arrayOf(operation,generation,UUID.randomUUID().toString(),UUID.randomUUID().toString(),"notes.gen.db.$generation"))
                r.execSQL("INSERT INTO library_resource_owner VALUES(?,?,'LEGACY',?,?,0,0,NULL)",arrayOf(operation,generation,UUID.randomUUID().toString(),UUID.randomUUID().toString()))
            }
            fun refused(action:()->Unit) {try {action();fail("Invalid ownership transition accepted")}catch(_:android.database.SQLException){}}
            candidate(op,gen)
            refused {r.execSQL("UPDATE library_resource_owner SET released=1,release_digest=zeroblob(32) WHERE operation_uuid=?",arrayOf(op))}
            r.execSQL("UPDATE library_resource_owner SET started=1 WHERE operation_uuid=?",arrayOf(op))
            refused {r.execSQL("UPDATE library_resource_owner SET started=0 WHERE operation_uuid=?",arrayOf(op))}
            r.execSQL("UPDATE library_candidate SET state='ABORTED' WHERE operation_uuid=?",arrayOf(op))
            refused {r.execSQL("UPDATE library_resource_owner SET released=1,release_digest=zeroblob(32) WHERE operation_uuid=?",arrayOf(op))}
            val fresh=UUID.randomUUID().toString();candidate(fresh,UUID.randomUUID().toString())
            r.execSQL("UPDATE library_candidate SET state='ABORTED' WHERE operation_uuid=?",arrayOf(fresh))
            refused {r.execSQL("UPDATE library_resource_owner SET released=1,release_digest=NULL WHERE operation_uuid=?",arrayOf(fresh))}
            r.execSQL("UPDATE library_resource_owner SET released=1,release_digest=zeroblob(32) WHERE operation_uuid=?",arrayOf(fresh))
            refused {r.execSQL("UPDATE library_resource_owner SET started=1 WHERE operation_uuid=?",arrayOf(fresh))}
            refused {r.execSQL("UPDATE library_resource_owner SET released=0,release_digest=NULL WHERE operation_uuid=?",arrayOf(fresh))}
            refused {r.execSQL("DELETE FROM library_resource_owner WHERE operation_uuid=?",arrayOf(fresh))}
            r.query("SELECT started,released FROM library_resource_owner WHERE operation_uuid=?",arrayOf(fresh)).use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0));assertEquals(1,it.getInt(1))}

        }finally {db.close()}
    }
}
