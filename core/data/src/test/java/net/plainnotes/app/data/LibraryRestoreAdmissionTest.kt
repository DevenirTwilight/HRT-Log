package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LibraryRestoreAdmissionTest {
    private fun version():AttachmentBackupManifest.Version {
        val c=AttachmentObjectContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
        return AttachmentBackupManifest.Version(UUID.randomUUID(),1,c,AttachmentObjectStreams.Manifest(c,0,40,"0".repeat(64),"0".repeat(64)),"application/octet-stream")
    }
    private fun available(aliases:Int=512,cipher:Long=256L*1024*1024,free:Long=Long.MAX_VALUE)=LibraryRestoreAdmission.Available(aliases,cipher,free,4096)
    private fun deny(action:()->Unit) {try {action();fail("Unsafe admission accepted")}catch(_:IllegalArgumentException) {}}
    @Test fun allVersionsAndDatabaseAliasMustFitWithoutRaising512Limit() {
        val versions=List(256) {version()}
        deny {LibraryRestoreAdmission.admit(available(),versions,10,10,0)}
        assertEquals(255*4096L+8192,LibraryRestoreAdmission.admit(available(),versions.take(255),10,10,0))
    }
    @Test fun evenNoOriginalNeedsItsOwnDatabaseKeyAndDirectoryBudget() {
        deny {LibraryRestoreAdmission.admit(available(aliases=0),emptyList(),10,10,0)}
        deny {LibraryRestoreAdmission.admit(available(cipher=0),emptyList(),10,10,0)}
        assertEquals(8192L,LibraryRestoreAdmission.admit(available(aliases=1),emptyList(),10,10,0))
    }
    @Test fun oldAndFailedRetainedBytesCannotBeTreatedAsAvailableAgain() {
        deny {LibraryRestoreAdmission.admit(available(cipher=8192),listOf(version()),10,10,0)}
        assertEquals(12288L,LibraryRestoreAdmission.admit(available(cipher=12288),listOf(version()),10,10,0))
    }
    @Test fun jointSqlWalRootGrowthAndFloorArePartOfStorageAdmission() {
        val exact=12288L+LibraryRestoreAdmission.DATABASE_BYTES*3+60+32L*1024*1024
        deny {LibraryRestoreAdmission.admit(available(free=exact-1),listOf(version()),10,10,0)}
        assertEquals(12288L,LibraryRestoreAdmission.admit(available(free=exact),listOf(version()),10,10,0))
    }
    @Test fun retainedProvenanceCannotGrowWithoutBoundOrBeNegative() {
        deny {LibraryRestoreAdmission.admit(available(),emptyList(),1,1,LibraryRestoreAdmission.METADATA_BYTES)}
        deny {LibraryRestoreAdmission.admit(available(),emptyList(),-1,1,0)}
        deny {LibraryRestoreAdmission.admit(available(),emptyList(),1,1,-1)}
    }
}
