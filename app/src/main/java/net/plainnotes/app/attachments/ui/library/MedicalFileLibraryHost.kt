package net.plainnotes.app.attachments.ui.library

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.attachments.ui.*
import net.plainnotes.app.data.*
import net.plainnotes.app.data.MedicalAttachmentOperations as Ops
import net.plainnotes.app.runtime.RuntimeLibrarySession
import net.plainnotes.app.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Real product host. Only the existing ViewModel/runtime integration may construct its gateway. */
@Composable internal fun MedicalFileLibraryHost(model:NotesViewModel,appointments:List<AppointmentEntity>,labs:List<LabValueEntity>,panels:List<LabPanelEntity>,
    onRecord:(Ops.Target)->Unit,onNewVisit:((AppointmentEntity)->Unit)->Unit,onNewDraw:((LabPanelEntity)->Unit)->Unit,
    onPrepare:()->Unit,onBackup:()->Unit,chooseFor:Ops.Target?=null,onChosen:()->Unit={}) {
    val runtime by model.libraryRuntime.state.collectAsState()
    if(runtime !is RuntimeLibrarySession.State.Ready) {Text(stringResource(R.string.library_scope_changed));return}
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope()
    val controller=remember(model,owner) {runCatching {model.medicalFileLibrary(context,owner,scope)}.getOrNull()}
    if(controller==null) {Text(stringResource(R.string.operation_error));return}
    val add=rememberAttachmentPicker(model,controller.files,stringResource(R.string.med03_default_file))
    DisposableEffect(controller) {onDispose {controller.close()}}
    LaunchedEffect(controller) {controller.refresh()}
    MedicalFileLibraryContent(controller,add,appointments,labs,panels,onRecord,onNewVisit,onNewDraw,onPrepare,onBackup,
        onSaveDraw={group,done->model.confirmDisplayedDraw(group,done)},chooseFor=chooseFor,onChosen=onChosen)
}

private data class LinkConfirmation(val document:Ops.Document,val target:Ops.Target,val label:String,val unlink:Ops.Relationship?=null)

