package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.HistoryPeriods
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.domain.SustainedPatterns
import net.plainnotes.app.domain.TherapyStandard
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** What the dialog edits: a candidate recognised from records ([key] null) or the current revision of a confirmed period. */
data class PeriodDraft(val key: String?, val medicationId: Long, val snapshot: MedicationSnapshot?, val identityJson: String, val standard: TherapyStandard,
                       val from: LocalDate, val lastDay: LocalDate?, val zone: ZoneId, val recordIds: List<Long>, val loggedDays: Int)

/** Callbacks for confirmed past periods (REQUIREMENTS §35a). */
class PeriodActions(val confirm: (String?, Long, TherapyStandard, LocalDate, LocalDate?, ZoneId, String, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
                    val revoke: (String) -> Unit = {}, val split: (String, LocalDate) -> Unit = { _, _ -> }, val merge: (String, String) -> Unit = { _, _ -> })

private fun evidenceJson(d: PeriodDraft, standard: TherapyStandard, from: LocalDate, lastDay: LocalDate?) = JSONObject()
    .put("detector", "sustained-patterns-v1").put("sustain_days", SustainedPatterns.SUSTAIN_DAYS).put("gap_days", SustainedPatterns.GAP_DAYS)
    .put("record_ids", JSONArray(d.recordIds.sorted())).put("records", d.recordIds.size).put("logged_days", d.loggedDays)
    .put("proposed", JSONObject().put("standard", JSONObject(HistoryPeriods.standardJson(d.standard.let { if (it.kind == "EVERY_N_DAYS") it else it.copy(kind = "EVERY_N_DAYS", interval = 1) })))
        .put("from", d.from.toString()).put("last_day", d.lastDay?.toString() ?: JSONObject.NULL).put("frequency_known", d.standard.kind == "EVERY_N_DAYS"))
    .put("user_changed", JSONArray(buildList {
        if (from != d.from) add("from"); if (lastDay != d.lastDay) add("last_day")
        if (standard.interval != d.standard.interval || standard.kind != d.standard.kind) add("frequency"); if (standard.doses != d.standard.doses) add("doses")
    })).toString()

@Composable fun HistoryPeriodDialog(draft: PeriodDraft, nextMergeable: String?, actions: PeriodActions, onDismiss: () -> Unit) {
    val known = draft.standard.kind == "EVERY_N_DAYS"
    var from by remember { mutableStateOf(draft.from) }
    var ongoing by remember { mutableStateOf(draft.lastDay == null) }
    var lastDay by remember { mutableStateOf(draft.lastDay ?: LocalDate.now()) }
    var interval by remember { mutableStateOf(if (known) draft.standard.interval.toString() else "") }
    var doses by remember { mutableStateOf(draft.standard.doses.let { if (known) it else it.take(1) }.map { inputNumber(it) }) }
    var pick by remember { mutableIntStateOf(0) }
    var revoke by remember { mutableStateOf(false) }
    val intervalV = interval.toIntOrNull()?.takeIf { it in 1..365 }
    val doseV = doses.map { it.toDoubleOrNull()?.takeIf { d -> d.isFinite() && d > 0 } }
    val valid = intervalV != null && doseV.isNotEmpty() && doseV.all { it != null } && (ongoing || lastDay >= from)
    val standard = draft.standard.copy(kind = "EVERY_N_DAYS", interval = intervalV ?: 1, weeklyCount = 0, doses = doseV.filterNotNull())
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("history-period-dialog"),
        title = { Text(stringResource(if (draft.key == null) R.string.period_confirm else R.string.period_edit)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.period_confirm_intro), style = MaterialTheme.typography.bodySmall)
            if (!LocalSimpleMode.current) draft.snapshot?.name?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text(stringResource(R.string.period_evidence_summary, draft.loggedDays, draft.recordIds.size), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.period_from), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { pick = 1 }, modifier = Modifier.fillMaxWidth()) { Text(formatDate(from)) }
            Row(Modifier.fillMaxWidth().toggleable(ongoing, role = Role.Checkbox) { ongoing = it }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(ongoing, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.period_ongoing))
            }
            if (!ongoing) {
                Text(stringResource(R.string.period_until), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pick = 2 }, modifier = Modifier.fillMaxWidth()) { Text(formatDate(lastDay)) }
            }
            NumberField(interval, { interval = it }, stringResource(R.string.period_interval_days), decimal = false, isError = intervalV == null)
            if (intervalV == null) Text(stringResource(R.string.period_frequency_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            DropdownField(stringResource(R.string.period_times_per_day), (1..6).toList(), doses.size, { it.toString() }, { n ->
                doses = List(n) { i -> doses.getOrNull(i) ?: doses.lastOrNull() ?: "" } })
            doses.forEachIndexed { i, v -> NumberField(v, { doses = doses.toMutableList().also { l -> l[i] = it } }, stringResource(R.string.period_dose_n, i + 1),
                suffix = draft.standard.unit?.let { unitLabel(it) }, isError = doseV[i] == null) }
            if (draft.key != null) {
                HorizontalDivider()
                OutlinedButton(onClick = { pick = 3 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.period_split)) }
                if (nextMergeable != null) OutlinedButton(onClick = { actions.merge(draft.key, nextMergeable); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.period_merge_next)) }
                TextButton(onClick = { revoke = true }) { Text(stringResource(R.string.period_revoke)) }
            }
        } },
        confirmButton = { Button(enabled = valid, onClick = {
            val last = if (ongoing) null else lastDay
            actions.confirm(draft.key, draft.medicationId, standard, from, last?.plusDays(1), draft.zone, draft.identityJson, evidenceJson(draft, standard, from, last)); onDismiss()
        }) { Text(stringResource(if (draft.key == null) R.string.period_confirm else R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    when (pick) {
        1 -> DatePickerModal(from, { pick = 0 }) { from = it; pick = 0 }
        2 -> DatePickerModal(lastDay, { pick = 0 }) { lastDay = it; pick = 0 }
        3 -> DatePickerModal(from.plusDays(1), { pick = 0 }) { day -> pick = 0; if (draft.key != null && day > draft.from && (draft.lastDay == null || day <= draft.lastDay)) { actions.split(draft.key, day); onDismiss() } }
    }
    if (revoke && draft.key != null) AlertDialog(onDismissRequest = { revoke = false }, text = { Text(stringResource(R.string.period_revoke_note)) },
        confirmButton = { Button(onClick = { revoke = false; actions.revoke(draft.key); onDismiss() }) { Text(stringResource(R.string.period_revoke)) } },
        dismissButton = { TextButton(onClick = { revoke = false }) { Text(stringResource(R.string.cancel)) } })
}
