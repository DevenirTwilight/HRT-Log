package net.plainnotes.app.attachments
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.*
import net.plainnotes.app.attachments.ui.*
import net.plainnotes.app.data.MedicalAttachmentOperations
import net.plainnotes.app.domain.AttachmentSession
import java.util.UUID

fun main()=runBlocking {
    val backend=MedicalAttachmentOperations()
    var imports=0
    val gateway=object:MedicalAttachmentGateway {
        override val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB,1)
        override suspend fun pickerIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT)
        override suspend fun documents()=backend.commits.values.map {MedicalAttachmentOperations.Document(it.document,1,"synthetic","application/pdf","ACTIVE","ACTIVE")}
        override suspend fun preview(document:UUID):MedicalAttachmentGateway.Preview=error("unused")
        override suspend fun newImport(title:String):MedicalAttachmentGateway.Import {
            imports++
            val session=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
            val flow=AttachmentFlowController(backend,session,AutoCloseable{},MedicalAttachmentOperations.Import(UUID.randomUUID(),UUID.randomUUID(),target,title),{})
            return object:MedicalAttachmentGateway.Import {
                override val operation get()=flow.request.operation
                override val document get()=flow.request.document
                override val state get()=flow.state
                override suspend fun selected(result:ActivityResult)=flow.selected(result)
                override fun cancel()=flow.cancel()
                override fun close()=flow.close()
            }
        }
        override suspend fun reconcile(operation:UUID,document:UUID):AttachmentFlowController.State=error("explicit check must happen first")
        override fun onRevoked(listener:()->Unit)=AutoCloseable{}
    }
    val worker=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
    val controller=MedicalAttachmentsController(gateway,worker,Dispatchers.Unconfined)
    fun result()=ActivityResult(Activity.RESULT_OK,Intent().setData(Uri.parse("content://synthetic/a")))
    check(controller.startImport()!=null)
    controller.picked(result(),"synthetic");controller.confirmImport("synthetic")
    check(controller.state.value.import==MedicalAttachmentsController.Import.Unknown)
    check(backend.commits.size==1)
    println("After synthetic COMMIT and lost response: state=${controller.state.value.import}, imports=$imports, commit facts=${backend.commits.size}")
    val retry=controller.startImport()
    check(retry!=null)
    println("startImport while UNKNOWN returned picker: true; state=${controller.state.value.import}")
    controller.picked(result(),"synthetic");controller.confirmImport("synthetic")
    check(imports==2 && backend.commits.size==2)
    check(controller.state.value.import is MedicalAttachmentsController.Import.Saved)
    val before=backend.reconciles;controller.checkStatus();check(backend.reconciles==before)
    println("New operation/document UUID accepted: imports=$imports, distinct commit facts=${backend.commits.size}; state=${controller.state.value.import}; first unresolved identity no longer checkable")
    controller.close();worker.cancel()
}
