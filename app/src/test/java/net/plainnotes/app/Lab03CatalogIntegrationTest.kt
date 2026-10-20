package net.plainnotes.app

import net.plainnotes.app.ui.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.data.LabValueEntity
import net.plainnotes.app.conc.ConcentrationCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Lab03CatalogIntegrationTest {
    @Test fun everyLegacyFactorRetainsItsExactDoubleResult() {
        val frozen=mapOf("E2" to listOf("pg/mL" to 1.0,"pmol/L" to 3.671),"T" to listOf("ng/dL" to 1.0,"nmol/L" to 0.03467),
            "P4" to listOf("ng/mL" to 1.0,"nmol/L" to 3.18),"P4_IA" to listOf("ng/mL" to 1.0,"nmol/L" to 3.18),"P4_MS" to listOf("ng/mL" to 1.0,"nmol/L" to 3.18),
            "PRL" to listOf("ng/mL" to 1.0,"mIU/L" to 21.2),"LH" to listOf("IU/L" to 1.0),"FSH" to listOf("IU/L" to 1.0),"SHBG" to listOf("nmol/L" to 1.0),
            "ALT" to listOf("U/L" to 1.0),"AST" to listOf("U/L" to 1.0),"GGT" to listOf("U/L" to 1.0),"CREA" to listOf("µmol/L" to 1.0,"mg/dL" to 1/88.42),"K" to listOf("mmol/L" to 1.0))
        for((code,units) in frozen) {
            assertEquals(units,analyte(code).units)
            for((unit,factor) in units)for(value in listOf(0.125,1.0,9.0,210.0,100000.0))
                assertEquals("Legacy $code/$unit",java.lang.Double.doubleToRawLongBits(value/factor),java.lang.Double.doubleToRawLongBits(toCanonical(analyte(code),value,unit)!!))
        }
    }
    @Test fun expandedAdaptersKeepRawIdentityAndUseAffineOrAnalyteSpecificConversions() {
        assertEquals(45,ANALYTES.size);assertEquals(45,ANALYTES.map{it.code}.toSet().size)
        assertEquals(305.0,toCanonical(analyte("TG"),3.05,"g/L")!!,1e-12)
        assertEquals(95.0,toCanonical(analyte("GLU_FASTING"),0.95,"g/L")!!,1e-12)
        assertEquals(4.43,toCanonical(analyte("RBC"),4.43,"millions/mm³")!!,1e-12)
        assertEquals(224.0,toCanonical(analyte("PLT"),224.0,"× 10³/mm³")!!,1e-12)
        val ngsp=6.0;val ifcc=(ngsp-2.152)/0.09148
        assertEquals(ifcc,toCanonical(analyte("HBA1C"),ngsp,"%")!!,1e-10)
        assertEquals(ngsp,convertLabUnit("HBA1C",ifcc,"mmol/mol","%")!!,1e-10)
        assertEquals(0.4,toCanonical(analyte("LH"),0.4,"mIU/mL")!!,0.0)
        assertNull(toCanonical(analyte("TG"),1.0,"mL"));assertNull(toCanonical(analyte("NEUT_ABS"),1.0,"%"))
        assertNull(toCanonical(analyte("SYNTHETIC_UNKNOWN"),1.0,"opaque-unit"))
        assertEquals(4,LabPreset.LIPIDS.codes.size);assertEquals(18,LabPreset.CBC.codes.size)
    }
    @Test fun searchKeepsCodeIdentityAccentsAndCategoryBoundaries() {
        val choices=listOf("EOS_ABS" to "Éosinophiles absolus","EOS_PCT" to "Éosinophiles pourcentage","TC" to "Cholestérol total","GLU_FASTING" to "Glycémie à jeun")
        assertEquals(listOf("EOS_ABS","EOS_PCT"),filterLabChoices(choices,"EOSINOPHILES",LabCategory.CBC).map{it.first})
        assertEquals(listOf("GLU_FASTING"),filterLabChoices(choices,"glycemie",null).map{it.first})
        assertTrue(filterLabChoices(choices,"EOS",LabCategory.LIPIDS).isEmpty())
        assertEquals(listOf("EOS_ABS"),filterLabChoices(choices,"EOS_ABS",null).map{it.first})
    }
    @Test fun nonHormonalAndCustomCodesCannotEnterE2PointsOrCalibration() {
        val now=Instant.parse("2026-03-29T12:00:00Z")
        fun row(code:String,unit:String,value:Double)=LabValueEntity(analyte_code=code,value=value,unit=unit,sampled_utc=now.minusSeconds(3600).toEpochMilli(),sampled_zone="Europe/Paris")
        val original=listOf(row("E2","pg/mL",210.0))
        val extra=LabCatalog.all.filter{it.code!="E2"}.map{row(it.code,it.canonicalUnit,12.5)}+row("SYNTHETIC_CUSTOM_E2","pg/mL",999.0)
        fun compute(rows:List<LabValueEntity>)=ConcentrationCalculator.compute(emptyList(),emptyMap(),emptyList(),emptyList(),rows,null,now)
        val before=compute(original);val after=compute(original+extra)
        assertEquals(1,before.labs.size);assertEquals(before.labs,after.labs)
        assertEquals(before.labEligibility,after.labEligibility);assertEquals(before.calibration,after.calibration)
        assertArrayEquals(before.e2,after.e2,0.0)
    }
    @Test fun unchangedSamplePreservesTheOriginalInstantAndZoneAcrossDstOverlap() {
        val zone=java.time.ZoneId.of("Europe/Paris")
        val late=Instant.parse("2026-10-25T01:30:00Z").toEpochMilli()
        val local=java.time.LocalDateTime.of(2026,10,25,2,30)
        assertEquals(LabSampleEdit(late,"Europe/Paris"),labSampleForEdit(late,"Europe/Paris",local,zone))
        assertEquals(LabSampleEdit(late,"Asia/Tokyo"),labSampleForEdit(late,"Asia/Tokyo",local,zone))
        val changed=labSampleForEdit(late,"Asia/Tokyo",local.plusHours(2),zone)
        assertEquals("Europe/Paris",changed.zone)
        assertEquals(Instant.parse("2026-10-25T03:30:00Z").toEpochMilli(),changed.utc)
    }
}
