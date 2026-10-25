package net.plainnotes.app.importer

import org.json.JSONObject
import org.json.JSONArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FeatherlineTest {
    private val med="00000000-0000-4000-8000-000000000001"
    private val log="00000000-0000-4000-8000-000000000002"
    private val panel="00000000-0000-4000-8000-000000000003"
    private val result="00000000-0000-4000-8000-000000000004"
    private fun fixture():JSONObject=JSONObject().put("snapshotVersion",7).put("exportedAtEpochMillis",1792929600000L)
        .put("app",JSONObject().put("packageName","com.mkx.hrttracker")).put("settings",JSONObject()).put("userProfile",JSONObject())
        .put("medicines",JSONArray().put(JSONObject().put("uuid",med).put("selectionKind","CATALOG").put("medicationKey","ESTRADIOL_VALERATE")
            .put("category","ESTRADIOL").put("preparationType","PILL").put("strengthMgPerTablet",2).put("displayDoseUnit","MG")))
        .put("medicationGroups",JSONArray()).put("customBloodAnalytes",JSONArray())
        .put("medicationLogs",JSONArray().put(JSONObject().put("uuid",log).put("category","ESTRADIOL").put("medicineUuid",med).put("applicationType","ORAL")
            .put("doseInstructionKind","TABLET_FRACTION").put("tabletFractionNumerator",1).put("tabletFractionDenominator",2)
            .put("count",1).put("appliedAtEpochMillis",1792888200000L).put("appliedAtTimeZoneId","Europe/Paris").put("equivalentE2Mg",999)))
        .put("bloodTestPanels",JSONArray().put(JSONObject().put("uuid",panel).put("collectedAtInstantEpochMillis",1792888200000L)
            .put("collectedAtTimeZoneId","Europe/Paris").put("notes","Synthetic only").put("results",JSONArray().put(JSONObject().put("uuid",result)
                .put("builtinAnalyteKey","e2").put("value",210).put("unitSnapshot","pg_ml").put("canonicalValue",999)))))
    private fun read(r:JSONObject=fixture())=Featherline.read(r.toString())
    @Test fun actualMassIsIndependentOfPkEquivalentAndOriginalLabIndependentOfCanonical() {
        val e=read();assertEquals(1.0,e.doses.single().amount);assertEquals("EV",e.doses.single().medicine.ester)
        assertEquals(210.0,e.labs.single().value);assertEquals("pg/mL",e.labs.single().unit)
        assertEquals("Europe/Paris",e.doses.single().zone);assertEquals(e.doses.single().at,e.labs.single().at)
        assertTrue(e.doses.single().sourceJson.contains("equivalentE2Mg"))
    }
    @Test fun unsupportedReleaseRateCustomAndActualDeltaNeverBecomeAssumedDoses() {
        for((key,value) in listOf("applicationType" to "PATCH_ON","doseAmountDelta" to 1)) {
            val r=fixture();r.getJSONArray("medicationLogs").getJSONObject(0).put(key,value)
            assertTrue(read(r).doses.isEmpty());assertEquals(1,read(r).issues.size)
        }
        val r=fixture();r.getJSONArray("medicines").getJSONObject(0).put("selectionKind","CUSTOM")
        assertTrue(read(r).doses.isEmpty())
    }
    @Test fun unknownUnitIsReportedWithoutSubstitutingCanonical() {
        val r=fixture();r.getJSONArray("bloodTestPanels").getJSONObject(0).getJSONArray("results").getJSONObject(0).put("unitSnapshot","mystery")
        val e=read(r);assertTrue(e.labs.isEmpty());assertEquals("UNSUPPORTED_RESULT",e.issues.single().reason)
    }
    @Test fun invalidVersionAppUuidOrOrphanRefuses() {
        assertThrows(Featherline.Unsupported::class.java){read(fixture().put("snapshotVersion",6))}
        assertThrows(Featherline.Invalid::class.java){read(fixture().put("app",JSONObject().put("packageName","other")))}
        val r=fixture();r.getJSONArray("medicines").getJSONObject(0).put("uuid","bad")
        assertThrows(Featherline.Invalid::class.java){read(r)}
        val orphan=fixture().put("medicines",JSONArray());assertThrows(Featherline.Invalid::class.java){read(orphan)}
    }
    @Test fun duplicateRowsAndMissingTimeZoneRefuse() {
        val r=fixture();r.getJSONArray("medicationLogs").put(r.getJSONArray("medicationLogs").getJSONObject(0))
        assertThrows(Featherline.Invalid::class.java){read(r)}
        val missing=fixture();missing.getJSONArray("medicationLogs").getJSONObject(0).remove("appliedAtTimeZoneId")
        assertThrows(Featherline.Invalid::class.java){read(missing)}
    }
    @Test fun repeatedAnalyteInOneSourcePanelIsReportedWithoutAmbiguousGrouping() {
        val r=fixture();val values=r.getJSONArray("bloodTestPanels").getJSONObject(0).getJSONArray("results")
        values.put(JSONObject(values.getJSONObject(0).toString()).put("uuid","00000000-0000-4000-8000-000000000019"))
        val e=read(r);assertTrue(e.labs.isEmpty());assertEquals(2,e.issues.count{it.reason=="DUPLICATE_ANALYTE"})
    }

    @Test fun unknownClinicalOverrideIsNotIgnoredOrTurnedIntoAnAssumedDose() {
        val r=fixture();r.getJSONArray("medicationLogs").getJSONObject(0).put("futureActualDose",9)
        val result=read(r);assertTrue(result.doses.isEmpty());assertEquals("UNSUPPORTED_FIELD",result.issues.single().reason);assertEquals(1,result.labs.size)
        assertThrows(Featherline.Unsupported::class.java){read(fixture().put("futureClinicalTable",JSONArray()))}
    }

    @Test fun verifiedCapsuleAndSublingualPillUseActualMassAndRoute() {
        val capsule=fixture();capsule.getJSONArray("medicines").getJSONObject(0).put("preparationType","CAPSULE").put("medicationKey","SPIRONOLACTONE").put("category","ANTIANDROGEN").put("strengthMgPerTablet",25)
        capsule.getJSONArray("medicationLogs").getJSONObject(0).put("doseInstructionKind","WHOLE_UNIT").put("category","ANTIANDROGEN").put("count",2)
        val c=read(capsule).doses.single();assertEquals("SPI",c.medicine.molecule);assertEquals("ORAL",c.medicine.route);assertEquals(50.0,c.amount)
        val sub=fixture();sub.getJSONArray("medicationLogs").getJSONObject(0).put("applicationType","SUBLINGUAL")
        val s=read(sub).doses.single();assertEquals("SUBLINGUAL",s.medicine.route);assertEquals("EV",s.medicine.ester);assertEquals(1.0,s.amount)
    }

    @Test fun repeatedLargeMedicineEvidenceHasAnAggregateMemoryBound() {
        val r=fixture();r.getJSONArray("medicines").getJSONObject(0).put("displayName","é".repeat(10000))
        val logs=r.getJSONArray("medicationLogs");val first=logs.getJSONObject(0).toString()
        for(i in 2..1000)logs.put(JSONObject(first).put("uuid",java.util.UUID(0,i.toLong()+100).toString()))
        val e=read(r);assertTrue(e.doses.size in 1..999);assertTrue(e.issues.any{it.reason=="INPUT_LIMIT"})
        assertTrue(e.doses.sumOf{it.sourceJson.toByteArray(Charsets.UTF_8).size.toLong()}+e.labs.sumOf{it.sourceJson.toByteArray(Charsets.UTF_8).size.toLong()}<=8*1024*1024)
        assertEquals(e.doses.first().sourceJson,Mona.canonical(JSONObject(e.doses.first().sourceJson)))
    }

}
