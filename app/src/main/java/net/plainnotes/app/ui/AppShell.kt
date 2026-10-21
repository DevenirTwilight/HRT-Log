package net.plainnotes.app.ui
import net.plainnotes.app.data.MedicationSnapshot

import androidx.core.content.edit
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.domain.TimelineEntry
import net.plainnotes.app.pk.CalibrationMode
import java.time.Instant
import java.time.LocalDate

enum class Destination(val title: Int, val icon: ImageVector, val ready: Boolean) {
    CALENDAR(R.string.calendar, Icons.Outlined.CalendarMonth, true),
    TIMELINE(R.string.timeline, Icons.Outlined.Timeline, true),
    MEDICAL(R.string.medical_records, Icons.Outlined.FolderOpen, true),
    VISITS(R.string.visits, Icons.AutoMirrored.Outlined.EventNote, true),
    HISTORY(R.string.nav_history, Icons.Outlined.History, true),
    STOCK(R.string.stock, Icons.Outlined.Inventory2, true),
    MEDICATIONS(R.string.medications, Icons.Outlined.Medication, true),
    WELLBEING(R.string.wellbeing, Icons.Outlined.FavoriteBorder, true),
    CONCENTRATION(R.string.concentration, Icons.AutoMirrored.Outlined.ShowChart, true),
    LABS(R.string.labs, Icons.Outlined.Science, true),
    SETTINGS(R.string.settings, Icons.Outlined.Settings, true),
    ABOUT(R.string.about, Icons.Outlined.Info, true),
}

