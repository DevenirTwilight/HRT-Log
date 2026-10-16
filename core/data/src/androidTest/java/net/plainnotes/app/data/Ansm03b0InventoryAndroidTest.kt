package net.plainnotes.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.DataInputStream
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertFalse

class Ansm03b0InventoryAndroidTest : Ansm03b0InventoryContract() {
    override val assetsContext: Context get() = InstrumentationRegistry.getInstrumentation().context
    override fun factory(pass: ByteArray): SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(pass)
    }
    override fun checkEncryption(name: String) {
        val header = context.getDatabasePath(name).inputStream().use { input -> ByteArray(16).also { DataInputStream(input).readFully(it) } }
        assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))
    }
}
