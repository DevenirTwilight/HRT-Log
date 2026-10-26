package net.plainnotes.app.attachments

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.AttachmentSession
import net.plainnotes.app.runtime.RuntimeLibrarySession
import java.util.UUID

/** No UI, format implementation or persistent credentials. Scoped calls to the existing engine. */
internal interface MedicalLibraryGateway:AutoCloseable {
    suspend fun export(uri:Uri,password:CharArray)
    suspend fun prepare(uri:Uri,password:CharArray):Prepared
    suspend fun restore():MedicalLibraryTransfer.Committed
    suspend fun status(operation:UUID):MedicalLibraryTransfer.Status?
    suspend fun abortPending(operation:UUID):Boolean
    val committedReceipt:MedicalLibraryTransfer.Committed?
    fun cancel()
    fun onHidden(listener:()->Unit):AutoCloseable=AutoCloseable {}
    fun onRevoked(listener:()->Unit):AutoCloseable
    data class Prepared(val operation:UUID,val summary:MedicalLibraryTransfer.Summary)
}
internal class RuntimeMedicalLibraryGateway(private val context:Context,private val runtime:RuntimeLibrarySession):MedicalLibraryGateway {
    private class Lease(val backend:MedicalLibraryTransfer,val cap:AttachmentSession.Capability,val registration:AutoCloseable):AutoCloseable {
        override fun close()=registration.close()
    }
    private val monitor=Any()
    private var epoch=0L
    private var closed=false
    private var current:Lease?=null
    private var prepared:MedicalLibraryTransfer.Prepared?=null
    @Volatile override var committedReceipt:MedicalLibraryTransfer.Committed?=null
        private set
    private suspend fun acquire():Lease {
        val token=synchronized(monitor) {check(!closed);epoch}
        val repo=runtime.repository();runtime.verify();runtime.checkRequest()
        val scope=(runtime.state.value as? RuntimeLibrarySession.State.Ready)?.scope ?: throw LibraryRequestRevoked()
        val session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
        val registration=runtime.protect(AutoCloseable {session.revoke()})
        try {
            val result=Lease(repo.medicalLibraryTransfer(),session.issue(),registration)
            synchronized(monitor) {check(!closed && epoch==token && current==null);current=result}
            return result
        }catch(t:Throwable) {registration.close();throw t}
    }
    private fun release(lease:Lease) {
        synchronized(monitor) {if(current===lease)current=null}
        lease.close()
    }
    override suspend fun export(uri:Uri,password:CharArray)=try {
        withContext(Dispatchers.IO) {
            require(uri.scheme=="content")
            committedReceipt=null
            val lease=acquire()
            try {requireNotNull(context.contentResolver.openOutputStream(uri,"w")).use {lease.backend.export(lease.cap,password,it)}}finally {release(lease)}
        }
    }finally {password.fill('\u0000')}
    override suspend fun prepare(uri:Uri,password:CharArray):MedicalLibraryGateway.Prepared=try {
        withContext(Dispatchers.IO) {
            require(uri.scheme=="content")
            committedReceipt=null
            val lease=acquire()
            try {
                val fd=requireNotNull(context.contentResolver.openFileDescriptor(uri,"r"))
                val channel=ParcelFileDescriptor.AutoCloseInputStream(fd).channel
                val candidate=lease.backend.prepare(lease.cap,password,channel)
                synchronized(monitor) {if(current!==lease || closed) {candidate.close();throw LibraryRequestRevoked()};prepared=candidate}
                MedicalLibraryGateway.Prepared(candidate.operation,candidate.summary)
            }catch(t:Throwable) {release(lease);throw t}
        }
    }finally {password.fill('\u0000')}
    override suspend fun restore():MedicalLibraryTransfer.Committed {
        val source=synchronized(monitor) {check(!closed);requireNotNull(prepared)}
        return try {
            source.restore().also {
                committedReceipt=it
                runtime.invalidate(net.plainnotes.app.R.string.library_change_committed)
            }
        }catch(t:Throwable) {
            source.committedReceipt?.let {committedReceipt=it;runtime.invalidate(net.plainnotes.app.R.string.library_change_committed);return it}
            throw t
        }finally {cancel()}
    }
    override suspend fun status(operation:UUID):MedicalLibraryTransfer.Status? {
        val lease=acquire();return try {lease.backend.status(lease.cap,operation)}finally {release(lease)}
    }
    override suspend fun abortPending(operation:UUID):Boolean {
        val lease=acquire();return try {lease.backend.abortPending(lease.cap,operation)}finally {release(lease)}
    }
    override fun onHidden(listener:()->Unit)=runtime.observePresentationHidden(listener)
    override fun onRevoked(listener:()->Unit)=runtime.protect(AutoCloseable(listener))
    override fun cancel() {
        val owned=synchronized(monitor) {epoch++;val pair=prepared to current;prepared=null;current=null;pair}
        try {runCatching {owned.first?.close()}}finally {runCatching {owned.second?.close()}}
    }
    override fun close() {synchronized(monitor) {closed=true};cancel()}
}
