package net.plainnotes.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                             onAdd: () -> Unit, contentPadding: PaddingValues, medical:Boolean=false, onMedicalEdit:(AppointmentEntity)->Unit={}, onLab:(LabValueEntity)->Unit={},
                             onAddHistorical:()->Unit=onAdd) {
    val appointment = selected?.let { id -> state.appointments.firstOrNull { it.id == id } }
    LaunchedEffect(selected,appointment,medical){if(medical && selected!=null && appointment==null)onSelect(null)}
    if (appointment != null) {
        BackHandler { onSelect(null) }
        VisitDetail(appointment, state, extra, model, { onSelect(null) }, contentPadding,medical,onMedicalEdit,onLab)
        return
    }
    val now = Instant.now().toEpochMilli()
    val system = ZoneId.systemDefault()
    val upcoming = state.appointments.filter { it.at_utc >= now }.sortedBy { it.at_utc }
    val past = state.appointments.filter { it.at_utc < now }.sortedByDescending { it.at_utc }
    val hero = VisitHero.pick(state.appointments, now)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // UI-14: the header card carries the "record a past visit" entry (same tag, same callback as before) and the next or last visit.
        item("historical-visit") { VisitsHeroCard(hero, medical, system, now, onAddHistorical) }
        if (state.appointments.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.EventNote, stringResource(R.string.visits), stringResource(R.string.visits_empty)) { Button(onClick = onAdd) { Text(stringResource(R.string.appointment)) } } }
        if (upcoming.isNotEmpty()) item { Text(stringResource(R.string.visits_upcoming), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() }.testTag("visits-group-upcoming")) }
        items(upcoming, key = { "u${it.id}" }) { VisitCard(it, extra, medical, system) { onSelect(it.id) } }
        if (past.isNotEmpty()) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.visits_past), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() }.testTag("visits-group-past"))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = Corners.pill, modifier = Modifier.testTag("visits-past-count")) {
                    Text("${past.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        items(past, key = { "p${it.id}" }) { VisitCard(it, extra, medical, system) { onSelect(it.id) } }
    }
}

/**
 * UI-14: the teal header card. Names the next visit (or, with none ahead, the last confirmed one) with its day count, and holds the
 * white "record a past visit" capsule. The two shapes in the corner are decoration only and are not read out.
 */
@Composable private fun VisitsHeroCard(pick: VisitHeroPick?, medical: Boolean, system: ZoneId, nowMs: Long, onRecordPast: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.primary, contentColor = c.onPrimary, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().testTag("visits-hero")) {
        Column(Modifier.drawBehind {
            drawCircle(c.onPrimary.copy(alpha = 0.10f), radius = 64.dp.toPx(), center = Offset(size.width - 12.dp.toPx(), 8.dp.toPx()))
            drawRoundRect(c.onPrimary.copy(alpha = 0.08f), topLeft = Offset(size.width - 120.dp.toPx(), size.height - 56.dp.toPx()), size = Size(72.dp.toPx(), 72.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()))
        }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pick != null) {
                val v = pick.visit; val zone = VisitHero.displayZone(v, medical, system); val local = Instant.ofEpochMilli(v.at_utc).atZone(zone)
                val days = VisitHero.days(VisitHero.dayOffset(v.at_utc, nowMs, zone))
                val daysText = when (days) {
                    VisitDays.Today -> stringResource(R.string.visits_hero_today)
                    is VisitDays.Ahead -> pluralStringResource(R.plurals.visits_hero_in_days, days.days, days.days)
                    is VisitDays.Ago -> pluralStringResource(R.plurals.visits_hero_days_ago, days.days, days.days)
                }
                val label = stringResource(if (pick.kind == VisitHeroKind.NEXT) R.string.visits_hero_next else R.string.visits_hero_last)
                val line = listOf(formatWeekdayLong(local.toLocalDate()) + " " + formatTime(local.toLocalTime()), choiceLabel(v.type.ifBlank { "OTHER" })).joinToString(" · ")
                val who = listOfNotNull(v.practitioner, v.location).filter { it.isNotBlank() }.joinToString(" · ")
                Column(Modifier.semantics(mergeDescendants = true) {}.testTag("visits-hero-info"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("visits-hero-label"))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(formatMonthDay(local.toLocalDate()), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("visits-hero-date"))
                        Surface(color = c.onPrimary.copy(alpha = 0.18f), contentColor = c.onPrimary, shape = Corners.pill, modifier = Modifier.testTag("visits-hero-days")) {
                            Text(daysText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("visits-hero-line"))
                    if (who.isNotEmpty()) Text(who, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("visits-hero-who"))
                }
            } else {
                Text(stringResource(R.string.visits_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("visits-hero-empty"))
            }
            Button(onClick = onRecordPast, shape = Corners.pill, colors = ButtonDefaults.buttonColors(containerColor = c.onPrimary, contentColor = c.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("visits-record-past")) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.medical_add_visit))
            }
        }
    }
}

