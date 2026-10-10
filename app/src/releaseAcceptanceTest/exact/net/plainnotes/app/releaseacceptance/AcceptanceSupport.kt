package net.plainnotes.app.releaseacceptance

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assume
import java.io.File
import java.security.MessageDigest

/**
 * P1 release acceptance helpers (only compiled through scripts/release-acceptance/acceptance.init.gradle).
 * Everything here touches synthetic data on a disposable emulator only; see [requireDisposableDevice].
 */
object AcceptanceSupport {
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val app: Context get() = instrumentation.targetContext
    val expected: JSONObject by lazy { JSONObject(asset("acceptance-expected.json")) }
    val condition: JSONObject by lazy { JSONObject(asset("acceptance-condition.json")) }
    val scenario: String get() = condition.getString("scenario")
    val shrunk: Boolean get() = condition.getBoolean("resource_shrink")
    val arm64Only: Boolean get() = condition.getJSONArray("abi_filter").length() > 0

    fun asset(name: String) = instrumentation.context.assets.open(name).bufferedReader().use { it.readText() }

    /** Refuses to run on anything but an emulator: these tests create and inspect the app's own storage. */
    fun requireDisposableDevice() {
        val emulator = Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.PRODUCT.contains("sdk")
        val allowed = InstrumentationRegistry.getArguments().getString("hrtDisposableDevice") == "yes"
        Assume.assumeTrue("P1 acceptance only runs on a disposable emulator explicitly marked by the orchestrator", emulator && allowed)
    }

    fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Evidence is written where the orchestrator can `adb pull` it without root or run-as. */
    fun evidence(name: String, json: JSONObject) {
        val dir = File(app.externalMediaDirs.first(), "p1-acceptance").apply { mkdirs() }
        File(dir, "$name.json").writeText(json.toString(1))
    }
}
