package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Synthetic, isolated candidate only. No production archive parser or database activation. */
abstract class AttachmentBackupCandidateContract {
    private class Fixture:AutoCloseable {
        val password="synthetic candidate password".toCharArray()
        val dataset=UUID.randomUUID();val epoch=UUID.randomUUID();val vault=UUID.randomUUID()
        val session=AttachmentSession(dataset,epoch)
        val record=UUID.randomUUID();val lab=UUID.randomUUID();val first=UUID.randomUUID();val trashed=UUID.randomUUID()
        val plain=listOf("%PDF synthetic multi-page fixture\n%%EOF".toByteArray(),byteArrayOf(1,4,9,16),ByteArray(65589) {(it*7).toByte()})
        val members=plain.mapIndexed {index,bytes->
            val c=AttachmentObjectContext(vault,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
            val key=ByteArray(32).also(java.security.SecureRandom()::nextBytes);val output=ByteArrayOutputStream()
            val manifest=AttachmentObjectStreams.write(session.issue(),c,key.copyOf(),ByteArrayInputStream(bytes),output,AttachmentObjectStreams.MAXIMUM_BYTES)
            DraftMedicalArchive.Member(AttachmentBackupManifest.Version(if(index<2)first else trashed,if(index<2)index+1L else 1L,c,manifest,
                if(index==0)"application/pdf" else "application/octet-stream"),output.toByteArray(),key)
        }
        val manifest=AttachmentBackupManifest(dataset,epoch,vault,
            listOf(AttachmentBackupManifest.Record(record,AttachmentBackupManifest.Kind.APPOINTMENT,7,"ACTIVE",null,"a".repeat(64)),
                AttachmentBackupManifest.Record(lab,AttachmentBackupManifest.Kind.LAB,7,"TRASHED",1,"b".repeat(64))),
            listOf(AttachmentBackupManifest.Document(first,2,"Synthetic report","REPORT","ACTIVE",members[1].version.context.version,null),
                AttachmentBackupManifest.Document(trashed,3,"Synthetic retained original","OTHER","TRASHED",members[2].version.context.version,1000)),
            members.map {it.version},
            listOf(AttachmentBackupManifest.Link(UUID.randomUUID(),record,first,1,"ACTIVE"),AttachmentBackupManifest.Link(UUID.randomUUID(),lab,trashed,2,"QUARANTINED")))
        val records=JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",RecordBackupCompatibility.SCHEMA).put("tables",JSONObject()
            .put("appointment",JSONArray().put(JSONObject().put("id",7).put("note","Synthetic")))
            .put("trash_item",JSONArray().put(JSONObject().put("id",1).put("kind","LAB").put("payload_json","synthetic candidate only"))))
        val targets=listOf(AttachmentBackupManifest.Target(record,AttachmentBackupManifest.Kind.APPOINTMENT,77),AttachmentBackupManifest.Target(lab,AttachmentBackupManifest.Kind.LAB,77))
        fun packet()=DraftMedicalArchive.pack(manifest,records,members,password)
        override fun close() {members.forEach {it.syntheticDek.fill(0)};password.fill('\u0000');session.revoke()}
    }
    private fun reject(block:()->Unit) {try {block();fail("Incomplete or invalid candidate accepted")}catch(_:Exception){}}
    @Test fun candidateNewDeviceRoundTripPreservesEveryVersionTrashLinkAndOriginalHashWithoutSourceKeys() {
        Fixture().use {f->val packet=f.packet();f.members.forEach {it.syntheticDek.fill(0)}
            val a=DraftMedicalArchive.Device();val b=DraftMedicalArchive.Device()
            try {
                val first=DraftMedicalArchive.restore(packet,f.password,a,f.targets);val second=DraftMedicalArchive.restore(packet,f.password,b,f.targets)
                assertEquals(f.manifest,first.manifest);assertEquals(f.records.toString(),first.records.toString())
                assertNotEquals(f.dataset,first.dataset);assertNotEquals(f.epoch,first.epoch);assertNotEquals(a.identity,b.identity)
                assertFalse(a.sharesWrappingKey(b))
                assertNotEquals(first.dataset,second.dataset);assertNotEquals(first.epoch,second.epoch)
                f.members.forEachIndexed {i,m->
                    val actual=first.originalBytes.getValue(m.version.context.version);assertArrayEquals(f.plain[i],actual)
                    val hash=MessageDigest.getInstance("SHA-256").digest(actual).joinToString("") {"%02x".format(it.toInt() and 255)}
                    assertEquals(m.version.manifest.plaintextSha256,hash)
                    assertFalse(first.deviceEnvelopes.getValue(m.version.context.version).contentEquals(second.deviceEnvelopes.getValue(m.version.context.version)))
                }
                assertEquals(3,first.originalBytes.size);assertEquals(2,first.manifest.documents.size)
                assertEquals("TRASHED",first.manifest.documents.last().state);assertEquals("QUARANTINED",first.manifest.links.last().state)
                assertEquals(77L,first.mapping.getValue(f.record).localId);assertEquals(77L,first.mapping.getValue(f.lab).localId)
            } finally {a.destroy();b.destroy()}
        }
    }
    @Test fun candidateWrongPasswordCipherDamageAndOrdinaryBackupPrefixNeverReplaceOldLibrary() {
        Fixture().use {f->val packet=f.packet();val d=DraftMedicalArchive.Device()
            try {val old=DraftMedicalArchive.restore(packet,f.password,d,f.targets)
                reject {DraftMedicalArchive.restore(packet,"wrong synthetic".toCharArray(),d,f.targets)};assertSame(old,d.current)
                val broken=packet.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}
                reject {DraftMedicalArchive.restore(broken,f.password,d,f.targets)};assertSame(old,d.current)
                reject {DraftMedicalArchive.restore("PNBAK1-synthetic-not-a-medical-archive".toByteArray(),f.password,d,f.targets)};assertSame(old,d.current)
            } finally {d.destroy()}
        }
    }
    @Test fun candidateAuthenticatedOmissionDuplicateExtraAndObjectCorruptionAllRefuse() {
        Fixture().use {f->val packet=f.packet();val d=DraftMedicalArchive.Device()
            try {val old=DraftMedicalArchive.restore(packet,f.password,d,f.targets)
                val changes=listOf<(JSONObject)->Unit>(
                    {it.getJSONArray("members").remove(0)},
                    {it.getJSONArray("members").put(JSONObject(it.getJSONArray("members").getJSONObject(0).toString()))},
                    {it.getJSONArray("members").put(JSONObject(it.getJSONArray("members").getJSONObject(0).toString()).put("version",UUID.randomUUID()))},
                    {val o=it.getJSONArray("members").getJSONObject(0);val bytes=java.util.Base64.getDecoder().decode(o.getString("cipher"));bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();o.put("cipher",java.util.Base64.getEncoder().encodeToString(bytes))},
                    {it.put("candidateSchema",1)},
                    {it.getJSONObject("structured").put("schema",RecordBackupCompatibility.SCHEMA+1)})
                for(change in changes) {
                    val invalid=DraftMedicalArchive.mutate(packet,f.password,change)
                    reject {DraftMedicalArchive.restore(invalid,f.password,d,f.targets)};assertSame(old,d.current)
                }
            } finally {d.destroy()}
        }
    }
    @Test fun candidateInterruptedActivationAndWrongTypedMappingKeepOldLibrary() {
        Fixture().use {f->val packet=f.packet();val d=DraftMedicalArchive.Device()
            try {val old=DraftMedicalArchive.restore(packet,f.password,d,f.targets)
                var reached=false
                reject {DraftMedicalArchive.restore(packet,f.password,d,f.targets) {reached=true;throw IOException("synthetic interruption before isolated activation")}}
                assertTrue(reached);assertSame(old,d.current)
                reject {DraftMedicalArchive.restore(packet,f.password,d,f.targets.mapIndexed {i,t->t.copy(kind=AttachmentBackupManifest.Kind.LAB,localId=100L+i)})};assertSame(old,d.current)
                reject {DraftMedicalArchive.restore(packet,f.password,d,f.targets.dropLast(1))};assertSame(old,d.current)
            } finally {d.destroy()}
        }
    }
    @Test fun productionMembershipRejectsMissingIdentityClosureDuplicateVersionsAndLimits() {
        Fixture().use {f->val m=f.manifest;m.validate()
            reject {m.copy(versions=m.versions.dropLast(1)).validate()}
            reject {m.copy(records=m.records.dropLast(1)).validate()}
            reject {m.copy(versions=m.versions+m.versions.first()).validate()}
            reject {m.copy(links=m.links+m.links.first()).validate()}
            reject {m.validate(AttachmentBackupManifest.Limits(maximumVersions=2))}
            reject {m.validate(AttachmentBackupManifest.Limits(maximumCiphertextBytes=1))}
            reject {m.mapTo(m.dataset,UUID.randomUUID(),f.targets)}
            reject {m.mapTo(UUID.randomUUID(),m.epoch,f.targets)}
            reject {m.mapTo(UUID.randomUUID(),UUID.randomUUID(),f.targets+f.targets.first())}
            val sameKind=m.copy(records=m.records.mapIndexed {i,r->r.copy(kind=AttachmentBackupManifest.Kind.APPOINTMENT,sourceId=7L+i)})
            reject {sameKind.mapTo(UUID.randomUUID(),UUID.randomUUID(),f.targets.map {it.copy(kind=AttachmentBackupManifest.Kind.APPOINTMENT)})}
        }
    }
    @Test fun purgedIdentityMapsOnlyToATombstoneAndNeverResurrectsClinicalRow() {
        Fixture().use {f->val m=f.manifest.copy(records=f.manifest.records.map {if(it.uuid==f.lab)it.copy(state="PURGED",trashId=null) else it})
            reject {m.mapTo(UUID.randomUUID(),UUID.randomUUID(),f.targets)}
            val mapped=m.mapTo(UUID.randomUUID(),UUID.randomUUID(),f.targets.map {if(it.record==f.lab)it.copy(localId=null) else it})
            assertNull(mapped.getValue(f.lab).localId)
        }
    }
}
