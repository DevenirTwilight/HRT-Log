package net.plainnotes.app.attachmentpoc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import net.plainnotes.poc.ChunkObject
import java.io.InputStream

/** Instrumentation-only, original encrypted bytes remain unchanged; no disk bitmap/thumbnail cache. */
object ImagePoc {
    private fun stream(r:ChunkObject.Reader)=object:InputStream(){
        var at=0L
        override fun read():Int{val b=ByteArray(1);return if(read(b,0,1)==-1)-1 else b[0].toInt() and 255}
        override fun read(b:ByteArray,off:Int,len:Int):Int{
            if(len==0)return 0
            val limited=minOf(len,65536);val scratch=ByteArray(limited)
            return try{val n=r.readAt(at,scratch,limited);if(n==0)-1 else{scratch.copyInto(b,off,0,n);at+=n;n}}finally{scratch.fill(0)}
        }
    }
    fun identify(header:ByteArray):String = when {
        header.size>=8 && header.copyOf(8).contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))->"image/png"
        header.size>=3 && header[0]==255.toByte() && header[1]==216.toByte() && header[2]==255.toByte()->"image/jpeg"
        header.size>=12 && header.copyOf(4).toString(Charsets.US_ASCII)=="RIFF" && header.copyOfRange(8,12).toString(Charsets.US_ASCII)=="WEBP"->"image/webp"
        else->error("Unsupported synthetic image magic")
    }
    fun decode(owned:ChunkObject.Owned,mime:String,edge:Int=512):Bitmap=owned.open().use{r->
        require(edge in 1..1024)
        val header=ByteArray(minOf(12,r.size.toInt()));r.readAt(0,header);require(identify(header)==mime)
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        stream(r).use{BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth<=32768 && bounds.outHeight<=32768)
        require(bounds.outWidth.toLong()*bounds.outHeight<=32L*1024*1024) // Reject before allocation.
        var sample=1
        while(bounds.outWidth/sample>edge || bounds.outHeight/sample>edge)sample*=2
        val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888}
        val bitmap=stream(r).use{BitmapFactory.decodeStream(it,null,options)}?:error("Synthetic image decode failed")
        if(bitmap.width>edge || bitmap.height>edge || bitmap.allocationByteCount>4*1024*1024){bitmap.recycle();error("Synthetic image bound exceeded")}
        bitmap
    }
}
