package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.data.ContainerEntity
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.data.RecordEntity
import net.plainnotes.app.data.StockForecast
import net.plainnotes.app.data.StockForecasts
import java.time.ZoneId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val LOW_STOCK_DAYS = net.plainnotes.app.data.StockForecasts.LOW_STOCK_DAYS
const val EXPIRY_WARNING_DAYS = net.plainnotes.app.reminder.StockAlerts.EXPIRY_WARNING_DAYS

/**
 * What the stock page shows for one medication. Days and the low-stock label come from the shared per-dose
 * [StockForecast] (B4), the same one the calendar and stock reminders use; packages and expiry come from containers.
 */
class StockSummary(val open: ContainerEntity?, val remaining: Double, val sealedCount: Int, val forecast: StockForecast?,
                   val daysLeft: Int?, val low: Boolean, val expiresOn: LocalDate?) {
    val tracked get() = open != null || sealedCount > 0
    fun expiringSoon(today: LocalDate) = expiresOn != null && ChronoUnit.DAYS.between(today, expiresOn) <= EXPIRY_WARNING_DAYS
}
fun stockSummary(m: MedicationEntity, containers: List<ContainerEntity>, forecast: StockForecast?, today: LocalDate, zone: ZoneId): StockSummary {
    val mine = containers.filter { it.medication_id == m.id }
    val open = mine.filter { it.state == "IN_USE" }.minByOrNull { it.opened_on ?: "" }
    val remaining = StockForecasts.available(m.id, containers) ?: 0.0
    val expires = open?.opened_on?.let { d -> m.expiry_days_after_open?.let { LocalDate.parse(d).plusDays(it.toLong()) } }
    return StockSummary(open, remaining, mine.count { it.state == "SEALED" }, forecast, forecast?.daysUntilShort(today, zone),
        forecast?.isLow(today, zone) == true, expires)
}

/** One sentence for a medication's shared forecast, plus whether it is a warning (calendar card and stock page). */
@Composable fun stockForecastText(f: StockForecast?, m: MedicationEntity, today: LocalDate, zone: ZoneId): Pair<String, Boolean> =
    when (f?.status ?: StockForecast.Status.UNTRACKED) {
        StockForecast.Status.UNTRACKED -> stringResource(R.string.forecast_untracked) to false
        StockForecast.Status.NO_PLAN -> stringResource(R.string.forecast_no_plan, formatDose(f!!.available ?: 0.0, m.unit)) to false
        // Date and day count both name the first dose the stock cannot cover, in calendar days.
        StockForecast.Status.SHORT -> { val short = f!!.firstShort!!
            if (f.lastCovered == null) stringResource(R.string.forecast_empty) to true
            else stringResource(R.string.forecast_short, formatShortDate(short.atZone(zone).toLocalDate()), f.daysUntilShort(today, zone) ?: 0,
                formatDose(f.available ?: 0.0, m.unit)) to f.isLow(today, zone) }
        // Not "unlimited": every planned dose in the window is covered, so it lasts at least to the last one.
        StockForecast.Status.COVERED_THROUGH_WINDOW -> stringResource(R.string.forecast_covered, formatShortDate(f!!.lastCovered!!.atZone(zone).toLocalDate()),
            formatDose(f.available ?: 0.0, m.unit)) to false
    }

