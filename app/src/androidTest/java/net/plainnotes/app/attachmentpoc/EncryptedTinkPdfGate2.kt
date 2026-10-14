package net.plainnotes.app.attachmentpoc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.*
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import net.plainnotes.app.domain.AttachmentSession
import net.plainnotes.poc.B2StreamingCandidate
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer

/** TEST ONLY: actual Tink full authentication -> same pinned FD lease -> public ProxyFD/PdfRenderer. */
class EncryptedTinkPdfGate2(context:Context,file:File,key:ByteArray,aad:ByteArray,
    private val owner:AttachmentSession.Capability, expected:B2StreamingCandidate.Evidence,
    private val beforeDelivery:()->Unit={}, private val beforeBitmapDelivery:()->Unit={}):Closeable {
    private val lease=B2StreamingCandidate.openAuthenticated(file,key,aad,owner,expected)
    private val thread=HandlerThread("synthetic-tink-gate2").apply{start()}
    private val monitor=Any()
    private var descriptor:ParcelFileDescriptor?=null
    private var renderer:PdfRenderer?=null
    private var bitmap:Bitmap?=null
    var rejectedBitmaps=0;private set
    var reads=0;private set
    var maxRequest=0;private set
    val callback=object:ProxyFileDescriptorCallback() {
        override fun onGetSize():Long=try{lease.size()}catch(e:Exception){throw ErrnoException("Synthetic revoked",OsConstants.EBADF,e)}
        override fun onRead(offset:Long,size:Int,data:ByteArray):Int {
            try {
                require(size>=0&&size<=data.size);maxRequest=maxOf(maxRequest,size);reads++
                // The kernel may ask for more than the lease scratch budget. Bound each decrypt/copy.
                var done=0
                while(done<size) {
                    val n=lease.readAt(offset+done,ByteBuffer.wrap(data,done,minOf(size-done,B2StreamingCandidate.SCRATCH)),beforeDelivery)
                    if(n<=0)break;done+=n
                }
                owner.check();return done
            }catch(e:Exception){data.fill(0);throw ErrnoException("Synthetic authenticated read",OsConstants.EIO,e)}
        }
        override fun onRelease(){lease.close()}
    }
    init {
        try {
            descriptor=(context.getSystemService(Context.STORAGE_SERVICE) as StorageManager).openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,callback,Handler(thread.looper))
            renderer=PdfRenderer(descriptor!!)
        }catch(e:Throwable){close();throw e}
    }
    val pages:Int get()=synchronized(monitor){owner.publish{renderer!!.pageCount}}
    fun render(page:Int,width:Int=256):Bitmap=synchronized(monitor) {
        owner.check();check(bitmap==null);require(width in 1..1024)
        renderer!!.openPage(page).use { p ->
            val height=width.toLong()*p.height/p.width;require(height in 1..1024 && width*height*4<=4L*1024*1024)
            val result=Bitmap.createBitmap(width,height.toInt(),Bitmap.Config.ARGB_8888)
            try {p.render(result,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);beforeDelivery();beforeBitmapDelivery();owner.publish{bitmap=result;result}}
            catch(t:Throwable){result.recycle();rejectedBitmaps++;throw t}
        }
    }
    fun release(page:Bitmap)=synchronized(monitor){check(bitmap===page);page.recycle();bitmap=null}
    override fun close() {
        owner.close();lease.close()
        synchronized(monitor){bitmap?.recycle();bitmap=null
            try{renderer?.close()}finally{renderer=null;try{descriptor?.close()}finally{descriptor=null;thread.quitSafely()}}}
    }
}
