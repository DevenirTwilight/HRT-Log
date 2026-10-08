package net.plainnotes.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shared facts formatter: exports and the screen read the same frozen revision. */
fun labContextLines(context:Context,row:LabContextEntity):List<String> {
    val o=LabContext.validate(row.context_json)
    val locale=context.resources.configuration.locales[0]
    val numbers=NumberFormat.getNumberInstance(locale).apply{maximumFractionDigits=3}
    fun number(n:Double)=numbers.format(n)
    val unknown=context.getString(R.string.lab_context_unknown)
    fun text(o:JSONObject,k:String)=if(o.isNull(k))unknown else o.getString(k)
    fun choice(o:JSONObject,k:String)=if(o.isNull(k))unknown else context.getString(choiceRes(o.getString(k)))
    val fmt=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT).withLocale(locale)
    fun date(t:Long,zone:String=o.getString("sampled_zone"))=Instant.ofEpochMilli(t).atZone(ZoneId.of(zone)).format(fmt)+" · "+zone
    return buildList {
        val origin=context.getString(when(row.origin){"AT_ENTRY"->R.string.lab_context_at_entry;"SAMPLE_CHANGED"->R.string.lab_context_sample_changed;else->R.string.lab_context_reconstructed})
        add(context.getString(R.string.lab_context_captured,row.revision,date(row.captured_utc),origin))
        add(context.getString(R.string.lab_context_sample,date(o.getLong("sampled_utc")),o.getString("analyte_code")))
        val epoch=o.getJSONObject("epoch")
        if(epoch.getBoolean("unknown"))add(context.getString(R.string.lab_context_epoch_unknown)) else {
            add(context.getString(R.string.lab_context_epoch,date(epoch.getLong("from")),if(epoch.isNull("until"))context.getString(R.string.lab_context_open_end) else date(epoch.getLong("until"))))
            if(epoch.getBoolean("reconstructed"))add(context.getString(R.string.lab_context_legacy_regimen))
        }
        o.optJSONArray("confirmed_periods")?.let{periods->for(i in 0 until periods.length()) {
            val p=periods.getJSONObject(i);val s=p.getJSONObject("standard");val day=DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            val last=if(p.isNull("until_date"))context.getString(R.string.lab_context_open_end) else java.time.LocalDate.parse(p.getString("until_date")).minusDays(1).format(day)
            add(context.getString(R.string.lab_context_confirmed_period,java.time.LocalDate.parse(p.getString("from_date")).format(day),last))
            val doses=s.getJSONArray("doses").let{a->(0 until a.length()).joinToString(" + "){number(a.getDouble(it))}}
            add("${choice(s,"route")} · $doses ${text(s,"unit")} · ${context.getString(choiceRes(s.getString("kind")))}: ${s.getInt("interval")}")
        }}
        val regimens=o.getJSONArray("regimens")
        if(regimens.length()==0 && !epoch.getBoolean("unknown"))add(context.getString(R.string.lab_context_no_regimen))
        for(i in 0 until regimens.length()) {
            val d=RegimenDefinition.read(regimens.getJSONObject(i).getJSONObject("definition").toString())
            val m=d.snapshot(regimens.getJSONObject(i).getLong("medication_id"))
            val doses=if(d.times.isEmpty())number(d.dose) else d.times.joinToString(" · "){(time,dose)->"$time: ${number(dose ?: d.dose)}"}
            add("${m?.name ?: unknown} · ${m?.route?.let{context.getString(choiceRes(it))} ?: unknown} · $doses ${m?.unit ?: unknown} · ${context.getString(choiceRes(d.kind))}: ${d.interval}")
            if(d.kind=="WEEKLY")add(java.time.DayOfWeek.entries.filter{d.weekdays and (1 shl (it.value-1))!=0}.joinToString(" · "){it.getDisplayName(java.time.format.TextStyle.SHORT,locale)})
            val anchor=d.anchorUtc?.let{date(it,d.zone)} ?: "${d.anchorLocal} · ${d.zone}"
            add(context.getString(R.string.lab_context_schedule_anchor,anchor))
        }
        add(context.getString(R.string.lab_context_actual_note))
        val actual=o.getJSONArray("actual")
        if(actual.length()==0)add(context.getString(R.string.lab_context_no_actual))
        for(i in 0 until actual.length()) {
            val r=actual.getJSONObject(i);val mins=r.getLong("elapsed_ms")/60_000
            add("${choice(r,"ingredient")} · ${text(r,"name")} · ${date(r.getLong("taken_utc"),r.getString("taken_zone"))}")
            val amount=if(r.isNull("amount"))unknown else number(r.getDouble("amount"))
            add(context.getString(R.string.lab_context_actual_line,amount,text(r,"unit"),choice(r,"route"),mins/60,mins%60))
        }
        val counts=o.getJSONObject("counts")
        add(context.getString(R.string.lab_context_counts,o.getInt("window_hours"),counts.getInt("late"),counts.getInt("missed"),counts.getInt("unconfirmed")))
        o.optJSONObject("estimate")?.let{e->
            add(context.getString(R.string.lab_context_estimate_note));add(context.getString(R.string.pk_disclaimer_short))
            add(context.getString(R.string.lab_context_estimate_coverage,e.getInt("used_doses"),e.getInt("skipped_doses")))
            if(e.getJSONArray("missing").length()>0 || e.getJSONArray("unsupported").length()>0)add(context.getString(R.string.lab_context_estimate_partial))
            val values=e.getJSONArray("values")
            if(values.length()==0)add(context.getString(R.string.lab_context_no_estimate))
            for(i in 0 until values.length()) {
                val v=values.getJSONObject(i)
                val label=context.getString(when(v.getString("curve")){"E2"->R.string.choice_e2;"CPA"->R.string.choice_cpa;"SPIRONOLACTONE"->R.string.choice_spi;"CANRENONE"->R.string.curve_canrenone;else->R.string.choice_p4})
                add("$label: ${number(v.getDouble("value"))} ${v.getString("unit")}")
                if(!v.isNull("p5") && !v.isNull("p95"))add(context.getString(R.string.lab_context_estimate_band,number(v.getDouble("p5")),number(v.getDouble("p95")),v.getString("unit")))
                val flags=v.getJSONArray("flags");for(j in 0 until flags.length())add(context.getString(when(flags.getString(j)){"EXTRAPOLATED_TIER"->R.string.flag_extrapolated_tier;"EXTRAPOLATED_AFTER_CALIBRATED_HOURS"->R.string.flag_after_8h;"NO_PRODUCT_DATA"->R.string.flag_no_product_data;else->R.string.flag_illustrative}))
            }
        }
        add(context.getString(R.string.lab_context_frozen_note))
    }
}

