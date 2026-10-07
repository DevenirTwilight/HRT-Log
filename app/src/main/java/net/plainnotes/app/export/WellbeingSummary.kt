package net.plainnotes.app.export

import android.content.Context
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.symptoms.SymptomCatalog
import net.plainnotes.app.ui.REVIEW_EFFECTS
import net.plainnotes.app.ui.localized
import org.json.JSONObject
import java.time.LocalDate

/** Pure selection shared by PDF and regression tests. Both boundaries are inclusive. */
class WellbeingSummary(d:ExportData,val from:LocalDate,val to:LocalDate) {
    init{require(from<=to)}
    val symptoms=d.symptoms.filter{LocalDate.parse(it.date) in from..to}.sortedWith(compareBy({it.date},{it.group_id}))
    val reviews=d.reviews.filter{LocalDate.parse(it.date) in from..to}.sortedBy{it.date}
    val scores=d.scores.filter{LocalDate.parse(it.date) in from..to}
    val notes=d.notes.filter{LocalDate.parse(it.date) in from..to}
}
fun reviewLines(context:Context,r:StageReviewEntity):List<String> {
    val values=JSONObject(r.effects_json)
    return buildList {
        add(r.date)
        REVIEW_EFFECTS.forEach{effect->
            val value=values.optString(effect.id)
            if(value.isNotEmpty())add(context.getString(effect.label)+": "+when(value){"NOT_YET"->context.getString(R.string.wb_not_yet);"NOTICED"->context.getString(R.string.wb_noticed);"UNSURE"->context.getString(R.string.wb_unsure);else->value})
            values.optString(effect.id+":note").takeIf{it.isNotEmpty()}?.let{add(context.getString(effect.label)+": "+it)}
        }
        // Unknown future/custom effect IDs remain visible in exports instead of being dropped.
        values.keys().asSequence().filter{k->REVIEW_EFFECTS.none{k==it.id||k==it.id+":note"}}.forEach{add(it+": "+values.getString(it))}
        r.tolerance_note?.let{add(context.getString(R.string.wb_tolerance)+": "+it)}
        r.risk_note?.let{add(context.getString(R.string.wb_risks)+": "+it)}
        r.smoking?.let{add(context.getString(R.string.wb_smoking)+": "+context.getString(if(it=="YES")R.string.yes else R.string.wb_no))}
        r.systolic?.let{add(context.getString(R.string.wb_systolic)+": "+it)}
        r.diastolic?.let{add(context.getString(R.string.wb_diastolic)+": "+it)}
        r.weight_kg?.let{add(context.getString(R.string.wb_review_weight)+": "+it)}
        r.satisfaction?.let{add(context.getString(R.string.wb_satisfaction)+": "+it)}
        r.satisfaction_note?.let{add(context.getString(R.string.wb_satisfaction)+": "+it)}
    }
}
fun symptomLines(context:Context,checks:List<SymptomCheckEntity>):List<String> {
    val catalog=SymptomCatalog.load();val locale=context.resources.configuration.locales[0]
    return checks.flatMap{check->buildList{
        add(check.date+" · "+(catalog.groups[check.group_id]?.localized(locale)?:check.group_id))
        check.note?.let{add(it)}
        catalog.entries.filter{it.group==check.group_id}.map{it.source}.distinctBy{it.id}.forEach{source->add(source.title+" · "+source.documentDate+" · "+source.section+" · "+source.url)}
    }}
}
