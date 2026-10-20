package net.plainnotes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.LabContextEntity
import net.plainnotes.app.data.LabPanelEntity
import net.plainnotes.app.domain.LabCatalog
import net.plainnotes.app.domain.LabCategory
import net.plainnotes.app.data.LabValueEntity
import java.time.Instant

/** One visual contract for formal panels, display-only legacy groups and filtered history. No writes or regrouping. */
@Composable internal fun LabHomeCard(
    draw: LabDraw,
    onOpen: (LabValueEntity) -> Unit,
    onEdit: (LabValueEntity?) -> Unit,
    onDelete: (LabValueEntity) -> Unit,
    contexts: List<LabContextEntity>,
    onRebuild: ((LabValueEntity, Boolean) -> Unit)?,
    onPanel: (() -> Unit)?,
    onSaveAsPanel: (() -> Unit)?,
) {
    val first = draw.labs.first()
    val panel = draw.panel
    var expanded by rememberSaveable(panel?.uuid, first.id) { mutableStateOf(draw.labs.size == 1) }
    val place = (panel?.laboratory ?: first.laboratory)?.trim()?.takeIf { it.isNotEmpty() }
    val title = listOfNotNull(formatDateTime(Instant.ofEpochMilli(panel?.sampled_utc ?: first.sampled_utc)), place,
        stringResource(R.string.lab_group_count, draw.labs.size)).joinToString(" · ")
    val tag = panel?.let { "lab-panel:${it.id}" } ?: "lab-group:${first.sampled_utc}:${first.laboratory?.trim().orEmpty()}"
    ElevatedCard(Modifier.fillMaxWidth().testTag(tag), shape = CardDefaults.elevatedShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (panel != null) R.string.lab_home_formal else R.string.lab_home_display_only),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val header = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            Row(if (onPanel != null) header.clickable(role = Role.Button, onClickLabel = stringResource(R.string.lab_panel_open), onClick = onPanel).testTag("lab-panel-open") else header,
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = sectionTitleStyle, modifier = Modifier.weight(1f).semantics { heading() }.testTag("lab-group-title"))
                if (onPanel != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
            panel?.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("lab-panel-note"))
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = Corners.list) {
                Column {
                    val shown = if (expanded) draw.labs else draw.labs.take(3)
                    shown.forEachIndexed { index, result ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column {
                            LabGroupRow(result) { onOpen(result) }
                            if (expanded) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(labHomeRangeStatus(result)), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).testTag("lab-home-status:${result.id}"))
                                    var menu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("lab-home-more:${result.id}")) {
                                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.lab_home_more, analyteLabel(result.analyte_code)))
                                        }
                                        DropdownMenu(menu, { menu = false }) {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit(result) })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { menu = false; onDelete(result) })
                                        }
                                    }
                                }
                                result.note?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }
                                LabContextSection(result, contexts.filter { it.lab_id == result.id }, onRebuild)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lab-home-expand:${first.id}")) {
                Text(stringResource(if (expanded) R.string.lab_home_collapse else R.string.lab_home_expand, draw.labs.size))
            }
            if (onSaveAsPanel != null) OutlinedButton(onClick = onSaveAsPanel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lab-save-as-panel")) {
                Text(stringResource(R.string.lab_panel_save_as))
            }
        }
    }
}

/** Wording describes only the entered report range. Unknown conversion is never presented as in range. */
internal fun labHomeRangeStatus(v: LabValueEntity): Int {
    if (v.reference_lower == null && v.reference_upper == null) return R.string.lab_home_range_missing
    val ru = v.reference_unit ?: return R.string.lab_home_range_unknown
    if (ru != v.unit && (toCanonical(analyte(v.analyte_code), v.value, v.unit) == null ||
        listOfNotNull(v.reference_lower, v.reference_upper).any { toCanonical(analyte(v.analyte_code), it, ru) == null })) return R.string.lab_home_range_unknown
    return when (labFlag(v)) {
        LabFlag.HIGH -> R.string.lab_home_range_high
        LabFlag.LOW -> R.string.lab_home_range_low
        null -> R.string.lab_home_range_within
    }
}

/** UI taxonomy only; codes remain the repository's unchanged identities. The catalogue can add entries independently. */
internal enum class LabHomeCategory(val label: Int) {
    HORMONES(R.string.lab_home_category_hormones), CBC(R.string.lab_home_category_cbc),
    LIVER(R.string.lab_home_category_liver), KIDNEY(R.string.lab_home_category_kidney),
    LIPIDS(R.string.lab_home_category_lipids), METABOLISM(R.string.lab_home_category_metabolism), OTHER(R.string.lab_home_category_other)
}
internal fun labHomeCategory(code: String): LabHomeCategory = when (LabCatalog.categoryFor(code)) {
    LabCategory.HORMONES -> LabHomeCategory.HORMONES
    LabCategory.CBC -> LabHomeCategory.CBC
    LabCategory.LIVER -> LabHomeCategory.LIVER
    LabCategory.KIDNEY -> LabHomeCategory.KIDNEY
    LabCategory.LIPIDS -> LabHomeCategory.LIPIDS
    LabCategory.GLUCOSE_METABOLISM -> LabHomeCategory.METABOLISM
    LabCategory.OTHER -> LabHomeCategory.OTHER
}

/** Preserve sub-milli recorded results that three-decimal display would round to zero or distort.
 * This changes presentation only; the stored Double does not recover the report's original precision.
 */
@Composable internal fun labReportNumber(value: Double): String {
    val regular = displayNumber(value)
    return if (value.isFinite() && value != 0.0 &&
        kotlin.math.abs(value) < 0.001) {
        value.toString().replace('.', java.text.DecimalFormatSymbols.getInstance(currentLocale()).decimalSeparator)
    } else regular
}
