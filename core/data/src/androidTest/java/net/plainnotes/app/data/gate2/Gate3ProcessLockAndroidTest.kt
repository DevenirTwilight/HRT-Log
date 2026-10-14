package net.plainnotes.app.data

import android.content.ContentProviderClient
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** B2-GATE3: two real Android PIDs (instrumentation + `:gate2peer`) on one synthetic SQLCipher fixture and one
 * per-operation fcntl lease. Emulator-only standalone library test package; never the user app or its data. */
class Gate3ProcessLockAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val bytes = ByteArray(140_000) { (it * 31).toByte() }
    private val myPid = android.os.Process.myPid()

    private fun fails(block: () -> Unit): Throwable { try { block() } catch (t: Throwable) { return t }; throw AssertionError("Expected rejection") }
    private fun failsWith(expected: String, block: () -> Unit) = fails(block).also { t ->
        assertTrue("Expected rejection '$expected' but got $t", generateSequence(t) { it.cause }.any { it.message?.contains(expected) == true })
    }
    /** Unstable client: the peer dying must not take this process down with it. */
    private fun peer(): ContentProviderClient =
        context.contentResolver.acquireUnstableContentProviderClient(Gate2PeerProvider.AUTHORITY) ?: error("Synthetic peer unavailable")
    private fun call(method: String, fixture: String, arg: String? = null): Bundle =
        peer().use { it.call(method, arg, Bundle().apply { putString("fixture", fixture) }) ?: error("No peer reply") }
    private fun await(what: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
        while (!condition()) { check(android.os.SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for $what" }; Thread.sleep(20) }
    }
    private fun evidence(test: String, vararg fields: Pair<String, Any?>) = android.util.Log.i("GATE3_PROCESS",
        "test=$test api=${android.os.Build.VERSION.SDK_INT} pid=$myPid " + fields.joinToString(" ") { "${it.first}=${it.second}" })
    /** Starts the peer's import of [id] on a background thread; it pauses in WRITING while holding the lease. */
    private fun holdInPeer(fixture: String, id: String) = Executors.newSingleThreadExecutor().let { pool ->
        pool.submit(Callable { runCatching { call("holdWriting", fixture, id).getString("result") } }).also { pool.shutdown() }
    }

    @Test fun sameProcessMaintenanceProbeNeverDropsALiveWritersOsLock() {
        val red = InstrumentationRegistry.getArguments().getString("gate3_red") == "true"
        Gate2Store.Leases.unsafeLegacyProbeForRedTest = red
        val s = Gate2Store(context); val writing = CountDownLatch(1); val leave = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val op = s.prepare()
            val write = pool.submit { s.import(op, ByteArrayInputStream(bytes), fault = { if (it == Gate2Store.Seam.WRITE && writing.count > 0L) { writing.countDown(); check(leave.await(30, TimeUnit.SECONDS)) } }) }
            assertTrue(writing.await(30, TimeUnit.SECONDS))
            assertEquals(Gate2Store.LeaseState.HELD_THIS_PROCESS, s.leaseState(op.id))
            assertEquals("SKIP_LIVE_WRITER", s.reconcile()[op.id])                       // same-process probe
            val peerState = call("leaseState", s.fixture, op.id).getString("state")
            evidence("sameProcessProbe", "red" to red, "peerPid" to call("pid", s.fixture).getInt("pid"), "peerSees" to peerState)
            assertEquals("Peer process could acquire a live writer's lease after a same-process probe", "HELD_OTHER_PROCESS", peerState)
            leave.countDown(); write.get(30, TimeUnit.SECONDS)
            assertEquals(op.id, s.active()); assertEquals(Gate2Store.LeaseState.FREE, s.leaseState(op.id))
        } finally { Gate2Store.Leases.unsafeLegacyProbeForRedTest = false; leave.countDown(); pool.shutdownNow(); s.destroy() }
    }

    @Test fun peerProcessWriterBlocksReconcileAndSecondWriterButNotOtherOperations() {
        val s = Gate2Store(context)
        try {
            val old = s.prepare().also { s.import(it, ByteArrayInputStream(bytes)) }
            val op = s.prepare(); val peerPid = call("pid", s.fixture).getInt("pid"); assertNotEquals(myPid, peerPid)
            val holder = holdInPeer(s.fixture, op.id)
            await("peer WRITING with lease") { s.phase(op.id) == "WRITING" && s.leaseState(op.id) == Gate2Store.LeaseState.HELD_OTHER_PROCESS }
            val leaf = s.file(op); assertTrue(leaf.exists()); val reservedBefore = s.reservedBytes()
            assertEquals("SKIP_LIVE_WRITER", s.reconcile()[op.id])
            assertEquals("WRITING", s.phase(op.id)); assertTrue("Live peer leaf must survive recovery", leaf.exists()); assertEquals(reservedBefore, s.reservedBytes())
            failsWith("Synthetic writer lease held") { s.import(op, ByteArrayInputStream(bytes)) }       // second writer, other PID
            failsWith("Synthetic writer lease held") { s.releaseQuarantined(op.id, confirmed = true) }
            val other = s.prepare(); s.import(other, ByteArrayInputStream(bytes)); assertEquals(other.id, s.active())  // other operation proceeds
            assertEquals(true, call("release", s.fixture, op.id).getBoolean("released"))
            val result = holder.get(30, TimeUnit.SECONDS).getOrThrow()
            evidence("peerWriter", "peerPid" to peerPid, "peerResult" to result)
            // The peer's commit loses the single-winner CAS to the operation committed meanwhile; it cleans only its own leaf.
            assertEquals("FAILED:Synthetic stale CAS", result); assertEquals("ABORTED", s.phase(op.id)); assertFalse(leaf.exists())
            assertEquals(other.id, s.active()); assertTrue(s.file(old).exists()); assertTrue(s.file(other).exists())
            assertEquals(Gate2Store.LeaseState.FREE, s.leaseState(op.id)); s.open().close(); s.assertProductionOff()
        } finally { s.destroy() }
    }

    @Test fun killedPeerWriterReleasesLeaseAndOnlyItsOwnLeafIsCleaned() {
        val s = Gate2Store(context)
        try {
            val old = s.prepare().also { s.import(it, ByteArrayInputStream(bytes)) }
            val foreign = java.io.File(s.root, "foreign-synthetic").apply { writeText("not owned") }
            val op = s.prepare(); val peerPid = call("pid", s.fixture).getInt("pid"); assertNotEquals(myPid, peerPid)
            val holder = holdInPeer(s.fixture, op.id)
            await("peer WRITING with lease") { s.phase(op.id) == "WRITING" && s.leaseState(op.id) == Gate2Store.LeaseState.HELD_OTHER_PROCESS }
            assertEquals("SKIP_LIVE_WRITER", s.reconcile()[op.id])
            android.os.Process.killProcess(peerPid)                                 // same UID, standalone test package only
            assertTrue("Killed peer call must not report success", holder.get(30, TimeUnit.SECONDS).isFailure)
            await("kernel releases the dead peer's lease") { s.leaseState(op.id) == Gate2Store.LeaseState.FREE }
            val result = s.reconcile()
            evidence("killedPeer", "peerPid" to peerPid, "reconcile" to result[op.id])
            assertEquals("CLEANED_OWNED_INCOMPLETE", result[op.id]); assertFalse(s.file(op).exists())
            assertEquals(old.id, s.active()); assertTrue(s.file(old).exists()); assertEquals("not owned", foreign.readText())
            assertNotEquals("Peer restarted under the old PID", peerPid, call("pid", s.fixture).getInt("pid"))
            s.open().close(); s.assertProductionOff()
        } finally { s.destroy() }
    }
}
