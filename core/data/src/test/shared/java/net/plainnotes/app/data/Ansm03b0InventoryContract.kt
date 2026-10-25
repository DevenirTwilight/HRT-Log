package net.plainnotes.app.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.*
import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.shadow.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** Same contract runs on synthetic Room/Robolectric and native SQLCipher. No production wiring. */
abstract class Ansm03b0InventoryContract {
    protected abstract fun factory(pass: ByteArray): SupportSQLiteOpenHelper.Factory
    protected open val assetsContext: Context get() = context
    protected open fun checkEncryption(name: String) {}
    protected open fun reportFile(name: String): File? = null
    protected val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var databaseName: String
    private val key = ByteArray(32) { (it + 23).toByte() } // Synthetic test key, not user material.
    private val raw get() = db.openHelper.writableDatabase
    private val dao get() = db.dao()
    private val at = Instant.parse("2026-03-27T06:00:00Z")
    private val zone = ZoneId.of("Europe/Paris")
    private val scope = ShadowScope("SYNTH-DATASET-A", "SYNTH-MED-A")
    private val ref = PresentationRef("SYNTH", "SYNTH-PRODUCT", "SYNTH-PACK", "v1", "a".repeat(64))
    private val ingredient = Meaning(QuantityKind.ACTIVE_MASS, "SYNTH-compound:SA:group1")
    private val tablet = Meaning(QuantityKind.USE_COUNT, "SYNTH-tablet@PACK:2mg:v1")
    private fun q(n: String, unit: MeasureUnit = MeasureUnit.TABLET, meaning: Meaning = tablet) = Quantity(BigDecimal(n), unit, meaning)
    private fun presentation(unit: MeasureUnit = MeasureUnit.TABLET, meaning: Meaning = tablet, strength: String = "2",
                             capacity: String? = "2", version: String = "v1"): PresentationSnapshot {
        val r = ref.copy(version = version)
        val evidence = ConversionEvidence(r, "synthetic://fixture", "per-use", "2026-10-16", "explicit synthetic fact", true, true)
        val relation = ContentRelation(q("1", unit, meaning), q(strength, MeasureUnit.MG, ingredient), evidence)
        return PresentationSnapshot(r, null, q("64", unit, meaning), listOf(relation, ContentRelation(relation.to, relation.from, evidence)),
            capacity?.let { q(it, unit, meaning) })
    }
    private fun open(name: String) = Room.databaseBuilder(context, NotesDatabase::class.java, name)
        .openHelperFactory(factory(key.clone())).addMigrations(*allMigrations()).addCallback(SchemaGuards).allowMainThreadQueries().build()
    @Before fun setup() {
        databaseName = "ansm03b0-synthetic-${UUID.randomUUID()}.db"
        db = open(databaseName); raw
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    }
    @After fun close() { db.close(); context.deleteDatabase(databaseName) }
    private suspend fun medication(unit: String = "TABLET", capacity: Double = 2.0, dose: Double = 1.0): Long = repo.saveMedication(
        MedicationEntity(name = "Synthetic B0", molecule = "OTHER", unit = unit, dose_per_intake = dose, container_capacity = capacity,
            soon_alert_minutes = 0, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = true, sort_order = 0),
        null, RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.of(9, 0)), emptySet(), at)
    private fun cId(id: Long) = "SYNTH-CONTAINER-$id"
    private fun rId(id: Long) = "SYNTH-RECORD-$id"
    /** Exact values here are independent synthetic ground truth, NEVER a conversion of historical Double. */
    private fun freeze(boxes: List<ContainerEntity>, usedTruth: String = "0", s: PresentationSnapshot = presentation(),
                       unit: MeasureUnit = MeasureUnit.TABLET, meaning: Meaning = tablet) = boxes.map {
        FrozenContainer(scope, cId(it.id), it.id, s, ingredient, unit, meaning, q(usedTruth, unit, meaning), it.state, it.opened_on) }
    private fun request(record: RecordEntity, n: String, boxes: List<FrozenContainer>, unit: MeasureUnit = MeasureUnit.TABLET,
                        meaning: Meaning = tablet, event: ShadowEvent = ShadowEvent.APPLY) = ShadowRequest(scope, rId(record.id), record.revision,
        "SYNTH-OP-${record.id}-${record.revision}", record.taken_utc ?: 0, event, ingredient, q(n, unit, meaning),
        ActualSelection(boxes.first().snapshot!!.ref, boxes.map { it.uuid }, true))
    private fun observed(entries: List<SupplyEntryEntity>, unit: MeasureUnit? = MeasureUnit.TABLET,
                         meaning: Meaning? = tablet) = entries.map { LegacyAllocation(scope, cId(it.container_id), it.dose_record_id?.let(::rId).orEmpty(), it.dose_revision ?: 0,
        it.id.toString(), it.kind, it.used_delta, unit, meaning, it.reversal_of_id?.toString()) }
    private fun rows(table: String): JSONArray = raw.query("SELECT * FROM `$table` ORDER BY rowid").use { c -> JSONArray().apply {
        while (c.moveToNext()) put(JSONObject().apply { for (i in 0 until c.columnCount) put(c.getColumnName(i), when (c.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_FLOAT -> "binary64:${java.lang.Double.doubleToRawLongBits(c.getDouble(i))}"
            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
            Cursor.FIELD_TYPE_BLOB -> Ansm03b0CandidateStore.digest(c.getBlob(i).joinToString(","))
            else -> c.getString(i)
        }) })
    } }
    private fun tables(): List<String> = raw.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) } }
    private fun fingerprint(): String {
        val data = JSONObject(); tables().forEach { data.put(it, rows(it)) }
        data.put("ddl", raw.query("SELECT type,name,sql FROM sqlite_master WHERE type IN('table','index','trigger') ORDER BY type,name").use { c ->
            JSONArray().apply { while (c.moveToNext()) put(JSONArray().put(c.getString(0)).put(c.getString(1)).put(if (c.isNull(2)) JSONObject.NULL else c.getString(2))) } })
        return Ansm03b0CandidateStore.digest(Ansm03b0CandidateStore.canonical(data))
    }
    private suspend fun state(label: String, med: Long): JSONObject {
        val timeline = repo.planned(at, at.plusSeconds(366 * 86400L), at, zone)
        val f = StockForecasts.forecast(med, dao.containers(), timeline)
        val data = JSONObject(); tables().forEach { data.put(it, rows(it)) }
        return JSONObject().put("label", label).put("precision", "legacy REAL values stored as binary64 bits; original decimal precision UNKNOWN")
            .put("tables", data).put("forecast", JSONObject().put("status", f.status.name).put("available_legacy", f.available?.let { "binary64:${java.lang.Double.doubleToRawLongBits(it)}" } ?: JSONObject.NULL)
                .put("first_short", f.firstShort?.toString() ?: JSONObject.NULL).put("days", f.daysUntilShort(at.atZone(zone).toLocalDate(), zone) ?: JSONObject.NULL))
    }
    private fun resultJson(result: ShadowResult): JSONObject {
        fun quantity(q: Quantity?): Any = q?.let { JSONObject().put("value", it.value.toPlainString())
            .put("unit", it.unit.name).put("kind", it.meaning.kind.name).put("identity", it.meaning.identity) } ?: JSONObject.NULL
        return JSONObject().put("status", result.status.name).put("comparison", result.comparison.name)
            .put("quantity", quantity(result.quantity)).put("unallocated", quantity(result.unallocated))
            .put("allocations", JSONArray().apply { result.allocations.forEach { put(JSONObject().put("container", it.container)
                .put("quantity", quantity(it.quantity)).put("original_entry", it.originalEntryId ?: JSONObject.NULL)) } })
            .put("reasons", JSONArray(result.reasons.map { it.name }.sorted())).put("error", result.conversionError?.name ?: JSONObject.NULL)
            .put("requires_confirmation", result.additionalConfirmationRequired).put("legacy_original_precision", result.legacyOriginalPrecision)
    }
    private fun report(name: String, value: JSONObject) {
        reportFile(name)?.let { it.parentFile.mkdirs(); it.writeText(Ansm03b0CandidateStore.canonical(value) + "\n") }
        println("ANSM03B0 $name ${Ansm03b0CandidateStore.digest(value.toString())}")
    }
    private suspend fun requireNoWrite(block: suspend () -> ShadowResult): ShadowResult {
        val before = fingerprint(); val result = block(); assertEquals("Shadow must not mutate any table or schema", before, fingerprint()); return result
    }
    private fun expectRejected(marker: String, block: () -> Unit) {
        try { block(); fail("Expected rejection: $marker") } catch (e: Exception) {
            val message = generateSequence(e as Throwable?) { it.cause }.joinToString(" ") { it.message.orEmpty() }
            assertTrue("Wrong failure: $message", message.contains(marker, ignoreCase = true))
        }
    }
    @Test fun baselinePublicRecordEditReverseAdjustmentAndBackupPreserveAppendOnlyHistory() = runBlocking {
        val m = medication(); val events = JSONArray(); events.put(state("before-packages", m))
        repo.addContainers(m, 2.0, 2, false); events.put(state("created-sealed", m))
        repo.replaceContainer(m, 2.0); events.put(state("opened-oldest", m))
        val before = freeze(dao.containers())
        val slot = repo.planned(at, at.plusSeconds(86400), at, zone).first().slot
        repo.complete(slot, slot.at, 1.0); events.put(state("completed", m))
        val record = dao.records().single { it.slot_key == slot.key }; val first = dao.supplyFor(record.id)
        val exact = requireNoWrite { ShadowMeasurementAllocator.evaluate(request(record, "1", before), before, observed(first)) }
        assertEquals(ShadowComparison.APPROXIMATE_MATCH, exact.comparison)
        repo.complete(slot, slot.at, 1.0); assertEquals(first, dao.supplyFor(record.id)); events.put(state("duplicate-public-complete", m))
        repo.editRecord(record.id, slot.at.plusSeconds(60), 1.5, at.plusSeconds(3 * 86400)); events.put(state("actual-correction", m))
        val corrected = dao.recordById(record.id)!!; val edited = dao.supplyFor(record.id)
        assertEquals(listOf("CONSUME", "REVERSE", "CONSUME"), edited.map { it.kind }); assertEquals(first, edited.take(first.size))
        val original = ShadowCapture(scope, rId(record.id), 1, "SYNTH-ORIGINAL", "captured", exact.quantity,
            exact.allocations.mapIndexed { i, a -> a.copy(originalEntryId = first[i].id.toString()) })
        val reverse = requireNoWrite { ShadowMeasurementAllocator.reverse(scope, rId(record.id), original.revision, original,
            observed(edited.filter { it.kind == "REVERSE" })) }
        assertEquals(ShadowComparison.APPROXIMATE_MATCH, reverse.comparison)
        repo.deleteRecord(record.id, at.plusSeconds(4 * 86400)); events.put(state("deleted-and-reversed", m))
        val beforeRepeat = dao.supplyFor(record.id); repo.deleteRecord(record.id); assertEquals(beforeRepeat, dao.supplyFor(record.id))
        repo.restoreTrash(repo.trash().single { it.kind == Trash.RECORD }.id, at.plusSeconds(5 * 86400)); events.put(state("restored-record", m))
        repo.correctStock(dao.containers().first().id, 0.25); events.put(state("manual-adjust", m))
        val unchanged = dao.supplyFor(record.id); repo.correctStock(dao.containers().first().id, 0.25); assertEquals(unchanged, dao.supplyFor(record.id))
        val password = "synthetic-B0-only".toCharArray(); val backup = repo.exportBackup(password)
        val plain = BackupCodec.decrypt(backup, password); val json = JSONObject(String(plain, Charsets.UTF_8)); plain.fill(0)
        assertEquals(12, json.getInt("schema")); assertEquals(1, json.getInt("format"))
        val businessBefore = RawData.dump(raw).toString()
        // P0 fixes the former RESTRICT failure. Preserve full rollback coverage using a real injected failure.
        val beforeRestore = fingerprint()
        try {
            db.withTransaction { repo.restoreBackup(backup, password); error("SYNTHETIC_P0_BEFORE_COMMIT") }
            fail("Injected replacement failure missed")
        } catch (e: IllegalStateException) { assertEquals("SYNTHETIC_P0_BEFORE_COMMIT",e.message) }
        assertEquals(beforeRestore, fingerprint()); events.put(state("PNBAK1-injected-failure-rolled-back", m))
        repo.restoreBackup(backup,password)
        assertEquals(businessBefore,RawData.dump(raw).toString())
        raw.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        events.put(state("PNBAK1-nonempty-target-restored",m))
        db.close(); context.deleteDatabase(databaseName)
        databaseName = "ansm03b0-synthetic-fresh-${UUID.randomUUID()}.db"
        db = open(databaseName); raw
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        repo.restoreBackup(backup, password)
        assertEquals(businessBefore, RawData.dump(raw).toString()); events.put(state("PNBAK1-fresh-target-restored", m))
        password.fill('\u0000'); backup.fill(0)
        report("ledger-baseline.json", JSONObject().put("events", events).put("shadow", resultJson(exact)).put("reverse", resultJson(reverse)))
    }
    @Test fun voidRestoreAndConsumedPackageRejectionKeepOriginalLedger() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 1, true); val id = dao.containers().single().id
        val events = JSONArray().put(state("open", m)); repo.correctStock(id, 1.0); events.put(state("ADJUST", m))
        val ledger = dao.supplyForContainer(id)
        repo.removeMistakenContainer(id); events.put(state("VOIDED", m)); val once = fingerprint()
        repo.removeMistakenContainer(id); assertEquals(once, fingerprint()); assertEquals(ledger, dao.supplyForContainer(id))
        repo.restoreVoidedContainer(id); events.put(state("restored", m)); assertEquals(ledger, dao.supplyForContainer(id))
        val again = fingerprint(); repo.restoreVoidedContainer(id); assertEquals(again, fingerprint())
        repo.unscheduled(m, at, 1.0); val before = fingerprint()
        try { repo.removeMistakenContainer(id); fail("Consumed package must not be voided") } catch (_: PackageConsumptionConflict) {}
        assertEquals(before, fingerprint()); report("void-restore.json", JSONObject().put("events", events))
    }
    @Test fun unsafeMassScalarBridgeIsDetectedAgainstRealContainerConsumption() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 1, true); val before = freeze(dao.containers())
        // Deliberately faulty TEST adapter: passes 2 mg as the scalar "2 tablets". Production is not changed.
        repo.unscheduled(m, at, 2.0); val r = dao.records().single()
        val result = requireNoWrite { ShadowMeasurementAllocator.evaluate(request(r, "2", before, MeasureUnit.MG, ingredient), before, observed(dao.supplyFor(r.id))) }
        assertEquals(ShadowComparison.DIFFERENCE, result.comparison); assertTrue(ShadowReason.LEGACY_QUANTITY_DIFFERENCE in result.reasons)
        assertEquals(0, BigDecimal.ONE.compareTo(result.quantity!!.value))
        report("unsafe-bridge-difference.json", JSONObject().put("result", resultJson(result)).put("database", state("faulty-adapter-result", m)))
    }
    @Test fun realScalarPoolCannotProveMixedStrengthPackageSelection() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 2, true)
        val boxes = dao.containers(); val before = freeze(boxes).mapIndexed { i, c ->
            if (i == 0) c else c.copy(snapshot = presentation(strength = "1").let {
                PresentationSnapshot(it.ref.copy(presentation = "SYNTH-1MG"), it.nominalTotal, it.nominalUses, emptyList(), it.confirmedUsable)
            }, stockMeaning = tablet.copy(identity = "SYNTH-1MG-tablet")) }
        repo.unscheduled(m, at, 1.0); val r = dao.records().single(); val history = dao.supplyFor(r.id)
        val result = requireNoWrite { ShadowMeasurementAllocator.evaluate(request(r, "1", before), before, observed(history)) }
        assertEquals(ShadowStatus.UNKNOWN, result.status); assertTrue(ShadowReason.MIXED_PRESENTATIONS in result.reasons)
        assertTrue(result.allocations.isEmpty()); assertEquals(history, dao.supplyFor(r.id))
        report("mixed-strength-unknown.json", JSONObject().put("result", resultJson(result)).put("state", state("legacy-scalar-pool", m)))
    }
    @Test fun gelPumpAndMassRecordsNeedIndependentFrozenUnitsAndActualCapacity() = runBlocking {
        val m = medication(unit = "PUMP"); repo.addContainers(m, 2.0, 1, true)
        val pump = Meaning(QuantityKind.USE_COUNT, "SYNTH-pump@PACK:0.75mg")
        val source = presentation(MeasureUnit.PUMP, pump, "0.75")
        val before = freeze(dao.containers(), s = source, unit = MeasureUnit.PUMP, meaning = pump)
        repo.unscheduled(m, at, 1.0); val r = dao.records().single()
        for (mass in listOf(false, true)) {
            val request = request(r, if (mass) "0.75" else "1", before,
                if (mass) MeasureUnit.MG else MeasureUnit.PUMP, if (mass) ingredient else pump)
            val result = requireNoWrite { ShadowMeasurementAllocator.evaluate(request, before, observed(dao.supplyFor(r.id), MeasureUnit.PUMP, pump)) }
            assertEquals(ShadowComparison.APPROXIMATE_MATCH, result.comparison); assertEquals(0, BigDecimal.ONE.compareTo(result.quantity!!.value))
        }
        val nominalOnly = before.map { it.copy(snapshot = presentation(MeasureUnit.PUMP, pump, "0.75", capacity = null)) }
        val unknown = requireNoWrite { ShadowMeasurementAllocator.evaluate(request(r, "1", nominalOnly, MeasureUnit.PUMP, pump), nominalOnly, emptyList()) }
        assertEquals(ShadowStatus.UNKNOWN, unknown.status); assertTrue(unknown.allocations.isEmpty())
        report("gel-capacity.json", JSONObject().put("nominal_only", resultJson(unknown)).put("state", state("actual-one-pump", m)))
    }
    @Test fun patchRemovalDoesNotConsumeAndRevisionReversesOriginalApplication() = runBlocking {
        val m = medication(unit = "PATCH"); repo.addContainers(m, 2.0, 1, true)
        val patch = Meaning(QuantityKind.USE_COUNT, "SYNTH-patch@PACK")
        val before = freeze(dao.containers(), s = presentation(MeasureUnit.PATCH, patch), unit = MeasureUnit.PATCH, meaning = patch)
        repo.unscheduled(m, at, 1.0); val r = dao.records().single(); val first = dao.supplyFor(r.id)
        val application = requireNoWrite { ShadowMeasurementAllocator.evaluate(request(r, "1", before, MeasureUnit.PATCH, patch), before, observed(first, MeasureUnit.PATCH, patch)) }
        assertEquals(ShadowComparison.APPROXIMATE_MATCH, application.comparison)
        for (event in listOf(ShadowEvent.DETACH, ShadowEvent.REMOVE)) {
            val removal = requireNoWrite { ShadowMeasurementAllocator.evaluate(request(r, "1", before, MeasureUnit.PATCH, patch, event), before, emptyList()) }
            assertEquals(ShadowStatus.NO_STOCK_EFFECT, removal.status); assertTrue(removal.allocations.isEmpty())
        }
        repo.editRecord(r.id, at.plusSeconds(60), 1.0, at.plusSeconds(86400))
        val edited = dao.supplyFor(r.id); assertEquals(first, edited.take(first.size))
        assertEquals(listOf("CONSUME", "REVERSE", "CONSUME"), edited.map { it.kind })
        assertEquals(-first.single().used_delta, edited[1].used_delta, 0.0)
        report("patch-history.json", JSONObject().put("application", resultJson(application)).put("state", state("revised-application", m)))
    }
    @Test fun unknownLegacyContainersStayUnknownAfterRenamingAndCurrentStrengthChanges() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 1, true)
        val old = dao.containers().single(); val frozen = freeze(listOf(old)).map { it.copy(snapshot = null, stockUnit = null, stockMeaning = null, used = null, ingredient = null) }
        repo.unscheduled(m, at, 1.0); val r = dao.records().single(); val before = dao.supplyFor(r.id)
        dao.updateMedication(dao.medication(m).copy(name = "Synthetic renamed", dose_per_intake = 2.0))
        val request = ShadowRequest(scope, rId(r.id), r.revision, "SYNTH-LEGACY", 1, ShadowEvent.APPLY, ingredient, q("1"), ActualSelection(ref, frozen.map { it.uuid }, true))
        val result = requireNoWrite { ShadowMeasurementAllocator.evaluate(request, frozen, observed(before, null, null)) }
        assertEquals(ShadowStatus.UNKNOWN, result.status); assertTrue(result.allocations.isEmpty()); assertEquals(before, dao.supplyFor(r.id))
    }
    @Test fun ledgerGuardsAndBusinessUuidConflictDetectActualBadWrites() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 1, true); repo.unscheduled(m, at, 1.0)
        val r = dao.records().single(); val entries = dao.supplyFor(r.id); val entry = entries.single()
        expectRejected("Append-only supply ledger") { raw.execSQL("UPDATE supply_transaction SET used_delta=0 WHERE id=?", arrayOf(entry.id)) }
        expectRejected("Append-only supply ledger") { raw.execSQL("DELETE FROM supply_transaction WHERE id=?", arrayOf(entry.id)) }
        Ansm03b0CandidateStore.create(raw); bind(m, r, dao.containers().single().id)
        db.withTransaction { assertTrue(Ansm03b0CandidateStore.operation(raw, scope.dataset, "op", rId(r.id), r.revision, scope.medication, "payload-A")) }
        db.withTransaction { assertFalse(Ansm03b0CandidateStore.operation(raw, scope.dataset, "op", rId(r.id), r.revision, scope.medication, "payload-A")) }
        val state = fingerprint()
        expectRejected("SYNTHETIC_OP_CONFLICT") { runBlocking { db.withTransaction { Ansm03b0CandidateStore.operation(raw, scope.dataset, "op", rId(r.id), r.revision, scope.medication, "payload-B") } } }
        assertEquals(state, fingerprint()); assertEquals(entries, dao.supplyFor(r.id))
        // Internal allocate itself has no business UUID: demonstrate the boundary without changing production.
        db.withTransaction { SupplyLedger.allocate(dao, r) }; assertEquals(2, dao.supplyFor(r.id).size)
        val shadow = ShadowMeasurementAllocator.evaluate(request(r, "1", freeze(listOf(oldContainer(entry.container_id)))),
            freeze(listOf(oldContainer(entry.container_id))), observed(dao.supplyFor(r.id)))
        assertEquals(ShadowComparison.DIFFERENCE, shadow.comparison); assertTrue(ShadowReason.ALLOCATION_COUNT in shadow.reasons)
    }
    private suspend fun oldContainer(id: Long): ContainerEntity = dao.container(id).copy(state = "IN_USE", used_amount = 0.0)
    private fun bind(m: Long, r: RecordEntity, container: Long, dataset: String = scope.dataset, snapshot: Boolean = true) {
        Ansm03b0CandidateStore.dataset(raw, dataset)
        Ansm03b0CandidateStore.saveSnapshot(raw, dataset, "source-v1", presentation())
        raw.execSQL("INSERT INTO ansm_lab_med VALUES (?,?,?)", arrayOf(dataset, scope.medication, m))
        raw.execSQL("INSERT INTO ansm_lab_container VALUES (?,?,?,?,'LIVE',?)", arrayOf(dataset, cId(container), scope.medication, container, if (snapshot) "source-v1" else null))
        raw.execSQL("INSERT INTO ansm_lab_record VALUES (?,?,?,?,?)", arrayOf(dataset, rId(r.id), r.revision, scope.medication, r.id))
    }
    private fun capture(r: RecordEntity, container: Long, op: String = "op", amount: String = "1", dataset: String = scope.dataset) {
        Ansm03b0CandidateStore.operation(raw, dataset, op, rId(r.id), r.revision, scope.medication, "payload-$op")
        raw.execSQL("INSERT INTO ansm_lab_capture VALUES (?,?,?,?,?,?,?,?,'CONSUME',NULL)",
            arrayOf(dataset, op, cId(container), scope.medication, "source-v1", amount, "TABLET", tablet.identity))
    }
    @Test fun recordLedgerAndCandidateCaptureRollBackAtEveryTestTransactionBoundary() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 1, true); Ansm03b0CandidateStore.create(raw)
        val id = dao.containers().single().id
        for (failAt in 0..3) {
            val before = fingerprint()
            try { db.withTransaction {
                repo.unscheduled(m, at, 1.0); if (failAt == 0) error("SYNTHETIC_AFTER_RECORD_AND_LEDGER")
                val r = dao.records().single(); bind(m, r, id); if (failAt == 1) error("SYNTHETIC_AFTER_IDENTITY")
                capture(r, id); if (failAt == 2) error("SYNTHETIC_AFTER_CAPTURE")
                raw.execSQL("INSERT INTO ansm_lab_capture VALUES ('bad','bad','bad','bad','bad','1','TABLET','bad','CONSUME',NULL)")
            }; fail("Fault should roll back") } catch (e: Exception) { assertTrue(e.message.orEmpty().contains("SYNTHETIC") || e.message.orEmpty().contains("FOREIGN KEY", true)) }
            assertEquals(before, fingerprint()); assertTrue(dao.records().isEmpty()); assertEquals(0.0, dao.container(id).used_amount, 0.0)
        }
    }
    @Test fun snapshotCaptureForeignKeysTombstonesAndRestoreWithoutCatalogAreIsolated() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 1, true); repo.unscheduled(m, at, 1.0)
        val r = dao.records().single(); val id = dao.containers().single().id; Ansm03b0CandidateStore.create(raw); bind(m, r, id); capture(r, id)
        expectRejected("ANSM_TEST_ONLY_IMMUTABLE") { raw.execSQL("UPDATE ansm_lab_snapshot SET payload='changed'") }
        expectRejected("ANSM_TEST_ONLY_FROZEN") { raw.execSQL("UPDATE ansm_lab_container SET snapshot=NULL") }
        val restored = Ansm03b0CandidateStore.loadSnapshot(raw, scope.dataset, "source-v1")
        val converted = restored.convert(q("2", MeasureUnit.MG, ingredient), tablet, MeasureUnit.TABLET, ActualPackage(ref, true))
        assertTrue(converted is MeasureResult.Success); assertEquals(0, BigDecimal.ONE.compareTo((converted as MeasureResult.Success).value.quantity.value))
        raw.execSQL("INSERT INTO ansm_lab_container VALUES (?,?,?,NULL,'PURGED',?)", arrayOf(scope.dataset, "SYNTH-OLD-ONLY", scope.medication, "source-v1"))
        raw.execSQL("UPDATE ansm_lab_container SET state='PURGED',legacy_id=NULL")
        Ansm03b0CandidateStore.retire(raw, scope.dataset); val next = "SYNTH-DATASET-B"; bind(m, r, id, next)
        assertEquals(next, Ansm03b0CandidateStore.current(raw))
        Ansm03b0CandidateStore.operation(raw, next, "op", rId(r.id), r.revision, scope.medication, "payload-op")
        // Every other FK exists in B; this handle exists only in retired A. The domain boundary itself must reject it.
        expectRejected("FOREIGN KEY") { raw.execSQL("INSERT INTO ansm_lab_capture VALUES (?,?,?,?,?,?,?,?,'CONSUME',NULL)", arrayOf(next, "op", "SYNTH-OLD-ONLY", scope.medication, "source-v1", "1", "TABLET", tablet.identity)) }
        assertEquals(1, rows("ansm_lab_capture").length())
        capture(r, id, dataset = next); assertEquals(2, rows("ansm_lab_capture").length())
    }
    @Test fun oldBackupCannotSilentlyPreserveNewAssociationsOrGuardState() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 1, true); repo.unscheduled(m, at, 1.0)
        val password = "synthetic-B0-backup".toCharArray(); val backup = repo.exportBackup(password)
        val r = dao.records().single(); val id = dao.containers().single().id
        Ansm03b0CandidateStore.create(raw); bind(m, r, id); capture(r, id)
        repo.restoreBackup(backup, password)
        // Expected integration gap, NOT a production regression: production has no ansm_lab tables.
        assertEquals(scope.dataset, Ansm03b0CandidateStore.current(raw))
        raw.query("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='ansm_lab_snapshot_update'").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        expectRejected("ANSM_TEST_ONLY_IMMUTABLE") { raw.execSQL("UPDATE ansm_lab_snapshot SET payload='{}'") }
        val before = fingerprint()
        try { db.withTransaction { Ansm03b0CandidateStore.retire(raw, scope.dataset); repo.restoreBackup(backup, password); error("SYNTHETIC_RESTORE_INTERRUPTED") }; fail("Restore fault missed") }
        catch (e: IllegalStateException) { assertEquals("SYNTHETIC_RESTORE_INTERRUPTED", e.message) }
        assertEquals(before, fingerprint())
        db.withTransaction {
            Ansm03b0CandidateStore.retire(raw, scope.dataset); repo.restoreBackup(backup, password)
            Ansm03b0CandidateStore.dataset(raw, "SYNTH-RESTORED-UNKNOWN"); Ansm03b0CandidateStore.installGuards(raw)
        }
        assertEquals("SYNTH-RESTORED-UNKNOWN", Ansm03b0CandidateStore.current(raw))
        raw.query("SELECT COUNT(*) FROM ansm_lab_container WHERE dataset='SYNTH-RESTORED-UNKNOWN'").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        val badPayload = JSONObject().put("format", 1).put("schema", RecordBackupCompatibility.SCHEMA + 1).put("tables", RawData.dump(raw)).toString().toByteArray()
        val candidateBackup = BackupCodec.encrypt(badPayload, password); badPayload.fill(0)
        val stable = fingerprint(); try { repo.restoreBackup(candidateBackup, password); fail("Old reader must reject candidate newer business schema") }
        catch (_: BackupCodec.NewerBackup) {}
        assertEquals(stable, fingerprint()); backup.fill(0); candidateBackup.fill(0); password.fill('\u0000')
    }
    @Test fun candidateDDLFailureRollsBackWithoutChangingRoomVersionOrLegacyRows() = runBlocking {
        val m = medication(); repo.addContainers(m, 2.0, 1, true)
        for (i in 0 until Ansm03b0CandidateStore.DDL_STATEMENTS) {
            val before = fingerprint()
            try { db.withTransaction { Ansm03b0CandidateStore.create(raw, i) }; fail("DDL fault missed") }
            catch (e: IllegalStateException) { assertEquals("SYNTHETIC_DDL_FAILURE", e.message) }
            assertEquals(before, fingerprint()); assertEquals(18, raw.version)
        }
    }
    @Test fun allOldRoomVersionsUpgradeThenAcceptSeparateCandidateWithoutBackfillingInventory() {
        for (version in 1..12) {
            val name = "ansm03b0-synthetic-chain-$version-${UUID.randomUUID()}.db"
            val schema = assetsContext.assets.open("net.plainnotes.app.data.NotesDatabase/$version.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(d: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) { val e = entities.getJSONObject(i); val t = e.getString("tableName")
                        d.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", t)); val indices = e.optJSONArray("indices") ?: JSONArray()
                        for (j in 0 until indices.length()) d.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", t)) }
                    val setup = schema.getJSONArray("setupQueries"); for (i in 0 until setup.length()) d.execSQL(setup.getString(i))
                }
                override fun onUpgrade(d: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture must not migrate")
            }).build()
            var migrated: NotesDatabase? = null
            try {
                factory(key.clone()).create(config).use { h ->
                    h.writableDatabase.execSQL("INSERT INTO day_note(date,text) VALUES ('2026-01-01','SYNTHETIC_CHAIN_SENTINEL')")
                    h.writableDatabase.execSQL("INSERT INTO medication(id,name,molecule,unit,dose_per_intake,container_capacity,site_rotation,notifications_on,active,sort_order) VALUES (1,'Synthetic old','OTHER','MG',1,2,0,0,1,0)")
                    h.writableDatabase.execSQL("INSERT INTO supply_container(id,medication_id,capacity,initial_used_amount,used_amount,state) VALUES (1,1,2,0,0,'SEALED')")
                }
                migrated = open(name); val d = migrated.openHelper.writableDatabase; assertEquals(18, d.version)
                Ansm03b0CandidateStore.create(d)
                d.query("SELECT text FROM day_note").use { assertTrue(it.moveToFirst()); assertEquals("SYNTHETIC_CHAIN_SENTINEL", it.getString(0)) }
                d.query("SELECT COUNT(*) FROM ansm_lab_container").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                d.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
                checkEncryption(name)
            } finally { migrated?.close(); context.deleteDatabase(name) }
        }
    }
}