@Composable private fun VisitCard(a: AppointmentEntity, extra: NotesViewModel.ExtraState, medical: Boolean, system: ZoneId, onClick: () -> Unit) {
    val questions = extra.visitQuestions.count { it.appointment_id == a.id }
    val last = extra.visitPacks.filter { it.appointment_id == a.id }.maxByOrNull { it.generated_utc }
    val zone = VisitHero.displayZone(a, medical, system); val local = Instant.ofEpochMilli(a.at_utc).atZone(zone); val date = local.toLocalDate()
    val c = MaterialTheme.colorScheme; val sem = NotesTheme.semantic; val accent = NotesTheme.accent
    val confirmed = a.completed_utc != null
    val type = choiceLabel(a.type.ifBlank { "OTHER" }); val time = formatTime(local.toLocalTime())
    val doctor = a.practitioner?.takeIf { it.isNotBlank() }; val place = a.location?.takeIf { it.isNotBlank() }
    val statusText = stringResource(if (confirmed) R.string.visit_completed else R.string.visit_not_confirmed)
    val questionText = if (questions > 0) stringResource(R.string.visit_question_count, questions) else null
    val packText = last?.let { stringResource(R.string.visit_last_pack, formatDateTime(Instant.ofEpochMilli(it.generated_utc))) }
    // One spoken description for the whole card; the pieces inside are decoration for the eye.
    val spoken = listOfNotNull(type, formatFullDate(date) + " " + time, doctor, place, statusText, questionText, packText).joinToString(", ")
    Surface(onClick = onClick, color = c.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .semantics { contentDescription = spoken }.testTag("visit-card:${a.id}")) {
        Row(Modifier.clearAndSetSemantics {}.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(58.dp).background(accent.blue.container, RoundedCornerShape(18.dp)).padding(vertical = 8.dp).testTag("visit-date-tile:${a.id}"),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatMonthShort(date), style = MaterialTheme.typography.labelSmall, color = accent.blue.onContainer, maxLines = 1)
                Text(formatDayOfMonth(date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent.blue.onContainer, maxLines = 1)
                Text(formatWeekdayShort(date), style = MaterialTheme.typography.labelSmall, color = accent.blue.onContainer, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text(type, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(time, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.primary)
                }
                doctor?.let { VisitLine(Icons.Outlined.Person, it, 1) }
                place?.let { VisitLine(Icons.Outlined.LocationOn, it, 2) }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (confirmed) Box(Modifier.testTag("visit-tag-confirmed")) { StatusPill(statusText, sem.success.container, sem.success.onContainer, Icons.Rounded.Check) }
                    else VisitDashedTag(statusText, Modifier.testTag("visit-tag-unconfirmed"))
                    questionText?.let { Box(Modifier.testTag("visit-tag-questions")) { StatusPill(it, accent.blue.soft, accent.blue.main) } }
                    packText?.let { Box(Modifier.testTag("visit-tag-pack")) { StatusPill(it, accent.purple.container, accent.purple.onContainer, Icons.Outlined.Description) } }
                }
            }
        }
    }
}

/** A line with a small accent icon; the text is cut after [maxLines] lines on screen, while the card's spoken description keeps it whole. */
@Composable private fun VisitLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, maxLines: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(16.dp), tint = NotesTheme.accent.blue.main)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** "Not confirmed" keeps its UI-06 look: a dashed outline and no fill, with the words, so it never depends on colour. */
@Composable private fun VisitDashedTag(text: String, modifier: Modifier = Modifier) {
    val sem = NotesTheme.semantic; val style = StatusStyles.of(DoseState.UNCONFIRMED)
    Surface(color = androidx.compose.ui.graphics.Color.Transparent, contentColor = sem.unconfirmed.onContainer, shape = Corners.pill,
        modifier = modifier.statusOutline(style, sem.unconfirmed, Corners.pill, 1.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable private fun VisitDetail(a: AppointmentEntity, state: NotesState, extra: NotesViewModel.ExtraState, model: NotesViewModel, onBack: () -> Unit, contentPadding: PaddingValues, medical:Boolean=false,onMedicalEdit:(AppointmentEntity)->Unit={},onLab:(LabValueEntity)->Unit={}) {
    // Related files of this explicit visit; null while the guarded attachment capability is off.
    val files = net.plainnotes.app.attachments.ui.rememberRecordFiles(model, net.plainnotes.app.data.MedicalAttachmentOperations.Target(
        net.plainnotes.app.data.MedicalAttachmentOperations.Kind.APPOINTMENT, a.id))
    val filesLabel = choiceLabel(a.type.ifBlank { "OTHER" }) + " · " + formatDate(Instant.ofEpochMilli(a.at_utc).atZone(ZoneId.of(a.at_zone)).toLocalDate())
    var edit by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var medicalRemove by remember { mutableStateOf<AppointmentEntity?>(null) }
    var medicalRetract by remember { mutableStateOf<AppointmentEntity?>(null) }
    var question by remember { mutableStateOf<VisitQuestionEntity?>(null) }
    var generate by rememberSaveable { mutableStateOf(false) }
    val questions = extra.visitQuestions.filter { it.appointment_id == a.id }.sortedWith(compareBy({ it.sort_order }, { it.id }))
    val packs = extra.visitPacks.filter { it.appointment_id == a.id }.sortedByDescending { it.generated_utc }
    Box(Modifier.fillMaxSize()) {
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
        if (files != null) item("visit-files") { net.plainnotes.app.attachments.ui.RecordFilesSection(files, filesLabel, Modifier.padding(top = 4.dp)) }
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
        // The file page covers only the page area below the shell's app bar, so its own back arrow stays reachable.
        if (files != null) Box(Modifier.fillMaxSize().padding(contentPadding)) { net.plainnotes.app.attachments.ui.RecordFilesViewer(files, filesLabel) }
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