/** Non-health UI preferences. */
class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    var appearance: Appearance
        get() = Appearance(runCatching { ThemeMode.valueOf(p.getString("theme_mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM), p.getBoolean("dynamic_color", false),
            runCatching { Contrast.valueOf(p.getString("contrast", "STANDARD")!!) }.getOrDefault(Contrast.STANDARD))
        set(v) { p.edit().putString("theme_mode", v.mode.name).putBoolean("dynamic_color", v.dynamic).putString("contrast", v.contrast.name).apply() }
    var lockAfterMillis: Long get() = p.getLong("lock_after", 30_000L); set(v) { p.edit().putLong("lock_after", v).apply() }
    var simpleMode: Boolean get() = p.getBoolean("simple_mode", false); set(v) { p.edit().putBoolean("simple_mode", v).apply() }
    var highReliability: Boolean get() = p.getBoolean("high_reliability", false); set(v) { p.edit().putBoolean("high_reliability", v).apply() }
    var conc: ConcSettings
        get() = ConcSettings(p.getBoolean("conc_pmol", false), p.getBoolean("calib_enabled", true),
            if (p.getString("calib_mode", "RETROSPECTIVE") == "CAUSAL") CalibrationMode.CAUSAL else CalibrationMode.RETROSPECTIVE)
        set(v) { p.edit().putBoolean("conc_pmol", v.pmol).putBoolean("calib_enabled", v.calibrate).putString("calib_mode", v.mode.name).apply() }
    var disclaimerAccepted: Boolean get() = p.getBoolean("pk_disclaimer_ack", false); set(v) { p.edit().putBoolean("pk_disclaimer_ack", v).apply() }
    var region:String? get()=p.getString("wellbeing_region",null);set(v){p.edit{putString("wellbeing_region",v)}}
    var wellbeingPrompt: Boolean get() = p.getBoolean("wellbeing_prompt", true); set(v) { p.edit().putBoolean("wellbeing_prompt", v).apply() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NotesApp(model: NotesViewModel, appearance: Appearance, onAppearance: (Appearance) -> Unit, backToCalendar: Boolean = true,
    writeProgress:net.plainnotes.app.domain.write.WriteProgressSummary?=null,
    onWriteResultCheck:(()->Unit)?=null,onWriteRetry:(()->Unit)?=null) {
    val ownedWriteProgress by model.measuredWriteProgress.collectAsStateWithLifecycle()
    val effectiveWriteProgress=writeProgress ?: ownedWriteProgress
    val checkWriteResult=onWriteResultCheck ?: {model.checkMeasuredSave();Unit}
    val context = LocalContext.current
    val prefs = remember { UiPrefs(context) }
    var region by remember { mutableStateOf(prefs.region) }
    // UI-02B: page-owned dialog state lives in holders (ShellHosts.kt), created here with the same remember/rememberSaveable.
    val wellbeingDialogs = rememberWellbeingDialogs()
    val stockDialogs = remember { StockDialogs() }
    val state by model.state.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val override by model.override.collectAsStateWithLifecycle()
    val conc by model.conc.collectAsStateWithLifecycle()
    val extra by model.extra.collectAsStateWithLifecycle()
    // Same projection as the timeline, so History labels and periods always agree (REQUIREMENTS §35).
    val historyLabels = remember(extra, state.appointments, state.ruleSnapshots) {
        runCatching { net.plainnotes.app.timeline.HistoryLabels.build(extra.records, net.plainnotes.app.timeline.PeriodTimelineProjection.build(extra, state.appointments, ruleSnapshots = state.ruleSnapshots), extra.annotations, java.time.ZoneId.systemDefault()) }.getOrDefault(emptyMap())
    }
    val milestoneSave by model.milestoneSave.collectAsStateWithLifecycle()
    val medicalSave by model.medicalVisitSave.collectAsStateWithLifecycle()
    val importedLink by model.importedLink.collectAsStateWithLifecycle()
    val notificationSlot by model.notificationSlot.collectAsStateWithLifecycle()
    val reminderTest by model.reminderTest.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.CALENDAR) }
    // UI-02 A2: the settings page shown, kept across rotation and process recreation.
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    val settingsSub = destination == Destination.SETTINGS && settingsPage != SettingsPage.OVERVIEW
    var importedRecordIds by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(destination){if(destination!=Destination.HISTORY)importedRecordIds=null}
    var visitId by rememberSaveable { mutableStateOf<Long?>(null) }
    var concSettings by remember { mutableStateOf(prefs.conc) }
    var highReliability by remember { mutableStateOf(prefs.highReliability) }
    var disclaimer by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val timelineAdd = remember { TimelineAddHost() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var completeEntry by remember { mutableStateOf<TimelineEntry?>(null) }
    var overrideEntry by remember { mutableStateOf<TimelineEntry?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    val historyDialogs = remember { HistoryDialogs() }
    var appointment by remember { mutableStateOf(false) }
    val medicationDialogs = remember { MedicationDialogs() }
    var referenceOpen by remember {mutableStateOf(false)}
    fun openReference(medication:net.plainnotes.app.data.MedicationEntity?){if(model.medicationReferences.open(medication?.id)!=null)referenceOpen=true}
    val labUi = rememberLabUiState()
    val labDialogs = labUi.dialogs
    var pickStart by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    var medicalOrigin by rememberSaveable { mutableStateOf(false) }
    // Only a hub-launched page returns to the hub. Drawer and calendar routes remain independent.
    var medicalChild by rememberSaveable { mutableStateOf<Destination?>(null) }
    fun openMedicalChild(target:Destination) { medicalChild=target;medicalOrigin=false;visitId=null;destination=target }
    LaunchedEffect(destination) { if(medicalChild!=null && destination!=medicalChild)medicalChild=null }
    fun returnFromMedicalChild() {
        if(destination==Destination.VISITS && visitId!=null)visitId=null
        else {medicalChild=null;medicalOrigin=false;destination=Destination.MEDICAL}
    }
    val medicalDialogs = remember { MedicalDialogs() }
    val medicalRefreshText=stringResource(R.string.medical_saved_refresh)
    val medicalErrorText=stringResource(if(medicalSave.stale)R.string.medical_stale else R.string.medical_save_error)
    LaunchedEffect(medicalSave.saved){if(medicalSave.saved!=null){medicalDialogs.editing=false;medicalDialogs.edit=null;if(medicalSave.refreshFailed)snackbar.showSnackbar(medicalRefreshText);model.clearMedicalVisitSave()}}
    LaunchedEffect(medicalSave.failed){if(medicalSave.failed && !medicalDialogs.editing){snackbar.showSnackbar(medicalErrorText);model.clearMedicalVisitSave()}}
    var wellbeingPrompt by remember { mutableStateOf(prefs.wellbeingPrompt) }
    var simpleMode by remember { mutableStateOf(prefs.simpleMode) }
    val promptText = stringResource(R.string.wb_prompt); val promptAction = stringResource(R.string.wb_prompt_action)
    fun afterIntake() {
        val today = LocalDate.now().toString()
        if (wellbeingPrompt && extra.scores.none { it.date == today }) scope.launch {
            if (snackbar.showSnackbar(promptText, promptAction, withDismissAction = true) == SnackbarResult.ActionPerformed) destination = Destination.WELLBEING
        }
    }
    fun siteFor(m: MedicationEntity) = if (m.site_rotation) suggestSite(extra.records, m.id) else null

    LaunchedEffect(notificationSlot) { notificationSlot?.let { s -> completeEntry = state.slots.firstOrNull { it.slot.key == s.key } ?: TimelineEntry(s, net.plainnotes.app.domain.SlotState.PENDING); model.notificationSlot.value = null } }
    LaunchedEffect(Unit) { model.loadExtra() }
    LaunchedEffect(destination) { if (destination in listOf(Destination.CALENDAR, Destination.HISTORY, Destination.STOCK, Destination.WELLBEING, Destination.TIMELINE, Destination.VISITS, Destination.MEDICAL, Destination.CONCENTRATION)) model.loadExtra() }
    LaunchedEffect(destination, concSettings.calibrate, concSettings.mode) {
        if (destination == Destination.CONCENTRATION || destination == Destination.LABS) {
            model.concentrationSettings(concSettings.calibrate, concSettings.mode); model.loadConcentration()
            if (destination == Destination.CONCENTRATION && !prefs.disclaimerAccepted) disclaimer = true
        }
    }
    val errorText = state.error?.let { stringResource(it) }
    LaunchedEffect(errorText) { errorText?.let { snackbar.showSnackbar(it); model.clearError() } }

    // B3-A (R-06): Back closes the drawer, then returns from any other top-level page to the calendar. Dialogs and the
    // visit detail handle Back first (their handlers are newer). On the calendar, in disguise mode (immediate exit to the
    // shell) and behind the lock Back falls through to MainActivity unchanged.
    androidx.activity.compose.BackHandler(enabled = backToCalendar && (drawer.isOpen || destination != Destination.CALENDAR)) {
        if (drawer.isOpen) scope.launch { drawer.close() } else destination = Destination.CALENDAR
    }
    // UI-02 A2: a settings page goes back to the overview first (registered later, so it runs before the handler above);
    // the drawer still closes first, dialogs still win, and disguise/lock (backToCalendar = false) are unchanged.
    androidx.activity.compose.BackHandler(enabled = backToCalendar && settingsSub && !drawer.isOpen) { settingsPage = SettingsPage.OVERVIEW }
    androidx.activity.compose.BackHandler(enabled = backToCalendar && destination==medicalChild && !drawer.isOpen) { returnFromMedicalChild() }
    CompositionLocalProvider(LocalSimpleMode provides (simpleMode && state.medications.count { it.active } <= 1)) {
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            DrawerContent(destination, lowStockMedications(state.medications.filter { it.active }, extra.stockForecasts, today, java.time.ZoneId.systemDefault()).size) { d -> medicalChild=null; destination = d; if (d == Destination.VISITS) { visitId = null; medicalOrigin=false }; if (d == Destination.SETTINGS) settingsPage = SettingsPage.OVERVIEW; scope.launch { drawer.close() } }
        }
    }) {
        Scaffold(
            topBar = {
                Column {
                TopAppBar(title = { Text(stringResource(if (settingsSub) settingsPage.title else if (destination == Destination.CALENDAR) R.string.app_name else destination.title), Modifier.testTag("top-title")) },
                    navigationIcon = {
                        if (destination==medicalChild) IconButton(onClick = { returnFromMedicalChild() }, modifier=Modifier.testTag("medical-center-back")) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.medical_records)) }
                        else if (settingsSub) IconButton(onClick = { settingsPage = SettingsPage.OVERVIEW }, modifier = Modifier.testTag("settings-back")) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.settings_back)) }
                        else IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, stringResource(R.string.menu)) }
                    },
                    actions = {
                        if (destination == Destination.WELLBEING) IconButton(onClick={wellbeingDialogs.summary=true}) { Icon(Icons.Outlined.PictureAsPdf,stringResource(R.string.wb_summary)) }
                        if (destination == Destination.CALENDAR) IconButton(onClick = { pickStart = true }) { Icon(Icons.Outlined.EditCalendar, stringResource(R.string.calendar_pick_start)) }
                        if (destination == Destination.CONCENTRATION) IconButton(onClick = { model.loadConcentration() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh)) }
                        // UI-05 D: the timeline's "Add milestone" / "New period" (the timeline registers its own callbacks).
                        if (destination == Destination.TIMELINE) TimelineAddMenu(timelineAdd)
                        // UI-05 F: "Record a past visit", the same action as the former list button.
                        if (destination == Destination.MEDICAL) MedicalAddAction { model.clearMedicalVisitSave();medicalDialogs.edit=null;medicalDialogs.editing=true }
                    })
                // Observe the process owner only inside the authenticated shell. No submission entry is enabled.
                effectiveWriteProgress?.let{WriteStatusArea(it,checkWriteResult,onWriteRetry)}
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                when (destination) {
                    Destination.CALENDAR -> Box {
                        ExtendedFloatingActionButton(onClick = { addMenu = true }, modifier = Modifier.testTag("calendar-add"), icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.add)) })
                        DropdownMenu(addMenu, { addMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.manual)) }, leadingIcon = { Icon(Icons.Outlined.AddTask, null) }, enabled = state.medications.any { it.active },
                                onClick = { addMenu = false; manual = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.appointment)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.EventNote, null) }, onClick = { addMenu = false; appointment = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.add_medication)) }, leadingIcon = { Icon(Icons.Outlined.Medication, null) }, onClick = { addMenu = false; model.edit(null) })
                        }
                    }
                    Destination.MEDICATIONS -> ExtendedFloatingActionButton(onClick = { model.edit(null) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.add_medication)) })
                    Destination.VISITS -> if (visitId == null) ExtendedFloatingActionButton(onClick = { appointment = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.appointment)) })
                    Destination.LABS -> ExtendedFloatingActionButton(onClick = { labDialogs.labNew = true; labDialogs.labEdit = null }, modifier=Modifier.testTag("lab-open-batch"), icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.lab_add)) })
                    else -> {}
                }
            },
        ) { pad ->
            when (destination) {
                Destination.CALENDAR -> CalendarScreen(state, today, { completeEntry = it }, { overrideEntry = it; model.loadOverride(it.slot.key) }, { model.edit(null) },
                    { model.calendarFrom(null) }, pad, onReview = { destination = Destination.MEDICATIONS }, extra = extra, onStock = { destination = Destination.STOCK },
                    onAppointment = { visitId = it.id; medicalOrigin=false; destination = Destination.VISITS }, onAddStock = { stockDialogs.addStock = it })
                Destination.MEDICATIONS -> MedicationsScreen(state, { model.edit(null) }, { model.edit(it) }, { medicationDialogs.archive = it }, pad,onReference={openReference(it)},onBrowseReference={openReference(null)},onStock={destination=Destination.STOCK})
                Destination.CONCENTRATION -> net.plainnotes.app.experimental.UnifiedConcentrationScreen(extra.records, state.ruleSnapshots, extra.labs, pad,
                    content = { innerPad, preview, selector -> ConcentrationScreen(state, conc.result, conc.loading, conc.weight, concSettings, { concSettings = it; prefs.conc = it },
                        { model.setWeight(it) }, { model.editById(it) }, { destination = Destination.LABS }, innerPad,extra.records,state.profiles,model::confirmHistoricalContext,{destination=Destination.HISTORY},preview,selector,m2Only=true) })
                Destination.LABS -> LabsScreen(extra.labs, conc.doseTimes, { labDialogs.labEdit = it; labDialogs.labNew = it == null }, { model.deleteLab(it) }, pad,extra.labContexts,{v,estimate->model.rebuildLabContext(v,estimate)},
                    extra.labPanels,{labDialogs.panel=it.id},{labDialogs.saveAs=it})
                Destination.SETTINGS -> {
                    val lockOn = remember(settingsPage) { net.plainnotes.app.security.AppLock(context).enabled }
                    val backup by model.backupHistory.state.collectAsState()
                    val regionLabel = stringResource(when (region) { "CN" -> R.string.wb_region_cn; "HK" -> R.string.wb_region_hk; "TW" -> R.string.wb_region_tw
                        "FR" -> R.string.wb_region_fr; "OTHER" -> R.string.wb_region_other; else -> R.string.wb_region_unset })
                    val summaries = SettingsSummaries(regionLabel, stringResource(if (lockOn) R.string.settings_lock_on else R.string.settings_lock_off),
                        backup.lastExport?.let { stringResource(R.string.settings_backup_last, formatDateTime(it)) } ?: stringResource(R.string.settings_backup_never),
                        stringResource(R.string.settings_bin_count, extra.trash.size))
                    SettingsScreen(appearance, onAppearance, highReliability, { highReliability = it; prefs.highReliability = it; model.sync() },
                        { model.sync() }, { model.testReminder() }, pad, wellbeingPrompt, { wellbeingPrompt = it; prefs.wellbeingPrompt = it },
                        page = settingsPage, onPage = { settingsPage = it }, summaries = summaries,
                        wellbeingSlot = { RegionSection(region){region=it;prefs.region=it} },
                        privacySlot = {
                            PrivacySection(state.medications.count { it.active }, simpleMode) { simpleMode = it; prefs.simpleMode = it }
                            net.plainnotes.app.disguise.DisguiseSection(model::destroyLegacyPrivateData, onRoutingChanged = { model.sync() }, backup = model::backupTo)
                        },
                        dataSlot = { DataSection(model, state.medications.associate { it.id to scheduleText(state.schedules[it.id]) }) },
                        binSlot = { RecycleBinSection(state, extra, model::restoreTrash, model::purgeTrash, model::emptyTrash) },
                        testStatus = reminderTest, onRefreshTest = model::refreshReminderTest)
                }
                Destination.ABOUT -> AboutScreen(pad)
                Destination.TIMELINE -> LongitudinalScreen(state,extra,model::saveMilestone,model::deleteMilestone,{kind->destination=when(kind){
                    net.plainnotes.app.timeline.EventKind.LAB->Destination.LABS
                    net.plainnotes.app.timeline.EventKind.SYMPTOM,net.plainnotes.app.timeline.EventKind.WELLBEING,net.plainnotes.app.timeline.EventKind.REVIEW->Destination.WELLBEING
                    net.plainnotes.app.timeline.EventKind.APPOINTMENT->Destination.VISITS
                    else->Destination.HISTORY
                }},pad,onAppointment={visitId=it;medicalOrigin=false;destination=Destination.VISITS},saveState=milestoneSave,onSaveHandled=model::clearMilestoneSave,onImportedHistory={importedRecordIds=it.toSet();destination=Destination.HISTORY},
                    timelineActions=TimelineActions(model::editTimeline,model::deletePeriod),addHost=timelineAdd)
                Destination.MEDICAL -> MedicalRecordsScreen(state.appointments,{visitId=it.id;medicalOrigin=true;destination=Destination.VISITS},
                    {model.clearMedicalVisitSave();medicalDialogs.edit=null;medicalDialogs.editing=true},{openMedicalChild(Destination.LABS)},{openMedicalChild(Destination.TIMELINE)},{destination=Destination.SETTINGS},pad,
                    onConfirm={model.clearMedicalVisitSave();model.saveMedicalVisit(it,it)},questionCounts=extra.visitQuestions.groupingBy{it.appointment_id}.eachCount(),onVisits={openMedicalChild(Destination.VISITS)})
                Destination.VISITS -> VisitsScreen(state, extra, model, visitId, { visitId = it;if(it==null && medicalOrigin){medicalOrigin=false;destination=Destination.MEDICAL} }, { appointment = true }, pad,
                    medical=medicalOrigin,onMedicalEdit={model.clearMedicalVisitSave();medicalDialogs.edit=it;medicalDialogs.editing=true},onLab={medicalDialogs.lab=it.id})
                Destination.HISTORY -> { val historySnapshot=extra;HistoryScreen(state, historySnapshot.records, { historyDialogs.referenceEpoch=historySnapshot.referenceEpoch;historyDialogs.editRecord = it }, { historyDialogs.deleteRecord = it }, pad, onAdd = { manual = true }, onBatch = { historyDialogs.batch = true }, onConfirmMissed={model.confirmMissed(it.id)},selectedRecordIds=importedRecordIds,labels=historyLabels,onExtra={r,on->model.setExtra(r.id,on)},onClearSelection={importedRecordIds=null},onUndoSkip={model.undoSkipped(it)}) }
                Destination.STOCK -> StockScreen(state, extra.containers, extra.records, { m -> model.replaceContainer(m.id, m.container_capacity) }, { stockDialogs.checkStock = it }, { stockDialogs.addStock = it }, pad,
                    onManage = { stockDialogs.managePackages = it }, forecasts = extra.stockForecasts)
                Destination.WELLBEING -> WellbeingHub(state,extra,model,region,{wellbeingDialogs.manageItems=true},pad)
                else -> ComingSoonScreen(destination.icon, stringResource(destination.title), pad)
            }
        }
    }

    }
    val meds = state.medications.associateBy { it.id }
    importedLink?.let { link -> ImportedPlanDialog(link, meds[link.record.medication_id], model::closeImportedLink) { key -> model.linkImported(link.record.id, key) } }
    editor?.let { MedicationEditor(it, { model.closeEditor() }, containers = extra.containers,onReference={openReference(it.medication)}) { d -> model.save(d) } }
    if(referenceOpen)MedicationReferenceDialog(model.medicationReferences){referenceOpen=false}
    completeEntry?.let { e ->
        val saved=state.ruleSnapshots[e.slot.ruleId]?.let{MedicationSnapshot.decode(it,e.slot.medicationId)?.medication(e.slot.medicationId)}
        val current=meds[e.slot.medicationId]
        val intakeMed=if(saved!=null && current!=null)current.copy(name=saved.name,molecule=saved.molecule,route=saved.route,unit=saved.unit) else saved ?: current
        IntakeDialog(stringResource(R.string.complete), intakeMed, e.slot.dose,
        if (e.state in listOf(net.plainnotes.app.domain.SlotState.MISSED,net.plainnotes.app.domain.SlotState.UNCONFIRMED)) e.slot.at else Instant.now(),
        { completeEntry = null }, intakeMed?.let(::siteFor)) { t, d, site -> model.complete(e.slot, t, d, site); completeEntry = null; afterIntake() } }
    HistoryRecordDialogs(historyDialogs, model, meds)
    StockPageDialogs(stockDialogs, model, extra, meds)
    if (wellbeingDialogs.manageItems) ManageCheckinItemsDialog(extra.items,{wellbeingDialogs.manageItems=false},model::saveCheckinItem,extra.effects,model::setReviewEffect,model::reorderItems)
    if(wellbeingDialogs.summary) SummaryExportDialog(model,state,{wellbeingDialogs.summary=false})
    overrideEntry?.let { e -> if (override?.key == e.slot.key) OverrideDialog(e, meds[e.slot.medicationId], override!!, { overrideEntry = null }) { o -> model.changeOverride(e.slot, o); overrideEntry = null } }
    HistoryBatchDialog(historyDialogs, model, state, snackbar)
    if (manual) ManualIntakeDialog(state.medications.filter { it.active }, ::siteFor, { manual = false }) { id, t, d, site, extraDose -> model.manual(id, t, d, site, extraDose); manual = false; afterIntake() }
    MedicalVisitEditor(medicalDialogs, model, medicalSave)
    MedicalLabDetail(medicalDialogs, labDialogs, extra)
    if (appointment) AppointmentDialog({ appointment = false }) { model.appointment(it); appointment = false }
    LabPanelHost(labDialogs, model, extra)
    LabEditDialog(labDialogs, model, extra.labs, labUi)
    if (pickStart) DatePickerModal(state.calendarStart, { pickStart = false }) { model.calendarFrom(it) }
    if (disclaimer) PkDisclaimerDialog({ prefs.disclaimerAccepted = true; disclaimer = false }) { disclaimer = false; destination = Destination.CALENDAR }
    ArchiveMedicationDialog(medicationDialogs, model)
}

