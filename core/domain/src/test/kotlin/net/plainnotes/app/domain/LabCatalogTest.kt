package net.plainnotes.app.domain

import java.math.BigDecimal
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LabCatalogTest {
    private fun converted(code: String, value: String, from: String, to: String): BigDecimal =
        (LabCatalog.convert(code, value, from, to) as LabConversionResult.Success).value
    private fun equal(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual), actual.toPlainString())
    private fun near(expected: BigDecimal, actual: BigDecimal) = assertTrue(expected.subtract(actual).abs() < BigDecimal("1E-25"), "$expected != $actual")
    private fun failure(reason: LabConversionFailure, code: String, value: String, from: String, to: String) =
        assertEquals(LabConversionResult.Failure(reason), LabCatalog.convert(code, value, from, to))

    @Test fun catalogueHasUniqueCodesAndUnitsWithCanonicalFirst() {
        assertEquals(45, LabCatalog.all.size)
        assertEquals(LabCatalog.all.size, LabCatalog.all.map { it.code }.toSet().size)
        LabCatalog.all.forEach { assertEquals(it.canonicalUnit, it.units.first()); assertEquals(it.units.size, it.units.toSet().size) }
        assertEquals(LabCategory.OTHER, LabCatalog.categoryFor("external-custom"))
        assertNull(LabCatalog.find("e2")) // identifiers are stable, not locale-dependent text
    }

    @Test fun frozenLegacyFactorsAndAssayIdentitiesRemainSeparate() {
        equal("770.91", converted("E2", "210", "pg/mL", "pmol/L")) // report rounds to771
        equal("0.31203", converted("T", "9", "ng/dL", "nmol/L"))
        // An independently rounded printed0 must never replace the primary9ng/dL result.
        equal("9", converted("T", "9", "ng/dL", "ng/dL"))
        equal("0", converted("T", "0", "nmol/L", "ng/dL"))
        listOf("P4", "P4_IA", "P4_MS").forEach { equal("3.18", converted(it,"1","ng/mL","nmol/L")) }
        assertEquals(3, LabCatalog.all.count { it.code in setOf("P4", "P4_IA", "P4_MS") })
        equal("21.2", converted("PRL", "1", "ng/mL", "mIU/L"))
        equal("0.4", converted("LH", "0.4", "mIU/mL", "IU/L"))
        equal("0.2", converted("FSH", "0.2", "mIU/mL", "IU/L"))
    }

    @Test fun syntheticFrenchMassVolumeAndCellCounts() {
        equal("305", converted("TG", "3.05", "g/L", "mg/dL"))
        equal("95", converted("GLU_FASTING", "0.95", "g/L", "mg/dL"))
        equal("95", converted("GLU_RANDOM", "0.95", "g/L", "mg/dL"))
        equal("138", converted("HB", "13.8", "g/dL", "g/L"))
        equal("0.401", converted("HCT", "40.1", "%", "L/L"))
        equal("4.43", converted("RBC", "4.43", "millions/mm³", "10¹²/L"))
        equal("224", converted("PLT", "224", "× 10³/mm³", "10⁹/L"))
        equal("54", converted("ALT", "54", "U/L", "IU/L"))
        equal("29", converted("AST", "29", "U/L", "U/L"))
    }

    @Test fun lipidAndGlucoseMolarFactorsAreAnalyteSpecific() {
        listOf("TC", "HDL_C", "LDL_C").forEach { equal("2.59", converted(it,"100","mg/dL","mmol/L")) }
        equal("1.13", converted("TG","100","mg/dL","mmol/L"))
        equal("5.551", converted("GLU_FASTING","100","mg/dL","mmol/L"))
        assertNotEquals(converted("TC","100","mg/dL","mmol/L"), converted("TG","100","mg/dL","mmol/L"))
    }

    @Test fun hbA1cUsesAffineMasterEquationIncludingOneSidedBounds() {
        equal("6.54304", converted("HBA1C","48","mmol/mol","%"))
        equal("48", converted("HBA1C","6.54304","%","mmol/mol"))
        // Callers may convert each existing lower OR upper bound without inventing the missing bound.
        equal("5.99416", converted("HBA1C","42","mmol/mol","%"))
        equal("7.27488", converted("HBA1C","56","mmol/mol","%"))
        equal("2.152", converted("HBA1C","0","mmol/mol","%"))
        failure(LabConversionFailure.OUT_OF_RANGE,"HBA1C","1","%","mmol/mol")
    }

    @Test fun incompatibleAndUnknownUnitsCannotBeGuessed() {
        failure(LabConversionFailure.UNKNOWN_ANALYTE,"unlisted","1","g/L","mg/dL")
        failure(LabConversionFailure.UNKNOWN_UNIT,"TG","1","mg","mg/dL")
        failure(LabConversionFailure.UNKNOWN_UNIT,"NEUT_ABS","3","10⁹/L","%")
        failure(LabConversionFailure.UNKNOWN_UNIT,"NEUT_PCT","30","%","10⁹/L")
        failure(LabConversionFailure.UNKNOWN_UNIT,"RBC","4.43","10³/mm³","10¹²/L")
        failure(LabConversionFailure.UNKNOWN_UNIT,"EGFR","90","mL/min","mL/min/1.73m²")
        failure(LabConversionFailure.UNKNOWN_UNIT,"UREA","5","mg/dL","mmol/L")
        failure(LabConversionFailure.UNKNOWN_UNIT,"E2","210","PG/ML","pg/mL")
    }

    @Test fun unsafeNumbersAreStructuredFailuresAndZeroIsNotMissing() {
        listOf("NaN","Infinity","1,25","","<31").forEach { failure(LabConversionFailure.INVALID_NUMBER,"TG",it,"g/L","mg/dL") }
        failure(LabConversionFailure.INVALID_NUMBER,"TG","-1","g/L","mg/dL")
        listOf("1E1000000000","1E-1000000000","9".repeat(65),"1".repeat(257)).forEach {
            failure(LabConversionFailure.OUT_OF_RANGE,"TG",it,"g/L","mg/dL")
        }
        failure(LabConversionFailure.OUT_OF_RANGE,"TG","1E30","g/L","mg/dL")
        equal("0", converted("TG","0","g/L","mg/dL"))
    }

    @Test fun allUnitPairsRoundTripWithinDecimal128Precision() {
        val random = Random(0x4C4142)
        LabCatalog.all.forEach { a -> repeat(25) {
            val canonical = BigDecimal(random.nextInt(1, 100000)).movePointLeft(3)
            a.units.forEach { unit ->
                val outward = (a.fromCanonical(canonical,unit) as LabConversionResult.Success).value
                val restored = (a.toCanonical(outward,unit) as LabConversionResult.Success).value
                near(canonical,restored)
            }
        } }
    }

    @Test fun repeatedExactDecimalConversionsDoNotDrift() {
        var value = BigDecimal("3.050000000000000000001")
        repeat(1000) {
            value = converted("TG", value.toPlainString(), "g/L", "mg/dL")
            value = converted("TG", value.toPlainString(), "mg/dL", "g/L")
        }
        equal("3.050000000000000000001",value)
        val input = BigDecimal("13.800000000000000000000000000000000000001")
        assertEquals(input, (LabCatalog.find("HB")!!.convert(input,"g/dL","g/dL") as LabConversionResult.Success).value)
    }
}
