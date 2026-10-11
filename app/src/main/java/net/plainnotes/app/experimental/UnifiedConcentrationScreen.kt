package net.plainnotes.app.experimental

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.experimental.*
import net.plainnotes.app.ui.DropdownField
import net.plainnotes.app.ui.SectionCard
import net.plainnotes.app.ui.currentLocale
import net.plainnotes.app.ui.formatDateTime
import java.time.Instant

/** Single concentration destination. Selection changes display only, never saved records or legacy preferences. */
@Composable fun UnifiedConcentrationScreen(records: List<RecordEntity>, ruleSnapshots: Map<Long, String>, labs: List<LabValueEntity>,
    pad: PaddingValues, legacyContent: @Composable (PaddingValues) -> Unit, now: () -> Instant = Instant::now) {
    var model by rememberSaveable { mutableStateOf(ResearchModel.M2) }
    var overlay by rememberSaveable { mutableStateOf(false) }
    var historical by rememberSaveable { mutableStateOf(false) }
    var display by rememberSaveable { mutableStateOf(ResearchDisplay.CONDITIONAL) }
    var pmol by rememberSaveable { mutableStateOf(false) }
    var window by rememberSaveable { mutableStateOf(ComparisonWindow.H24) }
    var single by rememberSaveable { mutableStateOf(false) }
    var originIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var m2Id by rememberSaveable { mutableStateOf(ResearchSublingualV01.candidates.first().id) }
    var m1Id by rememberSaveable { mutableStateOf(ResearchM1Catalog.candidates.first().id) }
    var background by rememberSaveable { mutableStateOf("25") }
    var amplitude by rememberSaveable { mutableStateOf("100") }
    var hour by rememberSaveable { mutableStateOf<Double?>(null) }
    val calibration = remember(background, amplitude) { runCatching { CommonCalibration(background.toDouble(), amplitude.toDouble()) }.getOrNull() }
    val audit by produceState<net.plainnotes.app.conc.experimental.SlAdapterAudit?>(null, records, ruleSnapshots) {
        value = withContext(Dispatchers.Default) { ExperimentalConcentrationComparison.audit(records, ruleSnapshots, now()) }
    }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val snapshot by produceState<ComparisonSnapshot?>(null, audit, window, originIndex, m2Id, m1Id, display, calibration, single, historical) {
        if (historical || (display == ResearchDisplay.CONDITIONAL && calibration == null)) return@produceState
        val input = audit ?: return@produceState
        loading = true; failed = false; value = null
        val computed = withContext(Dispatchers.Default) { runCatching {
            ExperimentalConcentrationComparison.compute(input, now(), window, originIndex, m2Id, m1Id, display, calibration ?: CommonCalibration(25.0, 100.0), single)
        } }
        failed = computed.isFailure
        value = computed.getOrNull()
        loading = false
    }
    val locale = currentLocale()
    fun fmt(v: Double) = String.format(locale, "%.3f", v)
    val colors = MaterialTheme.colorScheme
    val modelColors = mapOf(ResearchModel.M2 to colors.primary, ResearchModel.M1 to colors.tertiary, ResearchModel.LEGACY to colors.secondary)
    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResearchModel.entries.forEach { m -> FilterChip(model == m, { model = m; historical = m == ResearchModel.LEGACY && !overlay },
                label = { Text(m.name) }, modifier = Modifier.testTag("model-${m.name}")) }
            FilterChip(overlay, { overlay = !overlay; historical = false }, label = { Text(stringResource(R.string.uc_overlay)) }, modifier = Modifier.testTag("model-overlay"))
        }
        if (historical) {
            Text(stringResource(R.string.uc_legacy_note), Modifier.padding(horizontal = 16.dp).testTag("legacy-unverified"), style = MaterialTheme.typography.bodySmall)
            TextButton({ historical = false }) { Text(stringResource(R.string.uc_common_comparison)) }
            Box(Modifier.weight(1f).testTag("concentration-legacy")) { legacyContent(PaddingValues(bottom = pad.calculateBottomPadding())) }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(bottom = pad.calculateBottomPadding()).testTag("unified-concentration"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.uc_research_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("research-not-personal"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ResearchDisplay.CONDITIONAL to R.string.uc_conditional, ResearchDisplay.STUDY to R.string.uc_study, ResearchDisplay.RELATIVE to R.string.uc_relative).forEach { (d, label) ->
                    FilterChip(display == d, { display = d; hour = null }, label = { Text(stringResource(label)) }, modifier = Modifier.testTag("display-${d.name}"))
                }
            }
            if (display == ResearchDisplay.CONDITIONAL) {
                Text(stringResource(R.string.uc_assumptions), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("calibration-source"))
                OutlinedTextField(background, { background = it }, label = { Text(stringResource(R.string.uc_background)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("background"))
                OutlinedTextField(amplitude, { amplitude = it }, label = { Text(stringResource(R.string.uc_amplitude)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("amplitude"))
                if (calibration == null) Text(stringResource(R.string.uc_invalid), color = colors.error, modifier = Modifier.testTag("invalid-calibration"))
            }
            if (display == ResearchDisplay.RELATIVE) Text(stringResource(R.string.uc_relative_protocol), style = MaterialTheme.typography.bodySmall)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!pmol, { pmol = false }, label = { Text("pg/mL") }, modifier = Modifier.testTag("unit-pg"))
                FilterChip(pmol, { pmol = true }, label = { Text("pmol/L") }, modifier = Modifier.testTag("unit-pmol"))
            }
            DropdownField(stringResource(R.string.uc_m2_candidate), ResearchSublingualV01.candidates.map { it.id }, m2Id, { it }, { m2Id = it; hour = null }, modifier = Modifier.testTag("m2-candidate"))
            DropdownField(stringResource(R.string.uc_m1_candidate), ResearchM1Catalog.candidates.map { it.id }, m1Id, { it }, { m1Id = it; hour = null }, modifier = Modifier.testTag("m1-candidate"))
            Text(stringResource(R.string.uc_version, ResearchSublingualV01.version, m2Id, ResearchM1Catalog.version, m1Id), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("model-versions"))
            Text(stringResource(R.string.uc_selection), style = MaterialTheme.typography.bodySmall)
            val selectedM2 = ResearchSublingualV01.candidates.single { it.id == m2Id }
            val selectedM1 = ResearchM1Catalog.candidates.single { it.id == m1Id }
            listOf("M2" to selectedM2, "M1" to selectedM1).forEach { (name, c) ->
                Text("$name: n=${c.transitStages}; kf=${fmt(c.fastRatePerHour)} /h; ke=${fmt(c.eliminationRatePerHour)} /h; ks=${fmt(c.slowRatePerHour)} /h; w=${fmt(c.slowWeight)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComparisonWindow.entries.forEach { w -> FilterChip(window == w, { window = w; hour = null }, label = { Text(if (w.relative) "0–${w.hours.toInt()} h" else stringResource(R.string.uc_calendar)) }, modifier = Modifier.testTag("window-${w.name}")) }
            }
            if (display != ResearchDisplay.STUDY && audit?.events?.isNotEmpty() == true) {
                DropdownField(stringResource(R.string.uc_origin), audit!!.events.indices.toList().reversed(), (originIndex ?: audit!!.events.lastIndex).coerceIn(0, audit!!.events.lastIndex),
                    { i -> formatDateTime(Instant.ofEpochMilli((audit!!.events[i].timeH * 3_600_000).toLong())) }, { originIndex = it; hour = null })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(single, { single = true }, label = { Text(stringResource(R.string.uc_single)) })
                    FilterChip(!single, { single = false }, label = { Text(stringResource(R.string.uc_history)) })
                }
            }
            if (loading) CircularProgressIndicator()
            if (failed) Text(stringResource(R.string.uc_error), color = colors.error)
            val snap = snapshot.takeIf { it?.result?.display == display && !loading && !failed && (display != ResearchDisplay.CONDITIONAL || calibration != null) }
            if (snap == null && !loading && !failed) Text(stringResource(R.string.uc_empty))
            snap?.let { s ->
                val r = s.result; val factor = r.factor(pmol)
                val unit = if (display == ResearchDisplay.RELATIVE) stringResource(R.string.uc_relative_unit) else if (pmol) "pmol/L" else "pg/mL"
                val active = if (overlay) ResearchModel.entries.toList() else listOf(model)
                if (display != ResearchDisplay.RELATIVE) Text(stringResource(R.string.uc_calibration, fmt(r.calibration.backgroundPgMl), fmt(r.calibration.amplitudePgMl)), modifier = Modifier.testTag("common-calibration"))
                if (display == ResearchDisplay.STUDY) {
                    Text(stringResource(R.string.uc_price_protocol), modifier = Modifier.testTag("study-not-personal"))
                    Text(stringResource(R.string.uc_price_conflict, fmt(PriceFigure1997.figureAuc0to24AssumingZeroPredose()), fmt(PriceFigure1997.TABLE1_AUC_0_24)), style = MaterialTheme.typography.bodySmall)
                }
                Text(unit, modifier = Modifier.testTag("chart-unit"))
                val points = if (display == ResearchDisplay.STUDY) PriceFigure1997.hours.indices.map { Triple(PriceFigure1997.hours[it], PriceFigure1997.means[it] * factor, PriceFigure1997.readWidths[it] * factor) }
                    else researchObservationPoints(labs, display, pmol)
                ModelComparisonChart(ComparisonChartData(r.base.timeHours,
                    active.map { ComparisonSeries(r.series.getValue(it).map { y -> y * factor }.toDoubleArray(), modelColors.getValue(it)) },
                    band = if (ResearchModel.M2 in active) r.m2Range.first.map { it * factor }.toDoubleArray() to r.m2Range.second.map { it * factor }.toDoubleArray() else null,
                    bandColor = modelColors.getValue(ResearchModel.M2), longTail = r.base.longTail, doses = r.base.doses, points = points,
                    axisOrigin = if (window.relative || display == ResearchDisplay.STUDY) r.base.originHour else null,
                    description = stringResource(R.string.uc_chart_description, active.joinToString { it.name }, unit)), hour, { hour = it }, ::fmt, tag = "unified-chart")
                Text(active.joinToString(" · ") { it.name }, modifier = Modifier.testTag("curve-legend"))
                Text(stringResource(if (display == ResearchDisplay.STUDY) R.string.uc_study_points else R.string.uc_lab_points), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.uc_range_tail), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("tail-warning"))
                val tapped by produceState<Map<ResearchModel, Double>?>(null, r, hour) {
                    value = hour?.let { h -> withContext(Dispatchers.Default) { runCatching { UnifiedModelComparison.valuesAt(r, h) }.getOrNull() } }
                }
                val readout = tapped
                SectionCard(stringResource(R.string.uc_readout)) {
                    if (readout == null) Text(stringResource(R.string.uc_tap))
                    else {
                        Text("${fmt(hour!! - r.base.originHour)} h", modifier = Modifier.testTag("readout-time"))
                        active.forEach { m -> Text("${m.name}: ${fmt(readout.getValue(m) * factor)} $unit", modifier = Modifier.testTag("readout-${m.name}")) }
                        if (overlay) {
                            Text("M2 − M1: ${fmt((readout.getValue(ResearchModel.M2) - readout.getValue(ResearchModel.M1)) * factor)} $unit", modifier = Modifier.testTag("difference-M1"))
                            Text("M2 − Legacy: ${fmt((readout.getValue(ResearchModel.M2) - readout.getValue(ResearchModel.LEGACY)) * factor)} $unit", modifier = Modifier.testTag("difference-LEGACY"))
                        }
                    }
                }
                Text(stringResource(R.string.uc_metrics_note), style = MaterialTheme.typography.bodySmall)
                active.forEach { m -> val metrics = r.metrics.getValue(m)
                    SectionCard(m.name) {
                        Text(stringResource(R.string.uc_peak, fmt(metrics.peak * factor), unit, fmt(metrics.peakHoursAfterOrigin)), modifier = Modifier.testTag("metrics-${m.name}"))
                        Text(stringResource(R.string.uc_auc, fmt(metrics.auc0to4 * factor), fmt(metrics.auc0to8 * factor), fmt(metrics.auc0to24 * factor), "$unit·h"))
                        Text("8/12/24 h: ${fmt(metrics.at8h * factor)} / ${fmt(metrics.at12h * factor)} / ${fmt(metrics.at24h * factor)} $unit")
                    }
                }
                Text(stringResource(R.string.uc_coverage, r.base.doses.size, r.base.omittedOlderDoses, s.audit.excluded.values.sum()), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
