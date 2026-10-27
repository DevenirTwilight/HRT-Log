package net.plainnotes.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.ScheduleSummary
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.export.ExportData
import net.plainnotes.app.security.launchPicker
import net.plainnotes.app.visit.VisitFacts
import net.plainnotes.app.visit.VisitPlanning
import net.plainnotes.app.visit.factLines
import java.time.*

/** Appointments with their questions and visit packs. Selecting one shows its detail; Back returns to the list. */
@Composable fun VisitsScreen(state: NotesState, extra: NotesViewModel.ExtraState, model: NotesViewModel, selected: Long?, onSelect: (Long?) -> Unit,
                             onAdd: () -> Unit, contentPadding: PaddingValues, medical:Boolean=false, onMedicalEdit:(AppointmentEntity)->Unit={}, onLab:(LabValueEntity)->Unit={}) {
    val appointment = selected?.let { id -> state.appointments.firstOrNull { it.id == id } }
    LaunchedEffect(selected,appointment,medical){if(medical && selected!=null && appointment==null)onSelect(null)}
    if (appointment != null) {
        BackHandler { onSelect(null) }
        VisitDetail(appointment, state, extra, model, { onSelect(null) }, contentPadding,medical,onMedicalEdit,onLab)
        return
    }
    val now = Instant.now().toEpochMilli()
    val upcoming = state.appointments.filter { it.at_utc >= now }.sortedBy { it.at_utc }
    val past = state.appointments.filter { it.at_utc < now }.sortedByDescending { it.at_utc }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.appointments.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.EventNote, stringResource(R.string.visits), stringResource(R.string.visits_empty)) { Button(onClick = onAdd) { Text(stringResource(R.string.appointment)) } } }
        if (upcoming.isNotEmpty()) item { Text(stringResource(R.string.visits_upcoming), style = MaterialTheme.typography.titleSmall) }
        items(upcoming, key = { "u${it.id}" }) { VisitCard(it, extra) { onSelect(it.id) } }
        if (past.isNotEmpty()) item { Text(stringResource(R.string.visits_past), style = MaterialTheme.typography.titleSmall) }
        items(past, key = { "p${it.id}" }) { VisitCard(it, extra) { onSelect(it.id) } }
    }
}

