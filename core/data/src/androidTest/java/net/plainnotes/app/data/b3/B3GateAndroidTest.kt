package net.plainnotes.app.data

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.data.B3.Reject
import net.plainnotes.app.data.B3Frame.F
import net.plainnotes.app.domain.AttachmentSession
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.AEADBadTagException

/**
 * MED-02B5 / B3-BACKUP-GATE, TEST_ONLY_REAL_IO on the CI emulators: real SQLCipher databases, real AndroidKeyStore
 * aliases per namespace, real encrypted files. A synthetic medical graph is exported as one complete encrypted bundle
 * (all retained/trashed versions, portable keys, final manifest) and restored into a fresh namespace with its own alias.
 * Every failure test names the exact rejection and proves the destination's old CURRENT, rows and files are unchanged.
 * Not a production format; two simulated namespaces on one emulator are NOT two physical phones.
 */
class B3GateAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val created = mutableListOf<B3Vault>()
    private val bundles = mutableListOf<File>()
    private fun pw() = "synthetic-b3-password-only".toCharArray()
    private fun cap() = AttachmentSession(UUID.randomUUID(), UUID.randomUUID()).issue()
    private fun vault() = B3Vault.create(context).also { created += it }
    private fun payload(n: Int) = "SYNTHETIC-B3-PLAINTEXT-$n|".toByteArray() + ByteArray(48_000) { (it * 31 + n).toByte() }
    private fun evidence(test: String, vararg f: Pair<String, Any?>) = android.util.Log.i("B3_GATE",
        "test=$test api=${Build.VERSION.SDK_INT} pid=${android.os.Process.myPid()} " + f.joinToString(" ") { "${it.first}=${it.second}" })

    @After fun cleanup() {
        B3.unsafeSkipManifestForRedTest = false; B3.unsafeSkipCasForRedTest = false
        created.forEach { runCatching { it.destroy() } }; bundles.forEach { it.delete() }
    }

    private class Seed(val v: B3Vault, val ds: String, val plain: Map<String, ByteArray>, val records: List<B3Record>, val versions: List<B3Version>,
                       val links: List<B3Link>, val ids: Map<String, String>)
    /** One visit, labs (live, trashed, purged tombstone), a corrected document (retained v1, current v2), a trashed
     * document, and links in every state. */
    private fun seed(): Seed {
        val v = vault(); val ds = v.newDataset(); val ids = HashMap<String, String>(); val plain = HashMap<String, ByteArray>()
        ids["visit"] = v.addRecord(ds, "VISIT", 1, "LIVE", "synthetic visit 2026-03-01")
        ids["lab1"] = v.addRecord(ds, "LAB", 2, "LIVE", "synthetic estradiol 120 pg/mL")
        ids["lab2"] = v.addRecord(ds, "LAB", 3, "LIVE", "synthetic testosterone 0.4 nmol/L")
        ids["lab3"] = v.addRecord(ds, "LAB", 4, "TRASHED", "synthetic prolactin 12 ng/mL")
        ids["lab4"] = v.addRecord(ds, "LAB", 5, "PURGED", null)
        val d1 = "doc-${UUID.randomUUID()}"; val d2 = "doc-${UUID.randomUUID()}"; val d3 = "doc-${UUID.randomUUID()}"
        fun add(key: String, doc: String, seq: Int, state: String, n: Int, corrects: String? = null) =
            v.addVersion(ds, doc, seq, state, payload(n), corrects).also { ids[key] = it.uuid; plain[it.uuid] = payload(n) }
        val d1v1 = add("d1v1", d1, 1, "RETAINED", 1); add("d1v2", d1, 2, "CURRENT", 2, d1v1.uuid)
        add("d2v1", d2, 1, "CURRENT", 3); add("d3v1", d3, 1, "TRASHED", 4)
        ids["L1"] = v.addLink(ds, d1, ids["visit"]!!, "LIVE"); ids["L2"] = v.addLink(ds, d1, ids["lab1"]!!, "LIVE")
        ids["L3"] = v.addLink(ds, d2, ids["lab2"]!!, "LIVE"); ids["L4"] = v.addLink(ds, d3, ids["lab3"]!!, "TRASHED")
        ids["L5"] = v.addLink(ds, d2, ids["lab4"]!!, "PURGED"); ids["L6"] = v.addLink(ds, d2, ids["visit"]!!, "QUARANTINED")
        ids["d1"] = d1; ids["d2"] = d2; ids["d3"] = d3
        return Seed(v, ds, plain, v.records(ds), v.versions(ds), v.links(ds), ids)
    }
    /** A destination that already holds an older dataset (another epoch) whose lab reuses integer id 2, a live link,
     * and a file the restore does not own. */
    private class Dest(val v: B3Vault, val oldDs: String, val oldLab: String, val oldLink: String, val oldVersion: B3Version, val foreign: File)
    private fun dest(): Dest {
        val v = vault(); val ds = v.newDataset()
        val lab = v.addRecord(ds, "LAB", 2, "LIVE", "older epoch lab with the same integer id")
        val ver = v.addVersion(ds, "doc-old", 1, "CURRENT", payload(99))
        val link = v.addLink(ds, "doc-old", lab, "LIVE")
        val foreign = File(v.objects, "foreign-synthetic.bin").apply { writeText("not owned by any restore") }
        return Dest(v, ds, lab, link, ver, foreign)
    }
    private fun bundleFile() = File(context.noBackupFilesDir, "synthetic-b3-bundle-${UUID.randomUUID()}.b3").also { bundles += it }
    private fun export(s: Seed, before: (MutableList<F>) -> Unit = {}, after: (MutableList<F>) -> Unit = {}): File =
        bundleFile().also { B3Exporter.export(s.v, pw(), it, cap(), beforeManifest = before, afterManifest = after) }

    /** Everything a failed restore must leave exactly as it was. */
    private data class State(val current: String?, val revision: Long, val datasets: List<String>, val records: List<B3Record>, val versions: List<String>,
                             val links: List<B3Link>, val files: Set<String>, val staged: Long, val reserved: Long)
    private fun state(d: B3Vault) = State(d.current(), d.revision(),
        d.db.rawQuery("SELECT uuid||':'||state FROM dataset ORDER BY uuid", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() },
        d.current()?.let { d.records(it) } ?: emptyList(), d.current()?.let { d.versions(it).map { v -> v.uuid + v.state } } ?: emptyList(),
        d.current()?.let { d.links(it) } ?: emptyList(),
        d.objects.walkTopDown().filter { it.isFile }.map { it.relativeTo(d.objects).path }.toSet(), d.stagedRows(), d.reserved())
    private fun rejects(expected: Reject, block: () -> Unit): B3.Rejected {
        try { block() } catch (e: B3.Rejected) { assertEquals("Wrong rejection: ${e.message}", expected, e.reason); return e }
        fail("Expected $expected but the restore succeeded"); throw AssertionError()
    }
    private fun assertNoPlaintext(v: B3Vault) = v.root.walkTopDown().filter { it.isFile }.forEach { f ->
        val bytes = f.readBytes(); val marker = "SYNTHETIC-B3-PLAINTEXT".toByteArray()
        assertFalse("Plaintext found in ${f.name}", (0..bytes.size - marker.size).any { i -> marker.indices.all { bytes[i + it] == marker[it] } })
    }

    @Test fun portableRestoreIntoAFreshNamespaceKeepsTheWholeGraphAndRetiresTheOldDataset() {
        val s = seed(); val sourceEpoch = s.v.epoch(s.ds); val sourceVault = s.v.vaultOf(s.ds); val sourceGeneration = s.v.generationOf(s.ds)
        val bundle = export(s)
        val d = dest(); val oldEpoch = d.v.epoch(d.oldDs)
        s.v.destroy(); assertFalse("Source phone gone: its alias no longer exists", s.v.aliasExists())
        val receipt = B3Restorer.restore(d.v, bundle, pw(), capability = cap())
        val fresh = receipt.dataset
        // Exactly one CURRENT: the restored dataset, under a fresh local id and epoch; the logical crypto vault is kept.
        assertEquals(fresh, d.v.current()); assertEquals(1L, d.v.currentCount()); assertFalse(receipt.cleanupPending)
        assertNotEquals(s.ds, fresh); assertNotEquals(sourceEpoch, d.v.epoch(fresh)); assertNotEquals(oldEpoch, d.v.epoch(fresh))
        assertEquals(sourceVault, d.v.vaultOf(fresh))
        assertNotEquals("Fresh physical generation", sourceGeneration, d.v.generationOf(fresh)); assertNotEquals(d.v.generationOf(d.oldDs), d.v.generationOf(fresh))
        assertEquals(s.ds, d.v.scalarString("SELECT source_dataset FROM dataset WHERE uuid=?", fresh))
        // Graph identical in every state: retained/trashed versions, corrections, tombstone, every link state.
        assertEquals(s.records, d.v.records(fresh)); assertEquals(s.links, d.v.links(fresh))
        assertEquals(s.versions.map { listOf(it.uuid, it.document, it.seq, it.state, it.vault, it.obj, it.dek, it.corrects) },
            d.v.versions(fresh).map { listOf(it.uuid, it.document, it.seq, it.state, it.vault, it.obj, it.dek, it.corrects) })
        d.v.versions(fresh).forEach { v -> assertArrayEquals("Version ${v.state} decrypts under the destination's own key",
            s.plain.getValue(v.uuid), d.v.decrypt(fresh, v)) }
        // Old dataset: RETIRED with a traceable pointer, kept intact as a fallback; its live link quarantined, never rebound.
        assertEquals("RETIRED", d.v.datasetState(d.oldDs)); assertEquals(receipt.operation, d.v.scalarString("SELECT retired_by FROM dataset WHERE uuid=?", d.oldDs))
        assertEquals("QUARANTINED", d.v.links(d.oldDs).single { it.uuid == d.oldLink }.state)
        assertArrayEquals(payload(99), d.v.decrypt(d.oldDs, d.oldVersion))
        val sameInteger = d.v.records(fresh).single { it.intId == 2 }
        assertNotEquals("Same integer id, different epoch: a different record", d.oldLab, sameInteger.uuid)
        assertTrue("No restored link reaches the old dataset", d.v.links(fresh).none { it.record == d.oldLab })
        // Nothing left behind; the file it does not own is untouched.
        assertEquals("not owned by any restore", d.foreign.readText())
        assertEquals("COMPLETE", d.v.phase(receipt.operation)); assertEquals(0L, d.v.stagedRows()); assertEquals(0L, d.v.reserved())
        assertFalse(File(d.v.root, "staging/${receipt.operation}").exists())
        assertNoPlaintext(d.v)
        evidence("e2e", "versions" to d.v.versions(fresh).size, "links" to d.v.links(fresh).size, "bundleBytes" to bundle.length(),
            "sourceAliasExists" to s.v.aliasExists(), "result" to "RESTORED_INTO_FRESH_NAMESPACE")
    }

    @Test fun keysAreReWrappedForTheDestinationAndNoOtherAliasOpensThem() {
        val s = seed(); val bundle = export(s); val d = vault()
        val fresh = B3Restorer.restore(d, bundle, pw(), capability = cap()).dataset
        val v = d.versions(fresh).first(); val wrap = d.dekWrap(fresh, v.dek)!!
        assertNull("The source wrap is never copied", s.v.dekWrap(fresh, v.dek))
        try { d.unwrap(wrap, d.dekAad(fresh, v.dek), alias = s.v.kekAlias); fail("Source alias opened a destination key") } catch (_: AEADBadTagException) {}
        try { d.unwrap(s.v.dekWrap(s.ds, v.dek)!!, s.v.dekAad(s.ds, v.dek)); fail("Destination alias opened a source key") } catch (_: AEADBadTagException) {}
        try { d.unwrap(wrap, d.dekAad(s.ds, v.dek)); fail("A wrap opened under another dataset's context") } catch (_: AEADBadTagException) {}
        evidence("keyDomains", "result" to "DESTINATION_ONLY")
    }

    // ---- Frame-level tampering with the correct password (an attacker or a buggy writer). ----
    private fun frames(list: List<F>, type: Int) = list.withIndex().filter { it.value.type == type }
    private fun objectOf(f: F) = DataInputStream(ByteArrayInputStream(f.payload)).readUTF()
    private fun objectFrame(f: F, cipher: ByteArray) = DataInputStream(ByteArrayInputStream(f.payload)).run { val ver = readUTF(); val obj = readUTF()
        F(B3Frame.OBJECT, B3Frame.bytes { writeUTF(ver); writeUTF(obj); writeInt(cipher.size); write(cipher) }) }
    private fun cipherOf(f: F) = DataInputStream(ByteArrayInputStream(f.payload)).run { readUTF(); readUTF(); ByteArray(readInt()).also { readFully(it) } }
    private fun versionsFrame(vs: List<B3Version>) = with(B3Frame) { F(VERSIONS, bytes { writeInt(vs.size); vs.forEach { x -> writeUTF(x.uuid); writeUTF(x.document); writeInt(x.seq); writeUTF(x.state); writeUTF(x.vault); writeUTF(x.obj); writeUTF(x.dek); blob(x.plainHash); blob(x.cipherHash); writeLong(x.cipherBytes); str(x.corrects) } }) }
    private fun recordsFrame(rs: List<B3Record>) = with(B3Frame) { F(RECORDS, bytes { writeInt(rs.size); rs.forEach { r -> writeUTF(r.uuid); writeUTF(r.kind); writeInt(r.intId); writeUTF(r.state); str(r.body) } }) }
    private fun linksFrame(ls: List<B3Link>) = with(B3Frame) { F(LINKS, bytes { writeInt(ls.size); ls.forEach { l -> writeUTF(l.uuid); writeUTF(l.document); writeUTF(l.record); writeUTF(l.state) } }) }
    private fun replace(list: MutableList<F>, type: Int, f: F) { list[list.indexOfFirst { it.type == type }] = f }

    @Test fun everyFrameLevelTamperIsRejectedForItsOwnReasonAndTheOldDataIsUntouched() {
        val s = seed(); val d = dest(); val before = state(d.v)
        val vs = s.versions.associateBy { it.uuid }
        fun obj(key: String) = { l: MutableList<F> -> l.removeAt(frames(l, B3Frame.OBJECT).single { objectOf(it.value) == s.ids[key] }.index); Unit }
        val cases: List<Triple<String, Reject, Pair<(MutableList<F>) -> Unit, (MutableList<F>) -> Unit>>> = listOf(
            Triple("final manifest missing", Reject.MANIFEST_MISSING, {_: MutableList<F> -> } to { l -> l.removeAt(l.lastIndex) }),
            Triple("frame after the manifest", Reject.TRAILING_AFTER_MANIFEST, {_: MutableList<F> -> } to { l -> l += l.first() }),
            Triple("duplicated object frame", Reject.DUPLICATE_ENTRY, {_: MutableList<F> -> } to { l -> l.add(l.lastIndex, frames(l, B3Frame.OBJECT).first().value) }),
            Triple("reordered objects", Reject.MANIFEST_MISMATCH, {_: MutableList<F> -> } to { l -> val o = frames(l, B3Frame.OBJECT); java.util.Collections.swap(l, o[0].index, o[1].index) }),
            Triple("object dropped after the manifest", Reject.MANIFEST_MISMATCH, {_: MutableList<F> -> } to { l -> l.removeAt(frames(l, B3Frame.OBJECT).first().index) }),
            Triple("retained version's object missing", Reject.OBJECT_MISSING, obj("d1v1") to {_: MutableList<F> -> }),
            Triple("trashed version's object missing", Reject.OBJECT_MISSING, obj("d3v1") to {_: MutableList<F> -> }),
            Triple("objects of two versions swapped", Reject.OBJECT_HASH, { l: MutableList<F> -> val o = frames(l, B3Frame.OBJECT)
                val a = o[0]; val b = o[1]; l[a.index] = objectFrame(a.value, cipherOf(b.value)); l[b.index] = objectFrame(b.value, cipherOf(a.value)) } to {_: MutableList<F> -> }),
            Triple("swapped objects with consistent hashes (wrong version identity)", Reject.OBJECT_AUTH, { l: MutableList<F> -> val o = frames(l, B3Frame.OBJECT)
                val a = o[0]; val b = o[1]; val ca = cipherOf(a.value); val cb = cipherOf(b.value)
                l[a.index] = objectFrame(a.value, cb); l[b.index] = objectFrame(b.value, ca)
                val va = vs.getValue(objectOf(a.value)); val vb = vs.getValue(objectOf(b.value))
                replace(l, B3Frame.VERSIONS, versionsFrame(s.versions.map { when (it.uuid) {
                    va.uuid -> it.copy(cipherHash = vb.cipherHash, cipherBytes = vb.cipherBytes, plainHash = vb.plainHash)
                    vb.uuid -> it.copy(cipherHash = va.cipherHash, cipherBytes = va.cipherBytes, plainHash = va.plainHash); else -> it } })) } to {_: MutableList<F> -> }),
            Triple("embedded object ciphertext with a broken tag (hashes made consistent)", Reject.OBJECT_AUTH, { l: MutableList<F> -> val o = frames(l, B3Frame.OBJECT).first()
                val bad = cipherOf(o.value).also { it[it.size - 3] = (it[it.size - 3].toInt() xor 1).toByte() }; l[o.index] = objectFrame(o.value, bad)
                val ver = objectOf(o.value)
                replace(l, B3Frame.VERSIONS, versionsFrame(s.versions.map { if (it.uuid == ver) it.copy(cipherHash = B3.sha256(bad)) else it })) } to {_: MutableList<F> -> }),
            Triple("portable key missing", Reject.DEK_MISSING, { l: MutableList<F> -> l.removeAt(frames(l, B3Frame.DEK).first().index); Unit } to {_: MutableList<F> -> }),
            Triple("portable key truncated", Reject.DEK_MALFORMED, { l: MutableList<F> -> val k = frames(l, B3Frame.DEK).first()
                val h = DataInputStream(ByteArrayInputStream(k.value.payload)).readUTF(); l[k.index] = F(B3Frame.DEK, B3Frame.bytes { writeUTF(h); writeInt(16); write(ByteArray(16)) }) } to {_: MutableList<F> -> }),
            Triple("live link to a purged record", Reject.GRAPH_INVALID, { l: MutableList<F> -> replace(l, B3Frame.LINKS, linksFrame(s.links.map { if (it.uuid == s.ids["L5"]) it.copy(state = "LIVE") else it })) } to {_: MutableList<F> -> }),
            Triple("trashed link silently made live", Reject.GRAPH_INVALID, { l: MutableList<F> -> replace(l, B3Frame.LINKS, linksFrame(s.links.map { if (it.uuid == s.ids["L4"]) it.copy(state = "LIVE") else it })) } to {_: MutableList<F> -> }),
            Triple("two CURRENT versions of one document", Reject.GRAPH_INVALID, { l: MutableList<F> -> replace(l, B3Frame.VERSIONS, versionsFrame(s.versions.map { if (it.uuid == s.ids["d1v1"]) it.copy(state = "CURRENT") else it })) } to {_: MutableList<F> -> }),
            Triple("purged tombstone with content", Reject.GRAPH_INVALID, { l: MutableList<F> -> replace(l, B3Frame.RECORDS, recordsFrame(s.records.map { if (it.uuid == s.ids["lab4"]) it.copy(body = "resurrected") else it })) } to {_: MutableList<F> -> }),
        )
        cases.forEach { (name, reason, tamper) ->
            val bundle = export(s, before = tamper.first, after = tamper.second)
            val e = rejects(reason) { B3Restorer.restore(d.v, bundle, pw(), capability = cap()) }
            assertEquals("$name changed the destination", before, state(d.v))
            evidence("frameTamper", "case" to name.replace(' ', '_'), "rejected" to e.reason)
        }
    }

    /** RED CONTROL `b3_red=manifest`: without the manifest transcript check, reordered frames would be accepted. */
    @Test fun reorderedFramesAreRejectedByTheFinalManifest() {
        B3.unsafeSkipManifestForRedTest = args.getString("b3_red") == "manifest"
        val s = seed(); val d = dest(); val before = state(d.v)
        val bundle = export(s, after = { l -> val o = frames(l, B3Frame.OBJECT); java.util.Collections.swap(l, o[0].index, o[1].index) })
        val outcome = try { B3Restorer.restore(d.v, bundle, pw(), capability = cap()); "ACCEPTED" } catch (e: B3.Rejected) { e.reason.name }
        evidence("manifestRed", "red" to B3.unsafeSkipManifestForRedTest, "outcome" to outcome)
        assertEquals("Reordered bundle was accepted without manifest verification", Reject.MANIFEST_MISMATCH.name, outcome)
        assertEquals(before, state(d.v))
    }

    @Test fun byteLevelTamperPasswordAndKdfProfileAreRejectedBeforeAnythingIsStaged() {
        val s = seed(); val d = dest(); val before = state(d.v); val good = export(s).readBytes()
        fun variant(bytes: ByteArray) = bundleFile().apply { writeBytes(bytes) }
        fun restore(f: File, password: CharArray = pw()) = B3Restorer.restore(d.v, f, password, capability = cap())
        val m = B3.MAGIC.size
        val cases = listOf(
            Triple("wrong password", Reject.WRONG_PASSWORD_OR_HEADER) { restore(variant(good), "wrong-synthetic".toCharArray()) },
            Triple("bootstrap cut", Reject.BOOTSTRAP_TRUNCATED) { restore(variant(good.copyOf(B3.BOOTSTRAP / 2))) },
            Triple("body cut after bootstrap", Reject.STREAM_AUTH) { restore(variant(good.copyOf(B3.BOOTSTRAP + 10))) },
            Triple("last byte cut", Reject.STREAM_AUTH) { restore(variant(good.copyOf(good.size - 1))) },
            Triple("cut at a segment boundary", Reject.STREAM_AUTH) { restore(variant(good.copyOf(B3.BOOTSTRAP + B3.SEGMENT))) },
            Triple("trailing bytes", Reject.STREAM_AUTH) { restore(variant(good + ByteArray(37) { 1 })) },
            Triple("body bit flip", Reject.STREAM_AUTH) { restore(variant(good.copyOf().also { it[good.size / 2] = (it[good.size / 2].toInt() xor 1).toByte() })) },
            Triple("authentic prefix, corrupted tail", Reject.STREAM_AUTH) { restore(variant(good.copyOf().also { it[good.size - 5] = (it[good.size - 5].toInt() xor 1).toByte() })) },
            Triple("salt bit flip", Reject.WRONG_PASSWORD_OR_HEADER) { restore(variant(good.copyOf().also { it[m + 5] = (it[m + 5].toInt() xor 1).toByte() })) },
            Triple("archive id changed", Reject.WRONG_PASSWORD_OR_HEADER) { restore(variant(good.copyOf().also { it[m + 21] = (it[m + 21].toInt() xor 1).toByte() })) },
            Triple("magic changed", Reject.BAD_MAGIC) { restore(variant(good.copyOf().also { it[0] = 'X'.code.toByte() })) },
            Triple("protocol 2", Reject.UNSUPPORTED_PROTOCOL) { restore(variant(good.copyOf().also { it[m] = 0; it[m + 1] = 2 })) },
        )
        cases.forEach { (name, reason, run) -> rejects(reason) { run() }; assertEquals("$name changed the destination", before, state(d.v)) }
        // Unknown and over-budget KDF profiles are refused before any Argon2id work or memory allocation.
        val calls = B3.kdfCalls.get()
        rejects(Reject.UNSUPPORTED_KDF) { restore(variant(good.copyOf().also { it[m + 2] = 0x7E; it[m + 3] = 0x7F })) }
        rejects(Reject.RESOURCES) { restore(variant(good.copyOf().also { it[m + 2] = 0x7E; it[m + 3] = 0x02 })) }
        assertEquals("No KDF invoked for refused profiles", calls, B3.kdfCalls.get())
        // Stream-level failures happen after the operation is journaled; each one must have ended ABORTED.
        assertEquals(before, state(d.v)); assertEquals(0L, d.v.scalarLong("SELECT COUNT(*) FROM operation WHERE phase<>'ABORTED'"))
        evidence("byteTamper", "cases" to cases.size + 2, "kdfCallsForRefusedProfiles" to 0)
    }

    @Test fun quotaShortfallRefusesWithoutTouchingOldData() {
        val s = seed(); val bundle = export(s); val d = dest()
        val used = d.v.scalarLong("SELECT COALESCE(SUM(cipher_bytes),0) FROM version")
        d.v.setQuota(used + bundle.length() - 1); val before = state(d.v)
        rejects(Reject.QUOTA) { B3Restorer.restore(d.v, bundle, pw(), capability = cap()) }
        assertEquals(before, state(d.v)); assertEquals(0L, d.v.scalarLong("SELECT COUNT(*) FROM operation"))
        d.v.setQuota(used + bundle.length())
        assertNotNull("The exact budget is enough (old data plus the new copy)", B3Restorer.restore(d.v, bundle, pw(), capability = cap()).dataset)
    }

    /** RED CONTROL `b3_red=cas`: without the CAS, a restore would overwrite a CURRENT that changed under it. */
    @Test fun staleCasAbortsAndKeepsTheOldCurrent() {
        B3.unsafeSkipCasForRedTest = args.getString("b3_red") == "cas"
        val s = seed(); val bundle = export(s); val d = dest(); val before = state(d.v)
        val outcome = try {
            B3Restorer.restore(d.v, bundle, pw(), capability = cap()) { if (it == B3Vault.Seam.BEFORE_COMMIT) d.v.db.execSQL("UPDATE authority SET revision=revision+1 WHERE id=1") }
            "COMMITTED_OVER_STALE"
        } catch (e: B3.Rejected) { e.reason.name }
        evidence("casRed", "red" to B3.unsafeSkipCasForRedTest, "outcome" to outcome)
        assertEquals("Stale CAS committed", Reject.CAS_STALE.name, outcome)
        assertEquals(before.copy(revision = before.revision + 1), state(d.v))
        assertEquals("ABORTED", d.v.scalarString("SELECT phase FROM operation"))
    }

    @Test fun failureInsideTheCommitTransactionRollsBackEverything() {
        val s = seed(); val bundle = export(s); val d = dest(); val before = state(d.v)
        try { B3Restorer.restore(d.v, bundle, pw(), capability = cap()) { if (it == B3Vault.Seam.IN_COMMIT) throw IOException("synthetic failure inside the commit") }; fail() }
        catch (e: IOException) { assertTrue(e !is B3.Rejected) }
        assertEquals(before, state(d.v)); assertEquals("ABORTED", d.v.scalarString("SELECT phase FROM operation"))
    }

    @Test fun committedButUnacknowledgedRestoreRetriesToTheSameReceipt() {
        val s = seed(); val bundle = export(s); val d = dest(); val op = UUID.randomUUID().toString()
        try { B3Restorer.restore(d.v, bundle, pw(), op, cap()) { if (it == B3Vault.Seam.AFTER_COMMIT) throw IOException("ack lost") }; fail() } catch (_: IOException) {}
        val committed = d.v.current()!!; assertEquals("COMMITTED", d.v.phase(op))
        val datasets = d.v.scalarLong("SELECT COUNT(*) FROM dataset"); val bytes = d.v.scalarLong("SELECT SUM(cipher_bytes) FROM version")
        val again = B3Restorer.restore(d.v, bundle, pw(), op, cap())
        assertEquals(committed, again.dataset); assertEquals(committed, d.v.current())
        assertEquals("No second dataset", datasets, d.v.scalarLong("SELECT COUNT(*) FROM dataset"))
        assertEquals("No second copy counted against the quota", bytes, d.v.scalarLong("SELECT SUM(cipher_bytes) FROM version"))
        assertEquals(0L, d.v.reserved()); assertEquals("COMPLETE", d.v.phase(op))
        rejects(Reject.ARCHIVE_MISMATCH) { B3Restorer.restore(d.v, export(s), pw(), op, cap()) }
    }

    @Test fun cleanupFailureIsReportedAndFinishedByReconcile() {
        val s = seed(); val bundle = export(s); val d = dest()
        var fail = true
        val r = B3Restorer.restore(d.v, bundle, pw(), capability = cap()) { if (it == B3Vault.Seam.CLEANUP && fail) { fail = false; throw IOException("synthetic cleanup failure") } }
        assertTrue("Cleanup failure is not reported as done", r.cleanupPending)
        assertEquals("COMMITTED_CLEANUP_PENDING", d.v.phase(r.operation)); assertTrue(d.v.stagedRows() > 0); assertEquals(r.dataset, d.v.current())
        assertEquals(mapOf(r.operation to "COMPLETE"), B3Restorer.reconcile(d.v))
        assertEquals(0L, d.v.stagedRows()); assertEquals(emptyMap<String, String>(), B3Restorer.reconcile(d.v))
    }

    @Test fun twoCurrentDatasetsAreRefusedBeforeStaging() {
        val s = seed(); val bundle = export(s); val d = dest()
        d.v.db.execSQL("INSERT INTO dataset(uuid,state,epoch,vault,generation) VALUES(?,'CURRENT',?,?,?)", arrayOf(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        val before = state(d.v)
        rejects(Reject.INVARIANT) { B3Restorer.restore(d.v, bundle, pw(), capability = cap()) }
        assertEquals(before, state(d.v)); assertEquals(0L, d.v.scalarLong("SELECT COUNT(*) FROM operation"))
    }

    @Test fun interruptedOwnOrphansAreNeverPublishedAndOnlyTheyAreCleaned() {
        for (seam in listOf(B3Vault.Seam.AFTER_STAGE, B3Vault.Seam.BEFORE_COMMIT)) {
            val s = seed(); val bundle = export(s); val d = dest(); val before = state(d.v); val op = UUID.randomUUID().toString()
            try { B3Restorer.restore(d.v, bundle, pw(), op, cap()) { if (it == seam) throw B3.SimulatedProcessDeath() }; fail() } catch (_: B3.SimulatedProcessDeath) {}
            val orphans = d.v.scalarString("SELECT files FROM operation WHERE id=?", op)!!.lines().filter { it.isNotBlank() }.map(::File)
            assertTrue("$seam left owned files", orphans.any { it.exists() })
            assertEquals("$seam: CURRENT and its rows unchanged", before.copy(files = state(d.v).files, staged = state(d.v).staged, reserved = state(d.v).reserved), state(d.v))
            assertTrue("Orphans are not referenced by any version", orphans.all { d.v.scalarLong("SELECT COUNT(*) FROM version WHERE object=?", it.name.removeSuffix(".cipher")) == 0L })
            assertEquals(mapOf(op to "ABORTED"), B3Restorer.reconcile(d.v))
            assertTrue(orphans.none { it.exists() }); assertEquals(before, state(d.v)); assertEquals("not owned by any restore", d.foreign.readText())
            assertNotNull(B3Restorer.restore(d.v, bundle, pw(), capability = cap()).dataset)
            evidence("orphan", "seam" to seam, "orphans" to orphans.size, "reconcile" to "ABORTED")
        }
    }

    @Test fun exportSnapshotIsConsistentUnderAConcurrentWriterAndLeasedObjectsSurvive() {
        val s = seed(); val lab1 = s.ids.getValue("lab1"); val d2v1 = s.versions.single { it.uuid == s.ids["d2v1"] }; val d3v1 = s.versions.single { it.uuid == s.ids["d3v1"] }
        val committedAt = AtomicLong(0); var gateHeldMs = 0L; var gcDuringExport: Boolean? = null
        val writer = Thread {
            val second = s.v.secondConnection()
            try { val deadline = System.nanoTime() + 20_000_000_000L
                while (System.nanoTime() < deadline) {
                    try { second.execSQL("UPDATE record SET body='changed during export' WHERE uuid=?", arrayOf(lab1)); committedAt.set(System.nanoTime()); break }
                    catch (e: android.database.sqlite.SQLiteException) { Thread.sleep(20) }
                }
            } finally { second.close() }
        }
        val bundle = bundleFile()
        B3Exporter.export(s.v, pw(), bundle, cap(),
            duringCapture = { writer.start(); Thread.sleep(300); gateHeldMs = 300; assertEquals("Writer committed inside the snapshot gate", 0L, committedAt.get()) },
            afterCapture = {
                // A newer CURRENT replaces d2v1 and d3v1 is purged while the export runs.
                s.v.tx { s.v.db.execSQL("UPDATE version SET state='RETAINED' WHERE uuid=?", arrayOf(d2v1.uuid)) }
                s.v.addVersion(s.ds, d2v1.document, 2, "CURRENT", payload(7))
                s.v.tx { s.v.db.execSQL("DELETE FROM version WHERE uuid=?", arrayOf(d3v1.uuid)) }
                s.v.tx { s.v.db.execSQL("UPDATE record SET state='LIVE' WHERE uuid=?", arrayOf(s.ids.getValue("lab3"))) }   // trash restored meanwhile
                gcDuringExport = s.v.gcObject(s.v.generationOf(s.ds), d3v1.obj)
            })
        writer.join(25_000)
        assertTrue("Writer eventually committed (after the gate)", committedAt.get() > 0)
        assertEquals(false, gcDuringExport); assertTrue("Leased object survived", s.v.objectFile(s.v.generationOf(s.ds), d3v1.obj).exists())
        assertTrue("Lease released after export", s.v.gcObject(s.v.generationOf(s.ds), d3v1.obj))
        val d = vault(); val fresh = B3Restorer.restore(d, bundle, pw(), capability = cap()).dataset
        assertEquals("synthetic estradiol 120 pg/mL", d.records(fresh).single { it.uuid == lab1 }.body)
        assertEquals("CURRENT", d.versions(fresh).single { it.uuid == d2v1.uuid }.state)
        assertEquals("Snapshot keeps the trashed state", "TRASHED", d.records(fresh).single { it.uuid == s.ids["lab3"] }.state)
        assertEquals("Snapshot keeps the selected version", 1, d.versions(fresh).count { it.document == d2v1.document })
        assertArrayEquals(payload(4), d.decrypt(fresh, d.versions(fresh).single { it.uuid == d3v1.uuid }))
        assertEquals("changed during export", s.v.records(s.ds).single { it.uuid == lab1 }.body)
        evidence("snapshot", "writerHeldOffMs" to gateHeldMs, "gcRefusedUnderLease" to (gcDuringExport == false), "result" to "CONSISTENT")
    }

    /** Two restores race for the same destination: the second commit wins only if it started from the CURRENT it sees;
     * the loser fails its CAS, keeps nothing, and the winner's dataset and files are untouched. Deterministic interleaving:
     * B runs to completion while A is parked between publish and commit. */
    @Test fun concurrentRestoresLetExactlyOneCommit() {
        val a = seed(); val b = seed(); val bundleA = export(a); val bundleB = export(b); val d = dest()
        var winner: B3Receipt? = null
        val e = rejects(Reject.CAS_STALE) { B3Restorer.restore(d.v, bundleA, pw(), capability = cap()) { if (it == B3Vault.Seam.BEFORE_COMMIT) winner = B3Restorer.restore(d.v, bundleB, pw(), capability = cap()) } }
        val w = winner!!
        assertEquals(w.dataset, d.v.current()); assertEquals(1L, d.v.currentCount())
        assertEquals("Winner graph intact", b.records, d.v.records(w.dataset))
        d.v.versions(w.dataset).forEach { v -> assertArrayEquals(b.plain.getValue(v.uuid), d.v.decrypt(w.dataset, v)) }
        assertTrue("Loser left no rows", a.versions.none { v -> d.v.scalarLong("SELECT COUNT(*) FROM version WHERE uuid=?", v.uuid) > 0 })
        val names = d.v.objects.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
        assertTrue("Loser left no files", a.versions.none { v -> "${v.obj}.cipher" in names })
        assertEquals(0L, d.v.reserved()); assertEquals(0L, d.v.stagedRows())
        evidence("concurrentRestore", "loser" to e.reason, "winner" to "COMMITTED")
    }

    /** A valid ciphertext copied into the objects directory is never activated: only the SQL commit publishes. */
    @Test fun aPlantedObjectFileIsNeverActivated() {
        val s = seed(); val bundle = export(s); val d = dest()
        val v = s.versions.first(); val planted = File(d.v.objects, "${UUID.randomUUID()}/${UUID.randomUUID()}.cipher")
        planted.parentFile!!.mkdirs(); s.v.objectFile(s.v.generationOf(s.ds), v.obj).copyTo(planted)
        assertEquals(emptyMap<String, String>(), B3Restorer.reconcile(d.v))
        val fresh = B3Restorer.restore(d.v, bundle, pw(), capability = cap()).dataset
        assertTrue(planted.exists()); assertEquals(0L, d.v.scalarLong("SELECT COUNT(*) FROM version WHERE object=?", planted.name.removeSuffix(".cipher")))
        assertEquals(s.versions.size, d.v.versions(fresh).size)
    }

    @Test fun lockOrCancelStopsExportAndRestoreWithoutSuccess() {
        val s = seed(); val session = AttachmentSession(UUID.randomUUID(), UUID.randomUUID()); val out = bundleFile()
        rejects(Reject.CANCELLED) { B3Exporter.export(s.v, pw(), out, session.issue(), afterCapture = { session.revoke() }) }
        assertFalse("No partial bundle survives a cancelled export", out.exists())
        val bundle = export(s); val d = dest(); val before = state(d.v); val restoreSession = AttachmentSession(UUID.randomUUID(), UUID.randomUUID())
        rejects(Reject.CANCELLED) { B3Restorer.restore(d.v, bundle, pw(), capability = restoreSession.issue()) { if (it == B3Vault.Seam.AFTER_STAGE) restoreSession.revoke() } }
        assertEquals(before, state(d.v))
    }

    /** Evidence for B6: what the instrumentation process can actually allocate here (the API29 16 MiB episode). */
    @Test fun runtimeResourceEvidence() {
        fun prop(k: String) = runCatching { ProcessBuilder("/system/bin/getprop", k).start().inputStream.bufferedReader().readLine() }.getOrNull()
        val max = Runtime.getRuntime().maxMemory()
        evidence("runtime", "maxMemoryMiB" to max / (1024 * 1024), "heapgrowthlimit" to prop("dalvik.vm.heapgrowthlimit"), "heapsize" to prop("dalvik.vm.heapsize"),
            "qemuHeapsize" to prop("qemu.dalvik.vm.heapsize"), "abi" to Build.SUPPORTED_ABIS.joinToString("+"), "qemu" to prop("ro.kernel.qemu"),
            "kdfProfileKiB" to B3.PROFILES.getValue(B3.TEST_FAST).memoryKiB)
        assertTrue(max > 0)
    }
}
