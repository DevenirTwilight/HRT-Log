package net.plainnotes.app.releaseacceptance

import android.app.LocaleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.Process
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.app
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.evidence
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.expected
import net.plainnotes.app.releaseacceptance.AcceptanceSupport.sha256
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Runs against the scenario's production R8 output (mode=exact: no extra keep rules). It only uses framework APIs,
 * SQLCipher (kept by app/proguard-rules.pro), resources looked up by name and the UI through UiAutomator, so
 * nothing here depends on how R8 renamed the app's own code. Synthetic data only.
 */
class ReleaseRuntimeAcceptanceTest {
    private val pkg get() = app.packageName
    private val device get() = UiDevice.getInstance(AcceptanceSupport.instrumentation)
    private val dbFile get() = app.getDatabasePath("p1-acceptance-sqlcipher.db")

    @Before fun guard() { AcceptanceSupport.requireDisposableDevice() }
    @After fun cleanup() {
        // Only the synthetic file this class created; never the app's own notes.db.
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    }

    /** Which native library file the process actually mapped, read from the kernel, not from the APK listing. */
    private fun mappedSqlcipher(): List<String> = File("/proc/self/maps").readLines()
        .filter { it.contains("libsqlcipher.so") }.map { it.substring(it.indexOf('/')) }.distinct()

    @Test fun nativeSqlcipherIsLoadedFromTheInstalledApkForThisProcessAbi() {
        System.loadLibrary("sqlcipher")
        val maps = mappedSqlcipher()
        val info = app.applicationInfo
        val processAbi = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS.first() else Build.SUPPORTED_32_BIT_ABIS.first()
        val apkLibs = ZipFile(info.sourceDir).use { zip -> zip.entries().toList().map { it.name }.filter { it.startsWith("lib/") }.sorted() }
        evidence("native-abi", JSONObject().put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("process_is_64bit", Process.is64Bit()).put("os_arch", System.getProperty("os.arch")).put("process_abi", processAbi)
            .put("source_dir", info.sourceDir).put("native_library_dir", info.nativeLibraryDir).put("mapped_sqlcipher", JSONArray(maps))
            .put("apk_native_entries", JSONArray(apkLibs)).put("scenario", AcceptanceSupport.scenario)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT} ${Build.FINGERPRINT}"))
        assertTrue("libsqlcipher.so must be mapped after loadLibrary: $maps", maps.isNotEmpty())
        // Mapped either from the app's extracted lib dir or directly from its base.apk (uncompressed, page aligned).
        assertTrue("SQLCipher must come from the app under test, not the test APK: $maps",
            maps.all { it.startsWith(info.nativeLibraryDir) || it.startsWith(info.sourceDir + "!/lib/") })
        val abiDir = mapOf("arm64-v8a" to listOf("arm64", "arm64-v8a"), "x86_64" to listOf("x86_64"), "armeabi-v7a" to listOf("arm", "armeabi-v7a"), "x86" to listOf("x86"))
        assertTrue("Mapped library must match the process ABI $processAbi: $maps",
            maps.all { path -> abiDir.getValue(processAbi).any { path.contains("/lib/$it/") } })
        if (AcceptanceSupport.arm64Only) assertEquals(listOf("lib/arm64-v8a/libandroidx.graphics.path.so", "lib/arm64-v8a/libsqlcipher.so"), apkLibs)
        else assertEquals(8, apkLibs.size)
    }

