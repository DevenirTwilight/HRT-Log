package net.plainnotes.app

import net.plainnotes.app.conc.*
import net.plainnotes.app.data.*
import net.plainnotes.app.symptoms.*
import net.plainnotes.app.ui.adherence
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HistoricalContextTest {
    private val now=Instant.parse("2026-03-10T12:00:00Z")
    private val med=MedicationEntity(1,"Synthetic original","E2","ORAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private fun record(snapshot:String)=RecordEntity(1,1,taken_utc=now.minusSeconds(3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=snapshot)

    @Test fun changingCurrentRouteOrMoleculeDoesNotChangePastCurve() {
        val p=ProfileEntity(1,"EV","oral")
        val records=listOf(record(MedicationSnapshot.encode(med,p)))
        val original=ConcentrationCalculator.compute(listOf(med),mapOf(1L to p),records,emptyList(),emptyList(),null,now)
        val changed=ConcentrationCalculator.compute(listOf(med.copy(molecule="T",route="GEL")),mapOf(1L to ProfileEntity(1,"E2","gel",gel_product_id=2)),records,emptyList(),emptyList(),null,now)
        assertNotNull(original.currentPgMl)
        assertArrayEquals(original.e2,changed.e2,0.0)
        assertEquals(original.models,changed.models)
    }

    @Test fun legacyMissingGelParametersAreNotTakenFromCurrentProfile() {
        val old="{\"name\":\"Synthetic\",\"molecule\":\"E2\",\"unit\":\"MG\",\"route\":\"GEL\",\"ester\":\"E2\"}"
        val r=ConcentrationCalculator.compute(listOf(med.copy(route="GEL")),mapOf(1L to ProfileEntity(1,"E2","gel",gel_product_id=1)),listOf(record(old)),emptyList(),emptyList(),null,now)
        assertNull(r.currentPgMl);assertEquals(1,r.skippedDoses)
        assertTrue(r.missing.any{it.input==MissingInput.GEL_PRODUCT})
    }

    @Test fun onlyMatchingSourcesAreSnapshottedAndRemainReadableWithoutLiveCatalog() {
        val catalog=SymptomCatalog.load()
        val meds=listOf(med.copy(route="ORAL"))
        val profiles=mapOf(1L to ProfileEntity(1,"EV","oral"))
        val g=catalog.groupsFor(listOf(MedKey(1,"E2","ORAL","EV"))).first()
        val encoded=catalog.snapshot(g.id,meds,profiles)!!
        val observation=SymptomCheckEntity("2026-03-10",g.id,context_snapshot=encoded)
        val saved=SymptomCatalog.saved(observation)!!
        assertEquals(g.entries.map{it.source.id}.toSet(),saved.entries.map{it.source.id}.toSet())
        assertEquals(g.entries.map{it.quote},saved.entries.map{it.quote})
        assertEquals(listOf("Synthetic original"),SymptomCatalog.savedMedicationNames(observation))
    }

    @Test fun unconfirmedDoesNotBecomeMissedOrCompleteAdherenceRate() {
        val auto=RecordEntity(2,1,scheduled_utc=now.toEpochMilli(),scheduled_zone="UTC",status="MISSED",origin="AUTO_MISSED",revision=1,config_snapshot="{}")
        val taken=record("{}").copy(scheduled_utc=now.minusSeconds(3600).toEpochMilli(),scheduled_zone="UTC")
        val a=adherence(listOf(taken,auto));assertEquals(0,a.missed);assertEquals(1,a.unconfirmed);assertNull(a.onTimeRate)
        val confirmed=adherence(listOf(taken,auto.copy(origin="APP")));assertEquals(1,confirmed.missed);assertEquals(0,confirmed.unconfirmed)
    }

    @Test fun importedAntiandrogenWithoutRouteDoesNotInheritOralModel() {
        val current=med.copy(molecule="CPA",route="ORAL")
        val imported=record(MedicationSnapshot.encode(current.copy(route=null),null)).copy(origin="IMPORT_TM")
        val r=ConcentrationCalculator.compute(listOf(current),emptyMap(),listOf(imported),emptyList(),emptyList(),60.0,now)
        assertTrue(r.others.isEmpty());assertEquals(0,r.usedDoses);assertEquals(1,r.skippedDoses)
        assertTrue(r.missing.any{it.input==MissingInput.HISTORICAL_CONTEXT})
    }
    @Test fun legacySourceOnlyRecordUsesExplicitlyLinkedCompatibleRuleWithoutUsingCurrentProfile() {
        val p=ProfileEntity(1,"EV","oral");val old=record("{\"source\":\"hrttracker\"}").copy(rule_version_id=10,origin="APP")
        val known=MedicationSnapshot.encode(med,p)
        val result=ConcentrationCalculator.compute(listOf(med.copy(route="GEL")),mapOf(1L to ProfileEntity(1,"E2","gel",gel_product_id=1)),listOf(old),emptyList(),emptyList(),null,now,plannedSnapshots=mapOf(10L to known))
        assertNotNull(result.currentPgMl);assertEquals(1,result.usedDoses);assertFalse(result.missing.any{it.input==MissingInput.HISTORICAL_CONTEXT})
        val imported=ConcentrationCalculator.compute(listOf(med),mapOf(1L to p),listOf(old.copy(origin="IMPORT_HT")),emptyList(),emptyList(),null,now,plannedSnapshots=mapOf(10L to known))
        assertNull(imported.currentPgMl);assertTrue(imported.missing.any{it.input==MissingInput.HISTORICAL_CONTEXT})
        val conflict=old.copy(config_snapshot="{\"molecule\":\"E2\",\"route\":\"GEL\",\"unit\":\"MG\",\"ester\":\"E2\"}")
        val rejected=ConcentrationCalculator.compute(listOf(med),mapOf(1L to p),listOf(conflict),emptyList(),emptyList(),null,now,plannedSnapshots=mapOf(10L to known))
        assertNull(rejected.currentPgMl);assertEquals(1,rejected.skippedDoses)
    }

}
