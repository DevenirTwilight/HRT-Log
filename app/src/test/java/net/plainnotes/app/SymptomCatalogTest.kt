package net.plainnotes.app

import net.plainnotes.app.symptoms.*
import org.junit.Assert.*
import org.junit.Test

class SymptomCatalogTest {
    private val c = SymptomCatalog.load()

    @Test fun translatedPassagesKeepOriginalsAndNeverDriveUrgency() {
        val originals=c.entries.flatMap{listOf(it.quote,it.action.text)}+c.monitoring.map{it.quote}+c.reporting.flatMap{it.quotes}
        originals.forEach{assertTrue(it,SourceTranslations.covers(it))}
        assertNull(SourceTranslations.translation(c.actions.getValue("FR_ANSM_CPA").text,java.util.Locale.FRENCH))
        assertTrue(SourceTranslations.translation(c.actions.getValue("FR_ANSM_CPA").text,java.util.Locale.SIMPLIFIED_CHINESE)!!.contains("医生"))
        assertFalse(c.actions.getValue("FR_ANSM_CPA").urgent)
    }

    @Test fun eachMedicationGetsItsOwnSources() {
        assertEquals(MedSymptoms.Listed(listOf("FR_PROVAMES"), false), c.forMedication(MedKey(1, "E2", "ORAL", "E2")))
        assertEquals(MedSymptoms.Listed(listOf("CN_BUJIALE", "FR_PROGYNOVA"), true), c.forMedication(MedKey(1, "E2", "SUBLINGUAL", "EV")))
        assertEquals(MedSymptoms.Listed(listOf("FR_OESTRODOSE", "FR_ESTREVA", "CN_AISITUO"), false), c.forMedication(MedKey(1, "E2", "GEL", "E2")))
        assertEquals(MedSymptoms.Listed(listOf("FR_ANDROCUR", "FR_ANSM_CPA", "TW_MOHW_2022_CPA"), false), c.forMedication(MedKey(1, "CPA", null, null)))
        assertEquals(MedSymptoms.NoneListedByOfficialSources, c.forMedication(MedKey(1, "SPI", null, null)))
        assertEquals(MedSymptoms.NoOfficialSource, c.forMedication(MedKey(1, "E2", "INJECTION", "EV")))
        assertEquals(MedSymptoms.NoOfficialSource, c.forMedication(MedKey(1, "P4", null, null)))
        assertEquals(MedSymptoms.NoOfficialSource, c.forMedication(MedKey(1, "OTHER", null, null)))
    }

    @Test fun sharedSymptomsAreShownOnceWithEverySourceAndUrgentOnesComeFirst() {
        val g = c.groupsFor(listOf(MedKey(1, "E2", "ORAL", "EV"), MedKey(2, "CPA", null, null)))
        assertEquals(g.map { it.id }.distinct(), g.map { it.id })
        val jaundice = g.single { it.id == "JAUNDICE" }
        assertEquals(setOf("CN_BUJIALE", "FR_PROGYNOVA", "FR_ANDROCUR"), jaundice.entries.map { it.source.id }.toSet())
        assertEquals(setOf(1L, 2L), jaundice.medicationIds)
        val firstNonUrgent = g.indexOfFirst { !it.urgent }
        assertTrue(firstNonUrgent == -1 || g.drop(firstNonUrgent).none { it.urgent })
        // Nausea only comes from the ANSM page, which says "consultez votre médecin" (not "immédiatement"): not urgent.
        assertFalse(g.single { it.id == "NAUSEA" }.urgent)
    }

    @Test fun urgencyFollowsTheSourceWordingOnly() {
        c.actions.values.forEach { assertEquals(it.id, it.text.contains("immédiatement") || it.text.contains("立即"), it.urgent) }
        assertFalse(c.actions.getValue("CN_AISITUO").urgent)          // "最好停止用药"
        assertFalse(c.actions.getValue("TW_MOHW_2022_CPA").urgent)     // "儘速回診"
    }

    @Test fun noEmergencyNumbersAndNoAddedWords() {
        val all = c.entries.flatMap { listOf(it.quote, it.action.text) } + c.monitoring.map { it.quote } + c.reporting.flatMap { it.quotes }
        listOf("120", "119", "999", "112", " 15 ", "SAMU", "urgences", "急诊", "急救").forEach { n -> assertTrue(n, all.none { it.contains(n) }) }
    }

    @Test fun countryRulesOnlyForThatRegion() {
        assertTrue(c.monitoringFor(null, setOf("SPI", "CPA", "E2")).isEmpty())
        assertTrue(c.monitoringFor("CN", setOf("SPI", "CPA", "E2")).isEmpty())
        assertEquals(setOf("FR_HAS_R32", "FR_HAS_R40"), c.monitoringFor("FR", setOf("SPI", "E2")).map { it.id }.toSet())
        assertEquals(setOf("FR_ANSM_IRM", "FR_ANSM_ATTESTATION", "FR_HAS_R40"), c.monitoringFor("FR", setOf("CPA")).map { it.id }.toSet())
        assertNull(c.reportingFor("HK")); assertNull(c.reportingFor(null)); assertNotNull(c.reportingFor("CN"))
    }

    @Test fun thirdPartySourcesNameTheirSites() {
        c.sources.values.filter { it.thirdParty }.forEach { s -> assertTrue(s.id, s.sites.size >= 2); assertTrue(s.sites.none { "tfsci" in it.url }) }
    }
}
