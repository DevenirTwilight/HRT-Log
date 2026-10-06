package net.plainnotes.app.importer

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Reader for HRT tracker (Transmtf-HRT-Tracker, MIT) JSON exports: `{meta:{version}, weight, events[], labResults[], gelProducts[]}`
 * or a bare events array (older exports). Field meanings follow the upstream `types.ts`:
 * `timeH` hours since 1970 (UTC), `doseMG` the administered compound amount (not E2-equivalent), per-event route/ester/extras.
 *
 * Nothing is guessed: events the app cannot represent are counted and skipped, and exact duplicates
 * (same instant, route, ester, dose and extras) need an explicit choice.
 */
object HrtTracker {
    class InvalidExport(message: String) : Exception(message)
    class MissingChoice(val what: String) : Exception(what)

    val ROUTES = mapOf("injection" to "INJECTION", "oral" to "ORAL", "sublingual" to "SUBLINGUAL", "gel" to "GEL", "patchApply" to "PATCH")
    val ESTERS = setOf("E2", "EB", "EV", "EC", "EN", "EU", "CPA", "BICA")
    /** Gel products built into the PK engine (upstream GEL_PRODUCTS ids); custom ones are not carried over. */
    private val BUILTIN_GEL = 1..5
    /** Upstream site index order (legacy-stable): arm, thigh, scrotal, abdomen. */
    private val GEL_SITES = listOf("ARM", "THIGH", "SCROTAL", "ABDOMEN")

    class Event(val id: String, val route: String, val at: Instant, val doseMg: Double, val ester: String, val extras: Map<String, Any>)
    class Lab(val id: String, val at: Instant, val value: Double, val unit: String)
    class Export(val weightKg: Double?, val events: List<Event>, val labs: List<Lab>, val customGelProducts: Int)

    /** One medication-to-be: the combination of everything the PK profile needs. */
    data class Group(val route: String, val ester: String, val slTier: Int?, val gelProductId: Int?, val gelSite: String?, val gelAreaCm2: Double?, val patchUgDay: Double?) {
        val molecule get() = when (ester) { "CPA" -> "CPA"; "BICA" -> "BICA"; else -> "E2" }
        val pkRoute get() = ROUTES.entries.first { it.value == route }.key
        val unit get() = if (route == "PATCH") "PATCH" else "MG"
    }

    class Preview(val events: Int, val groups: Map<Group, Int>, val first: Instant?, val last: Instant?, val duplicates: Int, val labs: Int,
                  val skipped: Map<String, Int>, val weightKg: Double?) {
        val needsDuplicateChoice get() = duplicates > 0
    }

    enum class Duplicates { KEEP_ALL, MERGE }
    class PlannedIntake(val sourceKey: String, val group: Group, val taken: Instant, val dose: Double)
    class Plan(val intakes: List<PlannedIntake>, val labs: List<Lab>, val skipped: Map<String, Int>)

