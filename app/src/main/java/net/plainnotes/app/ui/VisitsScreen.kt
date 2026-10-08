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
                             onAdd: () -> Unit, contentPadding: PaddingValues) {
    val appointment = selected?.let { id -> state.appointments.firstOrNull { it.id == id } }
    if (appointment != null) {
        BackHandler { onSelect(null) }
        VisitDetail(appointment, state, extra, model, { onSelect(null) }, contentPadding)
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

@Composable private fun VisitDetail(a: AppointmentEntity, state: NotesState, extra: NotesViewModel.ExtraState, model: NotesViewModel, onBack: () -> Unit, contentPadding: PaddingValues) {
    var edit by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf<VisitQuestionEntity?>(null) }
    var generate by rememberSaveable { mutableStateOf(false) }
    val questions = extra.visitQuestions.filter { it.appointment_id == a.id }.sortedWith(compareBy({ it.sort_order }, { it.id }))
    val packs = extra.visitPacks.filter { it.appointment_id == a.id }.sortedByDescending { it.generated_utc }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.visits)) } }
        item {
            SectionCard(choiceLabel(a.type.ifBlank { "OTHER" })) {
                Text(formatDate(Instant.ofEpochMilli(a.at_utc).atZone(ZoneId.systemDefault()).toLocalDate()) + " " + formatTime(Instant.ofEpochMilli(a.at_utc)), style = MaterialTheme.typography.titleMedium)
                a.practitioner?.let { Text(stringResource(R.string.practitioner) + ": " + it) }
                a.location?.let { Text(stringResource(R.string.location) + ": " + it) }
                a.note?.let { Text(it) }
                AdaptiveActions(listOf(stringResource(R.string.edit),stringResource(R.string.visit_delete))) { i,mod ->
                    if(i==0)OutlinedButton(onClick={edit=true},modifier=mod){Icon(Icons.Outlined.Edit,null);Spacer(Modifier.width(6.dp));Text(stringResource(R.string.edit))}
                    else TextButton(onClick={remove=true},modifier=mod){Text(stringResource(R.string.visit_delete))}
                }
            }
        }
        item {
            SectionCard(stringResource(if (a.completed_utc != null) R.string.visit_completed else R.string.visit_not_confirmed)) {
                a.completed_utc?.let { Text(formatDateTime(Instant.ofEpochMilli(it)), style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.visit_confirm_help), style = MaterialTheme.typography.bodySmall)
                if (a.completed_utc == null) Button(onClick = { model.setVisitCompleted(a.id, true) }) { Icon(Icons.Outlined.TaskAlt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.visit_confirm)) }
                else OutlinedButton(onClick = { model.setVisitCompleted(a.id, false) }) { Text(stringResource(R.string.visit_unconfirm)) }
            }
        }
        item {
            SectionCard(stringResource(R.string.visit_questions)) {
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
                OutlinedButton(onClick = { question = VisitQuestionEntity(appointment_id = a.id, sort_order = 0, text = "") }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.visit_question_add)) }
            }
        }
        item {
            SectionCard(stringResource(R.string.visit_pack_title)) {
                Text(stringResource(R.string.visit_pdf_note), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { generate = true }) { Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.visit_generate)) }
                if (packs.isNotEmpty()) Text(stringResource(R.string.visit_generated_list), style = MaterialTheme.typography.titleSmall)
                packs.forEach { p -> Text(formatDateTime(Instant.ofEpochMilli(p.generated_utc)) + " · " + p.range_from + " – " + p.range_to + " · " + p.input_digest.take(12), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (edit) AppointmentDialog({ edit = false }, a) { model.appointment(it); edit = false }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, icon = { Icon(Icons.Outlined.Delete, null) }, text = { Text(stringResource(R.string.visit_delete_confirm)) },
        confirmButton = { Button(onClick = { remove = false; onBack(); model.deleteAppointment(a.id) }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text(stringResource(R.string.cancel)) } })
    question?.let { q -> QuestionDialog(q, { question = null }, { model.deleteVisitQuestion(q.id); question = null }) { model.saveVisitQuestion(it); question = null } }
    if (generate) VisitPackDialog(a, state, extra, { generate = false }) { uri, r -> model.exportVisitPack(uri, a.id, r.from, r.to, r.sections, r.context, r.labels, r.schedules, r.regimenLabels, r.unknownName) }
}