/** Same production components and controller in native UI and bounded component regressions. */
@Composable internal fun MedicalFileLibraryContent(controller:MedicalFileLibraryController,onAdd:()->Unit,
    appointments:List<AppointmentEntity>,labs:List<LabValueEntity>,panels:List<LabPanelEntity>,onRecord:(Ops.Target)->Unit,
    onNewVisit:((AppointmentEntity)->Unit)->Unit,onNewDraw:((LabPanelEntity)->Unit)->Unit,onPrepare:()->Unit,onBackup:()->Unit,
    onSaveDraw:(List<LabValueEntity>,(LabPanelEntity?)->Unit)->Unit,chooseFor:Ops.Target?=null,onChosen:()->Unit={}) {
    val state by controller.state.collectAsState();val fileState by controller.files.state.collectAsState()
    val scope=rememberCoroutineScope();val context=LocalContext.current
    var query by remember {mutableStateOf("")};var type by remember {mutableStateOf<MedicalAttachmentsController.FileKind?>(null)}
    var unlinked by remember {mutableStateOf(false)};var bin by remember {mutableStateOf(false)}
    var picker by remember {mutableStateOf(false)}
    var toSortOnly by remember {mutableStateOf(false)};var categoryFilter by remember {mutableStateOf<FileCategory?>(null)}
    var editInfo by remember {mutableStateOf(false)};var afterImportDetail by remember {mutableStateOf<Ops.Detail?>(null)}
    var confirmation by remember {mutableStateOf<LinkConfirmation?>(null)}
    var recycle by remember {mutableStateOf<Ops.Document?>(null)}
    var draft by remember {mutableStateOf<ImportDraft?>(null)}
    var chosenTarget by remember {mutableStateOf<Ops.Target?>(null)}
    var afterImportTarget by remember {mutableStateOf<Ops.Target?>(null)}
    var createAfterImport by remember {mutableStateOf(false)}
    var createRecord by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val thumbs by controller.thumbnails.state.collectAsState()
    val selected=state.documents.firstOrNull {it.document==state.selected}
    val canWrite=controller.canWrite()
    val chosenLabel=chooseFor?.let {targetLabel(it,appointments,labs,panels)}.orEmpty()
    val caps=LibraryCapabilities(standaloneImport=true,search=true,categories=true,bidirectionalLinks=true,panelLinks=true,chooseExisting=true,recycle=true)
    fun request(target:Ops.Target,document:Ops.Document,label:String) {
        scope.launch {try {val frozen=controller.resolve(target);confirmation=LinkConfirmation(document,frozen,label)}catch(_:Exception){error=true}}
    }
    val defaultFile=stringResource(R.string.med03_selected_file)
    LaunchedEffect(fileState.import) {
        if(fileState.import is MedicalAttachmentsController.Import.Confirming && draft==null)
            draft=ImportDraft(defaultFile,(fileState.import as MedicalAttachmentsController.Import.Confirming).title)
        if(fileState.import !is MedicalAttachmentsController.Import.Confirming)draft=null
        if(fileState.import is MedicalAttachmentsController.Import.Saved)controller.refresh()
    }
    LaunchedEffect(fileState.receipt,state.documents) {
        val receipt=fileState.receipt ?: return@LaunchedEffect
        if(fileState.import !is MedicalAttachmentsController.Import.Saved)return@LaunchedEffect
        val document=state.documents.firstOrNull {it.document==receipt.document} ?: return@LaunchedEffect
        val target=afterImportTarget;val detail=afterImportDetail;val create=createAfterImport
        afterImportTarget=null;afterImportDetail=null;createAfterImport=false
        fun proceed() {
            if(target!=null)controller.relate(document,target)
            if(create) {controller.select(document.document);createRecord=true}
        }
        // The file is already saved; its typed details are a separate small write, done first so the link write that may follow is not blocked.
        if(detail!=null)controller.saveDetail(document,detail,::proceed) else proceed()
    }
    LaunchedEffect(state.outcome) {
        if(chooseFor!=null && state.outcome in setOf(MedicalFileLibraryController.Outcome.COMMITTED,MedicalFileLibraryController.Outcome.LIST_STALE))onChosen()
    }
    BackHandler(selected!=null || bin || unlinked || toSortOnly || categoryFilter!=null || fileState.viewer!=MedicalAttachmentsController.Viewer.Closed) {
        when {fileState.viewer!=MedicalAttachmentsController.Viewer.Closed->controller.files.closeViewer();selected!=null->controller.select(null);else->{bin=false;unlinked=false;toSortOnly=false;categoryFilter=null;query="";type=null}}
    }
    @Composable fun summary(d:Ops.Document):DocumentSummary {
        val x=state.details[d.document]
        val category=x?.category?.let {runCatching {FileCategory.valueOf(it)}.getOrNull()}
        return DocumentSummary(d.document,d.title,d.fileKind(),if(d.linkCount==0)LinkState.Unlinked else LinkState.Linked(d.linkCount),category,
            d.importedUtc?.let {formatMediumDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())},
            x?.date?.let {runCatching {formatMediumDate(LocalDate.parse(it))}.getOrNull()},x?.institution,!state.detailsFailed)
    }
    val categoryNames=FileCategory.entries.associateWith {stringResource(it.label())}
    Column(Modifier.fillMaxSize().testTag("medical-file-library-host")) {
        if(state.revoked)Text(stringResource(R.string.library_scope_changed))
        else {
            if(state.loading || state.outcome==MedicalFileLibraryController.Outcome.WORKING)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.detailsFailed)Text(stringResource(R.string.med04_details_unavailable),Modifier.padding(16.dp).testTag("library-details-unavailable"),style=MaterialTheme.typography.bodySmall)
            if(state.detailOutcome!=MedicalFileLibraryController.DetailOutcome.NONE) {
                Text(stringResource(when(state.detailOutcome) {
                    MedicalFileLibraryController.DetailOutcome.SAVED->R.string.med04_detail_saved
                    MedicalFileLibraryController.DetailOutcome.CONFLICT->R.string.file_info_conflict
                    MedicalFileLibraryController.DetailOutcome.CHECKED->R.string.file_info_checked
                    else->R.string.med04_detail_failed
                }),
                    Modifier.padding(16.dp).semantics {liveRegion=LiveRegionMode.Polite}.testTag("library-detail-result"))
                TextButton(controller::dismissDetailOutcome,Modifier.testTag("library-detail-result-dismiss")) {Text(stringResource(R.string.attach_dismiss))}
            }
            if(state.failed || error) {
                Text(stringResource(R.string.operation_error),color=MaterialTheme.colorScheme.error)
                TextButton({error=false;controller.refresh()}) {Text(stringResource(R.string.attach_retry))}
            }
            if(state.outcome!=MedicalFileLibraryController.Outcome.IDLE && state.outcome!=MedicalFileLibraryController.Outcome.WORKING) {
                Text(stringResource(when(state.outcome) {
                    MedicalFileLibraryController.Outcome.COMMITTED->R.string.med03_relation_done
                    MedicalFileLibraryController.Outcome.LIST_STALE->R.string.med03_operation_stale
                    MedicalFileLibraryController.Outcome.NOT_SAVED->R.string.med03_relation_failed
                    MedicalFileLibraryController.Outcome.CHECKED->R.string.med03_state_checked
                    else->R.string.med03_relation_unknown
                }),Modifier.padding(16.dp).testTag("library-operation-result"))
                if(state.outcome==MedicalFileLibraryController.Outcome.UNKNOWN)TextButton(controller::checkStatus,Modifier.testTag("library-operation-check")) {Text(stringResource(R.string.attach_check))}
                else TextButton(controller::dismissOutcome) {Text(stringResource(R.string.attach_dismiss))}
            }
            if(fileState.import !in setOf(MedicalAttachmentsController.Import.Idle,MedicalAttachmentsController.Import.Cancelled) && fileState.import !is MedicalAttachmentsController.Import.Confirming) {
                MedicalAttachmentsSection(fileState.copy(files=MedicalAttachmentsController.Files.Ready(emptyList())),stringResource(R.string.mh_files_title),
                    onAdd={},onConfirm={},onCancelImport=controller.files::cancelImport,onCheckStatus=controller.files::checkStatus,onDismissMessage=controller.files::dismissImportMessage,
                    onRetry=controller::refresh,onOpen={},statusOnly=true)
            }
            if(selected!=null) {
                val links=state.links.map {r->DocumentLink(recordTarget(r.target,appointments,labs,panels))}
                if(state.linksLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(state.linksFailed)Text(stringResource(R.string.med03_unknown_links),Modifier.padding(16.dp))
                MedicalFileDetailPage(DocumentDetail(summary(selected),links,selected.bytes?.let {Formatter.formatShortFileSize(context,it)},selected.state=="TRASHED",!state.linksFailed && !state.linksLoading),caps,
                    preview={
                        val active=selected.state=="ACTIVE"
                        LaunchedEffect(selected.document) {if(active)controller.thumbnails.request(selected.document,selected.fileKind(),FileThumbnails.Size.PAGE)}
                        Box(Modifier.fillMaxSize().then(if(active && selected.fileKind()!=MedicalAttachmentsController.FileKind.OTHER)
                            Modifier.clickable(onClickLabel=stringResource(R.string.med05_open_viewer),role=Role.Button) {controller.preview(selected)}.testTag("file-detail-preview") else Modifier)) {
                            PagePreview(selected.fileKind(),thumbs[FileThumbnails.Key(selected.document,FileThumbnails.Size.PAGE)],active)
                        }
                    },onBack={controller.select(null)},onViewFull={controller.preview(selected)},
                    onAddLink={if(canWrite)picker=true},onUnlink={link->
                        if(canWrite)state.links.firstOrNull {sameTarget(link.target,it.target)}?.let {r->confirmation=LinkConfirmation(selected,r.target,link.target.label,r)}
                    },onOpenRecord={target->state.links.firstOrNull {sameTarget(target,it.target)}?.let {onRecord(it.target)}},
                    onRestore={if(canWrite)recycle=selected},modifier=Modifier.weight(1f),operationsEnabled=canWrite,
                    onEditInfo=if(!state.detailsFailed && selected.state=="ACTIVE") ({editInfo=true}) else null)
                if(selected.state=="ACTIVE")TextButton({recycle=selected},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("file-detail-recycle"),enabled=canWrite) {Text(stringResource(R.string.lib_bin))}
            } else if(!state.loading) {
                val available=state.documents.filter {if(bin)it.state=="TRASHED" else it.state=="ACTIVE"}
                fun categoryOf(d:Ops.Document)=state.details[d.document]?.category?.let {runCatching {FileCategory.valueOf(it)}.getOrNull()}
                fun needsSorting(d:Ops.Document)=categoryOf(d)==null || state.details[d.document]?.date==null
                fun matches(d:Ops.Document):Boolean {
                    val q=query.trim();if(q.isEmpty())return true
                    return d.title.contains(q,true) || state.details[d.document]?.institution?.contains(q,true)==true || categoryOf(d)?.let {categoryNames.getValue(it).contains(q,true)}==true
                }
                val filtered=available.filter {(!unlinked || it.linkCount==0) && (!toSortOnly || needsSorting(it)) && (categoryFilter==null || categoryOf(it)==categoryFilter) &&
                    (type==null || it.fileKind()==type) && matches(it)}
                val counts=if(state.detailsFailed)emptyMap() else available.mapNotNull(::categoryOf).groupingBy {it}.eachCount()
                if(bin || unlinked || toSortOnly || categoryFilter!=null)TextButton({bin=false;unlinked=false;toSortOnly=false;categoryFilter=null},Modifier.testTag("library-clear-filter")) {Text(stringResource(R.string.lib_all,available.size))}
                MedicalFileLibraryScreen(LibraryState(filtered.map {summary(it)},counts,available.count {it.linkCount==0},available.size,
                        toSort=if(state.detailsFailed || bin)null else available.count(::needsSorting)),caps,
                    onAdd={if(canWrite)onAdd()},onPrepare=onPrepare,onOpen={item->
                        val document=available.first {it.document==item.document}
                        if(chooseFor==null)controller.select(item.document) else request(chooseFor,document,chosenLabel)
                    },onUnsorted={toSortOnly=true;unlinked=false;bin=false;categoryFilter=null},onCategory={categoryFilter=it;toSortOnly=false;unlinked=false;bin=false},
                    onShowAll={unlinked=false;toSortOnly=false;categoryFilter=null;query="";type=null},onBackup=onBackup,onBin={bin=true;unlinked=false;toSortOnly=false;categoryFilter=null},query=query,onQuery={query=it.take(256)},
                    modifier=Modifier.weight(1f),typeCounts=available.groupingBy {it.fileKind()}.eachCount(),typeFilter=type,onType={type=it},busy=!canWrite,thumbs=thumbs,onThumb={controller.thumbnails.request(it.document,it.kind,FileThumbnails.Size.ROW)},
                    onUnlinked={unlinked=true;toSortOnly=false;categoryFilter=null;bin=false},categoryFilter=categoryFilter)
            }
        }
    }
    MedicalAttachmentViewer(fileState.viewer,controller.files::closeViewer,targetLabel=stringResource(R.string.mh_files_title),onPage=controller.files::showPage)
    draft?.let {d->FileWindow("",{controller.files.cancelImport()}) {
        Column {Text(stringResource(R.string.med03_type_unknown),Modifier.padding(16.dp))
        AddDocumentDetails(d,caps,onChange={if(it.choice!=draft?.choice || it.chosen!=draft?.chosen)chosenTarget=null;draft=it},onChoose={picker=true},onConfirm={
            if(canWrite && (d.choice !in setOf(LinkChoice.EXISTING_VISIT,LinkChoice.EXISTING_RECORD) || chosenTarget!=null)) {afterImportTarget=chosenTarget;createAfterImport=d.choice==LinkChoice.NEW_RECORD
            afterImportDetail=Ops.Detail(d.category?.name,d.date?.toString(),Ops.Detail.institutionOf(d.institution)).takeUnless {it.empty};controller.files.confirmImport(d.title)}
        },onCancel=controller.files::cancelImport)
        }
    }}
    if(editInfo && selected!=null)FileWindow("",{editInfo=false}) {
        FileInfoEditor(state.details[selected.document],onSave={detail->editInfo=false;controller.saveDetail(selected,detail)},onCancel={editInfo=false},
            initialTitle=selected.title,onSaveFileInfo={title,detail->editInfo=false;controller.saveFileInfo(selected,title,detail)})
    }
    if(picker)MedicalFileTargetPicker(appointments,labs,panels,onDismiss={picker=false},onSaveDraw=onSaveDraw) {target,label->
        picker=false
        if(draft!=null)scope.launch {try {chosenTarget=controller.resolve(target);draft=draft?.copy(chosen=recordTargetValue(target,label,panels,labs))}catch(_:Exception){error=true}}
        else selected?.let {request(target,it,label)}
    }
    confirmation?.let {c->AlertDialog(onDismissRequest={confirmation=null},title={Text(stringResource(if(c.unlink==null)R.string.lib_detail_add_link else R.string.attach_unlink_title))},
        text={Text(if(c.unlink==null)stringResource(R.string.med03_link_confirm,c.document.title,c.label) else stringResource(R.string.attach_unlink_explain,c.document.title))},
        confirmButton={TextButton({confirmation=null;controller.relate(c.document,c.target,c.unlink)},Modifier.testTag("library-link-confirm"),enabled=canWrite){Text(stringResource(R.string.save))}},
        dismissButton={TextButton({confirmation=null}){Text(stringResource(R.string.cancel))}})}
    recycle?.let {d->AlertDialog(onDismissRequest={recycle=null},title={Text(d.title)},text={Text(stringResource(if(d.state=="TRASHED")R.string.med03_restore_confirm else R.string.med03_bin_confirm))},
        confirmButton={TextButton({recycle=null;controller.recycle(d,d.state=="TRASHED")},Modifier.testTag("library-recycle-confirm"),enabled=canWrite){Text(stringResource(R.string.save))}},
        dismissButton={TextButton({recycle=null}){Text(stringResource(R.string.cancel))}})}
    if(createRecord)AlertDialog(onDismissRequest={createRecord=false},title={Text(stringResource(R.string.lib_link_new))},text={Text(stringResource(R.string.med03_new_prompt))},
        confirmButton={Column {
            TextButton({createRecord=false;onNewVisit {a->controller.state.value.documents.firstOrNull {it.document==controller.state.value.selected}?.let {request(Ops.Target(Ops.Kind.APPOINTMENT,a.id),it,a.type)}}}){Text(stringResource(R.string.medical_add_visit))}
            TextButton({createRecord=false;onNewDraw {p->controller.state.value.documents.firstOrNull {it.document==controller.state.value.selected}?.let {request(Ops.Target(Ops.Kind.LAB_PANEL,p.id,UUID.fromString(p.uuid)),it,p.laboratory.orEmpty())}}}){Text(stringResource(R.string.lab_batch_title))}
        }},dismissButton={TextButton({createRecord=false}){Text(stringResource(R.string.cancel))}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FileWindow(title:String,onBack:()->Unit,content:@Composable ()->Unit) {
    Dialog(onBack,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start=4.dp,end=16.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    NavBackButton(onBack,"file-window-back")
                    if(title.isNotEmpty())Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(start=4.dp).weight(1f).semantics{heading()})
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {content()}
            }
        }
    }
}

