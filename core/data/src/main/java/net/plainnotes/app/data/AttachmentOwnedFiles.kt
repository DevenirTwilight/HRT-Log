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

/** Private ciphertext only. Kernel-pinned parent, no-follow/exclusive creation, no rename/delete.
 * /proc/self/fd traverses our open directory descriptor, never an untrusted pathname ancestor.
 * Unknown or interrupted objects are retained, not reclaimed by filename or inode guesses.
 */
internal class AttachmentOwnedFiles(context: Context) {
    private val root = File(context.noBackupFilesDir, "attachment-objects-v1")
    data class Identity(val parentDevice: Long, val parentInode: Long, val device: Long, val inode: Long)

    fun locked(): Directory {
        try { Os.mkdir(root.path, 448) } catch (e: ErrnoException) { if (e.errno != EEXIST) throw e }
        val descriptor = Os.open(root.path, O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        val parent = try { ParcelFileDescriptor.dup(descriptor) } finally { Os.close(descriptor) }
        try {
            directory(Os.fstat(parent.fileDescriptor))
            return Directory(parent)
        } catch (failure: Throwable) { parent.close(); throw failure }
    }

    private fun directory(stat: StructStat) {
        check(S_ISDIR(stat.st_mode) && stat.st_uid == Os.getuid() && stat.st_mode and 63 == 0) { "Private parent unavailable" }
    }
    private fun regular(stat: StructStat) {
        check(S_ISREG(stat.st_mode) && stat.st_uid == Os.getuid() && stat.st_nlink == 1L && stat.st_mode and 63 == 0) { "Unowned ciphertext" }
    }

    inner class Directory internal constructor(private val parent: ParcelFileDescriptor): AutoCloseable {
        private val parentStat = Os.fstat(parent.fileDescriptor)
        private val anchor get() = "/proc/self/fd/${parent.fd}"
        private var lockStream: FileOutputStream? = null
        private var lock: FileLock? = null
        init {
            try {
                // Never truncate or follow an existing lock file. OS lock excludes live writers in other processes.
                val fd = Os.open("$anchor/lock", O_RDWR or O_NONBLOCK or O_CREAT or O_NOFOLLOW or O_CLOEXEC, 384)
                val stream = FileOutputStream(fd).also { lockStream = it }
                regular(Os.fstat(fd))
                lock = stream.channel.tryLock() ?: error("Attachment storage busy")
                Os.fsync(fd); Os.fsync(parent.fileDescriptor)
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
        fun create(id: UUID): Created {
            checkParent()
            val fd = Os.open(path(id), O_RDWR or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
            try {
                val stat = Os.fstat(fd); regular(stat)
                Os.fsync(fd); Os.fsync(parent.fileDescriptor)
                return Created(FileOutputStream(fd), Identity(parentStat.st_dev, parentStat.st_ino, stat.st_dev, stat.st_ino))
            } catch (failure: Throwable) { Os.close(fd); throw failure }
        }
        fun open(id: UUID, expected: Identity): FileInputStream {
            checkParent()
            check(expected.parentDevice == parentStat.st_dev && expected.parentInode == parentStat.st_ino) { "Unknown parent ownership" }
            val fd = Os.open(path(id), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
            try {
                val stat = Os.fstat(fd); regular(stat)
                check(stat.st_dev == expected.device && stat.st_ino == expected.inode && stat.st_mode and 128 == 0) { "Ciphertext replaced or still writable" }
                return FileInputStream(fd)
            } catch (failure: Throwable) { Os.close(fd); throw failure }
        }
        override fun close() {
            try { lock?.release() } finally { try { lockStream?.close() } finally { parent.close() } }
        }
    }
    class Created internal constructor(private val output: FileOutputStream, val identity: Identity): AutoCloseable {
        /** Separate owned descriptor: crypto writer closes its sink, then coordinator fsyncs the original. */
        fun sink() = ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(output.fd))
        fun seal() { Os.fsync(output.fd); Os.fchmod(output.fd, 256); Os.fsync(output.fd); close() }
        override fun close() = output.close()
    }
}
