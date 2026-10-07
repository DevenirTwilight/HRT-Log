package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

/** Built-in daily items (original set, REQUIREMENTS 15b). */
val DAILY_KEYS = listOf("DAY_MOOD", "DAY_ENERGY", "DAY_SLEEP", "DAY_BODY")
/** Earlier items that are the same concept (1-5, higher is better) and are merged into the new ones. */
val MERGED_KEYS = mapOf("MOOD" to "DAY_MOOD", "ENERGY" to "DAY_ENERGY", "SLEEP_QUALITY" to "DAY_SLEEP")
/** Earlier items kept under "previous items". */
val LEGACY_KEYS = listOf("OVERALL", "EMO_STABILITY", "AGGRESSIVENESS", "LIBIDO", "PAIN", "PERIOD_LIKE", "APPETITE", "SKIN_QUALITY")
/** A previous item stays enabled when it has a score in this many days up to the upgrade day. */
const val LEGACY_RECENT_DAYS = 30L

/**
 * Moves wellbeing items from schema 1 to schema 2. The Room migration and the restore of a schema-1 backup both call
 * this, so both paths give the same result. Idempotent; never deletes a score or a note.
 */
object WellbeingUpgrade {
    fun apply(db: SupportSQLiteDatabase, today: LocalDate) {
        fun itemId(key: String): Long? = db.query("SELECT id FROM checkin_item WHERE builtin_key = ? ORDER BY id LIMIT 1", arrayOf(key)).use { if (it.moveToFirst()) it.getLong(0) else null }
        for ((old, new) in MERGED_KEYS) {
            val from = itemId(old) ?: continue
            val to = itemId(new)
            if (to == null) db.execSQL("UPDATE checkin_item SET builtin_key = ?, legacy = 0 WHERE id = ?", arrayOf(new, from))
            else {
                // Both exist (not produced by schema 1, but keep it safe): move scores that do not collide, keep the rest.
                db.execSQL("UPDATE OR IGNORE checkin_score SET item_id = ? WHERE item_id = ?", arrayOf(to, from))
                db.execSQL("UPDATE checkin_item SET legacy = 1 WHERE id = ?", arrayOf(from))
            }
        }
        val since = today.minusDays(LEGACY_RECENT_DAYS).toString()
        for (key in LEGACY_KEYS) {
            val id = itemId(key) ?: continue
            val recent = db.query("SELECT 1 FROM checkin_score WHERE item_id = ? AND date >= ? AND date <= ? LIMIT 1", arrayOf(id, since, today.toString())).use { it.moveToFirst() }
            db.execSQL("UPDATE checkin_item SET legacy = 1, enabled = ? WHERE builtin_key = ?", arrayOf(if (recent) 1 else 0, key))
        }
        var order = db.query("SELECT COALESCE(MAX(sort_order), -1) FROM checkin_item").use { it.moveToFirst(); it.getInt(0) }
        for (key in DAILY_KEYS) if (itemId(key) == null)
            db.execSQL("INSERT INTO checkin_item (builtin_key, custom_label, enabled, sort_order, legacy) VALUES (?, NULL, 1, ?, 0)", arrayOf(key, ++order))
    }
}
