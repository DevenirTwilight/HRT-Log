package net.plainnotes.app.symptoms

import org.json.JSONObject
import org.json.JSONArray
import net.plainnotes.app.data.*

/**
 * Official symptom sources bundled with the app (`symptom-sources.json`, REQUIREMENTS 15/15a/15b). Every quote and action is
 * the source's own wording; group names are neutral translations. Nothing here scores, judges or adds advice.
 */
class Source(val id: String, val region: String, val kind: String, val title: String, val publisher: String, val url: String, val documentDate: String,
             val section: String, val thirdParty: Boolean, val sites: List<Site>, val viewed: String, val note: String?)
class Site(val site: String, val url: String, val revised: String?)
class Action(val id: String, val text: String, val urgent: Boolean)
class Entry(val source: Source, val group: String, val quote: String, val action: Action)
class GroupNames(val en: String, val zh: String, val zhHant: String, val fr: String)
class Monitoring(val id: String, val region: String, val whenMolecule: String, val publisher: String, val title: String, val url: String, val section: String, val quote: String, val documentDate:String)
class Reporting(val region: String, val publisher: String, val url: String, val quotes: List<String>)

/** What a medication gets: entries from its sources, or one of the two "nothing to show" states. */
sealed interface MedSymptoms {
    data class Listed(val sources: List<String>, val sublingualNotCovered: Boolean) : MedSymptoms
    /** The official sources reviewed list no symptoms that call for medical attention (spironolactone). */
    data object NoneListedByOfficialSources : MedSymptoms
    /** No official source was reviewed for this medication (including custom medications). */
    data object NoOfficialSource : MedSymptoms
}

/** A medication as the matcher needs it. */
data class MedKey(val id: Long, val molecule: String, val route: String?, val ester: String?)

/** One symptom group shown once, with every source that lists it for the user's current medications. */
class ShownGroup(val id: String, val names: GroupNames, val entries: List<Entry>, val medicationIds: Set<Long>) {
    /** Shown in the separate "immediately" card when any source for it says immediately / 立即. */
    val urgent: Boolean get() = entries.any { it.action.urgent }
}

