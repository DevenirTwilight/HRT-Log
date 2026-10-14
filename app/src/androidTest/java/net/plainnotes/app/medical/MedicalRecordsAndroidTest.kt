package net.plainnotes.app.medical
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
@RunWith(Parameterized::class) class MedicalRecordsAndroidTest(locale:String):MedicalRecordsUiContract(locale){companion object{@JvmStatic @Parameterized.Parameters(name="{0}/320dp/2x") fun locales()=listOf("en","zh-CN","zh-TW","fr").map{arrayOf<Any>(it)}}}
