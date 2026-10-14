package net.plainnotes.app.attachmentpoc

import android.os.Process
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.poc.ChunkObject
import net.plainnotes.poc.KeyEnvelope
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/** Only this synthetic directory/alias. External prepare intentionally blocks until actual force-stop. */
class AttachmentRestartAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val root get()=File(context.filesDir,"synthetic-med02a-restart")
    private val alias="synthetic-med02a-restart-key"
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    @Test fun interruptedSyntheticImport() {
        SyntheticEmulatorGuard.requireEmulator()
        when(InstrumentationRegistry.getArguments().getString("med02a_phase")) {
            "prepare"->prepare(true)
            "verify"->verify(true)
            null->{prepare(false);verify(false)}
            else->error("Unknown synthetic phase")
        }
    }
    private fun prepare(blockUntilKilled:Boolean) {
        root.deleteRecursively();check(root.mkdirs());store().deleteEntry(alias)
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        val key=generator.generateKey()
        LargeSyntheticPdf.input(1024).use{input->ChunkObject.write(root,ChunkObject.Identity.fresh(),input,1024).use{o->
            File(root,"object-state").writeText(listOf(o.file.name,o.identity.store,o.identity.objectId,o.identity.version).joinToString("\n"))
            val dek=o.syntheticKey();try{File(root,"key-envelope").writeBytes(KeyEnvelope.wrap(key,"synthetic-restart".toByteArray(),dek))}finally{dek.fill(0)}
            if(!blockUntilKilled){
                // Ordinary suite fixture: simulate a crashed partial without leaving any plaintext file.
                File(root,"${UUID.randomUUID()}.part").writeBytes(o.file.readBytes().copyOf(1100))
            }
        }}
        File(root,"sentinel").writeText("Synthetic unrelated sentinel")
        if(blockUntilKilled) {
            val blocked=CountDownLatch(1)
            val forever=CountDownLatch(1)
            val source=object:InputStream(){var calls=0
                override fun read()=error("Use bounded bulk reads")
                override fun read(b:ByteArray,off:Int,len:Int):Int {
                    if(++calls==3){blocked.countDown();forever.await()}
                    b.fill(7,off,off+len);return len
                }
            }
            Thread({ChunkObject.write(root,ChunkObject.Identity.fresh(),source,1024)},"synthetic-live-import").apply{isDaemon=true;start()}
            check(blocked.await(15,TimeUnit.SECONDS))
            check(root.listFiles()!!.any{it.extension=="part" && it.length()>ChunkObject.HEADER})
            File(root,"ready").writeText(Process.myPid().toString())
            forever.await() // Python kills this live process, not a finished test.
        }else File(root,"ready").writeText(Process.myPid().toString())
    }
    private fun verify(fresh:Boolean) {
        if(fresh)assertNotEquals(File(root,"ready").readText().toInt(),Process.myPid())
        try {
            val state=File(root,"object-state").readLines();val identity=ChunkObject.Identity(UUID.fromString(state[1]),UUID.fromString(state[2]),UUID.fromString(state[3]))
            val key=store().getKey(alias,null) as SecretKey
            val dek=KeyEnvelope.unwrap(key,"synthetic-restart".toByteArray(),File(root,"key-envelope").readBytes())
            try {
                val partial=root.listFiles()!!.filter{it.extension=="part"};assertTrue(partial.isNotEmpty())
                for(f in partial){assertThrows(Exception::class.java){ChunkObject.Reader(f,dek,identity).close()};assertEquals("SYNPOC01",f.readBytes().copyOf(8).toString(Charsets.US_ASCII));assertTrue(f.delete())}
                ChunkObject.Reader(File(root,state[0]),dek,identity).use{r->val header=ByteArray(5);assertEquals(5,r.readAt(0,header));assertEquals("%PDF-",header.toString(Charsets.US_ASCII))}
                assertEquals("Synthetic unrelated sentinel",File(root,"sentinel").readText());assertTrue(root.listFiles()!!.none{it.extension=="part"})
                Log.i("MED02APOC","restart api=${android.os.Build.VERSION.SDK_INT} fresh_pid=${if(fresh)1 else 0} partial_count=${partial.size} old_object_retained=1 plaintext_pdf_files=0")
            }finally{dek.fill(0)}
        }finally{root.deleteRecursively();store().deleteEntry(alias)}
    }
}
