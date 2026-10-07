package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w411dp-h891dp-xxhdpi",application=android.app.Application::class)
class LabContextUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun historicalVersionCanBeViewedAndRebuildingRequiresExplicitAction() {
        val at=Instant.parse("2026-03-10T12:00:00Z")
        val m=MedicationEntity(1,"Synthetic context","E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
        val lab=LabValueEntity(1,"E2",120.0,"pg/mL",at.toEpochMilli(),"UTC")
        val record=RecordEntity(1,1,taken_utc=at.minusSeconds(3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=MedicationSnapshot.encode(m,ProfileEntity(1,"E2","sublingual",sl_tier=2)))
        fun row(rev:Int,hours:Int)=LabContextEntity(rev.toLong(),1,rev,at.plusSeconds(rev.toLong()).toEpochMilli(),"RECONSTRUCTED",LabContext.build(lab,listOf(record.copy(taken_utc=at.minusSeconds(hours*3600L).toEpochMilli())),emptyList(),emptyMap()))
        var called=0;var estimate=false
        ui.setContent{MaterialTheme{Column(Modifier.verticalScroll(rememberScrollState())){LabContextSection(lab,listOf(row(1,1),row(2,2))){_,include->called++;estimate=include}}}}
        ui.onNodeWithText(ui.activity.getString(R.string.lab_context_title)).performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.lab_context_actual_line,"2","MG",ui.activity.getString(R.string.choice_sublingual),2L,0L)).assertExists()
        ui.waitForIdle()
        val view=ui.activity.window.decorView
        val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        val file=java.io.File("build/screenshots/lab_context.png");file.parentFile!!.mkdirs()
        file.outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        ui.onNodeWithText(ui.activity.getString(R.string.lab_context_version_number,2)).performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.lab_context_version_number,1)).performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.lab_context_actual_line,"2","MG",ui.activity.getString(R.string.choice_sublingual),1L,0L)).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.lab_context_rebuild)).performScrollTo().performClick()
        ui.runOnIdle{assertEquals(0,called)}
        ui.onNode(isToggleable()).performClick()
        ui.onNode(hasClickAction() and hasText(ui.activity.getString(R.string.lab_context_rebuild)) and hasAnyAncestor(isDialog())).performClick()
        ui.runOnIdle{assertEquals(1,called);assertTrue(estimate)}
    }
}
