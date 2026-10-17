package net.plainnotes.app.data

import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.StockQuantum
import org.json.JSONArray
import org.json.JSONObject

/** A user confirms each physical source and amount separately; different units/specifications are never summed. */
data class ConfirmedStockUse(
    val containerUuid:String,val presentationEventUuid:String,val presentationSha256:String,
    val quantity:Quantity,val userConfirmed:Boolean,
)
data class ConfirmedStockCount(
    val containerUuid:String,val presentationEventUuid:String,val presentationSha256:String,
    val remaining:Quantity,val userConfirmed:Boolean,
)
enum class MeasuredStockEffect { DEDUCT_CURRENT, ALREADY_ACCOUNTED, UNKNOWN }
/** Clinical dose and physical quantity remain independent user-entered facts. No recommended values. */
data class MeasuredActualUseDraft(
    val takenUtc:Long,val takenZone:String,val clinicalDose:Double,
    val uses:List<ConfirmedStockUse>,val stockEffect:MeasuredStockEffect,
    val unlinkedActual:Quantity?=null,val userConfirmed:Boolean,
)
internal data class FrozenUseRecord(
    val uuid:String,val revision:Int,val takenUtc:Long,val takenZone:String?,val status:String,
    /** Exact IEEE-754 context of the old factual field, not a recovered decimal or a stock quantity. */
    val legacyActualDoseBits:String,val configSha256:String,val scheduleSha256:String?=null,
)
internal data class MeasuredConsumptionRequest(
    val datasetUuid:String,val medicationUuid:String,val record:FrozenUseRecord,val uses:List<ConfirmedStockUse>,
    val stockEffect:MeasuredStockEffect=MeasuredStockEffect.DEDUCT_CURRENT,val unlinkedActual:Quantity?=null,
)
internal class FrozenConsumptionRequest internal constructor(val json:String) {
    val sha256:String by lazy {ReferencePayload.sha(json)}
    private val decoded:MeasuredConsumptionRequest by lazy {
        MeasuredUsePayload.decode(json).let{it.copy(uses=java.util.Collections.unmodifiableList(it.uses.toList()))}
    }
    fun request():MeasuredConsumptionRequest=decoded
}

