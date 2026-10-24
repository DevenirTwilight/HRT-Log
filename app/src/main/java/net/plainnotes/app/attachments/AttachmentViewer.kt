package net.plainnotes.app.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import net.plainnotes.app.data.MedicalAttachmentOperations
import java.io.InputStream
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Internal viewer, not an exported descriptor or ACTION_VIEW. All originals stay encrypted.
 * Caller uses an IO dispatcher, closes on page exit, and blanks the view when its scope is revoked.
 * Existing fully authenticated/scoped reader checks every callback and every bitmap publication.
 */
internal class AttachmentViewer(private val context:Context,private val read:MedicalAttachmentOperations.Read):AutoCloseable {
    private val monitor=Any()
    @Volatile private var closed=false
    private val disposed=AtomicBoolean(false)
    private var thread:HandlerThread?=null
    private var descriptor:ParcelFileDescriptor?=null
    private var pdf:PdfRenderer?=null
    private val images=mutableListOf<Bitmap>()
    private val prefixes=mutableListOf<ByteArray>()
    private val revocation=read.own(AutoCloseable {
        closed=true
        synchronized(images) {images.forEach {it.recycle()};images.clear();prefixes.forEach {it.fill(0)};prefixes.clear()}
        // Never block AppLock/publication or the ProxyFD callback thread on native renderer teardown.
        cleanup.launch {dispose()}
    })
    private fun valid() {check(!closed);read.check()}
    private fun retain(bitmap:Bitmap):Bitmap {
        try {return read.publish {check(!closed);synchronized(images) {images+=bitmap};bitmap}}catch(e:Throwable) {bitmap.recycle();throw e}
    }
    fun pdfPage(index:Int,width:Int=512):Bitmap=synchronized(monitor) {
        valid();require(width in 1..2048);check(synchronized(images) {images.isEmpty()}) {"Release the previous page"}
        if(pdf==null) {
            val handler=HandlerThread("attachment-view-reader").apply {start()}.also {thread=it}
            val callbacks=object:ProxyFileDescriptorCallback() {
                override fun onGetSize():Long=try {read.size}catch(e:Exception) {throw ErrnoException("Revoked attachment size",OsConstants.EIO,e)}
                override fun onRead(offset:Long,size:Int,data:ByteArray):Int=try {read.readAt(offset,data,0,minOf(size,data.size))}
                    catch(e:Exception) {data.fill(0);throw ErrnoException("Authenticated attachment read",OsConstants.EIO,e)}
                override fun onRelease() {read.close()}
            }
            try {
                descriptor=(context.getSystemService(Context.STORAGE_SERVICE) as StorageManager).openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY,callbacks,Handler(handler.looper))
                pdf=PdfRenderer(requireNotNull(descriptor))
            }catch(e:Throwable) {close();throw e}
        }
        requireNotNull(pdf).openPage(index).use {page->
            val height=(width.toLong()*page.height/page.width).coerceAtLeast(1);require(height<=2048 && width.toLong()*height*4<=16*1024*1024)
            val bitmap=Bitmap.createBitmap(width,height.toInt(),Bitmap.Config.ARGB_8888)
            try {page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);retain(bitmap)}catch(e:Throwable) {bitmap.recycle();throw e}
        }
    }
    /** Decode dimensions before allocating pixels. No provider MIME or filename participates. */
    fun image():Bitmap=synchronized(monitor) {
        valid();check(synchronized(images) {images.isEmpty()})
        fun stream():InputStream=object:InputStream() {
            var position=0L
            override fun read():Int {val b=ByteArray(1);return if(read(b,0,1)<0)-1 else b[0].toInt() and 255}
            override fun read(b:ByteArray,o:Int,n:Int):Int {if(n==0)return 0;val count=this@AttachmentViewer.read.readAt(position,b,o,n);if(count>0)position+=count;return count}
        }
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        stream().use {BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048 && bounds.outWidth.toLong()*bounds.outHeight*4<=16*1024*1024)
        retain(requireNotNull(stream().use {BitmapFactory.decodeStream(it)}))
    }
    fun prefix(maximum:Int=256):ByteArray=synchronized(monitor) {
        valid();require(maximum in 1..4096);val b=ByteArray(minOf(read.size,maximum.toLong()).toInt())
        try {var p=0;while(p<b.size) {val n=read.readAt(p.toLong(),b,p,b.size-p);check(n>0);p+=n};read.publish {check(!closed);synchronized(images) {prefixes.forEach {it.fill(0)};prefixes.clear();prefixes+=b};b}}
        catch(e:Throwable) {b.fill(0);throw e}
    }
    fun release(bitmap:Bitmap)=synchronized(images) {if(images.remove(bitmap))bitmap.recycle()}
    override fun close() {
        try {revocation.close()}finally {read.close()}
    }
    private fun dispose()=synchronized(monitor) {
        if(disposed.compareAndSet(false,true)) {
            try {pdf?.close()}finally {pdf=null;try {descriptor?.close()}finally {descriptor=null;thread?.quitSafely();thread=null}}
        }
    }
    companion object {private val cleanup=CoroutineScope(SupervisorJob()+Dispatchers.IO)}
}