@Composable private fun recordTarget(t:Ops.Target,a:List<AppointmentEntity>,l:List<LabValueEntity>,p:List<LabPanelEntity>)=
    recordTargetValue(t,targetLabel(t,a,l,p),p,l)
private fun recordTargetValue(t:Ops.Target,label:String,p:List<LabPanelEntity>,l:List<LabValueEntity>):RecordTarget=when(t.kind) {
    Ops.Kind.APPOINTMENT->RecordTarget.Visit(t.id,label)
    Ops.Kind.LAB->RecordTarget.LabValue(t.id,label)
    Ops.Kind.LAB_PANEL->RecordTarget.LabPanel(t.id,label,l.count {it.panel_id==t.id})
}
private fun sameTarget(v:RecordTarget,t:Ops.Target)=when(v) {
    is RecordTarget.Visit->t.kind==Ops.Kind.APPOINTMENT && t.id==v.id
    is RecordTarget.LabValue->t.kind==Ops.Kind.LAB && t.id==v.id
    is RecordTarget.LabPanel->t.kind==Ops.Kind.LAB_PANEL && t.id==v.id
}
@Composable private fun targetLabel(t:Ops.Target,a:List<AppointmentEntity>,l:List<LabValueEntity>,p:List<LabPanelEntity>):String=when(t.kind) {
    Ops.Kind.APPOINTMENT->a.firstOrNull {it.id==t.id}?.let {choiceLabel(it.type)+" · "+formatDateTime(Instant.ofEpochMilli(it.at_utc))} ?: stringResource(R.string.ref2_unknown)
    Ops.Kind.LAB->l.firstOrNull {it.id==t.id}?.let {analyteLabel(it.analyte_code)+" · "+formatDateTime(Instant.ofEpochMilli(it.sampled_utc))} ?: stringResource(R.string.ref2_unknown)
    Ops.Kind.LAB_PANEL->p.firstOrNull {it.id==t.id && (t.expectedUuid==null || it.uuid==t.expectedUuid.toString())}?.let {
        stringResource(R.string.med03_whole_draw,l.count {v->v.panel_id==it.id})+" · "+formatDateTime(Instant.ofEpochMilli(it.sampled_utc))
    } ?: stringResource(R.string.ref2_unknown)
}

