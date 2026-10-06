package net.plainnotes.app.disguise

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.disguise.privatenotes.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class PrivateStoreTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private class Keys:PrivateKeys {
        var key:SecretKey?=null
        override fun get(create:Boolean):SecretKey=key ?: if(create)SecretKeySpec(ByteArray(32){it.toByte()},"AES").also{key=it} else error("Unavailable")
        override fun destroy(){key=null}
    }
    private lateinit var keys:Keys
    private lateinit var store:PrivateStore
    private val file get()=File(context.noBackupFilesDir,PrivateStore.FILE_NAME)
    @Before fun setup(){keys=Keys();store=PrivateStore(context,keys);store.destroy()}
    @After fun cleanup(){store.destroy()}
    @Test fun emptyThenCreateEditReopenAndDeletePreservesOriginalIdentity() {
        assertTrue(store.list().isEmpty())
        val original=store.save(null,"Shopping","Milk, coffee",100)
        val edited=store.save(original.id,"Travel","Charger, passport",200)
        assertEquals(original.id,edited.id);assertEquals(100,edited.createdAt);assertEquals(200,edited.updatedAt)
        assertEquals(listOf(edited),PrivateStore(context,keys).list())
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("passport"))
        store.delete(edited.id);assertTrue(store.list().isEmpty())
    }
    @Test fun deletingPrivateStorageNeverTouchesOtherFilesAndRemovesAtomicSidecars() {
        val unrelated=File(context.noBackupFilesDir,"synthetic-primary-sentinel").apply{writeText("unchanged")}
        try {
            store.save(null,"Shopping","Milk")
            File(file.path+".bak").writeBytes(file.readBytes());File(file.path+".new").writeText("synthetic incomplete")
            store.destroy();assertNull(keys.key)
            listOf("",".bak",".new").forEach{assertFalse(File(file.path+it).exists())}
            assertEquals("unchanged",unrelated.readText());assertTrue(store.list().isEmpty())
        }finally{unrelated.delete()}
    }
    @Test fun missingKeyAndCorruptCiphertextDoNotBecomeAnEmptyStoreOrOverwriteData() {
        store.save(null,"Travel","Passport")
        val before=file.readBytes();keys.key=null
        assertTrue(runCatching{store.list()}.isFailure);assertTrue(runCatching{store.save(null,"Other","Text")}.isFailure)
        assertArrayEquals(before,file.readBytes())
        keys.get(true);val bytes=file.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
        assertTrue(runCatching{store.list()}.isFailure);assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun independentInstancesSerializeConcurrentReadModifyWrite() {
        val second=PrivateStore(context,keys)
        val threads=(1..12).map{i->Thread{(if(i%2==0)second else store).save(null,"Note $i","Ordinary text")}.apply{start()}}
        threads.forEach{it.join()};assertEquals(12,store.list().size);assertEquals(12,store.list().map{it.id}.distinct().size)
    }
}
