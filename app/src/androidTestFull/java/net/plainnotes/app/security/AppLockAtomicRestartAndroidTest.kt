package net.plainnotes.app.security

import android.os.Process
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.attachmentpoc.SyntheticEmulatorGuard
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Real Android 8 atomic-rename death and a fresh PID. Synthetic credential namespace only. */
class AppLockAtomicRestartAndroidTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val base get()=File(app.noBackupFilesDir,"security-f01.bin")
    private val backup get()=File(base.path+".bak")
    private fun lock()=AppLock(app,"security-f01.bin","notes.security.f01")
    @Test fun dieAfterActualAtomicBackupRename() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("securityPhase")=="f01-die")
        SyntheticEmulatorGuard.requireEmulator()
        lock().disable();lock().setPin("2468");assertTrue(lock().matches("2468"))
        File(app.filesDir,"security-f01-original.bin").writeBytes(base.readBytes())
        val fault=object:File(base.parentFile,base.name) {
            override fun renameTo(dest:File):Boolean {
                val renamed=super.renameTo(dest)
                if(renamed) {
                    File(app.filesDir,"security-f01-death.txt").outputStream().use {out->out.write(Process.myPid().toString().toByteArray());out.fd.sync()}
                    Process.killProcess(Process.myPid())
                    error("Process death returned")
                }
                return renamed
            }
        }
        // Executes the real platform primitive used by AppLock.setPin. No production death hook.
        AtomicFile(fault).startWrite()
        fail("Expected Android8 backup-rename seam")
    }
    @Test fun freshProcessRecoversAndFailureStaysLockedWithoutReplacingKey() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("securityPhase")=="f01-verify")
        SyntheticEmulatorGuard.requireEmulator()
        val old=File(app.filesDir,"security-f01-death.txt").readText().toInt()
        assertNotEquals(old,Process.myPid())
        assertFalse(base.exists());assertTrue(backup.exists())
        val original=File(app.filesDir,"security-f01-original.bin").readBytes()
        try {
            assertTrue(lock().enabled)
            assertTrue(Session.requiresAppPin(false,lock().enabled))
            assertFalse(lock().matches("2469"));assertTrue(lock().matches("2468"))
            assertArrayEquals(original,base.readBytes())
            val corrupt=original.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}
            base.writeBytes(corrupt)
            assertFalse(lock().matches("2468"));assertTrue(lock().enabled)
            assertArrayEquals(corrupt,base.readBytes())
            base.writeBytes(original)
            assertTrue("Authentication failure must not replace the existing Keystore key",lock().matches("2468"))
            // Simulate invalidation of this TEST-OWNED alias, never a user key.
            val keys=java.security.KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            keys.deleteEntry("notes.security.f01")
            assertFalse(lock().matches("2468"));assertTrue(lock().enabled)
            assertFalse("Authentication must not generate a replacement key",keys.containsAlias("notes.security.f01"))
            assertArrayEquals(original,base.readBytes())
            try {lock().setPin("1357");fail("Missing-key recovery silently replaced credentials")}
            catch(_:IllegalStateException) {}
            assertFalse(keys.containsAlias("notes.security.f01"));assertArrayEquals(original,base.readBytes())
        }finally {
            lock().disable();File(app.filesDir,"security-f01-original.bin").delete();File(app.filesDir,"security-f01-death.txt").delete()
        }
    }
}
