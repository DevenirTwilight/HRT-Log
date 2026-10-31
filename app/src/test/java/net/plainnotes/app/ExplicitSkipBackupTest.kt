package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.timeline.*
import net.plainnotes.app.ui.adherence
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class ExplicitSkipBackupTest {
    @Test fun realSkipSurvivesEncryptedBackupWithoutIntakeStockOrPlanMutation()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            val now=Instant.parse("2026-10-31T07:00:00Z");val zone=ZoneId.of("UTC");val day=LocalDate.parse("2026-10-31")
            val med=MedicationEntity(name="Synthetic frozen E2",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=20.0,
                soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0)
            val id=repo.saveMedication(med,"E2",RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0),LocalTime.of(20,0)),emptySet(),now.minusSeconds(86400))
            repo.addContainers(id,20.0,1,true)
            val before=db.dao().regimens();val stock=repo.containers()
            val slot=repo.calendar(now,zone,day).single{it.slot.at==Instant.parse("2026-10-31T08:00:00Z")}.slot
            repo.override(slot,SlotOverride(slot.key,skipped=true),now)
            suspend fun projection()=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=db.dao().records(),regimens=db.dao().regimens(),regimenLinks=db.dao().regimenLinks()),emptyList(),now)
            val view=projection();val skip=repo.records().single{it.status=="SKIPPED"}
            assertEquals(listOf("skip:${skip.id}"),view.events.map{it.key});assertTrue(view.upcoming.isEmpty());assertTrue(view.importedHistory.isEmpty())
            assertNotNull(view.events.single().displayPeriodKey)
            val stats=adherence(repo.records());assertEquals(1,stats.skipped);assertEquals(0,stats.due);assertNull(stats.onTimeRate)
            val curve=ConcentrationCalculator.compute(listOf(med.copy(id=id)),mapOf(id to ProfileEntity(id,"E2","oral")),repo.records(),emptyList(),emptyList(),60.0,now,calibrate=false)
            assertEquals(0,curve.usedDoses);assertTrue(curve.e2.all{it==0.0})
            val password="synthetic-skip-backup".toCharArray()
            try { repo.restoreBackup(repo.exportBackup(password),password) } finally {password.fill('\u0000')}
            assertEquals(skip,repo.records().single{it.status=="SKIPPED"})
            assertEquals(view.events,projection().events);assertTrue(projection().importedHistory.isEmpty())
            assertEquals(before,db.dao().regimens());assertEquals(stock,repo.containers())
            assertEquals(1,repo.calendar(now,zone,day).filter{it.slot.at.atZone(zone).toLocalDate()==day}.count{it.state==SlotState.SKIPPED})
            assertEquals(1,repo.calendar(now,zone,day).filter{it.slot.at.atZone(zone).toLocalDate()==day}.count{it.state!=SlotState.SKIPPED})
            repo.undoSkipped(skip,now)
            assertTrue(projection().events.isEmpty());assertTrue(projection().importedHistory.isEmpty())
            assertEquals(stock,repo.containers());assertEquals(before,db.dao().regimens())
        } finally {db.close()}
    }
}
