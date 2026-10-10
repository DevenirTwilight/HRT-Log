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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.R
import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.conc.experimental.SlExclusion
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.experimental.ConcentrationModelComparison
import net.plainnotes.app.pk.experimental.ExperimentalSlModelView
import net.plainnotes.app.pk.experimental.ModelMetrics
import net.plainnotes.app.pk.experimental.PriceFigure1997
import net.plainnotes.app.pk.experimental.ResearchSublingualV01
import net.plainnotes.app.pk.experimental.StudyAnchors
import net.plainnotes.app.pk.experimental.ExperimentalDose
import net.plainnotes.app.ui.DropdownField
import net.plainnotes.app.ui.SectionCard
import net.plainnotes.app.ui.currentLocale
import net.plainnotes.app.ui.formatDateTime
import java.time.Instant
import java.util.Locale

/** Which model the concentration page shows. LEGACY is the default and is the official, unmodified page. */
enum class ConcentrationModelMode(val label: Int) {
    LEGACY(R.string.xpk_mode_legacy), M2(R.string.xpk_mode_m2), COMPARISON(R.string.xpk_mode_comparison)
}

/** Unit of the M2 view: normalized relative response, or the Price 1997 literature scenario in pg/mL. */
enum class ComparisonScale { RELATIVE, STUDY }

/** Result of one read-only pass; [Empty] carries the exclusion audit for the empty state. */
sealed interface ComparisonUiState {
    data object Loading : ComparisonUiState
    class Empty(val audit: SlAdapterAudit) : ComparisonUiState
    class Ready(val snapshot: ComparisonSnapshot) : ComparisonUiState
    data object Failed : ComparisonUiState
}

fun comparisonUiState(records: List<RecordEntity>, ruleSnapshots: Map<Long, String>, now: Instant, window: ComparisonWindow, originIndex: Int?, candidateId: String): ComparisonUiState =
    runCatching {
        val audit = ExperimentalConcentrationComparison.audit(records, ruleSnapshots, now)
        ExperimentalConcentrationComparison.compute(audit, now, window, originIndex, candidateId)?.let { ComparisonUiState.Ready(it) } ?: ComparisonUiState.Empty(audit)
    }.getOrDefault(ComparisonUiState.Failed)

/**
 * Concentration page with model choice (REQUIREMENTS §57). [legacyContent] is the official ConcentrationScreen, rendered
 * unchanged with its own result object, so Legacy mode cannot regress. Without it (experimental drawer host), Legacy
 * mode shows the legacy model as a relative curve.
 */
@Composable fun UnifiedConcentrationScreen(
    records: List<RecordEntity>,
    ruleSnapshots: Map<Long, String>,
    pad: PaddingValues,
    legacyContent: (@Composable (PaddingValues) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    now: () -> Instant = Instant::now,
) {
    var mode by rememberSaveable { mutableStateOf(ConcentrationModelMode.LEGACY) }
    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        ModelSelector(mode) { mode = it }
        val inner = PaddingValues(bottom = pad.calculateBottomPadding())
        if (mode == ConcentrationModelMode.LEGACY && legacyContent != null) Box(Modifier.weight(1f).testTag("concentration-legacy")) { legacyContent(inner) }
        else ModelComparisonPanel(mode, records, ruleSnapshots, inner, onBack, now, Modifier.weight(1f))
    }
}

@Composable private fun ModelSelector(mode: ConcentrationModelMode, onMode: (ConcentrationModelMode) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ConcentrationModelMode.entries.forEach { m ->
            FilterChip(selected = mode == m, onClick = { onMode(m) }, modifier = Modifier.testTag("xpk-mode-${m.name}"),
                label = { Text(stringResource(m.label)) },
                leadingIcon = if (m != ConcentrationModelMode.LEGACY) ({ Icon(Icons.Outlined.Science, null, Modifier.size(16.dp)) }) else null)
        }
    }
}

