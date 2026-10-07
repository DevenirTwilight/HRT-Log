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
}
