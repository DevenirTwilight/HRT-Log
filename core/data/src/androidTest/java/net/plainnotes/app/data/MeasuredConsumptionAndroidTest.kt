package net.plainnotes.app.data
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertFalse
class MeasuredConsumptionAndroidTest : MeasuredConsumptionContract() {
    override fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory {System.loadLibrary("sqlcipher");return SupportOpenHelperFactory(key)}
    override fun encrypted(name:String) {
        val header=context.getDatabasePath(name).inputStream().use{input->ByteArray(16).also{java.io.DataInputStream(input).readFully(it)}}
        assertFalse(String(header,Charsets.US_ASCII).startsWith("SQLite format 3"))
    }
}
