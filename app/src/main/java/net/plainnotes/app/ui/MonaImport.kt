package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.importer.MonaMigration

/** Source-specific preview, in the existing unified import flow. No write on inspection. */
@Composable internal fun MonaImportWizard(preview:MonaImportPreview,onCancel:()->Unit,
    onConfirm:(Map<MonaMigration.Identity,Long?>,Map<MonaMigration.Identity,String>)->Unit) {
    val plan=preview.plan
    var page by remember(preview){mutableIntStateOf(0)}
    val count=preview.items.size+plan.issues.size+plan.sourceWarnings.size
    AlertDialog(onDismissRequest=onCancel,title={Text(stringResource(R.string.mona_preview))},
        text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.import_identified,"Mona"),style=MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.mona_version,plan.metadata.databaseVersion,plan.metadata.appVersion))
            Text(stringResource(R.string.mona_scan,plan.sourceIntakes,plan.sourceLabValues))
            Text(stringResource(R.string.mona_counts,preview.addedDoses,preview.addedLabs,preview.existing,preview.conflicts,plan.issues.size))
            Text(stringResource(R.string.mona_unsupported,plan.unsupportedSchedules,plan.unsupportedSupplies,plan.sourceWarnings.size))
            Text(stringResource(R.string.mona_review_note))
            HorizontalDivider()
            for(index in (page*20) until minOf((page+1)*20,count)) {
                val kind:String;val id:String;val message:String
                if(index<preview.items.size) {
                    val item=preview.items[index];kind=item.kind;id=item.sourceId+(if(item.part.isEmpty())"" else " / ${item.part}");message=monaStatus(item.status)
                } else if(index<preview.items.size+plan.issues.size) {
                    val issue=plan.issues[index-preview.items.size];kind=issue.kind;id=issue.sourceId+(if(issue.part.isEmpty())"" else " / ${issue.part}");message=monaReason(issue.reason)
                } else {
                    val issue=plan.sourceWarnings[index-preview.items.size-plan.issues.size];kind="SOURCE";id=listOfNotNull(issue.type,issue.sourceId,issue.field.takeIf{it.isNotEmpty()}).joinToString(" / ")
                    message=stringResource(if(issue.reason==net.plainnotes.app.importer.Mona.Reason.REFERENCE_ONLY)R.string.mona_reference_only else R.string.mona_unknown_field)
                }
                Text(stringResource(when(kind){"INTAKE"->R.string.mona_dose_item;"BLOOD_TEST"->R.string.mona_lab_item;else->R.string.mona_source_item},id,message),style=MaterialTheme.typography.bodySmall)
            }
            if(count>20)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(enabled=page>0,onClick={page--}){Text(stringResource(R.string.mona_previous))}
                Text(stringResource(R.string.mona_page,page+1,(count+19)/20))
                TextButton(enabled=(page+1)*20<count,onClick={page++}){Text(stringResource(R.string.mona_next))}
            }

        }},
        confirmButton={Button(enabled=preview.addedDoses+preview.addedLabs>0,onClick={onConfirm(preview.suggestedTargets,emptyMap())}){Text(stringResource(R.string.mona_confirm))}},
        dismissButton={TextButton(onClick=onCancel){Text(stringResource(R.string.cancel))}})
}
@Composable internal fun MonaImportReport(summary:MonaImportSummary,onClose:()->Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.mona_result))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.mona_committed,summary.addedDoses,summary.addedLabs,summary.addedDraws,summary.medications))
            Text(stringResource(R.string.mona_counts,summary.addedDoses,summary.addedLabs,summary.existing,summary.conflicts,summary.unsupported))
            Text(stringResource(R.string.mona_unsupported,summary.schedules,summary.supplies,summary.sourceNotices))
            Text(stringResource(R.string.mona_review_note))
        }},confirmButton={TextButton(onClick=onClose){Text(stringResource(R.string.ok))}})
}
@Composable private fun monaStatus(status:MonaItemStatus):String=stringResource(when(status){
    MonaItemStatus.NEW->R.string.mona_new
    MonaItemStatus.EXISTING->R.string.mona_existing
    MonaItemStatus.DELETED->R.string.mona_deleted
    MonaItemStatus.SOURCE_CHANGED->R.string.mona_source_changed
    MonaItemStatus.LOCAL_CHANGED->R.string.mona_local_changed
    MonaItemStatus.MISSING_TARGET->R.string.mona_missing_target
    MonaItemStatus.DRAW_CHANGED->R.string.mona_draw_changed
})
@Composable private fun monaReason(reason:MonaMigration.Reason):String=stringResource(when(reason){
    MonaMigration.Reason.NOT_TAKEN->R.string.mona_not_taken
    MonaMigration.Reason.INVALID_TIME,MonaMigration.Reason.TIME_PRECISION->R.string.mona_invalid_time
    MonaMigration.Reason.INVALID_DOSE,MonaMigration.Reason.DOSE_PRECISION,MonaMigration.Reason.INVALID_LAB->R.string.mona_invalid_value
    MonaMigration.Reason.UNKNOWN_IDENTITY,MonaMigration.Reason.UNKNOWN_ESTER->R.string.mona_unknown_identity
    MonaMigration.Reason.UNSUPPORTED_ROUTE->R.string.mona_unknown_route
    MonaMigration.Reason.UNSUPPORTED_UNIT,MonaMigration.Reason.UNKNOWN_LAB_UNIT,MonaMigration.Reason.UNSUPPORTED_QUANTITY->R.string.mona_unknown_unit
    MonaMigration.Reason.UNSUPPORTED_FIELD->R.string.mona_unknown_field
    MonaMigration.Reason.EMPTY_BLOOD_TEST->R.string.mona_empty_lab
})
