package net.plainnotes.app.attachmentpoc

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Collections

/** Valid one-page PDF with a large synthetic comment stream; generated without a whole-file buffer. */
object LargeSyntheticPdf {
    fun input(padding:Int=4*1024*1024):InputStream {
        val prefix=StringBuilder("%PDF-1.4\n")
        val offsets=mutableListOf<Int>()
        fun obj(id:Int,body:String){offsets+=prefix.length;prefix.append("$id 0 obj\n$body\nendobj\n")}
        obj(1,"<< /Type /Catalog /Pages 2 0 R >>")
        obj(2,"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        obj(3,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 320 480] /Resources << >> /Contents 4 0 R >>")
        val paint="q 1 1 1 rg 0 0 320 480 re f Q\n%"
        offsets+=prefix.length;prefix.append("4 0 obj\n<< /Length ${paint.length+padding+1} >>\nstream\n$paint")
        val endStream="\nendstream\nendobj\n"
        val xref=prefix.length+padding+endStream.length
        val tail=buildString {
            append(endStream);append("xref\n0 5\n0000000000 65535 f \n")
            offsets.forEach{append(String.format(java.util.Locale.ROOT,"%010d 00000 n \n",it))}
            append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
        val filler=object:InputStream(){var left=padding
            override fun read():Int=if(left--<=0)-1 else 'X'.code
            override fun read(b:ByteArray,off:Int,len:Int):Int{if(left==0)return -1;val n=minOf(left,len);b.fill('X'.code.toByte(),off,off+n);left-=n;return n}
        }
        return SequenceInputStream(Collections.enumeration(listOf(ByteArrayInputStream(prefix.toString().toByteArray(Charsets.US_ASCII)),filler,ByteArrayInputStream(tail.toByteArray(Charsets.US_ASCII)))))
    }
}
