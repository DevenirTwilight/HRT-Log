package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.importer.HrtTracker
import net.plainnotes.app.timeline.PeriodTimelineProjection
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.TimeZone

/**
 * Build 21: causes that leave two period cards looking identical while the join check says they differ, reproduced
 * through the real import and settings-save paths (synthetic data only). One medicine, HRT Tracker history until
 * 10-06 morning, the app plan saved at 10-06 12:00 UTC.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class SameCardSplitTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val zone=ZoneId.of("UTC")
    private val now=Instant.parse("2026-10-20T00:00:00Z")
    private val times=listOf(LocalTime.of(8,0),LocalTime.of(20,0))
    private val base=MedicationEntity(name="Synthetic E2",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=60.0,
        soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0)
    private fun at(day:String,time:String)=Instant.parse("${day}T${time}:00Z")

    @Before fun open() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val c=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();db.openHelper.writableDatabase
        repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})
    }
    @After fun close()=db.close()

    /** Twice daily 09-01 … 10-06 08:00, a single log every ninth day; [dose] lets a float-noise variant be tested. */
    private suspend fun importHistory(medication:Long,dose:String="2") {
        val start=Instant.parse("2026-09-01T00:00:00Z").epochSecond/3600
        val events=(0..35).flatMap{d->(if(d==35 || d%9==4)listOf(8) else listOf(8,20)).map{h->
            """{"id":"synthetic-split-$d-$h","route":"sublingual","timeH":${start+d*24+h},"doseMG":$dose,"ester":"E2","extras":{"sublingualTier":2}}"""}}
        val export=HrtTracker.read("""{"meta":{"version":2},"events":[${events.joinToString(",")}]}""")
        val groups=HrtTracker.preview(export).groups.keys
        repo.importHrtTracker(HrtTracker.plan(export,null),groups.associateWith{medication},emptyMap(),null,zone)
    }
    private suspend fun save(m:MedicationEntity,at:Instant,t:List<LocalTime> =times)=repo.saveMedication(m,"E2",RuleKind.EVERY_N_DAYS,1,t,emptySet(),at)
    private suspend fun view()=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),historyPeriods=repo.historyPeriods()),emptyList(),now)
    private suspend fun assertOnePeriod(label:String) {
        val v=view();assertFalse(label,v.recognitionUnavailable)
        assertEquals(label+": "+v.projection.raw.map{"${it.span.id} ${it.span.from}..${it.span.until} ${it.standard.doses}"},1,v.projection.periods.size)
    }

    @Test fun importAfterThePlanIsSavedIsNotAStop()=runBlocking {
        val id=save(base,at("2026-10-06","12:00"));importHistory(id)
        assertEquals(1,db.dao().regimens().size);assertOnePeriod("import after plan")
    }
    @Test fun settingsSavesOnTheSameDayAreNotAStop()=runBlocking {
        val id=save(base,at("2026-10-06","12:00"));importHistory(id)
        save(base.copy(id=id,name="Synthetic E2 renamed",container_capacity=56.0),at("2026-10-06","12:30"))
        save(base.copy(id=id),at("2026-10-06","13:00"),listOf(LocalTime.of(9,0),LocalTime.of(21,0)))
        assertTrue(db.dao().regimens().sortedBy{it.effective_from_utc}.zipWithNext().all{(a,b)->a.effective_until_utc==b.effective_from_utc})
        assertOnePeriod("metadata and time edits")
    }
    @Test fun aPlanCorrectedMinutesAfterSavingIsNotAPeriod()=runBlocking {
        // Saved first as once daily, corrected to twice daily five minutes later: the card shows only the final version.
        val id=save(base,at("2026-10-06","12:00"),listOf(LocalTime.of(8,0)));importHistory(id)
        save(base.copy(id=id),at("2026-10-06","12:05"))
        assertEquals(2,db.dao().regimens().size);assertOnePeriod("corrected plan")
    }
    @Test fun aDeactivateAndReactivateWithinMinutesIsNotAStopPeriod()=runBlocking {
        val id=save(base,at("2026-10-06","12:00"));importHistory(id)
        save(base.copy(id=id,active=false),at("2026-10-06","12:10"))
        save(base.copy(id=id,active=true),at("2026-10-06","12:15"))
        assertOnePeriod("toggle within minutes")
    }
    @Test fun doseFloatNoiseInTheImportIsTheSameDose()=runBlocking {
        val id=save(base,at("2026-10-06","12:00"));importHistory(id,"2.0000000001")
        assertOnePeriod("float noise")
    }
}
