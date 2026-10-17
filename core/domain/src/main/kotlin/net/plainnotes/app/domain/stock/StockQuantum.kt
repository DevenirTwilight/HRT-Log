package net.plainnotes.app.domain.stock

import java.math.BigDecimal
import net.plainnotes.app.domain.measurement.*

enum class StockCapacityBasis { USER_COUNTED, USER_DECLARED, LABEL_NOMINAL, UNKNOWN }

/** An explicitly confirmed physical capacity expressed in a frozen minimum quantum.
 * This is an arithmetic boundary, not a ledger, source verifier or stock allocation policy.
 * Only a future, separately validated new-container mode may use its integer projection.
 * It never interprets legacy Double values, nominal packaging, dose or absorbed exposure.
 */
class StockQuantum private constructor(
    val capacity:Quantity,
    val quantum:Quantity,
    val ref:PresentationRef,
    val capacityQuanta:Long,
    val capacityBasis:StockCapacityBasis,
) {
    companion object {
        const val ALGORITHM="ansm03-stock-quanta-v1"
        const val MAX_QUANTA=9007199254740991L
        private val max=BigDecimal.valueOf(MAX_QUANTA)
        private fun failure(error:MeasureError)=MeasureResult.Failure(error)
        private fun positive(q:Quantity):MeasureError? = Measurements.error(q)
            ?: if(q.value.signum()==0)MeasureError.INVALID_NUMBER else null
        private fun source(actual:ActualPackage?):MeasureError? = when {
            actual==null -> MeasureError.MISSING_HISTORICAL_SNAPSHOT
            !actual.userConfirmed || !Measurements.refValid(actual.ref) -> MeasureError.INSUFFICIENT_SOURCE
            else -> null
        }
        private fun same(amount:Quantity,unit:Quantity):MeasureResult<Quantity> =
            Measurements.add(unit.copy(value=BigDecimal.ZERO),amount)
        private fun count(amount:BigDecimal,quantum:BigDecimal):MeasureResult<Long> {
            val n=try {amount.divide(quantum)}catch(_:ArithmeticException){return failure(MeasureError.PRECISION_OR_RANGE)}
            if(n>max)return failure(MeasureError.PRECISION_OR_RANGE)
            return try {MeasureResult.Success(n.longValueExact(),algorithm=ALGORITHM)}
            catch(_:ArithmeticException){failure(MeasureError.PRECISION_OR_RANGE)}
        }
        /** The capacity must be an actual user-confirmed amount, never a nominal-label fallback. */
        fun create(confirmedCapacity:Quantity,quantum:Quantity,actual:ActualPackage?,basis:StockCapacityBasis):MeasureResult<StockQuantum> {
            source(actual)?.let{return failure(it)}
            if(basis in setOf(StockCapacityBasis.LABEL_NOMINAL,StockCapacityBasis.UNKNOWN))return failure(MeasureError.UNKNOWN_CAPACITY)
            positive(confirmedCapacity)?.let{return failure(it)}
            positive(quantum)?.let{return failure(it)}
            if(confirmedCapacity.meaning.kind in setOf(QuantityKind.ABSORBED_MASS,QuantityKind.RELEASE_RATE) ||
                quantum.meaning.kind in setOf(QuantityKind.ABSORBED_MASS,QuantityKind.RELEASE_RATE))
                return failure(MeasureError.INCOMPATIBLE_UNIT)
            val normalized=same(confirmedCapacity,quantum)
            if(normalized is MeasureResult.Failure)return normalized
            val quanta=count((normalized as MeasureResult.Success).value.value,quantum.value)
            if(quanta is MeasureResult.Failure)return quanta
            // Normalize numbers for deterministic persistence; retain the explicitly selected display units.
            val capacity=confirmedCapacity.copy(value=confirmedCapacity.value.stripTrailingZeros())
            val frozenQuantum=quantum.copy(value=quantum.value.stripTrailingZeros())
            return MeasureResult.Success(StockQuantum(capacity,frozenQuantum,actual!!.ref,(quanta as MeasureResult.Success).value,basis),algorithm=ALGORITHM)
        }
    }
    /** Requires confirmation of the same frozen source on every actual use; no cross-strength pooling. */
    fun toQuanta(actualAmount:Quantity,actual:ActualPackage?):MeasureResult<Long> {
        source(actual)?.let{return failure(it)}
        Measurements.compareRefs(ref,actual!!.ref)?.let{return failure(it)}
        positive(actualAmount)?.let{return failure(it)}
        val normalized=same(actualAmount,quantum)
        if(normalized is MeasureResult.Failure)return normalized
        val n=count((normalized as MeasureResult.Success).value.value,quantum.value)
        if(n is MeasureResult.Failure)return n
        return if((n as MeasureResult.Success).value>capacityQuanta)failure(MeasureError.INSUFFICIENT_STOCK)else n
    }
    /** Exact restore-cache reduction for validated integer-mode entries. This is not a history validator.
     * Arbitrary CONSUME/REVERSE cancellation order can exceed 2^53 or Long temporarily, even when
     * the final used count is small. Never use SQL SUM(Double) for this new mode.
     */
    fun restoredUsed(initialQuanta:Long,signedDeltas:List<Long>):MeasureResult<Long> {
        if(signedDeltas.size>100000)return failure(MeasureError.PRECISION_OR_RANGE)
        if(initialQuanta<0 || initialQuanta>capacityQuanta)return failure(MeasureError.INVALID_NUMBER)
        var sum=BigDecimal.valueOf(initialQuanta)
        for(delta in signedDeltas) {
            if(delta==0L || delta < -MAX_QUANTA || delta>MAX_QUANTA)return failure(MeasureError.INVALID_NUMBER)
            sum=sum.add(BigDecimal.valueOf(delta))
        }
        if(sum.signum()<0 || sum>BigDecimal.valueOf(capacityQuanta))return failure(MeasureError.INSUFFICIENT_STOCK)
        return MeasureResult.Success(sum.longValueExact(),algorithm=ALGORITHM)
    }
    /** Used count must come from the new integer-mode ledger, never rounded from an old scalar. */
    fun remaining(usedQuanta:Long):MeasureResult<Quantity> {
        if(usedQuanta<0)return failure(MeasureError.INVALID_NUMBER)
        if(usedQuanta>capacityQuanta)return failure(MeasureError.INSUFFICIENT_STOCK)
        val amount=quantum.copy(value=quantum.value.multiply(BigDecimal.valueOf(capacityQuanta-usedQuanta)))
        return when(val converted=Measurements.inUnit(amount,capacity.unit)) {
            is MeasureResult.Failure -> converted
            is MeasureResult.Success -> MeasureResult.Success(converted.value,algorithm=ALGORITHM)
        }
    }
}
