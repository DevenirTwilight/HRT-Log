package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class RegimenHistoryTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val now=Instant.parse("2026-03-09T12:00:00Z")
    private val med=MedicationEntity(name="Synthetic plan",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,
        soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    @Before fun open(){TimeZone.setDefault(TimeZone.getTimeZone("UTC"));val c=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})}
    @After fun close()=db.close()
    private suspend fun save(m:MedicationEntity,at:Instant=now)=repo.saveMedication(m,"EV",RuleKind.EVERY_N_DAYS,2,listOf(LocalTime.of(20,0)),emptySet(),at)
    @Test fun reminderChangesKeepCadenceAndVersionWhileDoseChangesFreezeNewContext()=runBlocking {
        val id=save(med);val original=db.dao().regimens().single();val anchor=repo.rules().single().anchor_local
        save(med.copy(id=id,name="Synthetic renamed",late_after_minutes=120),now.plusSeconds(86400))
        assertEquals(2,repo.rules().size);assertEquals(anchor,repo.rules().last().anchor_local)
        assertEquals(listOf(original),db.dao().regimens());assertEquals(2,db.dao().regimenLinks().size)
        assertEquals("Synthetic plan",RegimenDefinition.read(original.definition_json).snapshot(id)!!.name)
        save(med.copy(id=id,dose_per_intake=3.0),now.plusSeconds(2*86400))
        val versions=db.dao().regimens();assertEquals(2,versions.size)
        assertEquals(2.0,RegimenDefinition.read(versions[0].definition_json).dose,0.0)
        assertEquals(3.0,RegimenDefinition.read(versions[1].definition_json).dose,0.0)
        assertEquals(versions[0].effective_until_utc,versions[1].effective_from_utc)
    }
    @Test fun pauseResumeAndFormulationChangeAreRecordedAsDifferentStages()=runBlocking {
        val id=save(med)
        repo.removeMedication(id,now.plusSeconds(86400))
        save(med.copy(id=id),now.plusSeconds(2*86400))
        repo.saveMedication(med.copy(id=id,route="GEL"),"E2",RuleKind.EVERY_N_DAYS,2,listOf(LocalTime.of(20,0)),emptySet(),now.plusSeconds(3*86400),pk=ProfileEntity(id,"E2","gel",gel_product_id=1))
        repo.saveMedication(med.copy(id=id,route="GEL"),"E2",RuleKind.EVERY_N_DAYS,2,listOf(LocalTime.of(20,0)),emptySet(),now.plusSeconds(4*86400),pk=ProfileEntity(id,"E2","gel",gel_product_id=2))
        val epochs=TreatmentEpochs.build(db.dao().regimens().map{it.span()})
        assertEquals(5,epochs.size);assertTrue(epochs[1].regimenIds.isEmpty())
        assertTrue(db.dao().regimens().all{it.origin=="APP"})
    }
    @Test fun oldBackupIsReconstructedWithoutCurrentProfileAndNewTablesRoundtrip()=runBlocking {
        val id=save(med);repo.setSymptomCheck(LocalDate.of(2026,3,9),"SYNTHETIC",true,"keep")
        val password="synthetic-password".toCharArray()
        val json=JSONObject(String(BackupCodec.decrypt(repo.exportBackup(password),password)))
        val tables=json.getJSONObject("tables");listOf("regimen_version","regimen_rule_link","milestone").forEach{tables.remove(it)}
        json.put("schema",3)
        tables.getJSONArray("schedule_rule").getJSONObject(0).put("config_snapshot","{\"molecule\":\"E2\",\"route\":\"GEL\",\"unit\":\"MG\",\"ester\":\"E2\"}")
        repo.restoreBackup(BackupCodec.encrypt(json.toString().toByteArray(),password),password)
        val reconstructed=db.dao().regimens().single();assertEquals("LEGACY_RULE",reconstructed.origin);assertNull(reconstructed.recorded_at_utc)
        assertNull(RegimenDefinition.read(reconstructed.definition_json).snapshot(id)!!.profile!!.gel_product_id)
        repo.saveMilestone(MilestoneEntity(date="2026-03-09",title="Synthetic milestone",note="Synthetic note"))
        val file=repo.exportBackup(password);repo.restoreBackup(file,password)
        assertEquals("Synthetic milestone",db.dao().milestones().single().title)
        assertEquals(reconstructed,db.dao().regimens().single())
        assertEquals("keep",repo.symptomChecks(LocalDate.of(2026,3,9),LocalDate.of(2026,3,9)).single().note)
    }
    @Test fun invalidRegimenSnapshotRollsBackAndDoesNotReplaceMilestones()=runBlocking {
        save(med);repo.saveMilestone(MilestoneEntity(date="2026-03-09",title="Synthetic retained"))
        val password="synthetic-password".toCharArray();val original=repo.exportBackup(password)
        val json=JSONObject(String(BackupCodec.decrypt(original,password)))
        json.getJSONObject("tables").getJSONArray("regimen_version").getJSONObject(0).put("clinical_signature","0".repeat(64))
        try{repo.restoreBackup(BackupCodec.encrypt(json.toString().toByteArray(),password),password);fail()}catch(_:Exception){}
        assertEquals("Synthetic retained",db.dao().milestones().single().title)
        assertEquals(JSONObject(String(BackupCodec.decrypt(original,password))).getJSONObject("tables").toString(),JSONObject(String(BackupCodec.decrypt(repo.exportBackup(password),password))).getJSONObject("tables").toString())
    }
    @Test fun forgedRuleLinkIsRejectedAndFrozenDefinitionCannotBeUpdated()=runBlocking {
        save(med);val password="synthetic-password".toCharArray();val original=repo.exportBackup(password)
        try{db.openHelper.writableDatabase.execSQL("UPDATE regimen_version SET definition_json='{}'");fail()}catch(_:Exception){}
        val json=JSONObject(String(BackupCodec.decrypt(original,password)))
        json.getJSONObject("tables").getJSONArray("schedule_rule").getJSONObject(0).put("dose_snapshot",9.0)
        try{repo.restoreBackup(BackupCodec.encrypt(json.toString().toByteArray(),password),password);fail()}catch(_:Exception){}
        assertEquals(2.0,repo.rules().single().dose_snapshot,0.0)
        assertEquals(2.0,RegimenDefinition.read(db.dao().regimens().single().definition_json).dose,0.0)
    }

}
