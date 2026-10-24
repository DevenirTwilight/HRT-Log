package net.plainnotes.app.data

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

/** Independent PNMEDBK2 parser. No writer re-encoding acceptance, DB mutation or activation.
 * Nothing is returned until outer EOF, exact collection/footer and ALL inner AEADs authenticate. */
internal object MedicalCollectionReader {
    private data class Member(val offset:Long,val version:AttachmentBackupManifest.Version)
    class Archive private constructor(private val cap:AttachmentSession.Capability,
        val header:MedicalArchiveCrypto.Header,private val index:MedicalCollectionIndex,
        private val manifests:Map<UUID,MedicalArchiveManifest.Decoded>,private val records:Map<UUID,ByteArray>,
        private val members:Map<Pair<UUID,UUID>,Member>,private val keys:MedicalArchiveCrypto.Keys,
        private val plain:SeekableByteChannel,private val physical:SeekableByteChannel):AutoCloseable {
        val wholeArchiveAuthenticated=true
        val activatable=false
        private val reads=Any()
        private fun active() {check(Looper.myLooper()!=Looper.getMainLooper());cap.check();require(physical.size()==80+header.ciphertextBytes)}
        private fun <T> guarded(block:()->T):T=try {active();val result=block();active();cap.publish {result}}
            catch(e:Throwable) {try {close()}catch(cleanup:Throwable) {e.addSuppressed(cleanup)};throw e}
        fun index()=guarded {index}
        fun manifest(library:UUID)=guarded {manifests.getValue(library).manifest}
        fun <T> records(library:UUID,consume:(ByteArray)->T):T=guarded {
            val copy=records.getValue(library).copyOf();val lease=cap.ownBytes(copy)
            try {consume(copy)}finally {lease.close()}
        }
        /** Internal bridge for existing member staging/reconstruction, never a UI key API. */
        fun source(library:UUID):AttachmentRestoreStaging.CiphertextSource=guarded {
            require(library in manifests)
            object:AttachmentRestoreStaging.CiphertextSource {
                override fun members()=guarded {Collections.unmodifiableList(manifests.getValue(library).manifest.versions.map {it.manifest})}
                override fun takeDek(version:UUID)=dek(library,version)
                override fun open(version:UUID)=Channels.newInputStream(channel(library,version))
            }
        }
        private fun dek(library:UUID,version:UUID):ByteArray {
            var key:ByteArray?=null
            try {
                active();val m=members.getValue(library to version)
                val wrapped=MedicalArchiveCrypto.unhex(manifests.getValue(library).envelopes.getValue(version))
                val result=MedicalArchiveCrypto.unwrap(header,keys.wrapping,m.version.context,wrapped,library).also {key=it}
                active();return cap.publish {result}
            }catch(e:Throwable) {key?.fill(0);try {close()}catch(cleanup:Throwable) {e.addSuppressed(cleanup)};throw e}
        }
        private fun channel(library:UUID,version:UUID):SeekableByteChannel=guarded {
            val m=members.getValue(library to version);var at=0L;var opened=true
            object:SeekableByteChannel {
                override fun read(dst:ByteBuffer):Int=synchronized(reads) {
                    try {
                        check(opened);active();val size=m.version.manifest.ciphertextBytes
                        if(!dst.hasRemaining())0 else if(at==size)-1 else {
                            plain.position(m.offset+at);val limit=dst.limit();dst.limit(dst.position()+minOf(dst.remaining().toLong(),size-at).toInt())
                            try {val n=plain.read(dst);require(n>0);at+=n;active();n}finally {dst.limit(limit)}
                        }
                    }catch(e:Throwable) {opened=false;throw e}
                }
                override fun position()=at
                override fun position(newPosition:Long):SeekableByteChannel {require(newPosition in 0..size());at=newPosition;return this}
                override fun size()=m.version.manifest.ciphertextBytes
                override fun isOpen()=opened
                override fun close(){opened=false}
                override fun write(src:ByteBuffer):Int=throw NonWritableChannelException()
                override fun truncate(size:Long):SeekableByteChannel=throw NonWritableChannelException()
            }
        }
        override fun close()=cap.close()
        companion object {
            fun read(parent:AttachmentSession.Capability,password:CharArray,incoming:SeekableByteChannel):Archive {
                val source=object:SeekableByteChannel by incoming {
                    private var closed=false
                    override fun close() {if(!closed) {closed=true;incoming.close()}}
                }
                var child:AttachmentSession.Capability?=null
                try {
                    check(Looper.myLooper()!=Looper.getMainLooper()) {"Collection IO requires worker"}
                    val cap=parent.fork().also {child=it};cap.own(source)
                    val bytes=ByteBuffer.allocate(80);source.position(0);while(bytes.hasRemaining())require(source.read(bytes)>0)
                    val header=MedicalArchiveCrypto.parseCollection(bytes.array(),source.size())
                    val keys=MedicalArchiveCrypto.derive(header,password,cap);cap.own(keys)
                    val window=object:SeekableByteChannel {
                        private var at=0L
                        override fun read(dst:ByteBuffer):Int {
                            cap.check();require(source.size()==80+header.ciphertextBytes)
                            if(!dst.hasRemaining())return 0
                            if(at==size())return -1
                            source.position(80+at);val limit=dst.limit();dst.limit(dst.position()+minOf(dst.remaining().toLong(),size()-at).toInt())
                            return try {source.read(dst).also {require(it>0);at+=it}}finally {dst.limit(limit)}
                        }
                        override fun position()=at
                        override fun position(newPosition:Long):SeekableByteChannel {require(newPosition in 0..size());at=newPosition;return this}
                        override fun size()=header.ciphertextBytes
                        override fun isOpen()=source.isOpen
                        override fun close()=Unit
                        override fun write(src:ByteBuffer):Int=throw NonWritableChannelException()
                        override fun truncate(size:Long):SeekableByteChannel=throw NonWritableChannelException()
                    }
                    val plain=AttachmentObjectStreams.primitive(keys.outer).newSeekableDecryptingChannel(window,header.aad());cap.own(plain)
                    val hash=MessageDigest.getInstance("SHA-256").apply {update("notes.medical.backup.set.v2\u0000".toByteArray(Charsets.US_ASCII));update(header.bytes())}
                    val input=object:InputStream() {
                        var count=0L;var hashing=true
                        override fun read():Int {val b=ByteArray(1);return if(read(b)<0)-1 else b[0].toInt() and 255}
                        override fun read(b:ByteArray,off:Int,len:Int):Int {
                            cap.check();if(len==0)return 0
                            val n=plain.read(ByteBuffer.wrap(b,off,len))
                            if(n>0) {require(n<=header.plaintextBytes-count);count+=n;if(hashing)hash.update(b,off,n)}else require(n<0)
                            return n
                        }
                    }
                    val data=DataInputStream(input)
                    fun frame(tag:Int,max:Long):Long {require(data.readUnsignedByte()==tag);val n=data.readLong();require(n in 0..max && n<=header.plaintextBytes-input.count);return n}
                    fun id():UUID {val b=ByteArray(16);data.readFully(b);return MedicalArchiveCrypto.uuid(ByteBuffer.wrap(b))}
                    val indexBytes=ByteArray(frame(1,MedicalCollectionIndex.MAX_INDEX.toLong()).toInt());val il=cap.ownBytes(indexBytes)
                    val index=try {data.readFully(indexBytes);MedicalCollectionIndex.decode(indexBytes,cap::check)}finally {il.close()}
                    require(index.collection==header.archive)
                    val manifests=LinkedHashMap<UUID,MedicalArchiveManifest.Decoded>();val records=LinkedHashMap<UUID,ByteArray>();val members=LinkedHashMap<Pair<UUID,UUID>,Member>()
                    val buffer=ByteArray(65536);val bl=cap.ownBytes(buffer)
                    try {for(l in index.libraries) {
                        require(frame(2,16+MedicalArchiveCrypto.MAX_MANIFEST.toLong())==16+l.manifestBytes && id()==l.id)
                        val mb=ByteArray(l.manifestBytes.toInt());val ml=cap.ownBytes(mb)
                        try {
                            data.readFully(mb)
                            require(frame(3,16+BackupLimits.MAX_JSON_BYTES.toLong())==16+l.recordBytes && id()==l.id)
                            val rb=ByteArray(l.recordBytes.toInt());cap.ownBytes(rb);data.readFully(rb)
                            val decoded=index.verifyLibrary(l.id,rb,mb,cap::check);manifests[l.id]=decoded;records[l.id]=rb
                            val versions=decoded.manifest.versions.associateBy {it.context.version}
                            for(m in l.members) {
                                require(frame(4,56+AttachmentObjectStreams.MAXIMUM_BYTES+65536)==56+m.bytes)
                                require(id()==l.id && id()==m.objectId && id()==m.version && data.readLong()==m.bytes)
                                members[l.id to m.version]=Member(input.count,versions.getValue(m.version))
                                val sha=MessageDigest.getInstance("SHA-256");var remaining=m.bytes
                                while(remaining>0) {cap.check();val n=minOf(buffer.size.toLong(),remaining).toInt();data.readFully(buffer,0,n);sha.update(buffer,0,n);remaining-=n}
                                require(MedicalArchiveCrypto.hex(sha.digest())==m.sha256)
                            }
                        }finally {ml.close()}
                    }}finally {bl.close()}
                    val commitment=hash.digest();input.hashing=false
                    require(frame(5,64)==64L);val footer=ByteArray(32);data.readFully(footer);require(MessageDigest.isEqual(commitment,footer))
                    require(data.readLong()==index.libraries.size.toLong())
                    require(data.readLong()==index.libraries.sumOf {it.members.size}.toLong())
                    require(data.readLong()==index.libraries.sumOf {it.businessRows+it.metadataRows} && data.readLong()==header.plaintextBytes)
                    require(input.count==header.plaintextBytes && data.read()==-1 && plain.size()==header.plaintextBytes && source.size()==80+header.ciphertextBytes)
                    val result=Archive(cap,header,index,Collections.unmodifiableMap(manifests),Collections.unmodifiableMap(records),Collections.unmodifiableMap(members),keys,plain,source)
                    for(l in index.libraries)for(v in manifests.getValue(l.id).manifest.versions) {
                        result.active();AttachmentObjectStreams.open(cap,v.manifest,result.dek(l.id,v.context.version),result.channel(l.id,v.context.version)).close()
                    }
                    return cap.publish {result}
                }catch(e:Throwable) {
                    try {child?.close()}catch(cleanup:Throwable) {e.addSuppressed(cleanup)}
                    try {source.close()}catch(cleanup:Throwable) {e.addSuppressed(cleanup)};throw e
                }finally {password.fill('\u0000')}
            }
        }
    }
    fun read(parent:AttachmentSession.Capability,password:CharArray,source:SeekableByteChannel)=Archive.read(parent,password,source)
}
