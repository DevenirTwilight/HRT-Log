package net.plainnotes.app.data
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class MeasuredConsumptionStoreTest : MeasuredConsumptionContract() {
    override fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory=FrameworkSQLiteOpenHelperFactory()
}
