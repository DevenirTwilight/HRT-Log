package net.plainnotes.app.importer

import net.plainnotes.app.domain.ScheduleEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Importer for a user's own Trans Memo export (Room/SQLite, user_version 8).
 * Principle (docs/PLAN.md 5.0): a field is mapped only when the evidence is clear; everything else
 * is imported as unknown and must be confirmed by the user. The structure was described by the user;
 * no Trans Memo code is used.
 */
interface SqlSource {
    fun userVersion(): Int
    /** Column names, or null when the table does not exist. */
    fun columns(table: String): List<String>?
    fun rows(table: String): List<Map<String, Any?>>
}

class TmIntake(val id: Long, val productId: Long, val scheduledAt: String?, val takenAt: String?, val plannedDose: Double?, val realDose: Double?, val realSide: String?, val state: String)
class TmProduct(val id: Long, val name: String?, val molecule: String?, val unit: String?, val dosePerIntake: Double?, val capacity: Double?, val expirationDays: Long?,
                val intakeInterval: Long?, val soonAlertDelay: Long?, val lateAlertDelay: Long?, val handleSide: Long?, val inUse: Long?, val notifications: Long?)

class TmExport(
    val products: List<TmProduct>, val intakeTimes: Map<Long, List<String>>, val intakes: List<TmIntake>,
    val containers: List<Map<String, Any?>>, val wellbeingTypes: List<Map<String, Any?>>, val wellbeing: List<Map<String, Any?>>,
    val notes: List<Map<String, Any?>>, val appointments: List<Map<String, Any?>>,
)

class InvalidExport(val reason: String) : Exception(reason)

object TransMemo {
    private val REQUIRED = mapOf(
        "products" to listOf("id", "name", "molecule", "unit", "dosePerIntake", "capacity", "expirationDays", "intakeInterval", "soonAlertDelay", "lateAlertDelay", "handleSide", "inUse", "notifications"),
        "product_intake_time" to listOf("id", "productId", "intakeTime"),
        "intakes" to listOf("id", "productId", "scheduledAt", "takenAt", "plannedDose", "realDose", "plannedSide", "realSide", "state"),
        "containers" to listOf("id", "productId", "usedCapacity", "openDate", "state"),
        "wellbeing_types" to listOf("id", "name", "defaultType", "enabled"),
        "wellbeing" to listOf("id", "date", "typeId", "value"),
        "notes" to listOf("id", "date", "text"),
        "medical_appointments" to listOf("id", "type", "scheduledAt", "location", "doctorName", "notes", "reminderMinutesBefore"),
    )

    /** Molecule codes seen in the user's export or given in the requirements. */
    val MOLECULES_KNOWN = mapOf("ESTRADIOL" to "E2", "SPIRONOLACTONE" to "SPI", "CYPROTERONE_ACETATE" to "CPA")
    /** Mapped by naming analogy only; flagged as inferred in the review. */
    val MOLECULES_INFERRED = mapOf("TESTOSTERONE" to "T", "PROGESTERONE" to "P4", "BICALUTAMIDE" to "BICA", "FINASTERIDE" to "FIN", "DUTASTERIDE" to "DUT",
        "ANDROSTANOLONE" to "DHT", "DIHYDROTESTOSTERONE" to "DHT", "CHLORMADINONE_ACETATE" to "CMA", "NOMEGESTROL_ACETATE" to "NOMAC", "TRIPTORELIN" to "TRIP")
    val UNITS = mapOf("MILLIGRAM" to "MG", "PILL" to "TABLET")
    /** Trans Memo wellbeing types to HRT Log item keys. Mood, energy and sleep go to the new daily items (same 1-5 concept); the rest to previous items. */
    val WELLBEING_KEYS = mapOf("OVERALL" to "OVERALL", "MOOD" to "DAY_MOOD", "EMO_STABILITY" to "EMO_STABILITY", "DYNAMISM" to "DAY_ENERGY", "AGGRESSIVENESS" to "AGGRESSIVENESS",
        "LIBIDO" to "LIBIDO", "PAIN" to "PAIN", "PERIODS" to "PERIOD_LIKE", "APPETITE" to "APPETITE", "SLEEP_QUALITY" to "DAY_SLEEP", "SKIN_QUALITY" to "SKIN_QUALITY")

    private fun Map<String, Any?>.long(k: String) = (this[k] as? Number)?.toLong()
    private fun Map<String, Any?>.double(k: String) = (this[k] as? Number)?.toDouble()
    private fun Map<String, Any?>.str(k: String) = this[k]?.toString()

