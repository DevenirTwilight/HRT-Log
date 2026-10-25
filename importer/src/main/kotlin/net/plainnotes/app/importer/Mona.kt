package net.plainnotes.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Independently implemented reader of Mona's exported database rows, not its application code.
 * Only the reviewed schema23 is supported. This is a source model, not a medication/PK/stock plan.
 * No missing clinical value, route, time zone, dose or salt/ester receives a default.
 */
object Mona {
    const val DATABASE_VERSION = 23
    class InvalidExport(val code: String) : Exception(code)
    enum class Reason { INVALID_VALUE, UNKNOWN_VALUE, NOT_TAKEN, MISSING_REFERENCE, REFERENCE_ONLY,
        UNSUPPORTED_FIELD, UNSUPPORTED_COLLECTION, INPUT_LIMIT }
    data class Issue(val type: String, val sourceId: String?, val field: String, val reason: Reason, val count: Int = 1)
    data class Metadata(val appVersion: String, val databaseVersion: Int, val exportedAtRaw: String)
    data class Molecule(val name: String, val massUnit: String, val rateUnit: String?)
    data class Timing(val original: String, val instant: Instant?, val zone: String?)
    data class Intake(val id: String, val taken: Timing?, val scheduledTime: String?, val doseOriginal: String?,
        val dose: BigDecimal?, val doseUnit: String?, val molecule: Molecule?, val route: String?, val ester: String?,
        val dosingBasis: String?, val scheduleId: String?, val medicationSupplyItemId: String?, val notes: String?,
        val genericSupplyItemIdsJson: String?, val placementsJson: String?, val wastedAmountOriginal: String?,
        val deadSpaceOriginal: String?, val injectionType: String?)
    data class LabValue(val analyte: String, val valueOriginal: String?, val value: BigDecimal?, val unit: String?)
    data class BloodTest(val id: String, val timing: Timing?, val values: List<LabValue>, val notes: String?)
    data class Schedule(val id: String, val name: String?, val doseOriginal: String?, val dose: BigDecimal?,
        val startDate: String?, val molecule: Molecule?, val route: String?, val ester: String?,
        val dosingBasis: String?, val schedulingJson: String?)
    data class Supply(val id: String, val type: String?, val name: String?, val molecule: Molecule?,
        val route: String?, val ester: String?, val dosingBasis: String?, val deliveryForm: String?,
        val totalDoseOriginal: String?, val usedDoseOriginal: String?, val dosePerUnitOriginal: String?,
        val amount: String?, val genericSupplyType: String?)
    data class Export(val metadata: Metadata, val intakes: List<Intake>, val bloodTests: List<BloodTest>,
        val schedules: List<Schedule>, val supplies: List<Supply>, val issues: List<Issue>, val sourceRows: Map<String,String>)

    private val routes = setOf("injection", "oral", "sublingual", "patch", "gel", "implant", "suppository", "transdermalSpray", "transdermalDrops")
    private val esters = setOf("enanthate", "valerate", "cypionate", "undecylate", "benzoate", "cypionateSuspension")
    private val tables = setOf("medication_intakes", "medication_schedules", "supply_items", "blood_tests")