/** The primary laboratory choice is a real draw. A display group requires a separate member confirmation. */
@Composable private fun MedicalFileTargetPicker(appointments:List<AppointmentEntity>,labs:List<LabValueEntity>,panels:List<LabPanelEntity>,
    onDismiss:()->Unit,onSaveDraw:(List<LabValueEntity>,(LabPanelEntity?)->Unit)->Unit,onChoose:(Ops.Target,String)->Unit) {
    var kind by remember {mutableIntStateOf(1)};var group by remember {mutableStateOf<List<LabValueEntity>?>(null)};var saving by remember {mutableStateOf(false)}
    FileWindow(stringResource(R.string.med03_link_target_title),onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Column {
                listOf(R.string.mh_target_visit,R.string.lab_panel_title,R.string.med03_only_result).forEachIndexed {i,label->
                    FilterChip(kind==i,{kind=i},{Text(stringResource(label))},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("library-target-kind:$i"))
                }
            }
            LazyColumn(Modifier.fillMaxSize().testTag("library-targets"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(kind==0)for(a in appointments.sortedByDescending {it.at_utc}) item("visit:${a.id}") {
                    val t=Ops.Target(Ops.Kind.APPOINTMENT,a.id);val label=targetLabel(t,appointments,labs,panels)
                    OutlinedButton({onChoose(t,label)},Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("library-target:visit:${a.id}")) {Text(label)}
                }
                if(kind==1) {
                    for(p in panels.sortedByDescending {it.sampled_utc}) item("panel:${p.id}") {
                        val t=Ops.Target(Ops.Kind.LAB_PANEL,p.id,UUID.fromString(p.uuid));val label=targetLabel(t,appointments,labs,panels)
                        OutlinedButton({onChoose(t,label)},Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("library-target:panel:${p.id}")) {Text(label)}
                    }
                    for(d in labDraws(labs,panels).filter {it.panel==null}) item("display:${d.labs.first().id}") {
                        OutlinedButton({group=d.labs},Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("library-target:save-draw:${d.labs.first().id}")) {
                            Text(stringResource(R.string.lab_panel_save_as)+" · "+stringResource(R.string.med03_whole_draw,d.labs.size))
                        }
                    }
                }
                if(kind==2)for(v in labs.sortedByDescending {it.sampled_utc}) item("lab:${v.id}") {
                    val t=Ops.Target(Ops.Kind.LAB,v.id);val label=targetLabel(t,appointments,labs,panels)
                    OutlinedButton({onChoose(t,label)},Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("library-target:lab:${v.id}")) {Text(label)}
                }
                item("help") {
                    val available=when(kind){0->appointments.size;1->labDraws(labs,panels).size;else->labs.size}
                    Text(stringResource(if(available==0)R.string.med03_no_target else R.string.med03_select_target),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    group?.let {g->LabSaveAsPanelDialog(g,{if(!saving)group=null}) {
        if(!saving) {saving=true;onSaveDraw(g) {p->saving=false;if(p!=null) {group=null;onChoose(Ops.Target(Ops.Kind.LAB_PANEL,p.id,UUID.fromString(p.uuid)),p.laboratory.orEmpty())}}}
    }}
}
