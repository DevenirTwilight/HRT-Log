package net.plainnotes.app.attachments

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
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.MainActivity
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.disguise.Disguise
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.ui.Destination
import net.plainnotes.app.ui.UiPrefs
import org.junit.*
import org.junit.Assert.*
import java.security.MessageDigest

/** Real production Activity/navigation/gateway. Only input bytes and seed medical facts are synthetic.
 * Run on the isolated emulator package via run_med02_product_native.py; never a synthetic UI host. */
class MedicalAttachmentProductAndroidTest {
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
    @After fun close() {
        scenario?.close();Session.reset()
        val service=instrumentation.uiAutomation.serviceInfo;service.flags=originalAccessibilityFlags;instrumentation.uiAutomation.serviceInfo=service
    }
    private fun launch() {
        scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
        scenario!!.onActivity {model=ViewModelProvider(it)[NotesViewModel::class.java]}
        ui.waitUntil(30000) {model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready}
    }
    private fun open(destination:Destination) {
        ui.waitUntil(30000) {var focused=false;scenario!!.onActivity {focused=it.hasWindowFocus()};focused}
        // Return through the real hub-owned detail/list navigation before using the drawer.
        repeat(2) {
            if(ui.onAllNodesWithTag("medical-center-back").fetchSemanticsNodes().isNotEmpty()) {
                ui.onNodeWithTag("medical-center-back").performClick();ui.waitForIdle()
            }
        }
        ui.onNodeWithContentDescription(text(R.string.menu)).performClick()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(text(destination.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(text(destination.title))).performClick()
    }
    private var currentFiles:String?=null
    private fun press(original:SemanticsMatcher) {
        val matcher=if(currentFiles!=null && ui.onAllNodes(original).fetchSemanticsNodes().size>1)original and hasAnyAncestor(hasTestTag(currentFiles!!)) else original
        try {ui.waitUntil(30000) {ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()}}
        catch(e:androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Missing $matcher; production tree: "+ui.onAllNodes(isRoot(),useUnmergedTree=true).printToString(maxDepth=24),e)
        }
        val node=ui.onNode(matcher)
        if(ui.onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty())node.performScrollTo()
        node.assertIsEnabled().performClick()
    }
    private fun tag(value:String)=press(hasTestTag(value))
    private fun button(id:Int)=press(hasText(text(id)))
    private fun visit() {
        open(Destination.MEDICAL)
        // UI-10: the Files area lists the visit files entrance; the visit page itself carries the related files inline.
        tag("medical-tab:files")
        tag("library-prepare")
        press(hasText("SYNTHETIC product clinician",substring=true))
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachments").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun back() {
        // Use actual system BACK dispatch. Espresso's default root can still be
        // the unfocused Activity while the lab attachment Dialog is dismissing.
        // No direct navigation/state mutation and no retry of the operation.
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
    }
    private fun labDetail(id:Long) {
        currentFiles="record-files:LAB:$id"
        // Library scope readiness precedes NotesViewModel's asynchronous committed
        // medical snapshot. In particular a new post-restore Activity has not yet
        // composed its result rows at that first boundary. Observe both, never sleep.
        ui.waitUntil(30000) {!model.state.value.loading && model.extra.value.labs.any {it.id==id}}
        ui.waitUntil(30000) {ui.onAllNodesWithTag("lab-group-row:$id").fetchSemanticsNodes().isNotEmpty()}
        val row=ui.onNodeWithTag("lab-group-row:$id")
        row.performScrollTo()
        // The real Labs FAB overlaps the right side of a low visible row on 320dp.
        // Tap its unobscured label side, relative to the actual semantic row bounds.
        // This is still a real touch on the existing result, not a ViewModel/URI shortcut.
        row.performTouchInput {click(androidx.compose.ui.geometry.Offset(width*0.2f,center.y))}
        // UI-10: one full-screen result page whose related files are part of it; no button in between.
        ui.waitUntil(30000) {ui.onAllNodesWithTag("lab-detail").fetchSemanticsNodes().isNotEmpty() && ui.onAllNodesWithTag("attachments").fetchSemanticsNodes().isNotEmpty()}
    }
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
    private fun toolbarNavigation():AccessibilityNodeInfo? {
        fun find(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.className=="android.widget.ImageButton" && node.isClickable && shown(node))return node
            for(i in 0 until node.childCount)find(node.getChild(i))?.let{return it}
            return null
        }
        return find(systemId("toolbar"))
    }
    /** Empty Recent can be navigated while provider roots are still loading.
     * Keep this narrow: a generic empty dir_list cannot authorize selecting its root again. */
    private fun emptyRecentReady():Boolean {
        val root=pickerRoot() ?: return false
        if(rootsOpen() || systemId("dir_list")?.childCount!=0 || toolbarNavigation()==null)return false
        val label=try {
            val resources=context.packageManager.getResourcesForApplication(root.packageName.toString())
            val id=resources.getIdentifier("root_recent","string",root.packageName.toString())
            if(id==0)return false
            resources.getString(id)
        }catch(_:android.content.pm.PackageManager.NameNotFoundException) {return false}
        return systemId("toolbar")?.findAccessibilityNodeInfosByText(label)
            ?.any {it.text?.toString()==label && shown(it)}==true
    }
    private fun loadedDirectory()=!rootsOpen() &&
        (systemNode("SYNTHETIC MED-02")!=null || systemNode("SYNTHETIC samples")!=null) &&
        (systemId("dir_list")?.childCount ?: 0)>0 &&
        listOf("SYNTHETIC sample.png","SYNTHETIC sample.pdf","SYNTHETIC sample.bin")
            .any {systemNode(it)!=null}
    private fun settlePicker() {instrumentation.uiAutomation.waitForIdle(300,3000)}
    private fun selectRoot() {
        var rootAction:AccessibilityNodeInfo?=null
        waitSystem("settled synthetic drawer root") {
            settlePicker()
            // A toolbar folder title is not a root action. Scope to the actual pane.
            var node=systemId("drawer_roots")?.findAccessibilityNodeInfosByText("SYNTHETIC MED-02")
                ?.firstOrNull{it.text?.toString()=="SYNTHETIC MED-02" && shown(it)}
            while(node!=null && !node.isClickable)node=node.parent
            rootAction=node?.takeIf(::shown);rootAction!=null
        }
        assertTrue(requireNotNull(rootAction).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        // Root selection closes the drawer itself. A Back injected while that close
        // is being dispatched can cancel the entire picker after the drawer closes.
        waitSystem("root selection completed without cancelling picker") {pickerRoot()!=null && !rootsOpen()}
    }
    private fun pickPng()=pickSample("png","attachments-confirm-attach")
    private fun pickSample(kind:String,confirmation:String) {
        waitSystem("DocumentsUI") {pickerRoot()!=null}
        waitSystem("initial directory or root pane") {
            rootsOpen() || (systemId("dir_list")?.childCount ?: 0)>0 || systemId("message")!=null || emptyRecentReady()
        }
        if(!loadedDirectory()) {
            if(!rootsOpen()) {
                var nav:AccessibilityNodeInfo?=null
                waitSystem("toolbar navigation") {nav=toolbarNavigation();nav!=null}
                assertTrue(requireNotNull(nav).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            selectRoot()
        }
        waitSystem("MED-02 sample directory") {loadedDirectory()}
        val name="SYNTHETIC sample.$kind"
        waitSystem(name) {systemNode(name)!=null && !rootsOpen()}
        settlePicker();touch(requireNotNull(systemNode(name)))
        waitSystem("production Activity resumed") {scenario?.state==Lifecycle.State.RESUMED && pickerRoot()==null}
        ui.waitUntil(30000) {ui.onAllNodesWithTag(confirmation).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun add(title:String) {
        tag("attachments-add");pickPng()
        ui.onNodeWithTag("attachments-name").performTextClearance()
        ui.onNodeWithTag("attachments-name").performTextInput(title)
        tag("attachments-confirm-attach")
        ui.waitUntil(30000) {ui.onAllNodesWithText(text(R.string.attach_saved)).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun read(target:MedicalAttachmentOperations.Target):MedicalAttachmentOperations.Document=runBlocking {
        model.libraryRuntime.attachments(target,"SYNTHETIC independent read").use {flow->
            val doc=flow.documents().single()
            flow.open(doc.document).use {reader->
                val actual=ByteArray(reader.size.toInt());var p=0
                while(p<actual.size) {val n=reader.readAt(p.toLong(),actual,p,actual.size-p);assertTrue(n>0);p+=n}
                val expected=SyntheticDocumentsProvider.sample("png")
                try {assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(expected),MessageDigest.getInstance("SHA-256").digest(actual))}
                finally {actual.fill(0);expected.fill(0)}
            }
            doc
        }
    }
    @Test fun mainActivityVisitAndIndividualLabSafCommitReopenPreviewUnlinkAndTransfer() {
        assertTrue(AttachmentFlowController.PUBLIC_ENTRY_ENABLED)
        launch()
        val repo=model.libraryRuntime.currentRepository()
        runBlocking {
            assertTrue("Isolated synthetic installation only",repo.appointments().isEmpty() && repo.labs().isEmpty())
            repo.saveAppointment(AppointmentEntity(type="OTHER",at_utc=1800000000000,at_zone="UTC",practitioner="SYNTHETIC product clinician",remind_minutes_before=0))
            repo.saveLabs(listOf(
                LabValueEntity(analyte_code="E2",value=120.0,unit="pg/mL",sampled_utc=1800000000000,sampled_zone="UTC"),
                LabValueEntity(analyte_code="T",value=9.0,unit="ng/dL",sampled_utc=1800000000000,sampled_zone="UTC")))
        }
        ui.runOnIdle {model.sync()}
        ui.waitUntil(30000) {model.state.value.appointments.size==1 && model.extra.value.labs.size==2}
        val visit=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,model.state.value.appointments.single().id)
        val lab=model.extra.value.labs.single {it.analyte_code=="T"}
        assertNotEquals("Exercise a result ID that differs from its panel ID",lab.panel_id,lab.id)
        val labTarget=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB,lab.id)
        visit()
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachments-empty").fetchSemanticsNodes().isNotEmpty()}
        ui.onNode(hasTestTag("attachments-empty") and (currentFiles?.let {hasAnyAncestor(hasTestTag(it))} ?: hasTestTag("attachments-empty"))).assertExists()
        tag("attachments-add")
        waitSystem("settled real picker before cancellation") {
            settlePicker()
            pickerRoot()!=null && (rootsOpen() || systemId("dir_list")!=null || systemId("message")!=null)
        }
        // On a fresh API35 installation DocumentsUI may initially show its roots drawer.
        // A Back there only closes that pane; cancel the actual picker, not just its drawer.
        if(rootsOpen()) {
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            waitSystem("roots drawer closed before cancellation") {pickerRoot()!=null && !rootsOpen()}
        }
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        ui.waitUntil(30000) {ui.onAllNodesWithText(text(R.string.attach_cancelled)).fetchSemanticsNodes().isNotEmpty()}
        runBlocking {model.libraryRuntime.attachments(visit,"SYNTHETIC cancelled").use {assertTrue(it.documents().isEmpty())}}
        add("SYNTHETIC production visit")
        val saved=read(visit)
        scenario!!.close();scenario=null;launch();visit()
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachment:${saved.document}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(saved.document,read(visit).document)
        val label=context.getString(R.string.attach_item_description,saved.title,text(R.string.attach_kind_image),text(R.string.attach_status_saved))
        press(hasContentDescription(label))
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachment-viewer-page").fetchSemanticsNodes().isNotEmpty()}
        val closeBounds=ui.onNodeWithTag("attachment-viewer-close").fetchSemanticsNode().boundsInRoot
        val navigationBounds=ui.onNodeWithTag("medical-center-back").assertExists().fetchSemanticsNode().boundsInRoot
        assertTrue("Preview close must be below the real shell app bar, not covered by its Back button",closeBounds.top>=navigationBounds.bottom)
        tag("attachment-viewer-close")
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachment-viewer").fetchSemanticsNodes().isEmpty()}
        // A retained uncertain result is resolved only by the visible, read-only action.
        // Never repeat SAF input or encryption to make a management action available.
        if(ui.onAllNodesWithTag("attachments-check").fetchSemanticsNodes().isNotEmpty()) {
            tag("attachments-check")
            ui.waitUntil(30000) {ui.onAllNodesWithText(text(R.string.attach_saved)).fetchSemanticsNodes().isNotEmpty()}
            assertEquals(saved.document,read(visit).document)
        }
        // The link action is in the row's overflow menu.
        tag("attachment-more:${saved.document}")
        tag("attachment-unlink:${saved.document}")
        tag("attachments-unlink-confirm")
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachments-empty").fetchSemanticsNodes().isNotEmpty()}
        runBlocking {model.libraryRuntime.attachments(visit,"SYNTHETIC unlinked").use {assertTrue(it.documents().isEmpty())}}
        back();open(Destination.MEDICAL)
        tag("medical-tab:records")
        ui.onNodeWithTag("medical-list").performScrollToNode(hasTestTag("medical-labs"))
        tag("medical-labs")
        labDetail(lab.id)
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachments-empty").fetchSemanticsNodes().isNotEmpty()}
        add("SYNTHETIC production lab")
        val labSaved=read(labTarget)
        assertEquals("SYNTHETIC production lab",labSaved.title)
        // This test uses the real runtime transfer adapter, not a test-only archive or database.
        // Archive SAF/confirmation widgets have separate existing controller tests; do not claim those here.
        back()
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachments").fetchSemanticsNodes().isEmpty()}
        ui.waitUntil(30000) {
            var focused=false
            scenario!!.onActivity {focused=it.hasWindowFocus()}
            focused
        }
        open(Destination.MEDICAL)
        tag("medical-tab:files")
        // MED-03: the library is one lazy list; at 320 dp the backup entry is below the first screen, so a person scrolls to it.
        ui.waitUntil(30000) {ui.onAllNodesWithTag("library").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-backup"))
        tag("library-backup")
        ui.onNodeWithText(text(R.string.med_transfer_scope)).assertExists()
        back()
        ui.waitUntil(30000) {ui.onAllNodesWithText(text(R.string.med_transfer_scope)).fetchSemanticsNodes().isEmpty()}
        ui.waitUntil(30000) {var focused=false;scenario!!.onActivity {focused=it.hasWindowFocus()};focused}
        ui.onNodeWithTag("library").assertExists()
        open(Destination.SETTINGS)
        button(R.string.settings_data_backup)
        button(R.string.med_transfer_title)
        ui.onNodeWithText(text(R.string.med_transfer_export)).assertExists()
        ui.onNodeWithText(text(R.string.med_transfer_restore)).assertExists()
        val old=model.libraryRuntime
        val gateway=RuntimeMedicalLibraryGateway(context,old)
        val archive=android.net.Uri.parse("content://net.plainnotes.app.test.synthetic-medarchive/archive")
        context.contentResolver.call(archive,"reset",null,null)
        try {
            runBlocking {
                gateway.export(archive,"SYNTHETIC product archive".toCharArray())
                val candidate=gateway.prepare(archive,"SYNTHETIC product archive".toCharArray())
                assertEquals(2,candidate.summary.format)
                assertEquals("Unlinked content is still retained in the full archive",2,candidate.summary.versions)
                val committed=gateway.restore();assertEquals(committed,gateway.committedReceipt)
            }
            try {old.checkRequest();fail("Old Activity scope was rebound after root COMMIT")}catch(_:LibraryRequestRevoked) {}
        }finally {gateway.close()}
        scenario!!.close();scenario=null;launch()
        open(Destination.LABS);labDetail(lab.id)
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachment:${labSaved.document}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(labSaved.document,read(labTarget).document)
        val restoredLabel=context.getString(R.string.attach_item_description,labSaved.title,text(R.string.attach_kind_image),text(R.string.attach_status_saved))
        press(hasContentDescription(restoredLabel))
        ui.waitUntil(30000) {ui.onAllNodesWithTag("attachment-viewer-page").fetchSemanticsNodes().isNotEmpty()}
        tag("attachment-viewer-close")
        runBlocking {model.libraryRuntime.attachments(visit,"SYNTHETIC preserved unlink").use {assertTrue(it.documents().isEmpty())}}
        context.contentResolver.call(archive,"reset",null,null)
    }
    @Test fun standalonePdfExplicitWholeDrawLinkFiveMembersAndReopenUseOneOriginal() {
        launch()
        val panel=runBlocking {
            val repo=model.libraryRuntime.currentRepository()
            assertTrue(repo.labs().isEmpty())
            repo.saveLabs(listOf("E2" to "pg/mL","FSH" to "IU/L","AST" to "U/L","ALT" to "U/L","T" to "ng/dL").map {(code,unit)->
                LabValueEntity(analyte_code=code,value=12.0,unit=unit,sampled_utc=1800000000000,sampled_zone="UTC")})
        }
        ui.runOnIdle {model.sync()}
        ui.waitUntil(30000) {model.extra.value.labs.size==5 && model.extra.value.labPanels.any {it.id==panel.id}}
        open(Destination.MEDICAL);tag("medical-tab:files");tag("library-add")
        pickSample("pdf","add-document-confirm")
        ui.onNodeWithTag("add-document-title").performTextClearance()
        ui.onNodeWithTag("add-document-title").performTextInput("SYNTHETIC whole blood draw")
        tag("add-document-confirm")
        ui.waitUntil(30000) {ui.onAllNodesWithText(text(R.string.attach_saved)).fetchSemanticsNodes().isNotEmpty()}
        val document=runBlocking {model.libraryRuntime.attachments(null,"SYNTHETIC independent library check").use {it.documents().single()}}
        tag("library-doc:${document.document}");tag("file-detail-add-link")
        tag("library-target:panel:${panel.id}");tag("library-link-confirm")
        ui.waitUntil(30000) {ui.onAllNodesWithText(text(R.string.med03_relation_done)).fetchSemanticsNodes().isNotEmpty()}
        runBlocking {model.libraryRuntime.attachments(null,"SYNTHETIC no duplicate object").use {flow->
            assertEquals(1,flow.documents().size);assertEquals(1,flow.relationships(document.document).size)
            assertEquals(MedicalAttachmentOperations.Kind.LAB_PANEL,flow.relationships(document.document).single().target.kind)
        }}
        scenario!!.close();scenario=null;launch()
        val members=model.extra.value.labs.filter {it.panel_id==panel.id}
        ui.waitUntil(30000) {model.extra.value.labs.count {it.panel_id==panel.id}==5}
        for(member in model.extra.value.labs.filter {it.panel_id==panel.id}) {
            open(Destination.LABS);labDetail(member.id)
            ui.waitUntil(30000) {ui.onAllNodesWithTag("attachment:${document.document}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText(context.getString(R.string.med03_whole_draw,5)).assertExists()
            val panelTarget=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB_PANEL,panel.id,java.util.UUID.fromString(panel.uuid),member.id)
            runBlocking {model.libraryRuntime.attachments(panelTarget,"SYNTHETIC shared draw original").use {flow->
                assertEquals(document.document,flow.documents().single().document)
                flow.open(document.document).use {reader->val b=ByteArray(5);assertEquals(5,reader.readAt(0,b,0,5));assertEquals("%PDF-",String(b,Charsets.US_ASCII))}
            }}
            back();ui.waitUntil(30000) {ui.onAllNodesWithTag("lab-detail").fetchSemanticsNodes().isEmpty()}
        }
        // Select while the visit is still a form: cancellation does not discard its fields.
        currentFiles=null
        open(Destination.MEDICAL);tag("medical-tab:records");tag("medical-record-past-visit")
        tag("new-record-choose-file");pickSample("png","new-record-file-chosen")
        runBlocking {model.libraryRuntime.attachments(null,"SYNTHETIC no pre-save import").use {assertEquals(1,it.documents().size)}}
        tag("medical-save")
        ui.waitUntil(30000) {ui.onAllNodesWithTag("record-committed-before-file").fetchSemanticsNodes().isNotEmpty()}
        tag("attachments-confirm-attach")
        ui.waitUntil(30000) {ui.onAllNodesWithText(text(R.string.attach_saved)).fetchSemanticsNodes().isNotEmpty()}
        val visit=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,runBlocking {model.libraryRuntime.currentRepository().appointments().single().id})
        read(visit)
        tag("new-record-files-done")
        open(Destination.LABS);tag("labs-home-add")
        ui.onNodeWithTag("lab-batch-value:0").performTextInput("130")
        tag("new-record-choose-file");pickSample("pdf","new-record-file-chosen")
        tag("lab-batch-save")
        ui.waitUntil(30000) {ui.onAllNodesWithTag("record-committed-before-file").fetchSemanticsNodes().isNotEmpty()}
        tag("attachments-confirm-attach")
        ui.waitUntil(30000) {ui.onAllNodesWithText(text(R.string.attach_saved)).fetchSemanticsNodes().isNotEmpty()}
        val savedDraw=runBlocking {model.libraryRuntime.currentRepository().labPanels().single {it.id!=panel.id}}
        runBlocking {model.libraryRuntime.attachments(MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB_PANEL,savedDraw.id,java.util.UUID.fromString(savedDraw.uuid)),"SYNTHETIC form report").use {flow->
            assertEquals(1,flow.documents().size)
            flow.open(flow.documents().single().document).use {reader->val b=ByteArray(5);assertEquals(5,reader.readAt(0,b,0,5));assertEquals("%PDF-",String(b,Charsets.US_ASCII))}
        }}
        tag("new-record-files-done")

    }
}
