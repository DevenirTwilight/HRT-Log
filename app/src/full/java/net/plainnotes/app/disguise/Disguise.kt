package net.plainnotes.app.disguise

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import net.plainnotes.app.R
import net.plainnotes.app.data.Space
import net.plainnotes.app.reminder.NotificationPrefs
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session

/**
 * Disguise mode (full flavor only, docs/PLAN.md M7). The launcher entry is switched between activity-aliases:
 * the normal one, or a working calculator / notes shell. The secret code opens the real data, the optional decoy
 * code opens a separate empty space, anything else is handled by the shell as ordinary input, without feedback.
 */
object Disguise {
    const val AVAILABLE = true
    enum class Shell(val alias: String, val label: Int, val icon: Int) {
        CALCULATOR("net.plainnotes.app.CalculatorLauncher", R.string.shell_calc, R.mipmap.ic_shell_calc),
        NOTES("net.plainnotes.app.NotesLauncher", R.string.shell_notes, R.mipmap.ic_shell_notes)
    }
    private const val NORMAL = "net.plainnotes.app.Launcher"
    private const val MAX_MISSES = 10
    private const val PAUSE_MILLIS = 60_000L

    private fun code(c: Context) = AppLock(c, "disguise.bin", "notes.disguise")
    private fun decoy(c: Context) = AppLock(c, "decoy.bin", "notes.decoy")
    private fun prefs(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private fun component(c: Context, name: String) = ComponentName(c.packageName, name)
    private fun isOn(c: Context, name: String) =
        c.packageManager.getComponentEnabledSetting(component(c, name)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    private fun set(c: Context, name: String, on: Boolean) = c.packageManager.setComponentEnabledSetting(component(c, name),
        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)

    fun shell(c: Context): Shell? = Shell.entries.firstOrNull { isOn(c, it.alias) }
    fun enabled(c: Context) = shell(c) != null
    fun hasDecoy(c: Context) = decoy(c).enabled

    fun enable(c: Context, shell: Shell, secret: String, decoyCode: String?) {
        require(AppLock.validPin(secret) && (decoyCode == null || (AppLock.validPin(decoyCode) && decoyCode != secret)))
        Session.open = true
        code(c).setPin(secret)
        if (decoyCode != null) decoy(c).setPin(decoyCode) else decoy(c).disable()
        NotificationPrefs(c).disguised = true
        Shell.entries.forEach { if (it != shell) set(c, it.alias, false) }
        set(c, shell.alias, true); set(c, NORMAL, false)
        prefs(c).edit().remove("disguise_misses").remove("disguise_until").apply()
    }

    /** Restores the normal launcher entry. The caller deletes the decoy space. */
    fun disable(c: Context) {
        set(c, NORMAL, true); Shell.entries.forEach { set(c, it.alias, false) }
        code(c).disable(); decoy(c).disable(); NotificationPrefs(c).disguised = false
        prefs(c).edit().remove("disguise_misses").remove("disguise_until").apply()
    }

    /** Only well-formed codes count as attempts. After repeated misses checks pause silently for a minute. */
    fun check(c: Context, input: String, now: Long = System.currentTimeMillis()): Space? {
        if (!AppLock.validPin(input) || !enabled(c)) return null
        val p = prefs(c)
        if (now < p.getLong("disguise_until", 0)) return null
        val space = when { code(c).matches(input) -> Space.PRIMARY; decoy(c).matches(input) -> Space.DECOY; else -> null }
        val misses = if (space != null) 0 else p.getInt("disguise_misses", 0) + 1
        p.edit().putInt("disguise_misses", if (misses >= MAX_MISSES) 0 else misses)
            .putLong("disguise_until", if (misses >= MAX_MISSES) now + PAUSE_MILLIS else 0).apply()
        return space
    }

    fun shellIntent(c: Context) = Intent().setComponent(component(c, shell(c)?.alias ?: NORMAL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** One-gesture exit: close the session and show the shell in place of the app. */
    fun exit(a: Activity) {
        Session.open = false
        a.startActivity(shellIntent(a)); a.finishAndRemoveTask()
    }
}
