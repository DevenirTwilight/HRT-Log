package net.plainnotes.app.imports

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import net.plainnotes.app.importer.ImportFormatDetector as Detector
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=android.app.Application::class)
class AutomaticImportReaderTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun clean()=assertTrue(File(context.noBackupFilesDir,"import").listFiles().orEmpty().none{it.isFile})
    @Test fun oneProviderOpenUsesOnlyValidatedSnapshot()=runBlocking {
        val bytes=File("../importer/src/test/resources/hrttracker_v2_synthetic.json").readBytes()
        var opens=0;var closes=0;val sources=mutableListOf<Detector.Source>()
        val result=AutomaticImportReader.read(context.noBackupFilesDir,{
            opens++;object:ByteArrayInputStream(if(opens==1)bytes else "different".toByteArray()) {override fun close(){closes++;super.close()}}
        },{},sources::add)
        assertTrue(result is Detector.Parsed.Tracker);assertEquals(1,opens);assertEquals(1,closes)
        assertEquals(listOf(Detector.Source.HRT_TRACKER),sources);clean()
    }
    @Test fun realSqliteReadOnlyCopyIsRemovedBeforePreview()=runBlocking {
        val file=File.createTempFile("synthetic", ".json",context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(file,null).use {db->
                File("../importer/src/test/resources/transmemo_v8_synthetic.sql").readText().split(";\n")
                    .map{it.lines().filterNot{l->l.trim().startsWith("--")}.joinToString("\n").trim()}
                    .filter{it.isNotEmpty()&&!it.contains("android_metadata")}.forEach(db::execSQL)
            }
            val original=file.readBytes();var opens=0
            val result=AutomaticImportReader.read(context.noBackupFilesDir,{opens++;file.inputStream()},{},{}) as Detector.Parsed.Memo
            assertEquals(4,result.export.products.size);assertEquals(1,opens);assertArrayEquals(original,file.readBytes());clean()
        }finally{file.delete()}
    }
    @Test fun unsupportedUnknownDamagedAndOversizeRejectWithoutCopies()=runBlocking {
        suspend fun fails(type:Class<out Throwable>,bytes:ByteArray) {
            try {AutomaticImportReader.read(context.noBackupFilesDir,{bytes.inputStream()},{},{});fail("must refuse")}
            catch(e:Throwable){assertTrue("Expected ${type.simpleName}, actual ${e.javaClass.simpleName}",type.isInstance(e))}
            clean()
        }
        fails(Detector.Unsupported::class.java,"HRTBKP1not-a-native-backup".toByteArray())
        fails(Detector.Unknown::class.java,"unknown.json".toByteArray())
        fails(Detector.Damaged::class.java,"{broken".toByteArray())
        fails(Detector.Damaged::class.java,"SQLite format 3\u0000broken".toByteArray())
        fails(Detector.TooLarge::class.java,ByteArray(AutomaticImportReader.MAXIMUM_BYTES+1){if(it==0)'{'.code.toByte() else ' '.code.toByte()})
    }
    @Test fun cancellationAndRevocationCloseSourceAndNeverReturnPreview()=runBlocking {
        var closed=false;var checks=0
        val job=launch(Dispatchers.Default) {
            try {AutomaticImportReader.read(context.noBackupFilesDir,{
                object:InputStream(){override fun read()=123;override fun close(){closed=true}}
            },{if(++checks==8)throw CancellationException("synthetic revoke")},{})
                fail("revoked read must not return")
            }catch(_:CancellationException){}
        }
        job.join();assertTrue(closed);clean()
    }
}
