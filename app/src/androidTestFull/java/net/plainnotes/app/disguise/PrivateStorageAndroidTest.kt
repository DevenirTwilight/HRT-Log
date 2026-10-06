package net.plainnotes.app.disguise

import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.disguise.privatenotes.PrivateStore
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.KeyStore

/** Actual Android Keystore, authenticated encryption and AtomicFile, with synthetic ordinary notes only. */
class PrivateStorageAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val store get()=PrivateStore(context)
    private val file get()=File(context.noBackupFilesDir,PrivateStore.FILE_NAME)
    @Before fun setup(){store.destroy()}
    @After fun cleanup(){store.destroy()}
    @Test fun createEditReopenAndDeleteUseTheIndependentEncryptedFile() {
        val a=store.save(null,"Shopping","Milk, coffee",100)
        val b=store.save(null,"Travel","Passport, headphones",200)
        assertEquals(2,PrivateStore(context).list().size)
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("Passport"))
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("Shopping"))
        val edited=store.save(a.id,"Shopping","Milk, detergent",300)
        assertEquals(100L,edited.createdAt);assertEquals(300L,edited.updatedAt)
        assertEquals(edited,store.list().first());store.delete(b.id);assertEquals(listOf(edited),store.list())
    }
    @Test fun erasingTheSpaceDestroysItsKeyAndLeavesUnrelatedDataAlone() {
        val other=File(context.noBackupFilesDir,"synthetic-unrelated-file").apply{writeText("unchanged")}
        try {
            store.save(null,"Travel","Charger")
            assertTrue(KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.containsAlias(PrivateStore.KEY_ALIAS))
            store.destroy();assertFalse(file.exists())
            assertFalse(KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.containsAlias(PrivateStore.KEY_ALIAS))
            assertEquals("unchanged",other.readText())
        }finally{other.delete()}
    }
    @Test fun missingKeyAndTamperedCiphertextFailWithoutReplacingExistingNotes() {
        store.save(null,"Travel","Passport")
        val original=file.readBytes()
        val changed=original.copyOf().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()}
        file.writeBytes(changed);assertTrue(runCatching{store.list()}.isFailure);assertArrayEquals(changed,file.readBytes())
        file.writeBytes(original)
        KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry(PrivateStore.KEY_ALIAS)
        assertTrue(runCatching{store.list()}.isFailure);assertTrue(runCatching{store.save(null,"Other","Note")}.isFailure)
        assertArrayEquals(original,file.readBytes())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.containsAlias(PrivateStore.KEY_ALIAS))
    }
}
