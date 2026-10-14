package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fast fixture and Room validation feedback; native encryption is verified separately on devices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AttachmentMigrationTest : AttachmentMigrationContract() {
    override fun factory(pass: ByteArray): SupportSQLiteOpenHelper.Factory =
        FrameworkSQLiteOpenHelperFactory()
}