    /** Standalone entry for synthetic fixtures; the common detector passes its already parsed root. */
    fun read(text: String): Export {
        val root=try {
            ImportFormatDetector.bounds(text)
            val tokens=JSONTokener(text.removePrefix("\uFEFF"));val value=tokens.nextValue()
            if(tokens.nextClean()!='\u0000' || value !is JSONObject)throw InvalidExport("NOT_JSON_OBJECT")
            value
        } catch(e:InvalidExport){throw e}catch(_:Exception){throw InvalidExport("JSON_BOUNDS_OR_SYNTAX")}
        return readRoot(root)
    }
    internal fun readRoot(root:JSONObject):Export {
        val metadata = root.opt("metadata") as? JSONObject ?: throw InvalidExport("MISSING_METADATA")
        val version = metadata.opt("database_version")
        if (version !is Number || version.toString() != DATABASE_VERSION.toString()) throw InvalidExport("UNSUPPORTED_DATABASE_VERSION")
        val appVersion = (metadata.opt("app_version") as? String)?.takeIf { it.isNotBlank() && it.length<=128 } ?: throw InvalidExport("MISSING_APP_VERSION")
        val exported = (metadata.opt("export_date") as? String)?.takeIf { s ->
            runCatching { OffsetDateTime.parse(s) }.isSuccess || runCatching { LocalDateTime.parse(s) }.isSuccess
        } ?: throw InvalidExport("INVALID_EXPORT_DATE")
        val data = root.opt("data") as? JSONObject ?: throw InvalidExport("MISSING_DATA")
        val arrays = tables.associateWith { data.opt(it) as? JSONArray ?: throw InvalidExport("MISSING_TABLE") }
        var rows = 0L
        data.keySet().forEach { key ->
            val list = data.opt(key) as? JSONArray ?: throw InvalidExport("INVALID_TABLE")
            rows += list.length()
        }
        if (rows > 100000) throw InvalidExport("TOO_MANY_ROWS")
        val issues = mutableListOf<Issue>()
        root.keySet().filter { it !in setOf("metadata", "data") }.forEach { issues += Issue("export", null, it, Reason.UNSUPPORTED_FIELD) }
        metadata.keySet().filter { it !in setOf("app_version", "database_version", "export_date") }.forEach { issues += Issue("metadata", null, it, Reason.UNSUPPORTED_FIELD) }
        data.keySet().filter { it !in tables }.forEach { key ->
            issues += Issue(key, null, "", Reason.UNSUPPORTED_COLLECTION, (data.get(key) as JSONArray).length())
        }
        val sourceRows=linkedMapOf<String,String>()
        val reader = Reader(issues,sourceRows)
        val supplies = reader.rows(arrays.getValue("supply_items"), "supply_items") { row, id ->
            reader.known(row, "supply_items", id, setOf("id", "type", "name", "molecule", "administrationRoute", "ester", "dosingBasis", "deliveryForm", "totalDose", "usedDose", "dosePerUnit", "amount", "genericSupplyType"))
            issues += Issue("supply_items", id, "", Reason.REFERENCE_ONLY)
            Supply(id, row.string("type"), row.string("name"), reader.molecule(row, "supply_items", id),
                reader.route(row, "supply_items", id), reader.ester(row, "supply_items", id), reader.basis(row, "supply_items", id),
                row.string("deliveryForm"), row.string("totalDose"), row.string("usedDose"), row.string("dosePerUnit"),
                row.opt("amount").takeUnless { it == null || it == JSONObject.NULL }?.toString(), row.string("genericSupplyType"))
        }
        val schedules = reader.rows(arrays.getValue("medication_schedules"), "medication_schedules") { row, id ->
            val type = "medication_schedules"
            reader.known(row, type, id, setOf("id", "name", "dose", "startDate", "molecule", "administrationRoute", "ester", "scheduling", "dosingBasis"))
            issues += Issue(type, id, "", Reason.REFERENCE_ONLY)
            val start = row.string("startDate")
            if (start == null || runCatching { LocalDate.parse(start) }.isFailure) issues += Issue(type, id, "startDate", Reason.INVALID_VALUE)
            Schedule(id, row.string("name"), row.string("dose"), reader.decimal(row.string("dose"), type, id, "dose"),
                start, reader.molecule(row, type, id), reader.route(row, type, id), reader.ester(row, type, id), reader.basis(row, type, id), row.string("scheduling"))
        }
        val supplyIds = supplies.map { it.id }.toSet(); val scheduleIds = schedules.map { it.id }.toSet()
        val intakes = reader.rows(arrays.getValue("medication_intakes"), "medication_intakes") { row, id ->
            val type = "medication_intakes"
            reader.known(row, type, id, setOf("id", "scheduledTime", "takenDateTime", "takenTimeZone", "takenDose", "wastedAmount", "deadSpace", "scheduleId", "molecule", "administrationRoute", "ester", "medicationSupplyItemId", "genericSupplyItemIds", "notes", "placements", "dosingBasis", "injectionType"))
            val at = row.string("takenDateTime")
            if (at == null) issues += Issue(type, id, "takenDateTime", Reason.NOT_TAKEN)
            val molecule = reader.molecule(row, type, id); val basis = reader.basis(row, type, id)
            val schedule = reader.reference(row, "scheduleId", type, id, scheduleIds)
            val supply = reader.reference(row, "medicationSupplyItemId", type, id, supplyIds)
            Intake(id, at?.let { reader.timing(it, row.string("takenTimeZone"), type, id) }, row.string("scheduledTime"),
                row.string("takenDose"), reader.decimal(row.string("takenDose"), type, id, "takenDose"),
                when (basis) { "mass" -> molecule?.massUnit; "releaseRate" -> molecule?.rateUnit; else -> null },
                molecule, reader.route(row, type, id), reader.ester(row, type, id), basis, schedule, supply, row.string("notes"),
                row.string("genericSupplyItemIds"), row.string("placements"), row.string("wastedAmount"), row.string("deadSpace"), row.string("injectionType"))
        }
        val bloodTests = reader.rows(arrays.getValue("blood_tests"), "blood_tests") { row, id ->
            val type = "blood_tests"
            reader.known(row, type, id, setOf("id", "dateTime", "timeZone", "estradiolLevels", "testosteroneLevels", "notes"))
            val at = row.string("dateTime")
            if (at == null) issues += Issue(type, id, "dateTime", Reason.INVALID_VALUE)
            val values = listOfNotNull(
                reader.lab(row.string("estradiolLevels"), "E2", setOf("pg/mL", "pmol/L"), type, id),
                reader.lab(row.string("testosteroneLevels"), "T", setOf("ng/dL", "ng/mL", "nmol/L"), type, id))
            BloodTest(id, at?.let { reader.timing(it, row.string("timeZone"), type, id) }, values, row.string("notes"))
        }
        return Export(Metadata(appVersion, DATABASE_VERSION, exported), intakes, bloodTests, schedules, supplies, issues.toList(),sourceRows.toMap())
    }

