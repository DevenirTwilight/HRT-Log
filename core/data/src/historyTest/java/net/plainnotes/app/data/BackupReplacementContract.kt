package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.RuleKind
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** Production RawData/Repository contract, synthetic disk databases only. Shared by Robolectric and SQLCipher. */
abstract class BackupReplacementContract {
    protected abstract fun factory(key: ByteArray): SupportSQLiteOpenHelper.Factory
    protected open fun encrypted(name: String) {}
    protected val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var name: String
    private val key = ByteArray(32) { (it + 41).toByte() }
    private val raw get() = db.openHelper.writableDatabase
    private val at = Instant.parse("2026-03-27T09:00:00Z")
    private val password get() = "SYNTH-replacement-only".toCharArray()
    private fun open() {
        db = Room.databaseBuilder(context, NotesDatabase::class.java, name).openHelperFactory(factory(key.clone()))
            .addMigrations(*allMigrations()).addCallback(SchemaGuards).allowMainThreadQueries().build()
        db.openHelper.writableDatabase
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    }
    @Before fun setup() { name = "synthetic-replacement-${UUID.randomUUID()}.db"; open() }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private suspend fun ledger(label: String): Long {
        val med = repo.saveMedication(MedicationEntity(name = label, molecule = "OTHER", unit = "TABLET", dose_per_intake = 1.0,
            container_capacity = 10.0, soon_alert_minutes = 0, late_after_minutes = 60, site_rotation = false,
            notifications_on = false, active = true, sort_order = 0), null, RuleKind.EVERY_N_DAYS, 1, listOf(LocalTime.NOON), emptySet(), at)
        repo.addContainers(med, 10.0, 1, true)
        repo.unscheduled(med, at, 1.0)
        val record = db.dao().records().last()
        repo.editRecord(record.id, at.plusSeconds(60), 1.5, at.plusSeconds(86400))
        assertEquals(listOf("CONSUME", "REVERSE", "CONSUME"), db.dao().supplyFor(record.id).map { it.kind })
        return med
    }
    private fun business() = RawData.dump(raw).toString()
    private fun guards(): List<Pair<String, String>> = raw.query("SELECT name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name").use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }
    private fun foreignKeys() = raw.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    private fun assertImmutable() {
        try { raw.execSQL("UPDATE supply_transaction SET reason='changed'"); fail("Ledger guard lost") }
        catch (e: Exception) { assertTrue(e.message.orEmpty().contains("Append-only supply ledger")) }
    }
    @Test fun nonemptyReverseLedgerRestoresIntoItselfTwiceAndReopens() = runBlocking {
        ledger("SYNTH-original")
        val before = business(); val expectedGuards = guards(); val backup = repo.exportBackup(password)
        repeat(2) {
            repo.restoreBackup(backup, password)
            assertEquals(before, business()); assertEquals(expectedGuards, guards()); foreignKeys(); assertImmutable()
        }
        db.close(); encrypted(name); open()
        assertEquals(before, business()); foreignKeys(); assertImmutable(); backup.fill(0)
    }
    @Test fun nonemptyDifferentTargetIsCompletelyReplacedWithoutMixingIds() = runBlocking {
        ledger("SYNTH-source"); val wanted = business(); val backup = repo.exportBackup(password)
        // A second synthetic dataset in a different database deliberately starts integer IDs at 1 again.
        db.close(); context.deleteDatabase(name); name = "synthetic-replacement-target-${UUID.randomUUID()}.db"; open()
        ledger("SYNTH-target")
        assertNotEquals(wanted, business()); repo.restoreBackup(backup, password)
        assertEquals(wanted, business()); foreignKeys(); assertImmutable(); backup.fill(0)
    }
    @Test fun deleteAllStructuredRowsAlsoHonoursTheSelfReference() = runBlocking {
        ledger("SYNTH-clear"); val expected = guards()
        db.withTransaction { RawData.clear(raw) }
        assertEquals(0, RawData.dump(raw).getJSONArray("supply_transaction").length())
        assertEquals(0, db.dao().medications().size); assertEquals(expected, guards()); foreignKeys()
    }
    @Test fun maintenancePreservesPreviouslyInstalledNonAttachmentGuards() = runBlocking {
        raw.execSQL("CREATE TRIGGER synthetic_extra_guard BEFORE UPDATE ON day_note BEGIN SELECT RAISE(ABORT,'SYNTHETIC_EXTRA_GUARD'); END")
        val expected = guards(); val backup = repo.exportBackup(password)
        repo.restoreBackup(backup, password)
        assertEquals(expected, guards()); foreignKeys(); backup.fill(0)
    }
    private fun identityState(): String = raw.query("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'attachment_%' ORDER BY name").use { c ->
        val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
        JSONObject().apply { names.forEach { table ->
            val rows = org.json.JSONArray()
            raw.query("SELECT * FROM `$table`").use { values -> while (values.moveToNext()) rows.put(org.json.JSONArray((0 until values.columnCount).map { i ->
                if (values.isNull(i)) JSONObject.NULL else values.getString(i)
            })) }
            put(table, rows)
        } }.toString()
    }
    @Test fun invalidSourceRelationshipsAndDuplicateIdsLeaveEveryTargetRowAndGuardUntouched() = runBlocking {
        ledger("SYNTH-intact-target")
        val before = business(); val expectedGuards = guards(); val identities = identityState()
        val original = RawData.dump(raw)
        val supply = original.getJSONArray("supply_transaction")
        val reverseIndex = (0 until supply.length()).first { supply.getJSONObject(it).getString("kind") == "REVERSE" }
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONArray("supply_transaction").getJSONObject(reverseIndex).put("reversal_of_id", 99999999L) },
            { it.getJSONArray("supply_transaction").getJSONObject(reverseIndex).let { row -> row.put("reversal_of_id", row.getLong("id")) }; Unit },
            { it.getJSONArray("medication").put(JSONObject(it.getJSONArray("medication").getJSONObject(0).toString())); Unit },
            { it.getJSONArray("supply_transaction").getJSONObject(reverseIndex).put("used_delta", -19.0); Unit }
        )
        mutations.forEach { mutate ->
            val invalid = JSONObject(original.toString()); mutate(invalid)
            try { db.withTransaction { RawData.restore(context, raw, invalid) }; fail("Invalid replacement accepted") }
            catch (_: Exception) { }
            assertEquals(before, business()); assertEquals(expectedGuards, guards()); assertEquals(identities, identityState())
            foreignKeys(); assertImmutable()
        }
    }
    @Test fun failureAfterReplacementRollsBackRowsIdentitiesAndTriggerDefinitions() = runBlocking {
        ledger("SYNTH-rollback-target")
        raw.execSQL("CREATE TRIGGER synthetic_extra_guard BEFORE UPDATE ON day_note BEGIN SELECT RAISE(ABORT,'SYNTHETIC_EXTRA_GUARD'); END")
        val before = business(); val expected = guards(); val identities = identityState()
        val replacement = RawData.dump(raw).apply { getJSONArray("medication").getJSONObject(0).put("name", "SYNTH-other-source") }
        val injected = IllegalStateException("SYNTHETIC_BEFORE_COMMIT_FAILURE")
        try {
            db.withTransaction { RawData.restore(context, raw, replacement); assertNotEquals(before, business()); throw injected }
            fail("Injected failure swallowed")
        } catch (failure: IllegalStateException) {
            // Coroutines may copy standard exceptions during stacktrace recovery. The injected failure must still escape.
            assertEquals(injected.message, failure.message)
        }
        assertEquals(before, business()); assertEquals(expected, guards()); assertEquals(identities, identityState())
        foreignKeys(); assertImmutable()
        db.close(); encrypted(name); open()
        assertEquals(before, business()); assertEquals(expected, guards()); assertEquals(identities, identityState())
    }
    @Test fun inputLedgerOrderDoesNotChangeIdentityOrFrozenConsumption() = runBlocking {
        ledger("SYNTH-reordered")
        val before = business(); val tables = RawData.dump(raw)
        val rows = tables.getJSONArray("supply_transaction")
        tables.put("supply_transaction", org.json.JSONArray((rows.length()-1 downTo 0).map { rows.getJSONObject(it) }))
        db.withTransaction { RawData.restore(context, raw, tables) }
        assertEquals(before, business()); foreignKeys(); assertImmutable()
    }

    @Test fun streamedBusinessDigestIsStableAcrossReorderedRecoveryAndDetectsChangedFields()=runBlocking {
        ledger("SYNTHETIC digest")
        val before=db.withTransaction{SyntheticRestoreDigest.read(raw)}
        val tables=RawData.dump(raw);val rows=tables.getJSONArray("supply_transaction")
        tables.put("supply_transaction",org.json.JSONArray((rows.length()-1 downTo 0).map{rows.getJSONObject(it)}))
        db.withTransaction{RawData.restore(context,raw,tables);assertEquals(before,SyntheticRestoreDigest.read(raw))}
        db.withTransaction {
            raw.execSQL("UPDATE medication SET name='SYNTHETIC changed field'")
            assertNotEquals(before,SyntheticRestoreDigest.read(raw))
        }
    }
    @Test fun indexedInputMatchesFrozenDomRecoveryDecisionsAndWholeData()=runBlocking {
        ledger("SYNTHETIC differential target")
        val before=business();val guardsBefore=guards();val original=RawData.dump(raw)
        class Rollback:RuntimeException()
        suspend fun outcome(tables:BackupTables):Pair<Boolean,String> {
            var result:Pair<Boolean,String>?=null
            try {db.withTransaction {
                try {RawData.restoreRows(context,raw,tables);result=true to business()}
                catch(e:Exception){result=false to e.javaClass.name}
                throw Rollback()
            }}catch(_:Rollback){}
            assertEquals(before,business());assertEquals(guardsBefore,guards());foreignKeys()
            return checkNotNull(result)
        }
        val variants=listOf<(JSONObject)->Unit>(
            {},
            {val rows=it.getJSONArray("supply_transaction");it.put("supply_transaction",org.json.JSONArray((rows.length()-1 downTo 0).map{n->rows.getJSONObject(n)}));Unit},
            {it.getJSONArray("medication").getJSONObject(0).put("unknown_column",1);Unit},
            {it.getJSONArray("supply_transaction").getJSONObject(1).put("reversal_of_id",9999999);Unit},
            {it.getJSONArray("medication").put(JSONObject(it.getJSONArray("medication").getJSONObject(0).toString()));Unit},
            {it.getJSONArray("medication").getJSONObject(0).put("dose_per_intake","not a number");Unit})
        for(mutate in variants) {
            val candidate=JSONObject(original.toString());mutate(candidate)
            val bytes=JSONObject().put("format",1).put("schema",12).put("tables",candidate).toString().toByteArray()
            val frozen=FrozenRestoreJsonReader.read(bytes).getJSONObject("tables")
            val old=outcome(BackupTables.of(frozen))
            BackupJsonReader.indexed(bytes).use {assertEquals(old,outcome(it.tables))}
            assertTrue(bytes.all{it==0.toByte()})
        }
    }

}
