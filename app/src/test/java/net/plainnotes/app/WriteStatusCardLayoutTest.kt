package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import net.plainnotes.app.domain.write.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Eight representative layouts, not the full cross-product or actual shell navigation. */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WriteStatusCardLayoutTest(private val locale:String,private val width:Int,private val font:Float,private val dark:Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x/dark={3}")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr-rFR").flatMap {locale->listOf(
            arrayOf<Any>(locale,320,2f,true),arrayOf<Any>(locale,411,1f,false))}
    }
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun manualResultCanBeReadAndCheckedWithoutAFullScreenBlock() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h891dp-xxhdpi")
        RuntimeEnvironment.setFontScale(font)
        var checked=0;var hidden=0
        val progress=WriteProgressSummary("SYNTHETIC layout",WriteStatus.PENDING,WritePhase.MANUAL_CHECK)
        ui.setContent {NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT){Surface {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                WriteStatusCard(progress,{hidden++},onCheck={checked++})
            }
        }}}
        ui.onNodeWithTag("write-status-title").assertIsDisplayed()
        ui.onNodeWithTag("write-status-check").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("write-status-hide").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithTag("write-status-retry").assertDoesNotExist()
        assertEquals(1,checked);assertEquals(1,hidden)
        ui.waitForIdle()
        val view=ui.activity.window.decorView
        val card=ui.onNodeWithTag("write-status").fetchSemanticsNode().boundsInRoot
        assertTrue("Card exceeds viewport",card.width<=view.width.toFloat())
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        try {
            view.draw(android.graphics.Canvas(image))
            val file=File("build/p4-write-status-shots/${locale}_${width}dp_${font}x_${if(dark)"dark" else "light"}.png")
            file.parentFile.mkdirs();file.outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally{image.recycle()}
    }
}
