package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.conc.experimental.SlExclusion
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.experimental.ExperimentalPkState
import net.plainnotes.app.experimental.experimentalPkState
import net.plainnotes.app.pk.experimental.ExperimentalSlModelView
import net.plainnotes.app.reminder.ReminderCoordinator
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.*

/**
 * The navigation entry passes NotesViewModel.extra.records and state.ruleSnapshots to the experimental page.
 * Synthetic data through the real Room database, repository and view model: eligible history is read, nothing is written,
 * and editing the medication's current settings later does not change old results.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class) @LooperMode(LooperMode.Mode.PAUSED)
class ExperimentalPkDataPathTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private lateinit var model:NotesViewModel
    private val now=Instant.now()
    private val first=ExperimentalSlModelView.candidates.first().id
    private fun await(check:()->Boolean) {
        val deadline=System.nanoTime()+20_000_000_000L
        while(!check() && System.nanoTime()<deadline) { shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10) }
        assertTrue("State did not refresh: ${model.state.value.error}",check())
    }
    private fun medication(name:String,route:String)=MedicationEntity(name=name,molecule="E2",route=route,unit="MG",dose_per_intake=1.0,container_capacity=100.0,
        soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=false,sort_order=0)
    private fun save(m:MedicationEntity,pk:ProfileEntity)=runBlocking{repo.saveMedication(m,"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0)),emptySet(),now,pk=pk)}

    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        db.openHelper.writableDatabase
        repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
        model=NotesViewModel(repo,ReminderCoordinator(context,repo),context)
    }
    @After fun cleanup(){ androidx.lifecycle.ViewModelStore().apply{put("model",model);clear()};db.close() }

    private fun state(candidate:String=first)=experimentalPkState(model.extra.value.records,model.state.value.ruleSnapshots,now,candidate)

    @Test fun pageReadsEligibleHistoryThroughTheAppDataPathWithoutWriting() {
        val sl=save(medication("Synthetic SL E2","SUBLINGUAL"),ProfileEntity(0,"E2","sublingual",sl_tier=2))
        val oral=save(medication("Synthetic oral E2","ORAL"),ProfileEntity(0,"E2","oral"))
        runBlocking {
            repo.addContainers(sl,100.0,1,true);repo.addContainers(oral,100.0,1,true)
            repo.unscheduled(sl,now.minusSeconds(30*3600),1.0)
            repo.unscheduled(sl,now.minusSeconds(20*3600),0.5)
            repo.unscheduled(sl,now.minusSeconds(3*3600),1.0)
            repo.unscheduled(oral,now.minusSeconds(5*3600),2.0)
            repo.unscheduled(sl,now.minusSeconds(10*3600),4.0)
        }
        val deleted=runBlocking{db.dao().records().single{it.actual_dose==4.0}.id}
        runBlocking{repo.deleteRecord(deleted)}
        model.loadExtra()
        await{model.extra.value.records.size==4}
        // Deleted history never reaches the page: the DAO already filters it, and the adapter would reject it too.
        assertTrue(model.extra.value.records.none{it.id==deleted})

        fun allRows()=runBlocking{db.dao().records()+db.dao().deletedRecords()}
        val before=allRows()
        val ready=state() as ExperimentalPkState.Ready
        assertEquals(3,ready.view.consideredDoses)
        assertEquals(1,ready.audit.excluded[SlExclusion.NOT_SUBLINGUAL_E2])
        assertTrue(ready.view.m2Relative.all{it.isFinite() && it>=0.0} && ready.view.m2Relative.max()>0.0)
        // Every candidate reads the same records; nothing in the database changes.
        ExperimentalSlModelView.candidates.forEach{assertTrue(state(it.id) is ExperimentalPkState.Ready)}
        assertEquals(before,allRows())

        // The user later changes the sublingual tier of the current medication: old doses keep their frozen tier.
        save(model.state.value.medications.single{it.id==sl},ProfileEntity(sl,"E2","sublingual",sl_tier=3))
        model.loadExtra()
        await{runBlocking{db.dao().profile(sl)}?.sl_tier==3 && model.extra.value.records.size==4}
        val after=state() as ExperimentalPkState.Ready
        assertArrayEquals(ready.view.legacyRelative,after.view.legacyRelative,0.0)
        assertArrayEquals(ready.view.m2Relative,after.view.m2Relative,0.0)
        assertEquals(before.map{it.config_snapshot},allRows().map{it.config_snapshot})
    }
}
