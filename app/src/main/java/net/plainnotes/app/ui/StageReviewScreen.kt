package net.plainnotes.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.symptoms.SymptomCatalog
import org.json.JSONObject
import java.time.LocalDate

@Composable fun ReviewList(reviews:List<StageReviewEntity>,onEdit:(StageReviewEntity?)->Unit,onDelete:(Long)->Unit) {
    Text(stringResource(R.string.wb_review_intro),style=MaterialTheme.typography.bodySmall)
    SourceLink("https://www.has-sante.fr/upload/docs/application/pdf/2025-07/transidentite_prise_en_charge_de_ladulte_-_recommandations.pdf")
    Button(onClick={onEdit(null)}){Text(stringResource(R.string.wb_review_new))}
    if(reviews.isEmpty())Text(stringResource(R.string.wb_review_empty))
    var deleting by remember{mutableStateOf<StageReviewEntity?>(null)}
    reviews.sortedByDescending{it.date}.forEach{review->SectionCard(review.date){
        listOfNotNull(review.tolerance_note,review.risk_note,review.satisfaction_note).filter{it.isNotBlank()}.forEach{Text(it)}
        Row{TextButton(onClick={onEdit(review)}){Text(stringResource(R.string.edit))};TextButton(onClick={deleting=review}){Text(stringResource(R.string.remove))}}
    }}
    deleting?.let{review->AlertDialog(onDismissRequest={deleting=null},text={Text(stringResource(R.string.history_delete_confirm))},confirmButton={TextButton(onClick={onDelete(review.id);deleting=null}){Text(stringResource(R.string.remove))}},dismissButton={TextButton(onClick={deleting=null}){Text(stringResource(R.string.cancel))}})}
}

