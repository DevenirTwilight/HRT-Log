package net.plainnotes.app.experimental

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.pk.experimental.*

/** Read-only research display. Never a production ConcentrationResult or a personal lab fit. */
class ConcentrationPreview(val model: ResearchModel, val timeH: DoubleArray, val values: DoubleArray,
    val currentPgMl: Double?, val candidateId: String, private val exact: ((Double) -> Double)?) {
    fun valueAt(hour: Double): Double? = exact?.invoke(hour)
    companion object {
        val assumptions = CommonCalibration(25.0, 100.0)
        fun pending(model: ResearchModel) = ConcentrationPreview(model, doubleArrayOf(), doubleArrayOf(), null, candidate(model).id, null)
        private fun candidate(model: ResearchModel) = when (model) {
            ResearchModel.M2 -> ResearchSublingualV01.candidates.first()
            ResearchModel.M1 -> ResearchM1Catalog.candidates.first()
            ResearchModel.LEGACY -> error("Legacy uses the unchanged production result")
        }
        fun compute(audit: SlAdapterAudit, model: ResearchModel, nowH: Double): ConcentrationPreview {
            require(nowH.isFinite())
            val selected = candidate(model)
            if (audit.events.isEmpty()) return pending(model)
            val doses = audit.events.map { ExperimentalDose(it.timeH, it.doseMG) }
            fun exact(hour: Double) = assumptions.concentration(ResearchSublingualV01.relativeHistory(hour, doses, selected))
            val from = nowH - ConcentrationCalculator.HISTORY_DAYS * 24.0
            val to = nowH + ConcentrationCalculator.FORECAST_DAYS * 24.0
            // Calendar grid plus dose-local points preserves short SL peaks across the original 7/14/60-day view.
            val times = sortedSetOf<Double>()
            for (i in 0..((to-from).toInt())) times += from + i
            times += nowH
            val offsets = listOf(0.0, 1.0/60, .05, .1, .2, .35, .5, .75, 1.0, 1.5, 2.0, 3.0, 4.0, 8.0, 12.0, 24.0)
            doses.forEach { dose -> offsets.forEach { offset -> (dose.atHour+offset).takeIf { it in from..to }?.let { times += it } } }
            val x=times.toDoubleArray()
            return ConcentrationPreview(model,x,DoubleArray(x.size){exact(x[it])},exact(nowH),selected.id,::exact)
        }
    }
}
