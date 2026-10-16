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
internal data class FrozenUseRecord(
    val uuid:String,val revision:Int,val takenUtc:Long,val takenZone:String?,val status:String,
    /** Exact IEEE-754 context of the old factual field, not a recovered decimal or a stock quantity. */
    val legacyActualDoseBits:String,val configSha256:String,
)
internal data class MeasuredConsumptionRequest(
    val datasetUuid:String,val medicationUuid:String,val record:FrozenUseRecord,val uses:List<ConfirmedStockUse>,
)
internal class FrozenConsumptionRequest internal constructor(val json:String) {
    val sha256:String get()=ReferencePayload.sha(json)
    fun request():MeasuredConsumptionRequest=MeasuredUsePayload.decode(json)
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
    fun record(uuid:String,value:RecordEntity):FrozenUseRecord {
        require(value.deleted_at_utc==null)
        val actual=requireNotNull(value.actual_dose)
        require(actual.isFinite() && actual>0)
        return FrozenUseRecord(uuid,value.revision,requireNotNull(value.taken_utc),value.taken_zone,value.status,
            java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits(actual)).padStart(16,'0'),ReferencePayload.sha(value.config_snapshot))
    }
    fun freeze(request:MeasuredConsumptionRequest):FrozenConsumptionRequest {
        require(request.uses.size<=64)
        val r=request.record
        val json=JSONObject().put("format",FORMAT).put("kind","CONSUME").put("stock_algorithm",StockQuantum.ALGORITHM)
            .put("conversion_algorithm",Measurements.ALGORITHM).put("dataset_uuid",request.datasetUuid).put("medication_uuid",request.medicationUuid)
            .put("record",JSONObject().put("uuid",r.uuid).put("revision",r.revision).put("taken_utc",r.takenUtc)
                .put("taken_zone",r.takenZone ?: JSONObject.NULL).put("status",r.status).put("legacy_actual_dose_bits",r.legacyActualDoseBits).put("config_sha256",r.configSha256))
            .put("uses",JSONArray(request.uses.sortedBy{it.containerUuid}.map {u->JSONObject().put("container_uuid",u.containerUuid)
                .put("presentation_event_uuid",u.presentationEventUuid).put("presentation_sha256",u.presentationSha256)
                .put("quantity",PackageMeasurementPayload.quantity(u.quantity)).put("user_confirmed",u.userConfirmed)}))
        return read(ReferencePayload.canonical(json))
    }
    fun read(json:String):FrozenConsumptionRequest {
        try {decode(json)}catch(e:IllegalArgumentException){throw e}catch(e:Exception){throw IllegalArgumentException("Invalid measured-use input",e)}
        return FrozenConsumptionRequest(json)
    }
    internal fun decode(payload:String):MeasuredConsumptionRequest {
        require(payload.toByteArray(Charsets.UTF_8).size<=ReferencePayload.MAX_BYTES)
        val json=JSONObject(payload);require(ReferencePayload.canonical(json)==payload);keys(json,fields)
        require(json.get("format") is Int && json.getInt("format")==FORMAT && string(json,"kind")=="CONSUME")
        require(string(json,"stock_algorithm")==StockQuantum.ALGORITHM && string(json,"conversion_algorithm")==Measurements.ALGORITHM)
        val dataset=uuid(json,"dataset_uuid");val medication=uuid(json,"medication_uuid")
        val r=json.getJSONObject("record");keys(r,recordFields)
        require(r.get("revision") is Int && r.getInt("revision")>0)
        require(r.get("taken_utc") is Int || r.get("taken_utc") is Long)
        val zone=if(r.isNull("taken_zone"))null else string(r,"taken_zone")
        if(zone!=null)java.time.ZoneId.of(zone)
        val status=string(r,"status");require(status in setOf("ON_TIME","LATE"))
        val bits=string(r,"legacy_actual_dose_bits");require(Regex("[0-9a-f]{16}").matches(bits))
        val legacy=java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(bits,16));require(legacy.isFinite() && legacy>0)
        val record=FrozenUseRecord(uuid(r,"uuid"),r.getInt("revision"),r.getLong("taken_utc"),zone,status,bits,hash(r,"config_sha256"))
        val rows=json.getJSONArray("uses");require(rows.length()<=64)
        val uses=(0 until rows.length()).map {i->
            val u=rows.getJSONObject(i);keys(u,useFields);require(u.get("user_confirmed") is Boolean && u.getBoolean("user_confirmed"))
            val quantity=PackageMeasurementPayload.quantity(u.getJSONObject("quantity"));require(quantity.value.signum()>0 && quantity.meaning.kind !in setOf(QuantityKind.ABSORBED_MASS,QuantityKind.RELEASE_RATE))
            ConfirmedStockUse(uuid(u,"container_uuid"),uuid(u,"presentation_event_uuid"),hash(u,"presentation_sha256"),quantity,true)
        }
        require(uses.map{it.containerUuid}.distinct().size==uses.size)
        require(uses.map{it.containerUuid}==uses.map{it.containerUuid}.sorted())
        // An empty list records missing actual-package confirmation; it never means zero actual use.
        return MeasuredConsumptionRequest(dataset,medication,record,uses)
    }
}
