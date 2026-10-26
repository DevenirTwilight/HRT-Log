package net.plainnotes.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/** Independent, bounded reader for the verified Chrysalide exportAllData() v5 shape. */
object Chrysalide {
    class InvalidExport(val reason:String):Exception(reason)
    data class Medication(val id:Long,val name:String,val type:String,val dosage:Double?,val unit:String?,val method:String?,val route:String?,val sourceJson:String)
    data class Log(val id:Long,val medicationId:Long,val at:Instant,val taken:Boolean,val scheduledTime:String?,val doseIndex:Int?,val notes:String?,val sideEffects:String?,val applicationZone:String?,val sourceJson:String,val digest:String)
    data class Result(val marker:String,val value:Double,val unit:String)
    data class BloodTest(val id:Long,val at:Instant?,val dateOnly:String?,val laboratory:String?,val results:List<Result>,val notes:String?,val sourceJson:String,val digest:String)
    data class Export(val version:Int,val exportedAt:String,val exportDigest:String,val medications:List<Medication>,val logs:List<Log>,val bloodTests:List<BloodTest>,val unsupported:Map<String,Int>)

    private val arrays=listOf("medications","medicationLogs","bloodTests","physicalProgress","appointments","reminders","userProfile","journalEntries","objectives","milestones","treatmentChanges","practitioners","acts","actTodos")
    fun read(text:String):Export {
        if(text.toByteArray(Charsets.UTF_8).size>32*1024*1024)throw InvalidExport("TOO_LARGE")
        ImportFormatDetector.bounds(text,100000)
        val root=try { val t=JSONTokener(text.removePrefix("\uFEFF"));val v=t.nextValue();if(t.nextClean()!='\u0000'||v !is JSONObject)throw InvalidExport("INVALID_JSON");v }
            catch(e:InvalidExport){throw e}catch(_:Exception){throw InvalidExport("INVALID_JSON")}
        return readRoot(root)
    }
    internal fun readRoot(root:JSONObject):Export {
        val version=root.opt("version") as? Number ?: throw InvalidExport("UNSUPPORTED_VERSION")
        if(version.toDouble()!=5.0)throw InvalidExport("UNSUPPORTED_VERSION")
        val exported=root.opt("exportedAt") as? String ?: throw InvalidExport("INVALID_EXPORT_DATE")
        parseInstant(exported) ?: throw InvalidExport("INVALID_EXPORT_DATE")
        val tables=arrays.associateWith { root.opt(it) as? JSONArray ?: throw InvalidExport("MISSING_TABLE") }
        val total=tables.values.sumOf{it.length()};if(total>100000)throw InvalidExport("TOO_LARGE")
        val ids=hashMapOf<String,Set<Long>>()
        fun rows(table:String, transform:(JSONObject,Long,String)->Unit) {
            val seen=hashSetOf<Long>();val a=tables.getValue(table)
            for(i in 0 until a.length()) {
                val o=a.optJSONObject(i) ?: throw InvalidExport("INVALID_ROW")
                val raw=o.opt("id") as? Number ?: throw InvalidExport("INVALID_ID")
                val id=raw.toLong();if(raw.toDouble()!=id.toDouble()||id !in 0..9_007_199_254_740_991L||!seen.add(id))throw InvalidExport("INVALID_ID")
                transform(o,id,canonical(o))
            }
            ids[table]=seen
        }
        val medications=mutableListOf<Medication>()
        rows("medications"){o,id,_ ->
            val name=o.opt("name") as? String ?: throw InvalidExport("INVALID_MEDICATION")
            val type=o.opt("type") as? String ?: throw InvalidExport("INVALID_MEDICATION")
            if(type !in setOf("estrogen","antiandrogen","progesteron","testosterone","gnrh","other"))throw InvalidExport("INVALID_MEDICATION")
            requireText(o,"unit",true)
            val method=requireText(o,"method",true)!!
            if(method !in setOf("pill","injection","patch","gel","implant"))throw InvalidExport("INVALID_MEDICATION")
            for(key in listOf("startDate","createdAt","updatedAt")) if((o.opt(key) as? String)?.let(::parseInstant)==null)throw InvalidExport("INVALID_MEDICATION")
            if(o.has("endDate")&&o.opt("endDate")!=JSONObject.NULL&&(o.opt("endDate") as? String)?.let(::parseInstant)==null)throw InvalidExport("INVALID_MEDICATION")
            if(o.opt("isActive") !is Boolean)throw InvalidExport("INVALID_MEDICATION")
            val dosage=(o.opt("dosage") as? Number)?.toDouble()?.takeIf{it.isFinite()&&it>0} ?: throw InvalidExport("INVALID_MEDICATION")
            val unit=requireText(o,"unit",true)
            val route=o.opt("pillRoute")?.let{it as? String ?: throw InvalidExport("INVALID_MEDICATION")} ?: o.opt("injectionRoute")?.let{it as? String ?: throw InvalidExport("INVALID_MEDICATION")}
            medications+=Medication(id,name.take(512),type,dosage,unit,method,route,canonical(o))
        }
        val logs=mutableListOf<Log>()
        rows("medicationLogs"){o,id,raw ->
            val med=o.opt("medicationId") as? Number ?: throw InvalidExport("INVALID_LOG")
            val medId=med.toLong();if(med.toDouble()!=medId.toDouble()||medId !in ids.getValue("medications"))throw InvalidExport("BROKEN_REFERENCE")
            val taken=o.opt("taken") as? Boolean ?: throw InvalidExport("INVALID_LOG")
            val timestamp=o.opt("timestamp") as? String ?: throw InvalidExport("INVALID_LOG")
            val at=parseInstant(timestamp) ?: throw InvalidExport("INVALID_LOG_TIME")
            for(key in listOf("scheduledTime","notes","sideEffects","applicationZone")) if(o.has(key)&&o.opt(key)!=JSONObject.NULL&&o.opt(key) !is String)throw InvalidExport("INVALID_LOG")
            val index=(o.opt("doseIndex") as? Number)?.toInt()
            if(index!=null && (index<0 || index.toDouble()!=(o.opt("doseIndex") as Number).toDouble()))throw InvalidExport("INVALID_LOG")
            logs+=Log(id,medId,at,taken,o.optString("scheduledTime").takeIf{o.has("scheduledTime")},index,o.optString("notes").takeIf{o.has("notes")},o.optString("sideEffects").takeIf{o.has("sideEffects")},o.optString("applicationZone").takeIf{o.has("applicationZone")},raw,sha(raw))
        }
        val tests=mutableListOf<BloodTest>()
        rows("bloodTests"){o,id,raw ->
            val date=o.opt("date") as? String ?: throw InvalidExport("INVALID_TEST")
            val at=parseInstant(date);val dateOnly=if(at==null)runCatching{LocalDate.parse(date).toString()}.getOrNull() else null
            if(at==null && dateOnly==null)throw InvalidExport("INVALID_TEST_DATE")
            val resultArray=o.opt("results") as? JSONArray ?: throw InvalidExport("INVALID_TEST")
            val rs=(0 until resultArray.length()).map { i->
                val r=resultArray.optJSONObject(i)?:throw InvalidExport("INVALID_RESULT")
                val marker=r.opt("marker") as? String?:throw InvalidExport("INVALID_RESULT")
                val value=(r.opt("value") as? Number)?.toDouble()?.takeIf{it.isFinite()}?:throw InvalidExport("INVALID_RESULT")
                val unit=r.opt("unit") as? String?:throw InvalidExport("INVALID_RESULT")
                if(unit.isBlank())throw InvalidExport("INVALID_RESULT")
                Result(marker,value,unit)
            }
            tests+=BloodTest(id,at,dateOnly,o.optString("lab").takeIf{o.has("lab")},rs,o.optString("notes").takeIf{o.has("notes")},raw,sha(raw))
        }
        val unsupported=linkedMapOf<String,Int>()
        for(table in arrays.filter{it !in setOf("medications","medicationLogs","bloodTests")}) if(tables.getValue(table).length()>0)unsupported[table]=tables.getValue(table).length()
        return Export(5,exported,sha(canonical(root)),medications,logs,tests,unsupported)
    }
    fun canonical(o:JSONObject):String= o.keys().asSequence().toList().sorted().joinToString(",","{","}"){k->quote(k)+":"+value(o.get(k))}
    private fun value(v:Any?):String=when(v){null,JSONObject.NULL->"null";is JSONObject->canonical(v);is JSONArray->(0 until v.length()).joinToString(",","[","]"){value(v.get(it))};is String->quote(v);is Boolean->v.toString();is Number->java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString();else->throw InvalidExport("INVALID_JSON")}
    private fun quote(s:String)="\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\""
    private fun requireText(o:JSONObject,key:String,required:Boolean):String? {
        if(!o.has(key)||o.opt(key)==JSONObject.NULL){if(required)throw InvalidExport("INVALID_MEDICATION");return null}
        return o.opt(key) as? String ?: throw InvalidExport("INVALID_MEDICATION")
    }
    private fun sha(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun parseInstant(s:String):Instant?=runCatching{Instant.parse(s)}.getOrNull()
}