/** Drawer entries scroll so Settings and About stay reachable on short screens and with large font scales. */
/** One drawer section; display order only, the [Destination] enum (saved state, notification entry) is unchanged. */
internal sealed interface DrawerSection {
    val items: List<Destination>
    /** The first entries, with no heading or divider above them. */
    data class Top(override val items: List<Destination>) : DrawerSection
    /** A headed group; [key] names its `drawer-group:<key>` test tag. */
    data class Group(val key: String, val heading: Int, override val items: List<Destination>) : DrawerSection
    /** A divider, then entries without a heading (Settings and About). */
    data class Divider(override val items: List<Destination>) : DrawerSection
}

/** UI-03 (REQUIREMENTS §99): grouped by where the information comes from. */
internal val DRAWER_GROUPS: List<DrawerSection> = listOf(
    DrawerSection.Top(listOf(Destination.CALENDAR, Destination.TIMELINE)),
    DrawerSection.Group("journal", R.string.drawer_group_journal, listOf(Destination.HISTORY, Destination.WELLBEING)),
    DrawerSection.Group("medication", R.string.drawer_group_medication, listOf(Destination.MEDICATIONS, Destination.STOCK, Destination.CONCENTRATION)),
    DrawerSection.Group("records", R.string.drawer_group_records, listOf(Destination.MEDICAL, Destination.VISITS, Destination.LABS)),
    DrawerSection.Divider(listOf(Destination.SETTINGS, Destination.ABOUT)),
)