@Composable fun StageReviewDialog(review:StageReviewEntity?,reviews:List<StageReviewEntity>,checks:List<SymptomCheckEntity>,visibility:List<ReviewEffectEntity>,onDismiss:()->Unit,onSave:(StageReviewEntity)->Unit) {
    var date by rememberSaveable{mutableStateOf(review?.date?:LocalDate.now().toString())};var picker by remember{mutableStateOf(false)}
    val effectValues=rememberSaveable(saver=Saver<SnapshotStateMap<String,String>,HashMap<String,String>>(save={HashMap(it)},restore={mutableStateMapOf<String,String>().apply{putAll(it)}})){mutableStateMapOf<String,String>().apply{val o=JSONObject(review?.effects_json?:"{}");o.keys().forEach{put(it,o.getString(it))}}}
    var tolerance by rememberSaveable{mutableStateOf(review?.tolerance_note.orEmpty())};var risks by rememberSaveable{mutableStateOf(review?.risk_note.orEmpty())}
    var smoking by rememberSaveable{mutableStateOf(review?.smoking)};var satisfaction by rememberSaveable{mutableStateOf(review?.satisfaction)};var satisfactionNote by rememberSaveable{mutableStateOf(review?.satisfaction_note.orEmpty())}
    var systolic by rememberSaveable{mutableStateOf(review?.systolic?.toString().orEmpty())};var diastolic by rememberSaveable{mutableStateOf(review?.diastolic?.toString().orEmpty())};var weight by rememberSaveable{mutableStateOf(review?.weight_kg?.let(::inputNumber).orEmpty())}
    val sys=systolic.toIntOrNull();val dia=diastolic.toIntOrNull();val kg=weight.replace(',','.').toDoubleOrNull()
    val valid=(systolic.isBlank()||sys!=null&&sys in 1..999)&&(diastolic.isBlank()||dia!=null&&dia in 1..999)&&(weight.isBlank()||kg!=null&&kg.isFinite()&&kg>0)
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.wb_reviews))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TextButton(onClick={picker=true}){Text(date)}
        Text(stringResource(R.string.wb_review_intro),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.wb_effects),style=MaterialTheme.typography.titleMedium)
        REVIEW_EFFECTS.filter{visibility.firstOrNull{v->v.effect_id==it.id}?.enabled!=false}.forEach { effect ->
            Text(stringResource(effect.label));var source by remember{mutableStateOf(false)}
            listOf("NOT_YET" to R.string.wb_not_yet,"NOTICED" to R.string.wb_noticed,"UNSURE" to R.string.wb_unsure).forEach{(value,label)->Row {RadioButton(effectValues[effect.id]==value,{if(effectValues[effect.id]==value)effectValues.remove(effect.id)else effectValues[effect.id]=value});Text(stringResource(label),Modifier.padding(top=12.dp))}}
            OutlinedTextField(effectValues[effect.id+":note"].orEmpty(),{if(it.isBlank())effectValues.remove(effect.id+":note")else effectValues[effect.id+":note"]=it},label={Text(stringResource(R.string.wb_optional_note))})
            TextButton(onClick={source=!source}){Text(stringResource(R.string.wb_source))}
            if(source){
                Text(stringResource(R.string.wb_effect_estimates),style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)){Text("Endocrine Society 2017 · Table 13");Text(effect.esName);Text("${stringResource(R.string.wb_effect_onset)}: ${effect.esOnset}\n${stringResource(R.string.wb_effect_maximum)}: ${effect.esMax}");SourceLink("https://doi.org/10.1210/jc.2017-01658")}
                    Column(Modifier.weight(1f)){Text("SOC8 · Appendix C · Table 1");Text(effect.socName);Text("${stringResource(R.string.wb_effect_onset)}: ${effect.socOnset}\n${stringResource(R.string.wb_effect_maximum)}: ${effect.socMax}");SourceLink("https://doi.org/10.1080/26895269.2022.2100644")}
                }
                Text("Estimates represent clinical observations.",style=MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider();Text(stringResource(R.string.wb_tolerance),style=MaterialTheme.typography.titleMedium)
        val previous=reviews.filter{it.id!=review?.id&&it.date<date}.maxOfOrNull{it.date}?:"0001-01-01"
        val groups=SymptomCatalog.load().groups;val locale=androidx.compose.ui.platform.LocalContext.current.resources.configuration.locales[0]
        Text(stringResource(R.string.wb_period_symptoms))
        checks.filter{it.date>previous&&it.date<=date}.forEach{Text("${it.date} · ${groups[it.group_id]?.localized(locale)?:it.group_id}${it.note?.let{n->" · $n"}.orEmpty()}")}
        OriginalQuotation("L'évaluation de la tolérance clinique est à considérer au même titre que les dosages sanguins.");Text("HAS 2025 · R45 · p. 21",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(tolerance,{tolerance=it},label={Text(stringResource(R.string.wb_other_tolerance))})
        HorizontalDivider();Text(stringResource(R.string.wb_risks),style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(risks,{risks=it},label={Text(stringResource(R.string.wb_risk_note))})
        Text(stringResource(R.string.wb_smoking));listOf(null to R.string.wb_unrecorded,"YES" to R.string.yes,"NO" to R.string.wb_no).forEach{(value,label)->Row{RadioButton(smoking==value,{smoking=value});Text(stringResource(label),Modifier.padding(top=12.dp))}}
        NumberField(systolic,{systolic=it},stringResource(R.string.wb_systolic),decimal=false,isError=systolic.isNotBlank()&&(sys==null||sys !in 1..999))
        NumberField(diastolic,{diastolic=it},stringResource(R.string.wb_diastolic),decimal=false,isError=diastolic.isNotBlank()&&(dia==null||dia !in 1..999))
        NumberField(weight,{weight=it},stringResource(R.string.wb_review_weight),isError=weight.isNotBlank()&&(kg==null||!kg.isFinite()||kg<=0))
        HorizontalDivider();Text(stringResource(R.string.wb_satisfaction),style=MaterialTheme.typography.titleMedium)
        FiveLevelInput(satisfaction,stringResource(R.string.wb_satisfaction_low),stringResource(R.string.wb_satisfaction_high),stringResource(R.string.wb_satisfaction)){satisfaction=it}
        OutlinedTextField(satisfactionNote,{satisfactionNote=it},label={Text(stringResource(R.string.wb_optional_note))})
    }},confirmButton={Button(enabled=valid,onClick={onSave(StageReviewEntity(review?.id?:0,date,JSONObject(effectValues.toMap()).toString(),tolerance.takeIf{it.isNotBlank()},risks.takeIf{it.isNotBlank()},smoking,sys,dia,kg,satisfaction,satisfactionNote.takeIf{it.isNotBlank()}))}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
    if(picker)DatePickerModal(LocalDate.parse(date),{picker=false}){if(it<=LocalDate.now())date=it.toString();picker=false}
}
