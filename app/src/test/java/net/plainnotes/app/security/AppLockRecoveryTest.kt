package net.plainnotes.app.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[26,27],application=android.app.Application::class)
class AppLockRecoveryTest {
    @Test fun backupOnlyAndCorruptCredentialsNeverDisableTheLock() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="synthetic-lock-${UUID.randomUUID()}"
        val base=File(context.noBackupFilesDir,name);val backup=File(base.path+".bak")
        val lock=AppLock(context,name,name)
        try {
            assertFalse(lock.enabled)
            backup.writeBytes(byteArrayOf(1,2,3))
            assertTrue(lock.enabled)
            assertFalse(lock.matches("2468"))
            assertTrue(lock.enabled)
            assertTrue(base.exists() || backup.exists())
        }finally {base.delete();backup.delete()}
    }
}
