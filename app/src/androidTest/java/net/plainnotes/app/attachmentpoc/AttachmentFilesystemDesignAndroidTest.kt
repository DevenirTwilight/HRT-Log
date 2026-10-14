package net.plainnotes.app.attachmentpoc

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/** Synthetic public-API experiment, not proof of power-loss durability or production publication. */
class AttachmentFilesystemDesignAndroidTest {
    private fun fixture(block: (File) -> Unit) {
        SyntheticEmulatorGuard.requireEmulator()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "synthetic-b0-fs-").toFile()
        try { block(root) } finally { check(root.deleteRecursively()) }
    }
    private fun syncDirectory(root: File) {
        val fd = Os.open(root.path, OsConstants.O_RDONLY or OsConstants.O_DIRECTORY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW, 0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
    }
    @Test fun linkPublicationCannotReplaceExistingOwnedTargetAndDirectorySyncWorks() = fixture { root ->
        val source = File(root, "synthetic.part")
        FileOutputStream(source).use { it.write(byteArrayOf(1, 2, 3)); it.fd.sync() }
        val existing = File(root, "existing.object").also { it.writeBytes(byteArrayOf(9)) }
        try { Os.link(source.path, existing.path); fail("link replaced existing target") }
        catch (expected: ErrnoException) { assertEquals(OsConstants.EEXIST, expected.errno) }
        assertArrayEquals(byteArrayOf(9), existing.readBytes())
        assertArrayEquals(byteArrayOf(1, 2, 3), source.readBytes())
        val target = File(root, "new.object")
        Os.link(source.path, target.path); syncDirectory(root)
        assertEquals(Os.stat(source.path).st_ino, Os.stat(target.path).st_ino)
        Os.unlink(source.path); syncDirectory(root)
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        // Success only establishes these emulator/API operations, NOT flash/power-cut behavior.
    }
    @Test fun noFollowRejectsLeafSymlink() = fixture { root ->
        val real = File(root, "real.object").also { it.writeBytes(byteArrayOf(4)) }
        val link = File(root, "link.object"); Os.symlink(real.path, link.path)
        try {
            val fd = Os.open(link.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            Os.close(fd); fail("O_NOFOLLOW accepted symlink")
        } catch (expected: ErrnoException) { assertEquals(OsConstants.ELOOP, expected.errno) }
        assertArrayEquals(byteArrayOf(4), real.readBytes())
        // Ancestor replacement/openat binding remains a separate production acceptance gate.
    }
}
