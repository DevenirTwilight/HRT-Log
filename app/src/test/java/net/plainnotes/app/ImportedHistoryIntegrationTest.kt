package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.importer.HrtTracker
import net.plainnotes.app.timeline.PeriodTimelineProjection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

/** Synthetic JSON → importer → real database → timeline, including re-import and encrypted restore. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class ImportedHistoryIntegrationTest {
    @Test fun trackerHistoryAndLabsReachTimelineWithoutInventingSchedules()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            val export=HrtTracker.read("""{"meta":{"version":2},"events":[
                {"id":"synthetic-a","route":"sublingual","timeH":490000,"doseMG":2,"ester":"E2","extras":{"sublingualTier":2}},
                {"id":"synthetic-b","route":"sublingual","timeH":490012,"doseMG":1,"ester":"E2","extras":{"sublingualTier":2}}],
                "labResults":[{"id":"synthetic-lab","timeH":490006,"concValue":150,"unit":"pg/ml"}]}""")
            val plan=HrtTracker.plan(export,null);val groups=HrtTracker.preview(export).groups.keys
            repo.importHrtTracker(plan,groups.associateWith{null},groups.associateWith{"Synthetic imported medicine"},null,ZoneId.of("UTC"))
            suspend fun view()=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),labs=db.dao().labs()),emptyList(),Instant.parse("2026-10-07T12:00:00Z"))
            val first=view();assertEquals(2,first.importedHistory.single().records.size);assertEquals(1,first.events.size)
            assertTrue(first.projection.periods.isEmpty());assertEquals("SUBLINGUAL",MedicationSnapshot.decode(first.importedHistory.single().records.first().config_snapshot,1)!!.route)
            val medication=repo.medications().single()
            repo.importHrtTracker(plan,groups.associateWith{medication.id},emptyMap(),null,ZoneId.of("UTC"))
            assertEquals(first,view())
            val password="synthetic-password".toCharArray();repo.restoreBackup(repo.exportBackup(password),password)
            assertEquals(first,view());assertTrue(db.dao().containers().isEmpty())
        } finally {db.close()}
    }
    @Test fun recurringImportedHistoryAutomaticallyCreatesPastPeriodsAndSurvivesRestore()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            val events=(0..39).joinToString(","){day->"""{"id":"synthetic-period-$day","route":"sublingual","timeH":${480000+day*24},"doseMG":${if(day<20)2 else 3},"ester":"E2","extras":{"sublingualTier":2}}"""}
            val export=HrtTracker.read("""{"meta":{"version":2},"events":[$events],"labResults":[{"id":"synthetic-lab-period","timeH":480048,"concValue":150,"unit":"pg/ml"}]}""")
            val groups=HrtTracker.preview(export).groups.keys
            val plan=HrtTracker.plan(export,null)
            repo.importHrtTracker(plan,groups.associateWith{null},groups.associateWith{"Synthetic past periods"},null,ZoneId.of("UTC"))
            suspend fun view()=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),labs=db.dao().labs()),emptyList(),Instant.parse("2026-10-08T12:00:00Z"))
            val original=repo.records();val first=view()
            assertEquals(2,first.observed.size);assertTrue(first.importedHistory.isEmpty())
            assertEquals(listOf(2.0,3.0),first.projection.standards.map{it.standard.doses.single()})
            assertNotNull(first.events.single().displayPeriodKey);assertTrue(first.events.single().exactRegimenIds.isEmpty())
            assertTrue(db.dao().regimens().isEmpty());assertTrue(db.dao().rules().isEmpty())
            repo.importHrtTracker(plan,groups.associateWith{repo.medications().single().id},emptyMap(),null,ZoneId.of("UTC"))
            assertEquals(original,repo.records());assertEquals(first,view())
            val password="synthetic-password".toCharArray();repo.restoreBackup(repo.exportBackup(password),password)
            assertEquals(original,repo.records());assertEquals(first,view());assertTrue(db.dao().containers().isEmpty())
        } finally {db.close()}
    }

    @Test fun twiceDailyWithOccasionalMissingEntriesIsOnePeriodAfterImportReimportAndRestore()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            val events=(0..20).flatMap{day->(if(day%3==1)listOf(0) else listOf(0,10)).map{hour->
                """{"id":"synthetic-partial-$day-$hour","route":"sublingual","timeH":${480008+day*24+hour},"doseMG":2,"ester":"E2","extras":{"sublingualTier":2}}"""
            }}.joinToString(",")
            val export=HrtTracker.read("""{"meta":{"version":2},"events":[$events]}""")
            val groups=HrtTracker.preview(export).groups.keys;val plan=HrtTracker.plan(export,null)
            repo.importHrtTracker(plan,groups.associateWith{null},groups.associateWith{"Synthetic twice daily"},null,ZoneId.of("UTC"))
            suspend fun view()=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens()),emptyList(),Instant.parse("2026-10-08T12:00:00Z"))
            val original=repo.records();val first=view()
            assertEquals(1,first.observed.size);assertTrue(first.importedHistory.isEmpty())
            assertEquals(listOf(2.0,2.0),first.projection.standards.single().standard.doses)
            assertEquals(original.map{it.id}.toSet(),first.resolvedRecords.map{it.id}.toSet())
            repo.importHrtTracker(plan,groups.associateWith{repo.medications().single().id},emptyMap(),null,ZoneId.of("UTC"))
            assertEquals(original,repo.records());assertEquals(first,view())
            val password="synthetic-password".toCharArray();repo.restoreBackup(repo.exportBackup(password),password)
            assertEquals(original,repo.records());assertEquals(first,view())
            assertTrue(db.dao().regimens().isEmpty());assertTrue(db.dao().rules().isEmpty());assertTrue(db.dao().containers().isEmpty())
        } finally {db.close()}
    }

    @Test fun actualImportAndAppRecordShareCurrentPeriodAcrossOctoberSixAndRestore()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            val base=Instant.parse("2026-09-25T01:00:00Z");val cut=Instant.parse("2026-10-06T08:00:00Z")
            val events=(0..10).flatMap{day->listOf(0,12).map{hour->
                val time=(base.toEpochMilli()/3600000)+day*24+hour
                """{"id":"synthetic-continuity-$day-$hour","route":"sublingual","timeH":$time,"doseMG":2,"ester":"E2","extras":{"sublingualTier":2}}"""
            }}.joinToString(",")
            val export=HrtTracker.read("""{"meta":{"version":2},"events":[$events]}""")
            val groups=HrtTracker.preview(export).groups.keys;val plan=HrtTracker.plan(export,null)
            repo.importHrtTracker(plan,groups.associateWith{null},groups.associateWith{"Synthetic continued regimen"},null,ZoneId.of("Asia/Shanghai"))
            val medication=repo.medications().single();val firstRecord=repo.records().first()
            val d=RegimenDefinition(firstRecord.config_snapshot,"EVERY_N_HOURS",12,0,2.0,"UTC",null,cut.toEpochMilli(),emptyList())
            db.dao().regimen(RegimenVersionEntity(medication_id=medication.id,effective_from_utc=cut.toEpochMilli(),zone="UTC",definition_json=d.json(),clinical_signature=d.signature(),origin="APP",recorded_at_utc=cut.toEpochMilli()))
            db.dao().record(firstRecord.copy(id=0,origin="APP",source_record_key=null,taken_utc=cut.toEpochMilli(),taken_zone="UTC"))
            suspend fun view()=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens()),emptyList(),Instant.parse("2026-10-08T22:00:00Z"))
            val original=repo.records();val versions=db.dao().regimens();val first=view()
            assertEquals(1,first.projection.periods.size);assertEquals(1,first.projection.standards.size)
            assertEquals(original,first.resolvedRecords);assertTrue(first.importedHistory.isEmpty())
            repo.importHrtTracker(plan,groups.associateWith{medication.id},emptyMap(),null,ZoneId.of("Asia/Shanghai"))
            assertEquals(original,repo.records());assertEquals(first,view())
            val password="synthetic-password".toCharArray();repo.restoreBackup(repo.exportBackup(password),password)
            assertEquals(original,repo.records());assertEquals(versions,db.dao().regimens());assertEquals(first,view())
            assertTrue(db.dao().rules().isEmpty());assertTrue(db.dao().containers().isEmpty())
        } finally {db.close()}
    }

}
