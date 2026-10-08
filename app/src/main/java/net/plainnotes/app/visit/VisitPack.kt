package net.plainnotes.app.visit

import android.content.res.Resources
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.export.ExportData
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 2: per-record labels from confirmed periods instead of "unscheduled" (REQUIREMENTS §35a item 10). Saved packs keep their own version. */
const val VISIT_TEMPLATE_VERSION = 2

/** Extra (inferred), extra (marked by the user), dose differs, regimen unknown; a record awaiting a confirmed period counts as regimen unknown. */
fun labelCounts(ids: List<Long>, labels: Map<Long, Set<net.plainnotes.app.domain.RecordLabel>>): List<Int> {
    val l = ids.mapNotNull { labels[it] }
    fun n(vararg k: net.plainnotes.app.domain.RecordLabel) = l.count { set -> k.any { it in set } }
    return listOf(n(net.plainnotes.app.domain.RecordLabel.EXTRA_INFERRED), n(net.plainnotes.app.domain.RecordLabel.EXTRA_USER), n(net.plainnotes.app.domain.RecordLabel.DOSE_DIFFERS),
        n(net.plainnotes.app.domain.RecordLabel.REGIMEN_UNKNOWN, net.plainnotes.app.domain.RecordLabel.PENDING_PERIOD))
}

/** Default review window: from the last visit the user confirmed as completed; never inferred from time passing. */
data class VisitRange(val from: LocalDate, val to: LocalDate, val previous: AppointmentEntity?)

object VisitPlanning {
    const val FALLBACK_DAYS = 90L
    fun defaultRange(target: AppointmentEntity, all: List<AppointmentEntity>, today: LocalDate, zone: ZoneId): VisitRange {
        val to = minOf(Instant.ofEpochMilli(target.at_utc).atZone(zone).toLocalDate(), today)
        val previous = all.filter { it.id != target.id && it.completed_utc != null && it.at_utc < target.at_utc }.maxWithOrNull(compareBy({ it.at_utc }, { it.id }))
        val from = previous?.let { minOf(Instant.ofEpochMilli(it.at_utc).atZone(zone).toLocalDate(), to) } ?: to.minusDays(FALLBACK_DAYS - 1)
        return VisitRange(from, to, previous)
    }
}

data class MedicationFacts(val medicationId: Long, val name: String?, val taken: Int, val late: Int, val imported: Int, val confirmedMissed: Int, val skipped: Int, val unconfirmed: Int,
                           val labels: List<Int> = listOf(0, 0, 0, 0))

/** Counts of saved facts in an inclusive date range. No score, percentage, trend or judgement. */
data class VisitFacts(val days: Long, val regimenStarted: Int, val regimenEnded: Int, val medications: List<MedicationFacts>, val labs: Int, val analytes: List<String>,
                      val symptomDays: Int, val symptomGroups: Int, val reviews: Int, val dailyDays: Int, val noteDays: Int, val otherAppointments: Int, val milestones: Int) {
    fun json(): JSONObject = JSONObject().put("days", days).put("regimen_started", regimenStarted).put("regimen_ended", regimenEnded)
        .put("medications", org.json.JSONArray(medications.map { JSONObject().put("medication_id", it.medicationId).put("taken", it.taken).put("late", it.late).put("imported", it.imported)
            .put("confirmed_missed", it.confirmedMissed).put("skipped", it.skipped).put("unconfirmed", it.unconfirmed)
            .put("labels", JSONObject().put("basis", "inferred_from_records").put("extra_inferred", it.labels[0]).put("extra_user", it.labels[1])
                .put("dose_differs", it.labels[2]).put("regimen_unknown", it.labels[3])) }))
        .put("labs", labs).put("symptom_days", symptomDays).put("symptom_groups", symptomGroups).put("reviews", reviews).put("daily_days", dailyDays)
        .put("note_days", noteDays).put("other_appointments", otherAppointments).put("milestones", milestones)

    companion object {
        fun build(d: ExportData, target: AppointmentEntity, from: LocalDate, to: LocalDate, zone: ZoneId,
                  labels: Map<Long, Set<net.plainnotes.app.domain.RecordLabel>> = emptyMap()): VisitFacts {
            require(from <= to)
            val start = from.atStartOfDay(zone).toInstant().toEpochMilli(); val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            fun inside(ms: Long?) = ms != null && ms >= start && ms < end
            fun day(value: String) = LocalDate.parse(value) in from..to
            val records = d.records.filter { it.deleted_at_utc == null }
            // Intakes belong to their actual time; missed/skipped/unconfirmed to their scheduled time.
            val taken = records.filter { it.status in listOf("ON_TIME", "LATE") && inside(it.taken_utc) }
            val notTaken = records.filter { it.status in listOf("MISSED", "SKIPPED") && inside(it.scheduled_utc) }
            val names = d.medications.associate { it.id to it.name }
            val meds = (taken + notTaken).groupBy { it.medication_id }.map { (id, rows) ->
                val latest = rows.maxByOrNull { it.taken_utc ?: it.scheduled_utc ?: 0 }
                MedicationFacts(id, latest?.let { MedicationSnapshot.decode(it.config_snapshot, id)?.name } ?: names[id],
                    rows.count { it.status in listOf("ON_TIME", "LATE") }, rows.count { it.status == "LATE" }, rows.count { it.status in listOf("ON_TIME", "LATE") && it.origin.startsWith("IMPORT_") },
                    rows.count { it.status == "MISSED" && !it.unconfirmed }, rows.count { it.status == "SKIPPED" }, rows.count { it.unconfirmed },
                    labelCounts(rows.filter { it.status in listOf("ON_TIME", "LATE") }.map { it.id }, labels))
            }.sortedBy { it.medicationId }
            val labs = d.labs.filter { inside(it.sampled_utc) }
            val symptoms = d.symptoms.filter { day(it.date) }
            return VisitFacts(to.toEpochDay() - from.toEpochDay() + 1,
                d.regimens.count { inside(it.effective_from_utc) }, d.regimens.count { inside(it.effective_until_utc) }, meds,
                labs.size, labs.map { it.analyte_code }.distinct().sorted(), symptoms.map { it.date }.distinct().size, symptoms.map { it.group_id }.distinct().size,
                d.reviews.count { day(it.date) }, d.scores.filter { day(it.date) }.map { it.date }.distinct().size, d.notes.count { day(it.date) },
                d.appointments.count { it.id != target.id && inside(it.at_utc) }, d.milestones.count { day(it.date) })
        }
    }
}

