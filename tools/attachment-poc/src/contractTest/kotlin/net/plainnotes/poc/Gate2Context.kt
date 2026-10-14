package net.plainnotes.poc

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID

/** Candidate transcript only: no formal protocol magic, allocation or production parser. */
object Gate2Context {
    fun encode(vault:UUID,obj:UUID,version:UUID,attempt:UUID,handle:UUID,format:Int=0):ByteArray {
        require(format in 0..65535)
        return ByteArrayOutputStream().also { b->DataOutputStream(b).use { out->
            out.write("B2-SECURITY-GATE2-TEST-ONLY\u0000".toByteArray(Charsets.US_ASCII));out.writeShort(0);out.writeShort(1)
            for(u in listOf(vault,obj,version,attempt,handle)){out.writeLong(u.mostSignificantBits);out.writeLong(u.leastSignificantBits)}
            out.writeShort(format)
        }}.toByteArray()
    }
}