@Composable fun LabContextSection(lab:LabValueEntity,rows:List<LabContextEntity>,onRebuild:((LabValueEntity,Boolean)->Unit)?) {
    var expanded by remember(lab.id){mutableStateOf(false)}
    var selected by remember(lab.id,rows.maxOfOrNull{it.revision}){mutableStateOf(rows.maxByOrNull{it.revision})}
    var confirm by remember{mutableStateOf(false)};var includeEstimate by remember{mutableStateOf(false)}
    val context=LocalContext.current;val configuration=LocalConfiguration.current
    Column(Modifier.padding(start=16.dp,end=16.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(rows.isEmpty())Text(stringResource(R.string.lab_context_not_saved),style=MaterialTheme.typography.bodySmall)
        else {
            TextButton(onClick={expanded=!expanded}){Text(stringResource(R.string.lab_context_title))}
            if(expanded) {
                DropdownField(stringResource(R.string.lab_context_version),rows.sortedByDescending{it.revision},selected,{stringResource(R.string.lab_context_version_number,it.revision)},{selected=it})
                selected?.let{row->remember(row,configuration){labContextLines(context,row)}.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}}
            }
        }
        if(onRebuild!=null && (expanded || rows.isEmpty()))TextButton(onClick={includeEstimate=false;confirm=true}){Text(stringResource(R.string.lab_context_rebuild))}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.lab_context_rebuild))},text={Column {
        Text(stringResource(R.string.lab_context_rebuild_note))
        Row{Checkbox(includeEstimate,{includeEstimate=it});Text(stringResource(R.string.lab_context_include_estimate))}
    }},confirmButton={Button(onClick={confirm=false;onRebuild?.invoke(lab,includeEstimate)}){Text(stringResource(R.string.lab_context_rebuild))}},dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.cancel))}})
}
