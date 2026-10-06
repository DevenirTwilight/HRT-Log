package net.plainnotes.app.disguise

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import net.plainnotes.app.MainActivity
import net.plainnotes.app.R
import net.plainnotes.app.disguise.privatenotes.*
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.UnlockTarget
import org.junit.*
import org.junit.Assert.*

class DisguiseFlowAndroidTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrument get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrument.targetContext
    private val real="2468"
    private val alternate="1357"
    private fun configure(shell:Disguise.Shell,privateCode:String?=alternate) {
        Disguise.enable(context,shell,real,privateCode);Session.reset()
    }
    @Before fun setup(){Disguise.disable(context);AppLock(context).disable();Session.reset()}
    @After fun cleanup(){
        instrument.runOnMainSync{ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach{it.finish()}}
        instrument.waitForIdleSync();Disguise.disable(context);AppLock(context).disable();Session.reset()
        context.getSharedPreferences("shell_notes",Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun launch(type:Class<out Activity>)=ActivityScenario.launch<Activity>(Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    private fun resumed(type:Class<out Activity>):Boolean {
        var found=false
        instrument.runOnMainSync{found=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any{type.isInstance(it)}}
        return found
    }
    private fun await(type:Class<out Activity>){ui.waitUntil(20_000){resumed(type)};ui.waitForIdle()}
    private fun pressCode(code:String){code.forEach{ui.onNodeWithText(it.toString(),useUnmergedTree=true).performClick()};ui.onNodeWithText("=",useUnmergedTree=true).performClick()}
    private fun notesCode(code:String) {
        ui.onNodeWithContentDescription(context.getString(R.string.shell_search)).performClick()
        val search=ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction,ImeAction.Search))
        search.performTextInput(code);search.performImeAction()
    }
    private fun goHome() {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ui.waitUntil(10_000){Session.target==null}
    }
    private fun resumeProtected(type:Class<out Activity>) {
        context.startActivity(Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun assertNoHealthUi() {
        listOf(R.string.app_name,R.string.medications,R.string.labs,R.string.concentration,R.string.stock).forEach{ui.onAllNodesWithText(context.getString(it),substring=true).assertCountEquals(0)}
    }
    @Test fun calculatorPublicUseAndPrivateCodeOpenNotesAndBackReturnsOnlyToCalculator() {
        configure(Disguise.Shell.CALCULATOR)
        val monitor=instrument.addMonitor(MainActivity::class.java.name,null,false)
        try {
            launch(CalculatorActivity::class.java);await(CalculatorActivity::class.java)
            ui.onNodeWithText("2",useUnmergedTree=true).performClick();ui.onNodeWithText("+",useUnmergedTree=true).performClick();ui.onNodeWithText("3",useUnmergedTree=true).performClick();ui.onNodeWithText("=",useUnmergedTree=true).performClick()
            ui.onAllNodesWithText("5").fetchSemanticsNodes().let{assertTrue(it.size>=2)}
            ui.onNodeWithText("C",useUnmergedTree=true).performClick();pressCode(alternate)
            await(PrivateNotesActivity::class.java);assertEquals(UnlockTarget.PRIVATE,Session.target)
            ui.onNodeWithText(context.getString(R.string.private_empty)).assertIsDisplayed();assertNoHealthUi();assertEquals(0,monitor.hits)
            instrument.runOnMainSync{ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach{a->assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0);(a as PrivateNotesActivity).onBackPressedDispatcher.onBackPressed()}}
            await(CalculatorActivity::class.java);assertNull(Session.target);assertNoHealthUi();assertEquals(0,monitor.hits)
            assertTrue(context.getSystemService(ActivityManager::class.java).appTasks.none{it.taskInfo?.baseActivity?.className==MainActivity::class.java.name || it.taskInfo?.topActivity?.className==MainActivity::class.java.name})
        }finally{instrument.removeMonitor(monitor)}
    }
    @Test fun publicNotesPersistAndItsPrivateCodeUsesTheSamePrivateNotesSpace() {
        configure(Disguise.Shell.NOTES)
        val monitor=instrument.addMonitor(MainActivity::class.java.name,null,false)
        try {
            val scenario=launch(NotesShellActivity::class.java);await(NotesShellActivity::class.java)
            ui.onAllNodes(hasSetTextAction())[0].performTextInput("Shopping: milk")
            scenario.recreate();await(NotesShellActivity::class.java);ui.onNodeWithText("Shopping: milk").assertIsDisplayed()
            notesCode(alternate);await(PrivateNotesActivity::class.java);assertNoHealthUi();assertEquals(0,monitor.hits)
            ui.onNodeWithContentDescription(context.getString(R.string.private_close)).performClick();await(NotesShellActivity::class.java)
            ui.onNodeWithText("Shopping: milk").assertIsDisplayed();assertNull(Session.target)
        }finally{instrument.removeMonitor(monitor)}
    }
    @Test fun wrongSecretsRemainOrdinaryShellInput() {
        configure(Disguise.Shell.NOTES);launch(NotesShellActivity::class.java);await(NotesShellActivity::class.java)
        notesCode("9999");ui.waitUntil(10_000){ui.onAllNodesWithText(context.getString(R.string.shell_no_results)).fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithText(context.getString(R.string.shell_no_results)).assertIsDisplayed();assertTrue(resumed(NotesShellActivity::class.java));assertNull(Session.target)
        configure(Disguise.Shell.CALCULATOR);launch(CalculatorActivity::class.java);await(CalculatorActivity::class.java)
        pressCode("9999");ui.onNodeWithText("9999").assertIsDisplayed();assertTrue(resumed(CalculatorActivity::class.java));assertNull(Session.target)
    }
    @Test fun realSecretWithOrdinaryPinConfiguredOpensPrimaryWithoutASecondPinAndBackLocks() {
        AppLock(context).setPin("8642");configure(Disguise.Shell.CALCULATOR)
        launch(CalculatorActivity::class.java);await(CalculatorActivity::class.java);pressCode(real);await(MainActivity::class.java)
        assertEquals(UnlockTarget.PRIMARY,Session.target)
        ui.onAllNodesWithText(context.getString(R.string.lock_title)).assertCountEquals(0)
        ui.onAllNodesWithText(context.getString(R.string.app_name)).fetchSemanticsNodes().let{assertTrue(it.isNotEmpty())}
        instrument.runOnMainSync{ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach{(it as MainActivity).onBackPressedDispatcher.onBackPressed()}}
        await(CalculatorActivity::class.java);assertNull(Session.target)
    }
    @Test fun ordinaryAppLockCannotBeBypassedByATargetToken() {
        AppLock(context).setPin("8642");Session.begin(UnlockTarget.PRIMARY)
        launch(MainActivity::class.java);await(MainActivity::class.java)
        ui.onNodeWithText(context.getString(R.string.lock_title)).assertIsDisplayed()
    }
    @Test fun directMainIntentAndPrivateTokenCannotAuthenticatePrimary() {
        configure(Disguise.Shell.CALCULATOR)
        launch(MainActivity::class.java);await(CalculatorActivity::class.java);assertNoHealthUi()
        Session.begin(UnlockTarget.PRIVATE)
        launch(MainActivity::class.java);await(CalculatorActivity::class.java);assertNull(Session.target);assertNoHealthUi()
    }
    @Test fun privateNotesCrudAndRotationStayInsidePrivateActivity() {
        configure(Disguise.Shell.NOTES);Session.begin(UnlockTarget.PRIVATE)
        val scenario=launch(PrivateNotesActivity::class.java);await(PrivateNotesActivity::class.java)
        ui.onNodeWithContentDescription(context.getString(R.string.private_new)).performClick()
        ui.onNode(hasSetTextAction() and hasText(context.getString(R.string.private_title))).performTextInput("Travel")
        ui.onNode(hasSetTextAction() and hasText(context.getString(R.string.private_body))).performTextInput("Charger, passport")
        scenario.recreate();await(PrivateNotesActivity::class.java);ui.onNodeWithText("Charger, passport").assertIsDisplayed()
        ui.onNodeWithText(context.getString(R.string.private_save)).performClick()
        ui.waitUntil(10_000){ui.onAllNodesWithContentDescription(context.getString(R.string.private_new)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Travel").performClick()
        ui.onNode(hasSetTextAction() and hasText(context.getString(R.string.private_body))).performTextReplacement("Headphones")
        ui.onNodeWithText(context.getString(R.string.private_save)).performClick()
        ui.waitUntil(10_000){ui.onAllNodesWithContentDescription(context.getString(R.string.private_new)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Headphones").assertIsDisplayed()
        assertNoHealthUi()
        ui.onNodeWithContentDescription(context.getString(R.string.private_delete)).performClick()
        ui.onNodeWithText(context.getString(R.string.private_delete_confirm)).assertIsDisplayed()
        ui.onAllNodesWithText(context.getString(R.string.private_delete)).filter(hasClickAction())[0].performClick()
        ui.waitUntil(10_000){ui.onAllNodesWithText(context.getString(R.string.private_empty)).fetchSemanticsNodes().isNotEmpty()}
        assertTrue(PrivateStore(context).list().isEmpty())
    }
    @Test fun backgroundAndRecreatedClosedSessionReturnToShell() {
        configure(Disguise.Shell.NOTES);Session.begin(UnlockTarget.PRIVATE)
        launch(PrivateNotesActivity::class.java);await(PrivateNotesActivity::class.java)
        goHome();resumeProtected(PrivateNotesActivity::class.java);await(NotesShellActivity::class.java);assertNoHealthUi()
        Session.begin(UnlockTarget.PRIMARY)
        val main=launch(MainActivity::class.java);await(MainActivity::class.java)
        Session.reset();main.onActivity{it.recreate()};await(NotesShellActivity::class.java);assertNoHealthUi()
    }
    @Test fun primaryDocumentPickerIsTheOnlyBackgroundException() {
        configure(Disguise.Shell.CALCULATOR);val owner=Session.begin(UnlockTarget.PRIMARY)
        val main=launch(MainActivity::class.java);await(MainActivity::class.java)
        Session.beginPicker();main.moveToState(Lifecycle.State.CREATED)
        assertTrue(Session.allows(UnlockTarget.PRIMARY,owner))
        main.moveToState(Lifecycle.State.RESUMED);await(MainActivity::class.java);assertFalse(Session.pickerActive(owner))
        goHome();resumeProtected(MainActivity::class.java);await(CalculatorActivity::class.java)
    }
    @Test fun setupCompletionCannotReopenAnActivityThatLeftOrReplaceANewerPrivateSession() {
        val owner=Session.generation
        val main=launch(MainActivity::class.java);await(MainActivity::class.java)
        var activity:Activity?=null
        main.onActivity{activity=it}
        Disguise.enable(context,Disguise.Shell.CALCULATOR,real,alternate)
        assertNull(Session.target)
        main.onActivity{Disguise.completeSetup(it,owner)}
        assertTrue(Session.allows(UnlockTarget.PRIMARY,owner))
        goHome()
        instrument.runOnMainSync{Disguise.completeSetup(activity!!,owner)}
        assertNull(Session.target)
        val newer=Session.begin(UnlockTarget.PRIVATE)
        instrument.runOnMainSync{Disguise.completeSetup(activity!!,owner)}
        assertTrue(Session.allows(UnlockTarget.PRIVATE,newer))
    }
    @Test fun alternateCodeChangesAndRemovalPreserveMainCodeAndEraseOnlyPrivateContent() {
        configure(Disguise.Shell.CALCULATOR)
        assertEquals(UnlockTarget.PRIMARY,Disguise.check(context,real));assertEquals(UnlockTarget.PRIVATE,Disguise.check(context,alternate))
        assertTrue(runCatching{Disguise.setPrivateCode(context,real)}.isFailure)
        val note=PrivateStore(context).save(null,"Travel","Passport")
        Disguise.setPrivateCode(context,"9753")
        assertNull(Disguise.check(context,alternate));assertEquals(UnlockTarget.PRIVATE,Disguise.check(context,"9753"))
        assertEquals(listOf(note),PrivateStore(context).list())
        Disguise.removePrivateCode(context);assertFalse(Disguise.hasPrivateCode(context));assertTrue(PrivateStore(context).list().isEmpty())
        assertNull(Disguise.check(context,"9753"));assertEquals(UnlockTarget.PRIMARY,Disguise.check(context,real))
    }
    @Test fun unsetAlternateSameCodesAndSharedRateLimitAreHandledWithoutAnAuthTarget() {
        assertTrue(runCatching{Disguise.enable(context,Disguise.Shell.NOTES,real,real)}.isFailure)
        configure(Disguise.Shell.NOTES,null)
        assertNull(Disguise.check(context,alternate));assertEquals(UnlockTarget.PRIMARY,Disguise.check(context,real))
        repeat(10){assertNull(Disguise.check(context,"9999",1000))}
        Disguise.setPrivateCode(context,alternate)
        assertNull(Disguise.check(context,real,1001));assertNull(Disguise.check(context,alternate,1001))
        assertEquals(UnlockTarget.PRIVATE,Disguise.check(context,alternate,61_001))
    }
    @Test fun disablingDisguiseRemovesPrivateAccessAndRestoresOnlyNormalLauncher() {
        configure(Disguise.Shell.CALCULATOR);PrivateStore(context).save(null,"Shopping","Milk")
        Session.begin(UnlockTarget.PRIVATE);Disguise.disable(context)
        assertNull(Session.target);assertFalse(Disguise.enabled(context));assertFalse(Disguise.hasPrivateCode(context));assertTrue(PrivateStore(context).list().isEmpty())
        Disguise.Shell.entries.forEach{assertEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,context.packageManager.getComponentEnabledSetting(ComponentName(context,it.alias)))}
        launch(PrivateNotesActivity::class.java);instrument.waitForIdleSync();assertFalse(resumed(PrivateNotesActivity::class.java));assertFalse(resumed(MainActivity::class.java))
    }
    @Test fun applicationAliasAndProtectedActivityIdentityAreNeutralAndPrivateIsNotExported() {
        configure(Disguise.Shell.CALCULATOR)
        assertEquals(context.getString(R.string.system_app_name),context.applicationInfo.loadLabel(context.packageManager).toString())
        val normal=context.packageManager.getActivityInfo(ComponentName(context,"net.plainnotes.app.Launcher"),android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
        assertEquals(context.getString(R.string.app_name),normal.loadLabel(context.packageManager).toString())
        val info=context.packageManager.getActivityInfo(ComponentName(context,PrivateNotesActivity::class.java),0)
        assertFalse(info.exported);assertEquals(context.getString(R.string.private_notes),info.loadLabel(context.packageManager).toString())
    }
}