    fun read(src: SqlSource): TmExport {
        if (src.userVersion() != 8) throw InvalidExport("user_version=${src.userVersion()}, expected 8")
        REQUIRED.forEach { (t, cols) ->
            val have = src.columns(t) ?: throw InvalidExport("missing table $t")
            val missing = cols - have.toSet()
            if (missing.isNotEmpty()) throw InvalidExport("table $t lacks ${missing.joinToString()}")
        }
        val products = src.rows("products").map { r -> TmProduct(r.long("id")!!, r.str("name"), r.str("molecule"), r.str("unit"), r.double("dosePerIntake"), r.double("capacity"),
            r.long("expirationDays"), r.long("intakeInterval"), r.long("soonAlertDelay"), r.long("lateAlertDelay"), r.long("handleSide"), r.long("inUse"), r.long("notifications")) }
        val times = src.rows("product_intake_time").groupBy({ it.long("productId")!! }, { it.str("intakeTime") ?: "" })
        val intakes = src.rows("intakes").map { r -> TmIntake(r.long("id")!!, r.long("productId")!!, r.str("scheduledAt"), r.str("takenAt"), r.double("plannedDose"), r.double("realDose"), r.str("realSide"), r.str("state") ?: "") }
        return TmExport(products, times, intakes, src.rows("containers"), src.rows("wellbeing_types"), src.rows("wellbeing"), src.rows("notes"), src.rows("medical_appointments"))
    }

    private val LOCAL: DateTimeFormatter = DateTimeFormatterBuilder().appendPattern("uuuu-MM-dd'T'HH:mm:ss").optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd().toFormatter()
    /** Zone-less local timestamps are interpreted in [zone]; gaps move forward, overlaps take the earlier offset. */
    fun instant(local: String?, zone: ZoneId): Instant? = local?.takeIf { it.isNotBlank() }?.let { ScheduleEngine.wallInstant(LocalDateTime.parse(it.trim(), LOCAL), zone) }
    fun date(s: String?): LocalDate? = s?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
    fun time(s: String?): LocalTime? = s?.let { runCatching { LocalTime.parse(it.trim()).withSecond(0).withNano(0) }.getOrNull() }

    // ------------------------------------------------------------------ preview
    class Preview(
        val products: Int, val takenIntakes: Int, val takenWithoutTime: Int, val lateWithoutTime: Int, val lateWithTime: Int, val missed: Int, val pending: Int, val unknownStates: Int,
        val intakeRange: Pair<Instant, Instant>?, val containers: Int, val unknownContainerStates: Int,
        val wellbeingScores: Int, val wellbeingValues: Map<Long, Int>, val wellbeingRange: Pair<LocalDate, LocalDate>?, val notesWithText: Int, val emptyNotes: Int, val appointments: Int,
        val unknownMolecules: Set<String>, val inferredMolecules: Set<String>, val unknownUnits: Set<String>,
    ) {
        val needsLateChoice get() = lateWithoutTime > 0
        val needsWellbeingChoice get() = wellbeingScores > 0
    }

    fun preview(e: TmExport, zone: ZoneId): Preview {
        val taken = e.intakes.filter { it.state == "TAKEN" || it.state == "LATE" }
        val wbDates = e.wellbeing.mapNotNull { date(it.str("date")) }
        return Preview(
            e.products.size, taken.count { it.takenAt != null }, e.intakes.count { it.state == "TAKEN" && it.takenAt == null },
            e.intakes.count { it.state == "LATE" && it.takenAt == null }, e.intakes.count { it.state == "LATE" && it.takenAt != null }, e.intakes.count { it.state == "MISSED" },
            e.intakes.count { it.state == "PENDING" }, e.intakes.count { it.state !in listOf("TAKEN", "LATE", "MISSED", "PENDING") },
            e.intakes.filter { it.state != "PENDING" }.mapNotNull { (it.takenAt ?: it.scheduledAt)?.let { s -> runCatching { instant(s, zone) }.getOrNull() } }.let { if (it.isEmpty()) null else it.min() to it.max() },
            e.containers.size, e.containers.count { it.str("state") !in listOf("OPEN", "EMPTY") },
            e.wellbeing.size, e.wellbeing.mapNotNull { it.long("value") }.groupingBy { it }.eachCount(), if (wbDates.isEmpty()) null else wbDates.min() to wbDates.max(),
            e.notes.count { !it.str("text").isNullOrBlank() }, e.notes.count { it.str("text").isNullOrBlank() }, e.appointments.size,
            e.products.mapNotNull { it.molecule }.filter { it !in MOLECULES_KNOWN && it !in MOLECULES_INFERRED }.toSet(),
            e.products.mapNotNull { it.molecule }.filter { it in MOLECULES_INFERRED }.toSet(),
            e.products.mapNotNull { it.unit }.filter { it !in UNITS }.toSet(),
        )
    }

