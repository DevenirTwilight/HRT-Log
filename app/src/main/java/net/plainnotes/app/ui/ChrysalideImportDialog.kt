package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*

/** Explicit target selection; source current dosage is never presented as an event dose. */
@Composable internal fun ChrysalideImportDialog(preview:ChrysalideImportPreview,summary:ChrysalideImportSummary?,medications:List<MedicationEntity>,onCancel:()->Unit,onConfirm:(Map<Long,Long>)->Unit) {
    val groups=preview.plan.intakes.map{it.medication}.distinctBy{it.id}
    val targets=remember(preview){mutableStateMapOf<Long,Long>()}
    val ready=summary!=null || preview.newCount==0 || groups.isNotEmpty()&&groups.all{targets[it.id]!=null}
    AlertDialog(onDismissRequest=onCancel,title={Text(stringResource(if(summary==null)R.string.chry_preview else R.string.chry_result))},
        text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.import_identified,"Chrysalide · v5"),style=MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.chry_counts,preview.plan.export.logs.size,preview.plan.intakes.size,preview.newCount,preview.items.count{it.status==ChrysalideItemStatus.EXISTING},preview.conflicts,preview.plan.export.bloodTests.size,summary?.added ?: 0))
            Text(stringResource(R.string.chry_semantics),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.chry_definition_policy),style=MaterialTheme.typography.bodySmall)
            groups.forEach{source->
                Text(stringResource(R.string.chry_map,source.name,source.method.orEmpty(),source.unit.orEmpty()),style=MaterialTheme.typography.labelLarge)
                if(medications.isEmpty())Text(stringResource(R.string.chry_no_targets),color=MaterialTheme.colorScheme.error)
                medications.forEach{target->Row(Modifier.fillMaxWidth().selectable(targets[source.id]==target.id,role=Role.RadioButton){targets[source.id]=target.id}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                    RadioButton(targets[source.id]==target.id,{targets[source.id]=target.id});Text(target.name,style=MaterialTheme.typography.bodyMedium)
                }}
            }
            preview.plan.intakes.zip(preview.items).take(40).forEach{(item,review)->
                val status=when(review.status){ChrysalideItemStatus.NEW->R.string.chry_new;ChrysalideItemStatus.EXISTING->R.string.chry_existing;ChrysalideItemStatus.DELETED->R.string.chry_deleted;ChrysalideItemStatus.LOCAL_CHANGED->R.string.chry_conflict;ChrysalideItemStatus.MISSING_TARGET->R.string.chry_conflict}
                Text(stringResource(R.string.chry_event,item.sourceId,item.at.toString(),item.medication.name)+" · "+stringResource(status))
            }
            Text(stringResource(R.string.chry_not_taken,preview.plan.issues.count{it.reason==net.plainnotes.app.importer.ChrysalideMigration.Reason.NOT_TAKEN}),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.chry_blood_unsupported,preview.plan.export.bloodTests.size),color=MaterialTheme.colorScheme.tertiary)
            preview.plan.unsupportedCollections.forEach{(kind,count)->Text(stringResource(R.string.chry_collection_unsupported,kind,count),style=MaterialTheme.typography.bodySmall)}
            Text(stringResource(R.string.chry_id_policy),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},
        confirmButton={Button(enabled=ready,onClick={onConfirm(targets.toMap())}){Text(stringResource(if(summary==null)R.string.chry_confirm else R.string.ok))}},
        dismissButton={if(summary==null)TextButton(onClick=onCancel){Text(stringResource(R.string.cancel))}})
}
