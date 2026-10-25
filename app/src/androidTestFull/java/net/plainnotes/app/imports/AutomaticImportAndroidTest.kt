package net.plainnotes.app.imports

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.MainActivity
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.ui.Destination
import net.plainnotes.app.ui.UiPrefs
import org.junit.*
import org.junit.Assert.*

/** MainActivity -> real Settings navigation -> real DocumentsUI -> existing previews/transactions.
 * Only synthetic fixtures in the MANAGE_DOCUMENTS-protected test APK are offered. */
class AutomaticImportAndroidTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private var scenario:ActivityScenario<MainActivity>?=null
    private lateinit var model:NotesViewModel
    private fun text(id:Int)=context.getString(id)
    @Before fun setup() {net.plainnotes.app.attachmentpoc.SyntheticEmulatorGuard.requireEmulator();Disguise.disable(context);AppLock(context).disable();Session.reset();UiPrefs(context).disclaimerAccepted=true}
    @After fun close(){scenario?.close();Session.reset()}
    private fun launch() {
        scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
        scenario!!.onActivity{model=ViewModelProvider(it)[NotesViewModel::class.java]}
        ui.waitUntil(30000){model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready}
    }
    private fun open(destination:Destination) {
        ui.onNodeWithContentDescription(text(R.string.menu)).performClick()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(text(destination.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(text(destination.title))).performClick()
    }
    private fun data() {open(Destination.SETTINGS);ui.onNodeWithText(text(R.string.settings_data_backup)).performScrollTo().performClick()}
    private fun click(id:Int,scroll:Boolean=true){val node=ui.onNodeWithText(text(id));if(scroll)node.performScrollTo();node.performClick()}
    private fun waitSystem(label:String,predicate:()->Boolean) {
        val end=SystemClock.uptimeMillis()+30000
        while(SystemClock.uptimeMillis()<end){if(predicate())return;Thread.sleep(100)}
        val root=instrumentation.uiAutomation.rootInActiveWindow
        val tree=StringBuilder()
        fun snapshot(node:AccessibilityNodeInfo?,depth:Int) {
            if(node==null || depth>16 || tree.length>6000)return
            tree.append("\n").append(" ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                .append(" clickable=").append(node.isClickable).append(" visible=").append(node.isVisibleToUser)
            for(i in 0 until node.childCount)snapshot(node.getChild(i),depth+1)
        }
        snapshot(root,0)
        fail("System picker did not reach $label; window=${root?.packageName}; $tree")
    }
    private fun touch(node:AccessibilityNodeInfo) {
        val b=Rect();node.getBoundsInScreen(b);assertFalse(b.isEmpty)
        val now=SystemClock.uptimeMillis()
        val p=MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_FINGER}
        val c=MotionEvent.PointerCoords().apply{x=b.exactCenterX();y=b.exactCenterY();pressure=1f;size=1f}
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,1,arrayOf(p),arrayOf(c),0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
            try{assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
        }
    }
    private fun systemNode(label:String):AccessibilityNodeInfo?=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.firstOrNull{it.isVisibleToUser&&it.text?.toString()==label}
    private fun choose(kind:String) {
        click(R.string.import_other_apps)
        waitSystem("DocumentsUI"){instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.endsWith(".documentsui")==true}
        if(systemNode("SYNTHETIC tracker.db")==null) {
            // Navigate the real picker drawer without depending on translated content descriptions.
            var button:AccessibilityNodeInfo?=null
            waitSystem("navigation button") {
                fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
                    if(n==null)return null
                    if(n.className=="android.widget.ImageButton"&&n.isClickable&&n.isVisibleToUser)return n
                    for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null
                }
                button=find(instrumentation.uiAutomation.rootInActiveWindow);button!=null
            }
            assertTrue(requireNotNull(button).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            var rootAction:AccessibilityNodeInfo?=null
            waitSystem("settled synthetic root") {
                try {instrumentation.uiAutomation.waitForIdle(300,3000)}catch(_:java.util.concurrent.TimeoutException){return@waitSystem false}
                val current=instrumentation.uiAutomation.rootInActiveWindow ?: return@waitSystem false
                if(!current.refresh())return@waitSystem false
                var node=systemNode("SYNTHETIC IMPORT")
                while(node!=null && !node.isClickable)node=node.parent
                rootAction=node?.takeIf{it.isVisibleToUser};rootAction!=null
            }
            // Root drawer navigation has a real accessible click action. A finger at a
            // label's pre-layout coordinates can miss while that drawer is being attached.
            assertTrue(requireNotNull(rootAction).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        // Some DocumentsUI versions keep the roots drawer over the loaded directory
        // after an accessibility root action. Do not tap a filename through that overlay.
        if(systemNode("SYNTHETIC MED-02")!=null) {
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            waitSystem("closed roots drawer"){systemNode("SYNTHETIC MED-02")==null}
        }
        val name=if(kind=="tracker")"SYNTHETIC tracker.db" else "SYNTHETIC memo.json"
        waitSystem(name){systemNode(name)!=null}
        try{instrumentation.uiAutomation.waitForIdle(300,3000)}catch(_:java.util.concurrent.TimeoutException){}
        touch(requireNotNull(systemNode(name)))
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.HtReady || model.dataJob.value is NotesViewModel.DataJob.ImportReady || model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertFalse("Detection or parsing failed",model.dataJob.value is NotesViewModel.DataJob.Failed)
    }
    @Test fun realEntryAutomaticallyDetectsBothSourcesCommitsAndReopensHistory() {
        launch();data()
        ui.onNodeWithText(text(R.string.import_other_apps)).assertHasClickAction()
        ui.onNodeWithText(text(R.string.import_ht)).assertDoesNotExist();ui.onNodeWithText(text(R.string.import_tm)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.backup_restore)).assertExists()
        choose("tracker")
        ui.onNodeWithText(context.getString(R.string.import_identified,"HRT Tracker")).assertExists()
        click(R.string.ht_dup_merge);click(R.string.import_action,false)
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.HtImported || model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.HtImported)
        assertEquals(7,(model.dataJob.value as NotesViewModel.DataJob.HtImported).summary.intakes)
        click(R.string.ok,false)
        choose("memo")
        ui.onNodeWithText(context.getString(R.string.import_identified,"Trans Memo")).assertExists()
        // Original per-product late delay, late-state handling, wellbeing scale and mode choices.
        val fields=ui.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        repeat(fields){i->ui.onAllNodes(hasSetTextAction())[i].performScrollTo().performTextInput("60")}
        click(R.string.import_merge);click(R.string.import_late_missed);click(R.string.import_scale_1_5);click(R.string.import_action,false)
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.Imported || model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.Imported)
        assertEquals(9,(model.dataJob.value as NotesViewModel.DataJob.Imported).summary.intakes)
        click(R.string.ok,false)
        val imported=model.extra.value.records.filter{it.origin in setOf("IMPORT_HT","IMPORT_TM")}.sortedBy{it.id}
        assertEquals(16,imported.size)
        scenario!!.close();launch();open(Destination.HISTORY)
        ui.onNodeWithTag("top-title").assertTextEquals(text(R.string.history))
        ui.waitUntil(30000){model.state.value.medications.any{it.name=="Synthetic gel"} && model.extra.value.records.isNotEmpty()}
        assertEquals(imported,model.extra.value.records.filter{it.origin in setOf("IMPORT_HT","IMPORT_TM")}.sortedBy{it.id})
        data();click(R.string.import_other_apps)
        waitSystem("cancel DocumentsUI"){instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.endsWith(".documentsui")==true}
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        ui.waitUntil(10000){model.dataJob.value is NotesViewModel.DataJob.ImportCancelled}
        ui.onNodeWithText(text(R.string.import_cancelled)).assertExists()
    }
}