    // ------------------------------------------------------------------ mapping
    enum class LateHandling { SKIP, AS_MISSED }
    enum class WellbeingScale { ONE_TO_FIVE, ZERO_BASED, SKIP }
    class Choices(
        /** Late threshold (minutes) per Trans Memo product id; required for each product with taken intakes. */
        val lateMinutes: Map<Long, Int>,
        val lateHandling: LateHandling?,
        val wellbeingScale: WellbeingScale?,
    )

    class PlannedMedication(
        val sourceId: Long, val name: String, val molecule: String, val unit: String, val dose: Double, val capacity: Double, val expiryDays: Int?,
        val siteRotation: Boolean, val active: Boolean, val prefillTimes: List<LocalTime>, val prefillDaily: Boolean,
        /** Raw Trans Memo values that could not be mapped, shown in the review. */
        val review: Map<String, String>,
    )
    class PlannedIntake(val sourceKey: String, val productId: Long, val scheduled: Instant?, val taken: Instant?, val plannedDose: Double?, val actualDose: Double?,
                        val status: String, val lateMinutes: Int?, val site: String?)
    class PlannedContainer(val productId: Long, val capacity: Double, val used: Double, val openedOn: LocalDate?, val state: String)
    class PlannedCheckinItem(val sourceId: Long, val builtinKey: String?, val label: String?, val enabled: Boolean)
    class PlannedScore(val date: LocalDate, val itemSourceId: Long, val value: Int)
    class PlannedAppointment(val at: Instant, val location: String?, val practitioner: String?, val note: String?, val reminderMinutes: Int)
    class Plan(val medications: List<PlannedMedication>, val intakes: List<PlannedIntake>, val containers: List<PlannedContainer>, val items: List<PlannedCheckinItem>,
               val scores: List<PlannedScore>, val notes: Map<LocalDate, String>, val appointments: List<PlannedAppointment>, val skipped: Map<String, Int>)

    class MissingChoice(what: String) : Exception(what)

