package net.plainnotes.app.data

import net.plainnotes.app.domain.measurement.*
import net.plainnotes.app.domain.stock.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/** Explicit transcription, not official verification, a recommended dose or an absorption model. */
data class DeclaredPackageContent(
    val from:Quantity,val to:Quantity,val source:String,val locator:String,val recordedOn:String,
    val conditions:String,val confirmedFromSource:Boolean,val fractionalContentVerified:Boolean=false,
)
data class PackageMeasurementDraft(
    val product:UserReferenceProduct,val stockUnit:MeasureUnit,val stockMeaning:Meaning,
    val quantum:String,val confirmedCapacity:String?,val capacityBasis:StockCapacityBasis,
    val nominalTotal:Quantity?=null,val nominalUses:Quantity?=null,
    val relations:List<DeclaredPackageContent> = emptyList(),
)

/** The only retained state is canonical text. Caller-owned mutable lists cannot alter frozen facts. */
internal class FrozenPackageMeasurement internal constructor(val payload:String) {
    val sha256:String get()=ReferencePayload.sha(payload)
    fun product()=ReferencePayload.readProduct(JSONObject(payload).getJSONObject("product"))
    fun ref():PresentationRef {
        val json=JSONObject(payload);val product=json.getJSONObject("product")
        val identity=JSONObject(product.toString()).apply{remove("source_version");remove("recorded_on")}
        val productKey=ReferencePayload.optional(product,"cis")?.let{"declared-cis:$it"}
            ?: ReferencePayload.sha(ReferencePayload.canonical(identity))
        val physical=JSONObject().put("product",productKey).put("unit",json.getString("stock_unit"))
            .put("meaning",json.getJSONObject("stock_meaning")).put("quantum",json.getString("quantum"))
        val presentationKey=ReferencePayload.optional(product,"cip")?.let{"declared-cip:$it"}
            ?: ReferencePayload.sha(ReferencePayload.canonical(physical))
        // Per-container actual capacity is a distinct fact; it cannot alter the product specification.
        val specification=JSONObject(payload).apply{remove("confirmed_capacity");remove("capacity_basis")}
        return PresentationRef("USER_ENTERED",productKey,presentationKey,product.getString("source_version"),ReferencePayload.sha(ReferencePayload.canonical(specification)))
    }
    fun measurement():PresentationSnapshot {
        val json=JSONObject(payload);val ref=ref()
        val relations=json.getJSONArray("relations").let { rows -> (0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i)
            ContentRelation(PackageMeasurementPayload.quantity(row.getJSONObject("from")),PackageMeasurementPayload.quantity(row.getJSONObject("to")),
                ConversionEvidence(ref,row.getString("source"),row.getString("locator"),row.getString("recorded_on"),row.getString("conditions"),
                    row.getBoolean("confirmed_from_source"),row.getBoolean("fractional_content_verified")))
        } }
        fun optional(key:String)=if(json.isNull(key))null else PackageMeasurementPayload.quantity(json.getJSONObject(key))
        val capacity=ReferencePayload.optional(json,"confirmed_capacity")?.let { Quantity(BigDecimal(it),MeasureUnit.valueOf(json.getString("stock_unit")),PackageMeasurementPayload.meaning(json.getJSONObject("stock_meaning"))) }
        return PresentationSnapshot(ref,optional("nominal_total"),optional("nominal_uses"),relations,capacity)
    }
    fun stockScale():MeasureResult<StockQuantum> {
        val json=JSONObject(payload);val capacity=measurement().confirmedUsable ?: return MeasureResult.Failure(MeasureError.UNKNOWN_CAPACITY)
        return StockQuantum.create(capacity,Quantity(BigDecimal(json.getString("quantum")),capacity.unit,capacity.meaning),ActualPackage(ref(),true),StockCapacityBasis.valueOf(json.getString("capacity_basis")))
    }
}

