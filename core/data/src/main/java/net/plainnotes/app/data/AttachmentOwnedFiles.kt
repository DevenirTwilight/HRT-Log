package net.plainnotes.app.data

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import android.system.StructStat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileLock
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Files
import java.nio.file.Paths

/** Private ciphertext only. Kernel-pinned parent, no-follow/exclusive creation, no rename/delete.
 * /proc/self/fd traverses our open directory descriptor, never an untrusted pathname ancestor.
 * Unknown or interrupted objects are retained, not reclaimed by filename or inode guesses.
 */
internal class AttachmentOwnedFiles(context: Context,namespace:Namespace=Namespace.OBJECTS) {
    enum class Namespace(val directory:String) { OBJECTS("attachment-objects-v1"), RESTORE_QUARANTINE("attachment-restore-quarantine-v1") }
    private val root = File(context.noBackupFilesDir, namespace.directory)
    data class Identity(val parentDevice: Long, val parentInode: Long, val device: Long, val inode: Long)
    data class Entry(val name: String, val identity: Identity, val logicalBytes: Long,
        val chargedBytes: Long, val privateRegular: Boolean)
    data class Inventory(val entries: List<Entry>, val overheadBytes: Long,
        val freeBytes: Long, val allocationUnit: Long)

    fun locked(): Directory {
        val process=ProcessLease()
        try {
            try { Os.mkdir(root.path, 448) } catch (e: ErrnoException) { if (e.errno != EEXIST) throw e }
            val descriptor = Os.open(root.path, O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
            val parent = try { ParcelFileDescriptor.dup(descriptor) } finally { Os.close(descriptor) }
            try {
                directory(Os.fstat(parent.fileDescriptor))
                return Directory(parent,process)
            } catch (failure: Throwable) { parent.close(); throw failure }
        } catch(failure:Throwable) {process.close();throw failure}
    }
    /** Acquire BEFORE even opening lockfile: closing another fd can release this process's POSIX locks. */
    internal class ProcessLease:AutoCloseable {
        private val closed=AtomicBoolean(false)
        init {
            // The same thread asking twice is a bug in the caller (it would wait for itself): refuse at once, as before.
            check(holder.get()!==Thread.currentThread()) {"Attachment storage busy"}
            // Another in-process operation (a thumbnail, the viewer, an import, a link change) holds the gate only while it verifies,
            // opens or commits one object. Wait for it briefly instead of failing the one that merely overlapped (a viewer opened
            // while its own thumbnail was being made used to show "This file couldn't be displayed"). Never an unbounded wait.
            val acquired=try {processGate.tryAcquire(waitMillis,TimeUnit.MILLISECONDS)}
                catch(interrupted:InterruptedException) {Thread.currentThread().interrupt();false}
            check(acquired) {"Attachment storage busy"}
            holder.set(Thread.currentThread())
        }
        override fun close() {if(closed.compareAndSet(false,true)){holder.set(null);processGate.release()}}
    }

    private fun own(fd:java.io.FileDescriptor):ParcelFileDescriptor =
        try { ParcelFileDescriptor.dup(fd) } finally { Os.close(fd) }

    private fun directory(stat: StructStat) {
        check(S_ISDIR(stat.st_mode) && stat.st_uid == Os.getuid() && stat.st_mode and 63 == 0) { "Private parent unavailable" }
    }
    private fun regular(stat: StructStat) {
        check(S_ISREG(stat.st_mode) && stat.st_uid == Os.getuid() && stat.st_nlink == 1L && stat.st_mode and 63 == 0) { "Unowned ciphertext" }
    }

    inner class Directory internal constructor(private val parent: ParcelFileDescriptor, private val process:ProcessLease): AutoCloseable {
        private val closed=AtomicBoolean(false)
        private val parentStat = Os.fstat(parent.fileDescriptor)
        private val anchor get() = "/proc/self/fd/${parent.fd}"
        private var lockStream: FileOutputStream? = null
        private var lock: FileLock? = null
        init {
            try {
                // Never truncate or follow an existing lock file. OS lock excludes live writers in other processes.
                val fd = Os.open("$anchor/lock", O_RDWR or O_NONBLOCK or O_CREAT or O_NOFOLLOW or O_CLOEXEC, 384)
                val owned = own(fd)
                val stream = try { ParcelFileDescriptor.AutoCloseOutputStream(owned) } catch(failure:Throwable) {owned.close();throw failure}
                lockStream = stream
                regular(Os.fstat(owned.fileDescriptor))
                lock = stream.channel.tryLock() ?: error("Attachment storage busy")
                Os.fsync(owned.fileDescriptor); Os.fsync(parent.fileDescriptor)
                val parentFd = Os.open(root.parentFile!!.path, O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
                try { Os.fsync(parentFd) } finally { Os.close(parentFd) }
                checkParent()
            } catch (failure: Throwable) { close(); throw failure }
        }
        fun checkParent() {
            val current = Os.lstat(root.path)
            directory(current)
            check(current.st_dev == parentStat.st_dev && current.st_ino == parentStat.st_ino) { "Private parent replaced" }
            val held = Os.fstat(parent.fileDescriptor)
            check(held.st_dev == parentStat.st_dev && held.st_ino == parentStat.st_ino)
        }
        private fun path(id: UUID) = "$anchor/$id"
        /** Bounded metadata-only enumeration anchored to the same locked parent. Never opens a leaf. */
        fun inventory(maximumEntries: Int, checkAuthority: () -> Unit): Inventory {
            require(maximumEntries in 1..100000)
            checkParent();checkAuthority()
            fun charged(s: StructStat): Long {
                check(s.st_size >= 0 && s.st_blocks >= 0)
                return maxOf(s.st_size,Math.multiplyExact(s.st_blocks,512))
            }
            var overhead=charged(Os.fstat(parent.fileDescriptor))
            val heldLock=Os.fstat(requireNotNull(lockStream).fd)
            val entries=ArrayList<Entry>()
            Files.newDirectoryStream(Paths.get(anchor)).use { stream ->
                for (leaf in stream) {
                    checkAuthority()
                    val name=leaf.fileName.toString()
                    val s=Os.lstat("$anchor/$name")
                    if(name=="lock") {
                        regular(s);check(s.st_dev==heldLock.st_dev && s.st_ino==heldLock.st_ino) {"Storage lock replaced"}
                        overhead=Math.addExact(overhead,charged(s));continue
                    }
                    if(entries.size >= maximumEntries) throw AttachmentStorageBudget.Rejected(AttachmentStorageBudget.Refusal.ENTRY_BUDGET)
                    entries+=Entry(name,Identity(parentStat.st_dev,parentStat.st_ino,s.st_dev,s.st_ino),
                        s.st_size,charged(s),S_ISREG(s.st_mode) && s.st_uid==Os.getuid() && s.st_nlink==1L && s.st_mode and 63==0)
                }
            }
            val space=Os.statvfs(anchor)
            check(space.f_bavail>=0 && space.f_frsize>0)
            checkParent();checkAuthority()
            return Inventory(entries,overhead,Math.multiplyExact(space.f_bavail,space.f_frsize),space.f_frsize)
        }
        fun create(id: UUID): Created {
            checkParent()
            val fd = Os.open(path(id), O_RDWR or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
            val identity = try {
                val stat = Os.fstat(fd); regular(stat)
                Os.fsync(fd); Os.fsync(parent.fileDescriptor)
                Identity(parentStat.st_dev, parentStat.st_ino, stat.st_dev, stat.st_ino)
            } catch (failure: Throwable) { Os.close(fd); throw failure }
            val owned=own(fd)
            try { return Created(ParcelFileDescriptor.AutoCloseOutputStream(owned),identity) }
            catch(failure:Throwable) {owned.close();throw failure}
        }
        fun open(id: UUID, expected: Identity): FileInputStream {
            checkParent()
            check(expected.parentDevice == parentStat.st_dev && expected.parentInode == parentStat.st_ino) { "Unknown parent ownership" }
            val fd = Os.open(path(id), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
            try {
                val stat = Os.fstat(fd); regular(stat)
                check(stat.st_dev == expected.device && stat.st_ino == expected.inode && stat.st_mode and 128 == 0) { "Ciphertext replaced or still writable" }
            } catch (failure: Throwable) { Os.close(fd); throw failure }
            val owned=own(fd)
            try { return ParcelFileDescriptor.AutoCloseInputStream(owned) }
            catch(failure:Throwable) {owned.close();throw failure}
        }
        override fun close() {
            if(!closed.compareAndSet(false,true))return
            try { lock?.release() } finally {
                try {lockStream?.close()} finally {try {parent.close()} finally {process.close()}}
            }
        }
    }
    companion object {
        private val processGate=Semaphore(1)
        private val holder=java.util.concurrent.atomic.AtomicReference<Thread?>(null)
        /** How long a caller waits for another in-process storage operation. Tests shorten it; production keeps the default. */
        @Volatile internal var waitMillis=5_000L
    }
    class Created internal constructor(private val output: FileOutputStream, val identity: Identity): AutoCloseable {
        /** Separate owned descriptor: crypto writer closes its sink, then coordinator fsyncs the original. */
        fun sink() = ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(output.fd))
        fun seal() { Os.fsync(output.fd); Os.fchmod(output.fd, 256); Os.fsync(output.fd); close() }
        override fun close() = output.close()
    }
}
