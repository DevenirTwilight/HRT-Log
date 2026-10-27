package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import java.io.DataOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/** OFF PNMEDBK2 snapshot writer, not retained-library discovery, admission or activation.
 * Snapshots remain caller-owned. A caller must still prove inventory closure before/after export. */
internal object MedicalCollectionWriter {
    data class Source(val id:UUID,val role:MedicalCollectionIndex.Role,val snapshot:AttachmentBackupPreparation.Snapshot)
    data class Receipt(val collection:UUID,val bytes:Long,val libraries:Int,val objects:Int) {val activatable=false}
    fun write(sources:List<Source>,active:UUID,parent:AttachmentSession.Capability,password:CharArray,target:OutputStream):Receipt {
        var child:AttachmentSession.Capability?=null
        val owned=RevocableArchiveOutput(target)
        val sink=object:FilterOutputStream(owned) {
            var count=0L;private var closed=false
            override fun write(b:Int)=write(byteArrayOf(b.toByte()),0,1)
            override fun write(b:ByteArray,off:Int,len:Int) {check(!closed);child?.check();require(len>=0 && len<=MedicalArchiveCrypto.MAX_WIRE-count);out.write(b,off,len);count+=len}
            override fun close() {if(!closed) {closed=true;out.close()}}
        }
        try {
            val cap=parent.fork().also {child=it}
            cap.own(AutoCloseable { owned.revoke() })
            require(sources.size in 1..8 && sources.map {it.id}.toSet().size==sources.size)
            val ordered=sources.sortedBy {it.id.toString()}
            val manifests=ordered.associate {it.id to it.snapshot.manifest()}
            val descriptors=ordered.map {source->
                cap.check();val m=manifests.getValue(source.id)
                val length=MedicalArchiveManifest.encode(m,m.versions.associate {it.context.version to "0".repeat(120)},cap::check).let {b->try {b.size}finally {b.fill(0)}}
                source.snapshot.records {records->
                    val rows=MedicalArchiveRecords.count(records,cap::check)
                    MedicalCollectionIndex.Library(source.id,source.role,m.dataset,m.epoch,m.logicalVault,
                        records.size.toLong(),MedicalCollectionIndex.digest(records),length.toLong(),"0".repeat(64),rows,
                        m.records.size.toLong()+m.documents.size+m.versions.size+m.links.size,
                        m.versions.sortedBy {it.context.version.toString()}.map {v->MedicalCollectionIndex.Member(v.context.objectId,v.context.version,v.manifest.ciphertextBytes,v.manifest.ciphertextSha256)})
                }
            }
            val sizing=MedicalCollectionIndex(UUID(0,0),active,descriptors)
            val indexLength=sizing.encode().size
            var n=Math.addExact(9L+indexLength,73L)
            for(l in descriptors) {
                n=Math.addExact(n,Math.addExact(50L,Math.addExact(l.manifestBytes,l.recordBytes))) // two frame headers + library UUIDs
                l.members.forEach {n=Math.addExact(n,Math.addExact(65L,it.bytes))}
            }
            val header=MedicalArchiveCrypto.freshCollection(n)
            val keys=MedicalArchiveCrypto.derive(header,password,cap);cap.own(keys)
            val wrap=MedicalArchiveCrypto.CollectionWrapAttempt(header,keys.wrapping,ordered.flatMap {s->manifests.getValue(s.id).versions.map {s.id to it.context}})
            val payloads=ordered.associate {source->
                val m=manifests.getValue(source.id)
                val envelopes=m.versions.associate {v->
                    cap.check();v.context.version to source.snapshot.member(v.context.version) {_,dek->MedicalArchiveCrypto.hex(wrap.wrap(source.id,v.context,dek))}
                }
                val encoded=MedicalArchiveManifest.encode(m,envelopes,cap::check)
                cap.ownBytes(encoded);require(encoded.size.toLong()==descriptors.single {it.id==source.id}.manifestBytes)
                source.id to encoded
            }
            val index=MedicalCollectionIndex(header.archive,active,descriptors.map {it.copy(manifestSha256=MedicalCollectionIndex.digest(payloads.getValue(it.id)))})
            val indexBytes=index.encode();cap.ownBytes(indexBytes);require(indexBytes.size==indexLength)
            val headerBytes=header.bytes();sink.write(headerBytes)
            val hash=MessageDigest.getInstance("SHA-256").apply {update("notes.medical.backup.set.v2\u0000".toByteArray(Charsets.US_ASCII));update(headerBytes)}
            AttachmentObjectStreams.primitive(keys.outer).newEncryptingStream(sink,header.aad()).use {encrypted->
                val body=object:FilterOutputStream(encrypted) {
                    var count=0L;var hashing=true
                    override fun write(b:Int)=write(byteArrayOf(b.toByte()),0,1)
                    override fun write(b:ByteArray,off:Int,len:Int) {cap.check();require(len>=0 && len<=n-count);out.write(b,off,len);if(hashing)hash.update(b,off,len);count+=len}
                }
                val out=DataOutputStream(body)
                fun frame(tag:Int,size:Long) {out.writeByte(tag);out.writeLong(size)}
                frame(1,indexBytes.size.toLong());out.write(indexBytes)
                val buffer=ByteArray(65536);val lease=cap.ownBytes(buffer)
                try {for(source in ordered) {
                    val l=index.libraries.single {it.id==source.id}
                    frame(2,16+l.manifestBytes);out.write(MedicalArchiveCrypto.uuidBytes(l.id));out.write(payloads.getValue(l.id))
                    frame(3,16+l.recordBytes);out.write(MedicalArchiveCrypto.uuidBytes(l.id))
                    source.snapshot.records {records->require(records.size.toLong()==l.recordBytes && MedicalCollectionIndex.digest(records)==l.recordSha256);out.write(records)}
                    for(m in l.members) {
                        frame(4,56+m.bytes);out.write(MedicalArchiveCrypto.uuidBytes(l.id));out.write(MedicalArchiveCrypto.uuidBytes(m.objectId));out.write(MedicalArchiveCrypto.uuidBytes(m.version));out.writeLong(m.bytes)
                        source.snapshot.member(m.version) {input,_->
                            val sha=MessageDigest.getInstance("SHA-256");var count=0L
                            while(true) {cap.check();val got=input.read(buffer);if(got<0)break;require(got>0 && got<=m.bytes-count);out.write(buffer,0,got);sha.update(buffer,0,got);count+=got}
                            require(count==m.bytes && MedicalArchiveCrypto.hex(sha.digest())==m.sha256)
                        }
                    }
                }}finally {lease.close()}
                val commitment=hash.digest();body.hashing=false
                frame(5,64);out.write(commitment);out.writeLong(index.libraries.size.toLong());out.writeLong(index.libraries.sumOf {it.members.size}.toLong())
                out.writeLong(index.libraries.sumOf {it.businessRows+it.metadataRows});out.writeLong(n)
                require(body.count==n)
                ordered.forEach {it.snapshot.manifest()} // No stale source scope may acknowledge output.
                cap.check()
            }
            require(sink.count==80+header.ciphertextBytes)
            return cap.publish {Receipt(header.archive,sink.count,index.libraries.size,index.libraries.sumOf {it.members.size})}
        }finally {password.fill('\u0000');try {child?.close()}finally {sink.close()}}
    }
}
