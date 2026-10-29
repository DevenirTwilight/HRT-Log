package net.plainnotes.app.attachments.ui

import net.plainnotes.app.attachments.AttachmentReconciliationStore
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.data.LibraryRequestScope
import net.plainnotes.app.data.MedicalAttachmentOperations
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
class AttachmentReconciliationStoreTest {
    @Test fun onlyNeutralIdentitiesSurviveReconstructionAndCannotCrossScopeOrTypedTarget() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val scope=LibraryRequestScope("LEGACY",UUID.randomUUID().toString(),UUID.randomUUID().toString())
        val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB,1)
        val a=AttachmentReconciliationStore(context,scope,target)
        val operation=UUID.randomUUID();val document=UUID.randomUUID()
        a.retain(operation,document)
        val restored=AttachmentReconciliationStore(context,scope,target)
        assertEquals(operation to document,restored.pending())
        assertNull(AttachmentReconciliationStore(context,scope.copy(epoch=UUID.randomUUID().toString()),target).pending())
        assertNull(AttachmentReconciliationStore(context,scope,target.copy(kind=MedicalAttachmentOperations.Kind.APPOINTMENT)).pending())
        try {restored.retain(UUID.randomUUID(),UUID.randomUUID());fail("Overwrote unresolved identity")}catch(_:IllegalStateException){}
        restored.resolved(operation,document)
        assertNull(a.pending())
    }
    @Test fun wipeClearsEveryScopeAndRejectsLateCheckpointFromOldOwner() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val scope=LibraryRequestScope("LEGACY",UUID.randomUUID().toString(),UUID.randomUUID().toString())
        val target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.LAB,1)
        val a=AttachmentReconciliationStore(context,scope,target)
        val b=AttachmentReconciliationStore(context,scope.copy(epoch=UUID.randomUUID().toString()),target)
        a.retain(UUID.randomUUID(),UUID.randomUUID());b.retain(UUID.randomUUID(),UUID.randomUUID())
        val prefs=context.getSharedPreferences("attachment-reconciliation-v1",Context.MODE_PRIVATE)
        AttachmentReconciliationStore.clearAll(prefs)
        assertTrue(prefs.all.isEmpty())
        try {a.retain(UUID.randomUUID(),UUID.randomUUID());fail("Old request recreated wiped identity")}catch(_:IllegalStateException){}
        val fresh=AttachmentReconciliationStore(context,scope,target)
        assertNull(fresh.pending())
        val pair=UUID.randomUUID() to UUID.randomUUID()
        fresh.retain(pair.first,pair.second)
        assertEquals(pair,fresh.pending())
    }
    @Test fun importRelationAndLifecycleKeepIndependentNeutralCheckpointsAndAllObeyWipe() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val scope=LibraryRequestScope("LEGACY",UUID.randomUUID().toString(),UUID.randomUUID().toString())
        val stores=listOf(AttachmentReconciliationStore(context,scope,null),AttachmentReconciliationStore(context,scope,null,"RELATION"),AttachmentReconciliationStore(context,scope,null,"LIFECYCLE"),AttachmentReconciliationStore(context,scope,null,"FILE_INFO"))
        val pairs=List(4){UUID.randomUUID() to UUID.randomUUID()}
        stores.forEachIndexed {i,store->store.retain(pairs[i].first,pairs[i].second)}
        assertEquals(pairs,stores.map {it.pending()})
        assertEquals(pairs[1],AttachmentReconciliationStore(context,scope,null,"RELATION").pending())
        assertEquals(pairs[3],AttachmentReconciliationStore(context,scope,null,"FILE_INFO").pending())
        assertNull(AttachmentReconciliationStore(context,scope.copy(epoch=UUID.randomUUID().toString()),null,"FILE_INFO").pending())
        val prefs=context.getSharedPreferences("attachment-reconciliation-v1",Context.MODE_PRIVATE)
        assertTrue(prefs.all.values.all {it is String && it.matches(Regex("[0-9a-f-]{36}\\|[0-9a-f-]{36}"))})
        AttachmentReconciliationStore.clearAll(prefs)
        stores.forEachIndexed {i,store->assertNull(store.pending());try {store.retain(pairs[i].first,pairs[i].second);fail("Late old owner rewrote wiped neutral identity")}catch(_:IllegalStateException){}}
    }

}
