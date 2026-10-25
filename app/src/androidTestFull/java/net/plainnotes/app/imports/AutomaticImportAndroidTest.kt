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
import androidx.lifecycle.Lifecycle
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
    private fun selectImportRoot() {
        var rootAction:AccessibilityNodeInfo?=null
        waitSystem("settled synthetic drawer root") {
            settlePicker()
            // A toolbar folder title is not a root action. Scope to the actual pane.
            var node=systemId("drawer_roots")?.findAccessibilityNodeInfosByText("SYNTHETIC IMPORT")
                ?.firstOrNull{it.text?.toString()=="SYNTHETIC IMPORT" && shown(it)}
            while(node!=null && !node.isClickable)node=node.parent
            rootAction=node?.takeIf(::shown);rootAction!=null
        }
        assertTrue(requireNotNull(rootAction).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        // Root selection closes the drawer itself. A Back injected while that close
        // is being dispatched can cancel the entire picker after the drawer closes.
        waitSystem("root selection completed without cancelling picker") {pickerRoot()!=null && !rootsOpen()}
    }
    private fun choose(kind:String,expectFailure:Boolean=false) {
        val name=when(kind){"tracker"->"SYNTHETIC tracker.db";"mona"->"SYNTHETIC mona.db";"featherline"->"SYNTHETIC featherline.json";"invalid"->"SYNTHETIC invalid.json";else->"SYNTHETIC memo.json"}
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
            selectImportRoot()
        }
        // The fifth synthetic file can be below the viewport in DocumentsUI grid mode.
        // Navigate the real RecyclerView using advertised accessibility actions, not a
        // larger display, fixed coordinates, direct URI, or a changed source fixture.
        waitSystem("loaded synthetic directory"){!rootsOpen() && (systemId("dir_list")?.childCount ?: 0)>0}
        var backwards=true;var scrolls=0
        waitSystem(name){
            if(rootsOpen())false
            else if(systemNode(name)!=null)true
            else {
                val list=systemId("dir_list")
                if(list!=null && scrolls<24) {
                    var moved=list.performAction(if(backwards)AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    if(!moved && backwards){backwards=false;moved=list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)}
                    if(moved){scrolls++;settlePicker()}
                }
                false
            }
        }
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
        // ActivityResult can update the model while DocumentsUI is still finishing.
        // Compose assertions require the host's lifecycle-controlled frame clock to
        // be resumed as well as the data to be ready; neither implies the other.
        waitSystem("resumed application after document result") {
            scenario?.state==Lifecycle.State.RESUMED && pickerRoot()==null
        }
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.FeatherlinePassword || model.dataJob.value is NotesViewModel.DataJob.MonaReady || model.dataJob.value is NotesViewModel.DataJob.HtReady || model.dataJob.value is NotesViewModel.DataJob.ImportReady || model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertEquals("Detection result",expectFailure,model.dataJob.value is NotesViewModel.DataJob.Failed)
    }
    @Test fun realEntryAutomaticallyDetectsBothSourcesCommitsAndReopensHistory() {
        try { exerciseImport() } catch(failure:Throwable) {
            // Activity destruction may itself fail after a failed composition. Keep
            // the original failure/line before @After can mask it. Synthetic only.
            android.util.Log.e("SyntheticImport", "Primary unified import test failure", failure)
            throw failure
        }
    }
    private fun stage(value:String) {android.util.Log.i("SyntheticImport",value)}
    private fun exerciseImport() {
        stage("launch/settings");launch();data()
        ui.onNodeWithText(text(R.string.import_other_apps)).assertHasClickAction()
        ui.onNodeWithText(text(R.string.import_ht)).assertDoesNotExist();ui.onNodeWithText(text(R.string.import_tm)).assertDoesNotExist()
        ui.onNodeWithText(text(R.string.backup_restore)).assertExists()
        stage("select tracker");choose("tracker");stage("tracker preview")
        ui.onNodeWithText(context.getString(R.string.import_identified,"HRT Tracker")).assertExists()
        click(R.string.ht_dup_merge);stage("tracker choices");click(R.string.import_action,false)
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.HtImported || model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertTrue(model.dataJob.value is NotesViewModel.DataJob.HtImported)
        assertEquals(7,(model.dataJob.value as NotesViewModel.DataJob.HtImported).summary.intakes)
        click(R.string.ok,false)
        stage("tracker committed; select memo");choose("memo");stage("memo preview")
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
        stage("both committed; reopen");ui.waitForIdle();scenario!!.close();launch();open(Destination.HISTORY)
        ui.onNodeWithTag("top-title").assertTextEquals(text(Destination.HISTORY.title))
        ui.waitUntil(30000){model.state.value.medications.any{it.name=="Synthetic gel"} && model.extra.value.records.isNotEmpty()}
        assertEquals(imported,model.extra.value.records.filter{it.origin in setOf("IMPORT_HT","IMPORT_TM")}.sortedBy{it.id})
        stage("reopened legacy history verified; Mona cancel preview");data();choose("mona")
        val preview=model.dataJob.value as NotesViewModel.DataJob.MonaReady
        assertEquals(2,preview.preview.addedDoses);assertEquals(2,preview.preview.addedLabs)
        ui.onNodeWithText(text(R.string.mona_preview)).assertExists()
        click(R.string.cancel,false)
        assertEquals(16,model.extra.value.records.size)
        stage("Mona real confirmation");choose("mona");click(R.string.mona_confirm,false)
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.MonaImported || model.dataJob.value is NotesViewModel.DataJob.Failed}
        val saved=model.dataJob.value as NotesViewModel.DataJob.MonaImported
        assertEquals(2,saved.summary.addedDoses);assertEquals(2,saved.summary.addedLabs)
        click(R.string.ok,false)
        val mona=model.extra.value.records.filter{it.origin=="IMPORT_MONA"}.sortedBy{it.id}
        assertEquals(2,mona.size)
        assertTrue(mona.all{it.actual_dose==1.25 && it.taken_zone=="Europe/Paris" && it.scheduled_utc==null})
        scenario!!.close();launch();open(Destination.HISTORY)
        ui.waitUntil(30000){model.extra.value.records.any{it.origin=="IMPORT_MONA"}}
        assertEquals(mona,model.extra.value.records.filter{it.origin=="IMPORT_MONA"}.sortedBy{it.id})
        data();choose("mona")
        val repeated=model.dataJob.value as NotesViewModel.DataJob.MonaReady
        assertEquals(4,repeated.preview.existing);assertEquals(0,repeated.preview.addedDoses+repeated.preview.addedLabs)
        click(R.string.mona_confirm,false)
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.MonaImported || model.dataJob.value is NotesViewModel.DataJob.Failed}
        assertEquals(0,(model.dataJob.value as NotesViewModel.DataJob.MonaImported).summary.addedDoses)
        assertEquals(18,model.extra.value.records.size);click(R.string.ok,false)
        val persistedLabs=model.extra.value.labs.filter{it.sampled_utc==java.time.Instant.parse("2026-10-25T08:30:00Z").toEpochMilli()}
        assertEquals(setOf("pg/mL","ng/dL"),persistedLabs.map{it.unit}.toSet())
        assertTrue(persistedLabs.all{it.panel_id!=null && it.sampled_zone=="Europe/Paris"})
        choose("invalid",true);assertEquals(18,model.extra.value.records.size);click(R.string.ok,false)
        stage("reopened Mona and idempotency verified; cancel picker");click(R.string.import_other_apps)
        waitSystem("cancel DocumentsUI"){instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.endsWith(".documentsui")==true}
        settlePicker()
        if(rootsOpen())selectImportRoot()
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        ui.waitUntil(10000){model.dataJob.value is NotesViewModel.DataJob.ImportCancelled}
        ui.onNodeWithText(text(R.string.import_cancelled)).assertExists()
        exerciseFeatherline()
    }
    private fun exerciseFeatherline() {
        val expected=InstrumentationRegistry.getArguments().getString("featherlineExpectedHeapMb")
        if(expected!=null)assertEquals(expected.toLong()*1024*1024,Runtime.getRuntime().maxMemory())
        val memory=net.plainnotes.app.attachmentpoc.SampledMemory()
        try {
        scenario?.close();launch();data();choose("featherline")
        ui.onNodeWithText(text(R.string.featherline_password_title)).assertExists()
        val before=model.extra.value.records.filter{it.origin=="IMPORT_FEATHERLINE"}.size
        // Cancel the protected encrypted snapshot before any authentication or write.
        click(R.string.cancel,false);assertEquals(before,model.extra.value.records.count{it.origin=="IMPORT_FEATHERLINE"})
        choose("featherline")
        ui.onNode(hasSetTextAction()).performTextInput("wrong-password")
        click(R.string.featherline_unlock,false)
        ui.waitUntil(30000){(model.dataJob.value as? NotesViewModel.DataJob.FeatherlinePassword)?.error!=null}
        ui.onNodeWithText(text(R.string.featherline_auth_failed)).assertExists()
        assertEquals(before,model.extra.value.records.count{it.origin=="IMPORT_FEATHERLINE"})
        // Retry inside the same selected snapshot, without launching DocumentsUI again.
        ui.onNode(hasSetTextAction()).performTextReplacement("synthetic-only-password")
        click(R.string.featherline_unlock,false)
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.FeatherlineReady || model.dataJob.value is NotesViewModel.DataJob.Failed}
        val ready=model.dataJob.value as NotesViewModel.DataJob.FeatherlineReady
        val doses=ready.preview.newDoses;val labs=ready.preview.newLabs
        assertEquals(1,doses);assertEquals(2,labs)
        ui.onNodeWithText(text(R.string.featherline_preview)).assertExists()
        assertEquals(before,model.extra.value.records.count{it.origin=="IMPORT_FEATHERLINE"})
        click(R.string.featherline_confirm,false)
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.FeatherlineImported || model.dataJob.value is NotesViewModel.DataJob.Failed}
        val result=model.dataJob.value as NotesViewModel.DataJob.FeatherlineImported
        assertEquals(1,result.summary.doses);assertEquals(2,result.summary.labs)
        click(R.string.ok,false)
        val records=model.extra.value.records.filter{it.origin=="IMPORT_FEATHERLINE"}.sortedBy{it.id}
        assertEquals(before+1,records.size);assertEquals(1.0,records.last().actual_dose!!,0.0)
        assertEquals("Europe/Paris",records.last().taken_zone)
        scenario!!.close();launch();open(Destination.HISTORY)
        ui.waitUntil(30000){model.extra.value.records.any{it.origin=="IMPORT_FEATHERLINE"}}
        assertEquals(records,model.extra.value.records.filter{it.origin=="IMPORT_FEATHERLINE"}.sortedBy{it.id})
        data();choose("featherline")
        ui.onNode(hasSetTextAction()).performTextInput("synthetic-only-password");click(R.string.featherline_unlock,false)
        ui.waitUntil(30000){model.dataJob.value is NotesViewModel.DataJob.FeatherlineReady || model.dataJob.value is NotesViewModel.DataJob.Failed}
        val repeated=model.dataJob.value as NotesViewModel.DataJob.FeatherlineReady
        assertEquals(0,repeated.preview.newDoses+repeated.preview.newLabs);assertEquals(3,repeated.preview.existing)
        click(R.string.cancel,false)
        assertEquals(records,model.extra.value.records.filter{it.origin=="IMPORT_FEATHERLINE"}.sortedBy{it.id})

        } finally {
            memory.close()
            val measurements=org.json.JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("sampledNotPeak",true).put("sampleIntervalMs",20)
                .put("maxHeapBytes",Runtime.getRuntime().maxMemory()).put("javaObservedBytes",memory.javaBytes.get()).put("nativeObservedBytes",memory.nativeBytes.get()).put("pssObservedKib",memory.pssKib.get()).put("samples",memory.samples.get())
            java.io.File(context.filesDir,"synthetic-featherline-memory.json").writeText(measurements.toString())
            android.util.Log.i("SyntheticImport","Featherline full password/COMMIT/reopen sequence: $measurements")
        }

    }
}
