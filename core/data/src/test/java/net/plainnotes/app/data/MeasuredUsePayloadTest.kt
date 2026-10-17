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