private fun rel(locale: Locale, v: Double): String = when {
    v == 0.0 -> "0"
    v < 0.001 -> "<0.001"
    v < 10 -> String.format(locale, "%.3f", v)
    else -> String.format(locale, "%.1f", v)
}
private fun conc(locale: Locale, v: Double) = String.format(locale, "%.0f", v)
private fun hoursText(locale: Locale, v: Double) = String.format(locale, "%.2f", v)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ModelComparisonPanel(
    mode: ConcentrationModelMode, records: List<RecordEntity>, ruleSnapshots: Map<Long, String>, pad: PaddingValues,
    onBack: (() -> Unit)?, now: () -> Instant, modifier: Modifier = Modifier,
) {
    var window by rememberSaveable { mutableStateOf(ComparisonWindow.H24) }
    var originIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var candidateId by rememberSaveable { mutableStateOf(ExperimentalSlModelView.candidates.first().id) }
    var scale by rememberSaveable { mutableStateOf(ComparisonScale.RELATIVE) }
    var selectedHour by remember { mutableStateOf<Double?>(null) }
    var assumptions by rememberSaveable { mutableStateOf(false) }
    val state by produceState<ComparisonUiState>(ComparisonUiState.Loading, records, ruleSnapshots, window, originIndex, candidateId) {
        value = ComparisonUiState.Loading
        value = withContext(Dispatchers.Default) { comparisonUiState(records, ruleSnapshots, now(), window, originIndex, candidateId) }
    }
    LaunchedEffect(state) { selectedHour = null }
    val effectiveScale = if (mode == ConcentrationModelMode.M2) scale else ComparisonScale.RELATIVE
    val locale = currentLocale()
    val colors = MaterialTheme.colorScheme
    val legacyColor = colors.primary; val m2Color = colors.tertiary; val studyColor = Color(0xFFB4532A)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 16.dp).testTag("experimental-pk"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.WarningAmber, null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.xpk_experimental_badge), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.xpk_warning), style = MaterialTheme.typography.bodySmall)
                    if (effectiveScale == ComparisonScale.STUDY) Text(stringResource(R.string.xpk_study_banner), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.testTag("xpk-study-banner"))
                }
            }
        }
        if (mode == ConcentrationModelMode.LEGACY) Text(stringResource(R.string.xpk_legacy_relative_note), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.xpk_window_label), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ComparisonWindow.H4 to R.string.xpk_window_h4, ComparisonWindow.H8 to R.string.xpk_window_h8, ComparisonWindow.H24 to R.string.xpk_window_h24,
                ComparisonWindow.H48 to R.string.xpk_window_h48, ComparisonWindow.CALENDAR_7D to R.string.xpk_window_calendar).forEach { (w, label) ->
                FilterChip(selected = window == w, onClick = { window = w }, label = { Text(stringResource(label)) }, modifier = Modifier.testTag("xpk-window-${w.name}"))
            }
        }
        if (mode == ConcentrationModelMode.M2) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ComparisonScale.RELATIVE to R.string.xpk_scale_relative, ComparisonScale.STUDY to R.string.xpk_scale_study).forEach { (s, label) ->
                FilterChip(selected = scale == s, onClick = { scale = s }, label = { Text(stringResource(label)) }, modifier = Modifier.testTag("xpk-scale-${s.name}"))
            }
        }
        when (val s = state) {
            ComparisonUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(R.string.xpk_loading))
            }
            ComparisonUiState.Failed -> Text(stringResource(R.string.xpk_error), color = colors.error)
            is ComparisonUiState.Empty -> SectionCard(stringResource(R.string.xpk_empty_title)) {
                Text(stringResource(R.string.xpk_empty_body), style = MaterialTheme.typography.bodyMedium)
                Exclusions(s.audit)
            }
            is ComparisonUiState.Ready -> {
                val snap = s.snapshot; val r = snap.result
                val events = snap.audit.events
                DropdownField(stringResource(R.string.xpk_origin_label), events.indices.toList().reversed(), snap.originIndex,
                    { i -> stringResource(R.string.xpk_origin_item, formatDateTime(Instant.ofEpochMilli((events[i].timeH * 3_600_000).toLong())), rel(locale, events[i].doseMG)) },
                    { originIndex = it }, modifier = Modifier.testTag("xpk-origin"))
                val showLegacy = mode != ConcentrationModelMode.M2
                val showM2 = mode != ConcentrationModelMode.LEGACY
                val study = effectiveScale == ComparisonScale.STUDY
                Text(stringResource(if (study) R.string.xpk_unit_study else R.string.xpk_unit_relative), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("xpk-unit"))
                val series = buildList {
                    if (study) add(ComparisonSeries(snap.study.selected, studyColor))
                    else {
                        if (showLegacy) add(ComparisonSeries(r.legacy, legacyColor))
                        if (showM2) add(ComparisonSeries(r.m2, m2Color))
                    }
                }
                val description = stringResource(R.string.xpk_chart_description, series.size.toString())
                ModelComparisonChart(
                    ComparisonChartData(r.timeHours, series,
                        band = when { study -> snap.study.minimum to snap.study.maximum; showM2 -> r.m2Minimum to r.m2Maximum; else -> null },
                        bandColor = if (study) studyColor else m2Color, longTail = if (showM2) r.longTail else null, doses = r.doses,
                        nowHour = snap.nowHour, axisOrigin = if (snap.window.relative) r.originHour else null, description = description),
                    selectedHour, { selectedHour = it }, { if (study) conc(locale, it) else rel(locale, it) })
                Legend(showLegacy && !study, showM2 && !study, study, r.candidate.id, legacyColor, m2Color, studyColor, snap, locale)
                if (r.timeHours.last() > snap.nowHour) Text(stringResource(R.string.xpk_future_note), style = MaterialTheme.typography.bodySmall)
                Readout(snap, selectedHour, showLegacy, showM2, study, locale)
                Metrics(r.legacyMetrics.takeIf { showLegacy }, r.m2Metrics.takeIf { showM2 }, snap, selectedHour, locale)
                if (showM2) CandidateChips(candidateId) { candidateId = it }
                if (study) StudyReconstruction(snap, studyColor, locale)
                Coverage(snap, locale)
            }
        }
        TextButton(onClick = { assumptions = !assumptions }, modifier = Modifier.testTag("xpk-assumptions-toggle")) {
            Text(stringResource(if (assumptions) R.string.xpk_assumptions_hide else R.string.xpk_assumptions_show))
        }
        if (assumptions) SectionCard(stringResource(R.string.xpk_limits_title)) {
            Text(stringResource(R.string.xpk_unit_relative), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.xpk_metrics_definition), style = MaterialTheme.typography.bodySmall)
            listOf(R.string.xpk_limit_calibration, R.string.xpk_limit_identifiability, R.string.xpk_limit_tail, R.string.xpk_limit_comparison,
                R.string.xpk_limit_linearity, R.string.xpk_limit_price).forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("•", style = MaterialTheme.typography.bodySmall); Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            }
            Text(stringResource(R.string.xpk_version, ResearchSublingualV01.version), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        onBack?.let { back ->
            OutlinedButton(onClick = back, modifier = Modifier.testTag("xpk-back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.xpk_back))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun Swatch(color: Color, text: String, tag: String) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
    Canvas(Modifier.padding(top = 6.dp).size(16.dp, 4.dp)) { drawRect(color) }
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag(tag))
}

@Composable private fun Legend(legacy: Boolean, m2: Boolean, study: Boolean, candidateId: String, legacyColor: Color, m2Color: Color, studyColor: Color, snap: ComparisonSnapshot, locale: Locale) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (legacy) Swatch(legacyColor, stringResource(R.string.xpk_legend_legacy), "xpk-legend-legacy")
        if (m2) {
            Swatch(m2Color, stringResource(R.string.xpk_legend_m2, candidateId), "xpk-legend-m2")
            Swatch(m2Color.copy(alpha = 0.25f), stringResource(R.string.xpk_legend_range), "xpk-legend-range")
        }
        if (study) {
            Swatch(studyColor, stringResource(R.string.xpk_legend_study, candidateId, conc(locale, snap.study.background), conc(locale, snap.study.amplitude)), "xpk-legend-study")
            Swatch(studyColor.copy(alpha = 0.25f), stringResource(R.string.xpk_legend_study_range), "xpk-legend-study-range")
        }
        if (m2 || study) Swatch(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), stringResource(R.string.xpk_legend_tail), "xpk-legend-tail")
        Swatch(MaterialTheme.colorScheme.primary, stringResource(R.string.xpk_legend_doses), "xpk-legend-doses")
    }
}

