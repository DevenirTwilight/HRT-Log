package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import org.json.JSONObject
import java.time.LocalDate

/** REQUIREMENTS §39: the recycle bin lives in Settings, never on the main pages. */
@Composable fun ColumnScope.RecycleBinSection(state:NotesState,extra:NotesViewModel.ExtraState,onRestore:(Long)->Unit,onPurge:(TrashItemEntity)->Unit,onEmpty:()->Unit) {
    var open by remember{mutableStateOf(false)}
    SectionCard(stringResource(R.string.trash_title)) {
        Text(stringResource(R.string.trash_intro),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().testTag("trash-open")){Text(stringResource(R.string.trash_open,extra.trash.size))}
    }
    if(open)RecycleBinDialog(state,extra,onRestore,onPurge,onEmpty){open=false}
}

@Composable fun RecycleBinDialog(state:NotesState,extra:NotesViewModel.ExtraState,onRestore:(Long)->Unit,onPurge:(TrashItemEntity)->Unit,onEmpty:()->Unit,onDismiss:()->Unit) {
    var purge by remember{mutableStateOf<TrashItemEntity?>(null)}
    var empty by remember{mutableStateOf(false)}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().testTag("trash-dialog")) {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.trash_title),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    TextButton(onClick=onDismiss){Text(stringResource(R.string.close))}
                }
                if(extra.trash.isEmpty())Text(stringResource(R.string.trash_empty_state),style=MaterialTheme.typography.bodyMedium)
                else OutlinedButton(onClick={empty=true},modifier=Modifier.fillMaxWidth().testTag("trash-empty")){Text(stringResource(R.string.trash_empty_all))}
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(extra.trash,key={it.id}){t->
                        Surface(tonalElevation=1.dp,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().testTag("trash-item:${t.id}")) {
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(trashKindLabel(t.kind),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                                Text(formatDate(LocalDate.parse(t.item_date))+trashTitle(t,state,extra).let{if(it.isBlank())"" else " · $it"},style=MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick={onRestore(t.id)},modifier=Modifier.testTag("trash-restore:${t.id}")){Text(stringResource(R.string.trash_restore))}
                                    TextButton(onClick={purge=t},modifier=Modifier.testTag("trash-purge:${t.id}")){Text(stringResource(R.string.trash_purge))}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    purge?.let{t->AlertDialog(onDismissRequest={purge=null},modifier=Modifier.testTag("trash-purge-dialog"),title={Text(stringResource(R.string.trash_purge))},
        text={Text(stringResource(R.string.trash_purge_confirm)+"\n\n"+stringResource(if(t.kind in listOf(Trash.RECORD,Trash.PERIOD))R.string.trash_purge_hidden_note else R.string.trash_purge_removed_note)+
            "\n\n"+stringResource(R.string.trash_purge_backup_note))},
        confirmButton={Button(onClick={purge=null;onPurge(t)},modifier=Modifier.testTag("trash-purge-confirm")){Text(stringResource(R.string.trash_purge))}},
        dismissButton={TextButton(onClick={purge=null}){Text(stringResource(R.string.cancel))}})}
    if(empty)AlertDialog(onDismissRequest={empty=false},modifier=Modifier.testTag("trash-empty-dialog"),title={Text(stringResource(R.string.trash_empty_all))},
        text={Text(stringResource(R.string.trash_empty_confirm)+"\n\n"+stringResource(R.string.trash_purge_hidden_note)+"\n\n"+stringResource(R.string.trash_purge_backup_note))},
        confirmButton={Button(onClick={empty=false;onEmpty()},modifier=Modifier.testTag("trash-empty-confirm")){Text(stringResource(R.string.trash_empty_all))}},
        dismissButton={TextButton(onClick={empty=false}){Text(stringResource(R.string.cancel))}})
}

@Composable private fun trashKindLabel(kind:String)=stringResource(when(kind){
    Trash.MILESTONE->R.string.trash_kind_milestone;Trash.LAB->R.string.trash_kind_lab;Trash.APPOINTMENT->R.string.trash_kind_appointment
    Trash.REVIEW->R.string.trash_kind_review;Trash.SYMPTOM->R.string.trash_kind_symptom;Trash.SCORE->R.string.trash_kind_score;Trash.NOTE->R.string.trash_kind_note
    Trash.RECORD->R.string.trash_kind_record;Trash.PERIOD->R.string.trash_kind_period;else->R.string.trash_kind_correction})

/** A short title; medicine names and doses are left out in simple mode (§39). */
@Composable private fun trashTitle(t:TrashItemEntity,state:NotesState,extra:NotesViewModel.ExtraState):String {
    val simple=LocalSimpleMode.current
    fun row()=runCatching{JSONObject(t.payload_json).let{p->p.getJSONObject("tables").getJSONArray(p.getJSONArray("order").getString(0)).getJSONObject(0)}}.getOrNull()
    return when(t.kind) {
        Trash.MILESTONE->row()?.let{r->if(r.isNull("title"))milestoneKindLabel(r.optString("kind","CUSTOM")) else r.getString("title")}.orEmpty()
        Trash.LAB->row()?.optString("analyte_code")?.let{analyteLabel(it)}.orEmpty()
        Trash.APPOINTMENT->row()?.optString("type")?.let{choiceLabel(it)}.orEmpty()
        Trash.SCORE->row()?.optLong("item_id")?.let{id->extra.items.firstOrNull{it.id==id}?.let{checkinLabel(it)}}.orEmpty()
        Trash.SYMPTOM->row()?.optString("group_id").orEmpty()
        Trash.RECORD->if(simple)"" else extra.trashedRecords.firstOrNull{it.id.toString()==t.ref}?.let{r->
            listOfNotNull(state.medications.firstOrNull{it.id==r.medication_id}?.name,r.actual_dose?.let{formatDose(it,state.medications.firstOrNull{m->m.id==r.medication_id}?.unit)}).joinToString(" · ")}.orEmpty()
        Trash.PERIOD,Trash.CORRECTION->extra.historyPeriods.filter{it.group_key==t.ref}.let{rows->
            val last=rows.mapNotNull{it.until_date}.maxOrNull()?.let{LocalDate.parse(it).minusDays(1)}
            (last?.let{"→ "+formatDate(it)} ?: "")+(if(simple)"" else rows.firstOrNull()?.let{r->state.medications.firstOrNull{it.id==r.medication_id}?.name?.let{" · $it"}}.orEmpty())}
        else->""
    }
}
