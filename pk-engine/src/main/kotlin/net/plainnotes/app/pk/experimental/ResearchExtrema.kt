package net.plainnotes.app.pk.experimental

import kotlin.math.ceil

/** Numerical model extrema, not a sampled chart maximum or a measured human Cmax. */
data class ResearchPeak(val hour: Double, val pgMl: Double)
object ResearchExtrema {
    const val timeToleranceH = 1e-7
    /** Bracket every local maximum on an explicit grid, refine each, then compare endpoints too.
     * All supported frozen SL shapes are smooth; 1-minute default is independently convergence-tested.
     * Flat/tied values deterministically retain the earliest point. No single unimodality assumption.
     */
    fun peaks(from: Double, to: Double, stepH: Double = 1.0/60, value: (Double)->Double): List<ResearchPeak> {
        require(from.isFinite() && to.isFinite() && to >= from && stepH.isFinite() && stepH > 0)
        val count=ceil((to-from)/stepH).toInt().coerceAtLeast(1)
        require(count <= 100_000)
        fun at(t:Double)=ResearchPeak(t,value(t).also { require(it.isFinite() && it>=0) })
        val grid=List(count+1) { at(from+(to-from)*it/count) }
        val result=mutableListOf(grid.first(),grid.last())
        for(i in 1 until count) if(grid[i].pgMl>grid[i-1].pgMl && grid[i].pgMl>=grid[i+1].pgMl) {
            var a=grid[i-1].hour;var b=grid[i+1].hour
            val ratio=(kotlin.math.sqrt(5.0)-1)/2
            var x=b-ratio*(b-a);var y=a+ratio*(b-a)
            var fx=at(x);var fy=at(y)
            repeat(80) {
                if(b-a>timeToleranceH) {
                    if(fx.pgMl<fy.pgMl) { a=x;x=y;fx=fy;y=a+ratio*(b-a);fy=at(y) }
                    else { b=y;y=x;fy=fx;x=b-ratio*(b-a);fx=at(x) }
                }
            }
            result += listOf(fx,fy,grid[i]).maxBy { it.pgMl }
        }
        return result
    }
    fun maximum(from:Double,to:Double,stepH:Double=1.0/60,value:(Double)->Double):ResearchPeak =
        peaks(from,to,stepH,value).maxWith(compareBy<ResearchPeak>{it.pgMl}.thenBy{-it.hour})
}
