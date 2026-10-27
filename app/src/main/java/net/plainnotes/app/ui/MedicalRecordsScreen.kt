package net.plainnotes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.medical.*
import java.time.*

/**
 * UI-10: the medical archive in two areas. "Records" is what happened (next visit, visits waiting for confirmation, confirmed
 * visits, lab results, the treatment timeline); "Files" is where documents live. A view over existing records, never a second
 * clinical store or an inferred visit/lab association (§87: closeness in time is never shown as a link). Files are managed
 * through an explicit visit or an individual lab result; the "Add medical file" action first asks which record. Features that
 * do not exist yet are described once, in one card. [onConfirm] is wired to the same existing write the visit detail's
 * "Confirm the visit took place" uses (saveMedicalVisit → completed_utc, with its stale-record check); it is undone with
 * "Retract" in that detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MedicalRecordsScreen(appointments:List<AppointmentEntity>,onVisit:(AppointmentEntity)->Unit,onAdd:()->Unit,
    onLabs:()->Unit,onHistory:()->Unit,onTrash:()->Unit,contentPadding:PaddingValues,onConfirm:(AppointmentEntity)->Unit={onVisit(it)},
    questionCounts:Map<Long,Int> = emptyMap(),now:Instant=Instant.now(),onVisits:(()->Unit)?=null,onMedicalBackup:(()->Unit)?=null,
    labs:List<LabValueEntity> = emptyList(),onLab:((LabValueEntity)->Unit)?=null,initialTab:Int=0,onTabChange:(Int)->Unit={},
    labPanels:List<LabPanelEntity> = emptyList()) {
    val past=MedicalRecordIndex.past(appointments,now)
    val confirmed=past.filter{it.completed_utc!=null}
    val unconfirmed=past.filter{it.completed_utc==null}
    val next=appointments.filter{it.at_utc>now.toEpochMilli()}.minWithOrNull(compareBy<AppointmentEntity>{it.at_utc}.thenBy{it.id})
    val byYear=confirmed.groupBy{Instant.ofEpochMilli(it.at_utc).atZone(ZoneId.of(it.at_zone)).year}
    // The Files area exists only where the guarded attachment capability is wired in.
    val filesAvailable=onVisits!=null || onMedicalBackup!=null
    var tab by rememberSaveable{mutableIntStateOf(initialTab)}
    // A lab result tile counts blood tests (draws), not single results; the results are named too.
    val drawCount=remember(labs,labPanels){labDraws(labs,labPanels).size}
    var choosing by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        if(filesAvailable)PrimaryTabRow(tab,Modifier.padding(top=contentPadding.calculateTopPadding()).testTag("medical-tabs")) {
            Tab(tab==0,{tab=0;onTabChange(0)},Modifier.heightIn(min=48.dp).testTag("medical-tab:records"),text={Text(stringResource(R.string.mh_tab_records))})
            Tab(tab==1,{tab=1;onTabChange(1)},Modifier.heightIn(min=48.dp).testTag("medical-tab:files"),text={Text(stringResource(R.string.mh_tab_files))})
        }
        LazyColumn(Modifier.fillMaxSize().testTag("medical-list"),contentPadding=PaddingValues(start=16.dp,end=16.dp,
            top=if(filesAvailable)16.dp else contentPadding.calculateTopPadding()+8.dp,bottom=contentPadding.calculateBottomPadding()+32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(!filesAvailable || tab==0)recordsArea(next,unconfirmed,past.isEmpty(),confirmed,byYear,questionCounts,now,onVisit,onConfirm,onVisits,onLabs,onHistory,onTrash,drawCount,labs.size)
            else filesArea(onVisits,onLabs,onMedicalBackup,onAddFile={choosing=true})
        }
    }
    if(choosing)AddFileTargetDialog(appointments,labs,labPanels,onVisit={choosing=false;onVisit(it)},onLab=onLab?.let{open->{v:LabValueEntity->choosing=false;open(v)}},onDismiss={choosing=false})
}

private fun LazyListScope.recordsArea(next:AppointmentEntity?,unconfirmed:List<AppointmentEntity>,noVisits:Boolean,confirmed:List<AppointmentEntity>,byYear:Map<Int,List<AppointmentEntity>>,
    questionCounts:Map<Long,Int>,now:Instant,onVisit:(AppointmentEntity)->Unit,onConfirm:(AppointmentEntity)->Unit,onVisits:(()->Unit)?,onLabs:()->Unit,onHistory:()->Unit,onTrash:()->Unit,drawCount:Int,resultCount:Int) {
    // UI-05 F: "Record a past visit" is AppShell's top bar "+" (MedicalAddAction), not a list button.
    next?.let{a->item("next"){NextVisitCard(a,questionCounts[a.id] ?: 0,now,onVisit)}}
    if(unconfirmed.isNotEmpty())item("unconfirmed"){MedicalHeading(stringResource(R.string.medical_were_you_there))}
    items(unconfirmed,key={"visit:${it.id}"}) { MedicalVisitCard(it,questionCounts[it.id] ?: 0,onVisit,onConfirm) }
    if(noVisits)item("empty"){Text(stringResource(R.string.medical_empty))}
    if(confirmed.isNotEmpty())item("confirmed"){MedicalHeading(stringResource(R.string.medical_confirmed))}
    // UI-05 F: one inset white list per year (newest year first), each visit a row; a tap opens it as before.
    byYear.forEach{(year,rows)->
        item("year:$year"){Text(year.toString(),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.semantics{heading()}.testTag("medical-year:$year"))}
        item("year-list:$year"){
            // UI-07 E: each visit its own container in a connected grouped list; the row keeps its own click and tag.
            Column(Modifier.fillMaxWidth().testTag("medical-year-list:$year"),verticalArrangement=Arrangement.spacedBy(GroupedGap)) {
                rows.forEachIndexed{i,a-> GroupedItem(i,rows.size){MedicalVisitRow(a,questionCounts[a.id] ?: 0,onVisit)} }
            }
        }
    }
    item("related") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        MedicalHeading(stringResource(R.string.medical_related))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            HubTile(Icons.Outlined.Science,stringResource(R.string.mh_labs_tile),pluralStringResource(R.plurals.mh_lab_draws,drawCount,drawCount)+" · "+stringResource(R.string.lab_group_count,resultCount),"medical-labs",onLabs,Modifier.weight(1f).fillMaxHeight())
            HubTile(Icons.Outlined.Timeline,stringResource(R.string.medical_timeline),null,"medical-history",onHistory,Modifier.weight(1f).fillMaxHeight())
        }
        onVisits?.let{visits->RelatedCard(Icons.AutoMirrored.Outlined.EventNote,stringResource(R.string.mh_visits_tile),"medical-center-visits",visits)}
    } }
    item("bin") { TextButton(onClick=onTrash,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("medical-bin")){Text(stringResource(R.string.medical_bin))} }
}

private fun LazyListScope.filesArea(onVisits:(()->Unit)?,onLabs:()->Unit,onMedicalBackup:(()->Unit)?,onAddFile:()->Unit) {
    item("files-hero") {
        val c=MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth().testTag("medical-files-hero"),shape=Corners.card,color=c.secondaryContainer,contentColor=c.onSecondaryContainer) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(48.dp),shape=Corners.full,color=c.surfaceContainerLowest,contentColor=c.primary) {
                    Box(contentAlignment=Alignment.Center){Icon(Icons.Outlined.Description,null,Modifier.size(26.dp))}
                }
                Text(stringResource(R.string.mh_files_title),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.semantics{heading()})
                Text(stringResource(R.string.mh_files_body),style=MaterialTheme.typography.bodyMedium)
                Button(onAddFile,Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("medical-add-file")) {
                    Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.mh_add_file))
                }
            }
        }
    }
    item("files-by-record") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        MedicalHeading(stringResource(R.string.mh_by_record))
        Column(verticalArrangement=Arrangement.spacedBy(GroupedGap)) {
            val rows=listOfNotNull(onVisits?.let{Triple(Icons.AutoMirrored.Outlined.EventNote,R.string.mh_visit_files to R.string.mh_visit_files_body,"medical-visit-attachments" to it)},
                Triple(Icons.Outlined.Science,R.string.mh_lab_files to R.string.mh_lab_files_body,"medical-lab-attachments" to onLabs))
            rows.forEachIndexed{i,(icon,text,action)->
                GroupedItem(i,rows.size,onClick=action.second,modifier=Modifier.testTag(action.first)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Surface(Modifier.size(40.dp),shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer) {
                            Box(contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)}
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(text.first),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
                            Text(stringResource(text.second),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)
                    }
                }
            }
        }
    } }
    onMedicalBackup?.let{backup->item("files-backup") {
        Surface(backup,Modifier.fillMaxWidth().testTag("medical-complete-backup"),shape=Corners.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.VerifiedUser,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.med_transfer_title),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.mh_backup_body),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)
            }
        }
    }}
    item("files-soon") {
        Surface(Modifier.fillMaxWidth().testTag("medical-files-soon"),shape=Corners.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(16.dp).semantics(mergeDescendants=true){},horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.Info,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.mh_soon_title),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.mh_soon_body),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun MedicalHeading(text:String)=Text(text,style=sectionTitleStyle,modifier=Modifier.semantics{heading()})

/** Sample 05: a tinted tile with an icon, a title and a one-line summary (not a bordered row). */
@Composable private fun HubTile(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,summary:String?,tag:String,onClick:()->Unit,modifier:Modifier=Modifier) {
    Surface(onClick,modifier.heightIn(min=96.dp).testTag(tag),shape=Corners.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
            Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
            summary?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}

@Composable private fun RelatedCard(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,tag:String,onClick:()->Unit) {
    Surface(onClick,Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag),shape=Corners.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp))
            Text(label,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)
        }
    }
}

