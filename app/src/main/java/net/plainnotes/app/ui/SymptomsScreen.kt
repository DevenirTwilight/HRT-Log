package net.plainnotes.app.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.symptoms.*
import java.time.LocalDate
import java.util.Locale

fun GroupNames.localized(locale:Locale):String=when(locale.language){"fr"->fr;"zh"->if(locale.script=="Hant"||locale.country in listOf("TW","HK"))zhHant else zh;else->en}
@Composable fun SourceLink(url:String) {
    val context=LocalContext.current
    TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri()))}}){Text(stringResource(R.string.wb_source))}
}

@Composable fun OriginalQuotation(text:String,urgent:Boolean=false) {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Text(text,style=MaterialTheme.typography.bodySmall,color=if(urgent)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    SourceTranslations.translation(text,locale)?.let{translated->Text(stringResource(R.string.wb_unofficial)+": "+translated,style=MaterialTheme.typography.bodySmall)}
}

@Composable fun SymptomsScreen(date:LocalDate,medications:List<MedicationEntity>,profiles:Map<Long,ProfileEntity>,checks:List<SymptomCheckEntity>,region:String?,
    onCheck:(LocalDate,String,Boolean,String?)->Unit) {
    val catalog=remember{SymptomCatalog.load()};val context=LocalContext.current;val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val active=medications.filter{it.active};val keys=active.map{MedKey(it.id,it.molecule,it.route,profiles[it.id]?.ester)}
    val groups=remember(keys){catalog.groupsFor(keys)}
    var expanded by remember {mutableStateOf<String?>(null)}
    Text(stringResource(R.string.wb_symptoms_help),style=MaterialTheme.typography.bodySmall)
    active.forEach{m->when(val result=catalog.forMedication(keys.first{it.id==m.id})){
        MedSymptoms.NoneListedByOfficialSources->Text("${m.name}: ${stringResource(R.string.wb_symptom_none)}")
        MedSymptoms.NoOfficialSource->Text("${m.name}: ${stringResource(R.string.wb_symptom_unknown)}")
        is MedSymptoms.Listed->if(result.sublingualNotCovered)Text("${m.name}: ${stringResource(R.string.wb_sublingual)}")
    }}
    if(active.isEmpty())Text(stringResource(R.string.no_medications))
    listOf(true,false).forEach { urgent ->
        val section=groups.filter{it.urgent==urgent}
        if(section.isNotEmpty()) SectionCard(null) {
            if(urgent) Text(section.flatMap{it.entries}.filter{it.action.urgent}.map{if(it.action.text.contains("立即"))"立即" else "immédiatement"}.distinct().joinToString(" / "),
                color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.titleLarge)
            // Each source action appears once in this section, instead of repeating a warning for every symptom.
            section.flatMap{it.entries}.map{it.source to it.action}.distinctBy{it.first.id to it.second.id}.forEach{(source,action)->
                Text(source.title,style=MaterialTheme.typography.labelSmall);OriginalQuotation(action.text,action.urgent)
                Text(source.section,style=MaterialTheme.typography.bodySmall)
            }
            section.forEach { g ->
                val saved=checks.firstOrNull{it.date==date.toString()&&it.group_id==g.id}
                Row {
                    Checkbox(saved!=null,{onCheck(date,g.id,it,saved?.note)},Modifier.semantics{contentDescription=context.getString(R.string.wb_symptom_checked)+": "+g.names.localized(locale)})
                    Column(Modifier.weight(1f)){Text(g.names.localized(locale));Text(g.medicationIds.mapNotNull{id->active.firstOrNull{it.id==id}?.name}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)}
                    TextButton(onClick={expanded=if(expanded==g.id)null else g.id}){Text(stringResource(R.string.wb_source))}
                }
                if(saved!=null) key(date,g.id) {
                    var note by remember(saved.note){mutableStateOf(saved.note.orEmpty())}
                    OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.wb_optional_note))})
                    TextButton(onClick={onCheck(date,g.id,true,note)}){Text(stringResource(R.string.save))}
                }
                if(expanded==g.id) {Text(stringResource(R.string.wb_sources));g.entries.forEach{e->
                    Text(e.source.title,style=MaterialTheme.typography.titleSmall)
                    OriginalQuotation(e.quote);OriginalQuotation(e.action.text,e.action.urgent)
                    Text("${e.source.publisher} · ${e.source.region} · ${e.source.documentDate}\n${e.source.section}",style=MaterialTheme.typography.bodySmall)
                    if(e.source.thirdParty){Text(stringResource(R.string.wb_third_party));e.source.sites.forEach{site->Text("${site.site} · ${site.revised.orEmpty()}",style=MaterialTheme.typography.bodySmall);SourceLink(site.url)}}
                    if(e.source.note=="ARCHIVED_FR")Text(stringResource(R.string.wb_archived_fr))
                    Text(stringResource(R.string.wb_viewed,e.source.viewed),style=MaterialTheme.typography.bodySmall);SourceLink(e.source.url)
                }
                }
                HorizontalDivider()
            }
        }
    }
    val monitoring=catalog.monitoringFor(region,active.map{it.molecule}.toSet())
    if(monitoring.isNotEmpty())SectionCard(stringResource(R.string.wb_monitoring)){monitoring.forEach{m->OriginalQuotation(m.quote);Text("${m.publisher} · ${m.title} · ${m.documentDate} · ${m.section}",style=MaterialTheme.typography.bodySmall);SourceLink(m.url)}}
    catalog.reportingFor(region)?.let{report->SectionCard(stringResource(R.string.wb_about_symptoms)){Text(report.publisher);report.quotes.forEach{OriginalQuotation(it)};SourceLink(report.url)}}
}

@Composable fun RegionSection(value:String?,onValue:(String?)->Unit) {
    SectionCard(stringResource(R.string.wb_region)) {
        Text(stringResource(R.string.wb_region_help),style=MaterialTheme.typography.bodySmall)
        listOf(null to R.string.wb_region_unset,"CN" to R.string.wb_region_cn,"HK" to R.string.wb_region_hk,"TW" to R.string.wb_region_tw,"FR" to R.string.wb_region_fr,"OTHER" to R.string.wb_region_other).forEach{(key,label)->
            Row {RadioButton(value==key,{onValue(key)});Text(stringResource(label),Modifier.padding(top=12.dp))}
        }
    }
}
