package net.plainnotes.app

import net.plainnotes.app.stock.PackageHistoryUiContract
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PackageHistoryPaginationTest(private val locale: String) : PackageHistoryUiContract(locale) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/320dp/2x")
        fun locales() = listOf("en", "zh-CN", "zh-TW", "fr").map { arrayOf<Any>(it) }
    }
    @Before fun smallScreen() { RuntimeEnvironment.setQualifiers("${locale.replace("-", "-r")}-w320dp-h640dp-xxhdpi") }
}
