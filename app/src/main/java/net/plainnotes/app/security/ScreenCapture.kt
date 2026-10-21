package net.plainnotes.app.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

/**
 * REQUIREMENTS §135: whether the app's own screens may be captured (screenshots, screen recording, the recents preview).
 * Off by default, which keeps FLAG_SECURE as before. The owner can allow it in Settings → Privacy & security. Disguise
 * mode always keeps the screens secure, whatever the setting; the disguise's private notes screen sets FLAG_SECURE itself.
 */
object ScreenCapture {
    /** True when the window must block capture: the user has not allowed it, or disguise mode is on. */
    fun blocked(allowed: Boolean, disguised: Boolean): Boolean = !allowed || disguised

    /** Applies the rule to [activity]'s window now; nothing to restart. */
    fun apply(activity: Activity, allowed: Boolean, disguised: Boolean) {
        if (blocked(allowed, disguised)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** The activity behind a Compose context, if any. */
    fun activityOf(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }
}