@Composable fun StockScreen(state: NotesState, containers: List<ContainerEntity>, records: List<RecordEntity>, onReplace: (MedicationEntity) -> Unit,
                            onCheck: (MedicationEntity) -> Unit, onAdd: (MedicationEntity) -> Unit, contentPadding: PaddingValues, onManage: (MedicationEntity) -> Unit = {},
                            forecasts: Map<Long, StockForecast> = emptyMap(), today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()) {
    val meds = state.medications.filter { it.active }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (meds.isEmpty()) item { EmptyState(Icons.Outlined.Inventory2, stringResource(R.string.no_medications), stringResource(R.string.stock_empty_body)) }
        items(meds, key = { it.id }) { m ->
            val sum = stockSummary(m, containers, forecasts[m.id], today, zone)
            val unallocated = records.filter { it.medication_id == m.id && it.deleted_at_utc == null }.sumOf { it.unallocated_supply_amount ?: 0.0 }
            val packages = containers.count { it.medication_id == m.id && it.state != "VOIDED" }
            val checkable = containers.any { it.medication_id == m.id && it.state in CHECKABLE_STATES }
            StockCard(m, sum, unallocated, today, zone, packages, checkable, { onReplace(m) }, { onCheck(m) }, { onAdd(m) }, { onManage(m) }, state.schedules[m.id],
                boxes = containers.filter { it.medication_id == m.id && it.state != "VOIDED" })
        }
    }
}

/** Packages whose remaining amount "Check stock" may correct: open ones and ones the app counted as used up. */
val CHECKABLE_STATES = setOf("IN_USE", "EMPTY")