/**
 * "Add medical file": the file always belongs to an explicit record, so the first step is choosing it. Visits and lab results
 * are listed newest first; nothing is suggested by date or by name. Choosing opens that record's own page, where the related
 * files and the single add action are. A lab result opens the individual result, never a panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AddFileTargetDialog(appointments:List<AppointmentEntity>,labs:List<LabValueEntity>,panels:List<LabPanelEntity>,onVisit:(AppointmentEntity)->Unit,onLab:((LabValueEntity)->Unit)?,onDismiss:()->Unit) {
    var kind by remember{mutableIntStateOf(0)}
    val visits=remember(appointments){appointments.sortedWith(compareByDescending<AppointmentEntity>{it.at_utc}.thenByDescending{it.id})}
    // Lab results are offered per blood test, newest first, the same grouping as the labs page; a file still links to one result.
    val draws=remember(labs,panels){labDraws(labs,panels)}
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().testTag("file-target-dialog")) {
            Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.mh_target_title),Modifier.semantics{heading()})},
                navigationIcon={IconButton(onDismiss,Modifier.testTag("file-target-close")){Icon(Icons.Outlined.Close,stringResource(R.string.mh_target_close))}})},
                containerColor=MaterialTheme.colorScheme.surface) {inner->
                Column(Modifier.padding(inner).fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.mh_target_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(R.string.mh_target_visit,R.string.mh_target_lab).forEachIndexed{i,label->
                            SegmentedButton(kind==i,{kind=i},SegmentedButtonDefaults.itemShape(i,2),Modifier.heightIn(min=48.dp).testTag("file-target-kind:$i")){Text(stringResource(label))}
                        }
                    }
                    if(kind==0) {
                        if(visits.isEmpty())Text(stringResource(R.string.mh_target_no_visits))
                        else LazyColumn(Modifier.fillMaxSize().testTag("file-target-visits"),verticalArrangement=Arrangement.spacedBy(GroupedGap),contentPadding=PaddingValues(bottom=24.dp)) {
                            itemsIndexed(visits,key={_,a->"v:${a.id}"}){i,a->
                                GroupedItem(i,visits.size,onClick={onVisit(a)},modifier=Modifier.testTag("file-target:visit:${a.id}")) {
                                    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                        Text(choiceLabel(a.type.ifBlank{"OTHER"}),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
                                        Text(listOfNotNull(visitWhen(a),a.practitioner?.takeIf{it.isNotBlank()}).joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    } else {
                        if(draws.isEmpty()||onLab==null)Text(stringResource(R.string.mh_target_no_labs))
                        else {
                            Text(stringResource(R.string.mh_target_lab_help),Modifier.testTag("file-target-lab-help"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyColumn(Modifier.fillMaxSize().testTag("file-target-labs"),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                                items(draws,key={d->d.panel?.let{"p:${it.id}"} ?: "g:${d.labs.first().id}"}){d->
                                    val first=d.labs.first();val local=Instant.ofEpochMilli(d.panel?.sampled_utc ?: first.sampled_utc).atZone(ZoneId.of(d.panel?.sampled_zone ?: first.sampled_zone))
                                    val place=(d.panel?.laboratory ?: first.laboratory)?.trim()?.takeIf{it.isNotEmpty()}
                                    val when_=formatDate(local.toLocalDate())+" · "+formatTime(local.toLocalTime())
                                    Column(Modifier.testTag("file-target-draw:${d.panel?.id ?: "g${first.id}"}"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        // One header per blood test with several results; a single result keeps its own date line.
                                        if(d.labs.size>1)Text(listOfNotNull(when_,place,stringResource(R.string.lab_group_count,d.labs.size)).joinToString(" · "),
                                            style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,modifier=Modifier.semantics{heading()})
                                        Column(verticalArrangement=Arrangement.spacedBy(GroupedGap)) {
                                            d.labs.forEachIndexed{i,v->
                                                GroupedItem(i,d.labs.size,onClick={onLab(v)},modifier=Modifier.testTag("file-target:lab:${v.id}")) {
                                                    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                                        Text(analyteLabel(v.analyte_code)+" · "+displayNumber(v.value)+" "+v.unit,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
                                                        if(d.labs.size==1)Text(when_+(place?.let{" · $it"} ?: ""),style=MaterialTheme.typography.bodyMedium)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The visit's own clock; the zone is named only when it is not the phone's (never as an ID). */
