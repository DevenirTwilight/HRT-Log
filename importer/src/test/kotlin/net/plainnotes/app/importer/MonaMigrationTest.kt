package net.plainnotes.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MonaMigrationTest {
    private fun root()=JSONObject().put("metadata",JSONObject().put("database_version",23).put("app_version","synthetic").put("export_date","2026-10-25T12:00:00"))
        .put("data",JSONObject().put("medication_intakes",JSONArray()).put("medication_schedules",JSONArray()).put("supply_items",JSONArray()).put("blood_tests",JSONArray()))
    private fun intake(id:Long=1)=JSONObject().put("id",id).put("takenDateTime","2026-10-25T00:30:00Z").put("takenTimeZone","Europe/Paris").put("takenDose","1.2500")
        .put("scheduledTime","9:0").put("molecule",JSONObject().put("name","estradiol").put("massUnit","mg").put("rateUnit","µg/day").toString())
        .put("administrationRoute","oral").put("ester",JSONObject.NULL).put("dosingBasis","mass").put("genericSupplyItemIds","[]").put("placements","[]")
    private fun lab(id:Long=2)=JSONObject().put("id",id).put("dateTime","2026-10-25T08:30:00Z").put("timeZone","Europe/Paris")
        .put("estradiolLevels",JSONObject().put("value","210.00").put("unit","pg/mL").toString())
        .put("testosteroneLevels",JSONObject().put("value","9").put("unit","ng/dL").toString()).put("notes","Synthetic draw")
    private fun plan(root:JSONObject)=MonaMigration.plan(Mona.read(root.toString()))
    @Test fun onlyExplicitActualTimeDoseAndZoneReachTheClinicalProjection() {
        val r=root();r.getJSONObject("data").getJSONArray("medication_intakes").put(intake()).put(intake(3).put("takenDateTime",JSONObject.NULL))
        val p=plan(r);assertEquals(1,p.doses.size);assertEquals(2,p.sourceIntakes);assertEquals(1.25,p.doses.single().amount)
        assertEquals("1.2500",p.doses.single().original);assertEquals("Europe/Paris",p.doses.single().zone)
        assertEquals("2026-10-25T00:30:00Z",p.doses.single().at.toString());assertEquals(MonaMigration.Reason.NOT_TAKEN,p.issues.single().reason)
    }
    @Test fun subMillisecondTimeIsReportedInsteadOfRounded() {
        val r=root();r.getJSONObject("data").getJSONArray("medication_intakes").put(intake().put("takenDateTime","2026-10-25T00:30:00.123456Z"))
        val p=plan(r);assertTrue(p.doses.isEmpty());assertEquals(MonaMigration.Reason.TIME_PRECISION,p.issues.single().reason)
    }
    @Test fun precisionThatDoubleCannotRepresentIsNotSilentlyRounded() {
        val r=root();r.getJSONObject("data").getJSONArray("medication_intakes").put(intake().put("takenDose","1.0000000000000001"))
        val p=plan(r);assertTrue(p.doses.isEmpty());assertEquals(MonaMigration.Reason.DOSE_PRECISION,p.issues.single().reason)
    }
    @Test fun patchReleaseRateAndUnknownCompoundUnitAndRouteAreNotGuessed() {
        val variants=listOf(intake().put("dosingBasis","releaseRate"),intake().put("administrationRoute","unreviewed"),
            intake().put("molecule",JSONObject().put("name","estradiol unknown salt").put("massUnit","mg").toString()),
            intake().put("molecule",JSONObject().put("name","estradiol").put("massUnit","mL").toString()))
        for(v in variants){val r=root();r.getJSONObject("data").getJSONArray("medication_intakes").put(v);assertTrue(plan(r).doses.isEmpty())}
    }
    @Test fun esterAndInjectionSubtypeStayDifferentIdentities() {
        val r=root();val rows=r.getJSONObject("data").getJSONArray("medication_intakes")
        rows.put(intake().put("administrationRoute","injection").put("ester","cypionate").put("injectionType","intramuscular"))
        rows.put(intake(2).put("administrationRoute","injection").put("ester","cypionateSuspension").put("injectionType","intramuscular"))
        rows.put(intake(3).put("administrationRoute","injection").put("ester","cypionate").put("injectionType","subcutaneous"))
        val p=plan(r);assertEquals(3,p.doses.map{it.identity}.toSet().size);assertEquals(listOf("EC","EC_SUSPENSION","EC"),p.doses.map{it.identity.esterCode})
    }
    @Test fun wasteAndDeadSpaceDoNotIncreaseActualDoseAndSchedulesAndSupplyStayUnsupported() {
        val r=root();val d=r.getJSONObject("data");d.getJSONArray("medication_intakes").put(intake().put("wastedAmount","0.1").put("deadSpace","100"))
        d.getJSONArray("medication_schedules").put(JSONObject().put("id",7).put("dose","99"));d.getJSONArray("supply_items").put(JSONObject().put("id",9).put("amount",999))
        val p=plan(r);assertEquals(1.25,p.doses.single().amount);assertEquals(1,p.unsupportedSchedules);assertEquals(1,p.unsupportedSupplies)
    }
    @Test fun eachLabFragmentRetainsOnlyItsOwnReportedResultAndOriginalUnit() {
        val r=root();r.getJSONObject("data").getJSONArray("blood_tests").put(lab());val p=plan(r)
        assertEquals(2,p.labs.size);assertEquals(listOf("210.00","9"),p.labs.map{it.original});assertEquals(listOf("pg/mL","ng/dL"),p.labs.map{it.unit})
        assertFalse(JSONObject(p.labs[0].sourceJson).has("testosteroneLevels"));assertFalse(JSONObject(p.labs[1].sourceJson).has("estradiolLevels"))
        assertEquals(listOf("2","2"),p.labs.map{it.sourceId});assertNotEquals(p.labs[0].sourceDigest,p.labs[1].sourceDigest)
    }
    @Test fun updatingAnotherAnalyteOrExportDateDoesNotChangeUnmodifiedSourceIdentity() {
        val r=root();val l=lab();r.getJSONObject("data").getJSONArray("blood_tests").put(l);val p=plan(r)
        l.put("testosteroneLevels",JSONObject().put("value","10").put("unit","ng/dL").toString());r.getJSONObject("metadata").put("export_date","2026-10-26T10:00:00")
        val q=plan(r);assertEquals(p.labs[0].sourceDigest,q.labs[0].sourceDigest);assertNotEquals(p.labs[1].sourceDigest,q.labs[1].sourceDigest)
    }
}
