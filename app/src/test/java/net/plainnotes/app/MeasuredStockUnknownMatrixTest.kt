package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File
import java.time.*

/** Synthetic-only safety matrix: 32 configurations × 3 contrasts × stock/home = 192 rendered views. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class MeasuredStockUnknownMatrixTest(private val locale:String,private val width:Int,private val font:Float,private val dark:Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x/dark={3}") fun cases()=
            listOf("en","fr-rFR","zh-rCN","zh-rTW").flatMap {l->listOf(320,411).flatMap {w->listOf(1f,2f).flatMap {f->listOf(false,true).map {d->arrayOf<Any>(l,w,f,d)}}}}
    }
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun unknownNeverBecomesScalarStockOrAnUnscopedWrite() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if(width==320)640 else 891}dp-xxhdpi")
        RuntimeEnvironment.setFontScale(font)
        val med=MedicationEntity(1,"SYNTHETIC Œstradiol / ester / hydrate — long original product identity", "E2","ORAL","MG",2.0,28.0,null,15,60,false,null,true,true,0)
        val boxes=listOf(ContainerEntity(1,1,28.0,0.0,10.0,"2026-03-01","IN_USE",inventory_encoding="INTEGER_QUANTA_V1"))
        val forecasts=StockForecasts.forecastAll(listOf(med),boxes,emptyList())
        val calls=mutableListOf<String>()
        var contrast by mutableStateOf(Contrast.STANDARD)
        var home by mutableStateOf(false)
        ui.setContent {NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT,contrast=contrast){Surface {
            if(home) HomeStockCard(listOf(med),forecasts,LocalDate.of(2026,3,10),ZoneId.of("Europe/Paris"),{calls+="view"},{calls+="add"})
            else StockScreen(NotesState(listOf(med),loading=false),boxes,emptyList(),{calls+="replace"},{calls+="check"},{calls+="add"},PaddingValues(),onManage={calls+="manage"},forecasts=forecasts)
        }}}
        val label=System.getenv("ANSM_P4_SHOTS")?.takeIf{it.matches(Regex("[A-Za-z0-9_-]+"))}
        fun shot(name:String) {
            if(label==null)return
            ui.waitForIdle()
            val view=ui.activity.window.decorView
            check(view.width>0 && view.height>0)
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val folder=File("build/ansm-p4-ui-shots/$label").apply{mkdirs()}
            File(folder,"${locale}_${width}dp_${font}x_${if(dark)"dark" else "light"}_${contrast.name}_${name}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
        for(mode in Contrast.entries) {
            ui.runOnIdle{contrast=mode;home=false};ui.waitForIdle()
            ui.onNodeWithTag("stock-unknown-1").assertIsDisplayed().assertTextEquals(ui.activity.getString(R.string.forecast_needs_check))
            ui.onNodeWithText(med.name).assertIsDisplayed()
            for(tag in listOf("stock-primary-1","stock-more-1","stock-manage-1")) ui.onNodeWithTag(tag).assertDoesNotExist()
            ui.onAllNodes(hasText("mg",substring=true,ignoreCase=true)).assertCountEquals(0)
            shot("stock")
            ui.runOnIdle{home=true};ui.waitForIdle()
            ui.onNodeWithText(ui.activity.getString(R.string.forecast_needs_check)).assertIsDisplayed()
            ui.onNodeWithTag("home-stock-add:1").assertDoesNotExist()
            ui.onNodeWithTag("home-stock-unknown-view").assertIsDisplayed().assertHasClickAction()
            shot("home")
            ui.onNodeWithTag("home-stock-unknown-view").performClick()
        }
        assertEquals(listOf("view","view","view"),calls)
    }
}