    @Test fun sqlcipherCreatesReopensRejectsWrongKeyAndKeepsIntegrity() {
        System.loadLibrary("sqlcipher")
        val key = "p1-synthetic-key-not-a-user-secret"
        dbFile.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, key, null, null, null).use { db ->
            db.execSQL("CREATE TABLE synthetic(id INTEGER PRIMARY KEY, label TEXT NOT NULL, payload BLOB NOT NULL)")
            db.beginTransaction()
            try { for (i in 1..500) db.execSQL("INSERT INTO synthetic(id,label,payload) VALUES (?,?,?)", arrayOf<Any>(i, "synthetic-$i", ByteArray(64) { (i + it).toByte() })); db.setTransactionSuccessful() }
            finally { db.endTransaction() }
        }
        val header = dbFile.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        assertFalse("Database file must not be plaintext SQLite", String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3"))
        assertFalse(dbFile.readBytes().toString(Charsets.ISO_8859_1).contains("synthetic-250"))
        val sums = mutableListOf<Long>()
        var cipherVersion = ""; var provider = ""
        repeat(5) { // five independent open/close cycles stand in for app restarts at the SQLCipher layer
            SQLiteDatabase.openDatabase(dbFile.path, key, null, SQLiteDatabase.OPEN_READWRITE, null, null).use { db ->
                db.rawQuery("SELECT count(*), sum(id), sum(length(payload)) FROM synthetic", null).use { c -> assertTrue(c.moveToFirst()); assertEquals(500, c.getInt(0)); sums += c.getLong(1); assertEquals(500L * 64, c.getLong(2)) }
                db.rawQuery("PRAGMA integrity_check", null).use { c -> assertTrue(c.moveToFirst()); assertEquals("ok", c.getString(0)) }
                db.rawQuery("PRAGMA cipher_integrity_check", null).use { c -> assertEquals("cipher_integrity_check must report no page errors", 0, c.count) }
                db.rawQuery("PRAGMA cipher_version", null).use { c -> assertTrue(c.moveToFirst()); cipherVersion = c.getString(0) }
                db.rawQuery("PRAGMA cipher_provider", null).use { c -> if (c.moveToFirst()) provider = c.getString(0) }
                db.execSQL("INSERT INTO synthetic(id,label,payload) VALUES (?,?,?)", arrayOf<Any>(1000 + it, "cycle-$it", ByteArray(1)))
                db.execSQL("DELETE FROM synthetic WHERE id=?", arrayOf<Any>(1000 + it))
            }
        }
        assertEquals(List(5) { 125250L }, sums)
        val before = sha256(dbFile.readBytes())
        val wrong = runCatching { SQLiteDatabase.openDatabase(dbFile.path, "wrong-$key", null, SQLiteDatabase.OPEN_READWRITE, null, null).use { db -> db.rawQuery("SELECT count(*) FROM synthetic", null).use { it.moveToFirst() } } }
        assertTrue("A wrong key must be rejected", wrong.isFailure)
        assertEquals("A rejected key must not modify the database", before, sha256(dbFile.readBytes()))
        SQLiteDatabase.openDatabase(dbFile.path, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db -> db.rawQuery("SELECT count(*) FROM synthetic", null).use { c -> c.moveToFirst(); assertEquals(500, c.getInt(0)) } }
        evidence("sqlcipher", JSONObject().put("cipher_version", cipherVersion).put("cipher_provider", provider).put("reopen_cycles", 5)
            .put("wrong_key_error", wrong.exceptionOrNull()?.javaClass?.name).put("rows", 500).put("mapped", JSONArray(mappedSqlcipher())))
        assertTrue(cipherVersion.startsWith("4."))
    }

    @Test fun namedJsonResourcesAreByteIdenticalInTheApkAndThroughTheAppClassLoader() {
        val want = expected.getJSONObject("named_json_sha256")
        val result = JSONObject()
        ZipFile(app.applicationInfo.sourceDir).use { zip ->
            for (name in want.keys()) {
                val entry = zip.getEntry(name); assertNotNull("$name missing from APK root", entry)
                val inApk = sha256(zip.getInputStream(entry).readBytes())
                val viaLoader = app.classLoader.getResourceAsStream(name)?.use { sha256(it.readBytes()) }
                result.put(name, JSONObject().put("apk", inApk).put("class_loader", viaLoader).put("expected", want.getString(name)))
                assertEquals(name, want.getString(name), inApk); assertEquals(name, want.getString(name), viaLoader)
            }
        }
        evidence("named-json", result)
    }

