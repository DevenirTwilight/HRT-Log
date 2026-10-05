package net.plainnotes.app.data
import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) class KeyBoundaryTest {
    @Test fun lockedBootCannotOpenCredentialDatabase(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        try{DatabaseAccess(context).get();fail("Expected locked storage")}catch(_:DataLockedException){}
        assertFalse(context.getDatabasePath("notes.db").exists())
    }
    @Test fun existingDatabaseWithoutKeyIsNeverReplaced(){
        val context=ApplicationProvider.getApplicationContext<Context>();shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        val file=context.getDatabasePath("notes.db");file.parentFile!!.mkdirs();val original=byteArrayOf(1,2,3,4);file.writeBytes(original)
        try{DatabaseAccess(context).get();fail("Expected recovery error")}catch(_:Exception){}
        assertArrayEquals(original,file.readBytes())
    }
}
