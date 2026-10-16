package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

/** Trusted self-FK descriptors only. Backup fields never choose SQL identifiers. */
internal object ReferenceDependencies {
    private val edges = mapOf(
        "reference_identity" to ("uuid" to "owner_uuid"),
        "reference_event" to ("operation_uuid" to "previous_uuid"),
        "measured_allocation" to ("uuid" to "reverses_uuid"),
    )
    private fun levels(links: List<Pair<String, String?>>): List<List<String>> {
        if (links.size > BackupLimits.MAX_ROWS) throw BackupCodec.TooLarge()
        val parents = links.toMap(); require(parents.size == links.size) { "Duplicate reference identity" }
        val children = parents.keys.associateWith { 0 }.toMutableMap()
        parents.values.forEach { parent -> parent?.let { require(it in parents) { "Missing reference parent" }; children[it] = children.getValue(it) + 1 } }
        var leaves = links.filter { children.getValue(it.first) == 0 }.map { it.first }
        val result = ArrayList<List<String>>(); var count = 0
        while (leaves.isNotEmpty()) {
            result.add(leaves); count += leaves.size
            val next = ArrayList<String>()
            leaves.forEach { id -> parents.getValue(id)?.let { parent ->
                val left = children.getValue(parent) - 1; children[parent] = left
                if (left == 0) next.add(parent)
            } }
            leaves = next
        }
        require(count == links.size) { "Cyclic reference" }
        return result
    }
    fun current(db: SupportSQLiteDatabase): Map<String, List<List<String>>> = edges.mapValues { (table, edge) ->
        db.query("SELECT `${edge.first}`,`${edge.second}` FROM `$table`").use { c ->
            levels(buildList {
                while(c.moveToNext()) {
                    if (size == BackupLimits.MAX_ROWS) throw BackupCodec.TooLarge()
                    add(c.getString(0) to if(c.isNull(1)) null else c.getString(1))
                }
            })
        }
    }
    fun insertionOrder(table: String, rows: JSONArray): List<Int> {
        val edge = edges[table] ?: return (0 until rows.length()).toList()
        val links = (0 until rows.length()).map { i -> rows.getJSONObject(i).let { it.getString(edge.first) to ReferencePayload.optional(it, edge.second) } }
        val positions = links.mapIndexed { i, link -> link.first to i }.toMap()
        return levels(links).asReversed().flatMap { level -> level.map { positions.getValue(it) } }
    }
    fun delete(db: SupportSQLiteDatabase, table: String, levels: List<List<String>>) {
        val primary = edges.getValue(table).first
        levels.forEach { level -> level.chunked(500).forEach { batch ->
            db.execSQL("DELETE FROM `$table` WHERE `$primary` IN (${batch.joinToString(",") { "?" }})", batch.toTypedArray())
        } }
    }
}
