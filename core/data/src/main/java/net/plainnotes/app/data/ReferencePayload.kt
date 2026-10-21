package net.plainnotes.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate

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

/** User-entered snapshots only. A matching name is never an equivalence or an official identity claim. */
fun validatedUserReferenceProduct(product:UserReferenceProduct):UserReferenceProduct {
    try { ReferencePayload.userProduct(product) } catch(_:Exception) {throw ReferenceFailure(ReferenceError.INVALID_SOURCE)}
    return product.copy(ingredients=product.ingredients.toList(),routes=product.routes.toList())
}
data class SavedReferenceProduct(val snapshotUuid: String, val product: UserReferenceProduct)
data class SavedReferencePage(val entries: List<SavedReferenceProduct>, val limited: Boolean)
internal object ReferenceSearch {
    private fun fold(value:String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT).replace("œ","oe").replace("æ","ae")
    fun matches(product:UserReferenceProduct,query:String,form:String,route:String,strength:String=""):Boolean {
        val name=fold(query.trim());val f=fold(form.trim());val r=fold(route.trim());val spec=fold(strength.trim())
        return (name.isEmpty() || (listOf(product.name)+product.ingredients).any { fold(it).contains(name) }) &&
            (f.isEmpty() || fold(product.form.orEmpty()).contains(f)) &&
            (r.isEmpty() || product.routes.any {fold(it).contains(r)}) &&
            (spec.isEmpty() || fold(product.strength.orEmpty()).contains(spec))
    }
}

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

data class RecordedReferenceHistory(val recordRevision: Int, val contexts: List<RecordReferenceContext>)

internal object ReferencePayload {
    const val MAX_BYTES = 65536
    const val MAX_TEXT = 2048
    private val fields = setOf("format", "kind", "namespace", "source_version", "recorded_on", "name", "ingredients", "strength", "form", "routes", "cis", "cip", "source_url", "verification")
    /** Exactly the platform parse→canonical-format accepted set; never normalizes stored identities. */
    fun uuid(text:String):Boolean {
        if(text.length!=36)return false
        for(index in text.indices) {
            val c=text[index]
            if(index==8 || index==13 || index==18 || index==23) {
                if(c!='-')return false
            } else if(c !in '0'..'9' && c !in 'a'..'f')return false
        }
        return true
    }
    /** Encoded size only; common BMP text needs no temporary byte array.
     * Delegate the complete original string on ANY surrogate: Android's handling of malformed
     * sequences differs from the JVM and cannot be inferred from isolated-code-unit replacement.
     * Hashing/canonical Unicode validation and every caller's original limit remain separate.
     */
    internal fun utf8Length(text:String):Long {
        var bytes=0L
        for(c in text) {
            when {
                c.code<0x80 -> bytes++
                c.code<0x800 -> bytes+=2
                c in '\uD800'..'\uDFFF' -> return text.toByteArray(Charsets.UTF_8).size.toLong()
                else -> bytes+=3
            }
        }
        return bytes
    }
    fun sha(text: String): String {
        val digest=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        // Same fixed lowercase ASCII bytes; avoid32 locale Formatter instances for every audited digest.
        val alphabet="0123456789abcdef";val out=CharArray(digest.size*2)
        digest.forEachIndexed {index,byte->val n=byte.toInt() and 255;out[index*2]=alphabet[n ushr 4];out[index*2+1]=alphabet[n and 15]}
        return String(out)
    }
    /** Byte-identical scalar escaping, appending safe runs without per-character builder calls. */
    private fun StringBuilder.quote(text: String) {
        append(34.toChar())
        var index=0
        var run=0
        while(index<text.length) {
            val c=text[index]
            require(c.code!=0)
            when {
                Character.isHighSurrogate(c) -> {
                    require(index+1<text.length && Character.isLowSurrogate(text[index+1]))
                    index+=2
                }
                Character.isLowSurrogate(c) -> error("Unpaired Unicode surrogate")
                c.code==34 || c.code==92 || c.code<32 -> {
                    append(text,run,index)
                    when {
                        c=='"' -> append("\\\"")
                        c=='\\' -> append("\\\\")
                        c=='\b' -> append("\\b")
                        c=='\t' -> append("\\t")
                        c=='\n' -> append("\\n")
                        c=='\u000c' -> append("\\f")
                        c=='\r' -> append("\\r")
                        c.code<32 -> append("\\u"+c.code.toString(16).padStart(4,'0'))
                    }
                    index++;run=index
                }
                else -> index++
            }
        }
        // Android26's CharSequence range append copies character by character.
        // The whole String overload is safe only after the complete scalar scan above.
        if(run==0)append(text) else append(text,run,text.length)
        append(34.toChar())
    }
    fun canonical(json: JSONObject): String {
        // One builder for the whole tree; no intermediate encoded subtree strings.
        val out=StringBuilder()
        fun encode(value: Any, depth: Int) {
            require(depth <= 16) { "Reference nesting limit" }
            when (value) {
                JSONObject.NULL -> out.append("null")
                is JSONObject -> {
                    require(value.length() <= 64)
                    out.append('{')
                    value.keys().asSequence().toList().sorted().forEachIndexed { index,key ->
                        require(key.length <= 64)
                        if(index>0)out.append(',')
                        out.quote(key);out.append(':');encode(value.get(key),depth+1)
                    }
                    out.append('}')
                }
                is JSONArray -> {
                    require(value.length() <= 128)
                    out.append('[')
                    for(index in 0 until value.length()) {
                        if(index>0)out.append(',')
                        encode(value.get(index),depth+1)
                    }
                    out.append(']')
                }
                is String -> {require(value.length <= MAX_TEXT);out.quote(value)}
                is Int, is Long, is Boolean -> out.append(value.toString())
                else -> error("Exact reference values must use decimal strings, not floating point")
            }
        }
        encode(json,0)
        return out.toString().also { require(utf8Length(it) <= MAX_BYTES) }
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
