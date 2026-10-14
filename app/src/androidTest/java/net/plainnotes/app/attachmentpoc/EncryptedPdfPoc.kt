package net.plainnotes.app.attachmentpoc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import net.plainnotes.poc.ChunkObject
import java.io.Closeable
import net.plainnotes.poc.CapabilityObject

/** Instrumentation-only public-API experiment. No plaintext file, no production route. */
class EncryptedPdfPoc(context:Context,owned:ChunkObject.Owned,
                      private val session:CapabilityObject.Session=CapabilityObject.Session(),
                      private val beforeDelivery:()->Unit={}):Closeable {
    private val scoped=CapabilityObject.adopt(owned,session)
    private val reader=scoped.open() // Complete authentication BEFORE PdfRenderer receives an FD.
    private val thread=HandlerThread("synthetic-proxy-poc").apply{start()}
    private var descriptor:ParcelFileDescriptor?=null
    private var renderer:PdfRenderer?=null
    private val bitmaps=mutableListOf<Bitmap>()
    private val monitor=Any()
    val reads get()=reader.blockReads
    val scratchBytes get()=reader.maxScratchBytes
    private fun authorized() {if(!session.check())throw ErrnoException("Synthetic revoked",OsConstants.EBADF)}
    val callback=object:ProxyFileDescriptorCallback() {
        override fun onGetSize():Long {authorized();return reader.size}
        override fun onRead(offset:Long,size:Int,data:ByteArray):Int {
            authorized()
            return try {val n=reader.readAt(offset,data,size,beforeDelivery);n}
            catch(e:Exception){data.fill(0);throw ErrnoException("Synthetic authenticated read",OsConstants.EIO,e)}
        }
        override fun onRelease(){reader.close()}
    }
    init {
        try {
            descriptor=(context.getSystemService(Context.STORAGE_SERVICE) as StorageManager)
                .openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY,callback,Handler(thread.looper))
            renderer=PdfRenderer(descriptor!!)
        }catch(e:Throwable){close();throw e}
    }
    val pages:Int get()=synchronized(monitor){scoped.publish {renderer!!.pageCount}}
    fun render(page:Int,width:Int=512):Bitmap=synchronized(monitor) {
        scoped.publish {Unit};check(bitmaps.isEmpty()) { "Release the previous synthetic page first" };require(width in 1..2048)
        renderer!!.openPage(page).use{p->
            val height=(width.toLong()*p.height/p.width).coerceAtLeast(1)
            require(height<=2048 && width.toLong()*height*4<=16*1024*1024)
            val bitmap=Bitmap.createBitmap(width,height.toInt(),Bitmap.Config.ARGB_8888)
            try{p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);beforeDelivery();scoped.publish {bitmaps+=bitmap;bitmap}}
            catch(e:Throwable){bitmap.recycle();throw e}
        }
    }
    fun release(bitmap:Bitmap)=synchronized(monitor){if(bitmaps.remove(bitmap))bitmap.recycle()}
    /** Lock/switch cancels future callbacks first, then closes native state and all owned bitmaps. */
    override fun close() {
        session.revoke();scoped.close()
        synchronized(monitor) {
            bitmaps.forEach{it.recycle()};bitmaps.clear()
            try{renderer?.close()}finally{renderer=null;try{descriptor?.close()}finally{descriptor=null;reader.close();thread.quitSafely()}}
        }
    }
}
