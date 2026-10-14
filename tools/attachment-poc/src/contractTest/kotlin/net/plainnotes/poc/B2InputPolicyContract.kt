package net.plainnotes.poc

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/** Test-only admission contract: a signature is NOT parser validation or a trusted MIME. */
object B2InputPolicy {
    fun signature(bytes:ByteArray,declared:String):String {
        require(bytes.size<=64)
        val actual=when {
            bytes.size>=5&&bytes.copyOfRange(0,5).contentEquals("%PDF-".toByteArray())->"application/pdf"
            bytes.size>=8&&bytes.copyOfRange(0,8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))->"image/png"
            bytes.size>=3&&bytes[0]==(-1).toByte()&&bytes[1]==(-40).toByte()&&bytes[2]==(-1).toByte()->"image/jpeg"
            bytes.size>=12&&String(bytes.copyOfRange(0,4),Charsets.US_ASCII)=="RIFF"&&String(bytes.copyOfRange(8,12),Charsets.US_ASCII)=="WEBP"->"image/webp"
            else->throw IllegalArgumentException("Unsupported signature")
        }
        require(declared in setOf(actual,"application/octet-stream")) {"MIME conflict"}
        return actual
    }
    /** A user-supplied display name is shown/exported later, never used as a path: plain name, no separators,
     * control characters or dot-only names, at most 255 UTF-8 bytes. Not a statement about the file's origin. */
    fun displayName(name:String?):String {
        val n=name?.trim()
        require(n!=null&&n.isNotEmpty()&&n!="."&&n!=".."&&n.none{it=='/'||it=='\\'||it.code<0x20||it.code==0x7f}
            &&n.toByteArray(Charsets.UTF_8).size<=255){"Unsafe display name"}
        return n
    }
    fun pixels(width:Long,height:Long):Long {require(width in 1..10000&&height in 1..10000);val pixels=width*height;require(pixels<=20000000);return pixels}
    fun eligibleForProduct(parserVerified:Boolean,backupVerified:Boolean,ownerApproved:Boolean)=parserVerified&&backupVerified&&ownerApproved
}
abstract class B2InputPolicyContract {
    @Test fun pdfJpegPngWebpSignaturesAndMimeConflicts() {
        val fixtures=mapOf("application/pdf" to "%PDF-1.7\nsynthetic".toByteArray(),"image/png" to byteArrayOf(-119,80,78,71,13,10,26,10),
            "image/jpeg" to byteArrayOf(-1,-40,-1,0),"image/webp" to "RIFF0000WEBPsynthetic".toByteArray())
        for((mime,bytes) in fixtures){assertEquals(mime,B2InputPolicy.signature(bytes,mime));assertEquals(mime,B2InputPolicy.signature(bytes,"application/octet-stream"))
            try{B2InputPolicy.signature(bytes,"text/plain");fail("MIME mismatch accepted")}catch(_:IllegalArgumentException){}}
    }
    @Test fun fakePdfSignatureDoesNotAuthorizeLibraryOrPreview() {
        assertEquals("application/pdf",B2InputPolicy.signature("%PDF-fake".toByteArray(),"application/pdf"))
        assertFalse(B2InputPolicy.eligibleForProduct(false,true,true));assertFalse(B2InputPolicy.eligibleForProduct(true,false,true));assertFalse(B2InputPolicy.eligibleForProduct(true,true,false))
        try{B2InputPolicy.signature("not PDF".toByteArray(),"application/pdf");fail("Fake format accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun displayNamesArePlainNamesNeverPaths() {
        for(ok in listOf("synthetic.pdf","Bilan 2026-03.pdf","化验单 第1页.png","a..b.pdf","x".repeat(255)))assertEquals(ok,B2InputPolicy.displayName(ok))
        assertEquals("trimmed.pdf",B2InputPolicy.displayName("  trimmed.pdf "))
        for(bad in listOf(null,"","   ",".","..","../secret.pdf","dir/x.pdf","dir\\x.pdf","nul\u0000.pdf","tab\t.pdf","del\u007f.pdf","x".repeat(256),"化".repeat(86)))
            try{B2InputPolicy.displayName(bad);fail("Unsafe name accepted: $bad")}catch(e:IllegalArgumentException){assertEquals("Unsafe display name",e.message)}
    }
    @Test fun highCompressionDimensionsAreRejectedBeforeDecodeWithoutChangingOriginal() {
        val original=ByteBuffer.allocate(8).putInt(100000).putInt(100000).array();val snapshot=original.copyOf()
        val bounds=ByteBuffer.wrap(original)
        try{B2InputPolicy.pixels(bounds.int.toLong(),bounds.int.toLong());fail("Bomb dimensions accepted")}catch(_:IllegalArgumentException){}
        try{B2InputPolicy.pixels(10000,10000);fail("Pixel limit exceeded")}catch(_:IllegalArgumentException){}
        assertEquals(20000000L,B2InputPolicy.pixels(5000,4000));assertArrayEquals(snapshot,original)
    }
}
