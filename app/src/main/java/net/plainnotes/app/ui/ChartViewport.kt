package net.plainnotes.app.ui

import net.plainnotes.app.pk.Pk
import kotlin.math.*

/** Display geometry only. It never changes the simulated curve or probability bounds. */
object ChartViewport {
    fun samples(x:DoubleArray,y:DoubleArray,start:Double,end:Double,split:Double?=null):List<Pair<Double,Double>> {
        if(x.isEmpty() || x.size!=y.size || !start.isFinite() || !end.isFinite() || end<=start)return emptyList()
        val points=x.indices.filter{x[it] in start..end && x[it].isFinite() && y[it].isFinite()}.map{x[it] to y[it]}
        val cuts=listOfNotNull(start,end,split?.takeIf{it in start..end}).distinct().mapNotNull{at->
            if(at<x.first() || at>x.last())null else Pk.interpolate(x,y,at)?.takeIf{it.isFinite()}?.let{at to it}
        }
        return (points+cuts).distinctBy{it.first}.sortedBy{it.first}
    }
    fun top(data:ChartData,start:Double,end:Double):Double {
        val curve=samples(data.x,data.y,start,end).maxOfOrNull{it.second} ?: 0.0
        val upper=if(data.includeBandsInScale)data.band95?.let{samples(data.x,it.second,start,end).maxOfOrNull{p->p.second}} ?: 0.0 else 0.0
        val dots=data.points.filter{it.first in start..end && it.second.isFinite()}.maxOfOrNull{it.second} ?: 0.0
        val ref=data.range?.second?.takeIf{it.isFinite()} ?: 0.0
        val peak=maxOf(curve,upper,dots,ref)
        return if(peak>0)peak*1.15 else 1.0
    }
}
