package net.plainnotes.app.ui

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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.plainnotes.app.R
import net.plainnotes.app.conc.formulations.TestosteroneHistory
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.formulations.TestosteroneFormulations as T

@Composable internal fun AnalyteSelector(testosterone: Boolean, select: (Boolean) -> Unit) {
    Box(Modifier.testTag("concentration-analyte")) {
        AdaptiveChoice(listOf(stringResource(R.string.choice_e2), stringResource(R.string.choice_t)), if (testosterone) 1 else 0, { select(it == 1) })
    }
}

private sealed interface TLoad {
    data object Loading : TLoad
    data object Failed : TLoad
    data class Ready(val audit: TestosteroneHistory.Audit, val shape: T.Shape?) : TLoad
}

@Composable internal fun TestosteroneConcentration(records: List<RecordEntity>, ruleSnapshots: Map<Long, String>,
    nowH: Double, loading: Boolean, failed: Boolean, padding: PaddingValues, onE2: () -> Unit, onHistory: () -> Unit) {
    var productCode by rememberSaveable { mutableStateOf(T.products.first().code) }
    var days by rememberSaveable { mutableIntStateOf(7) }
    var scenario by rememberSaveable { mutableIntStateOf(1) }
    var retry by remember { mutableIntStateOf(0) }
    val product = T.product(productCode)!!
    val start = nowH - days * 24.0
    val load = key(records, ruleSnapshots, nowH, productCode, days, retry, loading, failed) {
        produceState<TLoad>(TLoad.Loading) {
            if (loading) return@produceState
            value = if (failed) TLoad.Failed else try {
                withContext(Dispatchers.Default) {
                    val audit = TestosteroneHistory.read(records, ruleSnapshots, nowH)
                    // Display a bounded grid, but retain ALL saved history, including old depot doses.
                    val steps = (days * 24 / if (product.route == "GEL") 0.5 else 2.0).toInt()
                    val grid = DoubleArray(steps + 1) { start + days * 24.0 * it / steps }
                    val shape = T.shape(product, audit.events[productCode].orEmpty(), grid) { ensureActive() }
                    TLoad.Ready(audit, shape)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
              catch (_: Exception) { TLoad.Failed }
        }.value
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnalyteSelector(true) { if (!it) onE2() }
        SectionCard(stringResource(R.string.t_formulation)) {
            DropdownField(stringResource(R.string.t_formulation), T.products, product, { it.name }, { productCode = it.code })
            Text(stringResource(R.string.t_shape_warning), modifier = Modifier.testTag("t-shape-warning"))
            Text(stringResource(R.string.t_actual_history), style = MaterialTheme.typography.bodySmall)
        }
        when (load) {
            TLoad.Loading -> CircularProgressIndicator(Modifier.testTag("t-loading"))
            TLoad.Failed -> SectionCard(null) {
                Text(stringResource(R.string.feedback_calculation_failed), modifier = Modifier.testTag("t-failed"))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.attach_retry)) }
            }
            is TLoad.Ready -> {
                val ready = load
                if (product.status == T.Status.RECORD_ONLY) SectionCard(null) {
                    Text(stringResource(R.string.t_record_only), modifier = Modifier.testTag("t-record-only"))
                } else if (ready.shape == null) SectionCard(null) {
                    Text(stringResource(R.string.t_no_qualified), modifier = Modifier.testTag("t-no-history"))
                } else SectionCard(stringResource(R.string.t_relative_title)) {
                    AdaptiveChoice(listOf(7, 28, 180).map { stringResource(R.string.days_short, it) }, listOf(7, 28, 180).indexOf(days), { days = listOf(7, 28, 180)[it] })
                    Text(stringResource(R.string.t_sensitivity))
                    AdaptiveChoice(listOf("A", "B", "C"), scenario, { scenario = it })
                    val data = remember(ready, scenario) { ChartData(ready.shape.timeH, ready.shape.variants[scenario],
                        splitX = nowH, unit = "", marks = ready.audit.events[productCode].orEmpty().map { it.timeH }.toDoubleArray()) }
                    ConcChart(data, start, nowH, Modifier.fillMaxWidth().height(260.dp).testTag("t-relative-chart")) { java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(it) }
                    Text(stringResource(R.string.t_normalized), style = MaterialTheme.typography.bodySmall)
                }
                SectionCard(null) {
                    Text(stringResource(R.string.t_history_counts, ready.audit.events[productCode].orEmpty().size, ready.audit.unsupported, ready.audit.missing))
                    TextButton(onClick = onHistory) { Text(stringResource(R.string.history)) }
                }
            }
        }
        SectionCard(stringResource(R.string.t_evidence)) {
            Text(product.version + " · " + product.ester + " · " + product.route)
            Text(product.source, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.t_evidence_limits), style = MaterialTheme.typography.bodySmall)
        }
    }
}
