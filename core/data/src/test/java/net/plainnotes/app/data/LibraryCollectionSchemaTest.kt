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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class LibraryCollectionSchemaTest {
    @Test fun partialCollectionAndMutableReservationsCannotPublishOrReceiveRefund() {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),NotesDatabase::class.java)
            .allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val r=db.openHelper.writableDatabase;val operation=UUID.randomUUID().toString();val generation=UUID.randomUUID().toString()
            fun reject(action:()->Unit) {try {action();fail("Unsafe transition accepted")}catch(_:android.database.SQLException){}}
            r.execSQL("INSERT INTO library_collection VALUES(?,zeroblob(32),'LEGACY',?,?,?,2,'PREPARED',1)",arrayOf(operation,UUID.randomUUID().toString(),UUID.randomUUID().toString(),generation))
            reject {r.execSQL("UPDATE library_collection SET state='READY'")}
            reject {r.execSQL("UPDATE library_collection SET state='COMMITTED'")}
            reject {r.execSQL("UPDATE library_collection SET members=1")}
            reject {r.execSQL("DELETE FROM library_collection")}
            r.execSQL("INSERT INTO library_write_reservation VALUES(?,?,?,?,zeroblob(32),16790000,'STARTED',1)",arrayOf(UUID.randomUUID().toString(),generation,UUID.randomUUID().toString(),UUID.randomUUID().toString()))
            reject {r.execSQL("UPDATE library_write_reservation SET reserved_ciphertext=0")}
            reject {r.execSQL("DELETE FROM library_write_reservation")}
            try {LibraryCollectionSchema.requireNoWrites(r);fail("Unresolved IO not fenced")}catch(_:IllegalStateException){}
            r.execSQL("UPDATE library_write_reservation SET state='COMMITTED'")
            LibraryCollectionSchema.requireNoWrites(r)
            reject {r.execSQL("UPDATE library_write_reservation SET state='STARTED'")}
        }finally {db.close()}
    }
}
