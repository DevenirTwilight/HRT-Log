package net.plainnotes.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.export.ExportData
import net.plainnotes.app.security.launchPicker
import net.plainnotes.app.visit.VisitFacts
import net.plainnotes.app.visit.VisitPlanning
import net.plainnotes.app.visit.VisitPrepLayout
import net.plainnotes.app.visit.factLines
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Visit pack selection after the owner-approved sample 08 (REQUIREMENTS §150): grouped parts with what each is made of,
 * the parts that are off by default marked as such, a live count, the PDF's contents in export order and where the
 * exported copy goes. Same parts, same defaults and same export as before; only structured records, no originals.
 */
@Composable internal fun VisitPrepDialog(a:AppointmentEntity,state:NotesState,extra:NotesViewModel.ExtraState,onDismiss:()->Unit,onExport:(android.net.Uri,VisitExportRequest)->Unit) {
    val context=LocalContext.current;val resources=androidx.compose.ui.platform.LocalResources.current;val zone=ZoneId.systemDefault();val today=LocalDate.now()
    val range=remember(a,state.appointments) {VisitPlanning.defaultRange(a,state.appointments,today,zone)}
    var from by rememberSaveable {mutableStateOf(range.from.toString())};var to by rememberSaveable {mutableStateOf(range.to.toString())}
    var chosen by rememberSaveable {mutableStateOf(VisitSection.entries.filter {it.defaultOn}.map {it.name})}
    var pick by remember {mutableIntStateOf(0)}
    val labels=checkinLabels();val schedules=state.medications.associate {it.id to scheduleText(state.schedules[it.id])}
    val regimenLabels=extra.regimens.associate {it.id to regimenLabel(it)}
    val unknown=stringResource(R.string.timeline_medication_unknown)
    val sections=chosen.map {VisitSection.valueOf(it)}.toSet()
    val validRange=from<=to && LocalDate.parse(to)<=today
    val valid=validRange && sections.isNotEmpty()
    // The same facts the exported pack is built from; the counts below never come from anywhere else.
    val facts=remember(extra,state,from,to,validRange) {
        if(!validRange)null else VisitFacts.build(ExportData(state.medications,state.profiles,extra.records,extra.labs,extra.items,extra.scores,extra.notes,
            schedules,labels,extra.containers,extra.symptoms,extra.reviews,extra.labContexts,extra.regimens,extra.milestones,state.appointments),a,LocalDate.parse(from),LocalDate.parse(to),zone)
    }
    val preview=remember(facts) {facts?.let {factLines(resources,it,unknown)}.orEmpty()}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {uri->
        if(uri!=null)onExport(uri,VisitExportRequest(LocalDate.parse(from),LocalDate.parse(to),sections,context,labels,schedules,regimenLabels,unknown));onDismiss()
    }
    @Composable fun detail(s:VisitSection):String?=when(s) {
        VisitSection.QUESTIONS->extra.visitQuestions.count {it.appointment_id==a.id}.let {pluralStringResource(R.plurals.visit_prep_questions,it,it)}
        VisitSection.PACKAGES->extra.containers.count {it.source_note!=null || it.batch!=null}.let {pluralStringResource(R.plurals.visit_prep_packages,it,it)}
        VisitSection.REGIMEN->state.medications.count {it.active}.let {pluralStringResource(R.plurals.visit_prep_meds,it,it)}
        VisitSection.INTAKES->if(validRange)stringResource(R.string.visit_prep_period,formatDate(LocalDate.parse(from)),formatDate(LocalDate.parse(to))) else null
        else->facts?.let {f->when(s) {
            VisitSection.FACTS->f.days.toInt().let {pluralStringResource(R.plurals.visit_prep_days,it,it)}
            VisitSection.LABS->pluralStringResource(R.plurals.visit_prep_labs,f.labs,f.labs)
            VisitSection.SYMPTOMS->pluralStringResource(R.plurals.visit_prep_symptom_days,f.symptomDays,f.symptomDays)
            VisitSection.REVIEWS->pluralStringResource(R.plurals.visit_prep_reviews,f.reviews,f.reviews)
            VisitSection.DAILY->pluralStringResource(R.plurals.visit_prep_daily_days,f.dailyDays,f.dailyDays)
            VisitSection.MILESTONES->pluralStringResource(R.plurals.visit_prep_milestones,f.milestones,f.milestones)
            else->null
        }}
    }
    val largeText=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    @Composable fun footer() {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(pluralStringResource(R.plurals.visit_prep_selected,sections.size,sections.size),Modifier.testTag("visit-prep-count"),
                        style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    val actions=listOf(stringResource(R.string.export_pdf),stringResource(R.string.cancel))
                    AdaptiveActions(actions) {i,mod->
                        if(i==0)Button(enabled=valid,onClick={launcher.launchPicker("notes-visit-${to}.pdf")},modifier=mod) {Text(actions[i])}
                        else TextButton(onClick=onDismiss,modifier=mod) {Text(actions[i])}
                    }
                }
    }
    Dialog(onDismiss,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().testTag("visit-prep"),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start=4.dp,end=16.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onDismiss,Modifier.size(48.dp)) {Icon(Icons.Outlined.Close,stringResource(R.string.close))}
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.visit_prep_title),Modifier.semantics {heading()},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                        Text(formatDate(Instant.ofEpochMilli(a.at_utc).atZone(zone).toLocalDate())+" · "+stringResource(choiceRes(a.type.ifBlank {"OTHER"})),
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.visit_prep_intro),style=MaterialTheme.typography.bodyLarge)
                    ExportPrivacyNotice()
                    Text(stringResource(R.string.visit_prep_range),style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics {heading()})
                    Text(range.previous?.let {stringResource(R.string.visit_range_previous,formatDate(Instant.ofEpochMilli(it.at_utc).atZone(zone).toLocalDate()))} ?: stringResource(R.string.visit_range_none),
                        style=MaterialTheme.typography.bodySmall,color=if(range.previous==null)NotesTheme.semantic.attention.accent else MaterialTheme.colorScheme.onSurfaceVariant) // UI-06: a note, not an error
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({pick=1},Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.batch_from)+": "+from)}
                        OutlinedButton({pick=2},Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.batch_to)+": "+to)}
                    }
                    VisitPrepLayout.groups.forEach {(group,parts)->
                        Text(stringResource(when(group) {VisitPrepLayout.Group.VISIT->R.string.visit_prep_group_visit;VisitPrepLayout.Group.LABS->R.string.visit_prep_group_labs;VisitPrepLayout.Group.OTHER->R.string.visit_prep_group_other}),
                            Modifier.padding(top=8.dp).semantics {heading()},style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            parts.forEachIndexed {i,s->
                                val on=s.name in chosen
                                GroupedItem(i,parts.size) {
                                    Row(Modifier.fillMaxWidth().toggleable(on,role=Role.Checkbox) {v->chosen=if(v)chosen+s.name else chosen-s.name}
                                        .padding(horizontal=12.dp,vertical=12.dp).testTag("visit-prep:${s.name}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        Checkbox(on,null)
                                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                            Text(stringResource(sectionLabel(s)),style=MaterialTheme.typography.titleMedium)
                                            detail(s)?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                                val source=VisitPrepLayout.source(s)==VisitPrepLayout.Source.SUMMARISED
                                                val tone=MaterialTheme.colorScheme
                                                StatusPill(stringResource(if(source)R.string.visit_prep_tag_summarised else R.string.visit_prep_tag_entered),
                                                    if(source)tone.secondaryContainer else tone.surfaceContainerHigh,if(source)tone.onSecondaryContainer else tone.onSurfaceVariant)
                                                // Reflects the real programme default (VisitSection.defaultOn), never a separate badge rule.
                                                if(!s.defaultOn)StatusPill(stringResource(R.string.visit_prep_tag_default_off),NotesTheme.semantic.attention.container,NotesTheme.semantic.attention.onContainer)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if(sections.isEmpty())Text(stringResource(R.string.visit_no_section),color=MaterialTheme.colorScheme.error)
                    if(VisitSection.FACTS in sections && preview.isNotEmpty()) {
                        Text(stringResource(R.string.visit_preview),style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics {heading()})
                        preview.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
                    }
                    if(sections.isNotEmpty()) {
                        Text(stringResource(R.string.visit_prep_toc),style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics {heading()}.testTag("visit-prep-toc"))
                        VisitPrepLayout.contents(sections).forEachIndexed {i,s->Text("%02d  %s".format(i+1,stringResource(sectionLabel(s))),style=MaterialTheme.typography.bodyMedium)}
                        Text(stringResource(R.string.visit_prep_toc_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.visit_pdf_note),style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Info,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.visit_prep_external),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(largeText) {HorizontalDivider();footer()}
                }
                // Sample 08 keeps the count and actions fixed at the bottom; with large text that would leave little room
                // for the choices, so then they scroll after the content instead.
                if(!largeText) {HorizontalDivider();footer()}
            }
        }
    }
    if(pick!=0)DatePickerModal(LocalDate.parse(if(pick==1)from else to),{pick=0}) {if(it<=today) {if(pick==1)from=it.toString() else to=it.toString()};pick=0}
}
