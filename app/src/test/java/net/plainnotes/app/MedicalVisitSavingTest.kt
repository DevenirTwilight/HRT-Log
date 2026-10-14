package net.plainnotes.app
import kotlinx.coroutines.*
import net.plainnotes.app.medical.*
import net.plainnotes.app.data.*
import org.junit.Test
import org.junit.Assert.*
class MedicalVisitSavingTest {
 private val draft=MedicalVisitDraft(AppointmentEntity(type="ENDO",at_utc=0,at_zone="UTC",remind_minutes_before=0))
 @Test fun doubleClickAndRepeatedSaveAfterCommitWriteOnce()=runBlocking{var writes=0;val gate=CompletableDeferred<Unit>();val saving=MedicalVisitSaving(this,{d,c->writes++;gate.await();c(d.value.copy(id=1))},{true});saving.save(draft);saving.save(draft);yield();assertEquals(1,writes);assertTrue(saving.state.value.saving);gate.complete(Unit);yield();assertEquals(1L,saving.state.value.saved!!.id);saving.save(draft);yield();assertEquals(1,writes)}
 @Test fun transactionFailureRetainsDraftAndAllowsRetry()=runBlocking{var writes=0;val saving=MedicalVisitSaving(this,{d,c->writes++;if(writes==1)error("Synthetic");c(d.value.copy(id=1))},{true});saving.save(draft);yield();assertTrue(saving.state.value.failed);assertNull(saving.state.value.saved);saving.save(draft);yield();assertEquals(1L,saving.state.value.saved!!.id)}
 @Test fun postCommitErrorCannotTurnSuccessIntoDuplicateInsert()=runBlocking{var writes=0;val saving=MedicalVisitSaving(this,{d,c->writes++;c(d.value.copy(id=1));error("Synthetic post-commit reminder")},{false});saving.save(draft);yield();assertNotNull(saving.state.value.saved);assertFalse(saving.state.value.failed);assertTrue(saving.state.value.refreshFailed);saving.save(draft);yield();assertEquals(1,writes)}
}
