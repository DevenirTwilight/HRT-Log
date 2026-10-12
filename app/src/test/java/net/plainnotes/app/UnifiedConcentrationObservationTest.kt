package net.plainnotes.app

import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.experimental.researchObservationPoints
import net.plainnotes.app.pk.experimental.ResearchDisplay
import org.junit.Assert.*
import org.junit.Test

class UnifiedConcentrationObservationTest {
    @Test fun realLabsAreUnitConvertedButNeverUsedAsCalibrationOrRelativeDots() {
        val pg=LabValueEntity(1,"E2",100.0,"pg/mL",3600000,"UTC")
        val pmol=LabValueEntity(2,"E2",367.1,"pmol/L",7200000,"UTC")
        val other=LabValueEntity(3,"T",100.0,"pg/mL",3600000,"UTC")
        val rows=listOf(pg,pmol,other,pg.copy(id=4,value=Double.NaN),pg.copy(id=5,value=-1.0),pg.copy(id=6,unit="ng/mL"))
        val points=researchObservationPoints(rows,ResearchDisplay.CONDITIONAL,false)
        assertEquals(listOf(1.0,2.0),points.map { it.first })
        points.forEach { assertEquals(100.0,it.second,1e-12);assertEquals(0.0,it.third,0.0) }
        val converted=researchObservationPoints(rows,ResearchDisplay.CONDITIONAL,true)
        assertEquals(367.1,converted[0].second,1e-9);assertEquals(367.1,converted[1].second,1e-9)
        assertTrue(researchObservationPoints(rows,ResearchDisplay.RELATIVE,false).isEmpty())
        assertTrue(researchObservationPoints(rows,ResearchDisplay.STUDY,false).isEmpty())
        assertTrue(researchObservationPoints(listOf(pg.copy(value=Double.MAX_VALUE)),ResearchDisplay.CONDITIONAL,true).isEmpty())
        assertTrue(researchObservationPoints(listOf(pg.copy(value=Double.MAX_VALUE)),ResearchDisplay.CONDITIONAL,false).isEmpty())
        assertEquals(100.0,pg.value,0.0);assertEquals(367.1,pmol.value,0.0)
    }
}
