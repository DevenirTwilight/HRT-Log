package net.plainnotes.app
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class BdpmReferenceFlowTest(private val locale:String):BdpmReferenceFlowContract() {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x") fun locales()=listOf("en","fr-rFR","zh-rCN","zh-rTW").map{arrayOf<Any>(it)}}
    // As in DataRefreshTest: Robolectric's paused Android main looper needs driving for viewModelScope results.
    override fun idlePlatform(){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
    override fun configureHost(){RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xxhdpi");RuntimeEnvironment.setFontScale(2f)}
}
