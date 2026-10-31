package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimelineSkippedGroupUiTest(private val language:String,private val width:Int,private val font:Float,private val mode:ThemeMode) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x/{3}") fun languages()=
        listOf("en","zh-rCN","b+zh+Hant","fr").flatMap{language->listOf(arrayOf<Any>(language,320,2f,ThemeMode.LIGHT),arrayOf<Any>(language,411,1f,ThemeMode.DARK))} }
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Before fun locale(){RuntimeEnvironment.setQualifiers("$language-w${width}dp-h800dp-xxhdpi")}
    private fun show():List<RecordEntity> {
        val today=LocalDate.now(ZoneOffset.UTC)
        val med=MedicationEntity(1,"SYNTHETIC MEDICINE","E2","ORAL","MG",2.0,20.0,site_rotation=false,notifications_on=false,active=true,sort_order=0)
        val frozen=MedicationSnapshot.encode(med,ProfileEntity(1,"E2","oral"))
        val definition=RegimenDefinition(frozen,"EVERY_N_DAYS",1,0,2.0,"UTC",today.minusDays(8).toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
        val version=RegimenVersionEntity(1,1,today.minusDays(8).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),null,"UTC",definition.json(),definition.signature(),"APP",1)
        val rows=(0L..4).flatMap{d->listOf(8,20).mapIndexed{i,h->
            RecordEntity(d*2+i+1,1,rule_version_id=7,slot_key="synthetic:$d:$h",scheduled_utc=today.minusDays(4).plusDays(d).atTime(h,0).toInstant(ZoneOffset.UTC).toEpochMilli(),scheduled_zone="UTC",planned_dose=2.0,status="SKIPPED",origin="APP",revision=1,config_snapshot=frozen)}}
        ui.setContent { CompositionLocalProvider(LocalDensity provides Density(3f,font)) { NotesTheme(mode) { androidx.compose.material3.Surface {
            LongitudinalScreen(NotesState(loading=false),NotesViewModel.ExtraState(records=rows,regimens=listOf(version)),{},{},{},PaddingValues())
        } } } }
        return rows
    }
    @Test fun fiveDaysHaveOneCollapsedSummaryAndEveryOriginalDetailRemainsAvailable() {
        val rows=show();val summary="timeline:skip-group:skip:1"
        System.getenv("HRT_SKIP_GROUP_SCREENSHOT_DIR")?.let{directory->
            val file=java.io.File(directory,"${language}-${width}-${font}-${mode}.png");file.parentFile.mkdirs()
            ui.waitForIdle()
            val view=ui.activity.window.decorView
            val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            try {
                ui.runOnUiThread{view.draw(android.graphics.Canvas(image))}
                file.outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            } finally { image.recycle() }
        }
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(summary))
        ui.onNodeWithTag(summary).assertIsDisplayed().assertHasClickAction()
            .assertTextContains(ui.activity.resources.getQuantityString(R.plurals.timeline_skip_count,10,10),substring=true)
        rows.forEach{ui.onNodeWithTag("timeline:skip:${it.id}").assertDoesNotExist()}
        ui.onNodeWithTag("timeline-upcoming-card").assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.timeline_imported_history)).assertDoesNotExist()
        ui.onNodeWithTag(summary).performClick()
        rows.forEach{row->
            ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:skip:${row.id}"))
            ui.onNodeWithTag("timeline:skip:${row.id}").assertIsDisplayed().assertHasClickAction()
        }
        ui.onNodeWithTag("timeline:skip:10").performClick()
        ui.onNodeWithTag("timeline-event-time").assertTextContains("UTC",substring=true)
        ui.onNodeWithText(ui.activity.getString(R.string.history_planned_dose_label,ui.activity.getString(R.string.dose_display,"2",ui.activity.getString(R.string.choice_mg)))).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.ok)).performClick()
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag(summary));ui.onNodeWithTag(summary).performClick()
        rows.forEach{ui.onNodeWithTag("timeline:skip:${it.id}").assertDoesNotExist()}
    }
    @Test fun labFilterDoesNotLeakSkippedSummaryIntoLabResults() {
        show()
        ui.onNodeWithTag("timeline-filter:LAB").performClick()
        ui.onNodeWithTag("timeline:skip-group:skip:1").assertDoesNotExist()
        ui.onNodeWithTag("timeline-filter:ALL").performClick()
        ui.onNodeWithTag("period-timeline").performScrollToNode(hasTestTag("timeline:skip-group:skip:1"))
        ui.onNodeWithTag("timeline:skip-group:skip:1").assertExists()
    }
}
