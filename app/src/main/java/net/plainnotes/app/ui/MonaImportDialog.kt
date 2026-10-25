package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.importer.MonaMigration

/** Source-specific review; no guessed mappings, active schedules, stock or PK configuration. */
@Composable internal fun MonaImportDialog(preview:MonaImportPreview,summary:MonaImportSummary?,onCancel:()->Unit,onConfirm:()->Unit) {
    val plan=preview.plan
    val doses=remember(plan){plan.doses.associateBy{it.sourceId}}
    val labs=remember(plan){plan.labs.associateBy{it.sourceId to it.analyte}}
    AlertDialog(onDismissRequest=onCancel,
        title={Text(stringResource(if(summary==null)R.string.mona_preview else R.string.mona_result))},
        text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(stringResource(R.string.mona_version,plan.metadata.databaseVersion,plan.metadata.appVersion))}
            item {Text(stringResource(R.string.mona_counts,plan.sourceIntakes,summary?.addedDoses ?: preview.addedDoses,
                plan.sourceDraws,summary?.addedLabs ?: preview.addedLabs))}
            item {Text(stringResource(R.string.mona_status_counts,preview.items.count{it.status==MonaItemStatus.EXISTING},
                preview.items.count{it.status==MonaItemStatus.DELETED},summary?.conflicts ?: preview.conflicts,plan.issues.size))}
            item {Text(stringResource(R.string.mona_unsupported,plan.unsupportedSchedules,plan.unsupportedSupplies))}
            item {Text(stringResource(R.string.mona_safety))}
            items(plan.doses.map{it.identity}.distinct()) {identity->Text(identity.label,style=MaterialTheme.typography.bodyMedium)}
            items(preview.items) {row->Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(stringResource(R.string.mona_item,
                stringResource(if(row.kind=="INTAKE")R.string.mona_intake else R.string.mona_blood),row.sourceId,row.part,
                stringResource(when(row.status) {
                    MonaItemStatus.NEW->if(summary==null)R.string.mona_new else R.string.mona_saved
                    MonaItemStatus.EXISTING->R.string.mona_existing
                    MonaItemStatus.DELETED->R.string.mona_deleted
                    MonaItemStatus.SOURCE_CHANGED->R.string.mona_source_changed
                    MonaItemStatus.LOCAL_CHANGED->R.string.mona_local_changed
                    MonaItemStatus.MISSING_TARGET->R.string.mona_missing
                    MonaItemStatus.DRAW_CHANGED->R.string.mona_draw_changed
                })))
                doses[row.sourceId]?.takeIf{row.kind=="INTAKE"}?.let {dose->
                    Text("${dose.identity.label} · ${dose.original} mg",style=MaterialTheme.typography.bodySmall)
                    Text("${dose.at} · ${dose.zone}",style=MaterialTheme.typography.bodySmall)
                }
                labs[row.sourceId to row.part]?.takeIf{row.kind=="BLOOD_TEST"}?.let {lab->
                    Text("${lab.analyte} · ${lab.original} ${lab.unit}",style=MaterialTheme.typography.bodySmall)
                    Text("${lab.at} · ${lab.zone}",style=MaterialTheme.typography.bodySmall)
                }
            }}
            items(plan.sourceWarnings.filter{it.reason !in setOf(net.plainnotes.app.importer.Mona.Reason.REFERENCE_ONLY,net.plainnotes.app.importer.Mona.Reason.NOT_TAKEN)}) {warning->
                Text(stringResource(R.string.mona_warning,warning.type,warning.sourceId ?: "—",warning.field,warning.count))
            }
            items(plan.issues) {issue->Text(stringResource(R.string.mona_item,
                stringResource(if(issue.kind=="INTAKE")R.string.mona_intake else R.string.mona_blood),issue.sourceId,issue.part,
                stringResource(when(issue.reason) {
                    MonaMigration.Reason.NOT_TAKEN->R.string.mona_not_taken
                    MonaMigration.Reason.INVALID_TIME->R.string.mona_time
                    MonaMigration.Reason.TIME_PRECISION,MonaMigration.Reason.DOSE_PRECISION->R.string.mona_precision
                    MonaMigration.Reason.INVALID_DOSE->R.string.mona_dose
                    MonaMigration.Reason.UNKNOWN_IDENTITY,MonaMigration.Reason.UNKNOWN_ESTER->R.string.mona_identity
                    MonaMigration.Reason.UNSUPPORTED_ROUTE->R.string.mona_route
                    MonaMigration.Reason.UNSUPPORTED_QUANTITY->R.string.mona_quantity
                    MonaMigration.Reason.UNSUPPORTED_UNIT,MonaMigration.Reason.UNKNOWN_LAB_UNIT->R.string.mona_unit
                    MonaMigration.Reason.UNSUPPORTED_FIELD->R.string.mona_field
                    MonaMigration.Reason.INVALID_LAB,MonaMigration.Reason.EMPTY_BLOOD_TEST->R.string.mona_lab
                })))}
        }},
        confirmButton={Button(onClick=onConfirm){Text(stringResource(if(summary==null)R.string.mona_confirm else R.string.ok))}},
        dismissButton={if(summary==null)TextButton(onClick=onCancel){Text(stringResource(R.string.cancel))}})
}
