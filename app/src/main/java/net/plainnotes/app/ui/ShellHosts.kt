package net.plainnotes.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.data.ContainerEntity
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.MedicationSnapshot
import net.plainnotes.app.data.RecordEntity
import java.time.Instant

/*
 * UI-02B: the page-owned dialog and transient state that used to be loose variables in NotesApp. Each holder is still
 * created once in NotesApp with the same remember/rememberSaveable as before, so lifetimes are unchanged; the dialogs are
 * still composed where NotesApp composed them (outside the LocalSimpleMode provider), only through these functions.
 */

/** Stock dialogs, opened from the stock page and from the calendar's low-stock card. */
@Stable class StockDialogs {
    var addStock by mutableStateOf<MedicationEntity?>(null)
    var checkStock by mutableStateOf<MedicationEntity?>(null)
    var managePackages by mutableStateOf<MedicationEntity?>(null)
    var restorePackage by mutableStateOf<ContainerEntity?>(null)
    var undoPackage by mutableStateOf<ContainerEntity?>(null)
    var packageInfo by mutableStateOf<ContainerEntity?>(null)
}

@Composable internal fun StockPageDialogs(s: StockDialogs, model: NotesViewModel, extra: NotesViewModel.ExtraState, meds: Map<Long, MedicationEntity>) {
    s.addStock?.let { m -> AddStockDialog(m, { s.addStock = null }) { cap, n, open,source,batch -> model.addContainers(m.id, cap, n, open,source,batch); s.addStock = null } }
    s.checkStock?.let { m -> CheckStockDialog(m, extra.containers, { s.checkStock = null }) { id, v -> model.correctStock(id, v); s.checkStock = null } }
    s.managePackages?.let { m -> PackagesDialog(m, extra.containers, extra.containersWithConsumption, { s.managePackages = null }, { s.packageInfo = it }, voided=extra.voidedContainers, onRestore={ s.restorePackage = it }, onUndo={ s.undoPackage = it }) }
    s.undoPackage?.let { box -> PackageCorrectionConfirmation(false,{s.undoPackage=null},label=meds[box.medication_id]?.let{it.name+" · "+packageLabel(box,it)}) { model.removeMistakenContainer(box.id);s.undoPackage=null } }
    s.restorePackage?.let { box -> PackageCorrectionConfirmation(true,{s.restorePackage=null},label=meds[box.medication_id]?.let{it.name+" · "+packageLabel(box,it)}) { model.restoreVoidedContainer(box.id);s.restorePackage=null } }
    s.packageInfo?.let{box->ContainerInfoDialog(box,{s.packageInfo=null}){source,batch->model.setContainerInfo(box.id,source,batch);s.packageInfo=null}}
}

/** History record dialogs and the batch backfill (its result snackbar may arrive after leaving the page). */
@Stable class HistoryDialogs {
    var editRecord by mutableStateOf<RecordEntity?>(null)
    var referenceEpoch by mutableStateOf<String?>(null)
    var referenceRecord by mutableStateOf<RecordEntity?>(null)
    var deleteRecord by mutableStateOf<RecordEntity?>(null)
    var batch by mutableStateOf(false)
    var batchDone by mutableStateOf<Int?>(null)
}

