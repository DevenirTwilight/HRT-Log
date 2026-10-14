package net.plainnotes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.medical.*
import java.time.*

/** A view over existing records, never a second clinical store or an inferred visit/lab association. */
@Composable fun MedicalRecordsScreen(appointments:List<AppointmentEntity>,onVisit:(AppointmentEntity)->Unit,onAdd:()->Unit,
    onLabs:()->Unit,onHistory:()->Unit,onTrash:()->Unit,contentPadding:PaddingValues) {
    val past=MedicalRecordIndex.past(appointments,Instant.now())
    val confirmed=past.filter{it.completed_utc!=null}
    val unconfirmed=past.filter{it.completed_utc==null}
    LazyColumn(Modifier.fillMaxSize().testTag("medical-list"),contentPadding=PaddingValues(start=16.dp,end=16.dp,
        top=contentPadding.calculateTopPadding()+8.dp,bottom=contentPadding.calculateBottomPadding()+32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("actions") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=onAdd,modifier=Modifier.fillMaxWidth().testTag("medical-add")){Text(stringResource(R.string.medical_add_visit))}
            OutlinedButton(onClick=onLabs,modifier=Modifier.fillMaxWidth().testTag("medical-labs")){Text(stringResource(R.string.labs))}
            OutlinedButton(onClick=onHistory,modifier=Modifier.fillMaxWidth().testTag("medical-history")){Text(stringResource(R.string.medical_timeline))}
            TextButton(onClick=onTrash,modifier=Modifier.fillMaxWidth().testTag("medical-bin")){Text(stringResource(R.string.medical_bin))}
        } }
        if(past.isEmpty())item("empty"){Text(stringResource(R.string.medical_empty))}
        if(confirmed.isNotEmpty())item("confirmed"){Text(stringResource(R.string.medical_confirmed),style=MaterialTheme.typography.titleSmall)}
        items(confirmed,key={"visit:${it.id}"}) { MedicalVisitCard(it,onVisit) }
        if(unconfirmed.isNotEmpty())item("unconfirmed"){Text(stringResource(R.string.medical_unconfirmed),style=MaterialTheme.typography.titleSmall)}
        items(unconfirmed,key={"visit:${it.id}"}) { MedicalVisitCard(it,onVisit) }
    }
}
@Composable private fun MedicalVisitCard(a:AppointmentEntity,onVisit:(AppointmentEntity)->Unit) {
    val local=Instant.ofEpochMilli(a.at_utc).atZone(ZoneId.of(a.at_zone))
    ElevatedCard(Modifier.fillMaxWidth().testTag("medical-visit:${a.id}").clickable(role=Role.Button){onVisit(a)}) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(choiceLabel(a.type),style=MaterialTheme.typography.titleMedium)
            Text(formatDate(local.toLocalDate())+" · "+formatTime(local.toLocalTime())+" · "+a.at_zone)
            listOfNotNull(a.practitioner,a.location).forEach{Text(it)}
            Text(stringResource(if(a.completed_utc!=null)R.string.visit_completed else R.string.visit_not_confirmed),style=MaterialTheme.typography.labelMedium)
        }
    }
}

