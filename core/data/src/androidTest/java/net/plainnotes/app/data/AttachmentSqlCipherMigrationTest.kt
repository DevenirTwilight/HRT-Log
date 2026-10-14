package net.plainnotes.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertFalse

/** Native encrypted driver; all shared migration assertions remain mandatory on devices. */
class AttachmentSqlCipherMigrationTest : AttachmentMigrationContract() {
    override val assetsContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    override fun factory(pass: ByteArray): SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(pass)
    }

    override fun assertEncryptedDatabase(name: String) {
        val header = context.getDatabasePath(name).inputStream().use {
            ByteArray(16).also { bytes -> java.io.DataInputStream(it).readFully(bytes) }
        }
        assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))
    }
}
