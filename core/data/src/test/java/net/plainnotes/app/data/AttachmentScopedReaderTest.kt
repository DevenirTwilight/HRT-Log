package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.domain.AttachmentSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real Room epochs and unchanged Tink primitive; only ciphertext is written to a private fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class AttachmentScopedReaderTest {
    private fun worker(block:()->Unit) {
        val pool=Executors.newSingleThreadExecutor()
        try {pool.submit(Callable {block()}).get(30,TimeUnit.SECONDS)} finally {pool.shutdownNow()}
    }
    private class Fixture:AutoCloseable {
        private val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).addCallback(SchemaGuards).build()
        val raw get()=db.openHelper.writableDatabase
        val scope:AttachmentIdentity.Scope
        val session:AttachmentSession
        val bytes=ByteArray(131091) {(it*7).toByte()}
        private val file=File.createTempFile("synthetic-epoch-",".cipher",context.cacheDir)
        var onRead:(()->Unit)?=null
        lateinit var channel:SeekableByteChannel
        lateinit var crypto:AttachmentObjectStreams.Reader
        init {
            raw.beginTransaction();try {scope=requireNotNull(AttachmentIdentity.initialize(raw,true));raw.setTransactionSuccessful()} finally {raw.endTransaction()}
            session=AttachmentSession(UUID.fromString(scope.dataset),UUID.fromString(scope.epoch))
        }
        fun reader():AttachmentScopedReader {
            val cap=session.issue()
            val content=AttachmentObjectContext(UUID.fromString(scope.logicalVault),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
            val key=ByteArray(32) {it.toByte()};val output=ByteArrayOutputStream()
            val manifest=AttachmentObjectStreams.write(cap,content,key.copyOf(),ByteArrayInputStream(bytes),output,bytes.size.toLong())
            file.writeBytes(output.toByteArray())
            val underlying=FileInputStream(file).channel
            channel=object:SeekableByteChannel by underlying {
                override fun read(destination:ByteBuffer):Int {
                    val count=underlying.read(destination)
                    val action=onRead;onRead=null;action?.invoke()
                    return count
                }
            }
            crypto=AttachmentObjectStreams.open(cap,manifest,key,channel)
            return AttachmentScopedReader(db,scope,cap,crypto)
        }
        fun switch() {raw.beginTransaction();try {AttachmentIdentity.replaceStructuredDataset(raw);raw.setTransactionSuccessful()} finally {raw.endTransaction()}}
        override fun close() {session.revoke();db.close();file.delete()}
    }
    @Test fun committedDatasetSwitchRefusesAlreadyOpenLeaseWithoutDependingOnCallerRevocation()=worker {
        Fixture().use {f->
            val reader=f.reader();val out=ByteArray(21) {9}
            assertEquals(21,reader.readAt(66000,out,0,21));assertArrayEquals(f.bytes.copyOfRange(66000,66021),out)
            f.switch();f.session.issue().check() // Deliberately prove the caller forgot to revoke the old session.
            // Frozen crypto primitive has no database knowledge: this witnesses the old facade's unsafe return type.
            assertEquals(21,f.crypto.readAt(66000,out,0,21));assertArrayEquals(f.bytes.copyOfRange(66000,66021),out)
            out.fill(9)
            try {reader.readAt(66000,out,0,21);fail("Old dataset lease published")}catch(_:IllegalStateException){}
            assertArrayEquals(ByteArray(21),out);assertFalse(f.channel.isOpen)
        }
    }
    @Test fun callerSnapshotCannotServeAsCurrentDatasetAuthority()=worker {
        Fixture().use {f->
            val reader=f.reader();val out=ByteArray(21) {9}
            f.raw.beginTransaction()
            try {try {reader.readAt(66000,out,0,21);fail("Caller snapshot accepted")}catch(_:IllegalStateException){}}
            finally {f.raw.endTransaction()}
            assertArrayEquals(ByteArray(21),out);assertFalse(f.channel.isOpen)
        }
    }
    @Test fun datasetSwitchDuringCipherReadClearsLatePlaintextBeforeReturning()=worker {
        Fixture().use {f->
            val reader=f.reader();var changed=false;f.onRead={f.switch();changed=true}
            val out=ByteArray(21) {9}
            try {reader.readAt(66000,out,0,21);fail("Late old-dataset output published")}catch(_:IllegalStateException){}
            assertTrue(changed);assertArrayEquals(ByteArray(21),out);assertFalse(f.channel.isOpen)
        }
    }
}
