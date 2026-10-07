package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.*
import net.plainnotes.app.pk.GEL_PRODUCT_IDS
import java.time.*

/** User attests to a historical formulation; current values are only editable prefills. */
@Composable fun HistoricalContextDialog(m:MedicationEntity,p:ProfileEntity?,records:List<RecordEntity>,onDismiss:()->Unit,
    onConfirm:(MedicationEntity,ProfileEntity?,LocalDate,LocalDate)->Unit) {
    val missing=records.filter{it.medication_id==m.id && it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc!=null && HistoricalContext.incomplete(it)}
    val dates=missing.map{Instant.ofEpochMilli(it.taken_utc!!).atZone(ZoneId.systemDefault()).toLocalDate()}
    var from by remember{mutableStateOf(dates.minOrNull() ?: LocalDate.now())};var to by remember{mutableStateOf(dates.maxOrNull() ?: LocalDate.now())}
    var pick by remember{mutableIntStateOf(0)}
    var molecule by remember{mutableStateOf(m.molecule)};var route by remember{mutableStateOf(m.route ?: "ORAL")}
    var unit by remember{mutableStateOf(m.unit)};var ester by remember{mutableStateOf(p?.ester ?: "E2")}
    var gel by remember{mutableStateOf(p?.gel_product_id)};var sl by remember{mutableStateOf(p?.sl_tier)}
    var rate by remember{mutableStateOf(p?.patch_release_ug_day?.let(::inputNumber).orEmpty())}
    val candidate=m.copy(molecule=molecule,route=route,unit=unit)
    val profile=if(molecule=="E2")ProfileEntity(m.id,ester,when(route){"ORAL"->"oral";"SUBLINGUAL"->"sublingual";"GEL"->"gel";"PATCH"->"patchApply";else->"injection"},sl_tier=sl.takeIf{route=="SUBLINGUAL"},gel_product_id=gel.takeIf{route=="GEL"},patch_release_ug_day=rate.toDoubleOrNull()?.takeIf{it.isFinite() && it>0 && route=="PATCH"}) else null
    val snapshot=MedicationSnapshot.encode(candidate,profile)
    val selected=missing.filter{Instant.ofEpochMilli(it.taken_utc!!).atZone(ZoneId.systemDefault()).toLocalDate() in from..to}
    val compatible=selected.all{HistoricalContext.fill(it.config_snapshot,snapshot)!=null}
    val valid=!to.isBefore(from) && selected.isNotEmpty() && compatible && ConcentrationCalculator.missingFor(candidate,profile).isEmpty()
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.history_context_repair))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(m.name,style=MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.history_context_repair_note),style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick={pick=1},modifier=Modifier.weight(1f)){Text(formatDate(from))}
                OutlinedButton(onClick={pick=2},modifier=Modifier.weight(1f)){Text(formatDate(to))}
            }
            DropdownField(stringResource(R.string.molecule),listOf("E2","CPA","SPI","P4"),molecule,{choiceLabel(it)},{molecule=it;if(it!="E2"){route="ORAL";unit="MG"}})
            DropdownField(stringResource(R.string.route),if(molecule=="E2")E2_ROUTES else listOf("ORAL"),route,{choiceLabel(it)},{route=it;ester=estersFor(it).first();if(it=="PATCH")unit="PATCH"})
            DropdownField(stringResource(R.string.unit),listOf("MG","PATCH"),unit,{unitLabel(it)},{unit=it})
            if(molecule=="E2") {
                DropdownField(stringResource(R.string.ester),estersFor(route),ester,{choiceLabel(it)},{ester=it})
                if(route=="GEL")DropdownField(stringResource(R.string.gel_product),GEL_PRODUCT_IDS,gel,{gelProductLabel(it)},{gel=it})
                if(route=="SUBLINGUAL")DropdownField(stringResource(R.string.sl_tier),listOf(0,1,2,3),sl,{slTierLabel(it)},{sl=it})
                if(route=="PATCH")NumberField(rate,{rate=it},stringResource(R.string.patch_release),suffix="µg/day")
            }
            Text(stringResource(R.string.history_context_repair_count,selected.size),style=MaterialTheme.typography.bodySmall)
            if(!compatible)Text(stringResource(R.string.history_context_conflict),color=MaterialTheme.colorScheme.error)
        }},confirmButton={Button(enabled=valid,onClick={onConfirm(candidate,profile,from,to)}){Text(stringResource(R.string.history_context_confirm))}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
    if(pick>0)DatePickerModal(if(pick==1)from else to,{pick=0}){if(pick==1)from=it else to=it;pick=0}
}
