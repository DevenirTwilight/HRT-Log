package net.plainnotes.app.experimental

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.experimental.ResearchHistoricalSlAdapter
import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.conc.experimental.SlExclusion
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.experimental.ExperimentalSlModelView
import net.plainnotes.app.pk.experimental.ExperimentalSlView
import net.plainnotes.app.pk.experimental.ResearchSublingualV01
import net.plainnotes.app.ui.SectionCard
import java.time.Instant
import java.util.Locale

/** What the opt-in page shows; LEGACY is the default so nothing new is drawn until the user switches. */
enum class ExperimentalPkMode(val label: Int) {
    LEGACY(R.string.xpk_mode_legacy), M2(R.string.xpk_mode_m2), COMPARISON(R.string.xpk_mode_comparison)
}

/** Result of one read-only pass over the in-memory records. */
sealed interface ExperimentalPkState {
    data object Loading : ExperimentalPkState
    class Empty(val audit: SlAdapterAudit) : ExperimentalPkState
    class Ready(val view: ExperimentalSlView, val audit: SlAdapterAudit) : ExperimentalPkState
    data object Failed : ExperimentalPkState
}

/** Pure computation for the page: qualified sublingual E2 history -> dimensionless view. Writes nothing. */
fun experimentalPkState(records: List<RecordEntity>, ruleSnapshots: Map<Long, String>, now: Instant, candidateId: String): ExperimentalPkState =
    runCatching {
        val asOf = ConcentrationCalculator.hours(now)
        val audit = ResearchHistoricalSlAdapter.audit(records, ruleSnapshots, asOf)
        ExperimentalSlModelView.compute(audit.events, asOf, candidateId)?.let { ExperimentalPkState.Ready(it, audit) } ?: ExperimentalPkState.Empty(audit)
    }.getOrDefault(ExperimentalPkState.Failed)

/**
 * Opt-in research page (REQUIREMENTS §55). Reads records and immutable rule snapshots already in memory,
 * never the database, and never changes the official concentration page or its pg/mL estimate.
 */
@Composable fun ExperimentalPkScreen(
    records: List<RecordEntity>,
    ruleSnapshots: Map<Long, String>,
    pad: PaddingValues,
    onBackToConcentration: (() -> Unit)? = null,
    now: () -> Instant = Instant::now,
) {
    var mode by rememberSaveable { mutableStateOf(ExperimentalPkMode.LEGACY) }
    var candidateId by rememberSaveable { mutableStateOf(ExperimentalSlModelView.candidates.first().id) }
    val state by produceState<ExperimentalPkState>(ExperimentalPkState.Loading, records, ruleSnapshots, candidateId) {
        value = ExperimentalPkState.Loading
        value = withContext(Dispatchers.Default) { experimentalPkState(records, ruleSnapshots, now(), candidateId) }
    }
    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).testTag("experimental-pk"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WarningAmber, null)
                Text(stringResource(R.string.xpk_warning), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExperimentalPkMode.entries.forEach { m ->
                FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(stringResource(m.label)) }, modifier = Modifier.testTag("xpk-mode-${m.name}"))
            }
        }
        when (val s = state) {
            ExperimentalPkState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(R.string.xpk_loading))
            }
            ExperimentalPkState.Failed -> Text(stringResource(R.string.xpk_error), color = MaterialTheme.colorScheme.error)
            is ExperimentalPkState.Empty -> {
                SectionCard(stringResource(R.string.xpk_empty_title)) {
                    Icon(Icons.Outlined.Science, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.xpk_empty_body), style = MaterialTheme.typography.bodyMedium)
                    Exclusions(s.audit)
                }
            }
            is ExperimentalPkState.Ready -> {
                SectionCard(stringResource(R.string.xpk_unit)) {
                    RelativeChart(s.view, mode)
                    Legend(s.view, mode)
                    Text(stringResource(R.string.xpk_scale_note), style = MaterialTheme.typography.bodySmall)
                }
                if (mode != ExperimentalPkMode.LEGACY) CandidateCard(candidateId) { candidateId = it }
                SectionCard(stringResource(R.string.xpk_inputs_title)) {
                    Text(stringResource(R.string.xpk_inputs_window), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.xpk_inputs_used, s.view.consideredDoses, s.view.omittedOlderDoses), style = MaterialTheme.typography.bodySmall)
                    Exclusions(s.audit)
                }
            }
        }
        SectionCard(stringResource(R.string.xpk_limits_title)) {
            listOf(R.string.xpk_limit_calibration, R.string.xpk_limit_identifiability, R.string.xpk_limit_tail, R.string.xpk_limit_comparison,
                R.string.xpk_limit_linearity, R.string.xpk_limit_price).forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("•", style = MaterialTheme.typography.bodySmall); Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            }
            Text(stringResource(R.string.xpk_version, ResearchSublingualV01.version), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onBackToConcentration?.let { back ->
            OutlinedButton(onClick = back, modifier = Modifier.testTag("xpk-back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.xpk_back))
            }
        }
    }
}

