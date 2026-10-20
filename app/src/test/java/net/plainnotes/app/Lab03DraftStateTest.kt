package net.plainnotes.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Test

/** Actual retained-store contract, not a claim of native activity rotation or process-death verification. */
class Lab03DraftStateTest {
    private class Owner(override val viewModelStore:ViewModelStore):ViewModelStoreOwner
    @Test fun aRetainedStoreKeepsThirtyUnsavedRowsWhileANewStoreStartsEmpty() {
        val store=ViewModelStore();val owner=Owner(store)
        val first=ViewModelProvider(owner)[LabUiState::class.java]
        first.dialogs.labNew=true;first.draft.laboratory="SYNTHETIC unsaved lab"
        first.draft.rows.clear();repeat(30){i->first.draft.rows.add(LabBatchRow(i,"TC").apply{value=(i+1).toString();unit="g/L";note="SYNTHETIC unknown assay"})}
        val next=ViewModelProvider(Owner(store))[LabUiState::class.java]
        assertSame(first,next);assertEquals(30,next.draft.rows.size);assertEquals("30",next.draft.rows.last().value)
        assertEquals("SYNTHETIC unknown assay",next.draft.rows.last().note);assertFalse(next.draft.includeEstimate)
        val otherStore=ViewModelStore();val fresh=ViewModelProvider(Owner(otherStore))[LabUiState::class.java]
        assertNotSame(next,fresh);assertFalse(fresh.dialogs.labNew);assertEquals("",fresh.draft.laboratory)
        assertEquals(1,fresh.draft.rows.size);assertEquals("",fresh.draft.rows.single().value)
        store.clear();otherStore.clear()
    }
    @Test fun clearDropsSensitiveDraftAndInvalidatesOlderCallbacks() {
        val state=LabUiState();val old=state.draft;val token=state.token
        state.dialogs.labNew=true;state.dialogs.batchSaving=true;old.laboratory="SYNTHETIC";old.rows[0].value="210";old.rows[0].note="SYNTHETIC report";old.includeEstimate=true
        state.clear()
        assertNotSame(token,state.token);assertNotSame(old,state.draft);assertTrue(old.rows.isEmpty());assertEquals("",old.laboratory)
        assertFalse(state.dialogs.labNew);assertFalse(state.dialogs.batchSaving);assertFalse(state.draft.includeEstimate)
        assertEquals("",state.draft.rows.single().value)
    }
}