    private fun JSONObject.string(key: String): String? = opt(key).let { if (it == null || it == JSONObject.NULL) null else it as? String }
    /** Evidence digest input, independently encoded identically on JVM/API26/29/35.
     * Clinical decimals in Mona are strings. Numeric JSON IDs remain integers. */
    fun canonical(value:Any?):String=when(value) {
        null,JSONObject.NULL->"null"
        is JSONObject->value.keySet().sorted().joinToString(",","{","}"){quote(it)+":"+canonical(value.get(it))}
        is JSONArray->(0 until value.length()).joinToString(",","[","]"){canonical(value.get(it))}
        is String->quote(value)
        is Boolean->value.toString()
        is Int,is Long->value.toString()
        is Number->{val n=BigDecimal(value.toString());require(n.precision()<=40 && n.scale() in -24..24);n.stripTrailingZeros().toPlainString()}
        else->throw InvalidExport("INVALID_JSON_VALUE")
    }
    private fun quote(value:String):String=buildString {
        append('"')
        for(c in value)when(c) {
            '"'->append("\\\"")
            '\\'->append("\\\\")
            else->if(c.code<32 || c.isSurrogate())append("\\u").append(c.code.toString(16).padStart(4,'0')) else append(c)
        }
        append('"')
    }
    private fun validUnicode(value:String):Boolean {
        var i=0
        while(i<value.length) {
            val c=value[i++]
            if(c.isHighSurrogate()){if(i==value.length || !value[i++].isLowSurrogate())return false}
            else if(c.isLowSurrogate())return false
        }
        return true
    }
    private class Reader(private val issues: MutableList<Issue>,private val sourceRows:MutableMap<String,String>) {
        fun <T> rows(array: JSONArray, type: String, transform: (JSONObject, String) -> T): List<T> {
            val ids = hashSetOf<String>()
            return (0 until array.length()).map { index ->
                val row = array.opt(index) as? JSONObject ?: throw InvalidExport("INVALID_ROW")
                val raw = row.opt("id")
                val id = (raw as? Number)?.toString()?.takeIf { it.toLongOrNull()?.let { n -> n >= 0 } == true } ?: throw InvalidExport("INVALID_ROW_ID")
                if (!ids.add(id)) throw InvalidExport("DUPLICATE_ROW_ID")
                if(type in setOf("medication_intakes","blood_tests"))sourceRows["$type:$id"]=canonical(row)
                transform(row, id)
            }
        }
        fun known(row: JSONObject, type: String, id: String, keys: Set<String>) {
            if(row.keySet().any {it.length>256})throw InvalidExport("FIELD_NAME_LIMIT")
            if(row.keySet().any {key->(row.opt(key) as? String)?.let{it.length>16384 || !validUnicode(it)}==true})throw InvalidExport("STRING_LIMIT_OR_ENCODING")
            row.keySet().filter { it !in keys }.forEach { issues += Issue(type, id, it, Reason.UNSUPPORTED_FIELD) }
            for (key in row.keySet().filter { it in keys }) {
                val value = row.opt(key)
                if (value == null || value == JSONObject.NULL) continue
                val number = key in setOf("id", "scheduleId", "medicationSupplyItemId", "amount")
                if ((number && (value !is Number || value.toString().toLongOrNull() == null)) || (!number && value !is String))
                    throw InvalidExport("INVALID_FIELD_TYPE")
            }
        }
        private fun embedded(raw: String, type: String, id: String, field: String): JSONObject? = try {
            ImportFormatDetector.bounds(raw)
            val tokens=JSONTokener(raw);val value=tokens.nextValue()
            require(tokens.nextClean()=='\u0000' && value is JSONObject)
            value
        } catch (_: ImportFormatDetector.TooLarge) { issues += Issue(type,id,field,Reason.INPUT_LIMIT);null }
        catch (_: Exception) { issues += Issue(type, id, field, Reason.INVALID_VALUE); null }
        fun decimal(raw: String?, type: String, id: String, field: String, allowZero: Boolean = false): BigDecimal? {
            val value = raw?.takeIf { it.length <= 128 }?.let { runCatching { BigDecimal(it) }.getOrNull() }
                ?.takeIf { (it.signum() > 0 || (allowZero && it.signum() == 0)) && it.precision() <= 40 && it.scale() in -12..24 }
            if (value == null) issues += Issue(type, id, field, Reason.INVALID_VALUE)
            return value
        }
        fun molecule(row: JSONObject, type: String, id: String): Molecule? {
            val value = row.string("molecule")?.let { embedded(it, type, id, "molecule") }
            value?.keySet()?.filter { it !in setOf("name", "massUnit", "rateUnit") }?.forEach { issues += Issue(type, id, "molecule.$it", Reason.UNSUPPORTED_FIELD) }
            value?.let { obj ->
                for(key in listOf("name","massUnit","rateUnit")) {
                    val raw=obj.opt(key)
                    if(raw!=null && raw!=JSONObject.NULL && raw !is String)throw InvalidExport("INVALID_MOLECULE_FIELD_TYPE")
                }
            }
            val name = value?.string("name")?.takeIf { it.isNotBlank() && it.length<=256 && validUnicode(it) }; val unit = value?.string("massUnit")?.takeIf { it.isNotBlank() && it.length<=32 && validUnicode(it) }
            if (name == null || unit == null) { issues += Issue(type, id, "molecule", Reason.INVALID_VALUE); return null }
            return Molecule(name, unit, value.string("rateUnit"))
        }
        fun route(row: JSONObject, type: String, id: String): String? = row.string("administrationRoute").also {
            if (it !in routes) issues += Issue(type, id, "administrationRoute", Reason.UNKNOWN_VALUE)
        }
        fun ester(row: JSONObject, type: String, id: String): String? = row.string("ester").also {
            if (it != null && it !in esters) issues += Issue(type, id, "ester", Reason.UNKNOWN_VALUE)
        }
        fun basis(row: JSONObject, type: String, id: String): String? = row.string("dosingBasis").also {
            if (it !in setOf("mass", "releaseRate")) issues += Issue(type, id, "dosingBasis", Reason.UNKNOWN_VALUE)
        }
        fun reference(row: JSONObject, field: String, type: String, id: String, valid: Set<String>): String? {
            val raw = row.opt(field)
            if (raw == null || raw == JSONObject.NULL) return null
            val key = (raw as? Number)?.toString()
            if (key == null || key !in valid) issues += Issue(type, id, field, Reason.MISSING_REFERENCE)
            return key
        }
        fun timing(raw: String, zone: String?, type: String, id: String): Timing {
            val instant = raw.takeIf { it.endsWith("Z") }?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (instant == null) issues += Issue(type, id, "timestamp", Reason.INVALID_VALUE)
            if (zone == null || runCatching { ZoneId.of(zone) }.isFailure) issues += Issue(type, id, "timeZone", Reason.UNKNOWN_VALUE)
            return Timing(raw, instant, zone)
        }
        fun lab(raw: String?, analyte: String, units: Set<String>, type: String, id: String): LabValue? {
            if (raw == null) return null
            val row = embedded(raw, type, id, analyte) ?: return LabValue(analyte, raw, null, null)
            row.keySet().filter { it !in setOf("value", "unit") }.forEach { issues += Issue(type, id, "$analyte.$it", Reason.UNSUPPORTED_FIELD) }
            val original = row.string("value"); val unit = row.string("unit")
            if (unit !in units) issues += Issue(type, id, analyte + ".unit", Reason.UNKNOWN_VALUE)
            return LabValue(analyte, original, decimal(original, type, id, analyte + ".value", allowZero = true), unit)
        }
    }
}
