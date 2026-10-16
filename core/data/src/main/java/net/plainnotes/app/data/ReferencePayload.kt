package net.plainnotes.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/** Canonical, bounded source facts. Not MedicationEntity, a prescription or a PK profile. */
data class UserReferenceProduct(
    val name: String,
    val ingredients: List<String> = emptyList(),
    val strength: String? = null,
    val form: String? = null,
    val routes: List<String> = emptyList(),
    val cis: String? = null,
    val cip: String? = null,
    val sourceUrl: String? = null,
    val sourceVersion: String,
    val recordedOn: String,
)

data class ReferenceScope(val dataset: String, val epoch: String)
enum class ReferenceError { STALE_SCOPE, STALE_REVISION, OPERATION_CONFLICT, UNKNOWN_IDENTITY, INVALID_SOURCE, WRONG_SPACE, ALREADY_CONSUMED }
class ReferenceFailure(val code: ReferenceError) : IllegalArgumentException(code.name)

data class MedicationReferenceState(val scope: ReferenceScope?, val reference: MedicationReference?, val previewEpoch: String)

data class MedicationReference(
    val medicationUuid: String,
    val revision: Int,
    val operationUuid: String,
    val snapshotUuid: String?,
    val product: UserReferenceProduct?,
)

data class RecordReferenceContext(
    val recordUuid: String,
    val recordRevision: Int,
    val captureRevision: Int,
    val referenceRevision: Int?,
    val product: UserReferenceProduct?,
    val actualPackageConfirmed: Boolean,
)

internal object ReferencePayload {
    const val MAX_BYTES = 65536
    const val MAX_TEXT = 2048
    private val fields = setOf("format", "kind", "namespace", "source_version", "recorded_on", "name", "ingredients", "strength", "form", "routes", "cis", "cip", "source_url", "verification")
    fun uuid(text: String): Boolean = runCatching { UUID.fromString(text).toString() == text }.getOrDefault(false)
    fun sha(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    /** Fixed JSON escaping and scalar Unicode validation; independent of Android's JSONObject writer version. */
    private fun quote(text: String): String = buildString {
        append('"')
        var index=0
        while(index<text.length) {
            val c=text[index++]
            require(c!='\u0000')
            when {
                Character.isHighSurrogate(c) -> {
                    require(index<text.length && Character.isLowSurrogate(text[index]))
                    append(c);append(text[index++])
                }
                Character.isLowSurrogate(c) -> error("Unpaired Unicode surrogate")
                c=='"' -> append("\\\"")
                c=='\\' -> append("\\\\")
                c=='\b' -> append("\\b")
                c=='\t' -> append("\\t")
                c=='\n' -> append("\\n")
                c=='\u000c' -> append("\\f")
                c=='\r' -> append("\\r")
                c.code<32 -> append("\\u"+c.code.toString(16).padStart(4,'0'))
                else -> append(c)
            }
        }
        append('"')
    }
    fun canonical(json: JSONObject): String {
        fun encode(value: Any, depth: Int): String {
            require(depth <= 16) { "Reference nesting limit" }
            return when (value) {
                JSONObject.NULL -> "null"
                is JSONObject -> {
                    require(value.length() <= 64)
                    value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { key ->
                        require(key.length <= 64)
                        quote(key) + ":" + encode(value.get(key), depth + 1)
                    }
                }
                is JSONArray -> {
                    require(value.length() <= 128)
                    (0 until value.length()).joinToString(",", "[", "]") { encode(value.get(it), depth + 1) }
                }
                is String -> { require(value.length <= MAX_TEXT); quote(value) }
                is Int, is Long, is Boolean -> value.toString()
                else -> error("Exact reference values must use decimal strings, not floating point")
            }
        }
        return encode(json, 0).also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }
    }
    fun userProduct(product: UserReferenceProduct): JSONObject = JSONObject().apply {
        put("format", 1); put("kind", "PRODUCT"); put("namespace", "USER_ENTERED")
        put("source_version", product.sourceVersion); put("recorded_on", product.recordedOn)
        put("name", product.name); put("ingredients", JSONArray(product.ingredients)); put("routes", JSONArray(product.routes))
        put("strength", product.strength ?: JSONObject.NULL); put("form", product.form ?: JSONObject.NULL)
        put("cis", product.cis ?: JSONObject.NULL); put("cip", product.cip ?: JSONObject.NULL)
        put("source_url", product.sourceUrl ?: JSONObject.NULL); put("verification", "UNVERIFIED")
    }.also(::validateProduct)

    fun validateProduct(json: JSONObject) {
        canonical(json)
        require(json.keys().asSequence().toSet() == fields)
        require(json.get("format") is Int && json.getInt("format") == 1 && json.getString("kind") == "PRODUCT")
        // Official snapshot adapter is a separate gated feature. User-entered facts never claim official verification.
        require(json.getString("namespace") == "USER_ENTERED" && json.getString("verification") == "UNVERIFIED")
        listOf("name", "source_version", "recorded_on").forEach { require(json.get(it) is String && json.getString(it).isNotBlank()) }
        LocalDate.parse(json.getString("recorded_on"))
        listOf("strength", "form", "cis", "cip", "source_url").forEach { require(json.isNull(it) || json.get(it) is String) }
        listOf("ingredients", "routes").forEach { key ->
            val rows = json.getJSONArray(key)
            for (i in 0 until rows.length()) require(rows.get(i) is String && rows.getString(i).isNotBlank())
        }
        val cis = optional(json, "cis"); val cip = optional(json, "cip")
        require(cis == null || Regex("[0-9]{8}").matches(cis))
        require(cip == null || cis != null && Regex("[0-9]{13}").matches(cip))
        optional(json, "source_url")?.let { value ->
            val uri = URI(value)
            require(cis != null && uri.scheme == "https" && uri.host == "base-donnees-publique.medicaments.gouv.fr")
            require(uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null)
            require(uri.rawPath == "/medicament/$cis/extrait") // Validate supplied identity URL; never generate a guessed URL.
        }
    }
    fun optional(json: JSONObject, key: String): String? = if (json.isNull(key)) null else json.getString(key)
    fun readProduct(json: JSONObject): UserReferenceProduct {
        validateProduct(json)
        fun strings(key: String) = json.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
        return UserReferenceProduct(json.getString("name"), strings("ingredients"), optional(json,"strength"), optional(json,"form"),
            strings("routes"), optional(json,"cis"), optional(json,"cip"), optional(json,"source_url"), json.getString("source_version"), json.getString("recorded_on"))
    }
}
