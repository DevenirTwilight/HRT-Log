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
class MedicalCenterMatrixTest(private val locale:String,private val width:Int,private val font:Float,private val dark:Boolean,private val landscape:Boolean) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x/dark={3}/landscape={4}") fun cases()=
        listOf("en","fr-rFR","zh-rCN","zh-rTW").flatMap {l->listOf(320,411).flatMap {w->listOf(1f,2f).flatMap {f->listOf(false,true).flatMap {d->
            (if(f==2f)listOf(false,true) else listOf(false)).map {land->arrayOf<Any>(l,w,f,d,land)}
        }}}}}
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun allExistingModuleEntrancesRemainReachableAtEveryContrast() {
        val height=if(width==320)640 else 891
        RuntimeEnvironment.setQualifiers("$locale-w${if(landscape)height else width}dp-h${if(landscape)width else height}dp-${if(landscape)"land" else "port"}-xxhdpi");RuntimeEnvironment.setFontScale(font)
        var contrast by mutableStateOf(Contrast.STANDARD)
        var visits=0;var labs=0;var timeline=0;var backups=0
        ui.setContent {NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT,contrast=contrast) {
            androidx.compose.material3.Scaffold { padding ->
                MedicalRecordsScreen(emptyList(),{}, {},{labs++},{timeline++},{},padding,onVisits={visits++},onMedicalBackup={backups++})
            }
        }}
        for(mode in Contrast.entries) {
            ui.runOnIdle{contrast=mode}
            val list=ui.onNodeWithTag("medical-list")
            for(tag in listOf("medical-center-visits","medical-center-prepare","medical-center-pack","medical-visit-attachments")) {
                list.performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction().performClick()
            }
            for(tag in listOf("medical-labs","medical-history","medical-complete-backup")) {
                list.performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction().performClick()
            }
            list.performScrollToNode(hasTestTag("medical-bin"));ui.onNodeWithTag("medical-bin").assertIsDisplayed()
            for(tag in listOf("medical-future-files","medical-future-practitioners","medical-future-organize")) {
                list.performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction().performClick()
                ui.onNodeWithTag("center-unavailable-dialog").assertExists()
                ui.onNodeWithTag("center-unavailable-close").assertIsDisplayed().performClick()
                ui.onNodeWithTag("center-unavailable-dialog").assertDoesNotExist()
            }
            list.performScrollToIndex(0)
            if(System.getenv("CENTER_SHOTS")=="1" && width==320 && font==2f && !landscape && mode==Contrast.HIGH) {
                ui.waitForIdle();val view=ui.activity.window.decorView
                check(view.width>0 && view.height>0)
                val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(image))
                val folder=File("build/medical-center-shots").apply{mkdirs()}
                File(folder,"${locale}_${width}dp_${font}x_${if(dark)"dark" else "light"}_${mode.name}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        assertEquals(12,visits);assertEquals(3,labs);assertEquals(3,timeline);assertEquals(3,backups)
        ui.onAllNodesWithTag("attachment-add").assertCountEquals(0)
    }
}