/** Drafts stay in memory; failure retains them. A stored instant is not reinterpreted in the device's current zone. */
@Composable fun MedicalVisitDialog(initial:AppointmentEntity?,state:MedicalVisitSaveState,onDismiss:()->Unit,onSave:(AppointmentEntity)->Unit) {
    val zone=remember(initial?.id){ZoneId.of(initial?.at_zone ?: ZoneId.systemDefault().id)}
    var time by remember(initial?.id){mutableStateOf(initial?.let{Instant.ofEpochMilli(it.at_utc).atZone(zone).toLocalDateTime()} ?: Instant.now().minusSeconds(60).atZone(zone).toLocalDateTime().withSecond(0).withNano(0))}
    var type by remember(initial?.id){mutableStateOf(initial?.type ?: "OTHER")}
    var doctor by remember(initial?.id){mutableStateOf(initial?.practitioner.orEmpty())}
    var institution by remember(initial?.id){mutableStateOf(initial?.location.orEmpty())}
    var note by remember(initial?.id){mutableStateOf(initial?.note.orEmpty())}
    var offset by remember{mutableStateOf<ZoneOffset?>(null)}
    var consumed by remember{mutableStateOf(false)}
    LaunchedEffect(state.failed){if(state.failed)consumed=false}
    val at=MedicalVisitTime.resolve(time,zone,initial,offset)
    val valid=at!=null && !at.isAfter(Instant.now())
    AlertDialog(onDismissRequest={if(!state.saving)onDismiss()},title={Text(stringResource(if(initial==null)R.string.medical_add_visit else R.string.medical_edit_visit))},
        text={Column(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.medical_past_help))
            if(state.failed)Text(stringResource(if(state.stale)R.string.medical_stale else R.string.medical_save_error),color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.medical_visit_zone,zone.id),modifier=Modifier.testTag("medical-zone"))
            DropdownField(stringResource(R.string.appointment_type),APPOINTMENT_TYPES+(initial?.type?.takeIf{it !in APPOINTMENT_TYPES}?.let{listOf(it)} ?: emptyList()),type,{choiceLabel(it)},{if(!state.saving)type=it})
            DateTimeRow(time,{if(!state.saving){val next=if(it.hour==time.hour && it.minute==time.minute)it.withSecond(time.second).withNano(time.nano) else it;if(next!=time){time=next;offset=null}}})
            val offsets=zone.rules.getValidOffsets(time)
            if(offsets.size>1 && at==null)Text(stringResource(R.string.medical_time_ambiguous),color=MaterialTheme.colorScheme.error)
            if(offsets.size>1)DropdownField(stringResource(R.string.medical_offset),offsets.map{it.id},offset?.id.orEmpty(),{it.ifBlank{stringResource(R.string.medical_offset)}},{if(!state.saving)offset=ZoneOffset.of(it)})
            if(offsets.isEmpty())Text(stringResource(R.string.medical_time_gap),color=MaterialTheme.colorScheme.error)
            if(at!=null && !valid)Text(stringResource(R.string.medical_future_error),color=MaterialTheme.colorScheme.error)
            OutlinedTextField(doctor,{doctor=it},enabled=!state.saving,modifier=Modifier.fillMaxWidth(),label={Text(stringResource(R.string.practitioner))})
            OutlinedTextField(institution,{institution=it},enabled=!state.saving,modifier=Modifier.fillMaxWidth(),label={Text(stringResource(R.string.location))})
            OutlinedTextField(note,{note=it},enabled=!state.saving,modifier=Modifier.fillMaxWidth(),label={Text(stringResource(R.string.note))},minLines=2)
        }},confirmButton={
            AdaptiveActions(listOf(stringResource(R.string.save),stringResource(R.string.cancel)),contentInset=48.dp){i,mod->
                if(i==0)Button(enabled=valid && !state.saving && !consumed,onClick={if(!consumed && !state.saving){val finalAt=MedicalVisitTime.resolve(time,zone,initial,offset);if(finalAt!=null && !finalAt.isAfter(Instant.now())){consumed=true;onSave((initial ?: AppointmentEntity(type=type,at_utc=finalAt.toEpochMilli(),at_zone=zone.id,remind_minutes_before=0)).copy(type=type,at_utc=finalAt.toEpochMilli(),at_zone=zone.id,practitioner=doctor,location=institution,note=note))}}},modifier=mod.testTag("medical-save")){Text(stringResource(if(state.saving)R.string.milestone_saving else R.string.save))}
                else TextButton(enabled=!state.saving,onClick=onDismiss,modifier=mod){Text(stringResource(R.string.cancel))}
            }
        })
}

/** Explicitly a time-window browser; it writes no association, duplicate lab, or inferred diagnosis. */
@Composable fun MedicalVisitLabs(a:AppointmentEntity,labs:List<LabValueEntity>,onLab:(LabValueEntity)->Unit) {
    val zone=ZoneId.of(a.at_zone)
    val day=Instant.ofEpochMilli(a.at_utc).atZone(zone).toLocalDate()
    var from by remember(a.id,a.at_utc,a.at_zone){mutableStateOf(day.minusDays(30))}
    var to by remember(a.id,a.at_utc,a.at_zone){mutableStateOf(day)}
    var pick by remember{mutableIntStateOf(0)}
    val values=remember(labs,from,to,zone){MedicalRecordIndex.labs(labs,from,to,zone)}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.medical_context_help),modifier=Modifier.testTag("medical-lab-context-help"))
        Text(zone.id,style=MaterialTheme.typography.labelSmall)
        TextButton(onClick={pick=1}){Text(stringResource(R.string.batch_from)+": "+formatDate(from))}
        TextButton(onClick={pick=2}){Text(stringResource(R.string.batch_to)+": "+formatDate(to))}
        if(values.isEmpty())Text(stringResource(R.string.medical_context_empty))
        else LazyColumn(Modifier.heightIn(max=320.dp).testTag("medical-lab-range")) {
            items(values,key={"lab:${it.id}"}) { v -> val local=Instant.ofEpochMilli(v.sampled_utc).atZone(zone)
                TextButton(onClick={onLab(v)},modifier=Modifier.fillMaxWidth().testTag("medical-lab:${v.id}")) {
                    Text(analyteLabel(v.analyte_code)+" · "+displayNumber(v.value)+" "+v.unit+" · "+formatDate(local.toLocalDate())+" "+formatTime(local.toLocalTime()))
                }
            }
        }
    }
    if(pick!=0)DatePickerModal(if(pick==1)from else to,{pick=0}) { d -> if(pick==1){from=d;if(to<d)to=d}else{to=d;if(from>d)from=d};pick=0 }
}
@Composable fun MedicalCorrectionConfirmation(retract:Boolean,onDismiss:()->Unit,onConfirm:()->Unit) {
    var consumed by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(if(retract)R.string.medical_retract else R.string.visit_delete))},
        text={Text(stringResource(if(retract)R.string.medical_retract_help else R.string.medical_delete_help))},
        confirmButton={AdaptiveActions(listOf(stringResource(if(retract)R.string.medical_retract else R.string.remove),stringResource(R.string.cancel)),contentInset=48.dp){i,mod->
            if(i==0)Button(enabled=!consumed,onClick={if(!consumed){consumed=true;onConfirm()}},modifier=mod.testTag("medical-correction-confirm")){Text(stringResource(if(retract)R.string.medical_retract else R.string.remove))}
            else TextButton(onClick=onDismiss,modifier=mod){Text(stringResource(R.string.cancel))}
        }})
}