@Composable private fun StockCard(m: MedicationEntity, s: StockSummary, unallocated: Double, today: LocalDate, zone: ZoneId, packages: Int, checkable: Boolean,
                                  onReplace: () -> Unit, onCheck: () -> Unit, onAdd: () -> Unit, onManage: () -> Unit, schedule: net.plainnotes.app.ScheduleSummary? = null,
                                  boxes: List<ContainerEntity> = emptyList()) {
    val c = MaterialTheme.colorScheme
    // UI-01 D: low stock (the shared forecast's rule) is a tertiary card with the days in words, not only a colour.
    SectionCard(null, Modifier.testTag("stock-card-${m.id}"), container = if (s.low) c.tertiaryContainer else c.surfaceContainerLow) {
        // Three different things: open the next real package, correct an existing one, add packages really obtained.
        // One of them is the card's main button; the others are in the ⋮ menu, unchanged.
        val primary = if (s.sealedCount > 0) R.string.stock_replace else R.string.stock_add
        val menu = (if (primary != R.string.stock_replace && (s.sealedCount > 0 || s.open != null)) listOf(R.string.stock_replace) else emptyList()) +
            (if (checkable) listOf(R.string.stock_check) else emptyList()) + (if (primary != R.string.stock_add) listOf(R.string.stock_add) else emptyList())
        val tracked = !(s.open == null && s.sealedCount == 0 && packages == 0)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // UI-05 C: route · schedule, the existing labels (scheduleText as on the medications page); nothing recomputed.
                Text(listOfNotNull(m.route?.let { choiceLabel(it) }, scheduleText(schedule)).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = if (s.low) c.onTertiaryContainer else c.onSurfaceVariant, modifier = Modifier.testTag("stock-sub-${m.id}"))
            }
            if (tracked) StockMenu(m.id, menu, onReplace, onCheck, onAdd)
        }
        if (s.low) StatusPill(s.daysLeft?.takeIf { s.forecast?.lastCovered != null }?.let { stringResource(R.string.stock_runs_out_in, it) } ?: stringResource(R.string.forecast_empty),
            c.tertiary, c.onTertiary, Icons.Outlined.Warning)
        if (s.open == null && s.sealedCount == 0) {
            if (packages == 0) {
                Text(stringResource(R.string.stock_not_tracked), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                OutlinedButton(onClick = onAdd, modifier = Modifier.testTag("stock-primary-${m.id}")) { Text(stringResource(R.string.stock_start)) }
                TextButton(onClick = onManage, modifier = Modifier.heightIn(min = 48.dp).testTag("stock-manage-${m.id}")) { Text(stringResource(R.string.stock_manage_plain)) }
                return@SectionCard
            }
            // Only used-up or discarded packages: correcting one must not require adding a package that does not exist.
            Text(stringResource(R.string.stock_no_active), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                // UI-07 C: the amount left in the stat style (28 sp, capped at 1.3× font size).
                Text(formatDose(s.remaining, m.unit), style = NotesTheme.typeExtra.statNumber, modifier = Modifier.testTag("stock-remaining-${m.id}"))
                // UI-05 C: the days the shared forecast already computed; nothing without a plan or when it lasts the whole window.
                s.daysLeft?.takeIf { s.forecast?.lastCovered != null }?.let { d ->
                    Text(stringResource(R.string.stock_about_days, d), style = MaterialTheme.typography.titleSmall.tnum(),
                        color = if (s.low) c.onTertiaryContainer else c.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp).testTag("stock-days-${m.id}"))
                }
            }
            // Same sentence and warning as the calendar's stock card, from the same shared forecast.
            val (detail, warn) = stockForecastText(s.forecast, m, today, zone)
            // On the tertiary low-stock card the label above already warns; keep the sentence at full contrast there.
            @Composable fun Sentence() = Text(detail, style = MaterialTheme.typography.bodySmall, color = if (s.low) c.onTertiaryContainer else if (warn) NotesTheme.semantic.attention.accent else c.onSurfaceVariant,
                modifier = Modifier.testTag("stock-forecast-${m.id}"))
            val o = s.open
            if (o == null) Sentence()
            if (o != null) {
                val left = o.capacity - o.used_amount
                LinearProgressIndicator(progress = { (left / o.capacity).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).testTag("stock-bar-${m.id}"), drawStopIndicator = {})
                // UI-05 C: the long sentence is secondary small text under the bar.
                Sentence()
                Text(stringResource(R.string.stock_current, formatDose(left, m.unit), formatDose(o.capacity, m.unit)), style = MaterialTheme.typography.bodySmall)
                o.opened_on?.let { Text(stringResource(R.string.stock_opened, formatShortDate(LocalDate.parse(it))), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
                s.expiresOn?.let { e -> Text(stringResource(if (e.isBefore(today)) R.string.stock_expired else R.string.stock_expires, formatShortDate(e)), style = MaterialTheme.typography.bodySmall,
                    // UI-06: expiring soon and "no opened package" need a look but are not errors: attention, not red.
                    color = if (s.expiringSoon(today)) NotesTheme.semantic.attention.accent else c.onSurfaceVariant, modifier = Modifier.testTag("stock-expiry-${m.id}")) }
            } else Text(stringResource(R.string.stock_none_open), style = MaterialTheme.typography.bodySmall, color = NotesTheme.semantic.attention.accent, modifier = Modifier.testTag("stock-none-open-${m.id}"))
            if (s.sealedCount > 0) Text(stringResource(R.string.stock_sealed, s.sealedCount), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        if (unallocated > 0) Text(stringResource(R.string.stock_unallocated, formatDose(unallocated, m.unit)), style = MaterialTheme.typography.bodySmall, color = c.tertiary)
        Button(onClick = if (primary == R.string.stock_replace) onReplace else onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("stock-primary-${m.id}")) {
            Text(stringResource(primary))
        }
        if (packages > 0) {
            // Collapsed by default; the packages dialog (details, undo, history, voided) opens from inside, unchanged.
            var expanded by rememberSaveable(m.id) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("stock-packages-${m.id}")) {
                Text(stringResource(R.string.stock_packages_section, packages), Modifier.weight(1f))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            // UI-07 D/E: the packages spring open as a connected grouped list; each opens the same packages dialog as "Manage".
            val reduce = LocalReduceMotion.current
            androidx.compose.animation.AnimatedVisibility(expanded,
                enter = if (reduce) androidx.compose.animation.EnterTransition.None else androidx.compose.animation.expandVertically(motionSpec(false)),
                exit = if (reduce) androidx.compose.animation.ExitTransition.None else androidx.compose.animation.shrinkVertically(motionSpec(false))) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    GroupedList(boxes, Modifier.testTag("stock-package-list-${m.id}"), onClick = { onManage() }, itemModifier = { b -> Modifier.testTag("stock-package-${b.id}") }) { b ->
                        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                            Text(packageLabel(b, m), style = MaterialTheme.typography.bodyMedium.tnum())
                        }
                    }
                    TextButton(onClick = onManage, modifier = Modifier.heightIn(min = 48.dp).testTag("stock-manage-${m.id}")) { Text(stringResource(R.string.stock_manage, packages)) }
                }
            }
        }
    }
}

