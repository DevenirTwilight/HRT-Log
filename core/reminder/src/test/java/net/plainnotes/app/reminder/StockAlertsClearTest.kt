package net.plainnotes.app.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PRIVACY-P1: "Delete all data" removes every stock-alert marker, including keys written by older versions, and nothing
 * else; clearing twice is harmless. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class StockAlertsClearTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val markers get() = context.getSharedPreferences(StockAlerts.PREFS, Context.MODE_PRIVATE)

    @Test fun clearRemovesCurrentAndLegacyMarkersAndIsIdempotent() {
        markers.edit().putString("1:LOW", "2026-03-10").putString("7:EXPIRY", "2026-03-09").putString("legacy-key", "x").putLong("n", 3).commit()
        assertFalse(StockAlerts.isClear(context))
        assertTrue(StockAlerts.clear(context))
        assertTrue(StockAlerts.isClear(context))
        assertNull("A new medication with a restarted id must not inherit 'already alerted today'", markers.getString("1:LOW", null))
        assertTrue(StockAlerts.clear(context)); assertTrue(StockAlerts.isClear(context))
    }

    @Test fun clearTouchesOnlyTheStockAlertStore() {
        val other = context.getSharedPreferences("synthetic-other", Context.MODE_PRIVATE)
        other.edit().putString("keep", "yes").commit(); markers.edit().putString("2:LOW", "2026-03-10").commit()
        assertTrue(StockAlerts.clear(context))
        assertEquals("yes", other.getString("keep", null)); other.edit().clear().commit()
    }
}