@Composable internal fun DrawerContent(selected: Destination, stockLow: Int = 0, onSelect: (Destination) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).testTag("drawer-content")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.EventAvailable, null, Modifier.padding(8.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        // UI-01 F: grouped display order only; every Destination stays, and the enum (saved state, notification entry) is unchanged.
        DRAWER_GROUPS.forEach { section ->
            when (section) {
                is DrawerSection.Top -> Unit
                is DrawerSection.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp).testTag("drawer-divider"))
                is DrawerSection.Group -> Text(stringResource(section.heading), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() }.testTag("drawer-group:${section.key}"))
            }
            section.items.forEach { d ->
                // UI-05 E: Stock shows how many medications are low (same set as the home strips), with a spoken description.
                val stockBadge = d == Destination.STOCK && stockLow > 0
                val badgeText = if (stockBadge) stringResource(R.string.drawer_stock_badge, stockLow) else ""
                NavigationDrawerItem(label = { Text(stringResource(d.title)) }, icon = { Icon(d.icon, null) }, selected = selected == d,
                    badge = if (!d.ready) ({ Text(stringResource(R.string.soon_badge), style = MaterialTheme.typography.labelSmall) })
                        else if (stockBadge) ({
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = Corners.badge,
                                modifier = Modifier.semantics { contentDescription = badgeText }.testTag("drawer-stock-badge")) {
                                Text("$stockLow", style = MaterialTheme.typography.labelLarge.tnum(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }) else null,
                    onClick = { onSelect(d) }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
            }
        }
    }
}
