package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** MED-04: user-entered file details travel in manifest version 3 only; every other manifest keeps its exact earlier bytes. */
class MedicalFileDetailManifestTest {
    private val dataset=UUID.randomUUID();private val epoch=UUID.randomUUID();private val vault=UUID.randomUUID()
    private fun version(document:UUID):AttachmentBackupManifest.Version {
        val c=AttachmentObjectContext(vault,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
        return AttachmentBackupManifest.Version(document,1,c,AttachmentObjectStreams.Manifest(c,100,MedicalArchiveCrypto.cipherLength(100),"a".repeat(64),"b".repeat(64)),"application/pdf")
    }
    private fun manifest(vararg detail:MedicalAttachmentOperations.Detail?):AttachmentBackupManifest {
        val ids=detail.map {UUID.randomUUID()};val versions=ids.map(::version)
        return AttachmentBackupManifest(dataset,epoch,vault,emptyList(),
            ids.mapIndexed {i,id->AttachmentBackupManifest.Document(id,1,"SYNTHETIC $i","REPORT","ACTIVE",versions[i].context.version,null,detail[i])},versions,emptyList())
    }
    private fun envelopes(m:AttachmentBackupManifest)=m.versions.associate {it.context.version to "00".repeat(60)}
    private fun encode(m:AttachmentBackupManifest)=String(MedicalArchiveManifest.encode(m,envelopes(m)),Charsets.UTF_8)
    private val detail=MedicalAttachmentOperations.Detail("LAB_REPORT","2026-10-07","某医院 \"A\" \\ B",3)

    @Test fun detailsRoundTripAsVersionThreeAndOnlyThen() {
        val m=manifest(detail,null,MedicalAttachmentOperations.Detail(null,null,null,2))
        val text=encode(m)
        assertTrue(text.startsWith("{\"version\":3,"))
        val decoded=MedicalArchiveManifest.decode(text.toByteArray(Charsets.UTF_8)).manifest
        assertEquals(m.documents.sortedBy {it.uuid.toString()}.map {it.detail}.toSet(),decoded.documents.map {it.detail}.toSet())
        assertEquals(m.documents.associate {it.uuid to it.detail},decoded.documents.associate {it.uuid to it.detail})
    }
    @Test fun withoutAnyDetailTheManifestKeepsItsEarlierVersionAndBytes() {
        val m=manifest(null,null)
        val text=encode(m)
        assertTrue(text,text.startsWith("{\"version\":2,")) // independent files (no links) were already version 2
        assertFalse(text.contains("\"detail\""))
        assertEquals(m.documents.map {it.detail},MedicalArchiveManifest.decode(text.toByteArray(Charsets.UTF_8)).manifest.documents.map {it.detail})
    }
    @Test fun versionThreeWithoutADetailAndDetailsInEarlierVersionsAreNotCanonical() {
        val three=encode(manifest(detail))
        val none=three.replace(Regex("\"detail\":\\{[^}]*\\}"),"\"detail\":null")
        assertThrows(Exception::class.java) {MedicalArchiveManifest.decode(none.toByteArray(Charsets.UTF_8))}
        val older=three.replaceFirst("{\"version\":3,","{\"version\":2,")
        assertThrows(Exception::class.java) {MedicalArchiveManifest.decode(older.toByteArray(Charsets.UTF_8))}
    }
    @Test fun invalidDetailsAreRefusedBeforeAnyDataIsExposed() {
        val good=encode(manifest(detail))
        fun refused(replacement:Pair<String,String>) {
            val broken=good.replace(replacement.first,replacement.second);assertNotEquals(good,broken)
            assertThrows(Exception::class.java) {MedicalArchiveManifest.decode(broken.toByteArray(Charsets.UTF_8))}
        }
        refused("\"LAB_REPORT\"" to "\"XRAY\"")
        refused("2026-10-07" to "2026-02-30")
        refused("2026-10-07" to "2026-1-7")
        refused("\"revision\":3}" to "\"revision\":0}")
        assertThrows(Exception::class.java) {MedicalAttachmentOperations.Detail(null,null,"x".repeat(81),1)}
        assertThrows(Exception::class.java) {MedicalAttachmentOperations.Detail(null,null,"  padded ",1)}
        assertThrows(Exception::class.java) {MedicalAttachmentOperations.Detail(null,null,"two\nlines",1)}
        assertThrows(Exception::class.java) {MedicalAttachmentOperations.Detail("XRAY",null,null,1)}
        assertThrows(Exception::class.java) {MedicalAttachmentOperations.Detail(null,"2026-02-29",null,1)}
        assertNull(MedicalAttachmentOperations.Detail.institutionOf("   "))
        assertEquals("某医院",MedicalAttachmentOperations.Detail.institutionOf("  某医院 "))
        assertThrows(Exception::class.java) {MedicalAttachmentOperations.Detail.institutionOf("x".repeat(81))}
    }
}