@Composable private fun StockMenu(id: Long, actions: List<Int>, onReplace: () -> Unit, onCheck: () -> Unit, onAdd: () -> Unit) {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }, Modifier.testTag("stock-more-$id")) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.stock_more_actions)) }
        DropdownMenu(open, { open = false }) {
            actions.forEach { a ->
                DropdownMenuItem({ Text(stringResource(a)) }, { open = false; when (a) { R.string.stock_replace -> onReplace(); R.string.stock_check -> onCheck(); else -> onAdd() } },
                    modifier = Modifier.testTag(when (a) { R.string.stock_replace -> "stock-replace-$id"; R.string.stock_check -> "stock-check-$id"; else -> "stock-add-$id" }))
            }
        }
    }
}

/** A package as people see it: amount, opening date, source and batch; never the internal row id. */
@Composable fun packageLabel(box: ContainerEntity, m: MedicationEntity): String = listOfNotNull(
    if (box.state == "IN_USE" || box.state == "EMPTY" || box.state == "VOIDED") stringResource(R.string.pkg_left, formatDose((box.capacity - box.used_amount).coerceAtLeast(0.0), m.unit)) + " / " + formatDose(box.capacity, m.unit)
    else formatDose(box.capacity, m.unit),
    box.opened_on?.let { stringResource(R.string.pkg_opened, formatShortDate(LocalDate.parse(it))) },
    box.source_note, box.batch).joinToString(" · ")

/**
 * "Check stock": pick an open or used-up package and enter what is really left, in the medication's stock unit. It
 * writes one ledger correction to that package; nothing is added and no dose record changes. With several
 * candidates nothing is preselected, so an old package is never revived by accident.
 */
