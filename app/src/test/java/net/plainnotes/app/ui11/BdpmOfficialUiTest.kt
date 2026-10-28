package net.plainnotes.app.ui11

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Public production data; synthetic host only for layout/intent checks. Native MainActivity coverage is separate. */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BdpmOfficialUiTest(private val locale:String,private val width:Int,private val font:Float,private val dark:Boolean) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x/dark={3}") fun cases()=
        listOf("en","fr-rFR","zh-rCN","zh-rTW").flatMap {l->listOf(320,411).flatMap {w->listOf(1f,2f).flatMap {f->listOf(false,true).map {d->arrayOf<Any>(l,w,f,d)}}}}}
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private fun text(id:Int)=ui.activity.getString(id)
    private fun press(tag:String)=ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
    @Test fun originalFactsAndExplicitExternalConsentRemainUsable() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h800dp-port-xxhdpi");RuntimeEnvironment.setFontScale(font)
        ui.setContent {NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT) {
            Surface(Modifier.fillMaxSize()) {Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {BdpmOfficialPanel("65421374","PROVAMES 2 mg, comprimé pelliculé")}}
        }}
        val app=org.robolectric.Shadows.shadowOf(ui.activity)
        press("bdpm-official-toggle")
        ui.waitUntil(10000) {ui.onAllNodesWithTag("bdpm-official-prescribing").fetchSemanticsNodes().isNotEmpty()}
        press("bdpm-official-prescribing");ui.onNodeWithText("liste II").performScrollTo().assertIsDisplayed()
        press("bdpm-official-has")
        ui.onNodeWithText("SMR · 2025-09-24 · Modéré · CT-21206").performScrollTo().assertIsDisplayed()
        press("bdpm-assessment-0")
        ui.onNode(hasText("Le service médical rendu par PROVAMES 2 mg",substring=true)).assertExists()
        press("bdpm-assessment-0");press("bdpm-official-has");press("bdpm-official-prescribing")
        press("bdpm-official-documents")
        ui.onNodeWithText(text(R.string.drug_center_rcp)).performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithText(text(R.string.official_external_title)).assertExists()
        assertNull("No browser before explicit confirmation",app.nextStartedActivity)
        ui.onNodeWithText(text(R.string.cancel)).performClick()
        assertNull("Cancel has no external side effect",app.nextStartedActivity)
        if(System.getenv("ANSM_OFFICIAL_SHOTS")=="1") {
            ui.onNodeWithTag("bdpm-official-documents").performScrollTo();ui.waitForIdle()
            val view=ui.activity.window.decorView
            val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image));val dir=File("build/ansm-official-shots").apply {mkdirs()}
            File(dir,"${locale}_${width}dp_${font}x_${if(dark)"dark" else "light"}.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        ui.onNodeWithText(text(R.string.drug_center_rcp)).performScrollTo().performClick()
        ui.onNodeWithTag("bdpm-official-open-confirm").performClick()
        val intent=requireNotNull(app.nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW,intent.action)
        assertEquals("https://base-donnees-publique.medicaments.gouv.fr/medicament/65421374/extrait#tabpanel-rcp-panel",intent.dataString)
        assertTrue(intent.extras==null || intent.extras!!.isEmpty)
    }
}
