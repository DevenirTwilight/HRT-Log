package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real component renders and touch feedback; screenshots contain only synthetic/empty data. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class CenterModuleScreenshotsTest(private val locale:String,private val dark:Boolean) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/dark={1}") fun cases()=
        listOf("en","fr-rFR","zh-rCN","zh-rTW").flatMap{l->listOf(false,true).map{d->arrayOf<Any>(l,d)}}}
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun futureSectionsAndDialogsAreRenderedAndDismissible() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-port-xxhdpi");RuntimeEnvironment.setFontScale(2f)
        var medical by mutableStateOf(true)
        var actions=0
        ui.setContent {NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT,contrast=Contrast.HIGH) {
            if(medical)Scaffold{pad->MedicalRecordsScreen(emptyList(),{actions++},{actions++},{actions++},{actions++},{actions++},pad,onVisits={actions++})}
            else DrugReferenceCenterDialog(emptyList(),{actions++},{actions++}){actions++}
        }}
        for(kind in listOf("medical","drug")) {
            ui.runOnIdle{medical=kind=="medical"}
            if(medical) {
                // UI-10: the three separate "not open" cards became one explanatory card in the Files area. It is read-only text:
                // no dialog, no importer; the drug center's dialogs below are unchanged.
                ui.onNodeWithTag("medical-tab:files").performClick()
                ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-files-soon"))
                ui.onNodeWithTag("medical-files-soon").assertIsDisplayed().assertHasNoClickAction()
                capture("medical-future",false)
                continue
            }
            ui.onAllNodesWithTag("drug-center-recalls").assertCountEquals(0)
            ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag("drug-center-rcp"))
            ui.onNodeWithTag("drug-center-rcp").assertHasClickAction()
            capture("$kind-official",true)
            ui.onNodeWithTag("center-unavailable-dialog").assertDoesNotExist()
        }
        Assert.assertEquals(0,actions)
    }
    private fun capture(kind:String,dialog:Boolean) {
        if(System.getenv("CENTER_SHOTS")!="1")return
        ui.waitForIdle()
        val view=if(dialog)requireNotNull(org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull{it.isShowing}?.window).decorView else ui.activity.window.decorView
        check(view.width>0 && view.height>0)
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val colors=hashSetOf<Int>()
        for(x in 0 until bitmap.width step 16)for(y in 0 until bitmap.height step 16)colors.add(bitmap.getPixel(x,y))
        check(colors.size>8){"Blank module screenshot"}
        val folder=File("build/center-module-shots").apply{mkdirs()}
        File(folder,"${kind}_${locale}_${if(dark)"dark" else "light"}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