@Composable private fun QuestionDialog(initial: VisitQuestionEntity, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (VisitQuestionEntity) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.text) }
    var answer by rememberSaveable { mutableStateOf(initial.answer_note.orEmpty()) }
    var asked by rememberSaveable { mutableStateOf(initial.status == "ASKED") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.visit_question_text)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.visit_question_text)) })
            Row(Modifier.fillMaxWidth().toggleable(asked, role = Role.Checkbox) { asked = it }, verticalAlignment = Alignment.CenterVertically) { Checkbox(asked, null); Text(stringResource(R.string.visit_question_asked)) }
            OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.visit_answer)) })
            if (initial.id != 0L) TextButton(onClick = onDelete) { Text(stringResource(R.string.remove)) }
        }
    }, confirmButton = { Button(enabled = text.isNotBlank(), onClick = { onSave(initial.copy(text = text, answer_note = answer, status = if (asked) "ASKED" else "OPEN")) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun sectionLabel(s: VisitSection) = when (s) {
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

@Composable fun VisitPackDialog(a: AppointmentEntity, state: NotesState, extra: NotesViewModel.ExtraState, onDismiss: () -> Unit, onExport: (android.net.Uri, VisitExportRequest) -> Unit) {
    val context = LocalContext.current; val resources = androidx.compose.ui.platform.LocalResources.current; val zone = ZoneId.systemDefault(); val today = LocalDate.now()
    val range = remember(a, state.appointments) { VisitPlanning.defaultRange(a, state.appointments, today, zone) }
    var from by rememberSaveable { mutableStateOf(range.from.toString()) }; var to by rememberSaveable { mutableStateOf(range.to.toString()) }
    var chosen by rememberSaveable { mutableStateOf(VisitSection.entries.filter { it.defaultOn }.map { it.name }) }
    var pick by remember { mutableIntStateOf(0) }
    val labels = checkinLabels(); val schedules = state.medications.associate { it.id to scheduleText(state.schedules[it.id]) }
    val regimenLabels = extra.regimens.associate { it.id to regimenLabel(it) }
    val unknown = stringResource(R.string.timeline_medication_unknown)
    val sections = chosen.map { VisitSection.valueOf(it) }.toSet()
    val valid = from <= to && LocalDate.parse(to) <= today && sections.isNotEmpty()
    val preview = remember(extra, state, from, to, valid) {
        if (!valid) emptyList() else factLines(resources, VisitFacts.build(ExportData(state.medications, state.profiles, extra.records, extra.labs, extra.items, extra.scores, extra.notes,
            schedules, labels, extra.containers, extra.symptoms, extra.reviews, extra.labContexts, extra.regimens, extra.milestones, state.appointments), a, LocalDate.parse(from), LocalDate.parse(to), zone), unknown)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) onExport(uri, VisitExportRequest(LocalDate.parse(from), LocalDate.parse(to), sections, context, labels, schedules, regimenLabels, unknown)); onDismiss()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.visit_generate)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(range.previous?.let { stringResource(R.string.visit_range_previous, formatDate(Instant.ofEpochMilli(it.at_utc).atZone(zone).toLocalDate())) } ?: stringResource(R.string.visit_range_none),
                style = MaterialTheme.typography.bodySmall, color = if (range.previous == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { pick = 1 }) { Text(stringResource(R.string.batch_from) + ": " + from) }
            TextButton(onClick = { pick = 2 }) { Text(stringResource(R.string.batch_to) + ": " + to) }
            Text(stringResource(R.string.visit_sections), style = MaterialTheme.typography.titleSmall)
            VisitSection.entries.forEach { s ->
                Row(Modifier.fillMaxWidth().toggleable(s.name in chosen, role = Role.Checkbox) { on -> chosen = if (on) chosen + s.name else chosen - s.name }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.name in chosen, null)
                    Text(stringResource(sectionLabel(s)))
                }
            }
            Text(stringResource(R.string.visit_sections_private), style = MaterialTheme.typography.bodySmall)
            if (sections.isEmpty()) Text(stringResource(R.string.visit_no_section), color = MaterialTheme.colorScheme.error)
            if (VisitSection.FACTS in sections && preview.isNotEmpty()) {
                Text(stringResource(R.string.visit_preview), style = MaterialTheme.typography.titleSmall)
                preview.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Text(stringResource(R.string.visit_pdf_note), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        val actions=listOf(stringResource(R.string.export_pdf),stringResource(R.string.cancel))
        AdaptiveActions(actions,contentInset=48.dp) { i,mod ->
            if(i==0)Button(enabled=valid,onClick={launcher.launchPicker("notes-visit-${to}.pdf")},modifier=mod){Text(actions[i])}
            else TextButton(onClick=onDismiss,modifier=mod){Text(actions[i])}
        }
    })
    if (pick != 0) DatePickerModal(LocalDate.parse(if (pick == 1) from else to), { pick = 0 }) { if (it <= today) { if (pick == 1) from = it.toString() else to = it.toString() }; pick = 0 }
}
