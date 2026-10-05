package net.plainnotes.app.reminder

import android.content.Context

/**
 * Notification wording chosen by the user. Kept in device-protected storage because reminders can fire
 * before the first unlock; the values are the user's own neutral text, never health data.
 */
class NotificationPrefs(context: Context) {
    private val p = context.createDeviceProtectedStorageContext().getSharedPreferences("notify", Context.MODE_PRIVATE)
    var title: String? get() = p.getString("title", null)?.takeIf { it.isNotBlank() }; set(v) { p.edit().putString("title", v?.trim()).apply() }
    var body: String? get() = p.getString("body", null)?.takeIf { it.isNotBlank() }; set(v) { p.edit().putString("body", v?.trim()).apply() }
    /** Show medication name and dose once the device is unlocked. Forced off while disguise mode is on. */
    var details: Boolean get() = p.getBoolean("details", false) && !disguised; set(v) { p.edit().putBoolean("details", v).apply() }
    var disguised: Boolean get() = p.getBoolean("disguised", false); set(v) { p.edit().putBoolean("disguised", v).apply() }
    fun clear() = p.edit().clear().commit()
}
