package net.plainnotes.app
import net.plainnotes.app.history.UndoSkippedUiContract
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndoSkippedHistoryUiTest(private val language:String):UndoSkippedUiContract(language) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x") fun languages()=listOf("en","zh-CN","zh-TW","fr").map{arrayOf<Any>(it)} }
    @Before fun screen(){RuntimeEnvironment.setQualifiers("${language.replace("-","-r")}-w320dp-h640dp-xxhdpi")}
}
