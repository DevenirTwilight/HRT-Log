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
}
