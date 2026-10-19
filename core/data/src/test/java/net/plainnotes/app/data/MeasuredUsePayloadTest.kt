package net.plainnotes.app.data

import java.math.BigDecimal
import net.plainnotes.app.domain.measurement.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class MeasuredUsePayloadTest {
    private fun id(n:Int)="00000000-0000-4000-8000-"+n.toString().padStart(12,'0')
    private val clinical=RecordEntity(id=1,medication_id=1,taken_utc=1000,taken_zone=null,actual_dose=0.1,status="ON_TIME",origin="APP",revision=1,config_snapshot="{}")
    private fun use(n:Int,unit:MeasureUnit=MeasureUnit.TABLET,kind:QuantityKind=QuantityKind.USE_COUNT)=ConfirmedStockUse(id(n),id(n+10),"a".repeat(64),Quantity(BigDecimal("0.5"),unit,Meaning(kind,"SYNTHETIC source $n")),true)
    private fun request(lines:List<ConfirmedStockUse>)=MeasuredConsumptionRequest(id(100),id(101),MeasuredUsePayload.record(id(102),clinical),lines)
    private fun reject(block:()->Unit){try{block();fail("Invalid use accepted")}catch(_:IllegalArgumentException){}}
    private fun embeddedLegacy(text:String):Pair<String,MeasuredConsumptionRequest> = try {
        val root=JSONObject(text)
        val frozen=MeasuredUsePayload.read(ReferencePayload.canonical(root.getJSONObject("request")))
        frozen.sha256 to frozen.request()
    } catch(e:Exception){throw IllegalArgumentException("Invalid embedded request",e)}
    private fun validEmbedded():List<Pair<String,Pair<String,MeasuredConsumptionRequest>>> {
        val initial=request(listOf(use(1)))
        val values=listOf(initial,request(emptyList()),request((1..64).map{use(it)}),
            initial.copy(record=initial.record.copy(takenZone="Europe/Paris",status="LATE")),
            initial.copy(record=initial.record.copy(scheduleSha256="b".repeat(64))),
            initial.copy(record=initial.record.copy(revision=Int.MAX_VALUE,takenUtc=Long.MAX_VALUE)),
            initial.copy(stockEffect=MeasuredStockEffect.UNKNOWN),
            initial.copy(stockEffect=MeasuredStockEffect.ALREADY_ACCOUNTED),
            request(emptyList()).copy(stockEffect=MeasuredStockEffect.UNKNOWN,unlinkedActual=use(1).quantity),
            initial.copy(uses=listOf(use(1).copy(quantity=use(1).quantity.copy(meaning=Meaning(QuantityKind.USE_COUNT,"SYNTHÉTIQUE 雌二醇 µg 🧪")))))
        )
        return values.map { value ->
            val frozen=MeasuredUsePayload.freeze(value)
            JSONObject().put("request",JSONObject(frozen.json)).toString() to (frozen.sha256 to frozen.request())
        }
    }
    private fun invalidEmbedded():List<String> {
        val base=MeasuredUsePayload.freeze(request(listOf(use(1)))).json
        val edits=listOf<(JSONObject)->Unit>(
            {it.put("format",1.5)}, {it.put("format",Long.MAX_VALUE)}, {it.put("extra",true)},
            {it.put("stock_algorithm","future")}, {it.put("conversion_algorithm","future")},
            {it.put("dataset_uuid","not-an-id")}, {it.put("medication_uuid",JSONObject.NULL)},
            {it.getJSONObject("record").put("revision",0)}, {it.getJSONObject("record").put("revision",2147483648L)},
            {it.getJSONObject("record").put("revision","1")}, {it.getJSONObject("record").put("taken_utc",1.5)},
            {it.getJSONObject("record").put("taken_zone","not-a-zone")},
            {it.getJSONObject("record").put("status","MISSED")},
            {it.getJSONObject("record").put("legacy_actual_dose_bits","7ff8000000000000")},
            {it.getJSONObject("record").put("legacy_actual_dose_bits","7ff0000000000000")},
            {it.getJSONObject("record").put("legacy_actual_dose_bits","3FB999999999999A")},
            {it.getJSONObject("record").put("config_sha256","bad")},
            {it.getJSONArray("uses").getJSONObject(0).put("user_confirmed",false)},
            {it.getJSONArray("uses").getJSONObject(0).put("user_confirmed","true")},
            {it.getJSONArray("uses").getJSONObject(0).put("container_uuid","bad")},
            {it.getJSONArray("uses").getJSONObject(0).put("presentation_sha256","bad")},
            {it.getJSONArray("uses").getJSONObject(0).getJSONObject("quantity").put("value","0")},
            {it.getJSONArray("uses").put(JSONObject(it.getJSONArray("uses").getJSONObject(0).toString()))}
        )
        return edits.map { edit ->JSONObject().put("request",JSONObject(base).also(edit)).toString() }+
            listOf("{}","[]","{bad-json}")
    }
    @Test fun originalEmbeddedRequestPathKeepsTheWireCorpusAndExactHashes() {
        for((text,expected) in validEmbedded())assertEquals(expected,embeddedLegacy(text))
    }
    @Test fun originalEmbeddedRequestPathRejectsMalformedAndAmbiguousFacts() {
        for(text in invalidEmbedded())reject{embeddedLegacy(text)}
    }

    @Test fun embeddedDecoderMatchesTheOriginalStringOracleForEveryWireCase() {
        for((text,expected) in validEmbedded()) {
            val actual=MeasuredUsePayload.readEmbeddedRequest(text)
            assertEquals(embeddedLegacy(text),actual.sha256 to actual.request)
            assertEquals(expected,actual.sha256 to actual.request)
        }
    }
    @Test fun embeddedDecoderKeepsEveryMalformedWireRejection() {
        for(text in invalidEmbedded()) {
            reject{embeddedLegacy(text)}
            reject{MeasuredUsePayload.readEmbeddedRequest(text)}
        }
    }
    @Test fun embeddedTypedFactsCannotBeChangedThroughTheReturnedJsonOrUsesList() {
        val text=validEmbedded().first().first
        val actual=MeasuredUsePayload.readEmbeddedRequest(text);val hash=actual.sha256;val before=actual.request
        actual.envelope.getJSONObject("request").getJSONObject("record").put("revision",999)
        actual.envelope.getJSONObject("request").getJSONArray("uses").getJSONObject(0).getJSONObject("quantity").put("value","999")
        assertEquals(before,actual.request);assertEquals(BigDecimal("0.5"),actual.request.uses.single().quantity.value)
        assertEquals(1,actual.request.record.revision);assertEquals(hash,actual.sha256)
        assertThrows(UnsupportedOperationException::class.java){(actual.request.uses as MutableList<ConfirmedStockUse>).clear()}
        assertEquals(embeddedLegacy(text),actual.sha256 to actual.request)
    }

    @Test fun canonicalOrderIsDeterministicAndKeepsDifferentStockQuantitiesSeparate() {
        val first=MeasuredUsePayload.freeze(request(listOf(use(2,MeasureUnit.PUMP),use(1))))
        val second=MeasuredUsePayload.freeze(request(listOf(use(1),use(2,MeasureUnit.PUMP))))
        assertEquals(first.json,second.json);assertEquals(first.sha256,second.sha256)
        assertEquals(listOf(MeasureUnit.TABLET,MeasureUnit.PUMP),first.request().uses.map{it.quantity.unit})
        assertEquals(listOf(BigDecimal("0.5"),BigDecimal("0.5")),first.request().uses.map{it.quantity.value})
    }
    @Test fun legacyBinaryFactIsCapturedWithoutBecomingADecimalStockAmount() {
        val frozen=MeasuredUsePayload.freeze(request(listOf(use(1))))
        assertEquals("3fb999999999999a",frozen.request().record.legacyActualDoseBits)
        assertEquals(BigDecimal("0.5"),frozen.request().uses.single().quantity.value)
        assertNull(frozen.request().record.takenZone)
    }
    @Test fun absentActualSourceIsUnknownAndCannotBeReplacedWithZero() {
        assertTrue(MeasuredUsePayload.freeze(request(emptyList())).request().uses.isEmpty())
        reject{MeasuredUsePayload.freeze(request(listOf(use(1).copy(quantity=use(1).quantity.copy(value=BigDecimal.ZERO)))))}
    }
    @Test fun duplicatesUnconfirmedSourcesAndInvalidIdentityAreRejected() {
        reject{MeasuredUsePayload.freeze(request(listOf(use(1),use(1))))}
        reject{MeasuredUsePayload.freeze(request(listOf(use(1).copy(userConfirmed=false))))}
        reject{MeasuredUsePayload.freeze(request(listOf(use(1).copy(presentationSha256="bad"))))}
        reject{MeasuredUsePayload.freeze(request(listOf(use(1).copy(containerUuid="1"))))}
        reject{MeasuredUsePayload.freeze(request(emptyList()).copy(record=request(emptyList()).record.copy(revision=0)))}
    }
    @Test fun callerMutationCannotAlterFrozenInput() {
        val lines=mutableListOf(use(1));val frozen=MeasuredUsePayload.freeze(request(lines));val before=frozen.json;lines.clear()
        assertEquals(before,frozen.json);assertEquals(1,frozen.request().uses.size)
    }
    @Test fun decodedFrozenRequestHasNoMutableCollectionAliases() {
        val frozen=MeasuredUsePayload.freeze(request(listOf(use(1))))
        val before=frozen.json;val hash=frozen.sha256
        assertThrows(UnsupportedOperationException::class.java){(frozen.request().uses as MutableList<ConfirmedStockUse>).clear()}
        assertEquals(1,frozen.request().uses.size);assertEquals(before,frozen.json);assertEquals(hash,frozen.sha256)
    }
    @Test fun unsupportedAlgorithmsFloatsAndInvalidClinicalContextAreRejected() {
        val frozen=MeasuredUsePayload.freeze(request(listOf(use(1))))
        for(change in listOf<(JSONObject)->Unit>({it.put("stock_algorithm","future")},{it.put("unexpected",true)},{it.getJSONObject("record").put("legacy_actual_dose_bits","7ff8000000000000")},{it.getJSONObject("record").put("taken_utc",1.5)})) {
            val json=JSONObject(frozen.json);change(json)
            reject{MeasuredUsePayload.read(json.toString())}
        }
        reject{MeasuredUsePayload.record(id(102),clinical.copy(deleted_at_utc=1))}
        reject{MeasuredUsePayload.record(id(102),clinical.copy(status="MISSED")) .let{MeasuredUsePayload.freeze(request(emptyList()).copy(record=it))}}
    }
}
