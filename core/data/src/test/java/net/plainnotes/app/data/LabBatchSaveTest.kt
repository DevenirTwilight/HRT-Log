package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.RuleKind
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/**
 * LAB-01 B, synthetic data only: saveLabs writes N results in one transaction exactly as N saveLab calls would (every field
 * but ids and the LAB-02 panel, contexts included, estimate asked per result), and a failure on any one keeps none of them.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class LabBatchSaveTest {
    private val opened=mutableListOf<NotesDatabase>()
    private val at=Instant.parse("2026-03-10T12:00:00Z")
    private val med=MedicationEntity(name="Synthetic E2",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private val sampled=at.minusSeconds(3600).toEpochMilli()
    private val six=listOf("FSH" to ("IU/L" to 4.0),"LH" to ("IU/L" to 3.0),"E2" to ("pg/mL" to 150.0),"P4_MS" to ("ng/mL" to 0.3),"T" to ("ng/dL" to 30.0),"PRL" to ("ng/mL" to 12.0))
        .map{(code,v)->LabValueEntity(analyte_code=code,value=v.second,unit=v.first,sampled_utc=sampled,sampled_zone="Europe/Paris",reference_lower=if(code=="E2")100.0 else null,
            reference_upper=if(code=="E2")200.0 else null,reference_unit=if(code=="E2")"pg/mL" else null,laboratory="Synthetic lab",note=if(code=="T")"Synthetic note" else null)}
    private fun open():Pair<NotesDatabase,NotesRepository> {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build().also{opened+=it}
        return db to NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})
    }
    @After fun close()=opened.forEach{it.close()}
    private fun NotesDatabase.analytes()=openHelper.readableDatabase.query("SELECT * FROM lab_analyte ORDER BY 1,2").use{c->
        buildList{while(c.moveToNext())add((0 until c.columnCount).joinToString("|"){c.getString(it) ?: "null"})}}
    private suspend fun seed(db:NotesDatabase,repo:NotesRepository) {
        val id=repo.saveMedication(med,"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0)),emptySet(),at.minusSeconds(10*86400),pk=ProfileEntity(0,"E2","sublingual",sl_tier=2))
        db.dao().record(RecordEntity(medication_id=id,taken_utc=at.minusSeconds(7200).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,
            config_snapshot=MedicationSnapshot.encode(db.dao().medication(id),db.dao().profile(id))))
    }
    /** A minimal estimate document that LabContext accepts; "missing" names the result so each one is told apart. */
    private fun synthetic(lab:LabValueEntity)=org.json.JSONObject().put("version",1).put("calibrated",false).put("sampled_utc",lab.sampled_utc).put("calculator_version",2)
        .put("used_doses",0).put("skipped_doses",0).put("weight_kg_at_capture",org.json.JSONObject.NULL).put("values",org.json.JSONArray())
        .put("parameter_document",org.json.JSONObject().put("version","synthetic").put("models",org.json.JSONObject()).put("references",org.json.JSONArray()))
        .put("missing",org.json.JSONArray().put("synthetic:"+lab.analyte_code)).put("unsupported",org.json.JSONArray()).put("inputs",org.json.JSONArray()).toString()
    private val estimate:suspend(NotesDao,LabValueEntity)->String?={_,lab->synthetic(lab)}

    @Test fun batchOfSixEqualsSixSingleSavesFieldByField()=runBlocking {
        val (batchDb,batch)=open();seed(batchDb,batch)
        val (singleDb,single)=open();seed(singleDb,single)
        batch.saveLabs(six,at,recapture=true,estimate=estimate)
        six.forEach{single.saveLab(it,at,recapture=true,estimate=estimate)}
        val b=batch.labs().sortedBy{it.id};val s=single.labs().sortedBy{it.id}
        assertEquals(6,b.size)
        // LAB-02: the batch is also one lab panel (ADR Q6); every other field equals the single saves.
        assertEquals(s.map{it.copy(id=0)},b.map{it.copy(id=0,panel_id=null)})
        assertTrue(s.all{it.panel_id==null});val panel=batch.labPanels().single();assertTrue(b.all{it.panel_id==panel.id})
        assertEquals(Triple(sampled,"Europe/Paris","Synthetic lab"),Triple(panel.sampled_utc,panel.sampled_zone,panel.laboratory))
        assertEquals(setOf(sampled),b.map{it.sampled_utc}.toSet());assertEquals(setOf("Europe/Paris"),b.map{it.sampled_zone}.toSet());assertEquals(setOf("Synthetic lab"),b.map{it.laboratory}.toSet())
        fun contexts(r:NotesRepository,labs:List<LabValueEntity>)=r.let{runBlocking{it.labContexts()}}.map{c->c.copy(id=0,lab_id=labs.indexOfFirst{it.id==c.lab_id}.toLong())}.sortedBy{it.lab_id}
        assertEquals(contexts(single,s),contexts(batch,b))
        assertEquals(6,batch.labContexts().size);assertTrue(batch.labContexts().all{it.origin=="AT_ENTRY" && "synthetic:" in it.context_json})
        assertEquals(singleDb.analytes(),batchDb.analytes())
    }

    @Test fun aFailureOnTheFourthKeepsNone()=runBlocking {
        val (db,repo)=open();seed(db,repo)
        var calls=0
        val failing:suspend(NotesDao,LabValueEntity)->String?={_,lab->if(++calls==4)error("synthetic failure") else synthetic(lab)}
        val analytes=db.analytes()
        try{repo.saveLabs(six,at,recapture=true,estimate=failing);fail("expected the batch to fail")}catch(_:IllegalStateException){}
        assertEquals(4,calls)
        assertTrue(repo.labs().isEmpty());assertTrue(repo.labContexts().isEmpty());assertEquals(analytes,db.analytes());assertTrue(repo.labPanels().isEmpty())
        // An invalid row (the single-result rules) also rolls the whole batch back.
        try{repo.saveLabs(six.take(3)+six[3].copy(value=0.0),at);fail("expected the batch to fail")}catch(_:IllegalArgumentException){}
        assertTrue(repo.labs().isEmpty())
    }

    @Test fun onlyNewResultsAndAtLeastOne()=runBlocking {
        val (db,repo)=open();seed(db,repo)
        try{repo.saveLabs(emptyList(),at);fail()}catch(_:IllegalArgumentException){}
        repo.saveLabs(six.take(1),at);val saved=repo.labs().single()
        try{repo.saveLabs(listOf(saved.copy(value=99.0)),at);fail()}catch(_:IllegalArgumentException){}
        assertEquals(saved,repo.labs().single())
    }
}
