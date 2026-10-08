package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.plainnotes.app.EditMedication
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.ProfileEntity
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.pk.GEL_PRODUCT_IDS
import java.time.DayOfWeek
import java.time.LocalTime

val MOLECULES = listOf("E2", "T", "P4", "CPA", "SPI", "BICA", "FIN", "DUT", "DHT", "CMA", "NOMAC", "TRIP", "OTHER")
val E2_ROUTES = listOf("ORAL", "SUBLINGUAL", "GEL", "PATCH", "INJECTION")
val UNITS = listOf("MG", "TABLET", "ML", "PUMP", "PATCH", "OTHER")
fun estersFor(route: String) = when (route) { "INJECTION" -> listOf("EV", "EC", "EB", "EN", "EU"); "ORAL", "SUBLINGUAL" -> listOf("E2", "EV"); else -> listOf("E2") }
private val GEL_SITES = listOf("ARM", "THIGH", "ABDOMEN", "SCROTAL")

class MedicationDraft(val medication: MedicationEntity, val ester: String?, val kind: RuleKind, val interval: Int, val times: List<LocalTime>,
                      val weekdays: Set<DayOfWeek>, val pk: ProfileEntity?, val resizeContainers: Boolean = false, val timeDoses: List<Double?>? = null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun MedicationEditor(edit: EditMedication, onDismiss: () -> Unit, inDialog: Boolean = true,
                                 containers: List<net.plainnotes.app.data.ContainerEntity> = emptyList(), onSave: (MedicationDraft) -> Unit) {
    val m = edit.medication; val p = edit.profile
    val review = remember(m) { m?.needs_review?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } }
    val prefill = review?.optJSONObject("prefill")
    val prefillTimes = prefill?.optJSONArray("times")?.let { a -> (0 until a.length()).mapNotNull { runCatching { LocalTime.parse(a.getString(it)) }.getOrNull() } }.orEmpty()
    var name by remember { mutableStateOf(m?.name ?: "") }
    var molecule by remember { mutableStateOf(m?.molecule ?: "E2") }
    var route by remember { mutableStateOf(m?.route ?: if (review != null) "" else "ORAL") }
    var ester by remember { mutableStateOf(p?.ester ?: "E2") }
    var unit by remember { mutableStateOf(m?.unit ?: "MG") }
    var dose by remember { mutableStateOf(m?.dose_per_intake?.let(::inputNumber) ?: "") }
    // HRT tracker has no package size; the stored placeholder is not shown as if it were known.
    var capacity by remember { mutableStateOf(m?.container_capacity?.takeUnless { review?.optJSONObject("raw")?.has("capacity") == true }?.let(::inputNumber) ?: "") }
    var resize by remember { mutableStateOf(true) }
    var expiry by remember { mutableStateOf(m?.expiry_days_after_open?.toString() ?: "") }
    var soon by remember { mutableStateOf(m?.soon_alert_minutes?.toString() ?: if (m == null) "15" else "") }
    var late by remember { mutableStateOf(m?.late_after_minutes?.toString() ?: if (m == null) "120" else "") }
    var active by remember { mutableStateOf(m?.active ?: true) }
    var notifications by remember { mutableStateOf(m?.notifications_on ?: true) }
    var siteRotation by remember { mutableStateOf(m?.site_rotation ?: false) }
    // §37a: on the import review sheet, the schedule recognised from the imported records is the prefill.
    val recognized = edit.recognized?.takeIf { review != null && edit.rule == null }
    var kind by remember { mutableStateOf(edit.rule?.kind?.let(RuleKind::valueOf) ?: RuleKind.EVERY_N_DAYS) }
    var interval by remember { mutableStateOf(edit.rule?.interval?.toString() ?: recognized?.interval?.toString() ?: if (review != null && prefill?.optBoolean("daily") != true) "" else "1") }
    val times = remember { mutableStateListOf<LocalTime>().apply { addAll(edit.times.map { LocalTime.parse(it.local_time) }.sorted().ifEmpty { recognized?.times ?: prefillTimes.ifEmpty { listOf(LocalTime.of(9, 0)) } }) } }
    var confirmMismatch by remember { mutableStateOf(false) }
    val timeDoses = remember { mutableStateListOf<Double?>().apply { addAll(if(edit.times.isEmpty()) times.map{null} else edit.times.sortedBy{it.local_time}.map{it.dose_override}) } }
    val weekdays = remember { mutableStateListOf<DayOfWeek>().apply { edit.rule?.let { r -> addAll(DayOfWeek.entries.filter { r.weekday_mask and (1 shl (it.value - 1)) != 0 }) } } }
    // PK inputs (estradiol only)
    var slTier by remember { mutableStateOf(p?.sl_tier) }
    var gelProduct by remember { mutableStateOf(p?.gel_product_id) }
    var gelSite by remember { mutableStateOf(p?.gel_site) }
    var patchRate by remember { mutableStateOf(p?.patch_release_ug_day?.let(::inputNumber) ?: "") }
    var tried by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Int?>(null) }

    val doseV = dose.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val capV = capacity.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val expV = expiry.takeIf { it.isNotBlank() }?.let { it.toIntOrNull()?.takeIf { v -> v > 0 } ?: -1 }
    val soonV = soon.toIntOrNull()?.takeIf { it >= 0 }; val lateV = late.toIntOrNull()?.takeIf { it >= 0 }
    val intervalV = interval.toIntOrNull()?.takeIf { it in 1..36500 }
    val isE2 = molecule == "E2"
    // Application area is not a model input any more; a value saved by an older version is kept as is.
    val resolvedArea = p?.gel_area_cm2
    val valid = (!isE2 || route in E2_ROUTES) && name.isNotBlank() && doseV != null && capV != null && expV != -1 && soonV != null && lateV != null && intervalV != null &&
        (kind == RuleKind.EVERY_N_HOURS || times.isNotEmpty()) && (kind != RuleKind.WEEKLY || weekdays.isNotEmpty())

    fun save() {
        if (valid) onSave(MedicationDraft(
            MedicationEntity(m?.id ?: 0, name.trim(), molecule, if (isE2) route else null, unit, doseV!!, capV!!, expV, soonV, lateV, siteRotation, if (siteRotation) "LR" else null, notifications, active, m?.sort_order ?: 0, null),
            if (isE2) ester else null, kind, intervalV!!, times.toList(), weekdays.toSet(),
            if (isE2) ProfileEntity(m?.id ?: 0, ester, "", slTier.takeIf { route == "SUBLINGUAL" }, gelProduct.takeIf { route == "GEL" }, gelSite.takeIf { route == "GEL" },
                resolvedArea.takeIf { route == "GEL" }, patchRate.toDoubleOrNull()?.takeIf { route == "PATCH" && it > 0 }) else null,
            resizeContainers = resize, timeDoses = timeDoses.toList()))
    }
    if (confirmMismatch && recognized != null) AlertDialog(onDismissRequest = { confirmMismatch = false }, modifier = Modifier.testTag("schedule-mismatch"),
        text = { Text(stringResource(R.string.review_schedule_mismatch, stringResource(R.string.period_frequency_days, recognized.times.size, recognized.interval),
            if (kind == RuleKind.EVERY_N_DAYS) stringResource(R.string.period_frequency_days, times.size, intervalV ?: 0) else choiceLabel(kind.name) + " " + interval)) },
        confirmButton = { Button(onClick = { confirmMismatch = false; save() }) { Text(stringResource(R.string.review_schedule_save_anyway)) } },
        dismissButton = { TextButton(onClick = { confirmMismatch = false }) { Text(stringResource(R.string.review_schedule_back)) } })
    val body: @Composable () -> Unit = {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(if (m == null) R.string.add_medication else R.string.edit_medication)) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) } },
                actions = { TextButton(onClick = {
                    tried = true
                    // §37a: a schedule clearly different from the imported records needs an explicit confirmation.
                    val differs = recognized != null && (kind != RuleKind.EVERY_N_DAYS || intervalV != recognized.interval || times.size != recognized.times.size)
                    if (valid && differs) confirmMismatch = true else save()
                }) { Text(stringResource(R.string.save)) } })
        }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (tried && !valid) Text(stringResource(R.string.invalid), color = MaterialTheme.colorScheme.error)
                review?.let { r -> ReviewCard(r) }
                SectionCard(stringResource(R.string.section_basic)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true, isError = tried && name.isBlank())
                    DropdownField(stringResource(R.string.molecule), MOLECULES, molecule, { choiceLabel(it) }, { molecule = it }, detail = { if (it == "E2") null else brandNames(it) })
                    if (isE2) {
                        DropdownField(stringResource(R.string.route), E2_ROUTES, route.takeIf { it in E2_ROUTES }, { choiceLabel(it) }, isError = tried && route !in E2_ROUTES, onSelect = {
                            route = it; if (ester !in estersFor(it)) ester = estersFor(it).first(); if (it == "PATCH") unit = "PATCH" else if (unit == "PATCH") unit = "MG"
                        })
                        DropdownField(stringResource(R.string.ester), estersFor(route), ester, { choiceLabel(it) }, { ester = it }, detail = ::brandNames)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(dose, { dose = it }, stringResource(R.string.dose), Modifier.weight(1f), isError = tried && doseV == null)
                        DropdownField(stringResource(R.string.unit), UNITS, unit, { unitLabel(it) }, { unit = it }, Modifier.weight(1f))
                    }
                    if (isE2 && route == "INJECTION") Text(stringResource(R.string.injection_dose_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isE2) SectionCard(stringResource(R.string.section_pk)) {
                    Text(stringResource(R.string.pk_inputs_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (route) {
                        "SUBLINGUAL" -> DropdownField(stringResource(R.string.sl_tier), listOf(0, 1, 2, 3), slTier, { slTierLabel(it) }, { slTier = it },
                            supporting = if (slTier == null) stringResource(R.string.pk_required) else null)
                        "GEL" -> {
                            DropdownField(stringResource(R.string.gel_product), GEL_PRODUCT_IDS, gelProduct, { gelProductLabel(it) }, { gelProduct = it },
                                supporting = if (gelProduct == null) stringResource(R.string.pk_required) else null)
                            DropdownField(stringResource(R.string.gel_site), GEL_SITES, gelSite, { gelSiteLabel(it) }, { gelSite = it },
                                supporting = stringResource(R.string.gel_site_not_modelled))
                            Text(stringResource(R.string.gel_assumptions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        "PATCH" -> {
                            NumberField(patchRate, { patchRate = it }, stringResource(R.string.patch_release), suffix = "µg/" + stringResource(R.string.day_unit),
                                supporting = stringResource(if (patchRate.toDoubleOrNull()?.let { it > 0 } == true) R.string.patch_release_hint else R.string.pk_required))
                            Text(stringResource(R.string.patch_assumption), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> Text(stringResource(R.string.pk_no_extra_inputs), style = MaterialTheme.typography.bodyMedium)
                    }
                    if ((route != "PATCH" && unit != "MG") || (route == "PATCH" && unit != "PATCH"))
                        Text(stringResource(if (route == "PATCH") R.string.pk_unit_patch else R.string.pk_unit_mg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                SectionCard(stringResource(R.string.section_schedule)) {
                    recognized?.let { r -> Text(stringResource(R.string.review_schedule_recognized, stringResource(R.string.period_frequency_days, r.times.size, r.interval)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("schedule-recognized")) }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        RuleKind.entries.forEachIndexed { i, k ->
                            SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, RuleKind.entries.size)) {
                                Text(stringResource(when (k) { RuleKind.EVERY_N_DAYS -> R.string.kind_days; RuleKind.EVERY_N_HOURS -> R.string.kind_hours; RuleKind.WEEKLY -> R.string.kind_weekly }), maxLines = 1)
                            }
                        }
                    }
                    NumberField(interval, { interval = it }, stringResource(when (kind) { RuleKind.EVERY_N_DAYS -> R.string.interval_days; RuleKind.EVERY_N_HOURS -> R.string.interval_hours; RuleKind.WEEKLY -> R.string.interval_weeks }),
                        decimal = false, isError = tried && intervalV == null)
                    // An interval above one day is easy to type by accident (e.g. "11" for "1"), so say plainly what it means.
                    if (kind == RuleKind.EVERY_N_DAYS && (intervalV ?: 1) > 1) Text(stringResource(R.string.interval_days_meaning, intervalV!!),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (kind == RuleKind.EVERY_N_HOURS) Text(stringResource(R.string.hours_anchor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (kind == RuleKind.WEEKLY) {
                        Text(stringResource(R.string.weekdays_label), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.entries.forEach { d ->
                                FilterChip(d in weekdays, { if (d in weekdays) weekdays.remove(d) else weekdays.add(d) }, label = { Text(weekdayShort(d)) })
                            }
                        }
                        if (tried && weekdays.isEmpty()) Text(stringResource(R.string.weekdays_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    // Times a day: fills evenly spaced times only (each stays editable); the dose always stays the user's own entry.
                    TimesPerDayRow(if (kind == RuleKind.EVERY_N_HOURS) 1 else times.size.coerceIn(1, MAX_TIMES_PER_DAY)) { n ->
                        kind = RuleKind.EVERY_N_DAYS; interval = "1"
                        if(n!=times.size) { times.clear(); times.addAll(evenTimes(n)); timeDoses.clear(); timeDoses.addAll(times.map{null}) }
                    }
                    Text(stringResource(R.string.times_per_day_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (kind != RuleKind.EVERY_N_HOURS) {
                        Text(stringResource(R.string.times_label), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            times.forEachIndexed { i, t ->
                                InputChip(true, { pickTime = i }, label = { Text(formatTime(t)+(timeDoses.getOrNull(i)?.let{" · "+formatDose(it,unit)} ?: "")) },
                                    trailingIcon = if (times.size > 1) ({
                                        IconButton(onClick = { times.removeAt(i); timeDoses.removeAt(i) }, Modifier.size(24.dp)) { Icon(Icons.Outlined.Close, stringResource(R.string.remove), Modifier.size(18.dp)) }
                                    }) else null)
                            }
                            AssistChip({ pickTime = -1 }, label = { Text(stringResource(R.string.add_time)) }, leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) })
                        }
                    }
                }
                SectionCard(stringResource(R.string.section_reminders)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(soon, { soon = it }, stringResource(R.string.soon), Modifier.weight(1f), suffix = stringResource(R.string.minutes_unit), decimal = false, isError = tried && soonV == null)
                        NumberField(late, { late = it }, stringResource(R.string.late), Modifier.weight(1f), suffix = stringResource(R.string.minutes_unit), decimal = false, isError = tried && lateV == null)
                    }
                    SwitchRow(stringResource(R.string.notifications), notifications) { notifications = it }
                    SwitchRow(stringResource(R.string.active), active) { active = it }
                    SwitchRow(stringResource(R.string.site_rotation), siteRotation, stringResource(R.string.site_rotation_desc)) { siteRotation = it }
                }
                SectionCard(stringResource(R.string.section_supply)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(capacity, { capacity = it }, stringResource(R.string.capacity), Modifier.weight(1f), suffix = unitLabel(unit), isError = tried && capV == null)
                        NumberField(expiry, { expiry = it }, stringResource(R.string.expiry), Modifier.weight(1f), suffix = stringResource(R.string.day_unit), decimal = false, isError = tried && expV == -1)
                    }
                    // Packages already in stock keep their own size unless the user chooses to update them here.
                    val mine = m?.let { med -> containers.filter { it.medication_id == med.id && it.state in setOf("IN_USE", "SEALED") } }.orEmpty()
                    val differing = capV?.let { c -> mine.filter { it.capacity != c } }.orEmpty()
                    if (differing.isNotEmpty()) {
                        val fits = net.plainnotes.app.data.resizableContainers(mine, m!!.id, capV!!)
                        SwitchRow(stringResource(R.string.resize_containers, differing.size, formatDose(capV, unit)), resize,
                            stringResource(R.string.resize_containers_desc)) { resize = it }
                        if (resize && fits.size < differing.size) Text(stringResource(R.string.resize_containers_blocked, differing.size - fits.size),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (inDialog) Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { body() } else body()
    pickTime?.let { idx ->
        TimePickerModal(if (idx >= 0) times[idx] else LocalTime.of(21, 0), { pickTime = null }) { t ->
            if (t !in times || idx >= 0 && times[idx]==t) {
                if (idx >= 0) times[idx] = t else { times.add(t); timeDoses.add(null) }
            }
        }
    }
}

@Composable fun SwitchRow(label: String, checked: Boolean, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onChange)
    }
}

@Composable fun slTierLabel(tier: Int) = stringResource(R.string.sl_tier_option, net.plainnotes.app.pk.PkParams.model("E2_SL").tierMinutes[tier],
    stringResource(listOf(R.string.sl_quick, R.string.sl_casual, R.string.sl_standard, R.string.sl_strict)[tier]))
@Composable fun gelProductLabel(id: Int) = stringResource(when (id) { 1 -> R.string.gel_oestrogel; 2 -> R.string.gel_estreva; 3 -> R.string.gel_estrogel; 4 -> R.string.gel_divigel; else -> R.string.gel_diy })
@Composable fun gelSiteLabel(site: String) = stringResource(when (site) { "ARM" -> R.string.site_arm; "THIGH" -> R.string.site_thigh; "ABDOMEN" -> R.string.site_abdomen; else -> R.string.site_scrotal })

/** Shows the Trans Memo values that could not be mapped, so the user can set them deliberately. */
@Composable private fun ReviewCard(review: org.json.JSONObject) {
    val raw = review.optJSONObject("raw") ?: org.json.JSONObject()
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val ht = review.optString("source") == "hrttracker"
            Text(stringResource(if (ht) R.string.review_title_ht else R.string.review_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(stringResource(if (ht) R.string.review_body_ht else R.string.review_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            raw.keys().asSequence().toList().sorted().forEach { k ->
                val label = when (k) {
                    "intakeInterval" -> stringResource(R.string.review_interval); "soonAlertDelay" -> stringResource(R.string.review_soon); "lateAlertDelay" -> stringResource(R.string.review_late)
                    "notifications" -> stringResource(R.string.review_notifications); "route" -> stringResource(R.string.review_route); "molecule" -> stringResource(R.string.review_molecule)
                    "molecule_inferred" -> stringResource(R.string.review_molecule_inferred); "unit" -> stringResource(R.string.review_unit)
                    "capacity" -> stringResource(R.string.review_capacity); else -> k
                }
                val v = raw.optString(k)
                Text("• $label" + if (v.isNotEmpty()) stringResource(R.string.review_raw, v) else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}
