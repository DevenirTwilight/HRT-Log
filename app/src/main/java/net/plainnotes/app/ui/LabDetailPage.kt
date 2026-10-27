package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.R
import net.plainnotes.app.data.LabContextEntity
import net.plainnotes.app.data.LabValueEntity
import java.time.Instant
import java.time.ZoneId

/**
 * UI-10: one lab result as a page, not a dialog. Top bar: back and the one secondary action (edit). Then the result itself
 * (name, value, unit, sample time), the report details that were actually entered, the frozen sample context, and the
 * record's related files. The files are supplied by the host through [files] and [overlay] (the file page) so the page stays
 * stateless and carries no attachment authority of its own. Nothing is interpreted: no in-range or out-of-range judgement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LabDetailPage(v: LabValueEntity, contexts: List<LabContextEntity>, onBack: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier,
                              files: (@Composable () -> Unit)? = null, overlay: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxSize()) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.lab_page_title), Modifier.semantics { heading() }) },
                navigationIcon = { IconButton(onBack, Modifier.testTag("lab-detail-back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.ui10_back)) } },
                actions = { TextButton(onEdit, Modifier.heightIn(min = 48.dp).testTag("lab-detail-edit")) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.edit)) } })
        }, containerColor = MaterialTheme.colorScheme.surface) { inner ->
            Column(Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
                .testTag("lab-detail"), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                LabHero(v)
                ReportDetails(v)
                files?.invoke()
                SampleContext(v, contexts)
            }
        }
        overlay?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun LabHero(v: LabValueEntity) {
    val c = MaterialTheme.colorScheme
    val zone = ZoneId.of(v.sampled_zone); val local = Instant.ofEpochMilli(v.sampled_utc).atZone(zone)
    val fs = LocalDensity.current.fontScale
    val valueStyle = MaterialTheme.typography.displaySmall.copy(fontSize = cappedSp(44f, fs), lineHeight = cappedSp(50f, fs), fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")
    val sampled = formatDate(local.toLocalDate()) + " · " + formatTime(local.toLocalTime()) +
        (if (zone.rules != ZoneId.systemDefault().rules) " · " + zone.getDisplayName(java.time.format.TextStyle.FULL, currentLocale()) else "")
    Surface(Modifier.fillMaxWidth(), shape = Corners.card, color = c.secondaryContainer, contentColor = c.onSecondaryContainer) {
        Column(Modifier.padding(20.dp).semantics(mergeDescendants = true) {}.testTag("lab-hero"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(40.dp), shape = Corners.full, color = c.surfaceContainerLowest, contentColor = c.primary) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Science, null, Modifier.size(22.dp)) }
                }
                Text(analyteLabel(v.analyte_code), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Bottom) {
                Text(labReportNumber(v.value), Modifier.alignByBaseline(), style = valueStyle)
                Text(v.unit, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                Column {
                    Text(stringResource(R.string.lab_page_sampled), style = MaterialTheme.typography.labelMedium)
                    Text(sampled, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Only what was entered. A missing field is left out; when everything is missing one sentence says how to add it. */
@Composable private fun ReportDetails(v: LabValueEntity) {
    val rows = buildList {
        val lo = v.reference_lower?.let { labReportNumber(it) }; val hi = v.reference_upper?.let { labReportNumber(it) }; val unit = v.reference_unit.orEmpty()
        if (lo != null || hi != null) add("lab-detail-range" to (stringResource(R.string.lab_page_range) to when {
            lo != null && hi != null -> stringResource(R.string.lab_page_range_both, lo, hi, unit)
            lo != null -> stringResource(R.string.lab_page_range_min, lo, unit)
            else -> stringResource(R.string.lab_page_range_max, hi!!, unit)
        }.trim()))
        v.laboratory?.takeIf { it.isNotBlank() }?.let { add("lab-detail-laboratory" to (stringResource(R.string.lab_page_laboratory) to it)) }
        v.note?.takeIf { it.isNotBlank() }?.let { add("lab-detail-note" to (stringResource(R.string.note) to it)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.lab_page_report), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        if (rows.isEmpty()) Text(stringResource(R.string.lab_page_nothing_more), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("lab-detail-nothing-more"))
        else Column(Modifier.testTag("lab-detail-report"), verticalArrangement = Arrangement.spacedBy(GroupedGap)) {
            rows.forEachIndexed { i, (tag, pair) ->
                GroupedItem(i, rows.size) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}.testTag(tag), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(pair.first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pair.second, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable private fun SampleContext(v: LabValueEntity, contexts: List<LabContextEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.lab_page_context), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Surface(Modifier.fillMaxWidth().testTag("lab-detail-context"), shape = Corners.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(top = 8.dp)) { LabContextSection(v, contexts, null) }
        }
    }
}

/** The page over the whole screen: its own window, so system BACK dismisses it and nothing is nested in a dialog. */
@Composable fun LabDetailFullscreen(v: LabValueEntity, contexts: List<LabContextEntity>, onBack: () -> Unit, onEdit: () -> Unit,
                                    files: (@Composable () -> Unit)? = null, overlay: (@Composable () -> Unit)? = null) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { LabDetailPage(v, contexts, onBack, onEdit, files = files, overlay = overlay) }
    }
}
