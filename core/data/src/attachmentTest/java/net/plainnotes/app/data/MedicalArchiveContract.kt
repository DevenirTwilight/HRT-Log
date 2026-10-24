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
    /** Independent v2 framing producer; no production collection writer or low-cost KDF. */
    private fun collectionFixture(cap:AttachmentSession.Capability,fault:String="OK"):ByteArray {
        val ids=listOf(UUID(0,1),UUID(0,2));val context=AttachmentObjectContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
        val doc=UUID.randomUUID();val dek=ByteArray(32) {23}
        val ciphers=ids.mapIndexed {i,_->ByteArrayOutputStream().also {output->
            AttachmentObjectStreams.write(cap,context,dek.copyOf(),ByteArrayInputStream(ByteArray(100+i) {i.toByte()}),output,101)
        }.toByteArray()}
        val tables=JSONObject();DOMAIN_TABLES.forEach {tables.put(it,org.json.JSONArray())}
        val records=JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",12).put("tables",tables).toString().toByteArray()
        val manifests=ids.mapIndexed {i,_->
            if(fault=="INNER" && i==0)ciphers[i][ciphers[i].lastIndex]=(ciphers[i].last().toInt() xor 1).toByte()
            val original=ByteArray(100+i) {i.toByte()}
            val member=AttachmentObjectStreams.Manifest(context,original.size.toLong(),ciphers[i].size.toLong(),MedicalCollectionIndex.digest(original),MedicalCollectionIndex.digest(ciphers[i]))
            AttachmentBackupManifest(UUID.randomUUID(),UUID.randomUUID(),context.logicalVault,emptyList(),
                listOf(AttachmentBackupManifest.Document(doc,1,"SYNTHETIC collection","OTHER","ACTIVE",context.version,null)),
                listOf(AttachmentBackupManifest.Version(doc,1,context,member,"application/octet-stream")),emptyList())
        }
        val dummy=manifests.map {MedicalArchiveManifest.encode(it,mapOf(context.version to "0".repeat(120)))}
        fun descriptor(i:Int,manifest:ByteArray)=MedicalCollectionIndex.Library(ids[i],if(i==0)MedicalCollectionIndex.Role.ACTIVE else MedicalCollectionIndex.Role.RETAINED,
            manifests[i].dataset,manifests[i].epoch,context.logicalVault,records.size.toLong(),MedicalCollectionIndex.digest(records),manifest.size.toLong(),MedicalCollectionIndex.digest(manifest),0,2,
            listOf(MedicalCollectionIndex.Member(context.objectId,context.version,ciphers[i].size.toLong(),MedicalCollectionIndex.digest(ciphers[i]))))
        val length=MedicalCollectionIndex(UUID(0,99),ids[0],ids.indices.map {descriptor(it,dummy[it])}).encode().size
        var n=9L+length+73+ids.indices.sumOf {50L+dummy[it].size+records.size+65+ciphers[it].size}
        if(fault=="MISSING")n-=65+ciphers[0].size
        if(fault=="EXTRA")n+=65+ciphers[0].size
        val header=MedicalArchiveCrypto.freshCollection(n);val output=ByteArrayOutputStream();output.write(header.bytes())
        try {MedicalArchiveCrypto.derive(header,"SYNTHETIC collection password".toCharArray(),cap).use {keys->
            val wrap=MedicalArchiveCrypto.CollectionWrapAttempt(header,keys.wrapping,ids.map {it to context})
            val envelopes=ids.map {MedicalArchiveCrypto.hex(wrap.wrap(it,context,dek))}
            val encoded=manifests.mapIndexed {i,m->MedicalArchiveManifest.encode(m,mapOf(context.version to envelopes[if(fault=="WRAP_SCOPE" && i==0)1 else i]))}
            val index=MedicalCollectionIndex(header.archive,ids[0],ids.indices.map {descriptor(it,encoded[it])}).encode()
            val prefix=ByteArrayOutputStream();val body=DataOutputStream(prefix)
            fun frame(tag:Int,size:Long) {body.writeByte(tag);body.writeLong(size)}
            frame(1,index.size.toLong());body.write(index)
            fun member(i:Int) {
                frame(4,56L+ciphers[i].size);body.write(MedicalArchiveCrypto.uuidBytes(if(fault=="WRONG_LIBRARY" && i==0)ids[1] else ids[i]))
                body.write(MedicalArchiveCrypto.uuidBytes(context.objectId));body.write(MedicalArchiveCrypto.uuidBytes(context.version));body.writeLong(ciphers[i].size.toLong());body.write(ciphers[i])
            }
            for(i in ids.indices) {
                frame(2,16L+encoded[i].size);body.write(MedicalArchiveCrypto.uuidBytes(ids[i]));body.write(encoded[i])
                frame(3,16L+records.size);body.write(MedicalArchiveCrypto.uuidBytes(ids[i]));body.write(records)
                if(fault!="MISSING" || i!=0)member(i)
                if(fault=="EXTRA" && i==0)member(i)
            }
            val sha=MessageDigest.getInstance("SHA-256");sha.update("notes.medical.backup.set.v2\u0000".toByteArray(Charsets.US_ASCII));sha.update(header.bytes());sha.update(prefix.toByteArray())
            frame(5,64);body.write(sha.digest());body.writeLong(2);body.writeLong(2);body.writeLong(4);body.writeLong(n)
            assertEquals(n,prefix.size().toLong())
            AttachmentObjectStreams.primitive(keys.outer).newEncryptingStream(output,header.aad()).use {it.write(prefix.toByteArray())}
        }}finally {dek.fill(0);records.fill(0);dummy.forEach {it.fill(0)}}
        return output.toByteArray()
    }
    @Test fun independentCollectionPreservesScopedCopiesAndAuthenticatesAllLibraries()=worker {
        val s=session();try {
            val wire=collectionFixture(s.issue());val password="SYNTHETIC collection password".toCharArray();val source=memory(wire)
            MedicalCollectionReader.read(s.issue(),password,source).use {archive->
                assertTrue(archive.wholeArchiveAuthenticated);assertFalse(archive.activatable);assertEquals(2,archive.index().libraries.size)
                val a=archive.manifest(UUID(0,1));val b=archive.manifest(UUID(0,2))
                assertNotEquals(a.dataset,b.dataset);assertEquals(a.versions.single().context,b.versions.single().context)
                assertNotEquals(a.versions.single().manifest.plaintextSha256,b.versions.single().manifest.plaintextSha256)
                archive.records(UUID(0,1)) {it[0]=0};archive.records(UUID(0,1)) {assertEquals('{'.code.toByte(),it[0])}
                val reader=archive.source(UUID(0,2));s.revoke();reject {reader.takeDek(b.versions.single().context.version)}
            }
            assertFalse(source.isOpen);assertTrue(password.all {it=='\u0000'})
        }finally {s.revoke()}
    }
    @Test fun authenticatedCollectionMissingExtraCrossLibraryWrapAndInnerCorruptionAllRefuse()=worker {
        val s=session();try {for(fault in listOf("MISSING","EXTRA","WRONG_LIBRARY","WRAP_SCOPE","INNER")) {
            val source=memory(collectionFixture(s.issue(),fault));val password="SYNTHETIC collection password".toCharArray()
            reject {MedicalCollectionReader.read(s.issue(),password,source)}
            assertFalse("Failed source ownership: $fault",source.isOpen);assertTrue(password.all {it=='\u0000'})
        }}finally {s.revoke()}
    }

}
