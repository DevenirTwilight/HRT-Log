package net.plainnotes.app.debug.attachments

import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.MedicalAttachmentViewer
import net.plainnotes.app.attachments.ui.MedicalAttachmentsController
import net.plainnotes.app.attachments.ui.MedicalAttachmentsSection
import net.plainnotes.app.attachments.ui.RuntimeAttachmentGateway
import net.plainnotes.app.data.MedicalAttachmentOperations
import net.plainnotes.app.runtime.RuntimeLibrarySession
import net.plainnotes.app.ui.NotesTheme
import java.util.UUID

/**
 * MED-02 attachment UI host: synthetic encrypted namespace on an emulator only, synthetic test provider
 * only, no launcher/exported entry and absent from release. Exercises the production section, controller,
 * gateway, flow controller and viewer against real COMMIT and authenticated reads.
 */
class MedicalAttachmentUiTestActivity:ComponentActivity() {
    internal lateinit var fixture:SyntheticAttachmentFixture
    internal var controller by mutableStateOf<MedicalAttachmentsController?>(null)
    internal var target by mutableStateOf<MedicalAttachmentOperations.Target?>(null)
    internal var message by mutableStateOf("SYNTHETIC loading")
    private var ready by mutableStateOf(false)
    /** The system picker covers this page; like the app's background grace period, that alone does not lock. */
    private var awaitingPicker=false
    private val select=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        awaitingPicker=false
        val c=controller ?: return@registerForActivityResult
        // Never an arbitrary original: anything other than the synthetic provider counts as cancelled.
        val synthetic=result.data?.data?.authority==AttachmentOperationTestActivity.AUTHORITY
        c.picked(if(synthetic)result else ActivityResult(RESULT_CANCELED,null),defaultTitle())
    }
    private fun defaultTitle()=getString(if(target?.kind==MedicalAttachmentOperations.Kind.LAB)R.string.attach_default_lab else R.string.attach_default_visit)
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val restored=androidx.lifecycle.SavedStateHandle()
        if(state!=null) {restored[RuntimeLibrarySession.RESTORED]=true;state.getStringArrayList("scope")?.let {restored[RuntimeLibrarySession.SAVED_SCOPE]=it}}
        fixture=SyntheticAttachmentFixture(this,state?.getString("fixture")?.let(UUID::fromString) ?: UUID.randomUUID(),restored)
        lifecycleScope.launch {try {
            fixture.initialize()
            // Only a typed identity survives recreation; the page shows the list again, never an old picker result or preview.
            state?.getString("target_kind")?.let {kind->choose(listOf(fixture.target,fixture.labTarget).single {it.kind.name==kind && it.id==state.getLong("target_id")})}
            ready=true;message="SYNTHETIC ready"
            fixture.runtime.state.first {it is RuntimeLibrarySession.State.Unavailable}
            controller?.libraryChanged();message="SYNTHETIC data changed"
        }catch(_:Exception) {ready=false;message="SYNTHETIC refused"}}
        setContent {NotesTheme {Surface {
            val c=controller
            val ui=c?.state?.collectAsState()?.value
            BackHandler(ui?.viewer!=null && ui.viewer!=MedicalAttachmentsController.Viewer.Closed) {c?.closeViewer()}
            // Synthetic test controls stay reachable above the page; the viewer covers only the page area below them.
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal=16.dp,vertical=8.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("MED-02 SYNTHETIC / PUBLIC OFF")
                    Text(message)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button({choose(fixture.target)},enabled=ready) {Text("Synthetic visit")}
                        Button({choose(fixture.labTarget)},enabled=ready) {Text("Synthetic lab")}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({fixture.runtime.pause();message="SYNTHETIC locked"},enabled=ready) {Text("Lock")}
                        OutlinedButton({lifecycleScope.launch {try {fixture.runtime.resume();controller?.resume();message="SYNTHETIC unlocked"}catch(_:Exception){message="SYNTHETIC unlock refused"}}},enabled=ready) {Text("Unlock")}
                        OutlinedButton({fixture.runtime.invalidate()},enabled=ready) {Text("Switch data")}
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(c!=null && ui!=null)MedicalAttachmentsSection(ui,targetLabel(),
                            onAdd={lifecycleScope.launch {c.startImport()?.let {awaitingPicker=true;select.launch(it.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                                DocumentsContract.buildDocumentUri(AttachmentOperationTestActivity.AUTHORITY,"samples")))}}},
                            onConfirm=c::confirmImport,
                            onCancelImport=c::cancelImport,onCheckStatus=c::checkStatus,onDismissMessage=c::dismissImportMessage,onRetry=c::refresh,onOpen=c::open)
                    }
                    if(c!=null && ui!=null)MedicalAttachmentViewer(ui.viewer,c::closeViewer)
                }
            }
        }}}
    }
    private fun targetLabel()=target?.let {"SYNTHETIC ${if(it.kind==MedicalAttachmentOperations.Kind.LAB)"lab result" else "visit"} #${it.id}"} ?: ""
    /** Explicit existing record identity only; switching record closes the previous page's controller. */
    private fun choose(next:MedicalAttachmentOperations.Target) {
        controller?.close()
        target=next
        controller=MedicalAttachmentsController(RuntimeAttachmentGateway(applicationContext,fixture.runtime,next),lifecycleScope).also {it.refresh()}
    }
    override fun onResume() {super.onResume();if(::fixture.isInitialized)lifecycleScope.launch {try {fixture.runtime.resume();controller?.resume()}catch(_:Exception){}}}
    override fun onStop() {if(::fixture.isInitialized && !awaitingPicker)fixture.runtime.pause();super.onStop()}
    override fun onSaveInstanceState(out:Bundle) {
        out.putString("fixture",fixture.id.toString());target?.let {out.putString("target_kind",it.kind.name);out.putLong("target_id",it.id)}
        (fixture.runtime.state.value as? RuntimeLibrarySession.State.Ready)?.scope?.let {out.putStringArrayList("scope",it.encoded())}
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() {controller?.close();if(::fixture.isInitialized)fixture.close();super.onDestroy()}
}
