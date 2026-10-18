package net.plainnotes.app.ui

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
    h.editRecord?.let { r -> IntakeDialog(stringResource(if (r.status == "MISSED") R.string.history_backfill else R.string.edit), MedicationSnapshot.decode(r.config_snapshot,r.medication_id)?.medication(r.medication_id), r.actual_dose ?: r.planned_dose ?: meds[r.medication_id]?.dose_per_intake ?: 1.0,
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
}

@Composable internal fun LabEditDialog(l: LabDialogs, model: NotesViewModel, labs: List<LabValueEntity> = emptyList()) {
    // LAB-01: "add" opens the batch form; editing one result keeps the single dialog, with the split hint when it is shown in a draw card.
    if (l.labNew && l.labEdit == null) LabBatchDialog({ if (!l.batchSaving) { l.labNew = false; l.batchFailed = false } }, { values, estimate ->
        l.batchSaving = true; l.batchFailed = false
        model.saveLabs(values, estimate) { ok -> l.batchSaving = false; if (ok) l.labNew = false else l.batchFailed = true }
    }, saving = l.batchSaving, failed = l.batchFailed)
    else if (l.labEdit != null) LabDialog(l.labEdit, { l.labNew = false; l.labEdit = null }, { model.saveLab(it); l.labNew = false; l.labEdit = null },onSaveWithEstimate={v,estimate->model.saveLab(v,estimate);l.labNew=false;l.labEdit=null},
        inGroup = l.labEdit!!.let { e -> labGroups(labs).any { g -> g.size > 1 && g.any { it.id == e.id } } })
}

/** Medical visit editor and lab detail, used by the medical records and the visit detail. */
@Stable class MedicalDialogs {
    var editing by mutableStateOf(false)
    var edit by mutableStateOf<AppointmentEntity?>(null)
    var lab by mutableStateOf<Long?>(null)
}

@Composable internal fun MedicalVisitEditor(m: MedicalDialogs, model: NotesViewModel, save: net.plainnotes.app.medical.MedicalVisitSaveState) {
    if(m.editing)MedicalVisitDialog(m.edit,save,{if(!save.saving){m.editing=false;m.edit=null;model.clearMedicalVisitSave()}}){model.saveMedicalVisit(it,m.edit)}
}

@Composable internal fun MedicalLabDetail(m: MedicalDialogs, l: LabDialogs, extra: NotesViewModel.ExtraState) {
    m.lab?.let{id->extra.labs.firstOrNull{it.id==id}?.let{v->LabDetailDialog(v,extra.labContexts.filter{it.lab_id==id},{m.lab=null}){m.lab=null;l.labEdit=v}}}
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
