package net.plainnotes.app.wellbeing

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.export.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** Synthetic records only. Android's native PDF backend verifies pagination, unlike Robolectric. */
class WellbeingAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun optionalDailySliderDoesNotWriteUntilTheUserChooses(){
        var value by mutableStateOf<Int?>(null);val writes=mutableListOf<Int?>()
        ui.setContent{MaterialTheme{FiveLevelInput(value,"Low","High","Synthetic entry"){value=it;writes+=it}}}
        ui.runOnIdle{assertTrue(writes.isEmpty())}
        ui.onNodeWithContentDescription("Synthetic entry").performSemanticsAction(SemanticsActions.SetProgress){it(5f)}
        ui.runOnIdle{assertEquals(5,value)}
        ui.onNodeWithText(ui.activity.getString(R.string.wb_clear)).performClick()
        ui.runOnIdle{assertNull(value);assertEquals(listOf(5,null),writes)}
    }
    @Test fun longMultilingualAppointmentSummaryHasValidNativePages(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val day=LocalDate.of(2026,2,10)
        val lab=LabValueEntity(1,"E2",120.0,"pg/mL",day.atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),java.time.ZoneId.systemDefault().id)
        val snapshot=LabContextEntity(1,1,1,lab.sampled_utc,"AT_ENTRY",LabContext.build(lab,emptyList(),emptyList(),emptyMap()))
        val d=ExportData(emptyList(),emptyMap(),emptyList(),listOf(lab),listOf(CheckinItemEntity(1,"DAY_MOOD",null,false,0)),listOf(CheckinScoreEntity(day.toString(),1,4)),emptyList(),emptyMap(),{"Synthetic mood"},
            symptoms=listOf(SymptomCheckEntity(day.toString(),"JAUNDICE","Synthetic symptom note")),
            reviews=listOf(StageReviewEntity(date=day.toString(),tolerance_note="Synthetic long note. 合成文字。 Texte synthétique.\n".repeat(300),weight_kg=60.0)),labContexts=listOf(snapshot))
        val out=ByteArrayOutputStream();PdfReport.write(context,d,1,null,out,day to day)
        val bytes=out.toByteArray();assertEquals("%PDF-",String(bytes.copyOfRange(0,5)))
        val file=java.io.File(context.cacheDir,"synthetic-summary.pdf")
        try{file.writeBytes(bytes);android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use{fd->android.graphics.pdf.PdfRenderer(fd).use{renderer->assertTrue(renderer.pageCount>2);renderer.openPage(renderer.pageCount-1).use{assertTrue(it.width>0)}}}}finally{file.delete()}
    }
}
