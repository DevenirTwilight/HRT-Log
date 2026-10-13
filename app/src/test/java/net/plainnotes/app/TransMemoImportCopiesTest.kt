package net.plainnotes.app

import kotlinx.coroutines.*
import net.plainnotes.app.privacy.TransMemoImportCopies as Copies
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.*
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TransMemoImportCopiesTest {
    @get:Rule val folder=TemporaryFolder()
    private fun files()=File(folder.root,"import").listFiles().orEmpty().filter{it.isFile}
    private fun failed(block:suspend()->Unit)=runBlocking {
        try {block();fail("Expected I/O failure")} catch(_:IOException){}
        assertTrue(files().isEmpty())
    }
    @Test fun materializedReturnClosesInputAndDeletesSidecars()=runBlocking {
        var closed=false
        val input=object:ByteArrayInputStream(byteArrayOf(1,2,3)){override fun close(){closed=true;super.close()}}
        val result=Copies.read(folder.root,{input}) { f ->
            listOf("-wal","-shm","-journal").forEach{File(f.path+it).writeText("synthetic")}
            f.readBytes()
        }
        assertArrayEquals(byteArrayOf(1,2,3),result);assertTrue(closed);assertTrue(files().isEmpty())
    }
    @Test fun interruptedCopyRemovesPartialFile()=failed {
        Copies.read(folder.root,{object:InputStream(){var reads=0;override fun read():Int {
            if(reads++>10)throw IOException("synthetic copy failure");return 1
        }}}){fail("Parser must not run")}
    }
    @Test fun closeFailureRemovesFile()=failed {
        Copies.read(folder.root,{object:ByteArrayInputStream(byteArrayOf(1)){override fun close(){throw IOException("synthetic close failure")}}}){fail("Parser must not run")}
    }
    @Test fun parserFailureRemovesFile()=failed {
        Copies.read(folder.root,{byteArrayOf(1).inputStream()}){throw IOException("synthetic parse failure")}
    }
    @Test fun missingInputRemovesFile()=failed {Copies.read(folder.root,{null}){fail("Parser must not run")}}
    @Test fun cancellationAndConcurrentReadAreOrderedAndUnique()=runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        var first:File?=null;var second:File?=null
        val one=launch(Dispatchers.IO) { Copies.read(folder.root,{byteArrayOf(1).inputStream()}) { f ->
            first=f;entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));assertTrue(f.exists())
        }}
        assertTrue(entered.await(10,TimeUnit.SECONDS))
        val two=async(Dispatchers.IO){Copies.read(folder.root,{byteArrayOf(2).inputStream()}){f ->
            second=f;assertFalse(first!!.exists());f.readBytes()
        }}
        one.cancel();release.countDown();one.join()
        assertArrayEquals(byteArrayOf(2),two.await());assertNotEquals(first,second);assertTrue(files().isEmpty())
    }
    @Test fun copyCancellationClosesInputAndAllowsNextRead()=runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);var closed=false
        val job=launch(Dispatchers.IO){Copies.read(folder.root,{object:InputStream(){override fun read():Int{
            entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return 1
        };override fun close(){closed=true}}}){fail("Cancelled read must not parse")}}
        assertTrue(entered.await(10,TimeUnit.SECONDS));job.cancel();release.countDown();job.join()
        assertTrue(closed);assertTrue(files().isEmpty())
        assertEquals(7,Copies.read(folder.root,{byteArrayOf(7).inputStream()}){it.readBytes()[0].toInt()})
    }
    @Test fun startupDeletesOnlyOwnedRegularFilesWithoutFollowingLinks()=runBlocking {
        val dir=File(folder.root,"import").apply{mkdir()}
        val unique="tm-import-12345678-1234-1234-1234-123456789abc.db"
        listOf("transmemo.db","transmemo.db-journal",unique,"$unique-wal","$unique-shm").forEach{File(dir,it).writeText("synthetic")}
        val unrelated=File(dir,"unrelated.db").apply{writeText("keep")}
        val nested=File(dir,"transmemo.db-wal").apply{mkdir()};File(nested,"keep").writeText("keep")
        val target=File(folder.root,"original.db").apply{writeText("original")}
        Files.createSymbolicLink(File(dir,"transmemo.db-shm").toPath(),target.toPath())
        assertEquals(Copies.CleanupState.CLEAN,Copies.startup({folder.root},true))
        assertEquals("keep",unrelated.readText());assertEquals("original",target.readText())
        assertTrue(Files.isSymbolicLink(File(dir,"transmemo.db-shm").toPath()));assertTrue(File(nested,"keep").exists())
        assertFalse(File(dir,unique).exists());assertFalse(File(dir,"transmemo.db").exists())
    }
    @Test fun lockedStartupDoesNotTouchCredentialDirectoryAndUnlockedRetries()=runBlocking {
        assertEquals(Copies.CleanupState.WAITING_FOR_UNLOCK,Copies.startup({error("Locked directory accessed")},false))
        assertEquals(Copies.CleanupState.CLEAN,Copies.startup({folder.root},true))
    }
    @Test fun symlinkDirectoryIsRejectedWithoutDeletingTarget()=runBlocking {
        val target=folder.newFolder("external");val original=File(target,"transmemo.db").apply{writeText("original")}
        Files.createSymbolicLink(File(folder.root,"import").toPath(),target.toPath())
        assertEquals(Copies.CleanupState.UNAVAILABLE,Copies.startup({folder.root},true));assertEquals("original",original.readText())
    }
}