    private fun localized(tag: String) = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) })

    @Test fun everyReferencedStringResolvesWithTheSourceValueInAllFourLocales() {
        val strings = expected.getJSONObject("strings")
        val referenced = expected.getJSONObject("referenced_in_source").getJSONArray("string").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        val report = JSONObject(); val problems = mutableListOf<String>()
        for ((tag, key) in listOf("en" to "en", "zh-CN" to "zh-CN", "zh-TW" to "zh-TW", "fr" to "fr")) {
            val res = localized(tag).resources; val want = strings.getJSONObject(key)
            val missing = mutableListOf<String>(); val mismatched = mutableListOf<String>(); val dump = JSONObject(); var checked = 0
            for (name in want.keys()) {
                val id = res.getIdentifier(name, "string", pkg)
                if (id == 0) { missing += name; if (name in referenced) problems += "$tag: referenced string $name missing"; continue }
                val actual = res.getString(id); dump.put(name, actual)
                if (!want.isNull(name)) { checked++; if (actual != want.getString(name)) { mismatched += name; problems += "$tag: $name value differs" } }
            }
            report.put(tag, JSONObject().put("source_strings", want.length()).put("value_checked", checked).put("missing", JSONArray(missing.sorted()))
                .put("mismatched", JSONArray(mismatched.sorted())).put("resolved", dump))
            if (!AcceptanceSupport.shrunk) assertTrue("$tag: without resource shrinking no source string may be missing: $missing", missing.isEmpty())
        }
        evidence("strings", report)
        assertTrue(problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    @Test fun launcherAliasesAndTheirIconsAndLabelsLoad() {
        val pm = app.packageManager; val components = expected.getJSONObject("launcher_components"); val out = JSONObject()
        for (name in components.keys()) {
            val spec = components.getJSONObject(name); val component = ComponentName(pkg, name)
            val info = pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
            val icon = info.loadIcon(pm); val label = info.loadLabel(pm).toString()
            val iconName = app.resources.getResourceEntryName(info.iconResource)
            val state = pm.getComponentEnabledSetting(component)
            val enabled = if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) info.enabled else state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            out.put(name, JSONObject().put("icon", iconName).put("label", label).put("enabled", enabled).put("icon_size", "${icon.intrinsicWidth}x${icon.intrinsicHeight}"))
            assertEquals(spec.getString("icon"), iconName)
            assertEquals(app.getString(app.resources.getIdentifier(spec.getString("label"), "string", pkg)), label)
            assertTrue(icon.intrinsicWidth > 0 && icon.intrinsicHeight > 0)
            assertEquals("$name launcher state on a fresh install", spec.getBoolean("enabled_by_default"), enabled)
        }
        for (kind in listOf("drawable", "mipmap")) {
            val names = expected.getJSONObject("files").getJSONArray(kind)
            for (i in 0 until names.length()) {
                val id = app.resources.getIdentifier(names.getString(i), kind, pkg); assertTrue("$kind/${names.getString(i)}", id != 0)
                assertNotNull(app.getDrawable(id))
            }
        }
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), 0).map { it.activityInfo.name }
        out.put("visible_launchers", JSONArray(launchers))
        assertEquals(listOf("net.plainnotes.app.Launcher"), launchers)
        evidence("launchers", out)
    }

    private fun text(key: String, locale: String) = expected.getJSONObject("strings").getJSONObject(locale).getString(key)

    /** Cold start through the real launcher alias, then each app language via the framework per-app locale API. */
    @Test fun mainEntryStartsAndShowsTranslatedNavigationInAllFourLocales() {
        val manager = app.getSystemService(LocaleManager::class.java)
        Assume.assumeTrue("Per-app locales need API 33+", Build.VERSION.SDK_INT >= 33)
        val out = JSONObject()
        try {
            for ((tag, key) in listOf("en" to "en", "zh-CN" to "zh-CN", "zh-TW" to "zh-TW", "fr" to "fr")) {
                manager.applicationLocales = LocaleList.forLanguageTags(tag)
                device.pressHome()
                app.startActivity(app.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                assertTrue("$tag: app window", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 20_000))
                assertTrue("$tag: FAB '${text("add", key)}'", device.wait(Until.hasObject(By.text(text("add", key))), 20_000))
                device.findObject(By.desc(text("menu", key))).click()
                assertTrue("$tag: drawer settings", device.wait(Until.hasObject(By.text(text("settings", key))), 10_000))
                device.findObject(By.text(text("settings", key))).click()
                assertTrue("$tag: settings screen title", device.wait(Until.hasObject(By.text(text("settings", key))), 10_000))
                out.put(tag, JSONObject().put("add", text("add", key)).put("settings", text("settings", key)).put("result", "shown"))
                device.pressBack()
            }
        } finally { manager.applicationLocales = LocaleList.getEmptyLocaleList(); device.pressHome() }
        // The app opened its own encrypted database while starting; it must not be plaintext.
        val db = app.getDatabasePath("notes.db")
        out.put("notes_db_exists", db.exists())
        if (db.exists()) {
            val header = db.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
            out.put("notes_db_plaintext_header", String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3"))
            assertFalse(String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3"))
        }
        out.put("default_locale", Locale.getDefault().toLanguageTag())
        evidence("ui-locales", out)
    }
}