@Composable private fun VisitCard(a: AppointmentEntity, extra: NotesViewModel.ExtraState, onClick: () -> Unit) {
    val questions = extra.visitQuestions.count { it.appointment_id == a.id }
    val last = extra.visitPacks.filter { it.appointment_id == a.id }.maxByOrNull { it.generated_utc }
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(choiceLabel(a.type.ifBlank { "OTHER" }), style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(formatDate(Instant.ofEpochMilli(a.at_utc).atZone(ZoneId.systemDefault()).toLocalDate()) + " " + formatTime(Instant.ofEpochMilli(a.at_utc)), a.practitioner, a.location).joinToString(" · "))
            Text(stringResource(if (a.completed_utc != null) R.string.visit_completed else R.string.visit_not_confirmed), style = MaterialTheme.typography.labelMedium,
                color = if (a.completed_utc != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (questions > 0) Text(stringResource(R.string.visit_question_count, questions), style = MaterialTheme.typography.bodySmall)
            last?.let { Text(stringResource(R.string.visit_last_pack, formatDateTime(Instant.ofEpochMilli(it.generated_utc))), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun VisitDetail(a: AppointmentEntity, state: NotesState, extra: NotesViewModel.ExtraState, model: NotesViewModel, onBack: () -> Unit, contentPadding: PaddingValues, medical:Boolean=false,onMedicalEdit:(AppointmentEntity)->Unit={},onLab:(LabValueEntity)->Unit={}) {
    // This page remains guarded until activation, backup compatibility and release isolation are verified.
    var attachmentsOpen by remember(a.id) { mutableStateOf(false) }
    if (attachmentsOpen && net.plainnotes.app.attachments.AttachmentFlowController.PUBLIC_ENTRY_ENABLED) {
        net.plainnotes.app.attachments.ui.MedicalAttachmentRecordPage(
            model, net.plainnotes.app.data.MedicalAttachmentOperations.Target(
                net.plainnotes.app.data.MedicalAttachmentOperations.Kind.APPOINTMENT, a.id),
            stringResource(R.string.appointment), onBack={attachmentsOpen=false})
        return
    }
    var edit by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var medicalRemove by remember { mutableStateOf<AppointmentEntity?>(null) }
    var medicalRetract by remember { mutableStateOf<AppointmentEntity?>(null) }
    var question by remember { mutableStateOf<VisitQuestionEntity?>(null) }
    var generate by rememberSaveable { mutableStateOf(false) }
    val questions = extra.visitQuestions.filter { it.appointment_id == a.id }.sortedWith(compareBy({ it.sort_order }, { it.id }))
    val packs = extra.visitPacks.filter { it.appointment_id == a.id }.sortedByDescending { it.generated_utc }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if(medical)R.string.medical_records else R.string.visits)) } }
        item {
            SectionCard(choiceLabel(a.type.ifBlank { "OTHER" })) {
                val displayZone=if(medical)ZoneId.of(a.at_zone) else ZoneId.systemDefault()
                val local=Instant.ofEpochMilli(a.at_utc).atZone(displayZone)
                Text(formatDate(local.toLocalDate()) + " " + formatTime(local.toLocalTime()), style = MaterialTheme.typography.titleMedium)
                if(medical)Text(stringResource(R.string.medical_visit_zone,a.at_zone),style=MaterialTheme.typography.bodySmall)
                a.practitioner?.let { Text(stringResource(R.string.practitioner) + ": " + it) }
                a.location?.let { Text(stringResource(R.string.location) + ": " + it) }
                a.note?.let { Text(it) }
                AdaptiveActions(listOf(stringResource(R.string.edit),stringResource(R.string.visit_delete))) { i,mod ->
                    if(i==0)OutlinedButton(onClick={if(medical)onMedicalEdit(a) else edit=true},modifier=mod){Icon(Icons.Outlined.Edit,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.edit))}
                    else TextButton(onClick={if(medical)medicalRemove=a else remove=true},modifier=mod){Text(stringResource(R.string.visit_delete))}
                }
            }
        }
        if (net.plainnotes.app.attachments.AttachmentFlowController.PUBLIC_ENTRY_ENABLED) item {
            OutlinedButton(onClick={attachmentsOpen=true}) { Text(stringResource(R.string.attach_section)) }
        }
        item {
            SectionCard(stringResource(if (a.completed_utc != null) R.string.visit_completed else R.string.visit_not_confirmed)) {
                a.completed_utc?.let { Text(formatDateTime(Instant.ofEpochMilli(it)), style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.visit_confirm_help), style = MaterialTheme.typography.bodySmall)
                if (a.completed_utc == null) Button(onClick = { if(medical){model.clearMedicalVisitSave();model.saveMedicalVisit(a,a)}else model.setVisitCompleted(a.id, true) }) { Icon(Icons.Outlined.TaskAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.visit_confirm)) }
                else if(medical)OutlinedButton(onClick={medicalRetract=a}){Text(stringResource(R.string.medical_retract))}
                else OutlinedButton(onClick = { model.setVisitCompleted(a.id, false) }) { Text(stringResource(R.string.visit_unconfirm)) }
            }
        }
        // UI-09 C / D: the sections below the visit card are numbered in display order (not read aloud) and separated by thin
        // rules instead of card fills.
        if(medical)item("medical-labs") { NumberedSection(1, stringResource(R.string.medical_context_labs), Modifier.padding(top = 12.dp).testTag("visit-section:labs")) { MedicalVisitLabs(a,extra.labs,onLab) } }
        item {
            NumberedSection(if (medical) 2 else 1, stringResource(R.string.visit_questions), Modifier.padding(top = 12.dp).testTag("visit-section:questions"), first = !medical) {
                questions.forEachIndexed { i, q ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(q.status == "ASKED", { model.saveVisitQuestion(q.copy(status = if (it) "ASKED" else "OPEN")) })
                        Column(Modifier.weight(1f).clickable { question = q }) {
                            Text("${i + 1}. " + q.text)
                            Text(stringResource(if (q.status == "ASKED") R.string.visit_question_asked else R.string.visit_question_open), style = MaterialTheme.typography.labelSmall)
                            q.answer_note?.let { Text(stringResource(R.string.visit_answer) + ": " + it, style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { model.moveVisitQuestion(q.id, true) }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.visit_move_up)) }
                        IconButton(onClick = { model.moveVisitQuestion(q.id, false) }, enabled = i < questions.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.visit_move_down)) }
                    }
                }
                OutlinedButton(onClick = { question = VisitQuestionEntity(appointment_id = a.id, sort_order = 0, text = "") }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.visit_question_add)) }
            }
        }
        item {
            NumberedSection(if (medical) 3 else 2, stringResource(R.string.visit_pack_title), Modifier.padding(top = 12.dp).testTag("visit-section:pack")) {
                Text(stringResource(R.string.visit_pdf_note), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { generate = true }) { Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.visit_generate)) }
                if (packs.isNotEmpty()) Text(stringResource(R.string.visit_generated_list), style = MaterialTheme.typography.titleSmall)
                packs.forEach { p -> Text(formatDateTime(Instant.ofEpochMilli(p.generated_utc)) + " · " + p.range_from + " – " + p.range_to + " · " + p.input_digest.take(12), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (edit) AppointmentDialog({ edit = false }, a) { model.appointment(it); edit = false }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, icon = { Icon(Icons.Outlined.Delete, null) }, text = { Text(stringResource(R.string.visit_delete_confirm)) },
        confirmButton = { Button(onClick = { remove = false; onBack(); model.deleteAppointment(a.id) }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text(stringResource(R.string.cancel)) } })
    medicalRemove?.let{expected->MedicalCorrectionConfirmation(false,{medicalRemove=null}){medicalRemove=null;model.deleteMedicalVisit(expected);onBack()}}
    medicalRetract?.let{expected->MedicalCorrectionConfirmation(true,{medicalRetract=null}){medicalRetract=null;model.retractMedicalVisit(expected)}}
    question?.let { q -> QuestionDialog(q, { question = null }, { model.deleteVisitQuestion(q.id); question = null },allowDelete=!medical) { model.saveVisitQuestion(it); question = null } }
    if (generate) VisitPackDialog(a, state, extra, { generate = false }) { uri, r -> model.exportVisitPack(uri, a.id, r.from, r.to, r.sections, r.context, r.labels, r.schedules, r.regimenLabels, r.unknownName) }
}

