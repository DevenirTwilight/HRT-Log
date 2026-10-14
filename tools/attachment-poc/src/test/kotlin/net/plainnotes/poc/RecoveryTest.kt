package net.plainnotes.poc

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class RecoveryTest {
    @Test fun portablePasswordAcrossDeviceWrapsAndWrongPassword() {
        val content=ByteArray(32){it.toByte()};val context="synthetic-space/object/version".toByteArray()
        val device1=SecretKeySpec(ByteArray(32){1},"AES");val device2=SecretKeySpec(ByteArray(32){2},"AES")
        val old=KeyEnvelope.wrap(device1,context,content)
        val portable=KeyEnvelope.portable("synthetic password".toCharArray(),context,KeyEnvelope.unwrap(device1,context,old))
        val restored=KeyEnvelope.restore("synthetic password".toCharArray(),context,portable)
        assertArrayEquals(content,KeyEnvelope.unwrap(device2,context,KeyEnvelope.wrap(device2,context,restored)))
        assertThrows(javax.crypto.AEADBadTagException::class.java){KeyEnvelope.restore("wrong password".toCharArray(),context,portable)}
        assertThrows(javax.crypto.AEADBadTagException::class.java){KeyEnvelope.restore("synthetic password".toCharArray(),"other space".toByteArray(),portable)}
        restored.fill(0)
    }
    @Test fun envelopeCorruptionAndWrongDeviceKeyReject() {
        val key=SecretKeySpec(ByteArray(32){3},"AES");val value=KeyEnvelope.wrap(key,byteArrayOf(1),ByteArray(32))
        assertThrows(javax.crypto.AEADBadTagException::class.java){KeyEnvelope.unwrap(SecretKeySpec(ByteArray(32){4},"AES"),byteArrayOf(1),value)}
        value[value.lastIndex]=(value.last().toInt() xor 1).toByte()
        assertThrows(javax.crypto.AEADBadTagException::class.java){KeyEnvelope.unwrap(key,byteArrayOf(1),value)}
    }
    @Test fun interruptedRestoreNeverChangesActiveGeneration() {
        val m=RecoveryModel();m.add(RecoveryModel.Entry(UUID.randomUUID(),"old",null));val old=m.all();val gen=m.generation
        val incoming=(0..5).map{RecoveryModel.Entry(UUID.randomUUID(),"new$it",null)}
        for(i in 0..incoming.size) {
            assertThrows(IllegalStateException::class.java){m.restore(incoming,m.store,m.recordEpoch){if(it==i)error("Synthetic interrupted restore")}}
            assertEquals(old,m.all());assertEquals(gen,m.generation)
        }
        m.restore(incoming,m.store,m.recordEpoch);assertEquals(incoming.toSet(),m.all().toSet());assertNotEquals(gen,m.generation)
    }
    @Test fun oldBackupSameNumericRecordIdCannotRelinkAttachment() {
        val m=RecoveryModel();val e=RecoveryModel.Entry(UUID.randomUUID(),"hash",RecoveryModel.Link(7,m.recordEpoch));m.add(e)
        m.oldRecordOnlyRestore();assertNotEquals(e.link!!.recordEpoch,m.recordEpoch);assertNull(m.all().single().link)
        assertThrows(IllegalArgumentException::class.java){m.add(e.copy(id=UUID.randomUUID(),hash="other"))}
    }
    @Test fun trashRestoreRevisionAndDuplicateImportModel() {
        val m=RecoveryModel();val e=RecoveryModel.Entry(UUID.randomUUID(),"hash",null);assertEquals(e.id,m.add(e));assertEquals(e.id,m.add(e.copy(id=UUID.randomUUID())))
        val trash=m.revise(e,true);assertTrue(m.all().single().trashed)
        assertThrows(IllegalStateException::class.java){m.revise(e,false)}
        val restored=m.revise(trash,false);assertFalse(restored.trashed);assertEquals(2,restored.revision)
    }
    @Test fun crossSpaceRestoreAndDuplicateObjectIdsRejected() {
        val m=RecoveryModel();val e=RecoveryModel.Entry(UUID.randomUUID(),"hash",null)
        assertThrows(IllegalArgumentException::class.java){m.restore(listOf(e),UUID.randomUUID(),m.recordEpoch)}
        assertThrows(IllegalArgumentException::class.java){m.restore(listOf(e,e),m.store,m.recordEpoch)}
        assertTrue(m.all().isEmpty())
    }
    @Test fun thousandMetadataRowsPageWithoutDecryptingOrCreatingPreviews() {
        val m=RecoveryModel();repeat(1000){m.add(RecoveryModel.Entry(UUID(0,it.toLong()),"hash$it",null))}
        val pages=(0 until 1000 step 20).flatMap{m.page(it)}
        assertEquals(1000,pages.size);assertEquals(1000,pages.map{it.id}.distinct().size);assertEquals(m.all(),pages);assertTrue(m.page(1000).isEmpty())
    }
}
