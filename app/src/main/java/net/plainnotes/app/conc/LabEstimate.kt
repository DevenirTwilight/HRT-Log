package net.plainnotes.app.conc

import net.plainnotes.app.data.*
import net.plainnotes.app.pk.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A frozen, uncalibrated value at sampling, never fits the result it accompanies. */
object LabEstimate {
    suspend fun capture(dao:NotesDao,lab:LabValueEntity):String {
        val at=Instant.ofEpochMilli(lab.sampled_utc)
        val records=dao.records().filter{it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc?.let{t->t<=lab.sampled_utc && t>=at.minusSeconds(ConcentrationCalculator.HISTORY_DAYS*86400L).toEpochMilli()}==true}
        val meds=dao.medications();val profiles=meds.mapNotNull{m->dao.profile(m.id)?.let{m.id to it}}.toMap()
        val rules=dao.rules().associate{it.id to it.config_snapshot};val weight=dao.pkSettings()?.current_weight_kg
        val r=ConcentrationCalculator.compute(meds,profiles,records,emptyList(),emptyList(),weight,at,calibrate=false,plannedSnapshots=rules)
        val values=JSONArray()
        fun add(curve:Curve,value:Double?,low:Double?,high:Double?) {if(value!=null && value.isFinite())values.put(JSONObject().put("curve",curve.name).put("value",value).put("unit",curve.unit).put("p5",low ?: JSONObject.NULL).put("p95",high ?: JSONObject.NULL).put("flags",JSONArray(r.flags[curve].orEmpty().map{it.name})))}
        add(Curve.E2,r.currentPgMl,r.bandOuter?.first?.let{Pk.interpolate(r.timeH,it,r.nowH)},r.bandOuter?.second?.let{Pk.interpolate(r.timeH,it,r.nowH)})
        r.others.forEach{(curve,b)->add(curve,Pk.interpolate(b.timeH,b.center,r.nowH),Pk.interpolate(b.timeH,b.p5,r.nowH),Pk.interpolate(b.timeH,b.p95,r.nowH))}
        val parameters=PkParams::class.java.getResourceAsStream("/pk-params.json")!!.bufferedReader().use{JSONObject(it.readText())}
        return JSONObject().put("version",1).put("sampled_utc",lab.sampled_utc).put("calibrated",false).put("calculator_version",ConcentrationCalculator.VERSION)
            .put("used_doses",r.usedDoses).put("skipped_doses",r.skippedDoses).put("weight_kg_at_capture",weight ?: JSONObject.NULL)
            .put("values",values).put("parameter_document",parameters).put("missing",JSONArray(r.missing.map{it.input.name}.distinct()))
            .put("unsupported",JSONArray(r.unsupported.values.map{it.name}.distinct()))
            .put("inputs",JSONArray(records.sortedBy{it.id}.map{JSONObject().put("record_id",it.id).put("revision",it.revision).put("taken_utc",it.taken_utc).put("amount",it.actual_dose ?: JSONObject.NULL).put("medication_id",it.medication_id).put("input_snapshot",JSONObject(HistoricalContext.resolved(it,rules)))})).toString()
    }
}
