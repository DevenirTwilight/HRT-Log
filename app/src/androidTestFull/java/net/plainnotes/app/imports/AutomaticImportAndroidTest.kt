package net.plainnotes.app.imports

import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
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
    private var originalAccessibilityFlags=0
    private fun text(id:Int)=context.getString(id)
    @Before fun setup() {
        net.plainnotes.app.attachmentpoc.SyntheticEmulatorGuard.requireEmulator()
        val service=instrumentation.uiAutomation.serviceInfo
        originalAccessibilityFlags=service.flags
        service.flags=service.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        instrumentation.uiAutomation.serviceInfo=service
        Disguise.disable(context);AppLock(context).disable();Session.reset();UiPrefs(context).disclaimerAccepted=true
    }
    @After fun close(){scenario?.close();Session.reset()
        val service=instrumentation.uiAutomation.serviceInfo;service.flags=originalAccessibilityFlags;instrumentation.uiAutomation.serviceInfo=service
    }
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
                .append(" id=").append(node.viewIdResourceName).append(" bounds=").append(Rect().also(node::getBoundsInScreen))
                .append(" actions=").append(node.actionList.map{it.id})
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
    private fun pickerRoot():AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow?.takeIf {
        it.packageName?.toString()?.endsWith(".documentsui")==true && it.refresh()
    }
    private fun shown(node:AccessibilityNodeInfo):Boolean {
        if(!node.refresh() || !node.isVisibleToUser)return false
        val screen=Rect();pickerRoot()?.getBoundsInScreen(screen) ?: return false
        val bounds=Rect();node.getBoundsInScreen(bounds)
        return !bounds.isEmpty && Rect.intersects(screen,bounds)
    }
    private fun systemNode(label:String):AccessibilityNodeInfo?=pickerRoot()?.findAccessibilityNodeInfosByText(label)
        ?.firstOrNull{it.text?.toString()==label && shown(it)}
    private fun systemId(id:String):AccessibilityNodeInfo? {
        val root=pickerRoot() ?: return null
        return root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/$id").firstOrNull(::shown)
    }
    private fun rootsOpen()=systemId("drawer_roots")!=null
    private fun settlePicker() {instrumentation.uiAutomation.waitForIdle(300,3000)}
    private fun closeRoots() {
        settlePicker()
        if(rootsOpen()) {
            // The main navigation button OPENS roots; it is not a toggle. Back is sent
            // only for a fresh, on-screen drawer_roots pane, never for a retained label.
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            waitSystem("closed roots pane, picker still active"){pickerRoot()!=null && !rootsOpen()}
        }
    }
    private fun choose(kind:String) {
        val name=if(kind=="tracker")"SYNTHETIC tracker.db" else "SYNTHETIC memo.json"
        click(R.string.import_other_apps)
        waitSystem("DocumentsUI"){pickerRoot()!=null}
        settlePicker()
        if(systemNode(name)==null) {
            if(!rootsOpen()) {
                var navigation:AccessibilityNodeInfo?=null
                waitSystem("main toolbar navigation") {
                    fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
                        if(n==null)return null
                        if(n.className=="android.widget.ImageButton" && n.isClickable && shown(n))return n
                        for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it}
                        return null
                    }
                    navigation=find(systemId("toolbar"));navigation!=null
                }
                assertTrue(requireNotNull(navigation).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            var rootAction:AccessibilityNodeInfo?=null
            waitSystem("settled synthetic root") {
                settlePicker()
                var node=systemNode("SYNTHETIC IMPORT")
                while(node!=null && !node.isClickable)node=node.parent
                rootAction=node?.takeIf(::shown);rootAction!=null
            }
            assertTrue(requireNotNull(rootAction).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        closeRoots()
        waitSystem(name){!rootsOpen() && systemNode(name)!=null}
        settlePicker()
        // Reacquire after navigation/layout; never touch coordinates from the covered list.
        assertFalse("Drawer must not cover the document",rootsOpen())
        val file=requireNotNull(systemNode(name))
        var parent:AccessibilityNodeInfo?=file;var inDirectory=false
        while(parent!=null) {
            check(parent.viewIdResourceName?.endsWith(":id/drawer_roots")!=true)
            if(parent.viewIdResourceName?.endsWith(":id/dir_list")==true){inDirectory=true;break}
            parent=parent.parent
        }
        assertTrue("Synthetic file must belong to the real directory list",inDirectory)
        touch(file)
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
        ui.onNodeWithTag("top-title").assertTextEquals(text(Destination.HISTORY.title))
        ui.waitUntil(30000){model.state.value.medications.any{it.name=="Synthetic gel"} && model.extra.value.records.isNotEmpty()}
        assertEquals(imported,model.extra.value.records.filter{it.origin in setOf("IMPORT_HT","IMPORT_TM")}.sortedBy{it.id})
        data();click(R.string.import_other_apps)
        waitSystem("cancel DocumentsUI"){instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.endsWith(".documentsui")==true}
        closeRoots()
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        ui.waitUntil(10000){model.dataJob.value is NotesViewModel.DataJob.ImportCancelled}
        ui.onNodeWithText(text(R.string.import_cancelled)).assertExists()
    }
}
