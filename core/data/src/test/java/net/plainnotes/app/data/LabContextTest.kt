package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.RuleKind
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class LabContextTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val at=Instant.parse("2026-03-10T12:00:00Z")
    private val med=MedicationEntity(name="Synthetic E2",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private fun lab(time:Instant=at)=LabValueEntity(analyte_code="E2",value=120.0,unit="pg/mL",sampled_utc=time.toEpochMilli(),sampled_zone="UTC")
    @Before fun open(){val c=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})}
    @After fun close()=db.close()
    private suspend fun medication(m:MedicationEntity=med):Long=repo.saveMedication(m,"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0)),emptySet(),at.minusSeconds(10*86400),pk=if(m.molecule=="E2")ProfileEntity(0,"E2","sublingual",sl_tier=2)else null)
    private suspend fun actual(id:Long,secondsAgo:Long,status:String="ON_TIME",snapshot:String?=null)=db.dao().record(RecordEntity(medication_id=id,taken_utc=at.minusSeconds(secondsAgo).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status=status,origin="APP",revision=1,config_snapshot=snapshot ?: MedicationSnapshot.encode(db.dao().medication(id),db.dao().profile(id))))
    @Test fun ingredientContextExcludesFutureAndDeletedAndNeverUsesCurrentProfileForImports()=runBlocking {
        val e=medication();val t=medication(med.copy(name="Synthetic T",molecule="T",route="INJECTION"))
        actual(e,3600);actual(t,600);actual(e,-3600)
        val old=actual(e,300);db.dao().updateRecord(db.dao().recordById(old)!!.copy(deleted_at_utc=at.toEpochMilli()))
        db.dao().record(RecordEntity(medication_id=e,taken_utc=at.minusSeconds(200).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",revision=1,config_snapshot="{\"source\":\"hrttracker\"}"))
        repo.saveLab(lab(),at)
        val o=LabContext.validate(repo.labContexts().single().context_json);val a=o.getJSONArray("actual")
        assertEquals(3,a.length());val known=(0 until a.length()).map{a.getJSONObject(it)}
        assertEquals(3600_000L,known.single{it.optString("ingredient")=="E2"}.getLong("elapsed_ms"))
        assertEquals(600_000L,known.single{it.optString("ingredient")=="T"}.getLong("elapsed_ms"))
        val unknown=known.single{it.isNull("ingredient")};assertTrue(unknown.isNull("route"));assertTrue(unknown.isNull("unit"))
        assertFalse(o.getJSONObject("epoch").getBoolean("unknown"));assertEquals(2,o.getJSONArray("regimens").length())
    }
    @Test fun revisionsStayFrozenAcrossEditsRebuildAndBackupAndDeleteCascades()=runBlocking {
        val id=medication();actual(id,3600);repo.saveLab(lab(),at)
        val original=repo.labContexts().single();val l=repo.labs().single()
        repo.saveLab(l.copy(value=150.0,note="Synthetic correction"),at.plusSeconds(1));assertEquals(listOf(original),repo.labContexts())
        actual(id,100);repo.rebuildLabContext(l.id,at.plusSeconds(2));assertEquals(original,repo.labContexts().first());assertEquals(2,repo.labContexts().size)
        repo.saveLab(l.copy(sampled_utc=at.plusSeconds(86400).toEpochMilli()),at.plusSeconds(3));assertEquals("SAMPLE_CHANGED",repo.labContexts().last().origin)
        val expected=repo.labContexts();val pwd="synthetic-pass".toCharArray();val backup=repo.exportBackup(pwd);repo.restoreBackup(backup,pwd)
        assertEquals(expected,repo.labContexts());assertEquals(original.context_json,repo.labContexts().first().context_json)
        try{db.openHelper.writableDatabase.execSQL("UPDATE lab_context_revision SET context_json='{}' WHERE id=?",arrayOf(original.id));fail()}catch(_:Exception){}
        repo.deleteLab(l.id);assertTrue(repo.labContexts().isEmpty())
    }
    @Test fun windowCountsDistinguishLateMissedAndUnconfirmedAndLegacyLabsStayUnknown()=runBlocking {
        val id=medication();actual(id,48*3600,"LATE");actual(id,48*3600+1,"LATE")
        listOf("APP","AUTO_MISSED").forEach{origin->db.dao().record(RecordEntity(medication_id=id,scheduled_utc=at.minusSeconds(3600).toEpochMilli(),scheduled_zone="UTC",status="MISSED",origin=origin,revision=1,config_snapshot="{}"))}
        db.dao().analyte(AnalyteEntity("E2","pg/mL"));val legacy=db.dao().insertLab(lab(at.minusSeconds(100*86400)))
        assertTrue(repo.labContexts().isEmpty());repo.rebuildLabContext(legacy,at)
        val old=repo.labContexts().single();assertEquals("RECONSTRUCTED",old.origin);assertTrue(JSONObject(old.context_json).getJSONObject("epoch").getBoolean("unknown"))
        repo.saveLab(lab(),at);val counts=JSONObject(repo.labContexts().last().context_json).getJSONObject("counts")
        assertEquals(1,counts.getInt("late"));assertEquals(1,counts.getInt("missed"));assertEquals(1,counts.getInt("unconfirmed"))
    }
    @Test fun schema4BackupNeedsNoContextAndMalformedContextFailsWithoutChangingData()=runBlocking {
        val id=medication();actual(id,3600);repo.saveLab(lab(),at)
        val pwd="synthetic-pass".toCharArray();val backup=repo.exportBackup(pwd);val o=JSONObject(String(BackupCodec.decrypt(backup,pwd)))
        val expected=repo.labContexts();val contexts=o.getJSONObject("tables").getJSONArray("lab_context_revision")
        val json=JSONObject(contexts.getJSONObject(0).getString("context_json"));json.getJSONArray("actual").getJSONObject(0).put("elapsed_ms",999)
        contexts.getJSONObject(0).put("context_json",json.toString())
        try{repo.restoreBackup(BackupCodec.encrypt(o.toString().toByteArray(),pwd),pwd);fail()}catch(_:Exception){}
        assertEquals(expected,repo.labContexts());assertEquals(1,repo.labs().size)
        val old=JSONObject(String(BackupCodec.decrypt(backup,pwd)));old.put("schema",4);old.getJSONObject("tables").remove("lab_context_revision")
        repo.restoreBackup(BackupCodec.encrypt(old.toString().toByteArray(),pwd),pwd);assertEquals(1,repo.labs().size);assertTrue(repo.labContexts().isEmpty())
    }
    @Test fun simultaneousLastIntakesAreBothPreservedAndNoCurrentConfigurationRewritesTheirContext()=runBlocking {
        val first=medication();val second=medication(med.copy(name="Synthetic second"))
        actual(first,3600);actual(second,3600);repo.saveLab(lab(),at)
        val row=repo.labContexts().single();assertEquals(2,LabContext.validate(row.context_json).getJSONArray("actual").length())
        repo.saveMedication(med.copy(id=first,route="GEL"),"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0)),emptySet(),at.plusSeconds(1),pk=ProfileEntity(first,"E2","gel",gel_product_id=1))
        assertEquals(row,repo.labContexts().single())
    }

}
