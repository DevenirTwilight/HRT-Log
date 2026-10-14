package net.plainnotes.app
import net.plainnotes.app.medical.MedicalRecordsUiContract
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class MedicalRecordsUiTest(private val locale:String):MedicalRecordsUiContract(locale){companion object{@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x") fun locales()=listOf("en","zh-CN","zh-TW","fr").map{arrayOf<Any>(it)}};@Before fun screen(){RuntimeEnvironment.setQualifiers("${locale.replace("-","-r")}-w320dp-h640dp-xxhdpi")}}
