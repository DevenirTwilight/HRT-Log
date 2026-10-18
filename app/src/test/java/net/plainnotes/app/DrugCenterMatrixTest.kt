package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual Compose render, synthetic empty state; no fixture health data or screenshot-only success. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class DrugCenterMatrixTest(private val locale:String,private val width:Int,private val font:Float,private val dark:Boolean,private val landscape:Boolean) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x/dark={3}/landscape={4}") fun cases()=
        listOf("en","fr-rFR","zh-rCN","zh-rTW").flatMap {l->listOf(320,411).flatMap {w->listOf(1f,2f).flatMap {f->listOf(false,true).flatMap {d->
            (if(f==2f)listOf(false,true) else listOf(false)).map {land->arrayOf<Any>(l,w,f,d,land)}
        }}}}}
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun allExistingModuleEntrancesRemainReachableAtEveryContrast() {
        val height=if(width==320)640 else 891
        RuntimeEnvironment.setQualifiers("$locale-w${if(landscape)height else width}dp-h${if(landscape)width else height}dp-${if(landscape)"land" else "port"}-xxhdpi");RuntimeEnvironment.setFontScale(font)
        var contrast by mutableStateOf(Contrast.STANDARD)
        var browsed=0;var linked=0
        val medication=net.plainnotes.app.data.MedicationEntity(id=7,name="SYNTHETIC long product / 0.75 mg per pump / 1.25 g gel / compound, ester, hydrate",molecule="OTHER",unit="MG",dose_per_intake=1.0,container_capacity=1.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        ui.setContent {NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT,contrast=contrast) {
            DrugReferenceCenterDialog(listOf(medication),{browsed++},{org.junit.Assert.assertEquals(medication,it);linked++}){}
        }}
        for(mode in Contrast.entries) {
            ui.runOnIdle{contrast=mode}
            ui.onNodeWithTag("drug-center-close").assertIsDisplayed().assertHasClickAction()
            val list=ui.onNodeWithTag("drug-center-list")
            list.performScrollToNode(hasTestTag("drug-center-browse"))
            ui.onNodeWithTag("drug-center-browse").assertIsDisplayed().assertHasClickAction().performClick()
            list.performScrollToNode(hasTestTag("drug-center-medication:7"))
            ui.onNodeWithTag("drug-center-medication:7").assertIsDisplayed().performClick()
            for(tag in listOf("drug-center-identity","drug-center-packaging")) {
                list.performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).assertIsDisplayed().assert(hasClickAction().not())
            }
            for(tag in listOf("drug-center-catalogue","drug-center-rcp","drug-center-notice","drug-center-safety","drug-center-supply","drug-center-recalls")) {
                list.performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction().performClick()
                ui.onNodeWithTag("center-unavailable-dialog").assertExists()
                ui.onNodeWithTag("center-unavailable-close").assertIsDisplayed().performClick()
                ui.onNodeWithTag("center-unavailable-dialog").assertDoesNotExist()
            }
            list.performScrollToIndex(0)
            if(System.getenv("CENTER_SHOTS")=="1" && width==320 && font==2f && !landscape && mode==Contrast.HIGH) {
                ui.waitForIdle();val view=requireNotNull(org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull{it.isShowing}?.window).decorView
                check(view.width>0 && view.height>0)
                val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(image))
                val colors=hashSetOf<Int>()
                for(x in 0 until image.width step 16)for(y in 0 until image.height step 16)colors.add(image.getPixel(x,y))
                check(colors.size>8){"Blank center screenshot"}
                val folder=File("build/drug-center-shots").apply{mkdirs()}
                File(folder,"${locale}_${width}dp_${font}x_${if(dark)"dark" else "light"}_${mode.name}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        assertEquals("browse callback",3,browsed);assertEquals("medication callback",3,linked)
        ui.onAllNodesWithTag("attachment-add").assertCountEquals(0)
    }
}
