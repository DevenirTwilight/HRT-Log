package net.plainnotes.app.data

import net.plainnotes.app.importer.HrtTracker
import org.json.JSONObject
import java.time.ZoneId

class HtImportSummary(val medications: Int, val intakes: Int, val alreadyImported: Int, val labs: Int, val weightSet: Boolean)

/**
 * Writes a planned HRT tracker import. Each event group becomes (or is mapped onto) one medication; new ones get the
 * exact PK profile from the export but stay in review until the user sets a schedule, capacity and reminders.
 * Intakes are history only (no stock deduction) and carry `ht:<event id>` so a later re-import skips them.
 */
internal object HrtTrackerWriter {
    suspend fun write(dao: NotesDao, plan: HrtTracker.Plan, targets: Map<HrtTracker.Group, Long?>, names: Map<HrtTracker.Group, String>,
                      weightKg: Double?, zone: ZoneId): HtImportSummary {
        val existing = dao.medications()
        val medIds = HashMap<HrtTracker.Group, Long>(); var created = 0
        plan.intakes.map { it.group }.distinct().forEachIndexed { i, g ->
            targets[g]?.let { id -> require(existing.any { it.id == id }); medIds[g] = id; return@forEachIndexed }
            val doses = plan.intakes.filter { it.group == g }.groupingBy { it.dose }.eachCount()
            val review = JSONObject().put("source", "hrttracker").put("raw", JSONObject()
                .put("intakeInterval", "").put("soonAlertDelay", "").put("lateAlertDelay", "").put("notifications", "").put("capacity", ""))
            val dose = doses.maxByOrNull { it.value }!!.key
            val id = dao.insertMedication(MedicationEntity(name = names[g] ?: g.ester, molecule = g.molecule, route = g.route, unit = g.unit, dose_per_intake = dose,
                container_capacity = dose, site_rotation = false, notifications_on = false, active = true, sort_order = existing.size + i, needs_review = review.toString()))
            dao.profile(ProfileEntity(id, g.ester, g.pkRoute, g.slTier, g.gelProductId, g.gelSite, g.gelAreaCm2, g.patchUgDay))
            medIds[g] = id; created++
        }
        val known = dao.records().mapNotNull { it.source_record_key }.toHashSet()
        var added = 0; var dups = 0
        plan.intakes.forEach { p ->
            if (!known.add(p.sourceKey)) { dups++; return@forEach }
            dao.record(RecordEntity(medication_id = medIds.getValue(p.group), taken_utc = p.taken.toEpochMilli(), taken_zone = zone.id, actual_dose = p.dose,
                status = "ON_TIME", origin = "IMPORT_HT", source_record_key = p.sourceKey, revision = 1, config_snapshot = MedicationSnapshot.encode(
                    dao.medication(medIds.getValue(p.group)).copy(molecule=p.group.molecule,route=p.group.route,unit=p.group.unit),
                    ProfileEntity(medIds.getValue(p.group),p.group.ester,p.group.pkRoute,p.group.slTier,p.group.gelProductId,p.group.gelSite,p.group.gelAreaCm2,p.group.patchUgDay))))
            added++
        }
        val labs = dao.labs(); var labCount = 0
        plan.labs.forEach { l ->
            if (labs.any { it.analyte_code == "E2" && it.sampled_utc == l.at.toEpochMilli() && it.value == l.value && it.unit == l.unit }) return@forEach
            dao.analyte(AnalyteEntity("E2", l.unit))
            dao.insertLab(LabValueEntity(analyte_code = "E2", value = l.value, unit = l.unit, sampled_utc = l.at.toEpochMilli(), sampled_zone = zone.id, note = "HRT tracker")); labCount++
        }
        weightKg?.let { dao.pkSettings(PkSettingsEntity(1, it)) }
        return HtImportSummary(created, added, dups, labCount, weightKg != null)
    }
}
