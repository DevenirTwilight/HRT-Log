package net.plainnotes.app.experimental

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.pk.experimental.ResearchModel
import net.plainnotes.app.pk.experimental.ResearchDisplayProtocol
import net.plainnotes.app.pk.experimental.ResearchSublingualV01
import java.time.Instant

/** The original screen owns every card, chart and setting. M2 is the only product SL reference; other routes retain explicitly scoped compatibility. */
@Composable fun UnifiedConcentrationScreen(records: List<RecordEntity>, ruleSnapshots: Map<Long,String>, labs: List<LabValueEntity>,
    pad: PaddingValues, content: @Composable (PaddingValues, ConcentrationPreview?, @Composable () -> Unit, @Composable () -> Unit) -> Unit,
    now: () -> Instant = Instant::now,
    /** D-1: the snapshot is recomputed this often while the page is shown, so "now" never silently goes stale. */
    refreshMillis: Long = 60_000,
    previewComputer: suspend (net.plainnotes.app.conc.experimental.SlAdapterAudit, ResearchModel, Double) -> ConcentrationPreview =
        { audit, model, hour ->
            val context=currentCoroutineContext()
            ConcentrationPreview.compute(audit,model,hour,checkCancelled={context.ensureActive()})
        }) {
    val model = ResearchModel.M2
    var retry by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshMillis) { while (true) { kotlinx.coroutines.delay(refreshMillis); tick++ } }
    val asOf = remember(records, ruleSnapshots, tick) { now() }
    val audit = key(records,ruleSnapshots,asOf) {
        produceState<net.plainnotes.app.conc.experimental.SlAdapterAudit?>(null) {
            value=withContext(Dispatchers.Default) { ExperimentalConcentrationComparison.audit(records,ruleSnapshots,asOf) }
        }.value
    }
    val scope=remember(records,ruleSnapshots,asOf) {
        net.plainnotes.app.conc.experimental.ResearchHistoryScope.inspect(records,ruleSnapshots,ConcentrationCalculator.hours(asOf))
    }
    // New keys create a fresh load state, so a previous model's result/error is never displayed.
    val load = key(audit,model,asOf,retry) {
        produceState<ResearchPreviewLoad>(ResearchPreviewLoad.Loading) {
            if (audit != null) {
                value = try {
                    ResearchPreviewLoad.Ready(withContext(Dispatchers.Default) {
                        previewComputer(audit!!,model,ConcentrationCalculator.hours(asOf)).also {
                            require(it.model==ResearchModel.M2 && it.candidateId==ResearchDisplayProtocol.m2Id)
                        }
                    })
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { ResearchPreviewLoad.Failed }
            }
        }.value
    }
    val displayed = (load as? ResearchPreviewLoad.Ready)?.preview?.takeIf { it.model == ResearchModel.M2 }
    // A periodic refresh of the same history keeps showing the previous complete snapshot (its own time base) instead of
    // an overlay; a history change still covers old values until the new snapshot is ready.
    var lastReady by remember { mutableStateOf<ConcentrationPreview?>(null) }
    var lastReadyHistory by remember { mutableStateOf<Pair<List<RecordEntity>,Map<Long,String>>?>(null) }
    SideEffect { if(displayed != null) { lastReady=displayed; lastReadyHistory=records to ruleSnapshots } }
    val sameHistory = lastReadyHistory == (records to ruleSnapshots)
    val shown = displayed ?: lastReady?.takeIf { sameHistory && load != ResearchPreviewLoad.Failed }
    var basisOpen by rememberSaveable { mutableStateOf(false) }
    // UI-12: what needs attention stays on the page (failure and retry, partial scope, no qualified history); the as-of time,
    // research basis and compatibility scope are explanations and live under "Model and literature".
    val status: @Composable () -> Unit = {
        if(load==ResearchPreviewLoad.Failed) {
            Text(stringResource(R.string.uc_preview_failed),modifier=Modifier.testTag("research-preview-error"))
            OutlinedButton(onClick={retry++},modifier=Modifier.testTag("m2-retry")) { Text(stringResource(R.string.m2_retry_reference)) }
        }
        if(scope.unknownRecords>0) Text(stringResource(R.string.ui12_unknown_records),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("e2-unknown-records"))
        if(shown!=null && shown.timeH.isEmpty() && !scope.compatibilityOnly) Text(stringResource(R.string.m2_no_qualified_history),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("m2-no-qualified-history"))
    }
    val basis: @Composable () -> Unit = {
        if (shown != null && shown.timeH.isNotEmpty()) {
            // Whole pg/mL and one-decimal hours: more digits would suggest a precision the evidence does not have.
            fun whole(v: Double) = java.text.NumberFormat.getIntegerInstance().format(kotlin.math.round(v))
            fun hours(v: Double) = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits=1 }.format(v)
            shown.asOfH?.let { h ->
                val at=java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withZone(java.time.ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli((h*3_600_000).toLong()))
                Text(stringResource(R.string.m2_scenario_as_of,at),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("m2-scenario-as-of"))
            }
            TextButton(onClick={basisOpen=!basisOpen},modifier=Modifier.testTag("m2-research-basis-toggle")) {
                Text(stringResource(if(basisOpen) R.string.m2_research_basis_hide else R.string.m2_research_basis))
            }
            if(basisOpen) Column(Modifier.testTag("m2-research-basis"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                val version = ResearchSublingualV01.version + " / " + ResearchDisplayProtocol.version
                Text(stringResource(R.string.m2_research_basis_body,model.name,shown.candidateId,version,
                    whole(shown.anchor.background),whole(shown.anchor.oneMgIncrementAtOneHour)),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.uc_original_assumptions),style=MaterialTheme.typography.bodySmall)
                val single=shown.singleDosePeak;val history=shown.historyWindowPeak
                if(single!=null && history!=null) Text(stringResource(R.string.uc_original_peaks,
                    whole(single.pgMl),hours(single.hour),whole(history.pgMl),hours(history.hour-shown.historyOriginH!!)),
                    style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("research-peaks"))
            }
        }
        if(scope.compatibilityOnly) Text(stringResource(R.string.m2_compatibility_scope),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("e2-compatibility-scope"))
    }
    // Keep the original renderer at the same composition position during model transitions.
    // An opaque, semantics-isolated overlay prevents old values/empty-state text being presented as the new model.
    // Keeping the last ready curve underneath retains the original non-saveable range, band and scroll state.
    val busy=shown == null && load!=ResearchPreviewLoad.Failed
    val rendererPreview=if(load==ResearchPreviewLoad.Failed) ConcentrationPreview.pending(model) else if(shown!=null && (scope.compatibilityOnly || records.isEmpty()) && shown.timeH.isEmpty()) null else shown ?: lastReady ?: ConcentrationPreview.pending(model)
    Box(Modifier.fillMaxSize()) {
        Box(if(busy) Modifier.clearAndSetSemantics {} else Modifier) {
            content(pad,rendererPreview,status,basis)
        }
        if(busy) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures(onTap={}) }.padding(pad).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            status()
            if (load == ResearchPreviewLoad.Failed) Text(stringResource(R.string.uc_preview_failed),modifier=Modifier.testTag("research-preview-error"))
            else {
                CircularProgressIndicator(Modifier.testTag("research-preview-loading"))
                Text(stringResource(R.string.uc_preview_loading))
            }
        }
    }


}

private sealed interface ResearchPreviewLoad {
    data object Loading : ResearchPreviewLoad
    data object Failed : ResearchPreviewLoad
    data class Ready(val preview: ConcentrationPreview) : ResearchPreviewLoad
}
