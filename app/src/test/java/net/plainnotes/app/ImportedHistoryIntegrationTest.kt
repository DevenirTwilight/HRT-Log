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
}
