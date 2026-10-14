package net.plainnotes.app.history
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import net.plainnotes.app.R
import net.plainnotes.app.NotesState
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.Locale

/** Same 320dp/2x real Compose interaction and accessibility contract on native JVM and API35. */
abstract class UndoSkippedUiContract(private val language:String) {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private var original:Configuration?=null
    private lateinit var strings:Context
    protected val med=MedicationEntity(1,"R15 Synthetic","E2","ORAL","MG",2.0,20.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    protected fun record(id:Long=701,status:String="SKIPPED",origin:String="APP")=RecordEntity(id=id,medication_id=1,rule_version_id=1,slot_key="wall:1@${LocalDate.now()}T08:00:00",
        scheduled_utc=LocalDate.now().atTime(8,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),scheduled_zone=ZoneId.systemDefault().id,planned_dose=2.0,late_after_minutes_snapshot=60,
        status=status,origin=origin,revision=1,config_snapshot=MedicationSnapshot.encode(med,null))
    @Suppress("DEPRECATION") protected fun content(body:@Composable ()->Unit) {
        val config=Configuration(ui.activity.resources.configuration).apply{setLocales(LocaleList(Locale.forLanguageTag(language)));fontScale=2f;screenWidthDp=320}
        original=Configuration(ui.activity.resources.configuration);ui.activity.resources.updateConfiguration(config,ui.activity.resources.displayMetrics)
        val ctx=ui.activity.createConfigurationContext(config);strings=ctx;val density=ui.activity.resources.displayMetrics.widthPixels/320f
        ui.setContent{CompositionLocalProvider(LocalContext provides ctx,LocalConfiguration provides config,LocalDensity provides Density(density,2f)){
            NotesTheme(ThemeMode.LIGHT){Surface(Modifier.fillMaxSize().testTag("r15-viewport")){body()}}
        }}
        assertEquals(320f,ui.onNodeWithTag("r15-viewport").fetchSemanticsNode().size.width/density,1f)
    }
    @After @Suppress("DEPRECATION") fun restoreConfiguration(){original?.let{ui.activity.resources.updateConfiguration(it,ui.activity.resources.displayMetrics)}}
    private fun row(id:Long=701)=ui.onNodeWithTag("history-record:$id")
    private fun open(id:Long=701) {
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        row(id).performScrollTo().assertIsEnabled().performClick()
    }
    private fun confirmation(id:Long=701) { open(id);ui.onNodeWithTag("history-undo-skip:$id").assertIsDisplayed().performClick() }
    private fun history(records:List<RecordEntity>,onUndo:(RecordEntity)->Unit={}) {
        content{HistoryScreen(NotesState(medications=listOf(med),loading=false),records,{},{},PaddingValues(),onUndoSkip=onUndo)}
    }
    @Test fun skippedHistoryRowIsAnEnabledCorrectionEntry() {
        val r=record();content{HistoryScreen(NotesState(medications=listOf(med),loading=false),listOf(r),{},{},PaddingValues())}
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        row().performScrollTo().assertHasClickAction().assertIsEnabled().performClick()
        ui.onNodeWithTag("history-undo-skip:701").assertTextEquals(strings.getString(R.string.history_undo_skip))
        ui.onNodeWithText(strings.getString(R.string.edit)).assertDoesNotExist()
        ui.onNodeWithText(strings.getString(R.string.remove)).assertDoesNotExist()
    }
    @Test fun cancellationDoesNotSubmitAnyCorrection() {
        var calls=0;history(listOf(record())){calls++};confirmation()
        ui.onNodeWithTag("undo-skip-confirm").assertIsDisplayed().assertIsEnabled()
        ui.onNodeWithTag("undo-skip-cancel").assertIsDisplayed().performClick()
        ui.onNodeWithTag("undo-skip-dialog").assertDoesNotExist();assertEquals(0,calls);row().assertIsEnabled()
    }
    @Test fun confirmationSubmitsTheSelectedVersionOnceAndRefreshesHistory() {
        val r=record();val rows=mutableStateOf(listOf(r));val submitted=mutableListOf<RecordEntity>()
        content{HistoryScreen(NotesState(medications=listOf(med),loading=false),rows.value,{},{},PaddingValues(),onUndoSkip={
            submitted.add(it);rows.value=listOf(it.copy(id=702,status="MISSED",origin="AUTO_MISSED",revision=1))
        })}
        confirmation();assertTrue(submitted.isEmpty());ui.onNodeWithTag("undo-skip-confirm").performClick();ui.waitForIdle()
        assertEquals(listOf(r),submitted);ui.onNodeWithTag("undo-skip-dialog").assertDoesNotExist();row(701).assertDoesNotExist()
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(3);row(702).performScrollTo().performClick()
        ui.onNodeWithTag("history-undo-skip:702").assertDoesNotExist()
        ui.onNodeWithText(strings.getString(R.string.history_backfill)).assertExists()
        ui.onNodeWithText(strings.getString(R.string.confirm_missed)).assertExists()
    }
    @Test fun staleOrRemovedRecordsDismissConfirmationWithoutSubmission() {
        val r=record();val rows=mutableStateOf(listOf(r));var calls=0
        content{HistoryScreen(NotesState(medications=listOf(med),loading=false),rows.value,{},{},PaddingValues(),onUndoSkip={calls++})}
        confirmation();ui.runOnIdle{rows.value=listOf(r.copy(revision=2,planned_dose=3.0))};ui.waitForIdle()
        ui.onNodeWithTag("undo-skip-confirm").assertDoesNotExist();assertEquals(0,calls)
        confirmation();ui.runOnIdle{rows.value=emptyList()};ui.waitForIdle()
        ui.onNodeWithTag("undo-skip-confirm").assertDoesNotExist();row().assertDoesNotExist();assertEquals(0,calls)
    }
    @Test fun takenAndBothMissedKindsKeepTheirOriginalActionsWithoutUndo() {
        val rows=mutableStateOf(listOf(record(status="ON_TIME").copy(actual_dose=2.0,taken_utc=record().scheduled_utc,taken_zone=ZoneId.systemDefault().id)))
        var edits=0;var confirms=0
        content{HistoryScreen(NotesState(medications=listOf(med),loading=false),rows.value,{edits++},{},PaddingValues(),onConfirmMissed={confirms++})}
        open();ui.onNodeWithTag("history-undo-skip:701").assertDoesNotExist()
        ui.onNodeWithText(strings.getString(R.string.remove)).assertExists();ui.onNodeWithText(strings.getString(R.string.edit)).performClick()
        for(origin in listOf("APP","AUTO_MISSED")) {
            ui.runOnIdle{rows.value=listOf(record(status="MISSED",origin=origin))};ui.waitForIdle();open()
            ui.onNodeWithTag("history-undo-skip:701").assertDoesNotExist()
            ui.onNodeWithText(strings.getString(R.string.remove)).assertDoesNotExist()
            ui.onNodeWithText(strings.getString(R.string.history_backfill)).assertExists()
            if(origin=="AUTO_MISSED")ui.onNodeWithText(strings.getString(R.string.confirm_missed)).performClick()
            else {ui.onNodeWithText(strings.getString(R.string.confirm_missed)).assertDoesNotExist();ui.onNodeWithText(strings.getString(R.string.history_backfill)).performClick()}
        }
        assertEquals(2,edits);assertEquals(1,confirms)
    }
    @Test fun talkBackActionAndScrollableConfirmationStayAccessibleAtLargeFont() {
        history(listOf(record().copy(planned_dose=3.0)))
        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        val semantics=row().fetchSemanticsNode().config
        assertEquals(Role.Button,semantics[SemanticsProperties.Role])
        assertEquals(strings.getString(R.string.history_undo_skip),semantics[SemanticsActions.OnClick].label)
        confirmation()
        ui.onNodeWithText(strings.getString(R.string.history_undo_skip_help)).assertExists()
        ui.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("undo-skip-dialog"))).assertExists()
        ui.onNodeWithTag("undo-skip-confirm").assertIsDisplayed().assertHasClickAction()
        ui.onNodeWithTag("undo-skip-cancel").assertIsDisplayed().assertHasClickAction()
    }

}
