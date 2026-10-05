package net.plainnotes.app.ui

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
    HISTORY(R.string.history, Icons.Outlined.History, false),
    STOCK(R.string.stock, Icons.Outlined.Inventory2, false),
    MEDICATIONS(R.string.medications, Icons.Outlined.Medication, true),
    WELLBEING(R.string.wellbeing, Icons.Outlined.FavoriteBorder, false),
    CONCENTRATION(R.string.concentration, Icons.AutoMirrored.Outlined.ShowChart, true),
    LABS(R.string.labs, Icons.Outlined.Science, true),
    SETTINGS(R.string.settings, Icons.Outlined.Settings, true),
    ABOUT(R.string.about, Icons.Outlined.Info, true),
}

/** Non-health UI preferences. */
class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    var appearance: Appearance
        get() = Appearance(runCatching { ThemeMode.valueOf(p.getString("theme_mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM), p.getBoolean("dynamic_color", false))
        set(v) { p.edit().putString("theme_mode", v.mode.name).putBoolean("dynamic_color", v.dynamic).apply() }
    var highReliability: Boolean get() = p.getBoolean("high_reliability", false); set(v) { p.edit().putBoolean("high_reliability", v).apply() }
    var conc: ConcSettings
        get() = ConcSettings(p.getBoolean("conc_pmol", false), p.getBoolean("calib_enabled", true),
            if (p.getString("calib_mode", "RETROSPECTIVE") == "CAUSAL") CalibrationMode.CAUSAL else CalibrationMode.RETROSPECTIVE)
        set(v) { p.edit().putBoolean("conc_pmol", v.pmol).putBoolean("calib_enabled", v.calibrate).putString("calib_mode", v.mode.name).apply() }
    var disclaimerAccepted: Boolean get() = p.getBoolean("pk_disclaimer_ack", false); set(v) { p.edit().putBoolean("pk_disclaimer_ack", v).apply() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NotesApp(model: NotesViewModel, appearance: Appearance, onAppearance: (Appearance) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UiPrefs(context) }
    val state by model.state.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val override by model.override.collectAsStateWithLifecycle()
    val conc by model.conc.collectAsStateWithLifecycle()
    val notificationSlot by model.notificationSlot.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.CALENDAR) }
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
    var appointment by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf<MedicationEntity?>(null) }
    var labEdit by remember { mutableStateOf<LabValueEntity?>(null) }
    var labNew by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    LaunchedEffect(notificationSlot) { notificationSlot?.let { s -> completeEntry = state.slots.firstOrNull { it.slot.key == s.key } ?: TimelineEntry(s, net.plainnotes.app.domain.SlotState.PENDING); model.notificationSlot.value = null } }
    LaunchedEffect(destination, concSettings.calibrate, concSettings.mode) {
        if (destination == Destination.CONCENTRATION || destination == Destination.LABS) {
            model.concentrationSettings(concSettings.calibrate, concSettings.mode); model.loadConcentration()
            if (destination == Destination.CONCENTRATION && !prefs.disclaimerAccepted) disclaimer = true
        }
    }
    val errorText = state.error?.let { stringResource(it) }
    LaunchedEffect(errorText) { errorText?.let { snackbar.showSnackbar(it); model.clearError() } }

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
                        onClick = { destination = d; scope.launch { drawer.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                }
            }
        }
    }) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(if (destination == Destination.CALENDAR) R.string.app_name else destination.title)) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, stringResource(R.string.menu)) } },
                    actions = {
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
                    Destination.LABS -> ExtendedFloatingActionButton(onClick = { labNew = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.lab_add)) })
                    else -> {}
                }
            },
        ) { pad ->
            when (destination) {
                Destination.CALENDAR -> CalendarScreen(state, today, { completeEntry = it }, { overrideEntry = it; model.loadOverride(it.slot.key) }, { model.edit(null) },
                    { model.calendarFrom(null) }, pad)
                Destination.MEDICATIONS -> MedicationsScreen(state, { model.edit(null) }, { model.edit(it) }, { archive = it }, pad)
                Destination.CONCENTRATION -> ConcentrationScreen(state, conc.result, conc.loading, conc.weight, concSettings, { concSettings = it; prefs.conc = it },
                    { model.setWeight(it) }, { model.editById(it) }, { destination = Destination.LABS }, pad)
                Destination.LABS -> LabsScreen(conc.labs, conc.doseTimes, { labEdit = it; labNew = it == null }, { model.deleteLab(it) }, pad)
                Destination.SETTINGS -> SettingsScreen(appearance, onAppearance, highReliability, { highReliability = it; prefs.highReliability = it; model.sync() },
                    { model.sync() }, { model.testReminder() }, pad)
                Destination.ABOUT -> AboutScreen(pad)
                else -> ComingSoonScreen(destination.icon, stringResource(destination.title), pad)
            }
        }
    }

    val meds = state.medications.associateBy { it.id }
    editor?.let { MedicationEditor(it, { model.closeEditor() }) { d -> model.save(d) } }
    completeEntry?.let { e -> IntakeDialog(stringResource(R.string.complete), meds[e.slot.medicationId], e.slot.dose,
        if (e.state == net.plainnotes.app.domain.SlotState.MISSED) e.slot.at else Instant.now(),
        { completeEntry = null }) { t, d -> model.complete(e.slot, t, d); completeEntry = null } }
    overrideEntry?.let { e -> if (override?.key == e.slot.key) OverrideDialog(e, meds[e.slot.medicationId], override!!, { overrideEntry = null }) { o -> model.changeOverride(e.slot, o); overrideEntry = null } }
    if (manual) ManualIntakeDialog(state.medications.filter { it.active }, { manual = false }) { id, t, d -> model.manual(id, t, d); manual = false }
    if (appointment) AppointmentDialog({ appointment = false }) { model.appointment(it); appointment = false }
    if (labNew || labEdit != null) LabDialog(labEdit, { labNew = false; labEdit = null }) { model.saveLab(it); labNew = false; labEdit = null }
    if (pickStart) DatePickerModal(state.calendarStart, { pickStart = false }) { model.calendarFrom(it) }
    if (disclaimer) PkDisclaimerDialog({ prefs.disclaimerAccepted = true; disclaimer = false }) { disclaimer = false; destination = Destination.CALENDAR }
    archive?.let { m ->
        AlertDialog(onDismissRequest = { archive = null }, icon = { Icon(Icons.Outlined.Archive, null) }, title = { Text(m.name) }, text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = { Button(onClick = { model.delete(m.id); archive = null }) { Text(stringResource(R.string.yes)) } },
            dismissButton = { TextButton(onClick = { archive = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