/** Immutable input capture only. No database writes, actual-record creation, automatic allocation or dose inference. */
internal object MeasuredUsePayload {
    const val FORMAT=1
    private val digest=Regex("[0-9a-f]{64}")
    private val fields=setOf("format","kind","stock_algorithm","conversion_algorithm","dataset_uuid","medication_uuid","record","uses")
    private val recordFields=setOf("uuid","revision","taken_utc","taken_zone","status","legacy_actual_dose_bits","config_sha256")
    private val useFields=setOf("container_uuid","presentation_event_uuid","presentation_sha256","quantity","user_confirmed")
    private fun keys(json:JSONObject,expected:Set<String>)=require(json.keys().asSequence().toSet()==expected)
    private fun string(json:JSONObject,key:String):String {require(json.get(key) is String);return json.getString(key)}
    private fun uuid(json:JSONObject,key:String)=string(json,key).also{require(ReferencePayload.uuid(it))}
    private fun hash(json:JSONObject,key:String)=string(json,key).also{require(digest.matches(it))}
    fun scheduleSha(rule:Long?,key:String?,at:Long?,zone:String?,planned:Double?,late:Int?):String? {
        if(listOf(rule,key,at,zone,planned,late).all{it==null})return null
        require(rule!=null && rule>0 && !key.isNullOrBlank() && at!=null && zone!=null && planned!=null && planned.isFinite() && planned>0 && late!=null && late>=0)
        java.time.ZoneId.of(zone)
        return ReferencePayload.sha(ReferencePayload.canonical(JSONObject().put("rule",rule).put("key",key).put("at",at).put("zone",zone)
            .put("planned_bits",java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits(planned)).padStart(16,'0')).put("late",late)))
    }
    fun scheduleSha(value:RecordEntity)=scheduleSha(value.rule_version_id,value.slot_key,value.scheduled_utc,value.scheduled_zone,value.planned_dose,value.late_after_minutes_snapshot)
    fun record(uuid:String,value:RecordEntity):FrozenUseRecord {
        require(value.deleted_at_utc==null)
        val actual=requireNotNull(value.actual_dose)
        require(actual.isFinite() && actual>0)
        return FrozenUseRecord(uuid,value.revision,requireNotNull(value.taken_utc),value.taken_zone,value.status,
            java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits(actual)).padStart(16,'0'),ReferencePayload.sha(value.config_snapshot),scheduleSha(value))
    }
    fun freeze(request:MeasuredConsumptionRequest):FrozenConsumptionRequest {
        require(request.uses.size<=64)
        val r=request.record
        val extended=r.scheduleSha256!=null || request.stockEffect!=MeasuredStockEffect.DEDUCT_CURRENT || request.unlinkedActual!=null
        val json=JSONObject().put("format",if(r.scheduleSha256!=null)3 else if(extended)2 else FORMAT).put("kind","CONSUME").put("stock_algorithm",StockQuantum.ALGORITHM)
            .put("conversion_algorithm",Measurements.ALGORITHM).put("dataset_uuid",request.datasetUuid).put("medication_uuid",request.medicationUuid)
            .put("record",JSONObject().put("uuid",r.uuid).put("revision",r.revision).put("taken_utc",r.takenUtc)
                .put("taken_zone",r.takenZone ?: JSONObject.NULL).put("status",r.status).put("legacy_actual_dose_bits",r.legacyActualDoseBits).put("config_sha256",r.configSha256))
            .put("uses",JSONArray(request.uses.sortedBy{it.containerUuid}.map {u->JSONObject().put("container_uuid",u.containerUuid)
                .put("presentation_event_uuid",u.presentationEventUuid).put("presentation_sha256",u.presentationSha256)
                .put("quantity",PackageMeasurementPayload.quantity(u.quantity)).put("user_confirmed",u.userConfirmed)}))
        if(r.scheduleSha256!=null)json.getJSONObject("record").put("schedule_sha256",r.scheduleSha256)
        if(extended)json.put("stock_effect",request.stockEffect.name).put("unlinked_actual",request.unlinkedActual?.let(PackageMeasurementPayload::quantity) ?: JSONObject.NULL)
        return read(ReferencePayload.canonical(json))
    }
    fun read(json:String):FrozenConsumptionRequest {
        val frozen=FrozenConsumptionRequest(json)
        try {frozen.request()}catch(e:IllegalArgumentException){throw e}catch(e:Exception){throw IllegalArgumentException("Invalid measured-use input",e)}
        return frozen
    }
    internal fun decode(payload:String):MeasuredConsumptionRequest {
        require(payload.toByteArray(Charsets.UTF_8).size<=ReferencePayload.MAX_BYTES)
        val json=JSONObject(payload);require(ReferencePayload.canonical(json)==payload)
        require(json.get("format") is Int && json.getInt("format") in 1..3 && string(json,"kind")=="CONSUME")
        val scheduled=json.getInt("format")==3
        val extended=json.getInt("format")>=2
        keys(json,if(extended)fields+setOf("stock_effect","unlinked_actual") else fields)
        val effect=if(extended)MeasuredStockEffect.valueOf(string(json,"stock_effect")) else MeasuredStockEffect.DEDUCT_CURRENT
        val unlinked=if(!extended || json.isNull("unlinked_actual"))null else PackageMeasurementPayload.quantity(json.getJSONObject("unlinked_actual")).also {
            require(it.value.signum()>0 && it.meaning.kind !in setOf(QuantityKind.ABSORBED_MASS,QuantityKind.RELEASE_RATE))
        }
        require(string(json,"stock_algorithm")==StockQuantum.ALGORITHM && string(json,"conversion_algorithm")==Measurements.ALGORITHM)
        val dataset=uuid(json,"dataset_uuid");val medication=uuid(json,"medication_uuid")
        val r=json.getJSONObject("record");keys(r,if(scheduled)recordFields+"schedule_sha256" else recordFields)
        require(r.get("revision") is Int && r.getInt("revision")>0)
        require(r.get("taken_utc") is Int || r.get("taken_utc") is Long)
        val zone=if(r.isNull("taken_zone"))null else string(r,"taken_zone")
        if(zone!=null)java.time.ZoneId.of(zone)
        val status=string(r,"status");require(status in setOf("ON_TIME","LATE"))
        val bits=string(r,"legacy_actual_dose_bits");require(Regex("[0-9a-f]{16}").matches(bits))
        val legacy=java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(bits,16));require(legacy.isFinite() && legacy>0)
        val record=FrozenUseRecord(uuid(r,"uuid"),r.getInt("revision"),r.getLong("taken_utc"),zone,status,bits,hash(r,"config_sha256"),if(scheduled)hash(r,"schedule_sha256") else null)
        val rows=json.getJSONArray("uses");require(rows.length()<=64)
        val uses=(0 until rows.length()).map {i->
            val u=rows.getJSONObject(i);keys(u,useFields);require(u.get("user_confirmed") is Boolean && u.getBoolean("user_confirmed"))
            val quantity=PackageMeasurementPayload.quantity(u.getJSONObject("quantity"));require(quantity.value.signum()>0 && quantity.meaning.kind !in setOf(QuantityKind.ABSORBED_MASS,QuantityKind.RELEASE_RATE))
            ConfirmedStockUse(uuid(u,"container_uuid"),uuid(u,"presentation_event_uuid"),hash(u,"presentation_sha256"),quantity,true)
        }
        require(uses.map{it.containerUuid}.distinct().size==uses.size)
        require(uses.map{it.containerUuid}==uses.map{it.containerUuid}.sorted())
        // An empty list records missing actual-package confirmation; it never means zero actual use.
        require(unlinked==null || uses.isEmpty())
        require(json.getInt("format")!=2 || effect!=MeasuredStockEffect.DEDUCT_CURRENT || unlinked!=null)
        return MeasuredConsumptionRequest(dataset,medication,record,uses,effect,unlinked)
    }
}
