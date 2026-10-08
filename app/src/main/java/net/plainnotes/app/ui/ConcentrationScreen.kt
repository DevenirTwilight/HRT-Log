package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.plainnotes.app.NotesState
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.conc.MissingInput
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

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ConcentrationScreen(state: NotesState, result: ConcentrationResult?, loading: Boolean, weight: Double?, settings: ConcSettings,
                                    onSettings: (ConcSettings) -> Unit, onWeight: (Double) -> Unit, onEditMedication: (Long) -> Unit, onOpenLabs: () -> Unit,
                                    contentPadding: PaddingValues,records:List<net.plainnotes.app.data.RecordEntity> = emptyList(),profiles:Map<Long,net.plainnotes.app.data.ProfileEntity> = emptyMap(),
                                    onConfirmHistory:(net.plainnotes.app.data.MedicationEntity,net.plainnotes.app.data.ProfileEntity?,java.time.LocalDate,java.time.LocalDate)->Unit={_,_,_,_->},onOpenHistory:()->Unit={}) {
    var repair by remember{mutableStateOf<net.plainnotes.app.data.MedicationEntity?>(null)}
    var editWeight by remember { mutableStateOf(false) }
    val factor = if (settings.pmol) Pk.PMOL_PER_PG else 1.0
    val unit = if (settings.pmol) "pmol/L" else "pg/mL"
    val chartLocale=currentLocale()
    val chartNumber=remember(chartLocale){java.text.NumberFormat.getNumberInstance(chartLocale).apply{maximumFractionDigits=3;isGroupingUsed=false}}
    fun fmt(v: Double) = (v * factor).roundToInt().toString()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.pk_disclaimer_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        val missing = result?.missing.orEmpty()
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
                        Text(labels.joinToString(stringResource(R.string.list_separator)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
        if (state.medications.none { it.molecule in listOf("E2", "CPA", "SPI", "P4") }) EmptyState(Icons.AutoMirrored.Outlined.ShowChart, stringResource(R.string.pk_no_e2_title), stringResource(R.string.pk_no_e2_body))

        if (result != null && result.timeH.isNotEmpty() && result.models.containsKey(Curve.E2)) {
            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), elevation = CardDefaults.elevatedCardElevation(0.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.pk_current), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(result.currentPgMl?.let(::fmt) ?: "—", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    val calibrated = result.calibration != null
                    Text(stringResource(if (calibrated) R.string.pk_basis_calibrated else R.string.pk_basis_population, result.usedDoses),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (result.skippedDoses > 0) Text(stringResource(R.string.pk_skipped_doses, result.skippedDoses), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            var rangeDays by remember { mutableIntStateOf(14) }
            var fullBand by remember { mutableStateOf(false) }
            SectionCard(null) {
                Text(stringResource(R.string.pk_chart_title),style=MaterialTheme.typography.titleMedium)
                val ranges=listOf(7,14,60)
                AdaptiveChoice(ranges.map{stringResource(R.string.days_short,it)},ranges.indexOf(rangeDays),{rangeDays=ranges[it]})
                val data = ChartData(result.timeH, DoubleArray(result.e2.size) { result.e2[it] * factor },
                    result.bandInner?.let { (l, h) -> DoubleArray(l.size) { l[it] * factor } to DoubleArray(h.size) { h[it] * factor } },
                    result.bandOuter?.let { (l, h) -> DoubleArray(l.size) { l[it] * factor } to DoubleArray(h.size) { h[it] * factor } },
                    result.nowH, result.labs.map { it.first to it.second * factor }, unit = unit,includeBandsInScale=fullBand)
                key(rangeDays) {
                    ConcChart(data, result.nowH - rangeDays * 24 * 0.75, result.nowH + rangeDays * 24 * 0.25, Modifier.fillMaxWidth().height(260.dp)) { if(it>=10)it.roundToInt().toString() else chartNumber.format(it) }
                }
                SwitchRow(stringResource(R.string.chart_full_band),fullBand){fullBand=it}
                if(!fullBand)Text(stringResource(R.string.chart_scale_note),style=MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Legend(stringResource(R.string.legend_recorded), MaterialTheme.colorScheme.primary, dashed = false)
                    Legend(stringResource(R.string.legend_forecast), MaterialTheme.colorScheme.primary, dashed = true)
                    if (result.labs.isNotEmpty()) Legend(stringResource(R.string.legend_labs), MaterialTheme.colorScheme.tertiary, dot = true)
                    if (result.bandInner != null) Legend(stringResource(R.string.legend_band_inner), MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), block = true)
                    if (result.bandOuter != null) Legend(stringResource(R.string.legend_band_outer), MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), block = true)
                }
                FlagNotes(result.flags[Curve.E2].orEmpty())
                Text(stringResource(R.string.pk_chart_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.pk_unit),style=MaterialTheme.typography.bodyMedium)
                AdaptiveChoice(listOf("pg/mL","pmol/L"),if(settings.pmol)1 else 0,{onSettings(ConcSettings(it==1,settings.calibrate,settings.mode))})
            }
        } else if (loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (result != null && result.simulatedMedications.isNotEmpty() && result.others.isEmpty())
            EmptyState(Icons.AutoMirrored.Outlined.ShowChart, stringResource(R.string.pk_no_doses_title), stringResource(R.string.pk_no_doses_body))

        if (result != null && result.others.isNotEmpty()) OtherCurvesCard(result)

        CalibrationCard(result, settings, onSettings, onOpenLabs, ::fmt, unit)

        SectionCard(stringResource(R.string.pk_model_title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pk_weight), style = MaterialTheme.typography.bodyLarge)
                    Text(weight?.let { stringResource(R.string.kg_value, displayNumber(it)) } ?: stringResource(R.string.not_set), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { editWeight = true }) { Text(stringResource(R.string.edit)) }
            }
            Text(stringResource(R.string.pk_weight_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(stringResource(R.string.pk_assumptions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.pk_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (result != null && result.models.isNotEmpty()) ModelsCard(result.models)
    }
    repair?.let{med->HistoricalContextDialog(med,profiles[med.id],records.map{r->r.copy(config_snapshot=net.plainnotes.app.data.HistoricalContext.resolved(r,state.ruleSnapshots))},{repair=null}){m,p,from,to->onConfirmHistory(m,p,from,to);repair=null}}
    if (editWeight) WeightDialog(weight, { editWeight = false }) { onWeight(it); editWeight = false }
}

@Composable private fun CalibrationCard(result: ConcentrationResult?, settings: ConcSettings, onSettings: (ConcSettings) -> Unit, onOpenLabs: () -> Unit, fmt: (Double) -> String, unit: String) {
    SectionCard(stringResource(R.string.calib_title)) {
        Text(stringResource(R.string.calib_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRow(stringResource(R.string.calib_enable), settings.calibrate) { onSettings(ConcSettings(settings.pmol, it, settings.mode)) }
        val s = result?.calibration
        if (s == null) Text(stringResource(R.string.calib_no_labs), style = MaterialTheme.typography.bodyMedium)
        else {
            val m = s.model
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(stringResource(R.string.calib_labs), s.labCount.toString(), Modifier.weight(1f))
                Metric(stringResource(R.string.calib_convergence), "${((s.diagnostics?.convergenceScore ?: 0.0) * 100).roundToInt()}%", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(stringResource(R.string.calib_amplitude), "×" + displayNumber(kotlin.math.exp(m.logAmplitude), 2), Modifier.weight(1f))
                Metric(stringResource(R.string.calib_clearance), "×" + displayNumber(kotlin.math.exp(m.logRate), 2), Modifier.weight(1f))
            }
            m.baselinePGmL?.let { Text(stringResource(R.string.calib_baseline, fmt(it), unit), style = MaterialTheme.typography.bodySmall) }
            s.diagnostics?.takeIf { m.postDoseObservationCount > 0 }?.let { d ->
                Text(stringResource(R.string.calib_last, fmt(d.observedPGmL), fmt(d.predictedPGmL), unit), style = MaterialTheme.typography.bodySmall)
                if (d.isOutlier) Text(stringResource(R.string.calib_outlier), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (m.postDoseObservationCount < 3) Text(stringResource(R.string.calib_few), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.calib_mode), style = MaterialTheme.typography.labelLarge)
            AdaptiveChoice(listOf(stringResource(R.string.calib_retro),stringResource(R.string.calib_causal)),if(settings.mode==CalibrationMode.RETROSPECTIVE)0 else 1,
                {onSettings(ConcSettings(settings.pmol,settings.calibrate,if(it==0)CalibrationMode.RETROSPECTIVE else CalibrationMode.CAUSAL))})
            Text(stringResource(if (settings.mode == CalibrationMode.RETROSPECTIVE) R.string.calib_retro_desc else R.string.calib_causal_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onOpenLabs) { Icon(Icons.Outlined.Science, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.calib_manage_labs)) }
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

@Composable private fun Legend(label: String, color: androidx.compose.ui.graphics.Color, dashed: Boolean = false, dot: Boolean = false, block: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 22.dp, height = 10.dp)) {
            val y = size.height / 2
            when {
                dot -> drawCircle(color, size.height / 2, center)
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
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** CPA, spironolactone, canrenone and progesterone: one chart at a time, each with its own unit and axis. */
@Composable private fun OtherCurvesCard(result: ConcentrationResult) {
    val curves = result.others.keys.sortedBy { it.ordinal }
    var selected by remember(curves) { mutableStateOf(curves.first()) }
    var rangeDays by remember { mutableIntStateOf(14) }
    var fullBand by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.pk_other_title)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            curves.forEach { c -> FilterChip(selected == c, { selected = c }, label = { Text(curveLabel(c)) }) }
        }
        val b: BandedCurve = result.others.getValue(selected)
        val data = ChartData(b.timeH, b.center, b.p25 to b.p75, b.p5 to b.p95, result.nowH, unit = selected.unit,includeBandsInScale=fullBand)
        val ranges=listOf(7,14,60)
        AdaptiveChoice(ranges.map{stringResource(R.string.days_short,it)},ranges.indexOf(rangeDays),{rangeDays=ranges[it]})
        val loc = currentLocale()
        val nf = remember(loc) { java.text.NumberFormat.getNumberInstance(loc).apply { maximumFractionDigits = 1; isGroupingUsed = false } }
        key(selected, rangeDays) {
            ConcChart(data, result.nowH - rangeDays * 24 * 0.75, result.nowH + rangeDays * 24 * 0.25, Modifier.fillMaxWidth().height(220.dp)) { v ->
                if (v >= 10) v.roundToInt().toString() else nf.format(v) }
        }
        SwitchRow(stringResource(R.string.chart_full_band),fullBand){fullBand=it}
        if(!fullBand)Text(stringResource(R.string.chart_scale_note),style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend(stringResource(R.string.legend_band_inner), MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), block = true)
            Legend(stringResource(R.string.legend_band_outer), MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), block = true)
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
