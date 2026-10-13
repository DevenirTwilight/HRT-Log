package net.plainnotes.app

import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import net.plainnotes.app.privacy.BackupHistory
import net.plainnotes.app.privacy.BackupWriter
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class BackupHistoryTest {
    private class MutableClock(var time:Instant):Clock(){override fun instant()=time;override fun getZone()=ZoneOffset.UTC;override fun withZone(zone:ZoneId)=Clock.fixed(time,zone)}
    private lateinit var prefs:SharedPreferences
    private lateinit var clock:MutableClock
    private lateinit var history:BackupHistory
    private lateinit var writer:BackupWriter
    private val old=Instant.parse("2026-01-01T00:00:00Z")
    @Before fun setup(){prefs=ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("b2-synthetic-history",0);prefs.edit().clear().commit();clock=MutableClock(old.plusSeconds(86400));history=BackupHistory(prefs,clock);writer=BackupWriter(history)}
    @After fun cleanup(){prefs.edit().clear().commit()}
    private fun seed(){prefs.edit().putLong(BackupHistory.LAST,old.toEpochMilli()).commit();history.refresh()}
    private val encrypt:suspend(CharArray)->ByteArray={byteArrayOf(1,2,3)}
    private fun failed(encryption:suspend(CharArray)->ByteArray=encrypt,output:()->OutputStream?){
        seed();val pw="synthetic-only-password".toCharArray()
        try{runBlocking{writer.write(pw,encryption,output)};fail("Expected export failure")}catch(_:IOException){}
        assertEquals("A failed export must preserve the prior timestamp",old,history.state.value.lastExport)
        assertTrue("Submitted password must be cleared",pw.all{it=='\u0000'})
    }
    @Test fun defaultIsOffAndNeverRecorded(){assertNull(history.state.value.lastExport);assertFalse(history.state.value.reminderEnabled);assertFalse(history.due())}
    @Test fun completedWriteRecordsOnlyAfterNormalCloseAndClearsSecretEarly()=runBlocking {
        seed();val pw="synthetic-only-password".toCharArray();var closed=false
        writer.write(pw,encrypt){object:ByteArrayOutputStream(){override fun write(b:ByteArray,off:Int,len:Int){
            assertEquals("No success timestamp before normal close",old,history.state.value.lastExport)
            assertTrue("Encryption password must not wait for the provider",pw.all{it=='\u0000'});super.write(b,off,len)
        };override fun close(){assertEquals("No success timestamp before normal close",old,history.state.value.lastExport);closed=true;super.close()}}}
        assertTrue(closed);assertEquals(clock.time,history.state.value.lastExport)
        assertEquals(clock.time.toEpochMilli(),prefs.getLong(BackupHistory.LAST,0))
    }
    @Test fun aSecondSuccessfulExportReplacesTheTime()=runBlocking {
        writer.write("synthetic-only-password".toCharArray(),encrypt){ByteArrayOutputStream()};clock.time=clock.time.plusSeconds(60)
        writer.write("synthetic-only-password".toCharArray(),encrypt){ByteArrayOutputStream()};assertEquals(clock.time,history.state.value.lastExport)
    }
    @Test fun missingOutputDoesNotRecord()=failed(output={null})
    @Test fun deniedOutputDoesNotRecord()=failed(output={throw IOException("Synthetic denied output")})
    @Test fun partialWriteDoesNotRecord()=failed(output={object:OutputStream(){override fun write(b:Int){throw IOException("Synthetic partial write")}}})
    @Test fun closeFailureDoesNotRecord()=failed(output={object:ByteArrayOutputStream(){override fun close(){throw IOException("Synthetic close failure")}}})
    @Test fun encryptionFailureDoesNotRecord()=failed(encryption={throw IOException("Synthetic encryption failure")},output={fail("No output may open");null})
    @Test fun cancellationAtCloseDoesNotRecordAndClearsSecret()=runBlocking {
        seed();val pw="synthetic-only-password".toCharArray()
        val job=launch {val active=currentCoroutineContext();writer.write(pw,encrypt){object:ByteArrayOutputStream(){override fun close(){active.cancel();super.close()}}}}
        job.join();assertTrue(job.isCancelled);assertEquals(old,history.state.value.lastExport);assertTrue(pw.all{it=='\u0000'})
    }
    @Test fun metadataCommitFailureCannotBecomeASuccess() {
        seed()
        val refusing=object:SharedPreferences by prefs{override fun edit():SharedPreferences.Editor {
            val delegate=prefs.edit();return object:SharedPreferences.Editor by delegate{
                override fun putLong(key:String,value:Long):SharedPreferences.Editor {delegate.putLong(key,value);return this}
                override fun remove(key:String):SharedPreferences.Editor {delegate.remove(key);return this}
                override fun commit():Boolean{delegate.commit();return false}
            }
        }}
        val h=BackupHistory(refusing,clock);val pw="synthetic-only-password".toCharArray()
        try{runBlocking{BackupWriter(h).write(pw,encrypt){ByteArrayOutputStream()}};fail("Metadata failure must surface")}catch(_:IOException){}
        assertEquals(old,h.state.value.lastExport);assertEquals(old.toEpochMilli(),prefs.getLong(BackupHistory.LAST,0));assertTrue(pw.all{it=='\u0000'})
    }
    @Test fun remindersHaveDeterministicIntervalsAndClearAfterSuccess()=runBlocking {
        history.configure(true,7);assertTrue(history.due());seed();assertFalse(history.due())
        clock.time=old.plusSeconds(7*86400);assertTrue(history.due())
        history.configure(true,14);assertFalse(history.due());clock.time=old.plusSeconds(14*86400);assertTrue(history.due())
        history.configure(true,30);assertFalse(history.due());clock.time=old.plusSeconds(30*86400);assertTrue(history.due())
        writer.write("synthetic-only-password".toCharArray(),encrypt){ByteArrayOutputStream()};assertFalse(history.due())
        history.configure(false,30);clock.time=clock.time.plusSeconds(60*86400);assertFalse(history.due())
    }
    @Test fun clockRollbackDoesNotMakeFutureExportsOverdue(){seed();history.configure(true,7);clock.time=old.minusSeconds(86400);assertFalse(history.due())}
    @Test fun cancellationDuringMetadataCommitRollsBackBeforeReturning()=runBlocking {
        seed();var cancel:()->Unit={}
        val cancelling=object:SharedPreferences by prefs{override fun edit():SharedPreferences.Editor{
            val delegate=prefs.edit();return object:SharedPreferences.Editor by delegate{
                override fun putLong(key:String,value:Long):SharedPreferences.Editor{delegate.putLong(key,value);return this}
                override fun remove(key:String):SharedPreferences.Editor{delegate.remove(key);return this}
                override fun commit():Boolean{val ok=delegate.commit();cancel();return ok}
            }
        }}
        val h=BackupHistory(cancelling,clock);val pw="synthetic-only-password".toCharArray()
        val job=launch{val active=currentCoroutineContext();cancel={active.cancel()};BackupWriter(h).write(pw,encrypt){ByteArrayOutputStream()}}
        job.join();assertTrue(job.isCancelled);assertEquals(old,h.state.value.lastExport);assertTrue(pw.all{it=='\u0000'})
    }
    @Test fun UTCStorageDoesNotChangeWithClockZone()=runBlocking {
        val instant=Instant.parse("2026-01-01T00:15:00Z")
        val h=BackupHistory(prefs,Clock.fixed(instant,ZoneId.of("America/New_York")))
        BackupWriter(h).write("synthetic-only-password".toCharArray(),encrypt){ByteArrayOutputStream()}
        assertEquals(instant.toEpochMilli(),prefs.getLong(BackupHistory.LAST,0))
        assertEquals(LocalDate.of(2025,12,31),h.state.value.lastExport!!.atZone(ZoneId.of("America/New_York")).toLocalDate())
        assertEquals(LocalDate.of(2026,1,1),h.state.value.lastExport!!.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())
    }
    @Test fun clearRemovesAllBackupMetadata(){seed();history.configure(true,7);history.clear();assertNull(history.state.value.lastExport);assertFalse(history.state.value.reminderEnabled);assertFalse(prefs.contains(BackupHistory.LAST))}
    @Test fun clearingDuringWriteCannotResurrectHistory()=runBlocking {
        seed();val pw="synthetic-only-password".toCharArray()
        try{writer.write(pw,encrypt){object:ByteArrayOutputStream(){override fun close(){history.clear();super.close()}}};fail("Superseded export must not record")}catch(_:IOException){}
        assertNull(history.state.value.lastExport);assertTrue(pw.all{it=='\u0000'})
    }
}