@Composable private fun QuestionDialog(initial: VisitQuestionEntity, onDismiss: () -> Unit, onDelete: () -> Unit, allowDelete:Boolean=true,onSave: (VisitQuestionEntity) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.text) }
    var answer by rememberSaveable { mutableStateOf(initial.answer_note.orEmpty()) }
    var asked by rememberSaveable { mutableStateOf(initial.status == "ASKED") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.visit_question_text)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.visit_question_text)) })
            Row(Modifier.fillMaxWidth().toggleable(asked, role = Role.Checkbox) { asked = it }, verticalAlignment = Alignment.CenterVertically) { Checkbox(asked, null); Text(stringResource(R.string.visit_question_asked)) }
            OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.visit_answer)) })
            if (initial.id != 0L && allowDelete) TextButton(onClick = onDelete) { Text(stringResource(R.string.remove)) }
        }
    }, confirmButton = { Button(enabled = text.isNotBlank(), onClick = { onSave(initial.copy(text = text, answer_note = answer, status = if (asked) "ASKED" else "OPEN")) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

internal fun sectionLabel(s: VisitSection) = when (s) {
    VisitSection.FACTS -> R.string.visit_section_facts; VisitSection.REGIMEN -> R.string.visit_section_regimen; VisitSection.INTAKES -> R.string.visit_section_intakes
    VisitSection.LABS -> R.string.visit_section_labs; VisitSection.SYMPTOMS -> R.string.visit_section_symptoms; VisitSection.REVIEWS -> R.string.visit_section_reviews
    VisitSection.DAILY -> R.string.visit_section_daily; VisitSection.QUESTIONS -> R.string.visit_section_questions; VisitSection.PACKAGES -> R.string.visit_section_packages
    VisitSection.MILESTONES -> R.string.visit_section_milestones
}

/** Description of a frozen regimen version from its own snapshot, never from the current medication. */
@Composable fun regimenLabel(version: RegimenVersionEntity): String {
    val d = RegimenDefinition.read(version.definition_json); val m = d.snapshot(version.medication_id)
    val schedule = ScheduleSummary(RuleKind.valueOf(d.kind), d.interval, DayOfWeek.entries.filter { d.weekdays and (1 shl (it.value - 1)) != 0 }.toSet(), d.times.map { LocalTime.parse(it.first) }, d.dose, d.times.map { it.second })
    return listOfNotNull(m?.name ?: stringResource(R.string.timeline_medication_unknown), m?.molecule?.let { choiceLabel(it) }, m?.profile?.ester?.takeIf { it != m.molecule }?.let { choiceLabel(it) },
        m?.route?.let { choiceLabel(it) }, formatDose(d.dose, m?.unit), scheduleText(schedule)).joinToString(" · ")
}

class VisitExportRequest(val from: LocalDate, val to: LocalDate, val sections: Set<VisitSection>, val context: android.content.Context, val labels: (CheckinItemEntity) -> String,
                         val schedules: Map<Long, String>, val regimenLabels: Map<Long, String>, val unknownName: String)

/** Sample 08 selection (VisitPrepDialog); same parts, defaults and export as before. */
@Composable fun VisitPackDialog(a: AppointmentEntity, state: NotesState, extra: NotesViewModel.ExtraState, onDismiss: () -> Unit, onExport: (android.net.Uri, VisitExportRequest) -> Unit) =
    VisitPrepDialog(a, state, extra, onDismiss, onExport)
