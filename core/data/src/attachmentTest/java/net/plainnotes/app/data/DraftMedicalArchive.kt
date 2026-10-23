package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** TEST_ONLY in-memory, 2MiB reference candidate. NOT a proposed frozen wire format or production restore.
 * Low-cost KDF parameters are deliberately test-only, NOT a production password security claim.
 * Production snapshot captures no raw DEKs; synthetic sources explicitly supply their own test keys.
 */
internal object DraftMedicalArchive {
    private val magic="MED02-DRAFT-ONLY-V0".toByteArray(Charsets.US_ASCII)
    private const val MAX=2*1024*1024
    data class Member(val version:AttachmentBackupManifest.Version,val cipher:ByteArray,val syntheticDek:ByteArray)
    data class Restored(val dataset:UUID,val epoch:UUID,val manifest:AttachmentBackupManifest,
        val records:JSONObject,val mapping:Map<UUID,AttachmentBackupManifest.Target>,
        val originalBytes:Map<UUID,ByteArray>,val deviceEnvelopes:Map<UUID,ByteArray>)
    class Device {
        val identity:UUID=UUID.randomUUID()
        private val key=random(32)
        var current:Restored?=null
        fun wrap(dek:ByteArray,context:AttachmentObjectContext,dataset:UUID,epoch:UUID)=
            seal(key,dek,context.encode()+"/$identity/$dataset/$epoch".toByteArray(Charsets.US_ASCII))
        fun destroy() {key.fill(0)}
    }
    private fun random(n:Int)=ByteArray(n).also(SecureRandom()::nextBytes)
    private fun derive(password:CharArray,salt:ByteArray):ByteArray = ByteArray(64).also {out->
        val p=Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withSalt(salt).withMemoryAsKB(1024).withIterations(2).withParallelism(1).build()
        try {Argon2BytesGenerator().apply {init(p)}.generateBytes(password,out)} finally {p.clear()}
    }
    private fun seal(key:ByteArray,plain:ByteArray,aad:ByteArray):ByteArray {
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"));c.updateAAD(aad)
        return c.iv+c.doFinal(plain)
    }
    private fun open(key:ByteArray,cipher:ByteArray,aad:ByteArray):ByteArray {
        require(cipher.size>=28 && cipher.size<=MAX)
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,cipher,0,12));c.updateAAD(aad)
        return c.doFinal(cipher,12,cipher.size-12)
    }
    private fun b64(b:ByteArray)=Base64.getEncoder().encodeToString(b)
    private fun JSONObject.uuid(k:String)=UUID.fromString(getString(k))
    private fun array(values:List<JSONObject>)=JSONArray().also {a->values.forEach(a::put)}
    private fun JSONArray.rows()=(0 until length()).map {getJSONObject(it)}
    private fun encode(m:AttachmentBackupManifest):JSONObject=JSONObject().put("dataset",m.dataset).put("epoch",m.epoch).put("vault",m.logicalVault)
        .put("records",array(m.records.map {JSONObject().put("id",it.uuid).put("kind",it.kind.name).put("local",it.sourceId).put("state",it.state).put("trash",it.trashId?:JSONObject.NULL).put("evidence",it.evidenceSha256)}))
        .put("documents",array(m.documents.map {JSONObject().put("id",it.uuid).put("revision",it.revision).put("title",it.title).put("category",it.category).put("state",it.state).put("active",it.activeVersion).put("deleted",it.deletedUtc?:JSONObject.NULL)}))
        .put("versions",array(m.versions.map {v->JSONObject().put("doc",v.document).put("ordinal",v.ordinal).put("object",v.context.objectId).put("version",v.context.version).put("attempt",v.context.attempt).put("dek",v.context.dekHandle)
            .put("plainBytes",v.manifest.plaintextBytes).put("cipherBytes",v.manifest.ciphertextBytes).put("plainHash",v.manifest.plaintextSha256).put("cipherHash",v.manifest.ciphertextSha256).put("mime",v.mime)}))
        .put("links",array(m.links.map {JSONObject().put("id",it.uuid).put("record",it.record).put("doc",it.document).put("revision",it.revision).put("state",it.state)}))
    private fun decode(o:JSONObject):AttachmentBackupManifest {
        val vault=o.uuid("vault")
        return AttachmentBackupManifest(o.uuid("dataset"),o.uuid("epoch"),vault,
            o.getJSONArray("records").rows().map {AttachmentBackupManifest.Record(it.uuid("id"),AttachmentBackupManifest.Kind.valueOf(it.getString("kind")),it.getLong("local"),it.getString("state"),if(it.isNull("trash"))null else it.getLong("trash"),it.getString("evidence"))},
            o.getJSONArray("documents").rows().map {AttachmentBackupManifest.Document(it.uuid("id"),it.getLong("revision"),it.getString("title"),it.getString("category"),it.getString("state"),it.uuid("active"),if(it.isNull("deleted"))null else it.getLong("deleted"))},
            o.getJSONArray("versions").rows().map {v->val c=AttachmentObjectContext(vault,v.uuid("object"),v.uuid("version"),v.uuid("attempt"),v.uuid("dek"))
                AttachmentBackupManifest.Version(v.uuid("doc"),v.getLong("ordinal"),c,AttachmentObjectStreams.Manifest(c,v.getLong("plainBytes"),v.getLong("cipherBytes"),v.getString("plainHash"),v.getString("cipherHash")),v.getString("mime"))},
            o.getJSONArray("links").rows().map {AttachmentBackupManifest.Link(it.uuid("id"),it.uuid("record"),it.uuid("doc"),it.getLong("revision"),it.getString("state"))}).also {it.validate()}
    }
    fun pack(manifest:AttachmentBackupManifest,records:JSONObject,members:List<Member>,password:CharArray):ByteArray {
        manifest.validate();require(members.map {it.version.context.version}.toSet()==manifest.versions.map {it.context.version}.toSet())
        require(members.size==manifest.versions.size)
        val salt=random(16);val material=derive(password,salt);val bodyKey=material.copyOfRange(0,32);val wrapKey=material.copyOfRange(32,64)
        try {
            val body=JSONObject().put("candidateSchema",0).put("manifest",encode(manifest)).put("structured",records)
                .put("members",array(members.map {m->JSONObject().put("version",m.version.context.version).put("cipher",b64(m.cipher))
                    .put("portableEnvelope",b64(seal(wrapKey,m.syntheticDek,m.version.context.encode()+"/draft/portable".toByteArray(Charsets.US_ASCII))))}))
            val bytes=body.toString().toByteArray(Charsets.UTF_8)
            try {require(bytes.size<=MAX-128);return magic+salt+seal(bodyKey,bytes,magic+salt)}finally {bytes.fill(0)}
        } finally {material.fill(0);bodyKey.fill(0);wrapKey.fill(0)}
    }
    /** Privileged malformed-export injection, never compiled in main/release. */
    fun mutate(packet:ByteArray,password:CharArray,change:(JSONObject)->Unit):ByteArray {
        val salt=packet.copyOfRange(magic.size,magic.size+16);val material=derive(password,salt);val key=material.copyOfRange(0,32)
        val bytes=open(key,packet.copyOfRange(magic.size+16,packet.size),magic+salt)
        try {val body=JSONObject(String(bytes,Charsets.UTF_8));change(body);val modified=body.toString().toByteArray(Charsets.UTF_8)
            try {return magic+salt+seal(key,modified,magic+salt)}finally {modified.fill(0)}
        } finally {bytes.fill(0);material.fill(0);key.fill(0)}
    }
    fun restore(packet:ByteArray,password:CharArray,device:Device,targets:List<AttachmentBackupManifest.Target>,beforeActivation:()->Unit={}):Restored {
        require(packet.size in (magic.size+16+28)..MAX && packet.copyOfRange(0,magic.size).contentEquals(magic))
        val salt=packet.copyOfRange(magic.size,magic.size+16);val material=derive(password,salt);val bodyKey=material.copyOfRange(0,32);val wrapKey=material.copyOfRange(32,64)
        val plaintexts=LinkedHashMap<UUID,ByteArray>()
        var accepted=false
        try {
            val bodyBytes=open(bodyKey,packet.copyOfRange(magic.size+16,packet.size),magic+salt)
            val body=try {JSONObject(String(bodyBytes,Charsets.UTF_8))} finally {bodyBytes.fill(0)}
            require(body.getInt("candidateSchema")==0)
            val m=decode(body.getJSONObject("manifest"));val structured=body.getJSONObject("structured")
            RecordBackupCompatibility.readVersion(structured)
            val members=body.getJSONArray("members").rows();require(members.size==m.versions.size)
            require(members.map {it.uuid("version")}.toSet().size==members.size)
            require(members.map {it.uuid("version")}.toSet()==m.versions.map {it.context.version}.toSet())
            val dataset=UUID.randomUUID();val epoch=UUID.randomUUID();val mapping=m.mapTo(dataset,epoch,targets)
            val envelopes=LinkedHashMap<UUID,ByteArray>()
            for(v in m.versions) {
                val member=members.single {it.uuid("version")==v.context.version}
                val cipher=Base64.getDecoder().decode(member.getString("cipher"));val envelope=Base64.getDecoder().decode(member.getString("portableEnvelope"))
                val dek=open(wrapKey,envelope,v.context.encode()+"/draft/portable".toByteArray(Charsets.US_ASCII))
                try {
                    require(dek.size==32)
                    // Ciphertext-only temporary file; never plaintext. Existing primitive authenticates full EOF and both commitments.
                    val file=Files.createTempFile("synthetic-draft-cipher-",".bin")
                    try {
                        Files.write(file,cipher)
                        val session=AttachmentSession(dataset,epoch)
                        try {AttachmentObjectStreams.open(session.issue(),v.manifest,dek.copyOf(),FileChannel.open(file,StandardOpenOption.READ)).use {reader->
                            val plain=ByteArray(reader.size.toInt());var at=0
                            try {while(at<plain.size) {val n=reader.readAt(at.toLong(),plain,at,plain.size-at);check(n>0);at+=n}
                                plaintexts[v.context.version]=plain
                            } catch(failure:Throwable) {plain.fill(0);throw failure}
                        }} finally {session.revoke()}
                    } finally {Files.deleteIfExists(file)}
                    envelopes[v.context.version]=device.wrap(dek,v.context,dataset,epoch)
                } finally {dek.fill(0);cipher.fill(0);envelope.fill(0)}
            }
            val candidate=Restored(dataset,epoch,m,structured,mapping,plaintexts,envelopes)
            beforeActivation() // Failure injection; old isolated library remains current until this point.
            device.current=candidate;accepted=true;return candidate
        } finally {if(!accepted)plaintexts.values.forEach {it.fill(0)};material.fill(0);bodyKey.fill(0);wrapKey.fill(0)}
    }
}
