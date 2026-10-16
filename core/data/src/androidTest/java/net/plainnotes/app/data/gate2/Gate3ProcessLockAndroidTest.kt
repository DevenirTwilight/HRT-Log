package net.plainnotes.app.data

import android.content.ContentProviderClient
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** B2-GATE3: two real Android PIDs (instrumentation + `:gate2peer`) contend for one per-operation fcntl writer lease
 * on a synthetic fixture. The peer holds the lease and creates the writer's own leaf; it never opens the database
 * (the production app is single-process). Emulator-only standalone library test package; never the user app. */
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
    private fun call(method: String, arg: String? = null, extras: Bundle = Bundle()): Bundle = peer().use { it.call(method, arg, extras) ?: error("No peer reply") }
    private fun marker(s: Gate2Store, op: Gate2Store.Operation) = File(s.root, "peer-paused-${op.id}")
    private fun paths(s: Gate2Store, op: Gate2Store.Operation) = Bundle().apply {
        putString("lock", s.leaseFileOf(op.id).path); putString("leaf", s.file(op).path); putString("marker", marker(s, op).path)
    }
    private fun await(what: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
        while (!condition()) { check(android.os.SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for $what" }; Thread.sleep(20) }
    }
    private fun evidence(test: String, vararg fields: Pair<String, Any?>) = android.util.Log.i("GATE3_PROCESS",
        "test=$test api=${android.os.Build.VERSION.SDK_INT} pid=$myPid " + fields.joinToString(" ") { "${it.first}=${it.second}" })
    /** Peer takes [op]'s lease, creates its leaf and parks; returns once the peer has signalled it is parked. */
    private fun holdInPeer(s: Gate2Store, op: Gate2Store.Operation): Future<Result<String?>> {
        val pool = Executors.newSingleThreadExecutor()
        val holder = pool.submit(Callable { runCatching { call("holdLease", op.id, paths(s, op)).getString("result") } }).also { pool.shutdown() }
        await("peer parked holding the lease") { marker(s, op).exists() || holder.isDone }
        check(marker(s, op).exists()) { "Peer did not park: ${holder.get()}" }
        return holder
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
            // Collect the same-process result, then observe the peer before asserting either invariant.
            // API26's deliberately unsafe red probe may itself acquire the second lock and clean the leaf;
            // asserting here would stop before the red control's required cross-process lock observation.
            val maintenanceResult = s.reconcile()[op.id]
            val lock = Bundle().apply { putString("lock", s.leaseFileOf(op.id).path) }
            val peerState = call("leaseState", op.id, lock).getString("state")
            evidence("sameProcessProbe", "red" to red, "peerPid" to call("pid").getInt("pid"), "peerSees" to peerState)
            assertEquals("Peer process could acquire a live writer's lease after a same-process probe", "HELD_OTHER_PROCESS", peerState)
            assertEquals("SKIP_LIVE_WRITER", maintenanceResult)
            leave.countDown(); write.get(30, TimeUnit.SECONDS)
            assertEquals(op.id, s.active()); assertEquals(Gate2Store.LeaseState.FREE, s.leaseState(op.id))
            assertEquals("FREE", call("leaseState", op.id, lock).getString("state"))
        } finally { Gate2Store.Leases.unsafeLegacyProbeForRedTest = false; leave.countDown(); pool.shutdownNow(); s.destroy() }
    }

    @Test fun peerProcessWriterBlocksReconcileAndSecondWriterButNotOtherOperations() {
        val s = Gate2Store(context)
        try {
            val old = s.prepare().also { s.import(it, ByteArrayInputStream(bytes)) }
            val op = s.prepare(); val peerPid = call("pid").getInt("pid"); assertNotEquals(myPid, peerPid)
            val holder = holdInPeer(s, op); s.recordPeerWriting(op)
            assertEquals(Gate2Store.LeaseState.HELD_OTHER_PROCESS, s.leaseState(op.id))
            val leaf = s.file(op); assertTrue("Peer leaf exists while parked", leaf.exists()); val reservedBefore = s.reservedBytes()
            assertEquals("SKIP_LIVE_WRITER", s.reconcile()[op.id])
            assertEquals("WRITING", s.phase(op.id)); assertTrue("Live peer leaf must survive recovery", leaf.exists()); assertEquals(reservedBefore, s.reservedBytes())
            failsWith("Synthetic writer lease held") { s.import(op, ByteArrayInputStream(bytes)) }       // second writer, other PID
            failsWith("Synthetic writer lease held") { s.releaseQuarantined(op.id, confirmed = true) }
            assertTrue("Live peer leaf untouched by refused writers", leaf.exists())
            val other = s.prepare(); s.import(other, ByteArrayInputStream(bytes)); assertEquals(other.id, s.active())  // other operation proceeds
            assertEquals(true, call("release", op.id).getBoolean("released"))
            assertEquals("RELEASED", holder.get(30, TimeUnit.SECONDS).getOrThrow())
            await("peer released its lease") { s.leaseState(op.id) == Gate2Store.LeaseState.FREE }
            val result = s.reconcile()[op.id]
            evidence("peerWriter", "peerPid" to peerPid, "afterRelease" to result)
            // Writer gone without committing: only its own recorded leaf is cleaned; CURRENT and other objects stay.
            assertEquals("CLEANED_OWNED_INCOMPLETE", result); assertFalse(leaf.exists())
            assertEquals(other.id, s.active()); assertTrue(s.file(old).exists()); assertTrue(s.file(other).exists())
            s.open().close(); s.assertProductionOff()
        } finally { s.destroy() }
    }

    @Test fun killedPeerWriterReleasesLeaseAndOnlyItsOwnLeafIsCleaned() {
        val s = Gate2Store(context)
        try {
            val old = s.prepare().also { s.import(it, ByteArrayInputStream(bytes)) }
            val foreign = File(s.root, "foreign-synthetic").apply { writeText("not owned") }
            val op = s.prepare(); val peerPid = call("pid").getInt("pid"); assertNotEquals(myPid, peerPid)
            val holder = holdInPeer(s, op); s.recordPeerWriting(op)
            assertEquals("SKIP_LIVE_WRITER", s.reconcile()[op.id]); assertTrue(s.file(op).exists())
            android.os.Process.killProcess(peerPid)                                 // same UID, standalone test package only
            assertTrue("Killed peer call must not report success", holder.get(30, TimeUnit.SECONDS).isFailure)
            await("kernel releases the dead peer's lease") { s.leaseState(op.id) == Gate2Store.LeaseState.FREE }
            val result = s.reconcile()
            evidence("killedPeer", "peerPid" to peerPid, "reconcile" to result[op.id])
            assertEquals("CLEANED_OWNED_INCOMPLETE", result[op.id]); assertFalse(s.file(op).exists())
            assertEquals(old.id, s.active()); assertTrue(s.file(old).exists()); assertEquals("not owned", foreign.readText())
            assertNotEquals("Peer restarted under the old PID", peerPid, call("pid").getInt("pid"))
            s.open().close(); s.assertProductionOff()
        } finally { s.destroy() }
    }
}
