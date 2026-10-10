package net.plainnotes.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.pk.experimental.ResearchShapeComparison
import net.plainnotes.app.pk.experimental.ResearchShapeComparisonV01

/**
 * Explicitly opt-in, ephemeral comparison. It cannot replace or calibrate production E2.
 * Both displayed traces are separately normalized shapes, NOT pg/mL.
 */
@Composable
internal fun ExperimentalSublingualComparison(result: ConcentrationResult) {
    if (result.researchSublingualHistory.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var baselineIndex by remember { mutableIntStateOf(2) } // arbitrary initial SCENARIO, not a best fit
    val baselines = ResearchShapeComparisonV01.assumedPriceBaselines
    val resultState by produceState<Pair<Boolean, ResearchShapeComparison?>>(
        initialValue = false to null, expanded, baselineIndex, result
    ) {
        value = false to null
        if (expanded) {
            val computed = withContext(Dispatchers.Default) {
                // Do not propagate sensitive event history through logs or network.
                runCatching {
                    ResearchShapeComparisonV01.compare(
                        result.researchSublingualHistory, result.nowH, baselines[baselineIndex]
                    )
                }.getOrNull()
            }
            value = true to computed
        }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pk_research_compare_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pk_research_compare_warning),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.pk_research_compare_close else R.string.pk_research_compare_open))
            }
            if (expanded) {
                Text(stringResource(R.string.pk_research_compare_baseline),
                    style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    baselines.forEachIndexed { index, baseline ->
                        FilterChip(
                            selected = index == baselineIndex,
                            onClick = { baselineIndex = index },
                            label = { Text("b=${baseline.toInt()}") },
                        )
                    }
                }
                val (finished, comparison) = resultState
                when {
                    !finished -> Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    comparison == null -> Text(stringResource(R.string.pk_research_compare_empty),
                        style = MaterialTheme.typography.bodySmall)
                    else -> {
                        Text(stringResource(R.string.pk_research_compare_scenario,
                            comparison.candidateId, comparison.matchingCandidateCount),
                            style = MaterialTheme.typography.bodySmall)
                        ConcChart(
                            ChartData(
                                x = comparison.timeHours,
                                y = comparison.legacyRelative,
                                comparisonY = comparison.experimentalRelative,
                                unit = stringResource(R.string.pk_research_compare_unit),
                            ),
                            comparison.timeHours.first(),
                            comparison.timeHours.last(),
                            Modifier.fillMaxWidth().height(240.dp),
                        ) { value -> java.lang.String.format(java.util.Locale.getDefault(), "%.2f", value) }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.pk_research_compare_old),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.pk_research_compare_new),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                        Text(stringResource(R.string.pk_research_compare_method,
                            comparison.consideredRecordedDoses, comparison.omittedOlderDoses),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
