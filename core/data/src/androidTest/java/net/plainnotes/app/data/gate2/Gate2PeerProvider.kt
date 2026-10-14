package net.plainnotes.app.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** TEST APK ONLY, separate process `:gate2peer`: a second real Android PID for cross-process writer-lease evidence.
 * It never opens the SQLCipher database (the production app is single-process, and Room's schema guards rewrite
 * triggers on every open); it only takes the per-operation fcntl lease and creates the writer's own leaf, exactly as
 * the writer's filesystem side does. Paths must lie inside a `synthetic-gate2-*` fixture of this test package. */
class Gate2PeerProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "net.plainnotes.app.data.test.synthetic.gate2peer"
        private val releases = ConcurrentHashMap<String, CountDownLatch>()
    }
    override fun onCreate(): Boolean { Gate2EmulatorGuard.requireEmulator(); return true }
    private fun fixturePath(extras: Bundle, key: String): File {
        val f = File(extras.getString(key) ?: error("Synthetic $key required"))
        val base = context!!.noBackupFilesDir.canonicalPath + File.separator + "synthetic-gate2-"
        check(f.canonicalPath.startsWith(base)) { "Synthetic fixture only" }
        return f
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val out = Bundle().apply { putInt("pid", android.os.Process.myPid()) }
        when (method) {
            "pid" -> Unit
            // Holds the operation's writer lease and its own new leaf until released or killed. Blocks this binder thread.
            "holdLease" -> {
                val id = arg!!; val lock = fixturePath(extras!!, "lock"); val leaf = fixturePath(extras, "leaf"); val marker = fixturePath(extras, "marker")
                val lease = Gate2Store.Leases.acquire(lock) ?: return out.apply { putString("result", "LEASE_HELD") }
                val latch = CountDownLatch(1); releases[id] = latch
                try {
                    check(leaf.parentFile!!.mkdirs() || leaf.parentFile!!.isDirectory)
                    FileChannel.open(leaf.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use {
                        it.write(ByteBuffer.wrap(ByteArray(4096) { 7 })); it.force(true)
                    }
                    marker.writeText(android.os.Process.myPid().toString())   // explicit event: lease held, leaf created, parked
                    check(latch.await(60, TimeUnit.SECONDS)) { "Peer hold timed out" }
                    out.putString("result", "RELEASED")
                } finally { releases.remove(id); lease.close() }
            }
            "release" -> out.putBoolean("released", releases[arg!!]?.let { it.countDown(); true } ?: false)
            "leaseState" -> out.putString("state", Gate2Store.Leases.state(fixturePath(extras!!, "lock")).name)
            else -> error("Unknown synthetic peer call")
        }
        return out
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Synthetic peer")
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = throw UnsupportedOperationException("Synthetic peer")
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = throw UnsupportedOperationException("Synthetic peer")
}