@Composable internal fun HistoryRecordDialogs(h: HistoryDialogs, model: NotesViewModel, meds: Map<Long, MedicationEntity>) {
    h.editRecord?.let { r -> IntakeDialog(stringResource(if (r.status == "MISSED") R.string.history_backfill else R.string.edit), MedicationSnapshot.decode(r.config_snapshot,r.medication_id)?.medication(r.medication_id), r.actual_dose ?: r.planned_dose ?: if(r.origin=="IMPORT_CHRYSALIDE")0.0 else meds[r.medication_id]?.dose_per_intake ?: 1.0,
        Instant.ofEpochMilli(r.taken_utc ?: r.scheduled_utc ?: System.currentTimeMillis()), { h.editRecord = null },onReference={h.referenceRecord=r}) { t, d, _ -> model.editRecord(r.id, t, d); h.editRecord = null } }
    h.referenceRecord?.let {r->RecordedReferenceDialog(r,h.referenceEpoch,model::recordedReferenceHistory){h.referenceRecord=null}}
    h.deleteRecord?.let { r -> AlertDialog(onDismissRequest = { h.deleteRecord = null }, icon = { Icon(Icons.Outlined.Delete, null) }, text = { Text(stringResource(R.string.history_delete_confirm)) },
        confirmButton = { Button(onClick = { model.deleteRecord(r.id); h.deleteRecord = null }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { h.deleteRecord = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable internal fun HistoryBatchDialog(h: HistoryDialogs, model: NotesViewModel, state: net.plainnotes.app.NotesState, snackbar: SnackbarHostState) {
    if (h.batch) BatchAddDialog(state.medications.filter { it.active && it.needs_review == null }, { m ->
        state.schedules[m.id]?.takeIf { it.kind == net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS && it.interval == 1 && it.times.isNotEmpty() }?.times ?: TWICE_DAILY
    }, { h.batch = false }) { id, from, to, times, d -> h.batch = false; model.backfill(id, from, to, times, d) { h.batchDone = it } }
    val batchText = h.batchDone?.let { stringResource(R.string.batch_done, it) }
    LaunchedEffect(batchText) { batchText?.let { snackbar.showSnackbar(it); h.batchDone = null } }
}

/** Lab add/edit, opened from the labs page and from a lab detail in the medical records. */
@Stable class LabDialogs {
    var labEdit by mutableStateOf<LabValueEntity?>(null)
    var labNew by mutableStateOf(false)
    var batchSaving by mutableStateOf(false)
    var batchFailed by mutableStateOf(false)
    /** LAB-02: the open lab panel, and results waiting for the "save as one blood test" confirmation. */
    var panel by mutableStateOf<Long?>(null)
    var saveAs by mutableStateOf<List<LabValueEntity>?>(null)
}

@Composable internal fun LabEditDialog(l: LabDialogs, model: NotesViewModel, labs: List<LabValueEntity> = emptyList(), retained:LabUiState?=null) {
    // LAB-01: "add" opens the batch form; editing one result keeps the single dialog, with the split hint when it is shown in a draw card.
    if (l.labNew && l.labEdit == null) LabBatchDialog({ if (!l.batchSaving) { l.labNew = false; l.batchFailed = false;retained?.discardDraft() } }, submit@{ values, estimate ->
        if(l.batchSaving)return@submit
        val token=retained?.token
        l.batchSaving = true; l.batchFailed = false
        model.saveLabs(values, estimate) { ok ->
            // A result from a discarded/locked session cannot clear a newer draft (ABA).
            if(retained==null || retained.token===token) {
                l.batchSaving = false
                if(ok){l.labNew=false;retained?.discardDraft()}else l.batchFailed=true
            }
        }
    }, saving = l.batchSaving, failed = l.batchFailed, draft=retained?.draft)
    else if (l.labEdit != null) LabDialog(l.labEdit, { l.labNew = false; l.labEdit = null }, { model.saveLab(it); l.labNew = false; l.labEdit = null },onSaveWithEstimate={v,estimate->model.saveLab(v,estimate);l.labNew=false;l.labEdit=null},
        inGroup = l.labEdit!!.let { e -> e.panel_id != null || labGroups(labs.filter { it.panel_id == null }).any { g -> g.size > 1 && g.any { it.id == e.id } } })
    else if (l.labNew && l.labEdit == null) { l.labNew = false }
}

/** LAB-02: a lab panel's detail and the "save as one blood test" confirmation (docs/design/lab-panel-adr.md §10). */
@Composable internal fun LabPanelHost(l: LabDialogs, model: NotesViewModel, extra: NotesViewModel.ExtraState) {
    l.panel?.let { id -> extra.labPanels.firstOrNull { it.id == id }?.let { p ->
        val files=net.plainnotes.app.attachments.ui.rememberRecordFiles(model,
            net.plainnotes.app.data.MedicalAttachmentOperations.Target(net.plainnotes.app.data.MedicalAttachmentOperations.Kind.LAB_PANEL,p.id,java.util.UUID.fromString(p.uuid)))
        val label=stringResource(R.string.lab_panel_title)
        val scope=stringResource(R.string.med03_whole_draw,extra.labs.count {it.panel_id==p.id})
        LabPanelDialog(p, extra.labs.filter { it.panel_id == id }.sortedBy { v -> ANALYTES.indexOfFirst { it.code == v.analyte_code }.let { if (it < 0) ANALYTES.size else it } },
            { l.panel = null }, { time, lab, note -> model.saveLabPanel(p, time, lab, note) }, { l.labEdit = it }, { model.removeFromLabPanel(it) },
            { values, estimate -> model.addToLabPanel(id, values, estimate) }, { l.panel = null; model.deleteLabPanel(id) },
            files=files?.let {f->{net.plainnotes.app.attachments.ui.RecordFilesSection(f,label,scopeNote=scope)}},
            overlay=files?.let {f->{net.plainnotes.app.attachments.ui.RecordFilesViewer(f,label)}})
    } ?: run { l.panel = null } }
    l.saveAs?.let { g -> LabSaveAsPanelDialog(g, { l.saveAs = null }) { l.saveAs = null; model.groupAsLabPanel(g) {id->l.panel=id} } }
}

/** Medical visit editor and lab detail, used by the medical records and the visit detail. */
@Stable class MedicalDialogs {
    var editing by mutableStateOf(false)
    var edit by mutableStateOf<AppointmentEntity?>(null)
    /** The open lab result page: always an individual LabValue ID, never a panel ID. */
    var lab by mutableStateOf<Long?>(null)
}

@Composable internal fun MedicalVisitEditor(m: MedicalDialogs, model: NotesViewModel, save: net.plainnotes.app.medical.MedicalVisitSaveState) {
    if(m.editing)MedicalVisitDialog(m.edit,save,{if(!save.saving){m.editing=false;m.edit=null;model.clearMedicalVisitSave()}}){model.saveMedicalVisit(it,m.edit)}
}

/** The lab result page, with its related files when the guarded attachment capability is on. One window; no nested dialogs. */
@Composable internal fun MedicalLabDetail(m: MedicalDialogs, l: LabDialogs, extra: NotesViewModel.ExtraState, model:NotesViewModel) {
    m.lab?.let {id->
        extra.labs.firstOrNull {it.id==id}?.let {v->
            val label=analyteLabel(v.analyte_code)
            val files=net.plainnotes.app.attachments.ui.rememberRecordFiles(model,
                net.plainnotes.app.data.MedicalAttachmentOperations.Target(net.plainnotes.app.data.MedicalAttachmentOperations.Kind.LAB,v.id))
            // The draw this result belongs to, from the same grouping the labs list uses (a real panel, or a display group).
            val draw=labDraws(extra.labs,extra.labPanels).firstOrNull {d->d.labs.any {it.id==id}}
            val panel=draw?.panel
            val panelFiles=panel?.let {p->net.plainnotes.app.attachments.ui.rememberRecordFiles(model,
                net.plainnotes.app.data.MedicalAttachmentOperations.Target(net.plainnotes.app.data.MedicalAttachmentOperations.Kind.LAB_PANEL,p.id,java.util.UUID.fromString(p.uuid),v.id))}
            val scope=stringResource(R.string.med03_only_result)
            val drawScope=stringResource(R.string.med03_whole_draw,draw?.labs?.size ?: 0)
            LabDetailFullscreen(v,extra.labContexts.filter {it.lab_id==id},{m.lab=null},{m.lab=null;l.labEdit=v},
                files={
                    panelFiles?.let {f->net.plainnotes.app.attachments.ui.RecordFilesSection(f,stringResource(R.string.lab_panel_title),scopeNote=drawScope)}
                    files?.let {f->net.plainnotes.app.attachments.ui.RecordFilesSection(f,label,scopeNote=scope)}
                },
                overlay={
                    panelFiles?.let {f->net.plainnotes.app.attachments.ui.RecordFilesViewer(f,stringResource(R.string.lab_panel_title))}
                    files?.let {f->net.plainnotes.app.attachments.ui.RecordFilesViewer(f,label)}
                },
                draw=draw,onOpenResult={m.lab=it.id},onOpenDraw=draw?.panel?.let {p->{m.lab=null;l.panel=p.id}})
        }
    }
}

/** Medications page: archive confirmation. */
@Stable class MedicationDialogs { var archive by mutableStateOf<MedicationEntity?>(null) }

@Composable internal fun ArchiveMedicationDialog(d: MedicationDialogs, model: NotesViewModel) {
    d.archive?.let { m ->
        AlertDialog(onDismissRequest = { d.archive = null }, icon = { Icon(Icons.Outlined.Archive, null) }, title = { Text(m.name) }, text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = { Button(onClick = { model.delete(m.id); d.archive = null }) { Text(stringResource(R.string.yes)) } },
            dismissButton = { TextButton(onClick = { d.archive = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

/** Well-being page: check-in item manager and the summary export (the latter was, and stays, saveable). */
@Stable class WellbeingDialogs(summaryState: MutableState<Boolean>) {
    var manageItems by mutableStateOf(false)
    var summary by summaryState
}
@Composable internal fun rememberWellbeingDialogs(): WellbeingDialogs {
    val summary = rememberSaveable { mutableStateOf(false) }
    return remember(summary) { WellbeingDialogs(summary) }
}
