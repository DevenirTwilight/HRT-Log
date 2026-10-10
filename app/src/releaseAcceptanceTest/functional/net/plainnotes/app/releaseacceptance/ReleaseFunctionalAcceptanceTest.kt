package net.plainnotes.app.releaseacceptance

import android.app.NotificationManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.export.ExportData
import net.plainnotes.app.export.PdfReport
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.app
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.evidence
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.sha256
import net.plainnotes.app.reminder.ReminderEntryPoint
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.TimeZone

/**
 * mode=functional: the scenario's R8 build plus scripts/release-acceptance/functional-keep.pro so the app's own
 * classes can be called in-process. Resources, native libraries and third-party code are processed as in the scenario.
 * Synthetic data only; every database here uses a dedicated p1-acceptance-* name except [appDatabasePersistsAcrossFreshAccessObjects],
 * which adds rows to the app's own (emulator-only, synthetic) PRIMARY database and never deletes or recreates it.
 */
class ReleaseFunctionalAcceptanceTest {
    private val names = listOf("p1-acceptance-migration.db", "p1-acceptance-restore-a.db", "p1-acceptance-restore-b.db")
    private val key = "p1-synthetic-room-key-0123456789".toByteArray()
    private fun wipeSynthetic() = names.forEach { app.deleteDatabase(it) }
    @Before fun guard() { AcceptanceSupport.requireDisposableDevice(); wipeSynthetic() }
    @After fun cleanup() = wipeSynthetic()

