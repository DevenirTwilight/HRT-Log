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
class AttachmentActivationTest {
    @Test fun migrationPreservesIdentityAndActivationRefusesOtherDatasetsAndInvalidFlags() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val room=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val db=room.openHelper.writableDatabase
            db.beginTransaction()
            try {
                val scope=requireNotNull(AttachmentIdentity.initialize(db,true))
                AttachmentSchema.install(db) // Reinstall the historical OFF guard to simulate the migration edge.
                var refused=false
                try {db.execSQL("UPDATE attachment_store SET feature_enabled=1")}catch(_:Exception){refused=true}
                assertTrue(refused)
                migration20To21.migrate(db)
                assertEquals(scope,AttachmentIdentity.current(db))
                refused=false
                try {AttachmentActivationSchema.activate(db,scope.copy(dataset=UUID.randomUUID().toString()))}catch(_:IllegalStateException){refused=true}
                assertTrue(refused)
                AttachmentActivationSchema.activate(db,scope)
                refused=false
                try {db.execSQL("UPDATE attachment_store SET feature_enabled=2")}catch(_:Exception){refused=true}
                assertTrue(refused)
                assertEquals(scope,AttachmentIdentity.current(db))
                db.query("SELECT feature_enabled FROM attachment_store").use {assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
            }finally {db.endTransaction()}
            assertNull(AttachmentIdentity.current(db)) // Uncommitted migration/activation rolls back completely.
        }finally {room.close()}
    }
}
