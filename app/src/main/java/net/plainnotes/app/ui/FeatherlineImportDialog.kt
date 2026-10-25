package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*

@Composable internal fun FeatherlinePasswordDialog(error:Int?,onCancel:()->Unit,onConfirm:(CharArray)->Unit) {
    var password by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest={password="";onCancel()},title={Text(stringResource(R.string.featherline_password_title))},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.featherline_password_note))
            OutlinedTextField(password,{if(it.length<=4096)password=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.featherline_password))},
                visualTransformation=PasswordVisualTransformation(),singleLine=true,isError=error!=null)
            if(error!=null)Text(stringResource(error),color=MaterialTheme.colorScheme.error)
        }},confirmButton={Button(onClick={val chars=password.toCharArray();password="";onConfirm(chars)}){Text(stringResource(R.string.featherline_unlock))}},
        dismissButton={TextButton(onClick={password="";onCancel()}){Text(stringResource(R.string.cancel))}})
}
@Composable internal fun FeatherlineImportDialog(preview:FeatherlineImportPreview,summary:FeatherlineImportSummary?,onCancel:()->Unit,onConfirm:()->Unit) {
    val export=preview.export
    val doses=remember(export){export.doses.associateBy{it.id}}
    val labs=remember(export){export.labs.associateBy{it.id}}
    val items=summary?.items ?: preview.items
    AlertDialog(onDismissRequest=onCancel,title={Text(stringResource(if(summary==null)R.string.featherline_preview else R.string.featherline_result))},
        text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{Text(stringResource(R.string.featherline_version))}
            item{Text(stringResource(R.string.featherline_counts,summary?.doses ?: preview.newDoses,summary?.labs ?: preview.newLabs,summary?.existing ?: preview.existing,summary?.conflicts ?: preview.conflicts,export.issues.size))}
            item{Text(stringResource(R.string.featherline_safety))}
            item{Text(stringResource(R.string.featherline_ignored,export.counts["medicationGroups"] ?: 0,export.counts["stock"] ?: 0,export.counts["notes"] ?: 0,(export.counts["settings"] ?: 0)+(export.counts["userProfile"] ?: 0)))}
            items(items){row->Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.featherline_item,stringResource(if(row.kind=="LOG")R.string.mona_intake else R.string.mona_blood),row.id,
                    stringResource(when(row.status){
                        FeatherlineItemStatus.NEW->if(summary==null)R.string.mona_new else R.string.mona_saved
                        FeatherlineItemStatus.EXISTING->R.string.mona_existing
                        FeatherlineItemStatus.DELETED->R.string.mona_deleted
                        FeatherlineItemStatus.SOURCE_CHANGED->R.string.mona_source_changed
                        FeatherlineItemStatus.LOCAL_CHANGED->R.string.mona_local_changed
                        FeatherlineItemStatus.MISSING_TARGET->R.string.mona_missing
                        FeatherlineItemStatus.GROUP_CHANGED->R.string.mona_draw_changed
                    })))
                doses[row.id]?.let{Text("${it.medicine.name} · ${it.medicine.ester.orEmpty()} · ${it.medicine.route} · ${inputNumber(it.amount)} mg");Text("${java.time.Instant.ofEpochMilli(it.at)} · ${it.zone}",style=MaterialTheme.typography.bodySmall)}
                labs[row.id]?.let{Text("${it.analyte} · ${inputNumber(it.value)} ${it.unit}");Text("${java.time.Instant.ofEpochMilli(it.at)} · ${it.zone}",style=MaterialTheme.typography.bodySmall)}
            }}
            items(export.issues){issue->Text(stringResource(R.string.featherline_item,issue.kind,issue.id,stringResource(when(issue.reason){
                "UNSUPPORTED_MEDICINE"->R.string.mona_identity
                "UNSUPPORTED_ROUTE","PATCH_REMOVAL"->R.string.mona_route
                "UNSUPPORTED_UNIT"->R.string.mona_unit
                "UNREPRESENTABLE_DOSE","ACTUAL_DELTA"->R.string.mona_quantity
                "UNSUPPORTED_ANALYTE","UNSUPPORTED_RESULT"->R.string.mona_lab
                else->R.string.mona_field
            })))}
        }},confirmButton={Button(enabled=summary!=null || preview.newDoses+preview.newLabs>0,onClick=onConfirm){Text(stringResource(if(summary==null)R.string.featherline_confirm else R.string.ok))}},
        dismissButton={if(summary==null)TextButton(onClick=onCancel){Text(stringResource(R.string.cancel))}})
}
