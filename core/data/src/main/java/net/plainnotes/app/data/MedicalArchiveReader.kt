package net.plainnotes.app.data

import android.content.Context
import android.os.Looper
import net.plainnotes.app.domain.AttachmentSession
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

/** Independent sequential reader and set verifier. No writer codec/re-encoding decides acceptance.
 * Only retained encrypted members escape, after outer EOF/footer and every inner AEAD/hash/EOF.
 * RAM-only business metadata, held source FD, no plaintext staging, SQL change or activation.
 */
internal object MedicalArchiveReader {
    private data class Member(val offset:Long,val version:AttachmentBackupManifest.Version)
    class Archive private constructor(private val cap:AttachmentSession.Capability,
        val header:MedicalArchiveCrypto.Header,private val manifest:AttachmentBackupManifest,
        private val records:ByteArray,private val envelopes:Map<UUID,String>,
        private val keys:MedicalArchiveCrypto.Keys,private val plain:SeekableByteChannel,
        private val members:Map<UUID,Member>,private val physical:SeekableByteChannel) :
        AutoCloseable,AttachmentRestoreStaging.CiphertextSource {
        val wholeArchiveAuthenticated=true
        val activatable=false
        private val reads=Any()
        private fun checkActive() {check(Looper.myLooper()!=Looper.getMainLooper()) {"Medical archive requires a worker"};cap.check();require(physical.size()==header.ciphertextBytes+80)}
        private fun <T> guarded(block:()->T):T = try {checkActive();val result=block();checkActive();cap.publish {result}}
            catch(e:Throwable) {try {close()}catch(cleanup:Throwable) {e.addSuppressed(cleanup)};throw e}
        fun manifest()=guarded {manifest}
        /** Borrowed RAM; callers must not retain. Revocation wipes it outside publication locks. */
        fun <T> records(consume:(ByteArray)->T):T=guarded {
            val copy=records.copyOf();val lease=cap.ownBytes(copy)
            try {consume(copy)}finally {lease.close()}
        }
        override fun members()=guarded {Collections.unmodifiableList(manifest.versions.map {it.manifest})}
        override fun takeDek(version:UUID):ByteArray {
            var key:ByteArray?=null
            try {
                checkActive();val v=requireNotNull(members[version]).version
                val owned=MedicalArchiveCrypto.unwrap(header,keys.wrapping,v.context,MedicalArchiveCrypto.unhex(envelopes.getValue(version))).also {key=it}
                checkActive();return cap.publish {owned}
            } catch(e:Throwable) {key?.fill(0);try {close()}catch(cleanup:Throwable) {e.addSuppressed(cleanup)};throw e}
        }
        private fun channel(version:UUID):SeekableByteChannel=guarded {
            val member=requireNotNull(members[version]);var at=0L;var open=true
            object:SeekableByteChannel {
                override fun read(dst:ByteBuffer):Int=synchronized(reads) {
                    try {check(open);checkActive();val length=member.version.manifest.ciphertextBytes
                        if(!dst.hasRemaining())0 else if(at==length)-1 else {
                            plain.position(member.offset+at)
                            val old=dst.limit();dst.limit(dst.position()+minOf(dst.remaining().toLong(),length-at).toInt())
                            try {val n=plain.read(dst);require(n>0);at+=n;checkActive();n} finally {dst.limit(old)}
                        }
                    } catch(e:Throwable) {open=false;throw e}
                }
                override fun position():Long=at
                override fun position(newPosition:Long):SeekableByteChannel {require(newPosition in 0..size());at=newPosition;return this}
                override fun size()=member.version.manifest.ciphertextBytes
                override fun isOpen()=open
                override fun close() {open=false}
                override fun write(src:ByteBuffer):Int=throw NonWritableChannelException()
                override fun truncate(size:Long):SeekableByteChannel=throw NonWritableChannelException()
            }
        }
        override fun open(version:UUID):InputStream=Channels.newInputStream(channel(version))
        fun preflight(namespace:AttachmentRestorePreflight.Namespace,targets:List<AttachmentBackupManifest.Target>,
            rows:List<AttachmentRestorePreflight.TargetRow>,forbidden:Set<UUID>,resources:AttachmentRestorePreflight.Resources,
            limits:AttachmentRestorePreflight.Limits=AttachmentRestorePreflight.Limits()):AttachmentRestorePreflight.Plan=guarded {
            AttachmentRestorePreflight.inspect(manifest,records.copyOf(),members(),namespace,targets,rows,forbidden,resources,limits) {checkActive()}
        }
        fun reconstruct(context:Context,currentDb:NotesDatabase,scope:AttachmentIdentity.Scope,
            plan:AttachmentRestorePreflight.Plan):AttachmentBusinessRestore.Candidate=guarded {
            require(plan.manifest==manifest)
            AttachmentBusinessRestore(context,currentDb).reconstruct(scope,cap,plan,records.copyOf(),this)
        }
        override fun close()=cap.close()
        companion object {
            private fun readHeader(channel:SeekableByteChannel):MedicalArchiveCrypto.Header {
                val size=channel.size();require(size in 80..MedicalArchiveCrypto.MAX_WIRE)
                val b=ByteBuffer.allocate(80);channel.position(0)
                while(b.hasRemaining())require(channel.read(b)>0)
                return MedicalArchiveCrypto.parse(b.array(),size)
            }
            /** Source is held, read-only ciphertext. Sole channel/password ownership on all paths. */
            fun read(parent:AttachmentSession.Capability,password:CharArray,incoming:SeekableByteChannel):Archive {
                val source=object:SeekableByteChannel by incoming {
                    private var closed=false
                    @Synchronized override fun close() {if(!closed) {closed=true;incoming.close()}}
                }
                var child:AttachmentSession.Capability?=null
                try {
                    check(Looper.myLooper()!=Looper.getMainLooper()) {"Medical archive requires a worker"}
                    val cap=parent.fork().also {child=it};cap.own(source)
                    val header=readHeader(source) // hard checks precede KDF and variable allocations
                    val keys=MedicalArchiveCrypto.derive(header,password,cap);cap.own(keys)
                    val window=object:SeekableByteChannel {
                        private var at=0L
                        override fun read(dst:ByteBuffer):Int {cap.check();require(source.size()==header.ciphertextBytes+80)
                            if(!dst.hasRemaining())return 0
                            if(at==size())return -1
                            source.position(at+80);val old=dst.limit();dst.limit(dst.position()+minOf(dst.remaining().toLong(),size()-at).toInt())
                            return try {source.read(dst).also {require(it>0);at+=it}}finally {dst.limit(old)}}
                        override fun size()=header.ciphertextBytes
                        override fun position()=at
                        override fun position(newPosition:Long):SeekableByteChannel {require(newPosition in 0..size());at=newPosition;return this}
                        override fun isOpen()=source.isOpen
                        override fun close()=Unit // source has one owner
                        override fun write(src:ByteBuffer):Int=throw NonWritableChannelException()
                        override fun truncate(size:Long):SeekableByteChannel=throw NonWritableChannelException()
                    }
                    val plain=AttachmentObjectStreams.primitive(keys.outer).newSeekableDecryptingChannel(window,header.aad());cap.own(plain)
                    val hash=MessageDigest.getInstance("SHA-256");hash.update("notes.medical.backup.set.v1\u0000".toByteArray(Charsets.US_ASCII));hash.update(header.bytes())
                    val input=object:InputStream() {
                        var count=0L;var hashing=true
                        override fun read():Int {val b=ByteArray(1);return if(read(b)<0)-1 else b[0].toInt() and 255}
                        override fun read(b:ByteArray,off:Int,len:Int):Int {cap.check();if(len==0)return 0
                            val n=plain.read(ByteBuffer.wrap(b,off,len));if(n>0) {require(n<=header.plaintextBytes-count);count+=n;if(hashing)hash.update(b,off,n)}
                            else require(n<0);return n}
                    }
                    val data=DataInputStream(input)
                    fun frame(tag:Int,max:Long):Long {require(data.readUnsignedByte()==tag);val n=data.readLong();require(n in 0..max && n<=header.plaintextBytes-input.count);return n}
                    val manifestBytes=ByteArray(frame(1,MedicalArchiveCrypto.MAX_MANIFEST.toLong()).toInt());val ml=cap.ownBytes(manifestBytes)
                    val decoded=try {data.readFully(manifestBytes);MedicalArchiveManifest.decode(manifestBytes) {cap.check()}}finally {ml.close()}
                    val m=decoded.manifest
                    val records=ByteArray(frame(2,BackupLimits.MAX_JSON_BYTES.toLong()).toInt());cap.ownBytes(records);data.readFully(records)
                    val rows=MedicalArchiveRecords.count(records) {cap.check()}
                    require(rows+m.records.size+m.documents.size+m.versions.size+m.links.size<=100000)
                    val members=LinkedHashMap<UUID,Member>();val buffer=ByteArray(65536);val bl=cap.ownBytes(buffer)
                    try {m.versions.sortedWith(compareBy(MedicalArchiveCrypto.uuidOrder) {it.context.version}).forEach {v->
                        require(frame(3,40L+AttachmentObjectStreams.MAXIMUM_BYTES+65536)==40+v.manifest.ciphertextBytes)
                        val identity=ByteArray(32);data.readFully(identity);val b=ByteBuffer.wrap(identity)
                        require(MedicalArchiveCrypto.uuid(b)==v.context.objectId && MedicalArchiveCrypto.uuid(b)==v.context.version)
                        require(data.readLong()==v.manifest.ciphertextBytes)
                        members[v.context.version]=Member(input.count,v)
                        val sha=MessageDigest.getInstance("SHA-256");var remaining=v.manifest.ciphertextBytes
                        while(remaining>0) {cap.check();val n=minOf(buffer.size.toLong(),remaining).toInt();data.readFully(buffer,0,n);sha.update(buffer,0,n);remaining-=n}
                        require(MedicalArchiveCrypto.hex(sha.digest())==v.manifest.ciphertextSha256)
                    }}finally {bl.close()}
                    val commitment=hash.digest();input.hashing=false
                    require(frame(4,64)==64L);val footer=ByteArray(32);data.readFully(footer);require(MessageDigest.isEqual(commitment,footer))
                    require(data.readLong()==m.versions.size.toLong()+3 && data.readLong()==m.versions.size.toLong() && data.readLong()==rows && data.readLong()==header.plaintextBytes)
                    require(input.count==header.plaintextBytes && data.read()==-1 && plain.size()==header.plaintextBytes && source.size()==header.ciphertextBytes+80)
                    val result=Archive(cap,header,m,records,decoded.envelopes,keys,plain,Collections.unmodifiableMap(members),source)
                    // Full inner authentication, including superseded originals, never just current files.
                    m.versions.forEach {v->result.checkActive();AttachmentObjectStreams.open(cap,v.manifest,result.takeDek(v.context.version),result.channel(v.context.version)).close()}
                    return cap.publish {result}
                } catch(e:Throwable) {try {child?.close()}catch(cleanup:Throwable) {e.addSuppressed(cleanup)};try {source.close()}catch(cleanup:Throwable) {e.addSuppressed(cleanup)};throw e}
                finally {password.fill('\u0000')}
            }
        }
    }
    fun read(parent:AttachmentSession.Capability,password:CharArray,source:SeekableByteChannel)=Archive.read(parent,password,source)
}
