package net.plainnotes.app.importer

import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Independently authored snapshot7 projection. Source catalog tokens are identities, not PK inputs. */
object Featherline {
    class Invalid:Exception()
    class Unsupported:Exception()
    data class Medicine(val sourceId:String,val name:String,val molecule:String,val route:String,val ester:String?)
    data class Dose(val id:String,val medicine:Medicine,val at:Long,val zone:String,val amount:Double,val sourceJson:String)
    data class Lab(val id:String,val panel:String,val analyte:String,val value:Double,val unit:String,val at:Long,val zone:String,val note:String?,val sourceJson:String)
    data class Issue(val kind:String,val id:String,val reason:String)
    class Export internal constructor(val doses:List<Dose>,val labs:List<Lab>,val issues:List<Issue>,val counts:Map<String,Int>,val exportedAt:Long)
    private val catalog=mapOf("ESTRADIOL" to Pair("E2",null),"ESTRADIOL_VALERATE" to Pair("E2","EV"),
        "SPIRONOLACTONE" to Pair("SPI",null),"CYPROTERONE_ACETATE" to Pair("CPA",null),
        "BICALUTAMIDE" to Pair("BICA",null),"FINASTERIDE" to Pair("FIN",null),"DUTASTERIDE" to Pair("DUT",null))
    private fun id(row:JSONObject,key:String="uuid"):String {
        val s=row.opt(key) as? String ?: throw Invalid()
        if(runCatching{UUID.fromString(s).toString()==s}.getOrDefault(false).not())throw Invalid()
        return s
    }
    private fun integer(row:JSONObject,key:String):Long {val n=row.opt(key);if(n !is Int && n !is Long)throw Invalid();return (n as Number).toLong()}
    private fun zone(row:JSONObject,key:String):String=(row.opt(key) as? String)?.takeIf{runCatching{ZoneId.of(it)}.isSuccess} ?: throw Invalid()
    private fun array(row:JSONObject,key:String)=row.opt(key) as? JSONArray ?: throw Invalid()
    private fun rows(a:JSONArray):List<JSONObject> {
        val seen=hashSetOf<String>();return (0 until a.length()).map {i->
            val r=a.optJSONObject(i) ?: throw Invalid();if(!seen.add(id(r)))throw Invalid();r
        }
    }
    private fun decimal(row:JSONObject,key:String):BigDecimal? = (row.opt(key) as? Number)?.let{runCatching{BigDecimal(it.toString())}.getOrNull()}
    fun read(text:String):Export {
        if(text.toByteArray(Charsets.UTF_8).size>8*1024*1024)throw Invalid()
        ImportFormatDetector.bounds(text)
        val tok=JSONTokener(text);val root=try {tok.nextValue() as? JSONObject ?: throw Invalid()}catch(_:org.json.JSONException){throw Invalid()}
        if(tok.nextClean()!='\u0000')throw Invalid()
        if(root.opt("snapshotVersion")!=7)throw Unsupported()
        if(root.optJSONObject("app")?.opt("packageName")!="com.mkx.hrttracker")throw Invalid()
        val at=integer(root,"exportedAtEpochMillis");Instant.ofEpochMilli(at)
        if(root.opt("settings") !is JSONObject || root.opt("userProfile") !is JSONObject)throw Invalid()
        val names=listOf("medicines","medicationGroups","medicationLogs","customBloodAnalytes","bloodTestPanels")
        val source=names.associateWith{rows(array(root,it))}.toMutableMap()
        for(name in listOf("trackedDates","notes"))source[name]=if(root.has(name))rows(array(root,name)) else emptyList()
        var total=source.values.sumOf{it.size.toLong()}
        if(total>100000)throw Invalid()
        val meds=source.getValue("medicines").associateBy{ id(it) }
        val groups=source.getValue("medicationGroups").associateBy{ id(it) }
        val custom=source.getValue("customBloodAnalytes").map{ id(it) }.toSet()
        val groupTimes=hashMapOf<String,Set<String>>()
        for((gid,g) in groups) {
            val schedule=g.optJSONObject("schedule") ?: throw Invalid()
            val times=array(schedule,"times");val timeIds=hashSetOf<String>()
            for(i in 0 until times.length()) {
                val row=times.optJSONObject(i) ?: throw Invalid()
                if(!row.isNull("uuid"))if(!timeIds.add(id(row)))throw Invalid()
            }
            groupTimes[gid]=timeIds
            for(r in rows(array(g,"medications"))) {
                val med=r.optString("medicineUuid","")
                if(med !in meds && !(r.opt("applicationType")=="PATCH_OFF" && r.isNull("medicineUuid")))throw Invalid()
            }
        }
        val doses=mutableListOf<Dose>();val labs=mutableListOf<Lab>();val issues=mutableListOf<Issue>()
        for(log in source.getValue("medicationLogs")) {
            val lid=id(log);val route=log.opt("applicationType") as? String ?: throw Invalid()
            val time=integer(log,"appliedAtEpochMillis");val tz=zone(log,"appliedAtTimeZoneId");Instant.ofEpochMilli(time)
            if(!log.isNull("sourceGroupUuid")) {
                val group=id(log,"sourceGroupUuid");if(group !in groups)throw Invalid()
                if(!log.isNull("scheduleTimeUuid") && id(log,"scheduleTimeUuid") !in groupTimes.getValue(group))throw Invalid()
            } else if(!log.isNull("scheduleTimeUuid"))throw Invalid()
            if(route=="PATCH_OFF" && log.isNull("medicineUuid")){issues+=Issue("LOG",lid,"PATCH_REMOVAL");continue}
            val med=meds[id(log,"medicineUuid")] ?: throw Invalid()
            val key=med.opt("medicationKey") as? String
            val identity=if(med.opt("selectionKind")=="CATALOG")catalog[key] else null
            if(identity==null){issues+=Issue("LOG",lid,"UNSUPPORTED_MEDICINE");continue}
            val expectedCategory=if(identity.first=="E2")"ESTRADIOL" else "ANTIANDROGEN"
            if(med.opt("category")!=expectedCategory || log.opt("category")!=expectedCategory)throw Invalid()
            if(route !in setOf("ORAL","SUBLINGUAL")){issues+=Issue("LOG",lid,"UNSUPPORTED_ROUTE");continue}
            if(!log.isNull("doseAmountDelta")){issues+=Issue("LOG",lid,"ACTUAL_DELTA");continue}
            val unit=med.opt("displayDoseUnit")
            if(unit!=null && unit!=JSONObject.NULL && unit!="MG"){issues+=Issue("LOG",lid,"UNSUPPORTED_UNIT");continue}
            val strength=decimal(med,"strengthMgPerTablet")
            val count=integer(log,"count");if(count<=0 || strength==null || strength.signum()<=0)throw Invalid()
            val amount=when {
                med.opt("preparationType")=="PILL" && log.opt("doseInstructionKind")=="TABLET_FRACTION"->{
                    val n=integer(log,"tabletFractionNumerator");val d=integer(log,"tabletFractionDenominator")
                    if(n<=0 || d<=0)throw Invalid()
                    runCatching{strength.multiply(BigDecimal(n)).multiply(BigDecimal(count)).divide(BigDecimal(d))}.getOrNull()
                }
                med.opt("preparationType")=="CAPSULE" && log.opt("doseInstructionKind")=="WHOLE_UNIT"->strength.multiply(BigDecimal(count))
                else->null
            }
            val value=amount?.toDouble()
            if(value==null || !value.isFinite() || value<=0 || BigDecimal.valueOf(value).compareTo(amount)!=0){issues+=Issue("LOG",lid,"UNREPRESENTABLE_DOSE");continue}
            val evidence=Mona.canonical(JSONObject().put("medicine",med).put("log",log))
            doses+=Dose(lid,Medicine(id(med),key!!,identity.first,route,identity.second),time,tz,value,evidence)
        }
        val resultIds=hashSetOf<String>()
        for(panel in source.getValue("bloodTestPanels")) {
            val pid=id(panel);val time=integer(panel,"collectedAtInstantEpochMillis");val tz=zone(panel,"collectedAtTimeZoneId")
            val note=panel.opt("notes").let{if(it==null || it==JSONObject.NULL)null else it as? String ?: throw Invalid()}
            val results=rows(array(panel,"results"));total+=results.size;if(total>100000)throw Invalid()
            for(result in results) {
                val rid=id(result);if(!resultIds.add(rid))throw Invalid()
                val key=result.opt("builtinAnalyteKey") as? String
                if(!result.isNull("customAnalyteUuid") && id(result,"customAnalyteUuid") !in custom)throw Invalid()
                val code=when(key){"e2"->"E2";"t"->"T";else->null}
                if(code==null || !result.isNull("customAnalyteUuid")){issues+=Issue("RESULT",rid,"UNSUPPORTED_ANALYTE");continue}
                val rawUnit=result.opt("unitSnapshot") as? String ?: throw Invalid()
                val unit=mapOf("pg_ml" to "pg/mL","pmol_l" to "pmol/L","ng_dl" to "ng/dL","ng_ml" to "ng/mL","nmol_l" to "nmol/L")[rawUnit]
                val allowed=if(code=="E2")setOf("pg/mL","pmol/L") else setOf("ng/dL","ng/mL","nmol/L")
                val original=decimal(result,"value");val v=original?.toDouble()
                if(unit !in allowed || v==null || !v.isFinite() || v<0 || BigDecimal.valueOf(v).compareTo(original)!=0){issues+=Issue("RESULT",rid,"UNSUPPORTED_RESULT");continue}
                val p=JSONObject(panel.toString());p.remove("results")
                labs+=Lab(rid,pid,code,v,requireNotNull(unit),time,tz,note,Mona.canonical(JSONObject().put("panel",p).put("result",result)))
            }
        }
        return Export(java.util.Collections.unmodifiableList(doses),java.util.Collections.unmodifiableList(labs),java.util.Collections.unmodifiableList(issues),
            java.util.Collections.unmodifiableMap(source.mapValues{it.value.size}+mapOf("stock" to meds.values.count{!it.isNull("stock")},"settings" to root.getJSONObject("settings").length(),"userProfile" to root.getJSONObject("userProfile").length())),at)
    }
    /** Re-evaluate one immutable receipt with the SAME source clinical conversion rules.
     * Parent schedule references are validated at initial full-snapshot import. They are
     * retained as provenance only, never restored as an active schedule or clinical fact. */
    fun evidence(kind:String,raw:String):Export {
        val evidence=JSONObject(raw)
        val root=JSONObject().put("snapshotVersion",7).put("app",JSONObject().put("packageName","com.mkx.hrttracker"))
            .put("exportedAtEpochMillis",0).put("settings",JSONObject()).put("userProfile",JSONObject())
        for(key in listOf("medicines","medicationGroups","medicationLogs","customBloodAnalytes","bloodTestPanels"))root.put(key,JSONArray())
        if(kind=="LOG") {
            val log=JSONObject(evidence.getJSONObject("log").toString())
            for(key in listOf("sourceGroupUuid","scheduleTimeUuid"))if(!log.isNull(key))id(log,key)
            log.remove("sourceGroupUuid");log.remove("scheduleTimeUuid")
            root.getJSONArray("medicines").put(evidence.getJSONObject("medicine"));root.getJSONArray("medicationLogs").put(log)
        } else {
            require(kind=="RESULT")
            val panel=JSONObject(evidence.getJSONObject("panel").toString()).put("results",JSONArray().put(evidence.getJSONObject("result")))
            root.getJSONArray("bloodTestPanels").put(panel)
        }
        return read(root.toString())
    }

}