/** Candidate P4 payload codec only. ReferenceSchema still rejects PRESENTATION/measurement activation. */
internal object PackageMeasurementPayload {
    private val fields=setOf("format","kind","namespace","source_version","product","stock_unit","stock_meaning","quantum","confirmed_capacity","capacity_basis","nominal_total","nominal_uses","relations")
    private val relationFields=setOf("from","to","source","locator","recorded_on","conditions","confirmed_from_source","fractional_content_verified")
    private fun keys(json:JSONObject,allowed:Set<String>)=require(json.keys().asSequence().toSet()==allowed)
    private fun text(json:JSONObject,key:String):String {
        require(json.get(key) is String)
        return json.getString(key).also{require(it.isNotBlank())}
    }
    private fun bool(json:JSONObject,key:String):Boolean {require(json.get(key) is Boolean);return json.getBoolean(key)}
    private fun exact(value:String):BigDecimal {
        val parsed=Measurements.parse(value)
        require(parsed is MeasureResult.Success)
        val number=parsed.value
        require((Measurements.decimalText(number) as MeasureResult.Success).value==value){"Noncanonical decimal"}
        return number
    }
    internal fun meaning(json:JSONObject):Meaning {
        keys(json,setOf("kind","identity"))
        return Meaning(QuantityKind.valueOf(text(json,"kind")),text(json,"identity"))
    }
    private fun meaning(value:Meaning)=JSONObject().put("kind",value.kind.name).put("identity",value.identity)
    internal fun quantity(json:JSONObject):Quantity {
        keys(json,setOf("value","unit","meaning"))
        return Quantity(exact(text(json,"value")),MeasureUnit.valueOf(text(json,"unit")),meaning(json.getJSONObject("meaning"))).also{require(Measurements.error(it)==null)}
    }
    private fun quantity(value:Quantity):JSONObject {
        require(Measurements.error(value)==null)
        return JSONObject().put("value",(Measurements.decimalText(value.value) as MeasureResult.Success).value)
            .put("unit",value.unit.name).put("meaning",meaning(value.meaning))
    }
    private fun decimalInput(value:String):String {
        val parsed=Measurements.parse(value);require(parsed is MeasureResult.Success)
        return (Measurements.decimalText(parsed.value) as MeasureResult.Success).value
    }
    fun freeze(draft:PackageMeasurementDraft):FrozenPackageMeasurement {
        require(draft.relations.size<=32 && draft.product.ingredients.size<=128 && draft.product.routes.size<=128)
        val json=JSONObject().put("format",1).put("kind","PRESENTATION").put("namespace","USER_ENTERED")
            .put("source_version",draft.product.sourceVersion).put("product",ReferencePayload.userProduct(draft.product))
            .put("stock_unit",draft.stockUnit.name).put("stock_meaning",meaning(draft.stockMeaning))
            .put("quantum",decimalInput(draft.quantum)).put("confirmed_capacity",draft.confirmedCapacity?.let(::decimalInput) ?: JSONObject.NULL)
            .put("capacity_basis",draft.capacityBasis.name).put("nominal_total",draft.nominalTotal?.let(::quantity) ?: JSONObject.NULL)
            .put("nominal_uses",draft.nominalUses?.let(::quantity) ?: JSONObject.NULL)
            .put("relations",JSONArray(draft.relations.map { r ->
                JSONObject().put("from",quantity(r.from)).put("to",quantity(r.to)).put("source",r.source).put("locator",r.locator)
                    .put("recorded_on",r.recordedOn).put("conditions",r.conditions).put("confirmed_from_source",r.confirmedFromSource)
                    .put("fractional_content_verified",r.fractionalContentVerified)
            }))
        return read(ReferencePayload.canonical(json))
    }
    /** Invalid external payloads share one explicit validation exception; no partial state is returned. */
    fun read(payload:String):FrozenPackageMeasurement = try { readCanonical(payload) }
        catch(e:IllegalArgumentException){throw e}
        catch(e:Exception){throw IllegalArgumentException("Invalid package measurement payload",e)}
    private fun readCanonical(payload:String):FrozenPackageMeasurement {
        require(payload.toByteArray(Charsets.UTF_8).size<=ReferencePayload.MAX_BYTES)
        val json=JSONObject(payload);require(ReferencePayload.canonical(json)==payload)
        keys(json,fields)
        require(json.get("format") is Int && json.getInt("format")==1)
        require(text(json,"kind")=="PRESENTATION" && text(json,"namespace")=="USER_ENTERED")
        val product=json.getJSONObject("product");ReferencePayload.validateProduct(product)
        require(text(json,"source_version")==product.getString("source_version"))
        val unit=MeasureUnit.valueOf(text(json,"stock_unit"));val meaning=meaning(json.getJSONObject("stock_meaning"))
        val quantum=Quantity(exact(text(json,"quantum")),unit,meaning)
        require(Measurements.error(quantum)==null && quantum.value.signum()>0)
        require(meaning.kind !in setOf(QuantityKind.ABSORBED_MASS,QuantityKind.RELEASE_RATE))
        val basis=StockCapacityBasis.valueOf(text(json,"capacity_basis"))
        val confirmed=basis in setOf(StockCapacityBasis.USER_COUNTED,StockCapacityBasis.USER_DECLARED)
        require(confirmed==!json.isNull("confirmed_capacity"))
        if(confirmed)exact(text(json,"confirmed_capacity"))
        listOf("nominal_total","nominal_uses").forEach { key ->if(!json.isNull(key))quantity(json.getJSONObject(key))}
        val relations=json.getJSONArray("relations");require(relations.length()<=32)
        for(i in 0 until relations.length()) {
            val row=relations.getJSONObject(i);keys(row,relationFields)
            quantity(row.getJSONObject("from"));quantity(row.getJSONObject("to"))
            listOf("source","locator","conditions").forEach{text(row,it)}
            LocalDate.parse(text(row,"recorded_on"));bool(row,"confirmed_from_source");bool(row,"fractional_content_verified")
        }
        val frozen=FrozenPackageMeasurement(payload)
        if(confirmed)require(frozen.stockScale() is MeasureResult.Success)
        return frozen
    }
}
