package net.plainnotes.app.ui
import net.plainnotes.app.data.MedicationSnapshot

import androidx.core.content.edit
import android.content.Context
import androidx.compose.foundation.layout.*
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
    VISITS(R.string.visits, Icons.AutoMirrored.Outlined.EventNote, true),
    HISTORY(R.string.history, Icons.Outlined.History, true),
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
@Composable fun NotesApp(model: NotesViewModel, appearance: Appearance, onAppearance: (Appearance) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UiPrefs(context) }
    var region by remember { mutableStateOf(prefs.region) }
    var summary by rememberSaveable { mutableStateOf(false) }
    var packageInfo by remember { mutableStateOf<net.plainnotes.app.data.ContainerEntity?>(null) }
    val state by model.state.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val override by model.override.collectAsStateWithLifecycle()
    val conc by model.conc.collectAsStateWithLifecycle()
    val extra by model.extra.collectAsStateWithLifecycle()
    val milestoneSave by model.milestoneSave.collectAsStateWithLifecycle()
    val importedLink by model.importedLink.collectAsStateWithLifecycle()
    val notificationSlot by model.notificationSlot.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.CALENDAR) }
    var visitId by rememberSaveable { mutableStateOf<Long?>(null) }
    var concSettings by remember { mutableStateOf(prefs.conc) }
    var highReliability by remember { mutableStateOf(prefs.highReliability) }
    var disclaimer by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var completeEntry by remember { mutableStateOf<TimelineEntry?>(null) }
    var overrideEntry by remember { mutableStateOf<TimelineEntry?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf(false) }
    var batchDone by remember { mutableStateOf<Int?>(null) }
    var appointment by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf<MedicationEntity?>(null) }
    var labEdit by remember { mutableStateOf<LabValueEntity?>(null) }
    var labNew by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    var editRecord by remember { mutableStateOf<net.plainnotes.app.data.RecordEntity?>(null) }
    var deleteRecord by remember { mutableStateOf<net.plainnotes.app.data.RecordEntity?>(null) }
    var addStock by remember { mutableStateOf<MedicationEntity?>(null) }
    var adjustStock by remember { mutableStateOf<Pair<net.plainnotes.app.data.ContainerEntity, MedicationEntity>?>(null) }
    var manageItems by remember { mutableStateOf(false) }
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
    LaunchedEffect(destination) { if (destination in listOf(Destination.CALENDAR, Destination.HISTORY, Destination.STOCK, Destination.WELLBEING, Destination.TIMELINE, Destination.VISITS)) model.loadExtra() }
    LaunchedEffect(destination, concSettings.calibrate, concSettings.mode) {
        if (destination == Destination.CONCENTRATION || destination == Destination.LABS) {
            model.concentrationSettings(concSettings.calibrate, concSettings.mode); model.loadConcentration()
            if (destination == Destination.CONCENTRATION && !prefs.disclaimerAccepted) disclaimer = true
        }
    }
    val errorText = state.error?.let { stringResource(it) }
    LaunchedEffect(errorText) { errorText?.let { snackbar.showSnackbar(it); model.clearError() } }

    CompositionLocalProvider(LocalSimpleMode provides (simpleMode && state.medications.count { it.active } <= 1)) {
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Outlined.EventAvailable, null, Modifier.padding(8.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Destination.entries.forEach { d ->
                    if (d == Destination.SETTINGS) HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    NavigationDrawerItem(label = { Text(stringResource(d.title)) }, icon = { Icon(d.icon, null) }, selected = destination == d,
                        badge = if (!d.ready) ({ Text(stringResource(R.string.soon_badge), style = MaterialTheme.typography.labelSmall) }) else null,
                        onClick = { destination = d; if (d == Destination.VISITS) visitId = null; scope.launch { drawer.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                }
            }
        }
    }) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(if (destination == Destination.CALENDAR) R.string.app_name else destination.title)) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, stringResource(R.string.menu)) } },
                    actions = {
                        if (destination == Destination.WELLBEING) IconButton(onClick={summary=true}) { Icon(Icons.Outlined.PictureAsPdf,stringResource(R.string.wb_summary)) }
                        if (destination == Destination.CALENDAR) IconButton(onClick = { pickStart = true }) { Icon(Icons.Outlined.EditCalendar, stringResource(R.string.calendar_pick_start)) }
                        if (destination == Destination.CONCENTRATION) IconButton(onClick = { model.loadConcentration() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh)) }
                    })
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                when (destination) {
                    Destination.CALENDAR -> Box {
                        ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.add)) })
                        DropdownMenu(addMenu, { addMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.manual)) }, leadingIcon = { Icon(Icons.Outlined.AddTask, null) }, enabled = state.medications.any { it.active },
                                onClick = { addMenu = false; manual = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.appointment)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.EventNote, null) }, onClick = { addMenu = false; appointment = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.add_medication)) }, leadingIcon = { Icon(Icons.Outlined.Medication, null) }, onClick = { addMenu = false; model.edit(null) })
                        }
                    }
                    Destination.MEDICATIONS -> ExtendedFloatingActionButton(onClick = { model.edit(null) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.add_medication)) })
                    Destination.VISITS -> if (visitId == null) ExtendedFloatingActionButton(onClick = { appointment = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.appointment)) })
                    Destination.LABS -> ExtendedFloatingActionButton(onClick = { labNew = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.lab_add)) })
                    else -> {}
                }
            },
        ) { pad ->
            when (destination) {
                Destination.CALENDAR -> CalendarScreen(state, today, { completeEntry = it }, { overrideEntry = it; model.loadOverride(it.slot.key) }, { model.edit(null) },
                    { model.calendarFrom(null) }, pad, onReview = { destination = Destination.MEDICATIONS }, extra = extra, onStock = { destination = Destination.STOCK },
                    onAppointment = { visitId = it.id; destination = Destination.VISITS })
                Destination.MEDICATIONS -> MedicationsScreen(state, { model.edit(null) }, { model.edit(it) }, { archive = it }, pad)
                Destination.CONCENTRATION -> ConcentrationScreen(state, conc.result, conc.loading, conc.weight, concSettings, { concSettings = it; prefs.conc = it },
                    { model.setWeight(it) }, { model.editById(it) }, { destination = Destination.LABS }, pad,extra.records,state.profiles,model::confirmHistoricalContext,{destination=Destination.HISTORY})
                Destination.LABS -> LabsScreen(extra.labs, conc.doseTimes, { labEdit = it; labNew = it == null }, { model.deleteLab(it) }, pad,extra.labContexts,{v,estimate->model.rebuildLabContext(v,estimate)})
                Destination.SETTINGS -> SettingsScreen(appearance, onAppearance, highReliability, { highReliability = it; prefs.highReliability = it; model.sync() },
                    { model.sync() }, { model.testReminder() }, pad, wellbeingPrompt, { wellbeingPrompt = it; prefs.wellbeingPrompt = it }) {
                    RegionSection(region){region=it;prefs.region=it}
                    PrivacySection(state.medications.count { it.active }, simpleMode) { simpleMode = it; prefs.simpleMode = it }
                    net.plainnotes.app.disguise.DisguiseSection(model::destroyLegacyPrivateData, onRoutingChanged = { model.sync() }, backup = model::backupTo)
                    DataSection(model, state.medications.associate { it.id to scheduleText(state.schedules[it.id]) })
                }
                Destination.ABOUT -> AboutScreen(pad)
                Destination.TIMELINE -> LongitudinalScreen(state,extra,model::saveMilestone,model::deleteMilestone,{kind->destination=when(kind){
                    net.plainnotes.app.timeline.EventKind.LAB->Destination.LABS
                    net.plainnotes.app.timeline.EventKind.SYMPTOM,net.plainnotes.app.timeline.EventKind.WELLBEING,net.plainnotes.app.timeline.EventKind.REVIEW->Destination.WELLBEING
                    net.plainnotes.app.timeline.EventKind.APPOINTMENT->Destination.VISITS
                    else->Destination.HISTORY
                }},pad,onAppointment={visitId=it;destination=Destination.VISITS},saveState=milestoneSave,onSaveHandled=model::clearMilestoneSave)
                Destination.VISITS -> VisitsScreen(state, extra, model, visitId, { visitId = it }, { appointment = true }, pad)
                Destination.HISTORY -> HistoryScreen(state, extra.records, { editRecord = it }, { deleteRecord = it }, pad, onAdd = { manual = true }, onBatch = { batch = true }, onLink = model::prepareImportedLink,onConfirmMissed={model.confirmMissed(it.id)})
                Destination.STOCK -> StockScreen(state, extra.containers, extra.records, { m -> model.replaceContainer(m.id, m.container_capacity) }, { c, m -> adjustStock = c to m }, { addStock = it }, pad,onInfo={packageInfo=it})
                Destination.WELLBEING -> WellbeingHub(state,extra,model,region,{manageItems=true},pad)
                else -> ComingSoonScreen(destination.icon, stringResource(destination.title), pad)
            }
        }
    }

    }
    val meds = state.medications.associateBy { it.id }
    importedLink?.let { link -> ImportedPlanDialog(link, meds[link.record.medication_id], model::closeImportedLink) { key -> model.linkImported(link.record.id, key) } }
    editor?.let { MedicationEditor(it, { model.closeEditor() }, containers = extra.containers) { d -> model.save(d) } }
    completeEntry?.let { e -> IntakeDialog(stringResource(R.string.complete), meds[e.slot.medicationId], e.slot.dose,
        if (e.state in listOf(net.plainnotes.app.domain.SlotState.MISSED,net.plainnotes.app.domain.SlotState.UNCONFIRMED)) e.slot.at else Instant.now(),
        { completeEntry = null }, meds[e.slot.medicationId]?.let(::siteFor)) { t, d, site -> model.complete(e.slot, t, d, site); completeEntry = null; afterIntake() } }
    editRecord?.let { r -> IntakeDialog(stringResource(if (r.status == "MISSED") R.string.history_backfill else R.string.edit), MedicationSnapshot.decode(r.config_snapshot,r.medication_id)?.medication(r.medication_id), r.actual_dose ?: r.planned_dose ?: meds[r.medication_id]?.dose_per_intake ?: 1.0,
        Instant.ofEpochMilli(r.taken_utc ?: r.scheduled_utc ?: System.currentTimeMillis()), { editRecord = null }) { t, d, _ -> model.editRecord(r.id, t, d); editRecord = null } }
    deleteRecord?.let { r -> AlertDialog(onDismissRequest = { deleteRecord = null }, icon = { Icon(Icons.Outlined.Delete, null) }, text = { Text(stringResource(R.string.history_delete_confirm)) },
        confirmButton = { Button(onClick = { model.deleteRecord(r.id); deleteRecord = null }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { deleteRecord = null }) { Text(stringResource(R.string.cancel)) } }) }
    addStock?.let { m -> AddStockDialog(m, { addStock = null }) { cap, n, open,source,batch -> model.addContainers(m.id, cap, n, open,source,batch); addStock = null } }
    packageInfo?.let{box->ContainerInfoDialog(box,{packageInfo=null}){source,batch->model.setContainerInfo(box.id,source,batch);packageInfo=null}}
    adjustStock?.let { (c, m) -> AdjustStockDialog(c, m, { adjustStock = null }) { v -> model.setRemaining(c.id, v); adjustStock = null } }
    if (manageItems) ManageCheckinItemsDialog(extra.items,{manageItems=false},model::saveCheckinItem,extra.effects,model::setReviewEffect,model::reorderItems)
    if(summary) SummaryExportDialog(model,state,{summary=false})
    overrideEntry?.let { e -> if (override?.key == e.slot.key) OverrideDialog(e, meds[e.slot.medicationId], override!!, { overrideEntry = null }) { o -> model.changeOverride(e.slot, o); overrideEntry = null } }
    if (batch) BatchAddDialog(state.medications.filter { it.active && it.needs_review == null }, { m ->
        state.schedules[m.id]?.takeIf { it.kind == net.plainnotes.app.domain.RuleKind.EVERY_N_DAYS && it.interval == 1 && it.times.isNotEmpty() }?.times ?: TWICE_DAILY
    }, { batch = false }) { id, from, to, times, d -> batch = false; model.backfill(id, from, to, times, d) { batchDone = it } }
    val batchText = batchDone?.let { stringResource(R.string.batch_done, it) }
    LaunchedEffect(batchText) { batchText?.let { snackbar.showSnackbar(it); batchDone = null } }
    if (manual) ManualIntakeDialog(state.medications.filter { it.active }, ::siteFor, { manual = false }) { id, t, d, site -> model.manual(id, t, d, site); manual = false; afterIntake() }
    if (appointment) AppointmentDialog({ appointment = false }) { model.appointment(it); appointment = false }
    if (labNew || labEdit != null) LabDialog(labEdit, { labNew = false; labEdit = null }, { model.saveLab(it); labNew = false; labEdit = null },onSaveWithEstimate={v,estimate->model.saveLab(v,estimate);labNew=false;labEdit=null})
    if (pickStart) DatePickerModal(state.calendarStart, { pickStart = false }) { model.calendarFrom(it) }
    if (disclaimer) PkDisclaimerDialog({ prefs.disclaimerAccepted = true; disclaimer = false }) { disclaimer = false; destination = Destination.CALENDAR }
    archive?.let { m ->
        AlertDialog(onDismissRequest = { archive = null }, icon = { Icon(Icons.Outlined.Archive, null) }, title = { Text(m.name) }, text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = { Button(onClick = { model.delete(m.id); archive = null }) { Text(stringResource(R.string.yes)) } },
            dismissButton = { TextButton(onClick = { archive = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
