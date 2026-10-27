package net.plainnotes.app.medical
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.*
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.util.Locale
import java.time.Instant

abstract class MedicalRecordsUiContract(private val language:String) {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 private lateinit var strings:Context
 private var original:Configuration?=null
 private val visit=AppointmentEntity(id=701,type="ENDO",at_utc=Instant.parse("2025-01-01T23:30:00Z").toEpochMilli(),at_zone="Pacific/Kiritimati",practitioner="Synthetic doctor",location="Synthetic clinic",note="Synthetic notes",remind_minutes_before=0,completed_utc=1)
 @Suppress("DEPRECATION") private fun content(body: @Composable () -> Unit){val c=Configuration(ui.activity.resources.configuration).apply{setLocales(LocaleList(Locale.forLanguageTag(language)));fontScale=2f;screenWidthDp=320};original=Configuration(ui.activity.resources.configuration);ui.activity.resources.updateConfiguration(c,ui.activity.resources.displayMetrics);strings=ui.activity.createConfigurationContext(c);val density=ui.activity.resources.displayMetrics.widthPixels/320f;ui.setContent{CompositionLocalProvider(LocalContext provides strings,LocalConfiguration provides c,LocalDensity provides Density(density,2f)){NotesTheme(ThemeMode.LIGHT){Surface(Modifier.fillMaxSize().testTag("medical-viewport")){body()}}}};assertEquals(320f,ui.onNodeWithTag("medical-viewport").fetchSemanticsNode().size.width/density,1f)}
 @After @Suppress("DEPRECATION") fun cleanup(){original?.let{ui.activity.resources.updateConfiguration(it,ui.activity.resources.displayMetrics)}}
 @Test fun archiveSeparatesConfirmationAndDoesNotListFutureAppointments(){var selected:AppointmentEntity?=null;content{MedicalRecordsScreen(listOf(visit,visit.copy(id=702,completed_utc=null),visit.copy(id=703,at_utc=Instant.now().plusSeconds(86400).toEpochMilli())),{selected=it},{},{},{},{},PaddingValues())};ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-visit:701"))/* UI-05 F: confirmed visits are rows in their year's inset list, not list keys */;ui.onNodeWithTag("medical-visit:701").assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Button)).assert(hasClickAction()).performClick();ui.runOnIdle{assertEquals(visit,selected)};ui.onNodeWithTag("medical-list").performScrollToKey("visit:702");ui.onNodeWithTag("medical-visit:702").assertIsDisplayed();ui.onNodeWithTag("medical-visit:703").assertDoesNotExist()}
 @Test fun archiveHasDiscoverableCorrectionAndHistoryRoutes(){var bin=0;var labs=0;var history=0;content{MedicalRecordsScreen(emptyList(),{},{},{labs++},{history++},{bin++},PaddingValues())};for(tag in listOf("medical-bin","medical-labs","medical-history")){/* UI-01 G: labs/timeline moved to "Related records", the bin to the page bottom; each is still scrolled to, shown and clicked. */ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag(tag));ui.onNodeWithTag(tag).assertIsDisplayed().performClick()};ui.runOnIdle{assertEquals(1,bin);assertEquals(1,labs);assertEquals(1,history)}}
 @Test fun editorExplainsPastVisitAndOriginalTimezone(){content{MedicalVisitDialog(visit,MedicalVisitSaveState(),{},{})};ui.onNodeWithText(strings.getString(R.string.medical_past_help)).performScrollTo().assertIsDisplayed();ui.onNodeWithTag("medical-zone").performScrollTo().assertTextEquals(strings.getString(R.string.medical_visit_zone,"Pacific/Kiritimati"));ui.onNodeWithTag("medical-save").assertIsDisplayed().assertIsEnabled()}
 @Test fun cancellingEditorWritesNothing(){var writes=0;var cancelled=0;content{MedicalVisitDialog(visit,MedicalVisitSaveState(),{cancelled++},{writes++})};ui.onNodeWithText(strings.getString(R.string.cancel)).assertIsDisplayed().performClick();ui.runOnIdle{assertEquals(0,writes);assertEquals(1,cancelled)}}
 @Test fun editorSubmitsSameRecordOnceWithOriginalInstantAndZone(){var submitted:AppointmentEntity?=null;var writes=0;content{MedicalVisitDialog(visit,MedicalVisitSaveState(),{},{submitted=it;writes++})};ui.onNodeWithTag("medical-save").assertIsEnabled().performClick();ui.onNodeWithTag("medical-save").assertIsNotEnabled();ui.runOnIdle{assertEquals(1,writes);assertEquals(visit.id,submitted!!.id);assertEquals(visit.at_utc,submitted!!.at_utc);assertEquals(visit.at_zone,submitted!!.at_zone)}}
 @Test fun failedSaveKeepsDraftAndVisibleRetry(){content{MedicalVisitDialog(visit,MedicalVisitSaveState(failed=true),{},{})};ui.onNodeWithText(strings.getString(R.string.medical_save_error)).performScrollTo().assertIsDisplayed();ui.onNodeWithText("Synthetic doctor").performScrollTo().assertExists();ui.onNodeWithTag("medical-save").assertIsDisplayed().assertIsEnabled()}

