package net.plainnotes.app.experimental

import androidx.compose.foundation.layout.*
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
import net.plainnotes.app.pk.experimental.ResearchModel
import java.time.Instant

/** The original screen owns every card, chart and setting. This adds only model selection and a read-only preview. */
@Composable fun UnifiedConcentrationScreen(records: List<RecordEntity>, ruleSnapshots: Map<Long,String>, labs: List<LabValueEntity>,
    pad: PaddingValues, legacyContent: @Composable (PaddingValues, ConcentrationPreview?, @Composable () -> Unit) -> Unit,
    now: () -> Instant = Instant::now) {
    var model by rememberSaveable { mutableStateOf(ResearchModel.M2) }
    val asOf = remember(records, ruleSnapshots) { now() }
    val audit by produceState<net.plainnotes.app.conc.experimental.SlAdapterAudit?>(null,records,ruleSnapshots,asOf) {
        value=withContext(Dispatchers.Default) { ExperimentalConcentrationComparison.audit(records,ruleSnapshots,asOf) }
    }
    val available=audit?.let { it.events.isNotEmpty() || records.isEmpty() }
    LaunchedEffect(available) { if(available==false) model=ResearchModel.LEGACY }
    val preview by produceState<ConcentrationPreview?>(null,audit,model,asOf) {
        value=null
        if (model != ResearchModel.LEGACY && audit != null) value=withContext(Dispatchers.Default) {
            runCatching { ConcentrationPreview.compute(audit!!,model,
                ConcentrationCalculator.hours(asOf)) }.getOrNull()
        }
    }
    val displayed = if (model == ResearchModel.LEGACY) null else preview?.takeIf { it.model == model } ?: ConcentrationPreview.pending(model)
    legacyContent(pad,displayed) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ResearchModel.entries.forEach { choice -> FilterChip(model == choice,{model=choice},
                enabled=choice==ResearchModel.LEGACY || available!=false,label={Text(choice.name)},modifier=Modifier.testTag("model-${choice.name}")) }
        }
        if (displayed != null) Text(stringResource(R.string.uc_original_research_note,model.name,displayed.candidateId),
            style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("research-not-personal"))
        else Text(stringResource(R.string.uc_legacy_note),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("concentration-legacy"))
    }
}
