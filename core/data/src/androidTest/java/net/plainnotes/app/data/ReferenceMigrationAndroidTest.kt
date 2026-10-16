package net.plainnotes.app.data
import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
class ReferenceMigrationAndroidTest : ReferenceMigrationContract() {
    override val assetsContext:Context get()=InstrumentationRegistry.getInstrumentation().context
    override fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory {System.loadLibrary("sqlcipher");return SupportOpenHelperFactory(key)}
}
