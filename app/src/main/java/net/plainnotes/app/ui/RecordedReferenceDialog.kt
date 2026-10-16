package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import net.plainnotes.app.R
import net.plainnotes.app.data.*

/** Frozen entry context, not a claim about the medicine actually taken. Read-only; no live catalogue join. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RecordedReferenceDialog(record:RecordEntity,epoch:String?,loader:suspend (String,Long)->RecordedReferenceHistory,onDismiss:()->Unit) {
    var loaded by remember(record.id,epoch){mutableStateOf<RecordedReferenceHistory?>(null)}
    var loading by remember(record.id,epoch){mutableStateOf(true)}
    var error by remember(record.id,epoch){mutableStateOf(false)}
    var stale by remember(record.id,epoch){mutableStateOf(false)}
    LaunchedEffect(record.id,epoch) {
        try {if(epoch!=null)loaded=loader(epoch,record.id)}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=true;stale=e is ReferenceFailure && e.code==ReferenceError.STALE_SCOPE}
        finally {loading=false}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.ref2_history_title))},navigationIcon={IconButton(onClick=onDismiss,modifier=Modifier.testTag("record-reference-close")){Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.cancel))}})}) {padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("record-reference-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text(stringResource(R.string.ref2_history_saved_only))}
                if(loading)item {LinearProgressIndicator(Modifier.fillMaxWidth().testTag("record-reference-loading"))}
                if(error)item {Text(stringResource(if(stale)R.string.ref2_history_stale else R.string.ref2_history_error),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("record-reference-error"))}
                if(!loading && !error) {
                    val history=loaded;val revision=history?.recordRevision ?: record.revision
                    if(history==null || history.contexts.none {it.recordRevision==revision})item {
                        Text(stringResource(R.string.ref2_history_revision,revision))
                        Text(stringResource(R.string.ref2_history_empty),modifier=Modifier.testTag("record-reference-empty"))
                    }
                    history?.contexts?.forEach { context->item(key="${context.recordUuid}:${context.recordRevision}:${context.captureRevision}") {
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.ref2_history_revision,context.recordRevision),style=MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.ref2_history_context))
                            context.product?.let {ReferenceProductDetails(it)} ?: Text(stringResource(R.string.ref2_history_empty))
                        }
                    } }
                }
            }
        }
    }
}
