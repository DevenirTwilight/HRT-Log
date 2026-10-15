package net.plainnotes.app.privacy

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** PRIVACY-P1 device tests run inside the shared full-app test process: they must leave no activity alive (a relaunched
 * screen would sit on top of later tests) and must put back every preference store they cleared. */
internal object DeviceTestHygiene {
    private val live = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED)
    fun activities(): List<Activity> {
        var found = emptyList<Activity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { found = live.flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) } }
        return found
    }
    fun finishAll() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        repeat(100) {
            val alive = activities()
            if (alive.isEmpty()) return
            instrument.runOnMainSync { alive.forEach { if (!it.isFinishing) it.finish() } }
            instrument.waitForIdleSync(); Thread.sleep(100)
        }
        error("Activities still alive after cleanup: ${activities().map { it.javaClass.simpleName }}")
    }

    /** Typed copy of a preference store, restorable after a destructive test. */
    class Saved(private val prefs: SharedPreferences) {
        private val values = HashMap(prefs.all)
        fun restore() {
            val e = prefs.edit().clear()
            values.forEach { (k, v) -> when (v) {
                is String -> e.putString(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v); is Float -> e.putFloat(k, v)
                is Boolean -> e.putBoolean(k, v); is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
            } }
            check(e.commit())
        }
    }
    fun save(context: Context) = listOf(
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE), context.getSharedPreferences("shell_notes", Context.MODE_PRIVATE),
        context.createDeviceProtectedStorageContext().getSharedPreferences("notify", Context.MODE_PRIVATE),
    ).map(::Saved)
}
