package net.plainnotes.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.*
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.reference.MedicationReferenceController
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File
import java.time.*
import java.util.UUID

/** Always exercises 4 locales × 2 widths × 2 fonts × 2 themes × 3 contrasts. Images are optional synthetic evidence. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class MedicationReferenceMatrixTest(private val locale:String,private val width:Int,private val font:Float,private val dark:Boolean) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x/dark={3}") fun cases()=
        listOf("en","fr-rFR","zh-rCN","zh-rTW").flatMap {l->listOf(320,411).flatMap {w->listOf(1f,2f).flatMap {f->listOf(false,true).map {d->arrayOf<Any>(l,w,f,d)}}}}}
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun productDetailsAndManualFallbackRemainReachable() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if(width==320)640 else 891}dp-xxhdpi");RuntimeEnvironment.setFontScale(font)
        val db=Room.inMemoryDatabaseBuilder(ui.activity,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val repo=NotesRepository(object:DatabaseAccess(ui.activity){override fun get(space:Space)=db})
            val product=UserReferenceProduct("SYNTHETIC long product Œstrogène / ester / hydrate — original strength preserved",listOf("SYNTHETIC compound A / SA / group 1","SYNTHETIC compound B, hydrate / FT / group 2"),
                "0,75 mg / 1,25 g gel / pump (SYNTHETIC)","gel",listOf("cutaneous"),"00000001","0000000000001",sourceVersion="user-synthetic-v1",recordedOn="2026-10-16")
            val controller=MedicationReferenceController(repo,scope)
            runBlocking {
                val id=repo.saveMedication(MedicationEntity(name="SYNTHETIC actual medication",molecule="OTHER",unit="TABLET",dose_per_intake=1.0,container_capacity=28.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),Instant.parse("2026-03-28T12:00:00Z"))
                repo.associateInitialMedication(id,UUID.randomUUID().toString(),product,repo.medicationReferenceState(id).previewEpoch);controller.open(id)!!.join()
            }
            var contrast by mutableStateOf(Contrast.STANDARD)
            ui.setContent{NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT,contrast=contrast){MedicationReferenceDialog(controller){}}}
            val label=System.getenv("ANSM_P2_SHOTS")?.takeIf {it.matches(Regex("[A-Za-z0-9_-]+"))}
            fun shot(name:String){
                if(label==null)return
                ui.waitForIdle()
                val view=requireNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window).decorView
                check(view.width>0 && view.height>0) { "Reference dialog has no rendered bounds" }
                val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(image))
                val folder=File("build/ansm-p2-ui-shots/$label").apply{mkdirs()}
                File(folder,"${locale}_${width}dp_${font}x_${if(dark)"dark" else "light"}_${contrast.name}_${name}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            for(mode in Contrast.entries) {
                ui.runOnIdle{contrast=mode};ui.waitForIdle()
                ui.onNodeWithTag("reference-close").assertIsDisplayed().assertHasClickAction()
                shot("top")
                ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-details"))
                ui.onAllNodesWithText(product.name).onFirst().assertExists()
                shot("details")
                ui.onNodeWithTag("reference-screen").performScrollToNode(hasTestTag("reference-manual"))
                ui.onNodeWithTag("reference-manual").assertIsDisplayed().assertIsEnabled()
                ui.onNodeWithTag("reference-screen").performScrollToIndex(0)
            }
        }finally{scope.cancel();db.close()}
    }
}