    fun read(text: String): Export {
        val root: Any = try { if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text) } catch (e: Exception) { throw InvalidExport("not JSON") }
        val obj = root as? JSONObject
        val events = (obj?.optJSONArray("events") ?: root as? JSONArray) ?: throw InvalidExport("no events")
        obj?.optJSONObject("meta")?.optInt("version", 0)?.let { if (it > 2) throw InvalidExport("unsupported version $it") }
        var customGel = 0
        val list = (0 until events.length()).map { i ->
            val e = events.optJSONObject(i) ?: throw InvalidExport("event $i")
            val t = e.optDouble("timeH"); val dose = e.optDouble("doseMG")
            if (!t.isFinite() || !dose.isFinite() || !e.has("route") || !e.has("ester")) throw InvalidExport("event $i fields")
            val extras = e.optJSONObject("extras")?.let { x -> x.keySet().associateWith { x.get(it) } }.orEmpty()
            Event(e.optString("id", "#$i"), e.getString("route"), Instant.ofEpochMilli(Math.round(t * 3_600_000)), dose, e.getString("ester"), extras)
        }
        val labs = obj?.optJSONArray("labResults")?.let { a -> (0 until a.length()).mapNotNull { i ->
            val l = a.optJSONObject(i) ?: return@mapNotNull null
            val t = l.optDouble("timeH"); val v = l.optDouble("concValue")
            val unit = when (l.optString("unit").lowercase()) { "pg/ml" -> "pg/mL"; "pmol/l" -> "pmol/L"; else -> return@mapNotNull null }
            if (!t.isFinite() || !v.isFinite() || v <= 0) null else Lab(l.optString("id", "#$i"), Instant.ofEpochMilli(Math.round(t * 3_600_000)), v, unit)
        } }.orEmpty()
        obj?.optJSONArray("gelProducts")?.let { customGel = it.length() }
        val weight = obj?.optDouble("weight")?.takeIf { it.isFinite() && it in 20.0..400.0 }
        return Export(weight, list, labs, customGel)
    }

    private fun num(x: Map<String, Any>, k: String) = (x[k] as? Number)?.toDouble()?.takeIf { it.isFinite() }

    /** The group of an event, or the reason it cannot be imported. */
    fun classify(e: Event): Pair<Group?, String?> {
        if (e.route == "patchRemove") return null to "patch_remove"
        val route = ROUTES[e.route] ?: return null to "unknown_route"
        if (e.ester !in ESTERS) return null to "unknown_ester"
        return when (route) {
            "SUBLINGUAL" -> {
                val tier = num(e.extras, "sublingualTier")?.toInt()
                if (tier == null || tier !in 0..3) null to "sublingual_custom" else Group(route, e.ester, tier, null, null, null, null) to null
            }
            "GEL" -> {
                val product = num(e.extras, "gelProductId")?.toInt() ?: 1 // upstream falls back to its first product
                if (product !in BUILTIN_GEL) return null to "gel_custom_product"
                if ((num(e.extras, "gelWashAfterH") ?: 0.0) > 0 || (num(e.extras, "gelCoApplied") ?: 0.0) > 0) return null to "gel_wash_or_coapplied"
                Group(route, e.ester, null, product, GEL_SITES.getOrNull(num(e.extras, "gelSite")?.toInt() ?: 0) ?: return null to "gel_site", num(e.extras, "areaCM2"), null) to null
            }
            "PATCH" -> num(e.extras, "releaseRateUGPerDay")?.takeIf { it > 0 }?.let { Group(route, e.ester, null, null, null, null, it) to null } ?: (null to "patch_zero_order_missing")
            else -> if (e.doseMg <= 0) null to "no_dose" else Group(route, e.ester, null, null, null, null, null) to null
        }
    }

    private fun dupKey(e: Event) = listOf(e.at.toEpochMilli(), e.route, e.ester, e.doseMg, e.extras.toSortedMap().toString())

    fun preview(x: Export): Preview {
        val skipped = HashMap<String, Int>(); val groups = LinkedHashMap<Group, Int>()
        x.events.forEach { e -> val (g, why) = classify(e); if (g != null) groups.merge(g, 1, Int::plus) else skipped.merge(why!!, 1, Int::plus) }
        if (x.customGelProducts > 0) skipped["custom_gel_products"] = x.customGelProducts
        val dups = x.events.filter { classify(it).first != null }.groupingBy(::dupKey).eachCount().values.sumOf { it - 1 }
        return Preview(x.events.size, groups, x.events.minOfOrNull { it.at }, x.events.maxOfOrNull { it.at }, dups, x.labs.size, skipped, x.weightKg)
    }

    fun plan(x: Export, duplicates: Duplicates?): Plan {
        val p = preview(x)
        if (p.needsDuplicateChoice && duplicates == null) throw MissingChoice("duplicates")
        val skipped = HashMap(p.skipped); val seen = HashSet<List<Any>>()
        val intakes = x.events.mapNotNull { e ->
            val g = classify(e).first ?: return@mapNotNull null
            if (duplicates == Duplicates.MERGE && !seen.add(dupKey(e))) { skipped.merge("duplicate_merged", 1, Int::plus); return@mapNotNull null }
            PlannedIntake("ht:${e.id}", g, e.at, if (g.route == "PATCH") 1.0 else e.doseMg)
        }
        return Plan(intakes.sortedBy { it.taken }, x.labs, skipped)
    }
}
