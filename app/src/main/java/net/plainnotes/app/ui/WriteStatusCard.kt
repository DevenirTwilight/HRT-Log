package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.domain.write.*

/** Read-only inline status, not a modal or a second operation owner. Hide only stops displaying
 * this card. Callers must supply actual owner observations; no P4 entry is enabled by this component. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun WriteStatusCard(
    progress:WriteProgressSummary,
    onHide:()->Unit,
    modifier:Modifier=Modifier,
    onCheck:(()->Unit)?=null,
    onRetry:(()->Unit)?=null,
) {
    val title=when(progress.status) {
        WriteStatus.SUCCESS->R.string.write_status_success
        WriteStatus.FAILED->R.string.write_status_failed
        WriteStatus.PENDING->when(progress.phase) {
            WritePhase.RECONCILING->R.string.write_status_reconciling
            WritePhase.MANUAL_CHECK->R.string.write_status_manual
            else->R.string.write_status_pending
        }
    }
    val body=when(progress.status) {
        WriteStatus.SUCCESS->null
        WriteStatus.FAILED->R.string.write_status_failed_body
        WriteStatus.PENDING->when(progress.phase) {
            WritePhase.RECONCILING->R.string.write_status_reconciling_body
            WritePhase.MANUAL_CHECK->R.string.write_status_manual_body
            else->R.string.write_status_pending_body
        }
    }
    Surface(modifier.fillMaxWidth().testTag("write-status"),shape=Corners.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(Spacing.l),verticalArrangement=Arrangement.spacedBy(Spacing.s)) {
            Row(horizontalArrangement=Arrangement.spacedBy(Spacing.m)) {
                if(progress.status==WriteStatus.PENDING && progress.phase!=WritePhase.MANUAL_CHECK)
                    CircularProgressIndicator(Modifier.size(24.dp).clearAndSetSemantics{},strokeWidth=2.dp)
                Text(stringResource(title),style=MaterialTheme.typography.titleMedium,
                    modifier=Modifier.weight(1f).semantics{liveRegion=LiveRegionMode.Polite}.testTag("write-status-title"))
            }
            body?.let{Text(stringResource(it),style=MaterialTheme.typography.bodyMedium)}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(Spacing.s)) {
                if(progress.phase==WritePhase.MANUAL_CHECK && onCheck!=null)
                    TextButton(onClick=onCheck,modifier=Modifier.heightIn(min=48.dp).testTag("write-status-check")){Text(stringResource(R.string.write_status_check))}
                if(progress.status==WriteStatus.FAILED && progress.retryAllowed && onRetry!=null)
                    TextButton(onClick=onRetry,modifier=Modifier.heightIn(min=48.dp).testTag("write-status-retry")){Text(stringResource(R.string.write_status_retry))}
                TextButton(onClick=onHide,modifier=Modifier.heightIn(min=48.dp).testTag("write-status-hide")){Text(stringResource(R.string.write_status_hide))}
            }
        }
    }
}
