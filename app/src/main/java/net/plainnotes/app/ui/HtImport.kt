package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.importer.HrtTracker
import java.time.ZoneId

/** Where a group of tracker events goes: a new medication (null) or an existing one. */
private class Target(val medication: MedicationEntity?)

/** An existing medication whose PK profile matches the group exactly, if there is exactly one. */
fun matchingMedication(g: HrtTracker.Group, meds: List<MedicationEntity>, profiles: Map<Long, ProfileEntity>): MedicationEntity? =
    meds.filter { m -> val p = profiles[m.id]
        m.molecule == g.molecule && m.route == g.route && p != null && p.ester == g.ester && p.pk_route == g.pkRoute && p.sl_tier == g.slTier &&
            p.gel_product_id == g.gelProductId && p.gel_site == g.gelSite && p.patch_release_ug_day == g.patchUgDay }.singleOrNull()

@Composable fun groupLabel(g: HrtTracker.Group): String = listOfNotNull(choiceLabel(g.ester), choiceLabel(g.route),
    g.slTier?.let { slTierLabel(it) }, g.patchUgDay?.let { stringResource(R.string.ht_patch_rate, displayNumber(it)) }).joinToString(" · ")

@Composable private fun skipLabel(reason: String) = stringResource(when (reason) {
    "patch_remove" -> R.string.ht_skip_patch_remove; "sublingual_custom" -> R.string.ht_skip_sublingual_custom
    "gel_custom_product", "custom_gel_products" -> R.string.ht_skip_gel_custom; "gel_wash_or_coapplied" -> R.string.ht_skip_gel_wash
    "patch_zero_order_missing" -> R.string.ht_skip_patch_rate; else -> R.string.ht_skip_unknown
})

@Composable fun HtImportWizard(export: HrtTracker.Export, preview: HrtTracker.Preview, meds: List<MedicationEntity>, profiles: Map<Long, ProfileEntity>,
                               onCancel: () -> Unit, onImport: (HrtTracker.Duplicates?, Map<HrtTracker.Group, Long?>, Map<HrtTracker.Group, String>, Boolean) -> Unit) {
    val defaultNames = preview.groups.keys.associateWith { g -> stringResource(R.string.ht_default_name, choiceLabel(g.ester), choiceLabel(g.route)) }
    val targets = remember { mutableStateMapOf<HrtTracker.Group, Target>().apply { preview.groups.keys.forEach { put(it, Target(matchingMedication(it, meds, profiles))) } } }
    val names = remember { mutableStateMapOf<HrtTracker.Group, String>().apply { putAll(defaultNames) } }
    var duplicates by remember { mutableStateOf<HrtTracker.Duplicates?>(null) }
    var weight by remember { mutableStateOf(preview.weightKg != null) }
    val zone = ZoneId.systemDefault()
    val ready = (!preview.needsDuplicateChoice || duplicates != null) && preview.groups.isNotEmpty() &&
        preview.groups.keys.all { targets[it]?.medication != null || !names[it].isNullOrBlank() }
    AlertDialog(onDismissRequest = onCancel, icon = { Icon(Icons.Outlined.MoveToInbox, null) }, title = { Text(stringResource(R.string.import_ht)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val first = preview.first; val last = preview.last
            val range = if (first != null && last != null) "${formatShortDate(first.atZone(zone).toLocalDate())} – ${formatShortDate(last.atZone(zone).toLocalDate())}" else "—"
            Text(stringResource(R.string.ht_summary, preview.events, range, preview.labs), style = MaterialTheme.typography.bodyMedium)
            preview.groups.forEach { (g, n) ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(groupLabel(g), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.ht_group_count, n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val options = listOf<MedicationEntity?>(null) + meds
                    DropdownField(stringResource(R.string.ht_import_into), options, targets[g]?.medication, { it?.name ?: stringResource(R.string.ht_new_medication) }, { targets[g] = Target(it) })
                    val chosen = targets[g]?.medication
                    if (chosen == null) OutlinedTextField(names[g] ?: "", { names[g] = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true)
                    else if (matchingMedication(g, listOf(chosen), profiles) == null)
                        Text(stringResource(R.string.ht_profile_differs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                } }
            }
            if (preview.needsDuplicateChoice) {
                Text(stringResource(R.string.ht_duplicates, preview.duplicates), style = MaterialTheme.typography.labelLarge)
                listOf(HrtTracker.Duplicates.MERGE to R.string.ht_dup_merge, HrtTracker.Duplicates.KEEP_ALL to R.string.ht_dup_keep).forEach { (d, label) ->
                    Row(Modifier.fillMaxWidth().selectable(duplicates == d) { duplicates = d }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(duplicates == d, { duplicates = d }); Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            preview.weightKg?.let { w -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(weight, { weight = it }); Text(stringResource(R.string.ht_weight, displayNumber(w)), style = MaterialTheme.typography.bodyMedium)
            } }
            if (preview.skipped.isNotEmpty()) {
                Text(stringResource(R.string.ht_skipped_title), style = MaterialTheme.typography.labelLarge)
                preview.skipped.forEach { (k, n) -> Text("• ${skipLabel(k)}: $n", style = MaterialTheme.typography.bodySmall) }
            }
            Text(stringResource(R.string.ht_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { Button(enabled = ready, onClick = {
            onImport(duplicates, preview.groups.keys.associateWith { targets[it]?.medication?.id }, preview.groups.keys.associateWith { names[it]?.trim().orEmpty() }, weight)
        }) { Text(stringResource(R.string.import_action)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } })
}
