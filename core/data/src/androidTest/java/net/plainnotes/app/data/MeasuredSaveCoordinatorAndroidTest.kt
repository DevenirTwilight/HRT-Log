package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertTrue

/** Same safety assertions as JVM; real native SQLCipher on each CI Android API. */
class MeasuredSaveCoordinatorAndroidTest:MeasuredSaveCoordinatorContract() {
    override fun factory():SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(ByteArray(32){0x37})
    }
    override fun verifyDatabase(database:NotesDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA cipher_version").use {
            assertTrue("SQLCipher runtime must be active",it.moveToFirst() && !it.getString(0).isNullOrBlank())
        }
    }
}