@Composable private fun visitWhen(a:AppointmentEntity):String {
    val zone=ZoneId.of(a.at_zone);val local=Instant.ofEpochMilli(a.at_utc).atZone(zone)
    return formatDate(local.toLocalDate())+" · "+formatTime(local.toLocalTime())+
        (if(zone.rules!=ZoneId.systemDefault().rules)" · "+zone.getDisplayName(java.time.format.TextStyle.FULL,currentLocale()) else "")
}

/**
 * UI-05 F: a date tile (month, day, weekday) beside "next visit · in n days", the type and "time · doctor · place", then two
 * inset rows, questions (with the saved count) and the visit pack, both opening the same visit detail as the former button.
 * The pack row shows no status: nothing on this page reads pack records, and none is queried for it.
 */
@Composable private fun NextVisitCard(a:AppointmentEntity,questions:Int,now:Instant,onVisit:(AppointmentEntity)->Unit) {
    val c=MaterialTheme.colorScheme
    val zone=ZoneId.of(a.at_zone);val local=Instant.ofEpochMilli(a.at_utc).atZone(zone)
    val days=java.time.temporal.ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(),local.toLocalDate()).toInt()
    Surface(color=c.secondaryContainer,contentColor=c.onSecondaryContainer,shape=Corners.card,modifier=Modifier.fillMaxWidth().testTag("medical-next:${a.id}")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                val locale=currentLocale()
                Surface(color=c.surfaceContainerLowest,contentColor=c.onSurface,shape=RoundedCornerShape(14.dp),modifier=Modifier.testTag("medical-next-date")) {
                    Column(Modifier.padding(horizontal=12.dp,vertical=8.dp).widthIn(min=48.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(local.month.getDisplayName(java.time.format.TextStyle.SHORT,locale),style=MaterialTheme.typography.labelMedium,color=c.primary)
                        Text(local.dayOfMonth.toString(),style=MaterialTheme.typography.headlineSmall.tnum(),fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(local.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT,locale),style=MaterialTheme.typography.labelSmall)
                    }
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.medical_next_visit)+(if(days>0)" · "+stringResource(R.string.medical_in_days,days) else ""),
                        style=MaterialTheme.typography.labelLarge,modifier=Modifier.semantics{heading()})
                    Text(choiceLabel(a.type.ifBlank{"OTHER"}),style=MaterialTheme.typography.titleLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(listOfNotNull(visitWhen(a),a.practitioner?.takeIf{it.isNotBlank()},a.location?.takeIf{it.isNotBlank()}).joinToString(" · "),
                        style=MaterialTheme.typography.bodyMedium)
                }
            }
            Surface(color=c.surfaceContainerLowest,contentColor=c.onSurface,shape=Corners.list) {
                Column {
                    InsetRow(Icons.AutoMirrored.Outlined.HelpOutline,stringResource(R.string.medical_next_questions,questions),"medical-next-questions"){onVisit(a)}
                    HorizontalDivider(Modifier.padding(start=52.dp),color=c.outlineVariant)
                    InsetRow(Icons.Outlined.Description,stringResource(R.string.medical_next_pack),"medical-next-pack"){onVisit(a)}
                }
            }
        }
    }
}
@Composable private fun InsetRow(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,tag:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=Role.Button,onClick=onClick).padding(horizontal=16.dp).testTag(tag),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
        Text(label,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)
    }
}
/** UI-05 F: one confirmed visit in its year's inset list: type icon, type, "date · doctor or place", note/questions, chevron. */
@Composable private fun MedicalVisitRow(a:AppointmentEntity,questions:Int,onVisit:(AppointmentEntity)->Unit) {
    val c=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=Role.Button){onVisit(a)}.padding(horizontal=16.dp,vertical=12.dp).testTag("medical-visit:${a.id}"),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=CircleShape,color=c.secondaryContainer,contentColor=c.onSecondaryContainer,modifier=Modifier.size(40.dp)) {
            Box(contentAlignment=Alignment.Center){Icon(Icons.AutoMirrored.Outlined.EventNote,null,Modifier.size(20.dp))}}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(choiceLabel(a.type),style=MaterialTheme.typography.titleSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
            Text(listOfNotNull(visitWhen(a),(a.practitioner?.takeIf{it.isNotBlank()} ?: a.location?.takeIf{it.isNotBlank()})).joinToString(" · "),
                style=MaterialTheme.typography.bodyMedium)
            // Read-only summary of what is already saved with the visit.
            val extras=listOfNotNull(a.note?.takeIf{it.isNotBlank()}?.let{stringResource(R.string.medical_has_note)},
                questions.takeIf{it>0}?.let{stringResource(R.string.medical_question_count,it)})
            if(extras.isNotEmpty())Text(extras.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MedicalVisitCard(a:AppointmentEntity,questions:Int,onVisit:(AppointmentEntity)->Unit,onConfirm:((AppointmentEntity)->Unit)?) {
    ElevatedCard(Modifier.fillMaxWidth().testTag("medical-visit:${a.id}").clickable(role=Role.Button){onVisit(a)},shape=Corners.card) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.Top) {
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer){
                Icon(Icons.AutoMirrored.Outlined.EventNote,null,Modifier.padding(8.dp).size(20.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(choiceLabel(a.type),style=MaterialTheme.typography.titleMedium)
                Text(visitWhen(a))
                listOfNotNull(a.practitioner?.takeIf{it.isNotBlank()},a.location?.takeIf{it.isNotBlank()}).forEach{Text(it)}
                // Read-only summary of what is already saved with the visit.
                val extras=listOfNotNull(a.note?.takeIf{it.isNotBlank()}?.let{stringResource(R.string.medical_has_note)},
                    questions.takeIf{it>0}?.let{stringResource(R.string.medical_question_count,it)})
                if(extras.isNotEmpty())Text(extras.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if(a.completed_utc!=null)R.string.visit_completed else R.string.visit_not_confirmed),style=MaterialTheme.typography.labelMedium)
                if(onConfirm!=null)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button({onConfirm(a)},Modifier.heightIn(min=48.dp).testTag("medical-confirm:${a.id}")){Text(stringResource(R.string.visit_confirm))}
                    TextButton({onVisit(a)},Modifier.heightIn(min=48.dp).testTag("medical-open:${a.id}")){Text(stringResource(R.string.medical_edit_or_delete))}
                }
            }
        }
    }
}

