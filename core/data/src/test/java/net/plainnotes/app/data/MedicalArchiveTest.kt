package net.plainnotes.app.data

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MedicalArchiveTest:MedicalArchiveContract() {
    override fun canonical()=requireNotNull(javaClass.getResourceAsStream("/portable-review/canonical.bin")).use {it.readBytes()}
}
