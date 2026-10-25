package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.importer.CrossAppInput

/** Counts describe the actual source scan, not promised inserts or guesses about target conflicts.
 * Existing source-specific choices and overwrite confirmation follow this shared first step. */
@Composable fun ForeignImportPreview(ready:NotesViewModel.DataJob.ForeignReady,onCancel:()->Unit,onOptions:()->Unit) {
    AlertDialog(onDismissRequest=onCancel,title={Text(stringResource(R.string.migration_preview))},
        text={Column(Modifier.testTag("migration-preview").verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.migration_source,when(ready.identity.source) {
                CrossAppInput.Source.HRT_TRACKER -> "HRT tracker / TransMTF"
                CrossAppInput.Source.TRANS_MEMO -> "Trans Memo"
            }),style=MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.migration_format,if(ready.identity.source==CrossAppInput.Source.TRANS_MEMO)"SQLite" else "JSON",ready.identity.version))
            Text(stringResource(R.string.migration_scan_note),style=MaterialTheme.typography.bodySmall)
            when(val next=ready.next) {
                is NotesViewModel.DataJob.HtReady -> {
                    Text(stringResource(R.string.migration_scan_ht,next.preview.events,next.preview.groups.size,next.preview.labs,next.preview.duplicates))
                    if(next.preview.skipped.isNotEmpty())Text(stringResource(R.string.migration_source_unsupported,next.preview.skipped.values.sum()))
                }
                is NotesViewModel.DataJob.ImportReady -> Text(stringResource(R.string.import_preview_body,next.preview.products,next.preview.takenIntakes,
                    next.preview.missed,next.preview.pending,next.preview.containers,next.preview.wellbeingScores,next.preview.notesWithText,next.preview.appointments))
                else -> error("Not a scanned import")
            }
            Text(stringResource(R.string.migration_unchanged),style=MaterialTheme.typography.bodySmall)
        }},confirmButton={Button(onClick=onOptions,modifier=Modifier.testTag("migration-options")){Text(stringResource(R.string.migration_options))}},
        dismissButton={TextButton(onClick=onCancel){Text(stringResource(R.string.cancel))}})
}