/** Drafts stay in memory; failure retains them. A stored instant is not reinterpreted in the device's current zone. */
@Composable fun MedicalVisitDialog(initial:AppointmentEntity?,state:MedicalVisitSaveState,onDismiss:()->Unit,onSave:(AppointmentEntity)->Unit) {
    var zone by remember(initial?.id){mutableStateOf(ZoneId.of(initial?.at_zone ?: ZoneId.systemDefault().id))}
    var chooseZone by remember{mutableStateOf(false)}
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
            TextButton(enabled=!state.saving,onClick={chooseZone=true},modifier=Modifier.testTag("medical-change-zone")){Text(stringResource(R.string.medical_change_zone))}
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
    if(chooseZone)MedicalTimeZoneDialog(zone.id,{chooseZone=false}){id->zone=ZoneId.of(id);offset=null;chooseZone=false}
}

@Composable private fun MedicalTimeZoneDialog(current:String,onDismiss:()->Unit,onChoose:(String)->Unit) {
    var search by remember{mutableStateOf("")}
    val keyboard=LocalSoftwareKeyboardController.current
    val all=remember(current){(ZoneId.getAvailableZoneIds()+current).sorted()}
    val options=remember(all,search){all.filter{it.contains(search.trim(),ignoreCase=true)}}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.medical_change_zone))},text={Column(Modifier.heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth().testTag("medical-zone-search"),label={Text(stringResource(R.string.medical_zone_search))},singleLine=true)
        LazyColumn(Modifier.weight(1f,fill=false).testTag("medical-zone-list")) {
            item("help"){Text(stringResource(R.string.medical_zone_help))}
            items(options,key={it}) { id ->
            TextButton(onClick={keyboard?.hide();onChoose(id)},modifier=Modifier.fillMaxWidth().testTag("medical-zone:$id")){Text(id)}
        } }
    }},confirmButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
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

/** UI-05 F: "Record a past visit" for AppShell's top bar; [onAdd] is the same action the former list button called. */
@Composable fun MedicalAddAction(onAdd:()->Unit) {
    IconButton(onAdd,Modifier.testTag("medical-add")){Icon(Icons.Outlined.Add,stringResource(R.string.medical_add_visit))}
}
