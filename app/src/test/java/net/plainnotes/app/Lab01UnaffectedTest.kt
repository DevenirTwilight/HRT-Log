package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.medical.MedicalRecordIndex
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/**
 * LAB-01 E, synthetic data only: results saved through the batch form are the same rows as results saved one by one, so every
 * per-result reader sees the same input: concentration lab points and calibration eligibility, timeline lab events, the
 * medical "labs in range", backup and restore. Both databases start identical, so even ids match.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class Lab01UnaffectedTest {
    private val opened=mutableListOf<NotesDatabase>()
    private val at=Instant.parse("2026-03-10T12:00:00Z")
    private val m=MedicationEntity(name="Synthetic estimate",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=40.0,soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    @After fun close()=opened.forEach{it.close()}
    private suspend fun open():Pair<NotesDatabase,NotesRepository> {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build().also{opened+=it}
        val repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})
        val id=repo.saveMedication(m,"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0)),emptySet(),at.minusSeconds(20*86400),pk=ProfileEntity(0,"E2","sublingual",sl_tier=2))
        val snapshot=MedicationSnapshot.encode(m.copy(id=id),ProfileEntity(id,"E2","sublingual",sl_tier=2))
        (1..10).forEach{d->db.dao().record(RecordEntity(medication_id=id,taken_utc=at.minusSeconds(d*86400L-3600).toEpochMilli(),taken_zone="UTC",actual_dose=2.0,status="ON_TIME",origin="APP",revision=1,config_snapshot=snapshot))}
        return db to repo
    }
    private fun draw(t:Instant,lab:String?)=listOf("E2" to 150.0,"T" to 30.0,"PRL" to 12.0).map{(c,v)->LabValueEntity(analyte_code=c,value=v,unit=net.plainnotes.app.ui.analyte(c).units.first().first,
        sampled_utc=t.toEpochMilli(),sampled_zone="UTC",reference_lower=if(c=="E2")100.0 else null,reference_upper=if(c=="E2")200.0 else null,reference_unit=if(c=="E2")"pg/mL" else null,laboratory=lab)}
    private val estimate:suspend(NotesDao,LabValueEntity)->String?={dao,lab->net.plainnotes.app.conc.SublingualEstimatePolicy.apply(net.plainnotes.app.conc.LabEstimate.capture(dao,lab))}

    @Test fun batchAndSingleSavesFeedEveryPerResultReaderTheSame()=runBlocking {
        val draws=listOf(draw(at.minusSeconds(5*86400),"Synthetic lab"),draw(at.minusSeconds(86400),null))
        val (bDb,batch)=open();val (sDb,single)=open()
        draws.forEach{batch.saveLabs(it,at,recapture=true,estimate=estimate)}
        draws.flatten().forEach{single.saveLab(it,at,recapture=true,estimate=estimate)}
        val bl=bDb.dao().labs();val sl=sDb.dao().labs()
        assertEquals(6,bl.size);assertEquals(sl,bl);assertEquals(sDb.dao().labContexts(),bDb.dao().labContexts())
        // Concentration: lab points and per-lab calibration eligibility.
        fun conc(db:NotesDatabase)=runBlocking{val d=db.dao();val meds=d.medications()
            ConcentrationCalculator.compute(meds,meds.mapNotNull{x->d.profile(x.id)?.let{x.id to it}}.toMap(),d.records(),emptyList(),d.labs(),null,at)}
        val bc=conc(bDb);val sc=conc(sDb)
        assertEquals(sc.labs,bc.labs);assertEquals(sc.labEligibility,bc.labEligibility);assertEquals(sc.calibration?.labCount,bc.calibration?.labCount);assertEquals(sc.e2.toList(),bc.e2.toList());assertTrue(bc.labs.isNotEmpty())
        // Timeline lab events and the medical records' "labs in this period".
        fun timeline(db:NotesDatabase)=runBlocking{net.plainnotes.app.timeline.LongitudinalProjection.build(NotesViewModel.ExtraState(records=db.dao().records(),regimens=db.dao().regimens(),labs=db.dao().labs()),emptyList(),ZoneId.of("UTC")).events}
        assertEquals(timeline(sDb),timeline(bDb))
        val from=LocalDate.of(2026,3,1);val to=LocalDate.of(2026,3,10)
        assertEquals(MedicalRecordIndex.labs(sl,from,to,ZoneId.of("UTC")),MedicalRecordIndex.labs(bl,from,to,ZoneId.of("UTC")))
        // Backup and restore keep the rows and their frozen contexts, one by one.
        val pwd="synthetic-pass".toCharArray();val backup=batch.exportBackup(pwd);batch.restoreBackup(backup,pwd)
        assertEquals(sl,bDb.dao().labs());assertEquals(sDb.dao().labContexts(),bDb.dao().labContexts())
    }
}
