package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.*
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.TimeZone

/** Real Room guards and real timeline; synthetic fixture only, shared by JVM and API35. */
abstract class UndoSkippedContract {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private lateinit var previousZone:TimeZone
    private val dao get()=db.dao()
    private val zone=ZoneId.of("UTC")
    private val day=LocalDate.parse("2026-03-10")
    private fun at(hour:Int,days:Long=0)=day.plusDays(days).atTime(hour,0).atZone(zone).toInstant()
    private val med=MedicationEntity(name="R15 Synthetic",molecule="E2",route="ORAL",unit="MG",dose_per_intake=2.0,container_capacity=20.0,
        soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    @Before fun open() { previousZone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));openDb() }
    private fun openDb(name:String?=null) {
        val c=ApplicationProvider.getApplicationContext<Context>()
        db=(if(name==null)Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java) else Room.databaseBuilder(c,NotesDatabase::class.java,name))
            .allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})
    }
    @After fun close() { db.close();TimeZone.setDefault(previousZone) }
    private suspend fun seed():Slot {
        val id=repo.saveMedication(med,null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0),LocalTime.of(20,0)),emptySet(),at(7))
        repo.addContainers(id,20.0,1,true)
        return repo.calendar(at(7),zone,day).single{it.slot.at==at(8)}.slot
    }
    private suspend fun skip(change:(Slot)->SlotOverride={SlotOverride(it.key,skipped=true)}):RecordEntity {
        val s=seed();repo.override(s,change(s),at(7));return repo.records().single{it.slot_key==s.key}
    }
    private suspend fun state(r:RecordEntity,now:Instant)=repo.calendar(now,zone,day).single{it.slot.key==r.slot_key}
    private suspend fun rejected(block:suspend()->Unit) { try { block();fail("Unsafe/stale undo must reject") } catch(_:IllegalStateException) {} }
    private suspend fun legacy(record:RecordEntity):RecordEntity {
        val context=org.json.JSONObject(record.config_snapshot);context.remove("skip_original_slot_v1")
        dao.updateRecord(record.copy(config_snapshot=context.toString()));return dao.recordById(record.id)!!
    }
    private suspend fun snapshot()=listOf(dao.records(),dao.deletedRecords(),dao.overrides(),dao.retained(),dao.containers(),dao.voidedContainers())
    @Test fun todayUndoReturnsToPendingWithoutConsumption()=runBlocking {
        val r=skip();val boxes=repo.containers();repo.undoSkipped(r,at(7).plusSeconds(60))
        assertEquals(SlotState.PENDING,state(r,at(7).plusSeconds(60)).state)
        assertEquals(boxes,repo.containers());assertTrue(dao.supplyFor(r.id).isEmpty());assertTrue(repo.records().none{it.status in listOf("ON_TIME","LATE")})
        val old=dao.recordById(r.id)!!;assertNotNull(old.deleted_at_utc);assertEquals(r.revision+1,old.revision);assertEquals(r.config_snapshot,old.config_snapshot)
    }
    @Test fun currentTimeDeterminesSoonAndOverdue()=runBlocking {
        val r=skip();repo.undoSkipped(r,at(8).minusSeconds(5*60));assertEquals(SlotState.SOON,state(r,at(8).minusSeconds(5*60)).state)
        assertEquals(SlotState.OVERDUE,state(r,at(10)).state)
    }
    @Test fun pastUndoCreatesOnlyUnconfirmedMissedAndKeepsSkipEvidence()=runBlocking {
        val r=skip();repo.undoSkipped(r,at(10,1))
        val replacement=dao.records().single{it.slot_key==r.slot_key};assertEquals("MISSED",replacement.status);assertEquals("AUTO_MISSED",replacement.origin)
        assertNull(replacement.actual_dose);assertNull(replacement.taken_utc);assertTrue(dao.supplyFor(replacement.id).isEmpty())
        assertNotNull(dao.recordById(r.id)!!.deleted_at_utc);assertEquals(SlotState.UNCONFIRMED,state(r,at(10,1)).state)
    }
    @Test fun doseOverrideIsKept()=runBlocking {
        val r=skip{SlotOverride(it.key,dose=3.0,skipped=true)};repo.undoSkipped(r,at(7))
        assertEquals(3.0,state(r,at(7)).slot.dose,0.0);assertEquals(3.0,repo.currentOverride(r.slot_key!!).dose!!,0.0);assertFalse(repo.currentOverride(r.slot_key).skipped)
    }
    @Test fun rescheduleAndDoseRemainAfterUndo()=runBlocking {
        val r=skip{SlotOverride(it.key,at(10),zone,3.0,true)};repo.undoSkipped(r,at(7))
        val s=state(r,at(7)).slot;assertEquals(at(8),s.original);assertEquals(at(10),s.at);assertEquals(3.0,s.dose,0.0)
        assertEquals(at(10),repo.currentOverride(r.slot_key!!).rescheduled)
    }
    @Test fun savedPlanAndSnapshotsSurviveLaterPlanChange()=runBlocking {
        val r=skip();val id=r.medication_id
        repo.saveMedication(med.copy(id=id,dose_per_intake=4.0),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(18,0)),emptySet(),at(9))
        val rules=dao.rules();val regimens=dao.regimens();repo.undoSkipped(r,at(10))
        val restored=state(r,at(10));assertEquals(r.rule_version_id,restored.slot.ruleId);assertEquals(2.0,restored.slot.dose,0.0);assertEquals(at(8),restored.slot.at)
        assertEquals(rules,dao.rules());assertEquals(regimens,dao.regimens());assertEquals(r.config_snapshot,dao.recordById(r.id)!!.config_snapshot)
    }
    @Test fun realIntakeCannotBeUndoneAndStockIsUntouched()=runBlocking {
        val s=seed();repo.complete(s,at(8),2.0);val r=repo.records().single{it.slot_key==s.key};val before=snapshot();val ledger=dao.supplyFor(r.id)
        rejected{repo.undoSkipped(r,at(10))};assertEquals(before,snapshot());assertEquals(ledger,dao.supplyFor(r.id))
    }
    @Test fun autoAndConfirmedMissedCannotBeUndone()=runBlocking {
        seed();repo.calendar(at(10,1),zone,day);val r=repo.records().first{it.unconfirmed};val before=snapshot()
        rejected{repo.undoSkipped(r,at(10,1))};assertEquals(before,snapshot())
        repo.confirmMissed(r.id);val confirmed=dao.recordById(r.id)!!;val saved=snapshot();rejected{repo.undoSkipped(confirmed,at(10,1))};assertEquals(saved,snapshot())
    }
    @Test fun repeatedUndoRejectsWithoutFurtherWrites()=runBlocking {
        val r=skip();repo.undoSkipped(r,at(7));val before=snapshot();rejected{repo.undoSkipped(r,at(7).plusSeconds(1))};assertEquals(before,snapshot())
    }
    @Test fun staleSkipRevisionIsRejected()=runBlocking {
        val r=skip();val s=state(r,at(7)).slot;repo.override(s,SlotOverride(s.key,dose=3.0,skipped=true),at(7));val before=snapshot()
        rejected{repo.undoSkipped(r,at(7))};assertEquals(before,snapshot())
    }
    @Test fun staleUndoCannotOverwriteSubsequentIntake()=runBlocking {
        val r=skip();repo.undoSkipped(r,at(7));repo.complete(state(r,at(7)).slot,at(8),2.0);val before=snapshot()
        rejected{repo.undoSkipped(r,at(10))};assertEquals(before,snapshot());assertEquals(2.0,repo.containers().single().used_amount,0.0)
    }
    @Test fun writeFailureRollsBackOverrideRecordAndRetention()=runBlocking {
        val r=skip();db.openHelper.writableDatabase.execSQL("DELETE FROM retained_slot");val before=snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_r15_failure BEFORE UPDATE ON dose_record WHEN OLD.id=${r.id} BEGIN SELECT RAISE(ABORT,'synthetic transaction failure'); END")
        try { repo.undoSkipped(r,at(7));fail("Fault injection must abort") } catch(e:Exception) { assertFalse(e is UnsupportedOperationException) }
        assertEquals(before,snapshot())
    }
    @Test fun otherDatesAndRealRecordsAreNotWritten()=runBlocking {
        val r=skip();val other=repo.calendar(at(7),zone,day).first{it.slot.at==at(20)}.slot;repo.complete(other,at(7),2.0)
        val records=dao.records().filter{it.id!=r.id};val ledger=records.associate{it.id to dao.supplyFor(it.id)};repo.undoSkipped(r,at(7))
        assertEquals(records,dao.records().filter{it.id!=r.id});assertEquals(ledger,records.associate{it.id to dao.supplyFor(it.id)})
    }
    @Test fun cannotFabricateUnknownHistoricalSlot()=runBlocking {
        val r=skip();val key="wall:${r.rule_version_id}@not-a-slot";val o=dao.overrides().single();dao.deleteOverride(o.slot_key)
        dao.insertOverride(o.copy(id=0,slot_key=key));dao.updateRecord(r.copy(slot_key=key,revision=r.revision+1));val changed=dao.recordById(r.id)!!;val before=snapshot()
        rejected{repo.undoSkipped(changed,at(7))};assertEquals(before,snapshot())
    }
    @Test fun missingManualOverrideIsNotInvented()=runBlocking {
        val r=skip();dao.deleteOverride(r.slot_key!!);val before=snapshot();rejected{repo.undoSkipped(r,at(7))};assertEquals(before,snapshot())
    }
    @Test fun subsequentNormalRecordDeductsOnlyOnce()=runBlocking {
        val r=skip();repo.undoSkipped(r,at(7));val s=state(r,at(7)).slot;repo.complete(s,at(8),2.0);repo.complete(s,at(8),2.0)
        assertEquals(2.0,repo.containers().single().used_amount,0.0);assertEquals(1,dao.records().count{it.slot_key==r.slot_key});assertEquals(1,dao.supplyFor(dao.records().single{it.slot_key==r.slot_key}.id).size)
    }
    @Test fun skippedStateStaysUndoneAfterDatabaseReopen()=runBlocking {
        db.close();val name="r15-synthetic-${java.util.UUID.randomUUID()}.db";openDb(name)
        try { val r=skip();repo.undoSkipped(r,at(7));db.close();openDb(name)
            assertNotNull(dao.recordById(r.id)!!.deleted_at_utc);assertFalse(repo.currentOverride(r.slot_key!!).skipped);assertEquals(SlotState.PENDING,state(r,at(7)).state)
        } finally { db.close();ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name) }
    }
    @Test fun legacyUnmovedSkipWithoutRetentionUsesItsSavedZone()=runBlocking {
        val r=legacy(skip());db.openHelper.writableDatabase.execSQL("DELETE FROM retained_slot")
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));repo.undoSkipped(r,at(7))
        assertEquals(at(8),repo.calendar(at(7),ZoneId.of("Europe/Paris"),day).single{it.slot.key==r.slot_key}.slot.at)
        assertEquals("UTC",dao.retained().single{it.slot_key==r.slot_key}.zone)
    }
    @Test fun legacyWallMoveWithoutOriginalEvidenceRejectsSafely()=runBlocking {
        val r=legacy(skip{SlotOverride(it.key,at(10),zone,3.0,true)});db.openHelper.writableDatabase.execSQL("DELETE FROM retained_slot")
        val before=snapshot();rejected{repo.undoSkipped(r,at(7))};assertEquals(before,snapshot())
    }
    @Test fun futureSlotCancelledByPlanChangeIsNotRecreated()=runBlocking {
        val first=seed();val future=repo.calendar(at(7),zone,day).single{it.slot.at==at(20)}.slot
        repo.override(future,SlotOverride(future.key,skipped=true),at(7));val r=repo.records().single{it.slot_key==future.key}
        repo.saveMedication(med.copy(id=first.medicationId),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(18,0)),emptySet(),at(9))
        val before=snapshot();rejected{repo.undoSkipped(r,at(10))};assertEquals(before,snapshot())
    }
    @Test fun voidedPackageAndLedgerNeverParticipateInUndoOrLaterIntake()=runBlocking {
        val r=skip();repo.addContainers(r.medication_id,20.0,1,true);val fake=repo.containers().maxBy{it.id}
        repo.correctStock(fake.id,2.0);repo.removeMistakenContainer(fake.id);val hidden=dao.container(fake.id);val ledger=dao.supplyForContainer(fake.id)
        val real=repo.containers();repo.undoSkipped(r,at(7));assertEquals(real,repo.containers());assertEquals(hidden,dao.container(fake.id));assertEquals(ledger,dao.supplyForContainer(fake.id))
        repo.complete(state(r,at(7)).slot,at(8),2.0);assertEquals(hidden,dao.container(fake.id));assertEquals(ledger,dao.supplyForContainer(fake.id))
        assertEquals(2.0,repo.containers().single().used_amount,0.0)
    }
    @Test fun forecastDemandReturnsButActualInventoryDoesNotChange()=runBlocking {
        val r=skip();val before=repo.containers();assertTrue(repo.stockDemand(at(7),zone).none{it.slot.key==r.slot_key})
        repo.undoSkipped(r,at(7));assertEquals(before,repo.containers());assertEquals(1,repo.stockDemand(at(7),zone).count{it.slot.key==r.slot_key})
    }
    @Test fun encryptedBackupKeepsCorrectionEvidenceAndProjection()=runBlocking {
        val r=skip();repo.undoSkipped(r,at(10,1));val before=snapshot();val password="r15-synthetic-backup".toCharArray()
        repo.restoreBackup(repo.exportBackup(password),password);assertEquals(before,snapshot())
        assertEquals(SlotState.UNCONFIRMED,state(r,at(10,1)).state);assertEquals("SKIPPED",dao.recordById(r.id)!!.status)
        assertNotNull(dao.recordById(r.id)!!.deleted_at_utc)
    }

    @Test fun failedMissedInsertionRollsBackTheWholeUndo()=runBlocking {
        val r=skip();val before=snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_r15_reconcile_failure BEFORE INSERT ON dose_record WHEN NEW.origin='AUTO_MISSED' BEGIN SELECT RAISE(ABORT,'synthetic reconcile failure'); END")
        try { repo.undoSkipped(r,at(10,1));fail("Reconcile write must abort") } catch(e:Exception) { assertFalse(e is UnsupportedOperationException) }
        assertEquals(before,snapshot())
    }
    @Test fun overrideChangedWithoutMatchingRecordIsRejected()=runBlocking {
        val r=skip();val o=dao.overrides().single();dao.updateOverride(o.copy(dose_override=3.0));val before=snapshot()
        rejected{repo.undoSkipped(r,at(7))};assertEquals(before,snapshot())
    }

    @Test fun undoThenFuturePlanChangeDoesNotCreateGhostDemand()=runBlocking {
        val r=skip();repo.undoSkipped(r,at(7))
        repo.saveMedication(med.copy(id=r.medication_id),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(18,0)),emptySet(),at(7).plusSeconds(30*60))
        assertTrue(repo.calendar(at(7).plusSeconds(31*60),zone,day).none{it.slot.key==r.slot_key})
        assertTrue(repo.stockDemand(at(7).plusSeconds(31*60),zone).none{it.slot.key==r.slot_key})
        assertNotNull(dao.recordById(r.id)!!.deleted_at_utc)
    }
    @Test fun realEarlyIntakeSurvivesTheSameFuturePlanCut()=runBlocking {
        val r=skip();repo.undoSkipped(r,at(7));repo.complete(state(r,at(7)).slot,at(7),2.0)
        repo.saveMedication(med.copy(id=r.medication_id),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(18,0)),emptySet(),at(7).plusSeconds(30*60))
        val real=repo.calendar(at(7).plusSeconds(31*60),zone,day).single{it.slot.key==r.slot_key}
        assertEquals(SlotState.ON_TIME,real.state);assertEquals(at(8),real.slot.at);assertEquals(2.0,repo.containers().single().used_amount,0.0)
    }

    @Test fun legacyMovedRetentionWithoutOriginalProofIsNotTrusted()=runBlocking {
        val r=skip{SlotOverride(it.key,at(10),zone,3.0,true)}
        val context=org.json.JSONObject(r.config_snapshot);context.remove("skip_original_slot_v1")
        dao.updateRecord(r.copy(config_snapshot=context.toString()));val legacy=dao.recordById(r.id)!!;val before=snapshot()
        rejected{repo.undoSkipped(legacy,at(7))};assertEquals(before,snapshot())
    }
    @Test fun differentInventoryUnitsDoNotReinterpretTheHistoricalDose()=runBlocking {
        val r=skip();repo.saveMedication(med.copy(id=r.medication_id,unit="UG"),null,RuleKind.EVERY_N_DAYS,1,
            listOf(LocalTime.of(8,0),LocalTime.of(20,0)),emptySet(),at(9))
        val before=snapshot();rejected{repo.undoSkipped(r,at(10))};assertEquals(before,snapshot())
    }

    @Test fun originalProofRestoresMovedSkipWithoutDerivedRetention()=runBlocking {
        val r=skip{SlotOverride(it.key,at(10),zone,3.0,true)};db.openHelper.writableDatabase.execSQL("DELETE FROM retained_slot")
        repo.undoSkipped(r,at(7));val restored=state(r,at(7)).slot
        assertEquals(at(8),restored.original);assertEquals(at(10),restored.at);assertEquals(3.0,restored.dose,0.0)
    }
    @Test fun timezoneReplanPreservesCapturedOriginAndDoesNotUncancelFutureSlot()=runBlocking {
        val id=repo.saveMedication(med,null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(20,0)),emptySet(),at(7,-1))
        val slot=repo.calendar(at(7),zone,day).single{it.slot.at==at(20)}.slot
        repo.override(slot,SlotOverride(slot.key,at(14),zone,3.0,true),at(7));val r=repo.records().single{it.slot_key==slot.key}
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        repo.saveMedication(med.copy(id=id),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(18,0)),emptySet(),at(9))
        assertEquals(at(20).toEpochMilli(),dao.retained().single{it.slot_key==r.slot_key}.original_utc)
        val before=snapshot();rejected{repo.undoSkipped(r,at(10))};assertEquals(before,snapshot())
    }
    @Test fun movedPastSlotKeepsObservedTimezoneAcrossReplanAndUndo()=runBlocking {
        val id=repo.saveMedication(med,null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(8,0)),emptySet(),at(7,-1))
        val slot=repo.calendar(at(7),zone,day).single{it.slot.at==at(8)}.slot
        repo.override(slot,SlotOverride(slot.key,at(14),zone,3.0,true),at(7));val r=repo.records().single{it.slot_key==slot.key}
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        repo.saveMedication(med.copy(id=id),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(18,0)),emptySet(),at(9))
        repo.undoSkipped(r,at(10));val restored=repo.calendar(at(10),ZoneId.of("Asia/Tokyo"),day).single{it.slot.key==r.slot_key}.slot
        assertEquals(at(8),restored.original);assertEquals(at(14),restored.at);assertEquals(zone,restored.originalZone)
        assertEquals(3.0,restored.dose,0.0)
    }

    @Test fun legacyUndoAlsoGetsScopedEvidenceAndCannotCreateGhostDemand()=runBlocking {
        val r=legacy(skip());repo.undoSkipped(r,at(7))
        val evidence=org.json.JSONObject(dao.recordById(r.id)!!.config_snapshot).getJSONObject("skip_original_slot_v1")
        assertEquals(r.slot_key,evidence.getString("key"));assertEquals(at(8).toEpochMilli(),evidence.getLong("original_utc"))
        repo.saveMedication(med.copy(id=r.medication_id),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.of(18,0)),emptySet(),at(7).plusSeconds(30*60))
        assertTrue(repo.stockDemand(at(7).plusSeconds(31*60),zone).none{it.slot.key==r.slot_key})
    }

}
