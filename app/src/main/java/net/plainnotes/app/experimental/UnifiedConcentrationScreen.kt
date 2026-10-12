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
import net.plainnotes.app.pk.experimental.ResearchM1Catalog
import java.time.Instant

/** The original screen owns every card, chart and setting. This adds only model selection and a read-only preview. */
@Composable fun UnifiedConcentrationScreen(records: List<RecordEntity>, ruleSnapshots: Map<Long,String>, labs: List<LabValueEntity>,
    pad: PaddingValues, legacyContent: @Composable (PaddingValues, ConcentrationPreview?, @Composable () -> Unit) -> Unit,
    now: () -> Instant = Instant::now,
    previewComputer: suspend (net.plainnotes.app.conc.experimental.SlAdapterAudit, ResearchModel, Double) -> ConcentrationPreview =
        { audit, model, hour ->
            val context=currentCoroutineContext()
            ConcentrationPreview.compute(audit,model,hour,checkCancelled={context.ensureActive()})
        }) {
    var model by rememberSaveable { mutableStateOf(ResearchModel.M2) }
    val asOf = remember(records, ruleSnapshots) { now() }
    val audit by produceState<net.plainnotes.app.conc.experimental.SlAdapterAudit?>(null,records,ruleSnapshots,asOf) {
        value=withContext(Dispatchers.Default) { ExperimentalConcentrationComparison.audit(records,ruleSnapshots,asOf) }
    }
    val available=audit?.let { it.events.isNotEmpty() || records.isEmpty() }
    LaunchedEffect(available) { if(available==false) model=ResearchModel.LEGACY }
    // New keys create a fresh load state, so a previous model's result/error is never displayed.
    val load = key(audit,model,asOf) {
        produceState<ResearchPreviewLoad>(ResearchPreviewLoad.Loading) {
            if (model != ResearchModel.LEGACY && audit != null) {
                value = try {
                    ResearchPreviewLoad.Ready(withContext(Dispatchers.Default) {
                        previewComputer(audit!!,model,ConcentrationCalculator.hours(asOf))
                    })
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { ResearchPreviewLoad.Failed }
            }
        }.value
    }
    val displayed = (load as? ResearchPreviewLoad.Ready)?.preview?.takeIf { model != ResearchModel.LEGACY && it.model == model }
    val selector: @Composable () -> Unit = {

        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ResearchModel.entries.forEach { choice -> FilterChip(model == choice,{model=choice},
                enabled=choice==ResearchModel.LEGACY || available!=false,label={Text(choice.name)},modifier=Modifier.testTag("model-${choice.name}")) }
        }
        if (displayed != null) {
            fun number(v: Double) = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits=3 }.format(v)
            val version = if(model==ResearchModel.M2) ResearchSublingualV01.version else ResearchM1Catalog.version + " / " + ResearchDisplayProtocol.m1ProjectionVersion
            Text(stringResource(R.string.uc_original_research_note,model.name,displayed.candidateId,version,
                number(displayed.anchor.background),number(displayed.anchor.oneMgIncrementAtOneHour)),
                style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("research-not-personal"))
            val single=displayed.singleDosePeak;val history=displayed.historyWindowPeak
            if(single!=null && history!=null) Text(stringResource(R.string.uc_original_peaks,
                number(single.pgMl),number(single.hour),number(history.pgMl),number(history.hour-displayed.historyOriginH!!)),
                style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("research-peaks"))
        }
        else if(model == ResearchModel.LEGACY) Text(stringResource(R.string.uc_legacy_note),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("concentration-legacy"))
    }
    // Keep the original renderer at the same composition position during model transitions.
    // An opaque, semantics-isolated overlay prevents old values/empty-state text being presented as the new model.
    // Keeping the last ready curve underneath retains the original non-saveable range, band and scroll state.
    var lastReady by remember { mutableStateOf<ConcentrationPreview?>(null) }
    SideEffect { if(displayed != null) lastReady=displayed }
    val busy=model != ResearchModel.LEGACY && displayed == null
    val rendererPreview=if(model == ResearchModel.LEGACY) null else displayed ?: lastReady ?: ConcentrationPreview.pending(model)
    Box(Modifier.fillMaxSize()) {
        Box(if(busy) Modifier.clearAndSetSemantics {} else Modifier) {
            legacyContent(pad,rendererPreview,selector)
        }
        if(busy) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures(onTap={}) }.padding(pad).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            selector()
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
