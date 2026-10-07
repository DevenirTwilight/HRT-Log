package net.plainnotes.app

import kotlinx.coroutines.*
import net.plainnotes.app.data.MilestoneEntity
import net.plainnotes.app.timeline.MilestoneSaving
import org.junit.Assert.*
import org.junit.Test

class MilestoneSavingTest {
    private val draft=MilestoneEntity(date="2025-01-01",kind="STARTED")
    @Test fun doubleClickWhileWritingCommitsOnceAndOnlyThenAcknowledges()=runBlocking {
        val gate=CompletableDeferred<Unit>();var writes=0
        val saving=MilestoneSaving(this,{v,commit->writes++;gate.await();commit(v.copy(id=9))},{true})
        saving.save(draft);saving.save(draft);yield()
        assertTrue(saving.state.value.saving);assertNull(saving.state.value.saved);assertEquals(1,writes)
        gate.complete(Unit);yield()
        assertEquals(9L,saving.state.value.saved!!.id);assertFalse(saving.state.value.saving)
    }
    @Test fun failedTransactionIsNotReportedAsSaved()=runBlocking {
        val saving=MilestoneSaving(this,{_,_->error("Synthetic write failure")},{error("must not refresh")})
        saving.save(draft);yield()
        assertTrue(saving.state.value.failed);assertNull(saving.state.value.saved)
    }
    @Test fun committedWriteSurvivesBothRefreshAndPostCommitReminderErrors()=runBlocking {
        for(postCommitFailure in listOf(false,true)) {
            val saving=MilestoneSaving(this,{v,commit->commit(v.copy(id=9));if(postCommitFailure)error("Synthetic scheduling failure")},{false})
            saving.save(draft);yield()
            assertEquals(draft.copy(id=9),saving.state.value.saved);assertTrue(saving.state.value.refreshFailed);assertFalse(saving.state.value.failed)
        }
    }
}
