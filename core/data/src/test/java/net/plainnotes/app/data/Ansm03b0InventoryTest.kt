package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Ansm03b0InventoryTest : Ansm03b0InventoryContract() {
    override fun factory(pass: ByteArray): SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
    override fun reportFile(name: String): File = File("build/reports/ansm03b0", name)
}
