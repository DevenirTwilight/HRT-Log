package net.plainnotes.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

/** PNMEDBK2 exact collection membership. An index is NOT authentication or admission authority.
 * Bounds are aggregate, including empty retained libraries. No DB/keys/files or clinical inference. */
internal data class MedicalCollectionIndex(val collection:UUID,val active:UUID,val libraries:List<Library>) {
    enum class Role { ACTIVE, RETAINED }
    data class Member(val objectId:UUID,val version:UUID,val bytes:Long,val sha256:String)
    data class Library(val id:UUID,val role:Role,val dataset:UUID,val epoch:UUID,val logicalVault:UUID,
        val recordBytes:Long,val recordSha256:String,val manifestBytes:Long,val manifestSha256:String,
        val businessRows:Long,val metadataRows:Long,val members:List<Member>)
    fun validate() {
        require(libraries.size in 1..8 && libraries.map {it.id}.toSet().size==libraries.size)
        require(libraries.count {it.role==Role.ACTIVE}==1 && libraries.single {it.role==Role.ACTIVE}.id==active)
        require(libraries.zipWithNext().all {(a,b)->a.id.toString()<b.id.toString()}) {"Unordered collection"}
        var json=0L;var manifests=0L;var rows=0L;var versions=0L;var cipher=0L
        for(l in libraries) {
            require(l.recordBytes in 1..BackupLimits.MAX_JSON_BYTES.toLong() && l.manifestBytes in 1..MedicalArchiveCrypto.MAX_MANIFEST.toLong())
            require(l.businessRows in 0..100000 && l.metadataRows in 0..100000)
            hash(l.recordSha256);hash(l.manifestSha256)
            json=Math.addExact(json,l.recordBytes);require(json<=BackupLimits.MAX_JSON_BYTES)
            manifests=Math.addExact(manifests,l.manifestBytes);require(manifests<=MedicalArchiveCrypto.MAX_MANIFEST)
            rows=Math.addExact(rows,Math.addExact(l.businessRows,l.metadataRows));require(rows<=100000)
            versions=Math.addExact(versions,l.members.size.toLong());require(versions<=1000)
            require(l.members.map {it.objectId}.toSet().size==l.members.size)
            require(l.members.zipWithNext().all {(a,b)->a.version.toString()<b.version.toString()}) {"Duplicate/unordered member"}
            l.members.forEach {m->
                require(m.bytes in MedicalArchiveCrypto.cipherLength(0)..MedicalArchiveCrypto.cipherLength(AttachmentObjectStreams.MAXIMUM_BYTES))
                hash(m.sha256);cipher=Math.addExact(cipher,m.bytes);require(cipher<=MedicalArchiveCrypto.MAX_CIPHER)
            }
        }
    }
    /** Reader independently verifies authenticated payload bytes and existing per-library semantics.
     * A same integer ID or even same version UUID in another library is never an identity match. */
    fun verifyLibrary(id:UUID,records:ByteArray,manifest:ByteArray,checkActive:()->Unit={}):MedicalArchiveManifest.Decoded {
        validate();val l=libraries.single {it.id==id}
        require(records.size.toLong()==l.recordBytes && digest(records)==l.recordSha256)
        require(manifest.size.toLong()==l.manifestBytes && digest(manifest)==l.manifestSha256)
        val decoded=MedicalArchiveManifest.decode(manifest,checkActive);val m=decoded.manifest
        require(m.dataset==l.dataset && m.epoch==l.epoch && m.logicalVault==l.logicalVault)
        require(MedicalArchiveRecords.count(records,checkActive)==l.businessRows)
        require(m.records.size.toLong()+m.documents.size+m.versions.size+m.links.size==l.metadataRows)
        require(m.versions.sortedBy {it.context.version.toString()}.map {Member(it.context.objectId,it.context.version,it.manifest.ciphertextBytes,it.manifest.ciphertextSha256)}==l.members)
        return decoded
    }
    fun encode():ByteArray {
        validate()
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {out->
            out.writeInt(2);out.uuid(collection);out.uuid(active);out.writeInt(libraries.size)
            libraries.forEach {l->
                out.uuid(l.id);out.writeByte(if(l.role==Role.ACTIVE)1 else 2)
                out.uuid(l.dataset);out.uuid(l.epoch);out.uuid(l.logicalVault)
                out.writeLong(l.recordBytes);out.write(MedicalArchiveCrypto.unhex(l.recordSha256))
                out.writeLong(l.manifestBytes);out.write(MedicalArchiveCrypto.unhex(l.manifestSha256))
                out.writeLong(l.businessRows);out.writeLong(l.metadataRows);out.writeInt(l.members.size)
                l.members.forEach {m->out.uuid(m.objectId);out.uuid(m.version);out.writeLong(m.bytes);out.write(MedicalArchiveCrypto.unhex(m.sha256))}
            }
        }
        return bytes.toByteArray().also {require(it.size<=MAX_INDEX)}
    }
    companion object {
        // 8 fixed library descriptors + 1000 fixed member descriptors, no arbitrary strings.
        const val MAX_INDEX=80000
        private fun hash(s:String) {require(s.length==64 && s.all {it in '0'..'9' || it in 'a'..'f'})}
        fun digest(bytes:ByteArray)=MedicalArchiveCrypto.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        private fun DataOutputStream.uuid(id:UUID) {writeLong(id.mostSignificantBits);writeLong(id.leastSignificantBits)}
        private fun DataInputStream.uuid()=UUID(readLong(),readLong())
        private fun DataInputStream.hash():String {val b=ByteArray(32);readFully(b);return MedicalArchiveCrypto.hex(b)}
        fun decode(bytes:ByteArray,checkActive:()->Unit={}):MedicalCollectionIndex {
            require(bytes.size<=MAX_INDEX)
            return DataInputStream(ByteArrayInputStream(bytes)).use {input->
                require(input.readInt()==2);val collection=input.uuid();val active=input.uuid()
                val count=input.readInt();require(count in 1..8)
                var members=0
                val libraries=List(count) {
                    checkActive();val id=input.uuid();val role=when(input.readUnsignedByte()) {1->Role.ACTIVE;2->Role.RETAINED;else->error("Unknown collection role")}
                    val dataset=input.uuid();val epoch=input.uuid();val vault=input.uuid()
                    val records=input.readLong();val rh=input.hash();val manifest=input.readLong();val mh=input.hash()
                    val business=input.readLong();val metadata=input.readLong();val n=input.readInt()
                    require(n in 0..1000 && n<=1000-members);members+=n
                    val objects=List(n) {checkActive();Member(input.uuid(),input.uuid(),input.readLong(),input.hash())}
                    Library(id,role,dataset,epoch,vault,records,rh,manifest,mh,business,metadata,Collections.unmodifiableList(objects))
                }
                require(input.read()==-1) {"Trailing collection index"}
                MedicalCollectionIndex(collection,active,Collections.unmodifiableList(libraries)).also {it.validate();checkActive()}
            }
        }
    }
}
