package net.plainnotes.app.experimental

import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.experimental.SlAdapterAudit
import net.plainnotes.app.pk.Pk
import net.plainnotes.app.pk.experimental.*

/** Read-only Price group-amplitude mapping to qualified actual SL E2 history, never personal lab calibration. */
class ConcentrationPreview(val model: ResearchModel, val timeH: DoubleArray, val values: DoubleArray,
    val currentPgMl: Double?, val candidateId: String, private val exact: ((Double) -> Double)?,
    val anchor: ExperimentalStudyAnchor, val singleDosePeak: ResearchPeak?,
    /** Latest actual dose → +24 h, includes earlier residuals; not the isolated single-dose Tmax. */
    val historyWindowPeak: ResearchPeak?, val historyOriginH: Double?) {
    fun valueAt(hour: Double): Double? = exact?.invoke(hour)
    companion object {
        fun pending(model: ResearchModel) = ConcentrationPreview(model,doubleArrayOf(),doubleArrayOf(),null,
            ResearchDisplayProtocol.candidate(model).id,null,ResearchDisplayProtocol.anchor(model),null,null,null)
        fun compute(audit: SlAdapterAudit, model: ResearchModel, nowH: Double,
                    selected: ExperimentalCandidate = ResearchDisplayProtocol.candidate(model),
                    checkCancelled: () -> Unit = {}): ConcentrationPreview {
            require(nowH.isFinite())
            require(if(model==ResearchModel.M2) selected in ResearchSublingualV01.candidates
                else model==ResearchModel.M1 && selected in ResearchM1Catalog.candidates)
            if (audit.events.isEmpty()) return pending(model)
            // Defense in depth for callers bypassing the historical adapter. No oral/other-E2 contribution is relabelled SL.
            require(audit.events.all { it.route==net.plainnotes.app.pk.Route.SUBLINGUAL &&
                it.ester==net.plainnotes.app.pk.Ester.E2 && it.timeH.isFinite() && it.timeH<=nowH && it.doseMG.isFinite() && it.doseMG>0 })
            val doses = audit.events.map { ExperimentalDose(it.timeH, it.doseMG) }.sortedBy { it.atHour }
            val anchor=ResearchDisplayProtocol.anchor(model,selected)
            val prepared=PreparedResearchHistory(doses,selected)
            // Repeated extrema/graph queries reuse exactly equal timestamps, never interpolated values.
            // The cache is bounded during construction and released before the exact tap evaluator escapes.
            var constructionCache: MutableMap<Double, Double>? = HashMap()
            fun exact(hour: Double): Double {
                if(constructionCache != null) checkCancelled()
                constructionCache?.get(hour)?.let { return it }
                val value=prepared.studyAt(hour,anchor).also {
                    require(it.isFinite() && it>=0 && (it*Pk.PMOL_PER_PG).isFinite())
                }
                constructionCache?.let { if(it.size>=50_000) it.clear();it[hour]=value }
                return value
            }
            val single=ResearchExtrema.maximum(0.0,24.0) { ResearchSublingualV01.studyScenario(it,listOf(ExperimentalDose(0.0,1.0)),selected,anchor) }
            val from = nowH - ConcentrationCalculator.HISTORY_DAYS * 24.0
            val to = nowH + ConcentrationCalculator.FORECAST_DAYS * 24.0
            val times = sortedSetOf<Double>()
            for (i in 0..((to-from).toInt())) times += from + i
            times += nowH
            // Dose-local five-minute grid preserves curve shape; exact refined maxima prevent off-grid peak suppression.
            // After +8 h every supported individual frozen curve is decreasing (catalog conformance test).
            doses.forEachIndexed { i,dose ->
                val start=maxOf(from,dose.atHour)
                val end=minOf(to,dose.atHour+8.0,doses.getOrNull(i+1)?.atHour ?: to)
                if(end>=start) {
                    val count=kotlin.math.ceil((end-start)*12).toInt().coerceAtLeast(1)
                    for(j in 0..count) times += start+(end-start)*j/count
                    ResearchExtrema.peaks(start,end,stepH=1.0/12,value=::exact).forEach { times+=it.hour }
                }
                (dose.atHour+single.hour).takeIf { it in from..to }?.let {times+=it}
            }
            val origin=doses.last().atHour
            val peak=ResearchExtrema.maximum(origin,origin+24.0,value=::exact)
            times+=peak.hour
            val x=times.toDoubleArray()
            val values=DoubleArray(x.size){exact(x[it])};val current=exact(nowH)
            constructionCache=null
            return ConcentrationPreview(model,x,values,current,selected.id,::exact,
                anchor,single,peak,origin)
        }
    }
}
