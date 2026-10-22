package net.plainnotes.app.data
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class AttachmentFoundationTest : AttachmentFoundationContract() {
    override val pipeStatSize: Long get() = 0L
}