    private fun encryptedRoom(name: String, passphrase: ByteArray = key) = Room.databaseBuilder(app, NotesDatabase::class.java, name)
        .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf())).addMigrations(*allMigrations { LocalDate.of(2026, 10, 1) })
        .addCallback(SchemaGuards).build()
    private fun header(file: File) = file.inputStream().use { s -> ByteArray(16).also { s.read(it) } }.toString(Charsets.ISO_8859_1)
    private fun repoOn(db: NotesDatabase) = NotesRepository(object : DatabaseAccess(app) { override fun get(space: Space) = db })

    @Test fun appDatabasePersistsAcrossFreshAccessObjects() {
        System.loadLibrary("sqlcipher")
        val marker = "P1 synthetic ${System.nanoTime()}"
        val first = DatabaseAccess(app)
        try {
            runBlocking { first.get().dao().insertMedication(MedicationEntity(name = marker, molecule = "OTHER", unit = "MG", dose_per_intake = 1.0, container_capacity = 10.0,
                soon_alert_minutes = 0, late_after_minutes = 10, site_rotation = false, notifications_on = false, active = false, sort_order = 99)) }
        } finally { first.close() }
        val file = app.getDatabasePath(Space.PRIMARY.file)
        assertFalse(header(file).startsWith("SQLite format 3"))
        val versions = mutableListOf<Int>()
        repeat(3) {
            val access = DatabaseAccess(app)
            try {
                val db = access.get()
                versions += db.openHelper.writableDatabase.version
                assertTrue(runBlocking { db.dao().medications() }.any { it.name == marker })
                db.openHelper.writableDatabase.query("PRAGMA cipher_integrity_check").use { assertEquals(0, it.count) }
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally { access.close() }
        }
        assertEquals(listOf(9, 9, 9), versions)
        evidence("app-database", JSONObject().put("schema_versions", JSONArray(versions)).put("plaintext_header", false).put("reopen_cycles", 3))
    }

    /** Schema 1 created from the exported Room schema, encrypted with SQLCipher, then opened by the shipped migrations. */
    @Test fun encryptedSchemaOneMigratesToNineAndRejectsAWrongKey() {
        System.loadLibrary("sqlcipher")
        val schema = JSONObject(AcceptanceSupport.instrumentation.context.assets.open("net.plainnotes.app.data.NotesDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = app.getDatabasePath("p1-acceptance-migration.db").apply { parentFile!!.mkdirs() }
        net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(file, key, null, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i); val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { ix -> for (j in 0 until ix.length()) db.execSQL(ix.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            schema.optJSONArray("views")?.let { v -> for (j in 0 until v.length()) v.getJSONObject(j).let { db.execSQL(it.getString("createSql").replace("\${VIEW_NAME}", it.getString("viewName"))) } }
            val setup = schema.getJSONArray("setupQueries"); for (j in 0 until setup.length()) db.execSQL(setup.getString(j))
            db.execSQL("INSERT INTO checkin_item (id,builtin_key,custom_label,enabled,sort_order) VALUES (1,'MOOD',NULL,1,0),(2,'PAIN',NULL,1,1),(3,'APPETITE',NULL,1,2)")
            db.execSQL("INSERT INTO checkin_score (date,item_id,value) VALUES ('2026-09-28',1,4),('2026-09-20',2,2),('2025-01-01',3,3)")
            db.version = 1
        }
        assertFalse(header(file).startsWith("SQLite format 3"))
        val db = encryptedRoom("p1-acceptance-migration.db")
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(9, sql.version)
            sql.query("SELECT builtin_key FROM checkin_item WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals("DAY_MOOD", it.getString(0)) }
            sql.query("SELECT value FROM checkin_score WHERE item_id=1").use { assertTrue(it.moveToFirst()); assertEquals(4, it.getInt(0)) }
            sql.query("SELECT enabled FROM checkin_item WHERE id=2").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            sql.query("SELECT enabled FROM checkin_item WHERE id=3").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            sql.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            sql.query("PRAGMA cipher_integrity_check").use { assertEquals(0, it.count) }
            sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close() }
        val before = sha256(file.readBytes())
        val wrong = encryptedRoom("p1-acceptance-migration.db", "wrong-passphrase-0123456789abcdef".toByteArray())
        val rejected = runCatching { wrong.openHelper.writableDatabase }.also { wrong.close() }
        assertTrue("Wrong passphrase must not open the migrated database", rejected.isFailure)
        assertEquals("Rejected key must leave the file untouched", before, sha256(file.readBytes()))
        evidence("migration", JSONObject().put("from", 1).put("to", 9).put("wrong_key_error", rejected.exceptionOrNull()?.javaClass?.name))
    }

    private fun fixture() = AcceptanceSupport.instrumentation.context.assets.open("backup/v1-synthetic.pnbak").use { it.readBytes() }
    private fun tables(backup: ByteArray, password: String) = JSONObject(String(BackupCodec.decrypt(backup, password.toCharArray()), Charsets.UTF_8))

    /** Existing Argon2id + AES-GCM format: schema-1 fixture written by 0.2.0, restore, re-export, restore again. */
    @Test fun legacyBackupRestoresAndRoundTripsWhileWrongPasswordAndDamageAreRejected() = runBlocking {
        val old = fixture()
        assertEquals("PNBAK1", String(old.copyOfRange(0, 6), Charsets.US_ASCII))
        val a = encryptedRoom("p1-acceptance-restore-a.db"); val b = encryptedRoom("p1-acceptance-restore-b.db")
        try {
            val repoA = repoOn(a); val repoB = repoOn(b)
            assertTrue(runCatching { repoA.restoreBackup(old, "wrong-password".toCharArray(), LocalDate.of(2026, 10, 1)) }.exceptionOrNull() is BackupCodec.WrongPassword)
            repoA.restoreBackup(old, "synthetic-backup-pw".toCharArray(), LocalDate.of(2026, 10, 1))
            val scores = mutableMapOf<String, Map<String, Int>>()
            a.openHelper.writableDatabase.query("SELECT i.builtin_key, s.date, s.value FROM checkin_score s JOIN checkin_item i ON i.id=s.item_id WHERE i.builtin_key IS NOT NULL").use { c ->
                while (c.moveToNext()) scores[c.getString(0)] = (scores[c.getString(0)] ?: emptyMap()) + (c.getString(1) to c.getInt(2)) }
            assertEquals(mapOf("2026-09-28" to 4, "2026-06-01" to 2), scores["DAY_MOOD"])
            assertEquals(mapOf("2026-09-29" to 3), scores["DAY_ENERGY"])
            assertEquals(mapOf("2026-05-02" to 5), scores["DAY_SLEEP"])
            a.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }

            val password = "p1-synthetic-backup-password"
            val fresh = repoA.exportBackup(password.toCharArray())
            assertEquals("PNBAK1", String(fresh.copyOfRange(0, 6), Charsets.US_ASCII))
            val exported = tables(fresh, password)
            assertEquals(BackupCodec.FORMAT_VERSION, exported.getInt("format")); assertEquals(9, exported.getInt("schema"))
            assertTrue(runCatching { BackupCodec.decrypt(fresh, "not-the-password".toCharArray()) }.exceptionOrNull() is BackupCodec.WrongPassword)
            val damaged = fresh.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 0x40).toByte() }
            assertTrue(runCatching { BackupCodec.decrypt(damaged, password.toCharArray()) }.exceptionOrNull() is BackupCodec.WrongPassword)
            assertTrue(runCatching { BackupCodec.decrypt(fresh.copyOfRange(0, 30), password.toCharArray()) }.exceptionOrNull() is BackupCodec.BadFile)
            val notBackup = fresh.copyOf().also { it[0] = 'X'.code.toByte() }
            assertTrue(runCatching { BackupCodec.decrypt(notBackup, password.toCharArray()) }.exceptionOrNull() is BackupCodec.BadFile)

            repoB.restoreBackup(fresh, password.toCharArray(), LocalDate.of(2026, 10, 1))
            val again = tables(repoB.exportBackup(password.toCharArray()), password)
            assertEquals("Restored data must re-export identically", exported.getJSONObject("tables").toString(), again.getJSONObject("tables").toString())
            evidence("backup", JSONObject().put("legacy_fixture_sha256", sha256(old)).put("legacy_schema", 1).put("restored_schema", 9)
                .put("layout", "PNBAK1|salt16|nonce12|AES-GCM ciphertext+tag16").put("fresh_size", fresh.size).put("round_trip_tables", exported.getJSONObject("tables").length()))
        } finally { a.close(); b.close() }
    }

    private val now = System.currentTimeMillis()
    private fun exportData() = ExportData(
        listOf(MedicationEntity(1, "Synthetic gel", "E2", "GEL", "MG", 1.5, 80.0, null, 15, 120, false, null, true, true, 0)),
        mapOf(1L to ProfileEntity(1, "E2", "gel")),
        (1..30).map { d -> RecordEntity(d.toLong(), 1, taken_utc = now - d * 86_400_000L, taken_zone = "UTC", actual_dose = 1.5, status = "ON_TIME", origin = "APP", revision = 1,
            config_snapshot = JSONObject().put("name", "Synthetic gel").put("molecule", "E2").put("unit", "MG").put("route", "GEL").toString()) },
        listOf(LabValueEntity(1, "E2", 150.0, "pg/mL", now - 7_200_000, "UTC", 50.0, 300.0, "pg/mL")),
        listOf(CheckinItemEntity(1, "DAY_MOOD", null, true, 0)), listOf(CheckinScoreEntity(LocalDate.now().toString(), 1, 4)),
        emptyList(), mapOf(1L to "daily"), { "Mood" })

    @Test fun pdfReportRendersInAllFourLocales() {
        val out = JSONObject(); val zone = TimeZone.getDefault()
        for (tag in listOf("en", "zh-CN", "zh-TW", "fr")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) })
            val file = File(app.cacheDir, "p1-acceptance-$tag.pdf")
            try {
                file.outputStream().use { PdfReport.write(context, exportData(), 90, null, it) }
                val bytes = file.readBytes(); assertEquals("%PDF-", String(bytes.copyOfRange(0, 5)))
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    assertTrue(pdf.pageCount >= 1)
                    val page = pdf.openPage(0); val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close()
                    val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                    val inked = pixels.count { it != Color.WHITE }
                    assertTrue("$tag: first page must contain rendered content", inked > 1000)
                    out.put(tag, JSONObject().put("bytes", bytes.size).put("pages", pdf.pageCount).put("inked_pixels_page1", inked))
                }
            } finally { file.delete() }
        }
        out.put("zone", zone.id); evidence("pdf", out)
    }

    /** The app's own "test reminder" path through the Hilt graph, AlarmManager and the notification channel. */
    @Test fun reminderIsScheduledAndPostedWhenNotificationsAreAllowed() {
        val phase = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("hrtNotificationPhase") ?: "granted"
        val coordinator = dagger.hilt.android.EntryPointAccessors.fromApplication(app, ReminderEntryPoint::class.java).coordinator()
        val nm = app.getSystemService(NotificationManager::class.java); nm.cancel(100)
        val granted = app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        assertEquals("orchestrator sets the permission for phase $phase", phase == "granted", granted)
        runBlocking { coordinator.testReminder() }
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline && nm.activeNotifications.none { it.id == 100 }) Thread.sleep(1000)
        val posted = nm.activeNotifications.any { it.id == 100 }
        evidence("reminder-$phase", JSONObject().put("permission_granted", granted).put("posted", posted).put("exact_allowed", coordinator.canExact()))
        assertEquals("Notification posted only when permission is granted", granted, posted)
        nm.cancel(100)
    }
}