@Composable private fun Readout(snap: ComparisonSnapshot, hour: Double?, legacy: Boolean, m2: Boolean, study: Boolean, locale: Locale) {
    val values by produceState<net.plainnotes.app.pk.experimental.ModelValuesAt?>(null, snap, hour) {
        value = hour?.let { h -> withContext(Dispatchers.Default) { runCatching { ExperimentalConcentrationComparison.valuesAt(snap, h) }.getOrNull() } }
    }
    SectionCard(null) {
        val v = values
        if (hour == null || v == null) { Text(stringResource(R.string.xpk_readout_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("xpk-readout-hint")); return@SectionCard }
        Column(Modifier.testTag("xpk-readout"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.xpk_readout_time, hoursText(locale, v.hour - snap.result.originHour), formatDateTime(Instant.ofEpochMilli((v.hour * 3_600_000).toLong()))),
                style = MaterialTheme.typography.labelLarge)
            if (study) {
                val a = snap.study.amplitude; val b = snap.study.background
                val range = StudyAnchors.scenarioRange(PriceFigure1997.STUDY_ID, doubleArrayOf(v.hour), snap.result.doses.map { (t, mg) -> ExperimentalDose(t, mg) })!!
                Text(stringResource(R.string.xpk_readout_study, conc(locale, b + a * v.m2), conc(locale, range.first[0]), conc(locale, range.second[0])), modifier = Modifier.testTag("xpk-readout-study"))
            } else {
                if (legacy) Text(stringResource(R.string.xpk_readout_legacy, rel(locale, v.legacy)), modifier = Modifier.testTag("xpk-readout-legacy"))
                if (m2) Text(stringResource(R.string.xpk_readout_m2, rel(locale, v.m2), rel(locale, v.m2Minimum), rel(locale, v.m2Maximum)), modifier = Modifier.testTag("xpk-readout-m2"))
                if (legacy && m2) ConcentrationModelComparison.ratio(v.m2, v.legacy)?.let { Text(stringResource(R.string.xpk_readout_ratio, rel(locale, it)), modifier = Modifier.testTag("xpk-readout-ratio")) }
            }
            if (v.longTail) Text(stringResource(R.string.xpk_readout_tail), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Metrics(legacy: ModelMetrics?, m2: ModelMetrics?, snap: ComparisonSnapshot, selectedHour: Double?, locale: Locale) {
    val selected by produceState<net.plainnotes.app.pk.experimental.ModelValuesAt?>(null, snap, selectedHour) {
        value = selectedHour?.let { h -> withContext(Dispatchers.Default) { runCatching { ExperimentalConcentrationComparison.valuesAt(snap, h) }.getOrNull() } }
    }
    data class Row4(val label: Int, val l: Double?, val m: Double?, val hours: Boolean = false, val tail: Boolean = false)
    val rows = listOf(
        Row4(R.string.xpk_metric_selected, selected?.legacy?.takeIf { legacy != null }, selected?.m2?.takeIf { m2 != null }),
        Row4(R.string.xpk_metric_peak, legacy?.peak, m2?.peak),
        Row4(R.string.xpk_metric_peak_time, legacy?.peakHoursAfterOrigin, m2?.peakHoursAfterOrigin, hours = true),
        Row4(R.string.xpk_metric_tmax, legacy?.singleDoseTmaxHours, m2?.singleDoseTmaxHours, hours = true),
        Row4(R.string.xpk_metric_auc4, legacy?.auc0to4, m2?.auc0to4), Row4(R.string.xpk_metric_auc8, legacy?.auc0to8, m2?.auc0to8),
        Row4(R.string.xpk_metric_auc24, legacy?.auc0to24, m2?.auc0to24, tail = true),
        Row4(R.string.xpk_metric_at8, legacy?.at8h, m2?.at8h), Row4(R.string.xpk_metric_at12, legacy?.at12h, m2?.at12h, tail = true),
        Row4(R.string.xpk_metric_at24, legacy?.at24h, m2?.at24h, tail = true),
    )
    val both = legacy != null && m2 != null
    SectionCard(stringResource(R.string.xpk_metrics_title)) {
        Column(Modifier.testTag("xpk-metrics"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(stringResource(R.string.xpk_metric_column), Modifier.weight(1.6f), style = MaterialTheme.typography.labelMedium)
                if (legacy != null) Text(stringResource(R.string.xpk_mode_legacy), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (m2 != null) Text(stringResource(R.string.xpk_mode_m2), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (both) Text(stringResource(R.string.xpk_metric_ratio), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
            rows.forEachIndexed { i, row ->
                fun cell(v: Double?) = when { v == null -> "—"; row.hours -> hoursText(locale, v); else -> rel(locale, v) }
                Row(Modifier.testTag("xpk-metric-$i")) {
                    Column(Modifier.weight(1.6f)) {
                        Text(stringResource(row.label), style = MaterialTheme.typography.bodySmall)
                        if (row.tail) Text(stringResource(R.string.xpk_metric_uncertain), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (legacy != null) Text(cell(row.l), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (m2 != null) Text(cell(row.m), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (both) Text(if (row.hours || row.l == null || row.m == null) "—" else ConcentrationModelComparison.ratio(row.m, row.l)?.let { rel(locale, it) } ?: "—",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.xpk_metrics_definition), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CandidateChips(selected: String, onSelect: (String) -> Unit) {
    val candidate = ExperimentalSlModelView.candidate(selected) ?: return
    val locale = currentLocale()
    SectionCard(stringResource(R.string.xpk_candidate_title)) {
        FlowRow(Modifier.testTag("xpk-candidate"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExperimentalSlModelView.candidates.forEach { c ->
                FilterChip(selected = c.id == selected, onClick = { onSelect(c.id) }, label = { Text(c.id) }, modifier = Modifier.testTag("xpk-candidate-${c.id}"))
            }
        }
        Text(stringResource(R.string.xpk_candidate_order), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.xpk_candidate_rates, candidate.transitStages.toString(), String.format(locale, "%.3g", candidate.fastRatePerHour),
            String.format(locale, "%.3g", candidate.eliminationRatePerHour), String.format(locale, "%.3g", candidate.slowRatePerHour),
            String.format(locale, "%.3g", candidate.slowWeight)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.xpk_candidate_price, conc(locale, candidate.assumedPriceBaselinePgMl)), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun StudyReconstruction(snap: ComparisonSnapshot, color: Color, locale: Locale) {
    val c = snap.result.candidate
    val anchor = PriceFigure1997.anchor(c)
    val grid = remember(c) { DoubleArray(24 * 60 + 1) { it / 60.0 } }
    val single = remember(c) { DoubleArray(grid.size) { anchor.background + anchor.oneMgIncrementAtOneHour * ResearchSublingualV01.relativeIncrement(grid[it], c) } }
    val range = remember { StudyAnchors.scenarioRange(PriceFigure1997.STUDY_ID, grid, listOf(ExperimentalDose(0.0, 1.0)))!! }
    var selected by remember { mutableStateOf<Double?>(null) }
    SectionCard(stringResource(R.string.xpk_study_title)) {
        Column(Modifier.testTag("xpk-study"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ModelComparisonChart(ComparisonChartData(grid, listOf(ComparisonSeries(single, color)), range, color,
                points = PriceFigure1997.hours.indices.map { Triple(PriceFigure1997.hours[it], PriceFigure1997.means[it], PriceFigure1997.readWidths[it]) },
                axisOrigin = 0.0, description = stringResource(R.string.xpk_study_title)), selected, { selected = it }, { conc(locale, it) },
                Modifier.fillMaxWidth().height(200.dp), tag = "xpk-study-chart")
            Text(stringResource(R.string.xpk_study_anchor, conc(locale, anchor.oneMgIncrementAtOneHour), conc(locale, anchor.background)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.xpk_study_points), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.xpk_study_auc_model, conc(locale, PriceFigure1997.incrementalAuc0to24(c))), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("xpk-study-auc-model"))
            Text(stringResource(R.string.xpk_study_auc_figure, String.format(locale, "%.1f", PriceFigure1997.figureAuc0to24AssumingZeroPredose())), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.xpk_study_auc_table, conc(locale, PriceFigure1997.TABLE1_AUC_0_24), conc(locale, PriceFigure1997.TABLE1_AUC_0_24_SD)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.xpk_study_conflict), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.xpk_study_cmax, conc(locale, single.max()), conc(locale, PriceFigure1997.TABLE1_CMAX), conc(locale, PriceFigure1997.TABLE1_CMAX_SD)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.xpk_study_refused_title), style = MaterialTheme.typography.labelMedium)
            listOf(R.string.xpk_study_refused_doll, R.string.xpk_study_refused_rosano, R.string.xpk_study_refused_komesaroff).forEach {
                Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun Coverage(snap: ComparisonSnapshot, locale: Locale) {
    val r = snap.result
    SectionCard(stringResource(R.string.xpk_coverage_title)) {
        Text(stringResource(R.string.xpk_coverage_doses, r.doses.size, rel(locale, r.doses.sumOf { it.second }),
            formatDateTime(Instant.ofEpochMilli((r.doses.first().first * 3_600_000).toLong())), formatDateTime(Instant.ofEpochMilli((r.doses.last().first * 3_600_000).toLong())),
            r.omittedOlderDoses), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("xpk-coverage"))
        if ((snap.audit.excluded[SlExclusion.NOT_SUBLINGUAL_E2] ?: 0) > 0) Text(stringResource(R.string.xpk_coverage_mixed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("xpk-coverage-mixed"))
        Exclusions(snap.audit)
    }
}

@Composable fun Exclusions(audit: SlAdapterAudit) {
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
