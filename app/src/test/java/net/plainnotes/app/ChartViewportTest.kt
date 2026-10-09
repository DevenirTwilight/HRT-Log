package net.plainnotes.app

import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class ChartViewportTest {
    @Test fun wideUncertaintyDoesNotFlattenTheCentralCurveAndCanBeShownInFull() {
        val x=doubleArrayOf(0.0,1.0,2.0);val y=doubleArrayOf(100.0,200.0,100.0)
        val bands=doubleArrayOf(0.0,0.0,0.0) to doubleArrayOf(10_000.0,20_000.0,10_000.0)
        val central=ChartViewport.top(ChartData(x,y,band95=bands),0.0,2.0)
        val full=ChartViewport.top(ChartData(x,y,band95=bands,includeBandsInScale=true),0.0,2.0)
        assertTrue(200.0/central>0.75);assertTrue(full>=20_000.0)
        val labs=ChartViewport.top(ChartData(x,y,points=listOf(1.0 to 700.0,10.0 to 1e9)),0.0,2.0)
        assertTrue(labs>=700.0 && labs<1000.0)
    }
    @Test fun sparseWindowAndNowSplitUseInterpolatedEndpointsInTheSameUnits() {
        val points=ChartViewport.samples(doubleArrayOf(0.0,10.0),doubleArrayOf(100.0,200.0),2.0,8.0,5.0)
        assertEquals(listOf(2.0 to 120.0,5.0 to 150.0,8.0 to 180.0),points)
        val solid=points.filter{it.first<=5};val dashed=points.filter{it.first>=5}
        assertEquals(solid.last(),dashed.first())
        val data=ChartData(doubleArrayOf(0.0,10.0),doubleArrayOf(367.1,734.2))
        assertEquals(ChartViewport.top(ChartData(data.x,doubleArrayOf(100.0,200.0)),2.0,8.0)*3.671,ChartViewport.top(data,2.0,8.0),1e-8)
        assertTrue(ChartViewport.top(ChartData(doubleArrayOf(0.0),doubleArrayOf(Double.NaN)),0.0,1.0).isFinite())
    }
    @Test fun fullBandOnlyChangesScaleAcrossLongWindowsZoomAndFitBreaks() {
        for (days in listOf(7,14,60)) {
            val x=DoubleArray(days*24+1){it.toDouble()}
            val y=DoubleArray(x.size){100.0+50.0*kotlin.math.sin(it/6.0)}
            val p5=DoubleArray(x.size){y[it]*0.3};val p25=DoubleArray(x.size){y[it]*0.7}
            val p75=DoubleArray(x.size){y[it]*1.5};val p95=DoubleArray(x.size){y[it]*3.0}
            val before=listOf(x,y,p5,p25,p75,p95).map{it.copyOf()}
            val readAt:(Double,()->Unit)->Double?={at,cancel->cancel(); net.plainnotes.app.pk.Pk.interpolate(x,y,at)}
            fun data(full:Boolean)=ChartData(x,y,p25 to p75,p5 to p95,days*12.0,
                includeBandsInScale=full,breaks=doubleArrayOf(24.0,72.0),readAt=readAt)
            val central=data(false);val full=data(true)
            for ((start,end) in listOf(0.0 to days*24.0,23.5 to 25.5,73.25 to 76.75)) {
                val top=ChartViewport.top(central,start,end);val wide=ChartViewport.top(full,start,end)
                assertTrue(top.isFinite() && top>0);assertTrue(wide.isFinite() && wide>=top)
                assertEquals(ChartViewport.segmentSamples(x,y,start,end,central.splitX,central.breaks),
                    ChartViewport.segmentSamples(x,y,start,end,full.splitX,full.breaks))
                val query=(start+end)/2
                assertEquals(central.readAt!!.invoke(query) {},full.readAt!!.invoke(query) {})
                assertTrue(ChartViewport.segmentSamples(x,y,start,end,breaks=full.breaks).flatten().isNotEmpty())
            }
            listOf(x,y,p5,p25,p75,p95).zip(before).forEach{(actual,original)->assertArrayEquals(original,actual,0.0)}
        }
    }

}
