package net.plainnotes.app.experimental

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    pad: PaddingValues, content: @Composable (PaddingValues, ConcentrationPreview?, @Composable () -> Unit) -> Unit,
    now: () -> Instant = Instant::now,
    previewComputer: suspend (net.plainnotes.app.conc.experimental.SlAdapterAudit, ResearchModel, Double) -> ConcentrationPreview =
        { audit, model, hour ->
            val context=currentCoroutineContext()
            ConcentrationPreview.compute(audit,model,hour,checkCancelled={context.ensureActive()})
        }) {
    val model = ResearchModel.M2
    var retry by remember { mutableIntStateOf(0) }
    val asOf = remember(records, ruleSnapshots) { now() }
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
    val notice: @Composable () -> Unit = {
        if(load==ResearchPreviewLoad.Failed) {
            Text(stringResource(R.string.uc_preview_failed),modifier=Modifier.testTag("research-preview-error"))
            OutlinedButton(onClick={retry++},modifier=Modifier.testTag("m2-retry")) { Text(stringResource(R.string.m2_retry_reference)) }
        }

        if (displayed != null && displayed.timeH.isNotEmpty()) {
            fun number(v: Double) = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits=3 }.format(v)
            val version = ResearchSublingualV01.version + " / " + ResearchDisplayProtocol.version
            Text(stringResource(R.string.uc_original_research_note,model.name,displayed.candidateId,version,
                number(displayed.anchor.background),number(displayed.anchor.oneMgIncrementAtOneHour)),
                style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("research-not-personal"))
            val single=displayed.singleDosePeak;val history=displayed.historyWindowPeak
            if(single!=null && history!=null) Text(stringResource(R.string.uc_original_peaks,
                number(single.pgMl),number(single.hour),number(history.pgMl),number(history.hour-displayed.historyOriginH!!)),
                style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("research-peaks"))
        }
        if(scope.partial && !scope.compatibilityOnly) Text(stringResource(R.string.m2_partial_scope),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("m2-partial-scope"))
        if(scope.compatibilityOnly) Text(stringResource(R.string.m2_compatibility_scope),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("e2-compatibility-scope"))
        if(displayed!=null && displayed.timeH.isEmpty() && !scope.compatibilityOnly) Text(stringResource(R.string.m2_no_qualified_history),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("m2-no-qualified-history"))
    }
    // Keep the original renderer at the same composition position during model transitions.
    // An opaque, semantics-isolated overlay prevents old values/empty-state text being presented as the new model.
    // Keeping the last ready curve underneath retains the original non-saveable range, band and scroll state.
    var lastReady by remember { mutableStateOf<ConcentrationPreview?>(null) }
    SideEffect { if(displayed != null) lastReady=displayed }
    val busy=displayed == null && load!=ResearchPreviewLoad.Failed
    val rendererPreview=if(load==ResearchPreviewLoad.Failed) ConcentrationPreview.pending(model) else if(displayed!=null && (scope.compatibilityOnly || records.isEmpty()) && displayed.timeH.isEmpty()) null else displayed ?: lastReady ?: ConcentrationPreview.pending(model)
    Box(Modifier.fillMaxSize()) {
        Box(if(busy) Modifier.clearAndSetSemantics {} else Modifier) {
            content(pad,rendererPreview,notice)
        }
        if(busy) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures(onTap={}) }.padding(pad).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            notice()
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
