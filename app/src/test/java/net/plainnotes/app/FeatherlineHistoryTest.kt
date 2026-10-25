package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.timeline.ObservedTreatmentHistory
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class FeatherlineHistoryTest {
    @Test fun knownSourceEsterIsHistoricalIdentityWithoutCreatingAPkProfile() {
        val med=MedicationEntity(id=1,name="SYNTHETIC EV",molecule="E2",route="ORAL",unit="MG",dose_per_intake=1.0,container_capacity=1.0,site_rotation=false,notifications_on=false,active=false,sort_order=0)
        val frozen=JSONObject(MedicationSnapshot.encode(med,null)).put("external_context",JSONObject().put("source","FEATHERLINE").put("ester","EV")).toString()
        val records=(0..3).map{day->RecordEntity(id=day+1L,medication_id=1,taken_utc=Instant.parse("2026-10-20T08:00:00Z").plusSeconds(day*86400L).toEpochMilli(),taken_zone="Europe/Paris",actual_dose=1.0,status="ON_TIME",origin="IMPORT_FEATHERLINE",source_record_key="featherline:log:synthetic-$day",revision=1,config_snapshot=frozen)}
        val result=ObservedTreatmentHistory.build(records,emptyList(),ZoneId.of("Europe/Paris"),Instant.parse("2026-10-25T12:00:00Z"))
        assertTrue(result.observed.isNotEmpty());assertTrue(result.observed.all{it.interval.standard.ester=="EV"})
        assertTrue(records.all{MedicationSnapshot.decode(it.config_snapshot,1)!!.profile==null})
    }
}
