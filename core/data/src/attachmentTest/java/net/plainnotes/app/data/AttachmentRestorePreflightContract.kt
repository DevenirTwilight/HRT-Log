package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Synthetic logical archive, deliberately NO portable wire format or password/KDF. */
internal class RestoreSyntheticArchive:AutoCloseable {
    val sourceDataset=UUID.randomUUID();val epoch=UUID.randomUUID();val vault=UUID.randomUUID()
    val session=AttachmentSession(sourceDataset,epoch)
    val appointment=UUID.randomUUID();val lab=UUID.randomUUID();val dose=UUID.randomUUID();val tombstone=UUID.randomUUID()
    val document=UUID.randomUUID();val trashDocument=UUID.randomUUID()
    val plaintext=listOf("%PDF-1.4\n% SYNTHETIC ONLY\n%%EOF".toByteArray(),
        java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jc1sAAAAASUVORK5CYII="),ByteArray(100003) {(it*17).toByte()})
    val keys=plaintext.map {ByteArray(32).also(java.security.SecureRandom()::nextBytes)}
    val ciphertext=ArrayList<ByteArray>()
    val versions=plaintext.mapIndexed {i,b->
        val c=AttachmentObjectContext(vault,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
        val output=ByteArrayOutputStream()
        val m=AttachmentObjectStreams.write(session.issue(),c,keys[i].copyOf(),ByteArrayInputStream(b),output,AttachmentObjectStreams.MAXIMUM_BYTES)
        ciphertext+=output.toByteArray()
        AttachmentBackupManifest.Version(if(i<2)document else trashDocument,if(i<2)i+1L else 1L,c,m,
            listOf("application/pdf","image/png","application/octet-stream")[i])
    }
    val labPayload=Trash.payload("lab_value" to JSONArray().put(JSONObject().put("id",7))).toString()
    val dosePayload="{}" // Reference trash: soft-deleted row stays in dose_record, never in snapshot tables.
    val manifest=AttachmentBackupManifest(sourceDataset,epoch,vault,
        listOf(AttachmentBackupManifest.Record(appointment,AttachmentBackupManifest.Kind.APPOINTMENT,7,"ACTIVE",null,"a".repeat(64)),
            AttachmentBackupManifest.Record(lab,AttachmentBackupManifest.Kind.LAB,7,"TRASHED",11,sha(labPayload.toByteArray())),
            AttachmentBackupManifest.Record(dose,AttachmentBackupManifest.Kind.DOSE_RECORD,8,"TRASHED",12,sha(dosePayload.toByteArray())),
            AttachmentBackupManifest.Record(tombstone,AttachmentBackupManifest.Kind.APPOINTMENT,9,"PURGED",null,"b".repeat(64))),
        listOf(AttachmentBackupManifest.Document(document,2,"Synthetic history","REPORT","ACTIVE",versions[1].context.version,null),
            AttachmentBackupManifest.Document(trashDocument,2,"Synthetic trash","OTHER","TRASHED",versions[2].context.version,123)),versions,
        listOf(AttachmentBackupManifest.Link(UUID.randomUUID(),appointment,document,1,"ACTIVE"),
            AttachmentBackupManifest.Link(UUID.randomUUID(),lab,trashDocument,2,"QUARANTINED"),
            AttachmentBackupManifest.Link(UUID.randomUUID(),dose,document,2,"QUARANTINED")))
    fun structured():JSONObject {
        val tables=JSONObject();DOMAIN_TABLES.forEach {tables.put(it,JSONArray())}
        tables.getJSONArray("appointment").put(JSONObject().put("id",7).put("note","Synthetic source appointment"))
        tables.getJSONArray("dose_record").put(JSONObject().put("id",8).put("deleted_at_utc",1000))
        tables.getJSONArray("trash_item").put(JSONObject().put("id",11).put("kind",Trash.LAB).put("ref","7").put("payload_json",labPayload))
            .put(JSONObject().put("id",12).put("kind",Trash.RECORD).put("ref","8").put("payload_json",dosePayload))
        return JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",12).put("tables",tables)
    }
    val namespace=AttachmentRestorePreflight.Namespace(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
    val targets=manifest.records.map {AttachmentBackupManifest.Target(it.uuid,it.kind,if(it.state=="PURGED")null else 77)}
    val targetRows=manifest.records.filter {it.state!="PURGED"}.map {AttachmentRestorePreflight.TargetRow(it.uuid,it.kind,77,it.state)}
    fun plan(m:AttachmentBackupManifest=manifest,bytes:ByteArray=structured().toString().toByteArray(),
        members:List<AttachmentObjectStreams.Manifest> = versions.map {it.manifest},
        mapping:List<AttachmentBackupManifest.Target> = targets,rows:List<AttachmentRestorePreflight.TargetRow> = targetRows,
        resources:AttachmentRestorePreflight.Resources=AttachmentRestorePreflight.Resources(1024L*1024*1024,4096,100,100),
        limits:AttachmentRestorePreflight.Limits=AttachmentRestorePreflight.Limits())=
        AttachmentRestorePreflight.inspect(m,bytes,members,namespace,mapping,rows,emptySet(),resources,limits)
    fun source(onRead:()->Unit={}):AttachmentRestoreStaging.CiphertextSource = object:AttachmentRestoreStaging.CiphertextSource {
        override fun members()=versions.map {it.manifest}
        override fun open(version:UUID)=object:ByteArrayInputStream(ciphertext[versions.indexOfFirst {it.context.version==version}]) {
            override fun read(b:ByteArray,off:Int,len:Int):Int {onRead();return super.read(b,off,len)}
        }
        override fun takeDek(version:UUID)=keys[versions.indexOfFirst {it.context.version==version}].copyOf()
    }
    override fun close() {keys.forEach {it.fill(0)};session.revoke()}
    companion object {fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString("") {"%02x".format(it.toInt() and 255)}}
}

abstract class AttachmentRestorePreflightContract {
    protected fun reject(block:()->Unit) {try {block();fail("Unsafe restore preparation accepted")}catch(_:Exception){}}
    @Test fun completeHistoryClinicalTrashTypedMappingAndTombstonesArePreservedWithoutWriting() {
        RestoreSyntheticArchive().use {f->val bytes=f.structured().toString().toByteArray();val plan=f.plan(bytes=bytes)
            assertTrue(bytes.all {it==0.toByte()});assertEquals(f.manifest,plan.manifest)
            assertEquals(3,plan.manifest.versions.size);assertEquals(77L,plan.mapping.getValue(f.lab).localId)
            assertNull(plan.mapping.getValue(f.tombstone).localId);assertEquals(6,plan.plannedAliases);assertEquals(6,plan.plannedWrapInvocations)
            assertEquals(f.ciphertext.sumOf {it.size.toLong()},plan.ciphertextBytes)
            assertTrue(plan.additionalSpaceBytes>plan.ciphertextBytes)
        }
    }
    @Test fun missingExtraDuplicateSubstitutedObjectsAndMissingHistoryRefuse() {
        RestoreSyntheticArchive().use {f->val all=f.versions.map {it.manifest}
            reject {f.plan(members=all.drop(1))};reject {f.plan(members=all+all.first())}
            reject {f.plan(members=all.mapIndexed {i,m->if(i==0)m.copy(ciphertextSha256="c".repeat(64))else m})}
            reject {f.plan(m=f.manifest.copy(versions=f.versions.drop(1)),members=all.drop(1))}
            reject {f.plan(m=f.manifest.copy(links=f.manifest.links+f.manifest.links.first().copy(record=UUID.randomUUID())))}
        }
    }
    @Test fun mappingsMustNameActualTypedRowsAndNeverResurrectTombstones() {
        RestoreSyntheticArchive().use {f->
            reject {f.plan(mapping=f.targets.drop(1))}
            reject {f.plan(rows=f.targetRows.mapIndexed {i,t->if(i==0)t.copy(localId=7)else t})}
            reject {f.plan(rows=f.targetRows.mapIndexed {i,t->if(i==0)t.copy(kind=AttachmentBackupManifest.Kind.LAB)else t})}
            reject {f.plan(mapping=f.targets.map {if(it.record==f.tombstone)it.copy(localId=88) else it})}
            val j=f.structured();j.getJSONObject("tables").getJSONArray("appointment").remove(0)
            reject {f.plan(bytes=j.toString().toByteArray())}
        }
    }
    @Test fun softDeletedDoseReferenceIsNotMistakenForSnapshotTrashAndTamperingRefuses() {
        RestoreSyntheticArchive().use {f->f.plan()
            val j=f.structured();j.getJSONObject("tables").getJSONArray("trash_item").getJSONObject(1).put("ref","7")
            reject {f.plan(bytes=j.toString().toByteArray())}
            val k=f.structured();k.getJSONObject("tables").getJSONArray("dose_record").getJSONObject(0).put("deleted_at_utc",JSONObject.NULL)
            reject {f.plan(bytes=k.toString().toByteArray())}
            val b=f.structured();b.getJSONObject("tables").getJSONArray("trash_item").getJSONObject(0).put("payload_json","{}")
            reject {f.plan(bytes=b.toString().toByteArray())}
        }
    }
    @Test fun unknownTablesInvalidJsonBoundsSpaceKeysAndOverflowRefuseAndWipe() {
        RestoreSyntheticArchive().use {f->
            val j=f.structured();j.getJSONObject("tables").put("untrusted_sql",JSONArray());reject {f.plan(bytes=j.toString().toByteArray())}
            val invalid="{\"format\":1,\"format\":1}".toByteArray();reject {f.plan(bytes=invalid)};assertTrue(invalid.all {it==0.toByte()})
            reject {f.plan(resources=AttachmentRestorePreflight.Resources(0,4096,100,100))}
            reject {f.plan(resources=AttachmentRestorePreflight.Resources(Long.MAX_VALUE,4096,5,100))}
            reject {f.plan(resources=AttachmentRestorePreflight.Resources(Long.MAX_VALUE,4096,100,5))}
            reject {f.plan(resources=AttachmentRestorePreflight.Resources(Long.MAX_VALUE,Long.MAX_VALUE,100,100))}
            reject {f.plan(limits=AttachmentRestorePreflight.Limits(maximumVersions=2))}
        }
    }
}