class SymptomCatalog(json: String) {
    private val root = JSONObject(json)
    val version: String = root.getString("version")
    val sources: Map<String, Source> = root.getJSONArray("sources").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.associate { o ->
        o.getString("id") to Source(o.getString("id"), o.getString("region"), o.getString("kind"), o.getString("title"), o.getString("publisher"), o.getString("url"),
            o.getString("document_date"), o.getString("section"), o.getBoolean("third_party"),
            o.optJSONArray("sites")?.let { s -> (0 until s.length()).map { s.getJSONObject(it) }.map { Site(it.getString("site"), it.getString("url"), it.optString("revised").takeIf { r -> r.isNotEmpty() && r != "null" }) } }.orEmpty(),
            o.getString("viewed"), o.optString("note").takeIf { it.isNotEmpty() }) } }
    val actions: Map<String, Action> = root.getJSONObject("actions").let { a -> a.keys().asSequence().associateWith { k -> a.getJSONObject(k).let { Action(k, it.getString("text"), it.getBoolean("urgent")) } } }
    val groups: Map<String, GroupNames> = root.getJSONObject("groups").let { g -> g.keys().asSequence().associateWith { k -> g.getJSONObject(k).let { GroupNames(it.getString("en"), it.getString("zh"), it.getString("zh_Hant"), it.getString("fr")) } } }
    val entries: List<Entry> = root.getJSONArray("entries").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
        Entry(sources.getValue(o.getString("source")), o.getString("group"), o.getString("quote"), actions.getValue(o.getString("action"))) } }
    val monitoring: List<Monitoring> = root.getJSONArray("monitoring").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
        Monitoring(o.getString("id"), o.getString("region"), o.getString("when"), o.getString("publisher"), o.getString("title"), o.getString("url"), o.getString("section"), o.getString("quote"),o.getString("document_date")) } }
    val reporting: List<Reporting> = root.getJSONArray("reporting").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
        Reporting(o.getString("region"), o.getString("publisher"), o.getString("url"), o.getJSONArray("quotes").let { q -> (0 until q.length()).map { q.getString(it) } }) } }
    private val rules = root.getJSONArray("rules").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    fun forMedication(m: MedKey): MedSymptoms {
        for (r in rules) {
            if (r.getString("molecule") != m.molecule) continue
            if (r.has("ester") && r.getString("ester") != m.ester) continue
            if (r.has("routes")) { val routes = r.getJSONArray("routes").let { a -> (0 until a.length()).map { a.getString(it) } }; if (m.route !in routes) continue }
            if (r.optBoolean("none_listed")) return MedSymptoms.NoneListedByOfficialSources
            val src = r.getJSONArray("sources").let { a -> (0 until a.length()).map { a.getString(it) } }
            return MedSymptoms.Listed(src, r.optString("note") == "SUBLINGUAL_NOT_COVERED")
        }
        return MedSymptoms.NoOfficialSource
    }

    /** Groups for the current medications, each shown once; urgent groups first, then by catalog order. */
    fun groupsFor(meds: List<MedKey>): List<ShownGroup> {
        val bySource = HashMap<String, MutableSet<Long>>()
        meds.forEach { m -> (forMedication(m) as? MedSymptoms.Listed)?.sources?.forEach { bySource.getOrPut(it) { mutableSetOf() } += m.id } }
        val order = groups.keys.toList()
        return entries.filter { it.source.id in bySource }.groupBy { it.group }
            .map { (g, es) -> ShownGroup(g, groups.getValue(g), es, es.flatMap { bySource.getValue(it.source.id) }.toSet()) }
            .sortedWith(compareBy<ShownGroup>({ !it.urgent }, { order.indexOf(it.id) }))
    }

    /** A compact immutable copy of exactly what was matched, not every source sharing the group. */
    fun snapshot(groupId:String,medications:List<MedicationEntity>,profiles:Map<Long,ProfileEntity>):String? {
        val meds=medications.filter{it.active}
        val group=groupsFor(meds.map{MedKey(it.id,it.molecule,it.route,profiles[it.id]?.ester)}).firstOrNull{it.id==groupId} ?: return null
        val sources=group.entries.map{it.source.id}.toSet()
        val entries=root.getJSONArray("entries")
        return JSONObject().put("version",version).put("captured_at",java.time.Instant.now().toString()).put("zone",java.time.ZoneId.systemDefault().id)
            .put("matched_medication_ids",JSONArray(group.medicationIds.toList()))
            .put("matched_medications",JSONArray(meds.filter{it.id in group.medicationIds}.map{JSONObject(MedicationSnapshot.encode(it,profiles[it.id])).put("id",it.id).put("source_ids",JSONArray(
                (forMedication(MedKey(it.id,it.molecule,it.route,profiles[it.id]?.ester)) as? MedSymptoms.Listed)?.sources.orEmpty().filter{it in sources}))}))
            .put("groups",JSONObject().put(groupId,root.getJSONObject("groups").getJSONObject(groupId)))
            .put("sources",JSONArray(root.getJSONArray("sources").let{a->(0 until a.length()).map{a.getJSONObject(it)}.filter{it.getString("id") in sources}.map{source->
                val frozen=JSONObject(source.toString())
                val digest=java.security.MessageDigest.getInstance("SHA-256").digest(source.toString().toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
                frozen.put("source_revision_id",source.getString("id")+":"+digest).put("content_sha256",digest)
            }}))
            .put("entries",JSONArray((0 until entries.length()).map{entries.getJSONObject(it)}.filter{it.getString("group")==groupId && it.getString("source") in sources}))
            .put("actions",root.getJSONObject("actions")).put("rules",JSONArray()).put("monitoring",JSONArray()).put("reporting",JSONArray()).toString()
    }

    fun savedGroup(groupId:String):ShownGroup? {
        val names=groups[groupId] ?: return null
        val ids=root.optJSONArray("matched_medication_ids")?.let{a->(0 until a.length()).map{a.getLong(it)}.toSet()}.orEmpty()
        return ShownGroup(groupId,names,entries.filter{it.group==groupId},ids)
    }

    /** Country rules and monitoring shown only to users who chose that region. */
    fun monitoringFor(region: String?, molecules: Set<String>): List<Monitoring> =
        if (region == null) emptyList() else monitoring.filter { it.region == region && (it.whenMolecule == "ANY" && molecules.isNotEmpty() || it.whenMolecule in molecules) }

    fun reportingFor(region: String?): Reporting? = reporting.firstOrNull { it.region == region }

    companion object {
        fun saved(check:SymptomCheckEntity):ShownGroup? = check.context_snapshot?.let{runCatching{SymptomCatalog(it).savedGroup(check.group_id)}.getOrNull()}
        fun savedMedicationNames(check:SymptomCheckEntity):List<String> = check.context_snapshot?.let{runCatching{
            val a=JSONObject(it).getJSONArray("matched_medications");(0 until a.length()).map{a.getJSONObject(it).getString("name")}
        }.getOrNull()}.orEmpty()

        @Volatile private var cached: SymptomCatalog? = null
        fun load(): SymptomCatalog = cached ?: SymptomCatalog(SymptomCatalog::class.java.getResourceAsStream("/symptom-sources.json")!!.bufferedReader().use { it.readText() }).also { cached = it }
    }
}
