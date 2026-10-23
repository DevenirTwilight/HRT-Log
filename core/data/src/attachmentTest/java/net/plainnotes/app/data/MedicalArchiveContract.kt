package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Synthetic production reader + independent test framing, not a TEST_ONLY low-cost KDF.
 * Actual writer->Keystore-free destination->SQLCipher reconstruction is separately native.
 */
abstract class MedicalArchiveContract {
    protected abstract fun canonical():ByteArray
    protected fun <T> worker(block:()->T):T {
        val pool=Executors.newSingleThreadExecutor()
        try {return pool.submit(Callable {block()}).get(120,TimeUnit.SECONDS)} finally {pool.shutdownNow()}
    }
    protected fun reject(block:()->Unit) {try {block();fail("Invalid archive accepted")}catch(_:Exception){}}
    protected fun memory(bytes:ByteArray):SeekableByteChannel=object:SeekableByteChannel {
        var at=0;var open=true
        override fun read(b:ByteBuffer):Int {check(open);if(!b.hasRemaining())return 0;if(at==bytes.size)return -1;val n=minOf(b.remaining(),bytes.size-at);b.put(bytes,at,n);at+=n;return n}
        override fun position()=at.toLong()
        override fun position(n:Long):SeekableByteChannel {require(n in 0..bytes.size.toLong());at=n.toInt();return this}
        override fun size()=bytes.size.toLong()
        override fun isOpen()=open
        override fun close(){open=false}
        override fun write(b:ByteBuffer):Int=error("read-only")
        override fun truncate(n:Long):SeekableByteChannel=error("read-only")
    }
    protected fun session()=AttachmentSession(UUID.randomUUID(),UUID.randomUUID())
    @Test fun productionCanonicalCodecMatchesIndependentPythonVectorAndRejectsEquivalentEncodings() {
        val bytes=canonical();val decoded=MedicalArchiveManifest.decode(bytes)
        assertArrayEquals(bytes,MedicalArchiveManifest.encode(decoded.manifest,decoded.envelopes))
        val text=String(bytes,Charsets.UTF_8)
        val invalid=listOf(" $text",text+"\n",text.replaceFirst("\"version\":1,","\"version\":1,\"version\":1,"),
            text.replaceFirst("\"version\":1","\"version\":1.0"),text.replaceFirst("\"version\":1","\"version\":01"),
            text.replaceFirst("\"version\":1","\"version\":true"),text.replaceFirst("\"version\":1","\"VERSION\":1"),
            text.replaceFirst("\\u000a","\\n"),text.replaceFirst("9223372036854775807","9223372036854775808"))
        invalid.forEach {require(it!=text);reject {MedicalArchiveManifest.decode(it.toByteArray())}}
        reject {MedicalArchiveManifest.decode(byteArrayOf(0xc0.toByte(),0x80.toByte()))}
        reject {MedicalArchiveManifest.decode(ByteArray(MedicalArchiveCrypto.MAX_MANIFEST+1))}
        // A syntactically closed object list with only the latest version must not become a
        // successful export. Reuse preflight's exact retained-history rule before writing too.
        val m=decoded.manifest;val document=m.versions.first {it.ordinal>1}.document
        val gap=m.copy(versions=m.versions.filter {it.document!=document || it.ordinal>1})
        reject {MedicalArchiveManifest.encode(gap,decoded.envelopes.filterKeys {v->gap.versions.any {it.context.version==v}})}
    }
    @Test fun exactHeaderRejectsUnknownParametersReservedBitsPhysicalTailAndOverflowBeforeKdf() {
        val h=MedicalArchiveCrypto.fresh(65536);assertEquals(80,h.bytes().size)
        assertEquals(h.archive,MedicalArchiveCrypto.parse(h.bytes(),h.ciphertextBytes+80).archive)
        for(offset in listOf(0,8,10,12,14,48,52,56,58,60,68,76)) {val b=h.bytes();b[offset]=(b[offset].toInt() xor 64).toByte();reject {MedicalArchiveCrypto.parse(b,h.ciphertextBytes+80)}}
        reject {MedicalArchiveCrypto.parse(h.bytes(),h.ciphertextBytes+81)}
        listOf(0L,1L,65479L,65480L,65481L,65520L,65536L,131073L,1048576L).forEach {n->
            assertEquals(n+40+16*maxOf(1,(n+40+65519)/65520),MedicalArchiveCrypto.cipherLength(n))}
        reject {MedicalArchiveCrypto.cipherLength(Long.MAX_VALUE)}
    }
    @Test fun fixedArgonHkdfVectorAndStrictPasswordOwnership() =worker {
        val session=session();try {val cap=session.issue();val bytes=MedicalArchiveCrypto.fresh(100).bytes()
            ByteArray(16) {it.toByte()}.copyInto(bytes,16)
            MedicalArchiveCrypto.uuidBytes(UUID(0,1)).copyInto(bytes,32)
            val h=MedicalArchiveCrypto.parse(bytes,MedicalArchiveCrypto.cipherLength(100)+80)
            val password="SYNTHETIC PNMEDBK1 resource probe".toCharArray()
            MedicalArchiveCrypto.derive(h,password,cap).use {k->
                assertEquals(OUTER_KAT,MedicalArchiveCrypto.hex(k.outer));assertEquals(WRAP_KAT,MedicalArchiveCrypto.hex(k.wrapping))}
            assertTrue(password.all {it=='\u0000'})
            for(p in listOf(charArrayOf(),charArrayOf('\ud800'),CharArray(4097) {'x'},CharArray(2000) {'漢'})) {
                reject {MedicalArchiveCrypto.derive(h,p,cap)};assertTrue(p.all {it=='\u0000'})}
        }finally {session.revoke()}
    }
    @Test fun portableWrapIsBoundToArchiveAndFullObjectContextAndAttemptCannotRetry() {
        val h=MedicalArchiveCrypto.fresh(100);val c=AttachmentObjectContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
        val key=ByteArray(32) {it.toByte()};val dek=ByteArray(32) {(it+7).toByte()}
        val attempt=MedicalArchiveCrypto.WrapAttempt(h,key,listOf(c));val e=attempt.wrap(c,dek)
        assertEquals(60,e.size);assertArrayEquals(dek,MedicalArchiveCrypto.unwrap(h,key,c,e))
        reject {attempt.wrap(c,dek)}
        reject {MedicalArchiveCrypto.unwrap(MedicalArchiveCrypto.fresh(100),key,c,e)}
        reject {MedicalArchiveCrypto.unwrap(h,key,c.copy(version=UUID.randomUUID()),e)}
        val wrong=key.copyOf().also {it[0]=99};reject {MedicalArchiveCrypto.unwrap(h,wrong,c,e)}
        val reserve=MedicalArchiveCrypto.WrapAttempt(h,key,listOf(c));reject {reserve.wrap(c,ByteArray(31))};reject {reserve.wrap(c,dek)}
    }
    /** Independent synthetic producer: no production writer/framing/receipt, but same real primitives. */
    private fun fixture(cap:AttachmentSession.Capability,fault:String="OK"):Pair<ByteArray,ByteArray> {
        val context=AttachmentObjectContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
        val dek=ByteArray(32) {17};val plaintext=ByteArray(70123) {(it*11).toByte()};val inner=ByteArrayOutputStream()
        val member=AttachmentObjectStreams.write(cap,context,dek.copyOf(),ByteArrayInputStream(plaintext),inner,plaintext.size.toLong())
        val doc=UUID.randomUUID()
        val m=AttachmentBackupManifest(UUID.randomUUID(),UUID.randomUUID(),context.logicalVault,emptyList(),
            listOf(AttachmentBackupManifest.Document(doc,1,"SYNTHETIC","OTHER","ACTIVE",context.version,null)),
            listOf(AttachmentBackupManifest.Version(doc,1,context,member,"application/octet-stream")),emptyList())
        val tables=JSONObject();DOMAIN_TABLES.forEach {tables.put(it,org.json.JSONArray())}
        val structured=JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",12).put("tables",tables).toString().toByteArray()
        val template=MedicalArchiveManifest.encode(m,mapOf(context.version to "00".repeat(60)))
        val size=91L+template.size+structured.size+49+member.ciphertextBytes
        val header=MedicalArchiveCrypto.fresh(size)
        val output=ByteArrayOutputStream();output.write(header.bytes())
        MedicalArchiveCrypto.derive(header,"SYNTHETIC password".toCharArray(),cap).use {keys->
            var envelope=MedicalArchiveCrypto.WrapAttempt(header,keys.wrapping,listOf(context)).wrap(context,dek)
            if(fault=="ENVELOPE")envelope=envelope.also {it[20]=(it[20].toInt() xor 1).toByte()}
            val manifest=MedicalArchiveManifest.encode(m,mapOf(context.version to MedicalArchiveCrypto.hex(envelope)))
            val prefix=ByteArrayOutputStream();val body=DataOutputStream(prefix)
            body.writeByte(1);body.writeLong(manifest.size.toLong());body.write(manifest)
            body.writeByte(2);body.writeLong(structured.size.toLong());body.write(structured)
            body.writeByte(if(fault=="MISSING")4 else 3);body.writeLong(40+member.ciphertextBytes)
            body.write(MedicalArchiveCrypto.uuidBytes(context.objectId));body.write(MedicalArchiveCrypto.uuidBytes(if(fault=="IDENTITY")UUID.randomUUID() else context.version));body.writeLong(member.ciphertextBytes)
            val cipher=inner.toByteArray();if(fault=="INNER")cipher[cipher.lastIndex]=(cipher.last().toInt() xor 1).toByte();body.write(cipher)
            val hash=MessageDigest.getInstance("SHA-256");hash.update("notes.medical.backup.set.v1\u0000".toByteArray(Charsets.US_ASCII));hash.update(header.bytes());hash.update(prefix.toByteArray())
            body.writeByte(4);body.writeLong(64);body.write(hash.digest());body.writeLong(if(fault=="COUNTS")5 else 4);body.writeLong(1);body.writeLong(0);body.writeLong(size)
            AttachmentObjectStreams.primitive(keys.outer).newEncryptingStream(output,header.aad()).use {it.write(prefix.toByteArray())}
        }
        dek.fill(0);return output.toByteArray() to plaintext
    }
    @Test fun independentArchiveAuthenticatesOuterFooterEveryInnerAndRevocation()=worker {
        val session=session();try {val cap=session.issue();val (bytes,plaintext)=fixture(cap)
            val password="SYNTHETIC password".toCharArray();val source=memory(bytes)
            MedicalArchiveReader.read(cap,password,source).use {a->assertTrue(a.wholeArchiveAuthenticated);assertFalse(a.activatable)
                val m=a.manifest();val member=m.versions.single().manifest;val cipher=a.open(member.context.version).use {it.readBytes()}
                AttachmentObjectStreams.open(cap,member,a.takeDek(member.context.version),memory(cipher)).use {r->
                    val b=ByteArray(plaintext.size);var at=0;while(at<b.size)at+=r.readAt(at.toLong(),b,at,b.size-at);assertArrayEquals(plaintext,b)}
                a.records {it[0]=0};a.records {assertEquals('{'.code.toByte(),it[0])}
                lateinit var borrowed:ByteArray;a.records {borrowed=it};session.revoke();assertTrue(borrowed.all {it==0.toByte()});reject {a.members()}
            };assertFalse(source.isOpen);assertTrue(password.all {it=='\u0000'})
        }finally {session.revoke()}
    }
    @Test fun authenticatedButMissingSubstitutedCorruptOrBadEnvelopeMembersRefuseWholeArchive()=worker {
        val session=session();try {val cap=session.issue();for(fault in listOf("MISSING","IDENTITY","INNER","ENVELOPE","COUNTS")) {
            val (b,_)=fixture(cap,fault);val source=memory(b);val p="SYNTHETIC password".toCharArray()
            reject {MedicalArchiveReader.read(cap,p,source)};assertFalse(source.isOpen);assertTrue(p.all {it=='\u0000'})
        }}finally {session.revoke()}
    }
    @Test fun wrongPasswordTruncationPhysicalTailAndOuterTagRefuseWithoutReturningPartialArchive()=worker {
        val session=session();try {val cap=session.issue();val (b,_)=fixture(cap)
            val cases=listOf(b to "wrong password",b.copyOf(b.size-1) to "SYNTHETIC password",(b+byteArrayOf(0)) to "SYNTHETIC password",
                b.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()} to "SYNTHETIC password")
            cases.forEach {(bytes,pw)->val source=memory(bytes);val p=pw.toCharArray();reject {MedicalArchiveReader.read(cap,p,source)};assertFalse(source.isOpen);assertTrue(p.all {it=='\u0000'})}
        }finally {session.revoke()}
    }
    companion object {
        // Generated independently with libargon2 + Python hmac RFC5869, fixed salt/archive above.
        private const val OUTER_KAT="95fac8296f52be1effaf4b943aac6cd10b026ab41be5a2c4f0b6b2aa8d7f2b20"
        private const val WRAP_KAT="93d9a15599d1d2ceaaf8174835ff5ea9aa4e0ff4d68d4d611666f6cb7e10f10a"
    }
}