@Composable fun CheckStockDialog(m: MedicationEntity, containers: List<ContainerEntity>, onDismiss: () -> Unit, onSave: (Long, Double) -> Unit) {
    val candidates = containers.filter { it.medication_id == m.id && it.state in CHECKABLE_STATES }
        .sortedWith(compareBy<ContainerEntity>({ if (it.state == "IN_USE") 0 else 1 }, { it.opened_on?.let { d -> -LocalDate.parse(d).toEpochDay() } ?: 0L }, { -it.id }))
    var chosen by remember { mutableStateOf(candidates.singleOrNull()?.id) }
    val box = candidates.firstOrNull { it.id == chosen }
    var left by remember(chosen) { mutableStateOf(box?.let { inputNumber((it.capacity - it.used_amount).coerceAtLeast(0.0)) } ?: "") }
    val v = left.toDoubleOrNull()?.takeIf { box != null && it.isFinite() && it >= 0 && it <= box.capacity }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Tune, null) }, title = { Text(stringResource(R.string.stock_check)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.stock_check_pick), style = MaterialTheme.typography.labelLarge)
            candidates.forEach { c ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(chosen == c.id, role = Role.RadioButton) { chosen = c.id }.testTag("check-package-${c.id}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(chosen == c.id, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (c.state == "IN_USE") R.string.pkg_in_use else R.string.pkg_empty) + " · " + packageLabel(c, m), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (box != null) {
                NumberField(left, { left = it }, stringResource(R.string.stock_check_amount), suffix = unitLabel(m.unit), isError = v == null,
                    supporting = stringResource(R.string.stock_max, formatDose(box.capacity, m.unit)))
                if (v != null && m.dose_per_intake > 0) Text(stringResource(R.string.stock_check_doses, inputNumber(Math.round(v / m.dose_per_intake * 10) / 10.0),
                    formatDose(m.dose_per_intake, m.unit)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.stock_check_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { Button(enabled = box != null && v != null, onClick = { onSave(box!!.id, v!!) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Bounded, keyed package management. History is collapsed initially; each group adds at most 20 rows at a time. */
@Composable fun PackagesDialog(m: MedicationEntity, containers: List<ContainerEntity>, withHistory: Set<Long>, onDismiss: () -> Unit,
                               onInfo: (ContainerEntity) -> Unit, voided: List<ContainerEntity> = emptyList(),
                               onRestore: (ContainerEntity) -> Unit = {}, listState: LazyListState = rememberLazyListState(),
                               onUndo: (ContainerEntity) -> Unit) {
    val order = compareByDescending<ContainerEntity> { it.opened_on.orEmpty() }.thenByDescending { it.id }
    val mine = remember(containers, m.id) { containers.filter { it.medication_id == m.id && it.state != "VOIDED" } }
    val grouped = remember(mine) { mine.groupBy { it.state }.mapValues { (_, boxes) -> boxes.sortedWith(order) } }
    val hidden = remember(voided, m.id) { voided.filter { it.medication_id == m.id && it.state == "VOIDED" }.sortedWith(order) }
    var showVoided by remember(m.id) { mutableStateOf(false) }
    val groups = listOf("IN_USE" to R.string.pkg_in_use, "SEALED" to R.string.pkg_sealed, "EMPTY" to R.string.pkg_empty, "DISCARDED" to R.string.pkg_discarded)
    val expanded = remember(m.id) { mutableStateMapOf("IN_USE" to true, "SEALED" to true, "EMPTY" to false, "DISCARDED" to false) }
    val visible = remember(m.id) { mutableStateMapOf<String, Int>() }
    fun append(group: String, total: Int) {
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        visible[group] = minOf((visible[group] ?: 20) + 20, total)
        // Preserve the viewport even when the load-more button itself was the first visible keyed item.
        listState.requestScrollToItem(index, offset)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(m.name) },
        text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("packages-${m.id}"), state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { (status, label) ->
                val boxes = grouped[status].orEmpty()
                if (boxes.isEmpty()) return@forEach
                item(key = "header-$status", contentType = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${stringResource(label)} (${boxes.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (status == "EMPTY" || status == "DISCARDED") TextButton(onClick = { expanded[status] = expanded[status] != true },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("package-toggle-$status")) {
                            Text(if (expanded[status] == true) stringResource(R.string.pkg_hide) else stringResource(R.string.pkg_show, boxes.size))
                        }
                    }
                }
                if (expanded[status] == true) {
                    val count = visible[status] ?: 20
                    items(boxes.take(count), key = { it.id }, contentType = { "package" }) { box ->
                        Column(Modifier.padding(start = 8.dp).testTag("package-row-${box.id}")) {
                            Text(packageLabel(box, m), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onInfo(box) }, modifier = Modifier.heightIn(min = 48.dp).testTag("package-info-${box.id}")) {
                                Text(stringResource(R.string.wb_package_info))
                            }
                            if (box.id !in withHistory) TextButton(onClick = { onUndo(box) }, modifier = Modifier.heightIn(min = 48.dp).testTag("undo-package-${box.id}")) {
                                Text(stringResource(R.string.pkg_undo))
                            }
                        }
                    }
                    if (count < boxes.size) item(key = "more-$status", contentType = "more") {
                        TextButton(onClick = { append(status, boxes.size) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("package-more-$status")) {
                            Text(stringResource(R.string.pkg_show_more))
                        }
                    }
                }
            }
            if (mine.any { it.id in withHistory }) item(key = "undo-locked", contentType = "notice") {
                Text(stringResource(R.string.pkg_undo_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "advanced-voided", contentType = "advanced") {
                TextButton(onClick = { showVoided = !showVoided; if (!showVoided) visible.remove("VOIDED") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("view-voided")) {
                    Text(stringResource(if (showVoided) R.string.pkg_hide_voided else R.string.pkg_view_voided))
                }
            }
            if (showVoided) {
                if (hidden.isEmpty()) item(key = "voided-empty", contentType = "notice") { Text(stringResource(R.string.pkg_no_voided)) }
                val count = visible["VOIDED"] ?: 20
                items(hidden.take(count), key = { it.id }, contentType = { "voided" }) { box ->
                    Column(Modifier.testTag("voided-package-${box.id}")) {
                        Text(stringResource(R.string.pkg_voided), style = MaterialTheme.typography.titleSmall)
                        Text(packageLabel(box, m))
                        TextButton(onClick = { onRestore(box) }, modifier = Modifier.heightIn(min = 48.dp).testTag("restore-package-${box.id}")) {
                            Text(stringResource(R.string.pkg_restore))
                        }
                    }
                }
                if (count < hidden.size) item(key = "more-VOIDED", contentType = "more") {
                    TextButton(onClick = { append("VOIDED", hidden.size) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("package-more-VOIDED")) {
                        Text(stringResource(R.string.pkg_show_more))
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable fun AddStockDialog(m: MedicationEntity, onDismiss: () -> Unit, onSave: (Double, Int, Boolean,String?,String?) -> Unit) {
    var capacity by remember { mutableStateOf(inputNumber(m.container_capacity)) }; var count by remember { mutableStateOf("1") }
    var openNow by remember { mutableStateOf(false) }
    var source by remember{mutableStateOf("")};var batch by remember{mutableStateOf("")}
    val cap = capacity.toDoubleOrNull()?.takeIf { it > 0 }; val n = count.toIntOrNull()?.takeIf { it in 1..50 }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Inventory2, null) }, title = { Text(stringResource(R.string.stock_add)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall)
            NumberField(count, { count = it }, stringResource(R.string.stock_count), decimal = false, isError = n == null)
            NumberField(capacity, { capacity = it }, stringResource(R.string.capacity), suffix = unitLabel(m.unit), isError = cap == null)
            OutlinedTextField(source,{source=it},label={Text(stringResource(R.string.wb_source_note))})
            OutlinedTextField(batch,{batch=it},label={Text(stringResource(R.string.wb_batch))})
            SwitchRow(stringResource(R.string.stock_open_now), openNow) { openNow = it }
        } },
        confirmButton = { Button(enabled = cap != null && n != null, onClick = { onSave(cap!!, n!!, openNow,source.takeIf{it.isNotBlank()},batch.takeIf{it.isNotBlank()}) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun ContainerInfoDialog(box:ContainerEntity,onDismiss:()->Unit,onSave:(String?,String?)->Unit) {
    var source by remember{mutableStateOf(box.source_note.orEmpty())};var batch by remember{mutableStateOf(box.batch.orEmpty())}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.wb_package_info))},text={Column {
        OutlinedTextField(source,{source=it},label={Text(stringResource(R.string.wb_source_note))})
        OutlinedTextField(batch,{batch=it},label={Text(stringResource(R.string.wb_batch))})
    }},confirmButton={Button(onClick={onSave(source.takeIf{it.isNotBlank()},batch.takeIf{it.isNotBlank()})}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}

/** Explicit second confirmation; scrolls on short screens and never submits on dismissal. */
@Composable fun PackageCorrectionConfirmation(restoring:Boolean,onDismiss:()->Unit,label:String?=null,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(stringResource(if(restoring)R.string.pkg_restore_title else R.string.pkg_undo_title))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){label?.let{Text(it,style=MaterialTheme.typography.titleSmall)};Text(stringResource(if(restoring)R.string.pkg_restore_body else R.string.pkg_undo_body))}},
        confirmButton={TextButton(onClick=onConfirm,modifier=Modifier.heightIn(min=48.dp).testTag("confirm-package-correction")){Text(stringResource(if(restoring)R.string.pkg_restore else R.string.pkg_undo))}},
        dismissButton={TextButton(onClick=onDismiss,modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.cancel))}})
}
