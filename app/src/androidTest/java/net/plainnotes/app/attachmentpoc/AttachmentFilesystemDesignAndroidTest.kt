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
        val fd = Os.open(root.path, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW, 0)
        try { assertTrue(OsConstants.S_ISDIR(Os.fstat(fd).st_mode)); Os.fsync(fd) } finally { Os.close(fd) }
    }
    @Test fun exclusiveCreationCannotReplaceExistingOwnedTargetAndDirectorySyncWorks() = fixture { root ->
        val existing = File(root, "existing.object").also { it.writeBytes(byteArrayOf(9)) }
        val flags = OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW
        try {
            val fd = Os.open(existing.path, flags, 384)
            Os.close(fd); fail("O_EXCL replaced existing target")
        } catch (expected: ErrnoException) { assertEquals(OsConstants.EEXIST, expected.errno) }
        assertArrayEquals(byteArrayOf(9), existing.readBytes())
        val target = File(root, "new.object")
        val fd = Os.open(target.path, flags, 384)
        FileOutputStream(fd).use { it.write(byteArrayOf(1, 2, 3)); it.fd.sync() }
        syncDirectory(root)
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        // Production would keep this owned file inaccessible until whole AEAD verification + DB commit.
        // This primitive is exclusive creation, not atomic file+DB publication or power-cut proof.
    }
    @Test fun hardlinkCandidateIsDeniedWithoutChangingEitherFile() = fixture { root ->
        val source = File(root, "source.part").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val target = File(root, "link.object")
        try { Os.link(source.path, target.path); fail("Sandbox hardlink denial assumption changed: reassess design") }
        catch (expected: ErrnoException) { assertEquals(OsConstants.EACCES, expected.errno) }
        assertFalse(target.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), source.readBytes())
        // Actual negative evidence: hardlinks cannot be the Android production publication primitive.
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
