package net.plainnotes.app.data

import net.plainnotes.app.domain.TreatmentEpochs
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Facts as known at capture time; never substitutes the mutable medication profile. */
object LabContext {
    const val WINDOW_HOURS=48
    const val MAX_BYTES=1_048_576
    fun build(lab:LabValueEntity,records:List<RecordEntity>,regimens:List<RegimenVersionEntity>,rules:Map<Long,String>,estimate:String?=null,historyPeriods:List<HistoryPeriodEntity> =emptyList()):String {
        val at=lab.sampled_utc
        val epoch=TreatmentEpochs.build(regimens.map{it.span()}).singleOrNull{it.contains(Instant.ofEpochMilli(at))}
        val active=regimens.filter{it.id in epoch?.regimenIds.orEmpty()}
        val actual=records.filter{it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc!=null && it.taken_utc<=at}
            .map{it to HistoricalContext.resolved(it,rules)}
        val latest=actual.groupBy{(r,json)->MedicationSnapshot.decode(json,r.medication_id)?.molecule?.let{"ingredient:$it"} ?: "unknown:${r.medication_id}"}
            .toSortedMap().values.flatMap{rows->val time=rows.maxOf{it.first.taken_utc!!};rows.filter{it.first.taken_utc==time}.sortedBy{it.first.id}}
        val doses=JSONArray(latest.map{(r,json)->
            val snapshot=MedicationSnapshot.decode(json,r.medication_id)
            JSONObject().put("ingredient",snapshot?.molecule ?: JSONObject.NULL).put("record_id",r.id).put("record_revision",r.revision)
                .put("medication_id",r.medication_id).put("name",snapshot?.name ?: JSONObject.NULL).put("taken_utc",r.taken_utc)
                .put("taken_zone",r.taken_zone).put("elapsed_ms",Math.subtractExact(at,r.taken_utc!!)).put("amount",r.actual_dose ?: JSONObject.NULL)
                .put("unit",snapshot?.unit ?: JSONObject.NULL).put("route",snapshot?.route ?: JSONObject.NULL).put("input_snapshot",JSONObject(json))
        })
        val from=Math.subtractExact(at,WINDOW_HOURS*3_600_000L)
        val nearby=records.filter{r->r.deleted_at_utc==null && when(r.status){"LATE"->r.taken_utc?.let{it in from..at}==true;"MISSED"->r.scheduled_utc?.let{it in from..at}==true;else->false}}
        val counts=JSONObject().put("late",nearby.count{it.status=="LATE"}).put("missed",nearby.count{it.status=="MISSED" && !it.unconfirmed}).put("unconfirmed",nearby.count{it.unconfirmed})
        return JSONObject().put("version",1).put("sampled_utc",at).put("sampled_zone",lab.sampled_zone).put("analyte_code",lab.analyte_code)
            .put("epoch",JSONObject().put("key",epoch?.key ?: JSONObject.NULL).put("from",epoch?.from?.toEpochMilli() ?: JSONObject.NULL)
                .put("until",epoch?.until?.toEpochMilli() ?: JSONObject.NULL).put("unknown",epoch==null).put("reconstructed",epoch?.reconstructed ?: false))
            .put("regimens",JSONArray(active.sortedBy{it.id}.map{JSONObject().put("id",it.id).put("medication_id",it.medication_id).put("origin",it.origin).put("definition",JSONObject(it.definition_json))}))
            .put("actual",doses).put("window_hours",WINDOW_HOURS).put("counts",counts)
            .put("nearby",JSONArray(nearby.sortedBy{it.id}.map{JSONObject().put("id",it.id).put("revision",it.revision).put("status",it.status).put("origin",it.origin)
                .put("time_utc",if(it.status=="LATE")it.taken_utc else it.scheduled_utc)}))
            .put("estimate",estimate?.let(::JSONObject) ?: JSONObject.NULL)
            .apply{confirmedPeriods(at,historyPeriods)?.let{put("confirmed_periods",it)}}.toString().also{validate(it)}
    }
    /** REQUIREMENTS §35a item 9: optional, only when a past period confirmed by the user contains the sample day; "由记录推定，已确认". */
    private fun confirmedPeriods(at:Long,rows:List<HistoryPeriodEntity>):JSONArray? {
        val hits=HistoryPeriods.confirmed(rows).filter{covers(it.from_date,it.until_date,it.zone,at)}.sortedBy{it.period_key}
        return if(hits.isEmpty())null else JSONArray(hits.map{JSONObject().put("period_key",it.period_key).put("revision",it.revision).put("medication_id",it.medication_id)
            .put("from_date",it.from_date).put("until_date",it.until_date ?: JSONObject.NULL).put("zone",it.zone).put("origin",it.origin)
            .put("standard",JSONObject(it.standard_json))})
    }
    private fun covers(from:String,until:String?,zone:String,at:Long):Boolean {
        val day=Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).toLocalDate()
        return day>=java.time.LocalDate.parse(from) && (until==null || day<java.time.LocalDate.parse(until))
    }
    fun validate(json:String):JSONObject {
        require(json.toByteArray(Charsets.UTF_8).size<=MAX_BYTES)
        BackupLimits.checkJson(json)
        val o=JSONObject(json);require(o.getInt("version")==1)
        val at=o.getLong("sampled_utc");ZoneId.of(o.getString("sampled_zone"));require(o.getString("analyte_code").isNotBlank())
        require(o.getInt("window_hours")==WINDOW_HOURS)
        val epoch=o.getJSONObject("epoch");val unknownEpoch=epoch.getBoolean("unknown");epoch.getBoolean("reconstructed")
        if(unknownEpoch)require(epoch.isNull("from") && epoch.isNull("until") && epoch.isNull("key") && !epoch.getBoolean("reconstructed"))
        else {require(!epoch.isNull("from") && epoch.getLong("from")<=at);if(!epoch.isNull("until"))require(at<epoch.getLong("until"))}
        val regimens=o.getJSONArray("regimens");val regimenIds=mutableSetOf<Long>();val medicationIds=mutableSetOf<Long>();var reconstructed=false
        for(i in 0 until regimens.length()) {
            val r=regimens.getJSONObject(i);require(r.getLong("id")>0 && regimenIds.add(r.getLong("id")) && r.getLong("medication_id")>0 && medicationIds.add(r.getLong("medication_id")))
            require(r.getString("origin") in listOf("APP","LEGACY_RULE"));reconstructed=reconstructed || r.getString("origin")=="LEGACY_RULE"
            RegimenDefinition.read(r.getJSONObject("definition").toString())
        }
        require(!unknownEpoch || regimens.length()==0)
        if(!unknownEpoch)require(epoch.getBoolean("reconstructed")==reconstructed && epoch.getString("key")=="epoch:${epoch.getLong("from")}:${regimenIds.sorted().joinToString(",")}")
        val actual=o.getJSONArray("actual");val groups=mutableMapOf<String,Long>();val actualIds=mutableSetOf<Long>()
        for(i in 0 until actual.length()) {
            val r=actual.getJSONObject(i);require(r.getLong("record_id")>0 && r.getInt("record_revision")>0 && r.getLong("medication_id")>0)
            val taken=r.getLong("taken_utc");require(taken<=at && r.getLong("elapsed_ms")==Math.subtractExact(at,taken));ZoneId.of(r.getString("taken_zone"))
            if(!r.isNull("amount"))require(r.getDouble("amount").let{it.isFinite() && it>0})
            val saved=MedicationSnapshot.decode(r.getJSONObject("input_snapshot").toString(),r.getLong("medication_id"))
            for((key,v) in listOf("ingredient" to saved?.molecule,"name" to saved?.name,"route" to saved?.route,"unit" to saved?.unit))require((if(r.isNull(key))null else r.getString(key))==v)
            val group=if(r.isNull("ingredient"))"unknown:${r.getLong("medication_id")}" else "ingredient:${r.getString("ingredient")}";
            require(actualIds.add(r.getLong("record_id")) && (groups[group]==null || groups[group]==taken));groups[group]=taken
        }
        val nearby=o.getJSONArray("nearby");var late=0;var missed=0;var unknown=0;val ids=mutableSetOf<Long>()
        val from=Math.subtractExact(at,WINDOW_HOURS*3_600_000L)
        for(i in 0 until nearby.length()) {
            val r=nearby.getJSONObject(i);require(ids.add(r.getLong("id")) && r.getLong("id")>0 && r.getInt("revision")>0 && r.getLong("time_utc") in from..at)
            require(r.getString("origin") in listOf("APP","IMPORT_HT","IMPORT_TM","AUTO_MISSED"))
            when(r.getString("status")){"LATE"->{require(r.getString("origin")!="AUTO_MISSED");late++};"MISSED"->if(r.getString("origin")=="AUTO_MISSED")unknown++ else missed++;else->error("Invalid nearby event")}
        }
        val counts=o.getJSONObject("counts");require(counts.getInt("late")==late && counts.getInt("missed")==missed && counts.getInt("unconfirmed")==unknown)
        if(o.has("confirmed_periods")) {
            val periods=o.getJSONArray("confirmed_periods");require(periods.length()>0);val keys=mutableSetOf<String>()
            for(i in 0 until periods.length()) {
                val p=periods.getJSONObject(i);val key=p.getString("period_key");java.util.UUID.fromString(key);require(keys.add(key))
                require(p.getInt("revision")>=1 && p.getLong("medication_id")>0 && p.getString("origin")==HistoryPeriods.ORIGIN)
                val until=if(p.isNull("until_date"))null else p.getString("until_date")
                until?.let{require(java.time.LocalDate.parse(it)>java.time.LocalDate.parse(p.getString("from_date")))}
                require(covers(p.getString("from_date"),until,p.getString("zone"),at));HistoryPeriods.readStandard(p.getJSONObject("standard").toString())
            }
        }
        require(o.has("estimate") && (o.isNull("estimate") || o.get("estimate") is JSONObject))
        o.optJSONObject("estimate")?.let{e->
            require(e.getInt("version")==1 && e.get("calibrated")==false && e.getLong("sampled_utc")==at && e.getInt("calculator_version")==1)
            require(e.getInt("used_doses")>=0 && e.getInt("skipped_doses")>=0)
            if(!e.isNull("weight_kg_at_capture"))require(e.getDouble("weight_kg_at_capture").let{it.isFinite() && it>0})
            val units=mapOf("E2" to "pg/mL","CPA" to "ng/mL","SPIRONOLACTONE" to "ng/mL","CANRENONE" to "ng/mL","PROGESTERONE" to "ng/mL")
            val curves=mutableSetOf<String>();val values=e.getJSONArray("values")
            for(i in 0 until values.length()) {
                val v=values.getJSONObject(i);val curve=v.getString("curve");require(curves.add(curve) && units[curve]==v.getString("unit"))
                require(v.getDouble("value").let{it.isFinite() && it>=0})
                for(k in listOf("p5","p95"))if(!v.isNull(k))require(v.getDouble(k).let{it.isFinite() && it>=0})
                if(!v.isNull("p5") && !v.isNull("p95"))require(v.getDouble("p5")<=v.getDouble("p95"))
                val flags=v.getJSONArray("flags");for(j in 0 until flags.length())require(flags.getString(j) in listOf("EXTRAPOLATED_TIER","EXTRAPOLATED_AFTER_CALIBRATED_HOURS","NO_PRODUCT_DATA","ILLUSTRATIVE"))
            }
            val document=e.getJSONObject("parameter_document");document.getString("version");document.getJSONObject("models");document.getJSONArray("references")
            e.getJSONArray("missing");e.getJSONArray("unsupported")
            val inputs=e.getJSONArray("inputs");val ids=mutableSetOf<Long>()
            for(i in 0 until inputs.length()) {
                val r=inputs.getJSONObject(i);require(r.getLong("record_id")>0 && ids.add(r.getLong("record_id")) && r.getInt("revision")>0 && r.getLong("medication_id")>0 && r.getLong("taken_utc")<=at)
                if(!r.isNull("amount"))require(r.getDouble("amount").let{it.isFinite() && it>0});r.getJSONObject("input_snapshot")
            }
        }
        return o
    }
    fun validateState(db:androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("SELECT lab_id,revision,captured_utc,origin,context_json FROM lab_context_revision ORDER BY lab_id,revision").use{c->
            val revisions=mutableMapOf<Long,Int>();val captures=mutableMapOf<Long,Long>()
            while(c.moveToNext()) {
                val id=c.getLong(0);val revision=c.getInt(1);val captured=c.getLong(2)
                require(revision==(revisions[id] ?: 0)+1 && captured>=(captures[id] ?: Long.MIN_VALUE));require(c.getString(3) in listOf("AT_ENTRY","RECONSTRUCTED","SAMPLE_CHANGED"))
                validate(c.getString(4));revisions[id]=revision;captures[id]=captured
            }
        }
        db.query("SELECT c.context_json,l.sampled_utc,l.sampled_zone,l.analyte_code FROM lab_context_revision c JOIN lab_value l ON l.id=c.lab_id WHERE c.revision=(SELECT MAX(revision) FROM lab_context_revision WHERE lab_id=c.lab_id)").use{c->while(c.moveToNext()) {
            val o=validate(c.getString(0));require(o.getLong("sampled_utc")==c.getLong(1) && o.getString("sampled_zone")==c.getString(2) && o.getString("analyte_code")==c.getString(3))
        }}
    }
}
