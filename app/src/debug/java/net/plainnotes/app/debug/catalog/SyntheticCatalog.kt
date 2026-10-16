package net.plainnotes.app.debug.catalog

import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** ANSM-02A prototype only. These DTOs have no dependency on medication, stock, plan or PK models. */
enum class Registration { ACTIVE, ARCHIVED, REPEALED, EXPIRED, UNVERIFIED }
enum class Marketing { MARKETED_DECLARED, STOPPED_DECLARED, NOT_MARKETED, UNVERIFIED }
enum class DocumentEntry { PANEL_OBSERVED, MISSING, UNVERIFIED }
data class CatalogIngredient(val name: String, val strength: String?, val denominator: String?, val role: String, val group: String)
data class CatalogPresentation(val cip: String, val cis: String, val label: String)
data class CatalogProduct(
    val cis: String, val name: String, val form: String, val strength: String?, val routes: List<String>,
    val ingredients: List<CatalogIngredient>, val presentations: List<CatalogPresentation>,
    val registration: Registration, val marketing: Marketing, val rcp: DocumentEntry, val notice: DocumentEntry,
)
data class SyntheticCatalog(val version: String, val source: String, val verifiedOn: LocalDate, val products: List<CatalogProduct>) {
    companion object {
        /** Fail closed: synthetic IDs cannot accidentally become real identifiers or real document links. */
        fun decode(text: String): SyntheticCatalog {
            require(text.length <= 128_000)
            val root = JSONObject(text)
            require(root.getString("kind") == "SYNTHETIC_ONLY")
            val version = root.getString("version").also { require(it.startsWith("synthetic-") && it.isNotBlank()) }
            fun JSONObject.required(key: String) = getString(key).also { require(it.isNotBlank()) }
            fun JSONObject.optional(key: String): String? = if (isNull(key)) null else required(key)
            fun JSONObject.strings(key: String): List<String> = getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it).also { s -> require(s.isNotBlank()) } } }
            val products = root.getJSONArray("products").let { a -> (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                val cis = p.required("cis").also { require(it.matches(Regex("SYNTH-CIS-[0-9]+"))) }
                val ingredients = p.getJSONArray("ingredients").let { rows -> (0 until rows.length()).map { j ->
                    val row = rows.getJSONObject(j)
                    CatalogIngredient(row.required("name"), row.optional("strength"), row.optional("denominator"), row.required("role").also { require(it in setOf("SA", "FT", "ST")) }, row.required("group"))
                } }.also { require(it.isNotEmpty()) }
                val presentations = p.getJSONArray("presentations").let { rows -> (0 until rows.length()).map { j ->
                    val row = rows.getJSONObject(j)
                    CatalogPresentation(row.required("cip").also { require(it.matches(Regex("SYNTH-CIP-[0-9]+"))) }, row.required("cis").also { require(it == cis) }, row.required("label"))
                } }
                val routes = p.strings("routes").also { require(it.isNotEmpty() && it.distinct().size == it.size) }
                require(!p.has("url")) // The mock never opens or guesses official pages.
                CatalogProduct(cis, p.required("name"), p.required("form"), p.optional("strength"), routes, ingredients, presentations,
                    Registration.valueOf(p.required("registration")), Marketing.valueOf(p.required("marketing")),
                    DocumentEntry.valueOf(p.required("rcp")), DocumentEntry.valueOf(p.required("notice")))
            } }
            require(products.map { it.cis }.distinct().size == products.size)
            val cips = products.flatMap { it.presentations }.map { it.cip }
            require(cips.distinct().size == cips.size)
            return SyntheticCatalog(version, root.required("source"), LocalDate.parse(root.required("verifiedOn")), products)
        }
    }
}

fun catalogSearchKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace("œ", "oe").replace(Regex("\\s+"), " ").trim()

data class CatalogFilter(val query: String = "", val form: String? = null, val strength: String? = null, val route: String? = null)
fun SyntheticCatalog.search(filter: CatalogFilter): List<CatalogProduct> {
    val terms = catalogSearchKey(filter.query).split(' ').filter { it.isNotBlank() }
    return products.filter { p ->
        val haystack = catalogSearchKey(listOf(p.name, p.cis, p.form, p.strength.orEmpty()).plus(p.ingredients.map { it.name }).joinToString(" "))
        terms.all { it in haystack } && (filter.form == null || filter.form == p.form) &&
            (filter.strength == null || filter.strength == p.strength) && (filter.route == null || filter.route in p.routes)
    }.sortedWith(compareBy({ catalogSearchKey(it.name) }, { it.cis }))
}

sealed interface CatalogLoad {
    data object Loading : CatalogLoad
    data class Ready(val catalog: SyntheticCatalog) : CatalogLoad
    data object Failed : CatalogLoad
}

/** Ephemeral preview, deliberately unrelated to MedicationDraft. Cancel/selection never changes user-entered values. */
data class MockCatalogDraft(val name: String = "", val doseText: String = "", val selection: MockSelection? = null)
data class MockSelection(val cis: String, val cip: String?, val version: String, val name: String)
fun MockCatalogDraft.choose(catalog: SyntheticCatalog, cis: String, cip: String?): MockCatalogDraft {
    val product = catalog.products.single { it.cis == cis }
    require(cip == null || product.presentations.any { it.cip == cip })
    return copy(selection = MockSelection(cis, cip, catalog.version, product.name))
}
