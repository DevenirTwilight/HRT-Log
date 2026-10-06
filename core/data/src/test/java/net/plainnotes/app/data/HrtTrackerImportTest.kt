package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.importer.HrtTracker
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** Writing an HRT tracker plan against the real schema guards (synthetic data only). */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class HrtTrackerImportTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private val json = """{"meta":{"version":2},"weight":58,"events":[
        {"id":"a","route":"sublingual","timeH":490000,"doseMG":2,"ester":"E2","extras":{"sublingualTier":2}},
        {"id":"b","route":"sublingual","timeH":490012,"doseMG":2,"ester":"E2","extras":{"sublingualTier":2}},
        {"id":"c","route":"injection","timeH":490100,"doseMG":5,"ester":"EV","extras":{}}],
        "labResults":[{"id":"l","timeH":490050,"concValue":150,"unit":"pg/ml"}]}"""

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build(); db.openHelper.writableDatabase
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
    }
    @After fun close() = db.close()

    @Test fun createsReviewedMedicationsWithProfilesAndIsIdempotent() = runBlocking {
        val x = HrtTracker.read(json); val plan = HrtTracker.plan(x, null)
        val groups = HrtTracker.preview(x).groups.keys
        val s = repo.importHrtTracker(plan, groups.associateWith { null }, groups.associateWith { "Synthetic ${it.ester}" }, x.weightKg, ZoneId.of("UTC"))
        assertEquals(2, s.medications); assertEquals(3, s.intakes); assertEquals(1, s.labs); assertTrue(s.weightSet)
        val meds = repo.medications()
        assertTrue(meds.all { it.needs_review != null && it.notifications_on.not() })
        val sl = meds.single { it.route == "SUBLINGUAL" }
        val p = repo.profile(sl.id)!!; assertEquals("E2", p.ester); assertEquals("sublingual", p.pk_route); assertEquals(2, p.sl_tier)
        val records = repo.records()
        assertTrue(records.all { it.origin == "IMPORT_HT" && it.status == "ON_TIME" && it.unallocated_supply_amount == null && it.source_record_key!!.startsWith("ht:") })
        assertEquals(58.0, repo.weight()!!, 1e-9)
        // Second run maps onto the existing medications and skips what is already there.
        val again = repo.importHrtTracker(plan, groups.associateWith { g -> meds.single { it.route == g.route }.id }, emptyMap(), null, ZoneId.of("UTC"))
        assertEquals(0, again.medications); assertEquals(0, again.intakes); assertEquals(3, again.alreadyImported); assertEquals(0, again.labs)
    }
}
