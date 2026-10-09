package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.conc.LabEstimate
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class LabEstimateTest {
    @Test fun frozenSublingualEstimateDoesNotUseFutureIntakesOrFitItsOwnLab()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            val at=Instant.parse("2026-03-10T12:00:00Z")
            val m=MedicationEntity(name="Synthetic estimate",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
            val id=repo.saveMedication(m,"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0)),emptySet(),at.minusSeconds(86400),pk=ProfileEntity(0,"E2","sublingual",sl_tier=2))
            val snapshot=MedicationSnapshot.encode(m.copy(id=id),ProfileEntity(id,"E2","sublingual",sl_tier=2))
            db.dao().record(RecordEntity(medication_id=id,taken_utc=at.minusSeconds(3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=snapshot))
            val lab=LabValueEntity(analyte_code="E2",value=120.0,unit="pg/mL",sampled_utc=at.toEpochMilli(),sampled_zone="UTC")
            val original=JSONObject(LabEstimate.capture(db.dao(),lab))
            assertEquals(2,original.getInt("calculator_version"));assertFalse(original.getBoolean("calibrated"));assertTrue(original.getJSONObject("parameter_document").has("models"))
            assertEquals(1,original.getJSONArray("inputs").length());assertTrue(original.getJSONArray("values").getJSONObject(0).getDouble("value")>0)
            db.dao().record(RecordEntity(medication_id=id,taken_utc=at.plusSeconds(3600).toEpochMilli(),taken_zone="UTC",actual_dose=200.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=snapshot))
            val other=JSONObject(LabEstimate.capture(db.dao(),lab.copy(value=12_000.0)))
            assertEquals(original.getJSONArray("values").toString(),other.getJSONArray("values").toString());assertEquals(1,other.getJSONArray("inputs").length())
            // A genuinely unknown imported input remains unknown and cannot prevent capturing the other facts.
            db.dao().record(RecordEntity(medication_id=id,taken_utc=at.minusSeconds(1800).toEpochMilli(),taken_zone="UTC",actual_dose=null,status="ON_TIME",origin="IMPORT_TM",revision=1,config_snapshot="{}"))
            repo.saveLab(lab,at,estimate={dao,l->LabEstimate.capture(dao,l)})
            val saved=repo.labContexts().single();assertEquals(original.getJSONArray("values").toString(),LabContext.validate(saved.context_json).getJSONObject("estimate").getJSONArray("values").toString())
            val malformed=JSONObject(saved.context_json)
            malformed.getJSONObject("estimate").getJSONArray("values").getJSONObject(0).put("unit","MG")
            assertThrows(IllegalArgumentException::class.java){LabContext.validate(malformed.toString())}
            assertEquals(2,LabContext.validate(saved.context_json).getJSONObject("estimate").getJSONArray("inputs").length())
            // Existing version counter changes; the envelope and immutable legacy JSON do not.
            val legacyBackup=JSONObject(String(BackupCodec.decrypt(repo.exportBackup("legacy-synthetic-pass".toCharArray()),"legacy-synthetic-pass".toCharArray())))
            val legacyRow=legacyBackup.getJSONObject("tables").getJSONArray("lab_context_revision").getJSONObject(0)
            val legacyJson=JSONObject(legacyRow.getString("context_json"));legacyJson.getJSONObject("estimate").put("calculator_version",1)
            legacyJson.getJSONObject("estimate").getJSONArray("values").getJSONObject(0).put("p5",0.0).put("p95",152714.8058637828)
            val legacyBytes=legacyJson.toString();legacyRow.put("context_json",legacyBytes)
            repo.restoreBackup(BackupCodec.encrypt(legacyBackup.toString().toByteArray(),"legacy-synthetic-pass".toCharArray()),"legacy-synthetic-pass".toCharArray())
            assertEquals(legacyBytes,repo.labContexts().single().context_json)
            assertEquals(1,LabContext.validate(legacyBytes).getJSONObject("estimate").getInt("calculator_version"))
            val unknown=JSONObject(legacyBytes);unknown.getJSONObject("estimate").put("calculator_version",3)
            assertThrows(IllegalArgumentException::class.java){LabContext.validate(unknown.toString())}
            val password="synthetic-pass".toCharArray();val backup=repo.exportBackup(password);repo.restoreBackup(backup,password);assertEquals(legacyBytes,repo.labContexts().single().context_json)
        } finally { db.close() }
    }
}