@Composable private fun Exclusions(audit: SlAdapterAudit) {
    val names = mapOf(
        SlExclusion.NOT_TAKEN to R.string.xpk_excl_not_taken, SlExclusion.DELETED to R.string.xpk_excl_deleted,
        SlExclusion.NO_TIME to R.string.xpk_excl_no_time, SlExclusion.FUTURE to R.string.xpk_excl_future,
        SlExclusion.OUTSIDE_WINDOW to R.string.xpk_excl_outside_window, SlExclusion.INVALID_DOSE to R.string.xpk_excl_invalid_dose,
        SlExclusion.NO_SNAPSHOT to R.string.xpk_excl_no_snapshot, SlExclusion.NOT_SUBLINGUAL_E2 to R.string.xpk_excl_not_sl_e2,
        SlExclusion.INCOMPLETE_CONTEXT to R.string.xpk_excl_incomplete,
    )
    val parts = audit.excluded.map { (reason, count) -> stringResource(names.getValue(reason), count) }
    Text(if (parts.isEmpty()) stringResource(R.string.xpk_inputs_none_excluded) else stringResource(R.string.xpk_inputs_excluded, parts.joinToString(" · ")),
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("xpk-exclusions"))
}

private fun number(v: Double) = String.format(Locale.getDefault(), "%.3g", v)

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CandidateCard(selected: String, onSelect: (String) -> Unit) {
    val candidate = ExperimentalSlModelView.candidate(selected) ?: return
    SectionCard(stringResource(R.string.xpk_candidate_title)) {
        Text(stringResource(R.string.xpk_candidate_label), style = MaterialTheme.typography.labelMedium)
        FlowRow(Modifier.testTag("xpk-candidate"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExperimentalSlModelView.candidates.forEach { c ->
                FilterChip(selected = c.id == selected, onClick = { onSelect(c.id) }, label = { Text(c.id) }, modifier = Modifier.testTag("xpk-candidate-${c.id}"))
            }
        }
        Text(stringResource(R.string.xpk_candidate_order), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.xpk_candidate_rates, candidate.transitStages.toString(), number(candidate.fastRatePerHour),
            number(candidate.eliminationRatePerHour), number(candidate.slowRatePerHour), number(candidate.slowWeight)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.xpk_candidate_price, number(candidate.assumedPriceBaselinePgMl)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.xpk_candidate_rosano, number(candidate.rosanoBaselineCapPmolL)), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun Legend(view: ExperimentalSlView, mode: ExperimentalPkMode) {
    val colors = MaterialTheme.colorScheme
    @Composable fun item(color: Color, text: String, tag: String) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Canvas(Modifier.padding(top = 6.dp).size(16.dp, 4.dp)) { drawRect(color) }
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag(tag))
    }
    if (mode != ExperimentalPkMode.M2) item(colors.primary, stringResource(R.string.xpk_legend_legacy), "xpk-legend-legacy")
    if (mode != ExperimentalPkMode.LEGACY) {
        item(colors.tertiary, stringResource(R.string.xpk_legend_m2, view.candidate.id), "xpk-legend-m2")
        item(colors.tertiary.copy(alpha = 0.25f), stringResource(R.string.xpk_legend_range), "xpk-legend-range")
        item(colors.onSurface.copy(alpha = 0.08f), stringResource(R.string.xpk_legend_tail), "xpk-legend-tail")
    }
}

@Composable private fun RelativeChart(view: ExperimentalSlView, mode: ExperimentalPkMode) {
    val colors = MaterialTheme.colorScheme
    val showLegacy = mode != ExperimentalPkMode.M2
    val showM2 = mode != ExperimentalPkMode.LEGACY
    val top = listOfNotNull(view.legacyRelative.takeIf { showLegacy }?.max(), view.m2Maximum.takeIf { showM2 }?.max()).max().takeIf { it > 0.0 } ?: 1.0
    val summary = buildList {
        if (showLegacy) add(stringResource(R.string.xpk_mode_legacy) + " " + number(view.legacyRelative.last()))
        if (showM2) add(stringResource(R.string.xpk_mode_m2) + " " + number(view.m2Relative.last()))
    }.joinToString(", ")
    val description = stringResource(R.string.xpk_chart_description, summary)
    Text(stringResource(R.string.xpk_axis_max, number(top * 1.1)), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(200.dp).testTag("xpk-chart").semantics { contentDescription = description }) {
        val t = view.timeHours
        val x0 = t.first(); val span = (t.last() - x0).takeIf { it > 0 } ?: 1.0
        fun x(h: Double) = ((h - x0) / span * size.width).toFloat()
        fun y(v: Double) = (size.height - v / (top * 1.1) * size.height).toFloat()
        if (showM2) {
            var i = 0
            while (i < t.size) {
                if (!view.longTail[i]) { i++; continue }
                val start = i; while (i < t.size && view.longTail[i]) i++
                val left = x(t[start])
                drawRect(colors.onSurface.copy(alpha = 0.08f), Offset(left, 0f), Size(x(t[i - 1]) - left, size.height))
            }
            val band = Path().apply {
                moveTo(x(t[0]), y(view.m2Maximum[0])); for (k in t.indices) lineTo(x(t[k]), y(view.m2Maximum[k]))
                for (k in t.indices.reversed()) lineTo(x(t[k]), y(view.m2Minimum[k])); close()
            }
            drawPath(band, colors.tertiary.copy(alpha = 0.25f))
        }
        drawLine(colors.outline, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        fun line(values: DoubleArray, color: Color) {
            val p = Path().apply { moveTo(x(t[0]), y(values[0])); for (k in 1 until t.size) lineTo(x(t[k]), y(values[k])) }
            drawPath(p, color, style = Stroke(width = 2.5.dp.toPx()))
        }
        if (showLegacy) line(view.legacyRelative, colors.primary)
        if (showM2) line(view.m2Relative, colors.tertiary)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(R.string.xpk_axis_start, R.string.xpk_axis_middle, R.string.xpk_axis_now).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}
