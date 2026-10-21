package net.plainnotes.app.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager

/**
 * REQUIREMENTS §135: whether the app's own screens may be captured (screenshots, screen recording). "Block screenshots" in
 * Settings → Privacy & security is on by default, which keeps FLAG_SECURE as before; turning it off allows capture, also in
 * disguise mode once inside the real app. In disguise mode the recents preview must still not show the real app: on Android 13+ it is turned off on
 * its own ([Activity.setRecentsScreenshotEnabled]); below 13 only FLAG_SECURE hides it, so allowing screenshots there also
 * shows the preview (the setting says so). The disguise's private notes screen follows the same rule (owner, 2026-10-21).
 */
object ScreenCapture {
    /** True when the window must block capture: the user has not allowed it. */
    fun blocked(allowed: Boolean): Boolean = !allowed

    /** True when, with capture allowed, the recents preview must still be hidden: disguise mode on Android 13+. */
    fun hideRecentsPreview(allowed: Boolean, disguised: Boolean, sdk: Int = Build.VERSION.SDK_INT): Boolean = allowed && disguised && sdk >= 33

    /** True when the recents preview would show the real app in disguise mode (allowed, disguised, below Android 13). */
    fun previewExposed(allowed: Boolean, disguised: Boolean, sdk: Int = Build.VERSION.SDK_INT): Boolean = allowed && disguised && sdk < 33

    /** Applies the rule to [activity]'s window now; nothing to restart. */
    fun apply(activity: Activity, allowed: Boolean, disguised: Boolean) {
        if (blocked(allowed)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!hideRecentsPreview(allowed, disguised))
    }

    /** The activity behind a Compose context, if any. */
    fun activityOf(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }
}
