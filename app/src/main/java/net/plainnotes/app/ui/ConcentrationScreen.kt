package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import net.plainnotes.app.conc.EligibilityReason
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.conc.MissingInput
import net.plainnotes.app.conc.TotalE2Calibration
import kotlinx.coroutines.ensureActive
import net.plainnotes.app.pk.BandedCurve
import net.plainnotes.app.pk.CalibrationMode
import net.plainnotes.app.pk.Curve
import net.plainnotes.app.pk.CurveFlag
import net.plainnotes.app.pk.FittedModel
import net.plainnotes.app.pk.PkParams
import net.plainnotes.app.pk.Unsupported
import net.plainnotes.app.pk.Pk
import kotlin.math.roundToInt

class ConcSettings(val pmol: Boolean, val calibrate: Boolean, val mode: CalibrationMode)

/** The page's total and, when labs were fitted, the calibration outcome behind it (shown in the calibration card). */
private class TotalWithOutcome(val curve: E2Total.Curve?, val outcome: TotalE2Calibration.Outcome?)

/**
 * UI-12 (REQUIREMENTS §162): one page for every route. The E2 estimate is the sum of the routes ([E2Total]); the result comes first,
 * one reminder sentence is the only caveat on the page, and sources, scope and limits live behind "Model and literature".
 * Display only: no model, parameter, calibration or export changes here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ConcentrationScreen(state: NotesState, result: ConcentrationResult?, loading: Boolean, weight: Double?, settings: ConcSettings,
                                    onSettings: (ConcSettings) -> Unit, onWeight: (Double) -> Unit, onEditMedication: (Long) -> Unit, onOpenLabs: () -> Unit,
                                    contentPadding: PaddingValues,records:List<net.plainnotes.app.data.RecordEntity> = emptyList(),profiles:Map<Long,net.plainnotes.app.data.ProfileEntity> = emptyMap(),
                                    onConfirmHistory:(net.plainnotes.app.data.MedicationEntity,net.plainnotes.app.data.ProfileEntity?,java.time.LocalDate,java.time.LocalDate)->Unit={_,_,_,_->},onOpenHistory:()->Unit={}, preview:net.plainnotes.app.experimental.ConcentrationPreview?=null,
                                    referenceNotice:@Composable ()->Unit={}, basisNotice:@Composable ()->Unit={},
                                    /** The compatibility result for non-sublingual estradiol only ([E2Total.nonSublingualInputs]); null when there is none. */
                                    e2NonSublingual: ConcentrationResult? = null,
                                    /** Times of the doses actually taken; only those before "now" are drawn as ticks. */
                                    doseTimes: List<java.time.Instant> = emptyList(),
                                    /** SL-CAL-02: the saved lab rows; the common-coefficient calibration reads the E2 ones (never edits them). */
                                    labs: List<net.plainnotes.app.data.LabValueEntity> = emptyList()) {
    var repair by remember{mutableStateOf<net.plainnotes.app.data.MedicationEntity?>(null)}
    var editWeight by remember { mutableStateOf(false) }
    var modelsOpen by rememberSaveable { mutableStateOf(false) }
    val factor = if (settings.pmol) Pk.PMOL_PER_PG else 1.0
    val unit = if (settings.pmol) "pmol/L" else "pg/mL"
    val chartLocale=currentLocale()
    val chartNumber=remember(chartLocale){java.text.NumberFormat.getNumberInstance(chartLocale).apply{maximumFractionDigits=3;isGroupingUsed=false}}
    fun fmt(v: Double) = (v * factor).roundToInt().toString()
    val compatibilityHasSl = result?.models?.get(Curve.E2)?.any { it.key=="E2_SL" } == true
    val previewReady = preview?.timeH?.isNotEmpty() == true
    // SL-CAL-02: with sublingual doses the labs scale the whole total by one coefficient ([TotalE2Calibration]); the fit and a mixed total read M2 at many
    // points, so they are built off the main thread; a single uncalibrated route is O(1).
    val wantCalibration = settings.calibrate && previewReady && labs.any { it.analyte_code == net.plainnotes.app.conc.ConcentrationCalculator.E2_CODE }
    val needsAsync = previewReady && (e2NonSublingual != null || wantCalibration)
    val firstDoseH = doseTimes.firstOrNull()?.let { net.plainnotes.app.conc.ConcentrationCalculator.hours(it) }
    fun build(cancel: () -> Unit): TotalWithOutcome {
        val outcome = if (wantCalibration) E2Calibration.run(preview, e2NonSublingual, labs, result, firstDoseH, preview?.asOfH ?: result?.nowH ?: 0.0, cancel) else null
        return TotalWithOutcome(E2Total.curve(preview, result, e2NonSublingual, outcome as? TotalE2Calibration.Calibrated), outcome)
    }
    val built: TotalWithOutcome? = if (!needsAsync) remember(preview, result, e2NonSublingual) { build {} }
        else produceState<TotalWithOutcome?>(null, preview, result, e2NonSublingual, labs, wantCalibration, firstDoseH) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { build { ensureActive() } } }.value
    val total: E2Total.Curve? = built?.curve
    val calibrationOutcome = built?.outcome
    val hasTotal = total != null
    val pending = needsAsync && !hasTotal
    val missing = result?.missing.orEmpty()
    val statusCards: @Composable () -> Unit = {
        if (missing.any { it.medicationId == null && it.input == MissingInput.WEIGHT }) SectionCard(stringResource(R.string.pk_need_weight_title)) {
            Text(stringResource(R.string.pk_need_weight_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { editWeight = true }) { Text(stringResource(R.string.pk_set_weight)) }
        }
        val perMed = missing.filter { it.medicationId != null }.groupBy { it.medicationId!! }
        if (perMed.isNotEmpty()) SectionCard(stringResource(R.string.pk_missing_title)) {
            perMed.forEach { (id, list) ->
                val med = state.medications.firstOrNull { it.id == id } ?: return@forEach
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(med.name, style = MaterialTheme.typography.titleSmall)
                        val labels = list.map { missingLabel(it.input) }
                        Text(labels.joinToString(stringResource(R.string.list_separator)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        if(list.any{it.input==MissingInput.ACTUAL_DOSE})TextButton(onClick=onOpenHistory){Text(stringResource(R.string.history))}
                        if(list.any{it.input==MissingInput.HISTORICAL_CONTEXT || it.input in listOf(MissingInput.GEL_PRODUCT,MissingInput.SL_TIER,MissingInput.PATCH_RELEASE,MissingInput.ROUTE_OR_ESTER)} && records.any{it.medication_id==id && it.deleted_at_utc==null && it.taken_utc!=null && net.plainnotes.app.data.HistoricalContext.incomplete(it)})
                            TextButton(onClick={repair=med}){Text(stringResource(R.string.history_context_repair))}
                        TextButton(onClick = { onEditMedication(id) }) { Text(stringResource(R.string.complete_info)) }
                    }
                }
            }
        }
        val unsupported = result?.unsupported.orEmpty()
        if (unsupported.isNotEmpty()) SectionCard(stringResource(R.string.pk_unsupported_title)) {
            unsupported.forEach { (id, why) ->
                val med = state.medications.firstOrNull { it.id == id } ?: return@forEach
                Text(med.name, style = MaterialTheme.typography.titleSmall)
                Text(unsupportedLabel(why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        referenceNotice()
        if (state.medications.none { it.molecule in listOf("E2", "CPA", "SPI", "P4") }) EmptyState(Icons.AutoMirrored.Outlined.ShowChart, stringResource(R.string.pk_no_e2_title), stringResource(R.string.pk_no_e2_body))
        if (!hasTotal && !pending && compatibilityHasSl) Text(stringResource(R.string.m2_compatibility_contains_sl),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("m2-unsafe-compatibility"))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Missing-input cards follow the estimate; without one they come first.
        if (!hasTotal) statusCards()
        if (total != null) {
            E2EstimateCard(total, result, ::fmt, unit)
            var rangeDays by rememberSaveable { mutableIntStateOf(3) }
            SectionCard(null) {
                Text(stringResource(R.string.ui12_curve_title), style = MaterialTheme.typography.titleMedium)
                val ranges = listOf(3, 7)
                AdaptiveChoice(ranges.map { stringResource(R.string.days_short, it) }, ranges.indexOf(rangeDays), { rangeDays = ranges[it] })
                val nowH = total.nowH
                val noon = stringResource(R.string.ui12_noon_tick)
                val spoken = stringResource(if (settings.pmol) R.string.ui12_unit_pmol_spoken else R.string.ui12_unit_pg_spoken)
                val data = remember(total, result, factor, doseTimes, rangeDays, noon, spoken, unit) {
                    E2Total.chartData(total, result?.labs.orEmpty(), factor, doseTimes, rangeDays, unit, noon, spoken)
                }
                val hasDoseMarks = data.marks.isNotEmpty()
                key(rangeDays) {
                    ConcChart(data, nowH - rangeDays * 24.0, nowH + if (rangeDays == 3) 12.0 else 24.0, Modifier.fillMaxWidth().height(260.dp).testTag("e2-chart")) { if(it>=10)it.roundToInt().toString() else chartNumber.format(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Legend(stringResource(R.string.legend_recorded), MaterialTheme.colorScheme.primary, dashed = false)
                    Legend(stringResource(R.string.ui12_legend_tail), MaterialTheme.colorScheme.primary, dashed = true)
                    if (!result?.labs.isNullOrEmpty()) Legend(stringResource(R.string.legend_labs), MaterialTheme.colorScheme.tertiary, dot = true)
                    if (hasDoseMarks) Legend(stringResource(R.string.legend_doses), MaterialTheme.colorScheme.secondary, tick = true)
                }
                Text(stringResource(R.string.pk_unit),style=MaterialTheme.typography.bodyMedium)
                AdaptiveChoice(listOf("pg/mL","pmol/L"),if(settings.pmol)1 else 0,{onSettings(ConcSettings(it==1,settings.calibrate,settings.mode))})
            }
            statusCards()
        } else if (pending || loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (!previewReady && result != null && result.usedDoses==0 && result.others.isEmpty() && !compatibilityHasSl && state.medications.any { it.molecule in listOf("E2", "CPA", "SPI", "P4") })
            EmptyState(Icons.AutoMirrored.Outlined.ShowChart, stringResource(R.string.pk_no_doses_title), stringResource(R.string.pk_no_doses_body))

        if (result != null && result.others.isNotEmpty()) OtherCurvesCard(result)

        if (compatibilityHasSl || previewReady) SublingualCalibrationCard(previewReady, settings, onSettings, calibrationOutcome, result?.labs.isNullOrEmpty(), onOpenLabs)
        else CalibrationCard(result, settings, onSettings, onOpenLabs, ::fmt, unit)

        SectionCard(stringResource(R.string.pk_model_title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pk_weight), style = MaterialTheme.typography.bodyLarge)
                    Text(weight?.let { stringResource(R.string.kg_value, displayNumber(it)) } ?: stringResource(R.string.not_set), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { editWeight = true }) { Text(stringResource(R.string.edit)) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = stringResource(R.string.ui12_models_title)) { modelsOpen = true }.testTag("models-literature-open"),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui12_models_title), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.ui12_models_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (modelsOpen) ModelsAndLiteratureWindow(result, e2NonSublingual, previewReady, basisNotice) { modelsOpen = false }
    repair?.let{med->HistoricalContextDialog(med,profiles[med.id],records.map{r->r.copy(config_snapshot=net.plainnotes.app.data.HistoricalContext.resolved(r,state.ruleSnapshots))},{repair=null}){m,p,from,to->onConfirmHistory(m,p,from,to);repair=null}}
    if (editWeight) WeightDialog(weight, { editWeight = false }) { onWeight(it); editWeight = false }
}

@Composable private fun routeName(route: E2Total.Route) = stringResource(when (route) {
    E2Total.Route.SUBLINGUAL -> R.string.ui12_route_sl; E2Total.Route.ORAL -> R.string.ui12_route_oral; E2Total.Route.GEL -> R.string.ui12_route_gel
    E2Total.Route.PATCH -> R.string.ui12_route_patch; E2Total.Route.INJECTION -> R.string.ui12_route_injection
})

/** The result first: label, the total now, the routes added and how many doses it rests on, and the one reminder sentence. */
@Composable private fun E2EstimateCard(total: E2Total.Curve, result: ConcentrationResult?, fmt: (Double) -> String, unit: String) {
    val locale = currentLocale()
    fun cap(text: String) = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val names = total.routes.map { routeName(it) }
    ElevatedCard(Modifier.fillMaxWidth().testTag("e2-estimate-card"), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), elevation = CardDefaults.elevatedCardElevation(0.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val label = if (total.routes.size == 1) stringResource(R.string.ui12_label_single, cap(names.single())) else stringResource(R.string.ui12_label_total)
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("e2-label"))
                if (total.calibrated) StatusPill(stringResource(R.string.ui12_label_calibrated), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(total.nowValue?.let(fmt) ?: "—", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("e2-now-value"))
                Spacer(Modifier.width(6.dp))
                Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(bottom = 8.dp))
            }
            val phrase = if (names.size == 1) cap(names.single()) else stringResource(R.string.ui12_sum_phrase,
                names.dropLast(1).let { head -> if (head.size == 1) stringResource(R.string.ui12_list_last, head.single(), names.last())
                    else stringResource(R.string.ui12_list_last, head.joinToString(stringResource(R.string.list_separator)), names.last()) })
            Text(stringResource(R.string.ui12_basis, phrase, total.doseCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("e2-basis"))
            if ((result?.skippedDoses ?: 0) > 0) Text(stringResource(R.string.pk_skipped_doses, result!!.skippedDoses), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ui12_reminder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("e2-reminder"))
        }
    }
}

/**
 * "Model and literature": every explanation that used to sit on the page (nothing is deleted, only moved here): the research note,
 * calculation time and basis, assumptions, weight and parameter scope, sources, flags and the model cards.
 */
@Composable private fun ModelsAndLiteratureWindow(result: ConcentrationResult?, nonSublingual: ConcentrationResult?, previewReady: Boolean,
                                                 basisNotice: @Composable () -> Unit, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("models-literature-window"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    NavBackButton(onClose, "models-literature-back")
                    Text(stringResource(R.string.ui12_models_title), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (previewReady) Text(stringResource(R.string.m2_scenario_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("research-not-personal"))
                    basisNotice()
                    Text(stringResource(R.string.pk_disclaimer_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.ui12_baseline_once), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("baseline-once-note"))
                    Text(stringResource(if (previewReady) R.string.m2_weight_scope else R.string.pk_weight_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Text(stringResource(if (previewReady) R.string.m2_parameter_scope else R.string.pk_assumptions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.pk_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (result?.flags?.get(Curve.E2).orEmpty() + nonSublingual?.flags?.get(Curve.E2).orEmpty()).let { FlagNotes(it) }
                    val sublingualInResult = result?.models?.get(Curve.E2)?.any { it.key == "E2_SL" } == true
                    val models = result?.models.orEmpty().mapValues { (curve, set) ->
                        when {
                            curve != Curve.E2 -> set
                            previewReady || sublingualInResult -> nonSublingual?.models?.get(Curve.E2).orEmpty()   // sublingual is M2 (above); never the retired model card
                            else -> set
                        }
                    }.filterValues { it.isNotEmpty() }
                    if (models.isNotEmpty()) ModelsCard(models)
                }
            }
        }
    }
}

/**
 * SL-CAL-02: calibration of the total when sublingual doses are recorded. The switch is the same setting as before; the card says in plain
 * words what was done (labs used, how well they agree, how much they say) or why nothing was done. No interval is shown.
 */
@Composable private fun SublingualCalibrationCard(m2Ready: Boolean, settings: ConcSettings, onSettings: (ConcSettings) -> Unit, outcome: TotalE2Calibration.Outcome?,
                                                  noLabs: Boolean, onOpenLabs: () -> Unit) {
    SectionCard(stringResource(R.string.calib_title)) {
        // Label and switch share a row (the switch never leaves the screen); the status pill has its own line so a long word (French, 2x text) can wrap.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.calib_enable), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = m2Ready && settings.calibrate, onCheckedChange = { onSettings(ConcSettings(settings.pmol, it, settings.mode)) }, enabled = m2Ready, modifier = Modifier.testTag("calibration-switch"))
        }
        val (pill, container, content) = when {
            !m2Ready -> Triple(R.string.ui12_calib_unavailable, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
            !settings.calibrate -> Triple(R.string.ui12_cal_off, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
            outcome is TotalE2Calibration.Calibrated -> Triple(R.string.ui12_label_calibrated, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            else -> Triple(R.string.ui12_cal_not_applied, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill(stringResource(pill), container, content)
        when {
            !m2Ready -> Text(stringResource(R.string.ui12_calib_unavailable_sl), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("calibration-unavailable-sl"))
            !settings.calibrate -> Text(stringResource(R.string.ui12_cal_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("calibration-explain"))
            noLabs || outcome == null -> Text(stringResource(R.string.calib_no_labs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("calibration-no-labs"))
            outcome is TotalE2Calibration.Calibrated -> Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("calibration-result")) {
                Text(stringResource(R.string.ui12_cal_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.ui12_cal_used, outcome.usedLabIds.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("calibration-used"))
                Text(stringResource(if (TotalE2Calibration.tier(outcome.informationGain) == TotalE2Calibration.Tier.SUFFICIENT) R.string.ui12_cal_tier_sufficient else R.string.ui12_cal_tier_limited),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("calibration-tier"))
                val loo = outcome.leaveOneOut
                Text(if (loo == null) stringResource(R.string.ui12_cal_loo_single) else stringResource(R.string.ui12_cal_loo, loo.medianPercent.roundToInt()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("calibration-loo"))
                Text(stringResource(R.string.ui12_cal_time_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("calibration-time-note"))
                CalibrationNotes(outcome.decisions)
            }
            outcome is TotalE2Calibration.Refused -> Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("calibration-refused")) {
                Text(stringResource(if (outcome.reasons.contains(TotalE2Calibration.Reason.INSUFFICIENT_INFORMATION)) R.string.ui12_cal_refused_info else R.string.ui12_cal_refused_none),
                    style = MaterialTheme.typography.bodyMedium)
                CalibrationNotes(outcome.decisions)
            }
        }
        OutlinedButton(onClick = onOpenLabs, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.calib_manage_labs)) }
    }
}

/** How many labs count for less, and why each unused lab was left out. */
@Composable private fun CalibrationNotes(decisions: List<TotalE2Calibration.LabDecision>) {
    val down = decisions.count { it.use == TotalE2Calibration.Use.DOWNWEIGHT }
    if (down > 0) Text(stringResource(R.string.ui12_cal_downweighted, down), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("calibration-downweighted"))
    decisions.filter { it.use == TotalE2Calibration.Use.REJECT }.flatMap { d -> d.reasons }.groupingBy { it }.eachCount().forEach { (reason, count) ->
        val text = stringResource(when (reason) {
            TotalE2Calibration.Reason.HISTORY_NOT_ELIGIBLE -> R.string.ui12_cal_r_history
            TotalE2Calibration.Reason.LAB_TIME_NOT_PRECISE -> R.string.ui12_cal_r_time
            TotalE2Calibration.Reason.SL_LAST_DOSE_UNKNOWN -> R.string.ui12_cal_r_sl_unknown
            else -> R.string.ui12_cal_r_invalid
        })
        Text(stringResource(R.string.ui12_cal_skipped, count, text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("calibration-skipped"))
    }
}

@Composable internal fun CalibrationCard(result: ConcentrationResult?, settings: ConcSettings, onSettings: (ConcSettings) -> Unit, onOpenLabs: () -> Unit, fmt: (Double) -> String, unit: String) {
    SectionCard(stringResource(R.string.calib_title)) {
        Text(stringResource(R.string.calib_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.calib_sample_time_semantics), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.calib_safety_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRow(stringResource(R.string.calib_enable), settings.calibrate) { onSettings(ConcSettings(settings.pmol, it, settings.mode)) }
        CalibrationEligibilitySection(result,fmt,unit)
        result?.let { Text(stringResource(R.string.calib_evaluation_time,formatDateTime(java.time.Instant.ofEpochMilli(kotlin.math.round(it.evaluationH*3_600_000).toLong()))),style=MaterialTheme.typography.bodySmall) }
        val s = result?.calibration
        if (s == null) Text(stringResource(if (settings.mode==CalibrationMode.CAUSAL) R.string.calib_no_available_sample else if (result?.labEligibility?.isNotEmpty()==true) R.string.calib_history_not_fitted else R.string.calib_no_labs), style = MaterialTheme.typography.bodyMedium)
        else {
            val m = s.model
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(stringResource(R.string.calib_labs), m.postDoseObservationCount.toString(), Modifier.weight(1f))
                Metric(stringResource(R.string.calib_convergence), "${((s.diagnostics?.convergenceScore ?: 0.0) * 100).roundToInt()}%", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(stringResource(R.string.calib_amplitude), "×" + displayNumber(kotlin.math.exp(m.logAmplitude), 2), Modifier.weight(1f))
                Metric(stringResource(if (!m.rateAdjustable) R.string.calib_rate_fixed else if (result.models[Curve.E2]?.any { it.key == "E2_SL" } == true) R.string.calib_rate_non_sl else R.string.calib_clearance), "×" + displayNumber(kotlin.math.exp(m.logRate), 2), Modifier.weight(1f))
            }
            m.baselinePGmL?.let { Text(stringResource(R.string.calib_baseline, fmt(it), unit), style = MaterialTheme.typography.bodySmall) }
            s.diagnostics?.takeIf { m.postDoseObservationCount > 0 }?.let { d ->
                Text(stringResource(R.string.calib_last, fmt(d.observedPGmL), fmt(d.predictedPGmL), unit), style = MaterialTheme.typography.bodySmall)
                if (d.isOutlier) Text(stringResource(R.string.calib_outlier), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text(stringResource(if(d.excludedFromFit)R.string.calib_last_excluded else if(d.usedInFit)R.string.calib_last_used else R.string.calib_last_not_used),style=MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.calib_fit_counts,s.labCount,m.postDoseObservationCount,m.excludedLabIds.size),style=MaterialTheme.typography.bodySmall)
            if(m.warningLabIds.any { it in m.usedLabIds })Text(stringResource(R.string.calib_warning_used),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            if (m.postDoseObservationCount < 3) Text(stringResource(R.string.calib_few), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        }
        Text(stringResource(R.string.calib_mode), style = MaterialTheme.typography.labelLarge)
        AdaptiveChoice(listOf(stringResource(R.string.calib_retro),stringResource(R.string.calib_causal)),if(settings.mode==CalibrationMode.RETROSPECTIVE)0 else 1,
            {onSettings(ConcSettings(settings.pmol,settings.calibrate,if(it==0)CalibrationMode.RETROSPECTIVE else CalibrationMode.CAUSAL))})
        Text(stringResource(if (settings.mode == CalibrationMode.RETROSPECTIVE) R.string.calib_retro_desc else R.string.calib_causal_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onOpenLabs,modifier=Modifier.heightIn(min=48.dp)) { Icon(Icons.Outlined.Science, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.calib_manage_labs)) }
    }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable private fun Legend(label: String, color: androidx.compose.ui.graphics.Color, dashed: Boolean = false, dot: Boolean = false, block: Boolean = false, tick: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 22.dp, height = 10.dp)) {
            val y = size.height / 2
            when {
                dot -> drawCircle(color, size.height / 2, center)
                tick -> drawLine(color, androidx.compose.ui.geometry.Offset(size.width / 2, 0f), androidx.compose.ui.geometry.Offset(size.width / 2, size.height), 1.5.dp.toPx())
                block -> drawRect(color)
                else -> drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 2.dp.toPx(),
                    pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null)
            }
        }
        Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun missingLabel(m: MissingInput) = stringResource(when (m) {
    MissingInput.WEIGHT -> R.string.missing_weight; MissingInput.ROUTE_OR_ESTER -> R.string.missing_route; MissingInput.UNIT_NOT_MG -> R.string.missing_unit_mg
    MissingInput.PATCH_RELEASE -> R.string.missing_patch_release; MissingInput.PATCH_UNIT -> R.string.missing_patch_unit; MissingInput.GEL_PRODUCT -> R.string.missing_gel_product
    MissingInput.SL_TIER -> R.string.missing_sl_tier
    MissingInput.ROUTE_NOT_MODELLED -> R.string.missing_route_not_modelled
    MissingInput.HISTORICAL_CONTEXT -> R.string.history_context_unknown
    MissingInput.ACTUAL_DOSE -> R.string.missing_actual_dose
})

@Composable fun unsupportedLabel(u: Unsupported) = stringResource(when (u) {
    Unsupported.SUBLINGUAL_EV -> R.string.unsupported_sl_ev; Unsupported.NO_LITERATURE_ESTER -> R.string.unsupported_ester
    Unsupported.NO_LITERATURE_ROUTE -> R.string.unsupported_route; Unsupported.BICALUTAMIDE -> R.string.unsupported_bica
    Unsupported.PATCH_WITHOUT_RATE -> R.string.unsupported_patch_rate
})

@Composable fun curveLabel(c: Curve) = stringResource(when (c) {
    Curve.E2 -> R.string.pk_chart_title; Curve.CPA -> R.string.choice_cpa; Curve.SPIRONOLACTONE -> R.string.choice_spi
    Curve.CANRENONE -> R.string.curve_canrenone; Curve.PROGESTERONE -> R.string.choice_p4
})

@Composable private fun FlagNotes(flags: Set<CurveFlag>) {
    flags.sortedBy { it.ordinal }.forEach { f ->
        Text(stringResource(when (f) {
            CurveFlag.EXTRAPOLATED_TIER -> R.string.flag_extrapolated_tier; CurveFlag.EXTRAPOLATED_AFTER_CALIBRATED_HOURS -> R.string.flag_after_8h
            CurveFlag.NO_PRODUCT_DATA -> R.string.flag_no_product_data; CurveFlag.ILLUSTRATIVE -> R.string.flag_illustrative
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** CPA, spironolactone, canrenone and progesterone: one chart at a time, each with its own unit and axis. UI-12: 3 or 7 days, no interval bands. */
@Composable private fun OtherCurvesCard(result: ConcentrationResult) {
    val curves = result.others.keys.sortedBy { it.ordinal }
    var selected by remember(curves) { mutableStateOf(curves.first()) }
    var rangeDays by rememberSaveable { mutableIntStateOf(3) }
    SectionCard(stringResource(R.string.pk_other_title)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            curves.forEach { c -> FilterChip(selected == c, { selected = c }, label = { Text(curveLabel(c)) }) }
        }
        val b: BandedCurve = result.others.getValue(selected)
        val noon = stringResource(R.string.ui12_noon_tick)
        val data = ChartData(b.timeH, b.center, null, null, result.nowH, unit = selected.unit, axisDays = true, noonLabel = noon, lineWidthDp = if (rangeDays == 3) 2.6f else 2f)
        val ranges=listOf(3,7)
        AdaptiveChoice(ranges.map{stringResource(R.string.days_short,it)},ranges.indexOf(rangeDays),{rangeDays=ranges[it]})
        val loc = currentLocale()
        val nf = remember(loc) { java.text.NumberFormat.getNumberInstance(loc).apply { maximumFractionDigits = 1; isGroupingUsed = false } }
        key(selected, rangeDays) {
            ConcChart(data, result.nowH - rangeDays * 24.0, result.nowH + if (rangeDays == 3) 12.0 else 24.0, Modifier.fillMaxWidth().height(220.dp).testTag("other-chart")) { v ->
                if (v >= 10) v.roundToInt().toString() else nf.format(v) }
        }
        FlagNotes(result.flags[selected].orEmpty())
        when (selected) {
            Curve.CPA -> R.string.note_cpa; Curve.SPIRONOLACTONE, Curve.CANRENONE -> R.string.note_spi; Curve.PROGESTERONE -> R.string.note_p4; else -> null
        }?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun modelLabel(key: String) = stringResource(when (key) {
    "EV_ORAL" -> R.string.model_ev_oral; "E2_ORAL" -> R.string.model_e2_oral; "CPA_ORAL" -> R.string.model_cpa_oral; "E2_PATCH" -> R.string.model_e2_patch
    "E2_GEL_ESTROGEL" -> R.string.model_gel_estrogel; "E2_GEL_DIVIGEL" -> R.string.model_gel_divigel; "E2_GEL_OTHER" -> R.string.model_gel_other
    "EV_IM" -> R.string.model_ev_im; "E2_SL" -> R.string.model_e2_sl; "SPI_PARENT" -> R.string.model_spi_parent; "SPI_CANRENONE" -> R.string.model_spi_canrenone
    "P4_ORAL" -> R.string.model_p4_oral; else -> R.string.choice_other
})

/** Every curve's model, its fitted numbers and the literature it was fitted to. */
@Composable private fun ModelsCard(models: Map<Curve, Set<FittedModel>>) {
    var open by remember { mutableStateOf<String?>(null) }
    SectionCard(stringResource(R.string.pk_models_title)) {
        Text(stringResource(R.string.pk_models_intro, PkParams.version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        models.values.flatten().distinctBy { it.key }.sortedBy { it.key }.forEach { m ->
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(modelLabel(m.key), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { open = if (open == m.key) null else m.key }) { Text(stringResource(if (open == m.key) R.string.pk_model_hide else R.string.pk_model_show)) }
            }
            val nf = java.text.NumberFormat.getNumberInstance(currentLocale()).apply { maximumFractionDigits = 1; isGroupingUsed = false }
            val halfLives = m.terms.joinToString(" / ") { nf.format(kotlin.math.ln(2.0) / it.second) }
            Text(stringResource(R.string.pk_model_params, displayNumber(m.ka, 3), halfLives, (m.cv * 100).roundToInt(), m.unit), style = MaterialTheme.typography.bodySmall)
            if (open == m.key) {
                Text(m.assumption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.cvSource.isNotEmpty()) Text("CV: " + m.cvSource, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                m.basis.forEach { b ->
                    val ref = PkParams.reference(b.substringBefore(" ("))
                    val cite = ref?.let { "${it.optString("authors")} (${it.optString("year")}). ${it.optString("title")}. ${it.optString("journal")}" + (it.optString("pmid").takeIf { p -> p.isNotEmpty() }?.let { p -> " PMID $p" } ?: "") }
                    Text("• " + (cite ?: b), style = MaterialTheme.typography.bodySmall)
                    if (ref != null && b.contains(" (")) Text("  " + b.substringAfter(" (").removeSuffix(")"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable fun WeightDialog(current: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(current?.let(::inputNumber) ?: "") }
    val v = text.toDoubleOrNull()?.takeIf { it in 25.0..300.0 }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.pk_weight)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(text, { text = it }, stringResource(R.string.pk_weight), suffix = "kg", isError = text.isNotEmpty() && v == null)
            Text(stringResource(R.string.pk_weight_note), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(onClick = { onSave(v!!) }, enabled = v != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun PkDisclaimerDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Info, null) }, title = { Text(stringResource(R.string.pk_disclaimer_title)) },
        text = { Text(stringResource(R.string.pk_disclaimer_body)) },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.pk_disclaimer_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}


/** Original chart observations are unchanged; every history decision is available without logging facts. */
@Composable internal fun CalibrationEligibilitySection(result: ConcentrationResult?, fmt: (Double)->String, unit: String) {
    val rows=result?.labEligibility.orEmpty()
    if(rows.isEmpty() && result?.models?.containsKey(Curve.E2)!=true)return
    var details by remember{mutableStateOf(false)}
    val cutoff=if(result?.mode==CalibrationMode.CAUSAL)minOf(result.evaluationH,result.nowH) else result?.nowH ?: Double.POSITIVE_INFINITY
    val available=rows.filter{it.sampleH<=cutoff}
    Text(stringResource(R.string.calib_history_counts,available.count{it.eligible},available.count{!it.eligible}),style=MaterialTheme.typography.bodySmall)
    if(available.none{it.eligible} && result?.e2?.isNotEmpty()==true)Text(stringResource(R.string.calib_history_population),style=MaterialTheme.typography.bodySmall)
    if(rows.isNotEmpty())OutlinedButton(onClick={details=true},modifier=Modifier.heightIn(min=48.dp)){Text(stringResource(R.string.calib_history_details))}
    if(details)AlertDialog(onDismissRequest={details=false},title={Text(stringResource(R.string.calib_history_details))},
        text={LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item { Text(stringResource(R.string.calib_history_total,rows.count { it.eligible }),style=MaterialTheme.typography.bodySmall) }
            itemsIndexed(rows){index,row->Column{
                val at=java.time.Instant.ofEpochMilli((row.sampleH*3_600_000).toLong()).atZone(java.time.ZoneId.systemDefault())
                Text(at.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                result?.labs?.getOrNull(index)?.let{Text(fmt(it.second)+" "+unit)}
                Text(stringResource(if(row.eligible && row.sampleH>cutoff)R.string.calib_time_pending else if(row.eligible)R.string.calib_history_eligible else R.string.calib_history_excluded))
                val fit=result?.currentEvaluation?.fitDisposition ?: result?.calibration?.model
                if(row.eligible && row.sampleH<=cutoff && fit!=null){
                    val id="l${row.labId}"
                    if(id in fit.warningLabIds)Text(stringResource(R.string.calib_residual_warning),style=MaterialTheme.typography.bodySmall)
                    Text(stringResource(when {
                        id in fit.usedLabIds -> R.string.calib_fit_used
                        id in fit.excludedLabIds -> R.string.calib_fit_excluded
                        id in fit.baselineLabIds -> R.string.calib_fit_baseline
                        else -> when(fit.ignoredLabReasons[id]) {
                            net.plainnotes.app.pk.LabNotFittedReason.INVALID_CONCENTRATION -> R.string.calib_fit_invalid_value
                            net.plainnotes.app.pk.LabNotFittedReason.INVALID_TIME -> R.string.calib_fit_invalid_time
                            net.plainnotes.app.pk.LabNotFittedReason.UNCONFIRMED_PRE_TREATMENT -> R.string.calib_fit_unconfirmed
                            net.plainnotes.app.pk.LabNotFittedReason.NO_E2_EVENTS -> R.string.calib_fit_no_exposure
                            net.plainnotes.app.pk.LabNotFittedReason.DUPLICATE_ID -> R.string.calib_fit_duplicate
                            null -> R.string.calib_fit_not_candidate
                        }
                    }),style=MaterialTheme.typography.bodySmall)
                }
                row.reasons.forEach{reason->Text(stringResource(when(reason){
                    EligibilityReason.READ_SCOPE_UNKNOWN->R.string.calib_history_scope
                    EligibilityReason.SAMPLE_OUTSIDE_WINDOW->R.string.calib_history_window
                    EligibilityReason.READ_END_BEFORE_SAMPLE->R.string.calib_history_future
                    EligibilityReason.UNKNOWN_CONTEXT->R.string.calib_history_context
                    EligibilityReason.UNKNOWN_DOSE->R.string.calib_history_dose
                    EligibilityReason.UNKNOWN_TIME->R.string.calib_history_time
                    EligibilityReason.UNMODELLED_EXPOSURE->R.string.calib_history_unmodelled
                    EligibilityReason.OMITTED_EXPOSURE->R.string.calib_history_omitted
                    EligibilityReason.NO_TREATMENT_EVIDENCE->R.string.calib_history_no_start
                    EligibilityReason.RESOURCE_LIMIT->R.string.calib_history_resource
                }),style=MaterialTheme.typography.bodySmall)}
            }}
        }},confirmButton={TextButton(onClick={details=false}){Text(stringResource(R.string.calib_history_done))}})
}
