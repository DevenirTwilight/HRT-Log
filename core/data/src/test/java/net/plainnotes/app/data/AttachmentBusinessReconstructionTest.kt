package net.plainnotes.app.data

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class AttachmentBusinessReconstructionTest:AttachmentBusinessReconstructionContract() {
    override fun factory()=FrameworkSQLiteOpenHelperFactory()
}
