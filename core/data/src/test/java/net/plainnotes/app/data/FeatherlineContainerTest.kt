package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FeatherlineContainerTest {
    private fun bytes()=File("src/test/resources/featherline/v3-s7.cipher").readBytes()
    private fun password()="synthetic-only-password".toCharArray()
    private inline fun <reified T:Throwable> rejects(block:()->Unit){try{block();fail("Expected ${T::class.java.simpleName}")}catch(t:Throwable){if(t !is T)throw t}}
    @Test fun independentPythonVectorAuthenticatesExactlyAndClearsPassword() {
        val pw=password();FeatherlineContainer.read(bytes()).use {reader->
            val plain=reader.authenticate(pw)
            try {assertArrayEquals(File("src/test/resources/featherline/v3-s7.json").readBytes(),plain)}finally{plain.fill(0)}
        };assertTrue(pw.all{it=='\u0000'})
    }
    @Test fun wrongPasswordAndCiphertextOrTagTamperingNeverPublishPlaintext() {
        val wrong="wrong-password".toCharArray()
        FeatherlineContainer.read(bytes()).use{rejects<FeatherlineContainer.Authentication>{it.authenticate(wrong)}}
        assertTrue(wrong.all{it=='\u0000'})
        for(i in listOf(70,bytes().lastIndex)) {val b=bytes();b[i]=(b[i].toInt() xor 1).toByte()
            FeatherlineContainer.read(b).use{rejects<FeatherlineContainer.Authentication>{it.authenticate(password())}}}
    }
    @Test fun headerIsAuthenticatedAndHostileCostsAreRejectedBeforeKdf() {
        val salt=bytes();salt[37]=(salt[37].toInt() xor 1).toByte()
        FeatherlineContainer.read(salt).use{rejects<FeatherlineContainer.Authentication>{it.authenticate(password())}}
        for(index in listOf(7,8,9,10,22,26,30,34,35,36)) {
            val b=bytes();b[index]=(b[index].toInt() xor 0x40).toByte()
            rejects<FeatherlineContainer.Unsupported>{FeatherlineContainer.read(b)}
        }
        rejects<FeatherlineContainer.Invalid>{FeatherlineContainer.read(bytes().copyOf(64))}
        val huge=bytes();java.nio.ByteBuffer.wrap(huge).putLong(11,Int.MAX_VALUE.toLong())
        rejects<FeatherlineContainer.TooLarge>{FeatherlineContainer.read(huge)}
    }
    @Test fun cancellationClearsPasswordAndClosedInputCannotBeReused() {
        val pw=password();val reader=FeatherlineContainer.read(bytes())
        rejects<java.util.concurrent.CancellationException>{reader.authenticate(pw){throw java.util.concurrent.CancellationException()}}
        assertTrue(pw.all{it=='\u0000'});reader.close()
        rejects<IllegalStateException>{reader.authenticate(password())}
    }
    @Test fun authenticatedButInvalidOrExpandingGzipIsRejected() {
        FeatherlineContainer.read(File("src/test/resources/featherline/gzip-over-declared.cipher").readBytes()).use {
            rejects<FeatherlineContainer.TooLarge>{it.authenticate(password())}
        }
        FeatherlineContainer.read(File("src/test/resources/featherline/gzip-bad-crc.cipher").readBytes()).use {
            rejects<FeatherlineContainer.Invalid>{it.authenticate(password())}
        }
    }
}