 @Test fun labRangeIsContextOnlyAndOpensTheOriginalLab(){val lab=LabValueEntity(id=901,analyte_code="E2",value=100.0,unit="pg/mL",sampled_utc=visit.at_utc,sampled_zone="UTC");var selected:LabValueEntity?=null;content{MedicalVisitLabs(visit,listOf(lab,lab.copy(id=902,sampled_utc=Instant.parse("2025-02-01T00:00:00Z").toEpochMilli())),{selected=it})};ui.onNodeWithTag("medical-lab-context-help").assertTextEquals(strings.getString(R.string.medical_context_help));ui.onNodeWithTag("medical-lab-range").performScrollToKey("lab:901");ui.onNodeWithTag("medical-lab:901").assertIsDisplayed().performClick();ui.runOnIdle{assertEquals(lab,selected)};ui.onNodeWithTag("medical-lab:902").assertDoesNotExist()}
 @Test fun correctionCancelWritesNothingAndExplicitConfirmWritesOnce(){var shown by mutableStateOf(true);var writes=0;content{if(shown)MedicalCorrectionConfirmation(false,{shown=false},{writes++;shown=false})};ui.onNodeWithText(strings.getString(R.string.medical_delete_help)).assertIsDisplayed();ui.onNodeWithText(strings.getString(R.string.cancel)).assertIsDisplayed().performClick();ui.runOnIdle{assertEquals(0,writes);shown=true};ui.onNodeWithTag("medical-correction-confirm").assertIsDisplayed().performClick();ui.runOnIdle{assertEquals(1,writes)};ui.onNodeWithTag("medical-correction-confirm").assertDoesNotExist()}
 @Test fun existingLabDetailsExposeTheSavedValueAndEditRoute(){var edit=0;val lab=LabValueEntity(id=901,analyte_code="E2",value=100.0,unit="pg/mL",sampled_utc=visit.at_utc,sampled_zone="Pacific/Kiritimati",laboratory="Synthetic laboratory",note="Synthetic lab note");content{LabDetailDialog(lab,emptyList(),{},{edit++})};ui.onNodeWithText("Synthetic lab note").performScrollTo().assertIsDisplayed();ui.onNode(hasTestTag("lab-detail-edit") and (hasText(strings.getString(R.string.edit)) or hasContentDescription(strings.getString(R.string.edit)))).assertIsDisplayed().performClick();ui.runOnIdle{assertEquals(1,edit)}}
 @Test fun visitTimezoneCanBeExplicitlyCorrectedWithoutLosingLocalClockOrId(){var saved:AppointmentEntity?=null;content{MedicalVisitDialog(visit,MedicalVisitSaveState(),{},{saved=it})};ui.onNodeWithTag("medical-change-zone").performScrollTo().performClick();ui.onNodeWithTag("medical-zone-search").performTextInput("UTC");ui.onNodeWithTag("medical-zone-list").performScrollToKey("UTC");ui.onNodeWithTag("medical-zone:UTC").assertIsDisplayed().performClick();ui.onNodeWithTag("medical-zone").performScrollTo().assertTextEquals(strings.getString(R.string.medical_visit_zone,"UTC"));ui.onNodeWithTag("medical-save").assertIsDisplayed().performClick();ui.runOnIdle{assertEquals(visit.id,saved!!.id);assertEquals("UTC",saved!!.at_zone);assertEquals(visit.at_utc+14*3600000L,saved!!.at_utc)}}
}
