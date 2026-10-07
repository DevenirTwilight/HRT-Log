package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.importer.HrtTracker
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class HistoricalContextRepairTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val now=Instant.parse("2026-03-10T12:00:00Z")
    private val med=MedicationEntity(name="Synthetic history",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    @Before fun open(){val c=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})}
    @After fun close()=db.close()
    @Test fun explicitContextConfirmationIsDateScopedFrozenAndDoesNotTouchStockOrDoses()=runBlocking {
        val id=repo.saveMedication(med,"EV",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),now)
        repo.addContainers(id,40.0,1,true);repo.unscheduled(id,now.minusSeconds(3600),2.0)
        val r=repo.records().single();db.dao().recordContext(r.id,"{\"source\":\"synthetic-old\"}")
        val old=r.copy(id=0,taken_utc=now.minusSeconds(10*86400).toEpochMilli(),unallocated_supply_amount=null,config_snapshot="{\"source\":\"synthetic-old\"}");db.dao().record(old)
        val boxes=repo.containers();val ledger=db.dao().supplyFor(r.id)
        val date=now.atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(1,repo.confirmHistoricalContext(med.copy(id=id),ProfileEntity(id,"EV","oral"),date,date,now))
        val confirmed=db.dao().recordById(r.id)!!;assertEquals(r.actual_dose,confirmed.actual_dose);assertEquals(r.taken_utc,confirmed.taken_utc);assertEquals(r.revision,confirmed.revision)
        assertEquals(boxes,repo.containers());assertEquals(ledger,db.dao().supplyFor(r.id))
        assertEquals("USER_CONFIRMED",JSONObject(confirmed.config_snapshot).getJSONObject("historical_context_confirmation").getString("kind"))
        assertEquals(1,repo.records().count{HistoricalContext.incomplete(it)})
        repo.saveMedication(med.copy(id=id,route="GEL"),"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),now.plusSeconds(86400),pk=ProfileEntity(id,"E2","gel",gel_product_id=1))
        assertEquals("ORAL",MedicationSnapshot.decode(db.dao().recordById(r.id)!!.config_snapshot,id)!!.route)
        val password="synthetic-password".toCharArray();val backup=repo.exportBackup(password);repo.restoreBackup(backup,password)
        assertEquals(confirmed.config_snapshot,db.dao().recordById(r.id)!!.config_snapshot);assertEquals(boxes,repo.containers())
    }
    @Test fun savedConflictingChemistryCannotBeOverwrittenByConfirmation() {
        val source="{\"molecule\":\"E2\",\"route\":\"ORAL\",\"unit\":\"MG\",\"ester\":\"EV\"}"
        assertNull(HistoricalContext.fill(source,MedicationSnapshot.encode(med.copy(route="GEL"),ProfileEntity(1,"E2","gel",gel_product_id=1))))
    }
    @Test fun originalTrackerReimportRepairsOnlyMatchingMissingRecords()=runBlocking {
        val id=repo.saveMedication(med,"EV",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),now)
        val group=HrtTracker.Group("ORAL","EV",null,null,null,null,null)
        val rows=listOf("ht:synthetic-1","ht:synthetic-2").map{key->db.dao().record(RecordEntity(medication_id=id,taken_utc=now.minusSeconds(3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",source_record_key=key,revision=1,config_snapshot="{\"source\":\"hrttracker\"}"))}
        val plan=HrtTracker.Plan(listOf(HrtTracker.PlannedIntake("ht:synthetic-1",group,now.minusSeconds(3600),2.0),HrtTracker.PlannedIntake("ht:synthetic-2",group,now.minusSeconds(3600),3.0)),emptyList(),emptyMap())
        val result=HrtTrackerWriter.write(db.dao(),plan,mapOf(group to id),emptyMap(),null,ZoneId.of("UTC"))
        assertEquals(0,result.intakes);assertEquals(2,repo.records().size)
        assertFalse(HistoricalContext.incomplete(db.dao().recordById(rows[0])!!))
        assertTrue(HistoricalContext.incomplete(db.dao().recordById(rows[1])!!))
        assertEquals("ORIGINAL_HT_EXPORT",JSONObject(db.dao().recordById(rows[0])!!.config_snapshot).getJSONObject("historical_context_confirmation").getString("kind"))
    }

    @Test fun explicitConfirmationAlsoRespectsFactsFromLinkedOldRule()=runBlocking {
        val id=repo.saveMedication(med,"EV",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),now.minusSeconds(86400))
        val rule=repo.rules().single();val r=RecordEntity(medication_id=id,rule_version_id=rule.id,taken_utc=now.minusSeconds(3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot="{\"source\":\"hrttracker\"}")
        val row=db.dao().record(r);val date=now.atZone(ZoneId.systemDefault()).toLocalDate()
        try{repo.confirmHistoricalContext(med.copy(id=id,route="GEL"),ProfileEntity(id,"E2","gel",gel_product_id=1),date,date,now);fail()}catch(_:Exception){}
        assertEquals(r.config_snapshot,db.dao().recordById(row)!!.config_snapshot)
    }

}
