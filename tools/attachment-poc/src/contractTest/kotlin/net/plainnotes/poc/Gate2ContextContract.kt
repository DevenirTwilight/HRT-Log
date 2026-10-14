package net.plainnotes.poc
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
abstract class Gate2ContextContract {
    private val ids=(1..5).map{UUID.fromString("00000000-0000-4000-8000-00000000000$it")}
    @Test fun fixedBigEndianContextMatchesIndependentPythonUuidTranscript() {
        val expected="42322d53454355524954592d47415445322d544553542d4f4e4c59000000000100000000000040008000000000000001000000000000400080000000000000020000000000004000800000000000000300000000000040008000000000000004000000000000400080000000000000050000".chunked(2).map{it.toInt(16).toByte()}.toByteArray()
        assertArrayEquals(expected,Gate2Context.encode(ids[0],ids[1],ids[2],ids[3],ids[4]))
        assertFalse(expected.contentEquals(Gate2Context.encode(ids[1],ids[0],ids[2],ids[3],ids[4])))
    }
    @Test fun invalidFormatCannotSilentlyTruncateUnsignedEncoding() {
        assertThrows(IllegalArgumentException::class.java){Gate2Context.encode(ids[0],ids[1],ids[2],ids[3],ids[4],65536)}
        assertThrows(IllegalArgumentException::class.java){Gate2Context.encode(ids[0],ids[1],ids[2],ids[3],ids[4],-1)}
    }
}
