package net.plainnotes.app.attachments

import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import net.plainnotes.app.attachmentpoc.SyntheticEmulatorGuard
import java.io.ByteArrayOutputStream

/** Test APK only, protected by MANAGE_DOCUMENTS; no filesystem or patient data. */
class SyntheticDocumentsProvider:DocumentsProvider() {
    override fun onCreate():Boolean {SyntheticEmulatorGuard.requireEmulator();return true}
    override fun queryRoots(projection:Array<out String>?):Cursor=MatrixCursor(projection ?: ROOTS).apply {
        val r=newRow();for(c in columnNames)r.add(c,when(c) {Root.COLUMN_ROOT_ID->"samples";Root.COLUMN_DOCUMENT_ID->"samples";Root.COLUMN_TITLE->"SYNTHETIC MED-02";Root.COLUMN_MIME_TYPES->"*/*";Root.COLUMN_FLAGS->Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD;Root.COLUMN_AVAILABLE_BYTES->32L*1024*1024;else->null})
    }
    override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor=MatrixCursor(projection ?: DOCS).apply {row(this,documentId)}
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor {
        require(parentDocumentId=="samples");return MatrixCursor(projection ?: DOCS).apply {listOf("pdf","png","binary").forEach {row(this,it)}}
    }
    override fun isChildDocument(parentDocumentId:String,documentId:String)=parentDocumentId=="samples" && documentId in listOf("pdf","png","binary")
    override fun findDocumentPath(parentDocumentId:String?,childDocumentId:String):android.provider.DocumentsContract.Path {
        require(parentDocumentId==null || parentDocumentId=="samples")
        require(childDocumentId in listOf("samples","pdf","png","binary"))
        return android.provider.DocumentsContract.Path(if(parentDocumentId==null)"samples" else null,
            if(childDocumentId=="samples")listOf("samples") else listOf("samples",childDocumentId))
    }
    private fun row(c:MatrixCursor,id:String) {
        require(id in listOf("samples","pdf","png","binary"));val r=c.newRow()
        for(name in c.columnNames)r.add(name,when(name) {
            Document.COLUMN_DOCUMENT_ID->id
            Document.COLUMN_DISPLAY_NAME->when(id) {"samples"->"SYNTHETIC samples";"pdf"->"SYNTHETIC sample.pdf";"png"->"SYNTHETIC sample.png";else->"SYNTHETIC sample.bin"}
            // Intentionally wrong MIME and length: actual pipeline must classify/bound real bytes.
            Document.COLUMN_MIME_TYPE->if(id=="samples")Document.MIME_TYPE_DIR else "application/octet-stream"
            Document.COLUMN_SIZE->if(id=="samples")null else 1L
            Document.COLUMN_FLAGS->0
            else->null
        })
    }
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
        require(mode=="r");signal?.throwIfCanceled();val bytes=sample(documentId)
        val pipe=ParcelFileDescriptor.createReliablePipe()
        Thread {try {ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use {it.write(bytes)}}finally {bytes.fill(0)}}.apply {isDaemon=true;start()}
        return pipe[0]
    }
    companion object {
        val ROOTS=arrayOf(Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,Root.COLUMN_MIME_TYPES,Root.COLUMN_FLAGS,Root.COLUMN_AVAILABLE_BYTES)
        val DOCS=arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_FLAGS)
        private fun pdf():ByteArray {
            val content="BT /F1 12 Tf 5 100 Td (SYNTHETIC ONLY) Tj ET\n"
            val objects=listOf("<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 160] /Resources << /Font << /F1 7 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 160] /Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >>",
                "<< /Length ${content.length} >>\nstream\n${content}endstream","<< /Length ${content.length} >>\nstream\n${content}endstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
            val out=StringBuilder("%PDF-1.4\n");val offsets=mutableListOf(0)
            objects.forEachIndexed {i,o->offsets+=out.length;out.append("${i+1} 0 obj\n$o\nendobj\n")}
            val start=out.length;out.append("xref\n0 ${objects.size+1}\n0000000000 65535 f \n")
            offsets.drop(1).forEach {out.append(it.toString().padStart(10,'0')).append(" 00000 n \n")}
            out.append("trailer\n<< /Size ${objects.size+1} /Root 1 0 R >>\nstartxref\n$start\n%%EOF\n")
            return out.toString().toByteArray(Charsets.US_ASCII)
        }
        fun sample(id:String):ByteArray=when(id) {
            "binary"->ByteArray(8192) {((it*31+17)%251).toByte()}
            "png"->ByteArrayOutputStream().also {out->val b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);try {b.eraseColor(Color.BLUE);check(b.compress(Bitmap.CompressFormat.PNG,100,out))}finally {b.recycle()}}.toByteArray()
"pdf"->pdf()
            else->error("Synthetic sample only")
        }
    }
}
