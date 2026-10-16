package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

/** Bounded, iterative dependency plan. Never disable FK enforcement, even during whole-dataset replacement. */
internal object LedgerReplacement {
    data class Link(val id: Long, val parent: Long?)

    /** Each level contains only leaves; parent and child must not share one DELETE statement. */
    fun deletionLevels(links: List<Link>): List<List<Long>> {
        if (links.size > BackupLimits.MAX_ROWS) throw BackupCodec.TooLarge()
        val byId = links.associateBy { it.id }
        require(byId.size == links.size) { "Duplicate ledger identity" }
        val children = links.associate { it.id to 0 }.toMutableMap()
        links.forEach { link -> link.parent?.let { parent ->
            require(parent in byId) { "Missing ledger parent" }
            children[parent] = children.getValue(parent) + 1
        } }
        var leaves = links.filter { children.getValue(it.id) == 0 }.map { it.id }
        val levels = ArrayList<List<Long>>()
        var visited = 0
        while (leaves.isNotEmpty()) {
            levels.add(leaves); visited += leaves.size
            val next = ArrayList<Long>()
            leaves.forEach { id -> byId.getValue(id).parent?.let { parent ->
                val remaining = children.getValue(parent) - 1
                children[parent] = remaining
                if (remaining == 0) next.add(parent)
            } }
            leaves = next
        }
        require(visited == links.size) { "Cyclic ledger reference" }
        return levels
    }

    fun current(db: SupportSQLiteDatabase): List<List<Long>> {
        val links = db.query("SELECT id,reversal_of_id FROM supply_transaction").use { c -> buildList {
            while (c.moveToNext()) {
                if (size == BackupLimits.MAX_ROWS) throw BackupCodec.TooLarge()
                add(Link(c.getLong(0), if (c.isNull(1)) null else c.getLong(1)))
            }
        } }
        return deletionLevels(links)
    }

    fun insertionOrder(rows: JSONArray): List<Int> {
        val links = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            Link(it.getLong("id"), if (it.isNull("reversal_of_id")) null else it.getLong("reversal_of_id"))
        } }
        val rowById = links.mapIndexed { i, link -> link.id to i }.toMap()
        return deletionLevels(links).asReversed().flatMap { level -> level.map { rowById.getValue(it) } }
    }

    fun delete(db: SupportSQLiteDatabase, levels: List<List<Long>>) {
        levels.forEach { leaves -> leaves.chunked(500).forEach { chunk ->
            db.execSQL("DELETE FROM supply_transaction WHERE id IN (${chunk.joinToString(",") { "?" }})", chunk.toTypedArray())
        } }
    }
}
