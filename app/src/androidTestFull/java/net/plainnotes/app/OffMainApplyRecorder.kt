package net.plainnotes.app

import android.os.Looper
import android.util.Log
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import java.util.Collections

/**
 * REQUIREMENTS §176: records every Compose snapshot apply that happens off the main thread during a device test. In the Compose test
 * environment such an apply runs recomposition and layout on that thread, which corrupts the composition (the SlotTable index errors
 * seen at Activity destroy). The test fails at teardown with the offending thread and what the main thread was doing, instead of a
 * later, unattributable crash.
 */
class OffMainApplyRecorder : AutoCloseable {
    private val offenders = Collections.synchronizedList(ArrayList<String>())
    private val handle: ObserverHandle = Snapshot.registerApplyObserver { _, _ ->
        val main = Looper.getMainLooper().thread
        if (Thread.currentThread() !== main) {
            val worker = Throwable("Compose state applied off the main thread on " + Thread.currentThread().name)
            Log.e("OffMainApply", "main thread was at " + main.stackTrace.take(12).joinToString(" <- "), worker)
            if (offenders.size < 5) offenders += Thread.currentThread().name + " | worker " +
                worker.stackTrace.drop(1).take(12).joinToString(" <- ") + " | main " + main.stackTrace.take(8).joinToString(" <- ")
        }
    }
    fun assertNone() { check(offenders.isEmpty()) { "Compose state applied off the main thread:\n" + offenders.joinToString("\n") } }
    override fun close() = handle.dispose()
}
