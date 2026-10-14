package net.plainnotes.app.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** TEST APK ONLY, separate process `:gate2peer`: a second real Android PID that opens the same synthetic fixture.
 * It only touches `synthetic-gate2-*` fixtures of this standalone library test package. */
class Gate2PeerProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "net.plainnotes.app.data.test.synthetic.gate2peer"
        private val releases = ConcurrentHashMap<String, CountDownLatch>()
    }
    override fun onCreate(): Boolean { Gate2EmulatorGuard.requireEmulator(); return true }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val fixture = extras?.getString("fixture") ?: error("Synthetic fixture required")
        check(fixture.startsWith("synthetic-gate2-")) { "Synthetic fixture only" }
        val out = Bundle().apply { putInt("pid", android.os.Process.myPid()) }
        when (method) {
            "pid" -> Unit
            // Blocks this binder thread while holding the operation's writer lease in WRITING.
            "holdWriting" -> {
                val id = arg!!; val latch = CountDownLatch(1); releases[id] = latch
                val store = Gate2Store(context!!, fixture)
                try {
                    var paused = false
                    store.import(store.operationOf(id), ByteArrayInputStream(ByteArray(150_000) { (it * 13).toByte() }), fault = {
                        if (it == Gate2Store.Seam.WRITE && !paused) { paused = true; check(latch.await(60, TimeUnit.SECONDS)) { "Peer hold timed out" } }
                    })
                    out.putString("result", store.phase(id))
                } catch (t: Throwable) {
                    out.putString("result", "FAILED:${t.message}")
                } finally { releases.remove(id); store.close() }
            }
            "release" -> out.putBoolean("released", releases[arg!!]?.let { it.countDown(); true } ?: false)
            "leaseState" -> Gate2Store(context!!, fixture).let { s -> try { out.putString("state", s.leaseState(arg!!).name) } finally { s.close() } }
            "reconcile" -> Gate2Store(context!!, fixture).let { s -> try { out.putString("state", s.reconcile()[arg!!]) } finally { s.close() } }
            "secondWriter" -> Gate2Store(context!!, fixture).let { s ->
                try { s.import(s.operationOf(arg!!), ByteArrayInputStream(ByteArray(1000))); out.putString("result", "IMPORTED") }
                catch (t: Throwable) { out.putString("result", "FAILED:${t.message}") } finally { s.close() }
            }
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
