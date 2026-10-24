package net.plainnotes.app.debug.attachments

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import net.plainnotes.app.attachments.AttachmentFlowController
import net.plainnotes.app.attachments.AttachmentViewer
import java.util.UUID

/** No launcher/exported entry. Test DocumentsProvider ONLY, no arbitrary original-file switch. */
class AttachmentOperationTestActivity:ComponentActivity() {
    internal lateinit var fixture:SyntheticAttachmentFixture
    internal var flow:AttachmentFlowController?=null
    private var viewer:AttachmentViewer?=null
    private var chosen:ActivityResult?=null // Never Bundle/SavedState/persisted URI grants.
    private var waiting=false
    private var operation=UUID.randomUUID()
    private var document=UUID.randomUUID()
    private var ready by mutableStateOf(false)
    internal var message by mutableStateOf("SYNTHETIC loading")
    private var image by mutableStateOf<Bitmap?>(null)
    private val select=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        if(waiting) {
            waiting=false
            if(result.resultCode==Activity.RESULT_OK && result.data?.data?.authority==AUTHORITY) {chosen=result;message="SYNTHETIC selected; confirm"}
            else {chosen=null;message="SYNTHETIC cancelled or refused"}
        }
    }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val restored=androidx.lifecycle.SavedStateHandle()
        if(state!=null) {restored[net.plainnotes.app.runtime.RuntimeLibrarySession.RESTORED]=true;state.getStringArrayList("scope")?.let {restored[net.plainnotes.app.runtime.RuntimeLibrarySession.SAVED_SCOPE]=it}}
        fixture=SyntheticAttachmentFixture(this,state?.getString("fixture")?.let(UUID::fromString) ?: UUID.randomUUID(),restored)
        operation=state?.getString("operation")?.let(UUID::fromString) ?: operation
        document=state?.getString("document")?.let(UUID::fromString) ?: document
        lifecycleScope.launch {try {fixture.initialize();ready=true;message="SYNTHETIC ready"}catch(_:Exception){message="SYNTHETIC refused"}}
        setContent {MaterialTheme {Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("MED-02 SYNTHETIC / PUBLIC OFF")
            Text(message)
            Button(enabled=ready,onClick={lifecycleScope.launch {
                fresh();waiting=true
                select.launch(requireNotNull(flow).pickerIntent().putExtra(DocumentsContract.EXTRA_INITIAL_URI,DocumentsContract.buildDocumentUri(AUTHORITY,"samples")))
            }}) {Text("Choose synthetic attachment")}
            Button(enabled=ready && chosen!=null,onClick={lifecycleScope.launch {val result=chosen ?: return@launch;chosen=null;fresh();requireNotNull(flow).selected(result);message="SYNTHETIC ${requireNotNull(flow).state.value}"}}) {Text("Confirm synthetic import")}
            Button(enabled=ready,onClick={lifecycleScope.launch {try {
                if(flow==null)fresh()
                val rows=requireNotNull(flow).documents();val saved=rows.single {it.document==document && it.state=="ACTIVE" && it.linkState=="ACTIVE"}
                val bitmap=withContext(Dispatchers.IO) {
                    viewer?.close();val v=AttachmentViewer(this@AttachmentOperationTestActivity,requireNotNull(flow).open(saved.document)).also {viewer=it}
                    when(saved.mime) {"application/pdf"->v.pdfPage(0);"image/png","image/jpeg"->v.image();else->{v.prefix();null}}
                }
                fixture.runtime.checkRequest();image=bitmap;message="SYNTHETIC read verified"
            }catch(_:Exception) {image=null;message="SYNTHETIC view refused"}}}) {Text("Read saved attachment")}
            Button(enabled=ready,onClick={lifecycleScope.launch {try {if(flow==null)fresh();message="SYNTHETIC backup ${requireNotNull(flow).backupEligibility()}"}catch(_:Exception){message="SYNTHETIC backup refused"}}}) {Text("Check backup")}
            Button(enabled=ready,onClick={lifecycleScope.launch {if(flow==null)fresh();requireNotNull(flow).reconcile();message="SYNTHETIC ${requireNotNull(flow).state.value}"}}) {Text("Reconcile operation")}
            Button(enabled=ready,onClick={lifecycleScope.launch {try {if(flow==null)fresh();requireNotNull(flow).recover();message="SYNTHETIC ${requireNotNull(flow).state.value}"}catch(_:Exception){message="SYNTHETIC recovery refused"}}}) {Text("Recover incomplete operation")}
            image?.takeUnless {it.isRecycled}?.let {Image(it.asImageBitmap(),"Synthetic authenticated attachment")}
        }}}
    }
    private suspend fun fresh() {flow?.close();flow=fixture.runtime.attachments(fixture.target,"SYNTHETIC report",operation,document)}
    override fun onResume() {super.onResume();if(::fixture.isInitialized)lifecycleScope.launch {try {fixture.runtime.resume();ready=true}catch(_:Exception){ready=false}}}
    override fun onStop() {ready=false;image=null;viewer?.close();viewer=null;if(::fixture.isInitialized)fixture.runtime.pause();super.onStop()}
    override fun onSaveInstanceState(out:Bundle) {out.putString("fixture",fixture.id.toString());out.putString("operation",operation.toString());out.putString("document",document.toString());(fixture.runtime.state.value as? net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready)?.scope?.let {out.putStringArrayList("scope",it.encoded())};super.onSaveInstanceState(out)}
    override fun onDestroy() {flow?.close();if(::fixture.isInitialized)fixture.close();super.onDestroy()}
    companion object {const val AUTHORITY="net.plainnotes.app.test.synthetic-medflow"}
}
