package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.*
import java.time.*

/**
 * Synthetic LAB-04 data, all inside one treatment period that started 60 days ago:
 * - panel 1: six results (E2 and ALT outside their report range) at one time, "Synthetic Lab A";
 * - result 20: one PRL without a panel;
 * - results 30, 31: no panel, same time and laboratory "Synthetic Lab B" (a LAB-01 display group);
 * - result 32: same time as 30/31 but "Synthetic Lab C"; result 33: same day and laboratory as 30/31, another time.
 */
internal object Lab04Fixture {
    val zone: ZoneId = ZoneId.of("UTC")
    private val day = 86_400_000L
    val start: Long = Instant.now().minusSeconds(86_400L * 60).truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
    val t1 = start + 20 * day + 8 * 3_600_000L
    val t2 = start + 25 * day + 9 * 3_600_000L
    val t3 = start + 30 * day + 7 * 3_600_000L
    val panel = LabPanelEntity(1, "panel-uuid-1", t1, "UTC", "Synthetic Lab A", null, t1)
    fun lab(id: Long, code: String, value: Double, unit: String, at: Long, laboratory: String?, panel: Long? = null, lo: Double? = null, hi: Double? = null) =
        LabValueEntity(id, code, value, unit, at, "UTC", lo, hi, if (lo != null || hi != null) unit else null, laboratory, null, panel)
    val panelLabs = listOf(lab(1, "FSH", 5.0, "IU/L", t1, "Synthetic Lab A", 1, 1.0, 10.0), lab(2, "LH", 4.0, "IU/L", t1, "Synthetic Lab A", 1, 1.0, 10.0),
        lab(3, "E2", 400.0, "pg/mL", t1, "Synthetic Lab A", 1, 20.0, 300.0), lab(4, "T", 30.0, "ng/dL", t1, "Synthetic Lab A", 1, 10.0, 50.0),
        lab(5, "ALT", 80.0, "U/L", t1, "Synthetic Lab A", 1, 5.0, 40.0), lab(6, "AST", 20.0, "U/L", t1, "Synthetic Lab A", 1, 5.0, 40.0))
    val single = lab(20, "PRL", 10.0, "ng/mL", t2, null)
    val loose = listOf(lab(30, "E2", 150.0, "pg/mL", t3, "Synthetic Lab B"), lab(31, "T", 40.0, "ng/dL", t3, "Synthetic Lab B"),
        lab(32, "LH", 3.0, "IU/L", t3, "Synthetic Lab C"), lab(33, "FSH", 4.0, "IU/L", t3 + 2 * 3_600_000L, "Synthetic Lab B"))
    val labs = panelLabs + single + loose
    private val med = MedicationEntity(1, "Synthetic", "E2", "ORAL", "MG", 2.0, 30.0, site_rotation = false, notifications_on = false, active = true, sort_order = 0)
    private val def = RegimenDefinition(MedicationSnapshot.encode(med, ProfileEntity(1, "EV", "oral")), "EVERY_N_DAYS", 1, 0, 2.0, "UTC",
        LocalDate.ofInstant(Instant.ofEpochMilli(start), ZoneOffset.UTC).toString(), null, listOf("08:00:00" to null))
    val regimens = listOf(RegimenVersionEntity(1, 1, start, null, "UTC", def.json(), def.signature(), "APP", start))
    fun extra(labs: List<LabValueEntity> = this.labs, panels: List<LabPanelEntity> = listOf(panel)) =
        NotesViewModel.ExtraState(regimens = regimens, labs = labs, labPanels = panels)
    fun event(lab: LabValueEntity, period: String?) = PeriodEvent("lab:${lab.id}", EventKind.LAB, Instant.ofEpochMilli(lab.sampled_utc),
        LocalDate.ofInstant(Instant.ofEpochMilli(lab.sampled_utc), zone), EventSource.Lab(lab), period, emptySet(), false)
}
