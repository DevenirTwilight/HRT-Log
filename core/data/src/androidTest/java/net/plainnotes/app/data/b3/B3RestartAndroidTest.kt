package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.domain.AttachmentSession
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch

/** MED-02B5 / B3-BACKUP-GATE: `scripts/run_b3_gate_restart.py` force-stops only this standalone test package while a
 * restore is parked at BEFORE_COMMIT (staged and published, not committed) or AFTER_COMMIT (committed, receipt and
 * cleanup not done), then a fresh process reopens the destination through its own Keystore alias. A plain test run
 * simulates the death in-process instead. Force-stop is not a power cut. */
class B3RestartAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker get() = File(context.filesDir, "synthetic-b3-restart-ready")
    private val result get() = File(context.filesDir, "synthetic-b3-restart-result")
    private val src = "synthetic-b3-restart-src"; private val dst = "synthetic-b3-restart-dst"
    private val password get() = "synthetic-b3-restart".toCharArray()
    private fun cap() = AttachmentSession(UUID.randomUUID(), UUID.randomUUID()).issue()
    private fun bundle() = File(context.noBackupFilesDir, "synthetic-b3-restart.b3")

    @Test fun interruptedRestoreRecoversInAFreshProcess() {
        val args = InstrumentationRegistry.getArguments(); val seam = B3Vault.Seam.valueOf(args.getString("b3_seam") ?: "BEFORE_COMMIT")
        require(seam == B3Vault.Seam.BEFORE_COMMIT || seam == B3Vault.Seam.AFTER_COMMIT)
        when (args.getString("b3_phase")) {
            "prepare" -> prepare(seam, block = true)
            "verify" -> verify(seam, fresh = true)
            null -> { prepare(seam, block = false); verify(seam, fresh = false) }
            else -> error("Unknown synthetic phase")
        }
    }

    private fun prepare(seam: B3Vault.Seam, block: Boolean) {
        marker.delete(); result.delete(); bundle().delete()
        listOf(src, dst).forEach { B3Vault.purge(context, it) }
        val s = B3Vault.create(context, src); val ds = s.newDataset()
        val lab = s.addRecord(ds, "LAB", 2, "LIVE", "synthetic estradiol 120 pg/mL")
        val v1 = s.addVersion(ds, "doc-r", 1, "RETAINED", "SYNTHETIC-B3-PLAINTEXT-r1".toByteArray() + ByteArray(30_000) { 3 })
        s.addVersion(ds, "doc-r", 2, "CURRENT", "SYNTHETIC-B3-PLAINTEXT-r2".toByteArray() + ByteArray(30_000) { 5 }, v1.uuid)
        s.addLink(ds, "doc-r", lab, "LIVE")
        B3Exporter.export(s, password, bundle(), cap()); s.destroy()
        val d = B3Vault.create(context, dst); val old = d.newDataset(); d.addVersion(old, "doc-old", 1, "CURRENT", ByteArray(20_000) { 9 })
        File(d.objects, "foreign-synthetic.bin").writeText("foreign")
        val op = UUID.randomUUID().toString()
        val state = JSONObject().put("pid", android.os.Process.myPid()).put("op", op).put("old", old).put("seam", seam.name)
        try {
            B3Restorer.restore(d, bundle(), password, op, cap()) {
                if (it == seam) { if (seam == B3Vault.Seam.AFTER_COMMIT) state.put("committed", d.current())
                    marker.writeText(state.toString()); if (block) CountDownLatch(1).await() else throw B3.SimulatedProcessDeath() }
            }
            fail("Restore should have stopped at $seam")
        } catch (_: B3.SimulatedProcessDeath) { check(!block) } finally { d.close() }
    }

    private fun verify(seam: B3Vault.Seam, fresh: Boolean) {
        val state = JSONObject(marker.readText()); val op = state.getString("op")
        val d = B3Vault.open(context, dst)   // only this namespace's alias can open its database
        try {
            if (fresh) assertNotEquals("Fresh process", state.getInt("pid"), android.os.Process.myPid())
            val before = d.phase(op)
            if (seam == B3Vault.Seam.BEFORE_COMMIT) {
                assertEquals("SYNCED", before); assertEquals("Uncommitted restore never became CURRENT", state.getString("old"), d.current())
                val owned = d.scalarString("SELECT files FROM operation WHERE id=?", op)!!.lines().filter { it.isNotBlank() }.map(::File)
                assertEquals(mapOf(op to "ABORTED"), B3Restorer.reconcile(d))
                assertTrue(owned.none { it.exists() }); assertEquals("CURRENT", d.datasetState(state.getString("old")))
                // The same bundle restores cleanly afterwards.
                assertNotNull(B3Restorer.restore(d, bundle(), password, capability = cap()).dataset)
            } else {
                assertEquals("COMMITTED", before); assertEquals(state.getString("committed"), d.current())
                assertEquals(mapOf(op to "COMPLETE"), B3Restorer.reconcile(d))
                assertEquals("RETIRED", d.datasetState(state.getString("old")))
            }
            val current = d.current()!!
            d.versions(current).forEach { v -> assertTrue(d.decrypt(current, v).copyOfRange(0, 22).decodeToString().startsWith("SYNTHETIC-B3-PLAINTEXT")) }
            assertEquals("foreign", File(d.objects, "foreign-synthetic.bin").readText()); assertEquals(0L, d.stagedRows())
            val marker = "SYNTHETIC-B3-PLAINTEXT".toByteArray()
            d.root.walkTopDown().filter { it.isFile }.forEach { f -> val b = f.readBytes()
                assertFalse("Plaintext in ${f.name}", (0..b.size - marker.size).any { i -> marker.indices.all { b[i + it] == marker[it] } }) }
            result.writeText(JSONObject().put("previous_pid", state.getInt("pid")).put("fresh_pid", android.os.Process.myPid()).put("fresh_process_asserted", fresh)
                .put("seam", seam.name).put("phase_found", before).put("current_is_old", d.current() == state.getString("old")).put("api", android.os.Build.VERSION.SDK_INT).toString())
            android.util.Log.i("B3_GATE", "test=restart api=${android.os.Build.VERSION.SDK_INT} seam=$seam previousPid=${state.getInt("pid")} freshPid=${android.os.Process.myPid()} phaseFound=$before")
        } finally { d.destroy(); bundle().delete() }
    }
}