    fun plan(e: TmExport, zone: ZoneId, c: Choices): Plan {
        val p = preview(e, zone)
        if (p.needsLateChoice && c.lateHandling == null) throw MissingChoice("lateHandling")
        if (p.needsWellbeingChoice && c.wellbeingScale == null) throw MissingChoice("wellbeingScale")
        val skipped = linkedMapOf<String, Int>()
        fun skip(k: String) { skipped[k] = (skipped[k] ?: 0) + 1 }
        val meds = e.products.map { pr ->
            val review = linkedMapOf<String, String>()
            val molecule = pr.molecule?.let { MOLECULES_KNOWN[it] ?: MOLECULES_INFERRED[it]?.also { _ -> review["molecule_inferred"] = pr.molecule } } ?: "OTHER".also { review["molecule"] = pr.molecule ?: "" }
            val unit = pr.unit?.let { UNITS[it] } ?: "OTHER".also { review["unit"] = pr.unit ?: "" }
            review["intakeInterval"] = pr.intakeInterval?.toString() ?: ""
            review["soonAlertDelay"] = pr.soonAlertDelay?.toString() ?: ""
            review["lateAlertDelay"] = pr.lateAlertDelay?.toString() ?: ""
            review["notifications"] = pr.notifications?.toString() ?: ""
            if (molecule == "E2") review["route"] = ""
            val times = e.intakeTimes[pr.id].orEmpty().mapNotNull { time(it) }.distinct().sorted()
            PlannedMedication(pr.id, pr.name?.takeIf { it.isNotBlank() } ?: (pr.molecule ?: "?"), molecule, unit, pr.dosePerIntake?.takeIf { it > 0 } ?: 1.0,
                pr.capacity?.takeIf { it > 0 } ?: 1.0, pr.expirationDays?.toInt()?.takeIf { it > 0 }, (pr.handleSide ?: 0) != 0L, (pr.inUse ?: 0) != 0L,
                times, pr.intakeInterval == 1L && times.isNotEmpty(), review)
        }
        val productIds = e.products.map { it.id }.toSet()
        val intakes = e.intakes.mapNotNull { i ->
            if (i.productId !in productIds) { skip("intake_unknown_product"); return@mapNotNull null }
            val key = "tm:intakes:${i.id}:${i.scheduledAt}"
            val scheduled = instant(i.scheduledAt, zone)
            when {
                i.state == "PENDING" -> { skip("pending"); null }
                (i.state == "TAKEN" || i.state == "LATE") && i.takenAt != null -> {
                    val late = c.lateMinutes[i.productId] ?: throw MissingChoice("lateMinutes:${i.productId}")
                    val taken = instant(i.takenAt, zone)!!
                    val status = if (scheduled != null && taken.isAfter(scheduled.plusSeconds(late * 60L))) "LATE" else "ON_TIME"
                    val dose = i.realDose?.takeIf { it > 0 }
                    PlannedIntake(key, i.productId, scheduled, taken, i.plannedDose?.takeIf { it > 0 }, dose, status, late, i.realSide?.takeIf { it.isNotBlank() && it != "UNDEFINED" })
                }
                i.state == "TAKEN" -> { skip("taken_without_time"); null }
                i.state == "LATE" -> when (c.lateHandling) {
                    LateHandling.AS_MISSED -> PlannedIntake(key, i.productId, scheduled, null, i.plannedDose?.takeIf { it > 0 }, null, "MISSED", null, null)
                    else -> { skip("late_skipped"); null }
                }
                i.state == "MISSED" -> PlannedIntake(key, i.productId, scheduled, null, i.plannedDose?.takeIf { it > 0 }, null, "MISSED", null, null)
                else -> { skip("unknown_state"); null }
            }
        }
        val capacity = e.products.associate { it.id to (it.capacity?.takeIf { c -> c > 0 } ?: 1.0) }
        val containers = e.containers.mapNotNull { r ->
            val pid = (r["productId"] as? Number)?.toLong() ?: return@mapNotNull null
            val state = when (r["state"]?.toString()) { "OPEN" -> "IN_USE"; "EMPTY" -> "EMPTY"; else -> { skip("container_unknown_state"); return@mapNotNull null } }
            val cap = capacity[pid] ?: return@mapNotNull null
            PlannedContainer(pid, cap, ((r["usedCapacity"] as? Number)?.toDouble() ?: 0.0).coerceIn(0.0, cap), date(r["openDate"]?.toString()), state)
        }
        val items = e.wellbeingTypes.map { r ->
            val name = r["name"]?.toString()?.takeIf { it.isNotBlank() }
            PlannedCheckinItem((r["id"] as Number).toLong(), if (name == null) WELLBEING_KEYS[r["defaultType"]?.toString()] else null,
                name ?: r["defaultType"]?.toString()?.takeIf { WELLBEING_KEYS[it] == null }, ((r["enabled"] as? Number)?.toInt() ?: 1) != 0)
        }
        val scores = if (c.wellbeingScale == WellbeingScale.SKIP) emptyList() else e.wellbeing.mapNotNull { r ->
            val raw = (r["value"] as? Number)?.toInt() ?: return@mapNotNull null
            val v = if (c.wellbeingScale == WellbeingScale.ZERO_BASED) raw + 1 else raw
            val d = date(r["date"]?.toString())
            if (v !in 1..5 || d == null) { skip("score_out_of_range"); null } else PlannedScore(d, (r["typeId"] as Number).toLong(), v)
        }
        val notes = e.notes.mapNotNull { r ->
            val t = r["text"]?.toString()?.trim(); val d = date(r["date"]?.toString())
            if (t.isNullOrEmpty() || d == null) { skip("empty_note"); null } else d to t
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.joinToString("\n") }
        val appts = e.appointments.mapNotNull { r ->
            val at = instant(r["scheduledAt"]?.toString(), zone) ?: return@mapNotNull null
            val type = r["type"]?.toString()?.takeIf { it.isNotBlank() }
            PlannedAppointment(at, r["location"]?.toString()?.takeIf { it.isNotBlank() }, r["doctorName"]?.toString()?.takeIf { it.isNotBlank() },
                listOfNotNull(type?.let { "[Trans Memo: $it]" }, r["notes"]?.toString()?.takeIf { it.isNotBlank() }).joinToString("\n").ifBlank { null },
                ((r["reminderMinutesBefore"] as? Number)?.toInt() ?: 0).coerceAtLeast(0))
        }
        return Plan(meds, intakes, containers, items, scores, notes, appts, skipped)
    }
}