fun factLines(res: Resources, f: VisitFacts, unknownName: String): List<String> = buildList {
    add(res.getString(R.string.visit_fact_days, f.days))
    add(res.getString(R.string.visit_fact_regimens, f.regimenStarted, f.regimenEnded))
    if (f.medications.isEmpty()) add(res.getString(R.string.visit_fact_no_intakes))
    f.medications.forEach { m -> add(res.getString(R.string.visit_fact_medication, m.name ?: unknownName, m.taken, m.late, m.imported, m.confirmedMissed, m.skipped, m.unconfirmed))
        if (m.labels.any { it > 0 }) add(res.getString(R.string.visit_fact_labels, m.labels[0], m.labels[1], m.labels[2], m.labels[3])) }
    add(res.getString(R.string.visit_fact_unconfirmed_note))
    add(res.getString(R.string.visit_fact_labs, f.labs) + if (f.analytes.isEmpty()) "" else " · " + f.analytes.joinToString(", "))
    add(res.getString(R.string.visit_fact_symptoms, f.symptomDays, f.symptomGroups))
    add(res.getString(R.string.visit_fact_reviews, f.reviews))
    add(res.getString(R.string.visit_fact_daily, f.dailyDays, f.noteDays))
    add(res.getString(R.string.visit_fact_appointments, f.otherAppointments))
}

/** Everything a visit pack needs besides the base export data; formatting of frozen regimens is done by the caller's UI. */
class VisitPackSpec(val appointment: AppointmentEntity, val from: LocalDate, val to: LocalDate, val sections: Set<VisitSection>, val questions: List<VisitQuestionEntity>,
                    val regimenLabels: Map<Long, String>, val unknownName: String)

/** SHA-256 over the template, language, range, chosen parts and every included row; lets the user match a PDF to its record. Not a signature. */
object VisitDigest {
    fun compute(d: ExportData, spec: VisitPackSpec, facts: VisitFacts, language: String, zone: ZoneId): String {
        val start = spec.from.atStartOfDay(zone).toInstant().toEpochMilli(); val end = spec.to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun inside(ms: Long?) = ms != null && ms >= start && ms < end
        fun day(value: String) = LocalDate.parse(value) in spec.from..spec.to
        val lines = mutableListOf("v$VISIT_TEMPLATE_VERSION", language, zone.id, spec.from.toString(), spec.to.toString(), VisitSection.encode(spec.sections), spec.appointment.toString())
        spec.sections.sortedBy { it.ordinal }.forEach { s ->
            lines += "#" + s.name
            lines += when (s) {
                VisitSection.FACTS -> listOf(facts.json().toString())
                VisitSection.REGIMEN -> d.medications.filter { it.active }.sortedBy { it.id }.map { it.toString() + d.scheduleText[it.id] } +
                    d.regimens.filter { it.effective_from_utc < end && (it.effective_until_utc ?: Long.MAX_VALUE) > start }.sortedBy { it.id }.map { it.toString() }
                VisitSection.INTAKES -> d.records.filter { it.deleted_at_utc == null && inside(it.taken_utc ?: it.scheduled_utc) }.sortedBy { it.id }.map { it.toString() }
                VisitSection.LABS -> d.labs.filter { inside(it.sampled_utc) }.sortedBy { it.id }.flatMap { l -> listOf(l.toString()) + d.labContexts.filter { it.lab_id == l.id }.maxByOrNull { it.revision }?.toString().orEmpty() }
                VisitSection.SYMPTOMS -> d.symptoms.filter { day(it.date) }.sortedWith(compareBy({ it.date }, { it.group_id })).map { it.toString() }
                VisitSection.REVIEWS -> d.reviews.filter { day(it.date) }.sortedBy { it.id }.map { it.toString() }
                VisitSection.DAILY -> d.scores.filter { day(it.date) }.sortedWith(compareBy({ it.date }, { it.item_id })).map { it.toString() } + d.notes.filter { day(it.date) }.sortedBy { it.date }.map { it.toString() }
                VisitSection.QUESTIONS -> spec.questions.sortedWith(compareBy({ it.sort_order }, { it.id })).map { it.toString() }
                VisitSection.PACKAGES -> d.containers.filter { it.source_note != null || it.batch != null }.sortedBy { it.id }.map { it.toString() }
                VisitSection.MILESTONES -> d.milestones.filter { day(it.date) }.sortedBy { it.id }.map { it.toString() }
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
