package net.plainnotes.app.data

import net.plainnotes.app.domain.AttachmentSession
import java.io.DataOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Internal §147.D OFF writer. A complete ciphertext stream is not a committed restore or activation.
 * No paths, archive SQL, device aliases, source database keys or plaintext staging files.
 */
internal object MedicalArchiveWriter {
    data class Receipt(val archive:java.util.UUID,val bytes:Long,val objects:Int) {val activatable=false}
    /** Sole password/target ownership on ALL paths. No resumable encryption attempt. */
    fun write(snapshot:AttachmentBackupPreparation.Snapshot,parent:AttachmentSession.Capability,
        password:CharArray,target:OutputStream):Receipt {
        var child:AttachmentSession.Capability?=null
        val sink=object:FilterOutputStream(target) {
            private var closed=false
            var count=0L
            override fun write(b:Int)=write(byteArrayOf(b.toByte()),0,1)
            override fun write(b:ByteArray,off:Int,len:Int) {check(!closed);child?.check();require(len<=MedicalArchiveCrypto.MAX_WIRE-count);out.write(b,off,len);count+=len}
            @Synchronized override fun close() {if(!closed) {closed=true;out.close()}}
        }
        try {
            val cap=parent.fork().also {child=it};cap.own(sink)
            val m=snapshot.manifest()
            val versions=m.versions.sortedWith(compareBy(MedicalArchiveCrypto.uuidOrder) {it.context.version})
            require(versions.size<=1000)
            return snapshot.records {records->
                val rows=MedicalArchiveRecords.count(records) {cap.check()}
                require(rows+m.records.size+m.documents.size+m.versions.size+m.links.size<=100000)
                val placeholders=versions.associate {it.context.version to "00".repeat(60)}
                val template=MedicalArchiveManifest.encode(m,placeholders) {cap.check()}
                val manifestLength=template.size;template.fill(0)
                var n=Math.addExact(91L,Math.addExact(manifestLength.toLong(),records.size.toLong()))
                versions.forEach {n=Math.addExact(n,Math.addExact(49L,it.manifest.ciphertextBytes))}
                val header=MedicalArchiveCrypto.fresh(n)
                MedicalArchiveCrypto.derive(header,password,cap).use {keys->
                    val lease=cap.own(keys)
                    try {
                        val wrap=MedicalArchiveCrypto.WrapAttempt(header,keys.wrapping,versions.map {it.context})
                        val envelopes=versions.associate {v->cap.check();v.context.version to snapshot.member(v.context.version) {_,key->
                            MedicalArchiveCrypto.hex(wrap.wrap(v.context,key))}}
                        val manifest=MedicalArchiveManifest.encode(m,envelopes) {cap.check()}
                        val ownedManifest=cap.ownBytes(manifest)
                        try {
                            require(manifest.size==manifestLength)
                            val headerBytes=header.bytes();sink.write(headerBytes)
                            val hash=MessageDigest.getInstance("SHA-256")
                            hash.update("notes.medical.backup.set.v1\u0000".toByteArray(Charsets.US_ASCII));hash.update(headerBytes)
                            val cipher=AttachmentObjectStreams.primitive(keys.outer).newEncryptingStream(sink,header.aad())
                            cipher.use {encrypted->
                                val body=object:FilterOutputStream(encrypted) {
                                    var count=0L;var hashing=true
                                    override fun write(b:Int)=write(byteArrayOf(b.toByte()),0,1)
                                    override fun write(b:ByteArray,off:Int,len:Int) {cap.check();require(len<=n-count);out.write(b,off,len);if(hashing)hash.update(b,off,len);count+=len}
                                }
                                val out=DataOutputStream(body)
                                fun frame(tag:Int,size:Long) {out.writeByte(tag);out.writeLong(size)}
                                frame(1,manifest.size.toLong());out.write(manifest)
                                frame(2,records.size.toLong());out.write(records)
                                val buffer=ByteArray(65536);val bufferLease=cap.ownBytes(buffer)
                                try {versions.forEach {v->
                                    frame(3,Math.addExact(40L,v.manifest.ciphertextBytes))
                                    out.write(MedicalArchiveCrypto.uuidBytes(v.context.objectId));out.write(MedicalArchiveCrypto.uuidBytes(v.context.version));out.writeLong(v.manifest.ciphertextBytes)
                                    snapshot.member(v.context.version) {input,_->
                                        val sha=MessageDigest.getInstance("SHA-256");var count=0L
                                        while(true) {cap.check();val got=input.read(buffer);if(got<0)break;require(got>0 && got<=v.manifest.ciphertextBytes-count)
                                            out.write(buffer,0,got);sha.update(buffer,0,got);count+=got}
                                        require(count==v.manifest.ciphertextBytes && MedicalArchiveCrypto.hex(sha.digest())==v.manifest.ciphertextSha256)
                                    }
                                }} finally {bufferLease.close()}
                                val commitment=hash.digest();body.hashing=false
                                frame(4,64);out.write(commitment);out.writeLong(versions.size.toLong()+3);out.writeLong(versions.size.toLong());out.writeLong(rows);out.writeLong(n)
                                require(body.count==n);cap.check()
                            }
                            require(sink.count==header.ciphertextBytes+80)
                            cap.publish {Receipt(header.archive,sink.count,versions.size)}
                        } finally {ownedManifest.close()}
                    } finally {lease.close()}
                }
            }
        } finally {password.fill('\u0000');try {child?.close()} finally {sink.close()}}
    }
}

/** Reuse strict versioned business decoding; never normalize/re-encode authenticated frame bytes. */
internal object MedicalArchiveRecords {
    fun count(bytes:ByteArray,active:()->Unit):Long {
        require(bytes.size<=BackupLimits.MAX_JSON_BYTES)
        val copy=bytes.copyOf()
        return BackupJsonReader.indexed(copy,checkActive=active).use {input->
            val version=RecordBackupCompatibility.readVersion(input.metadata)
            require(version in 12..RecordBackupCompatibility.SCHEMA && input.tables.keys()==DOMAIN_TABLES.toSet())
            RecordBackupCompatibility.validateInventoryVersion(version,input.tables)
            RecordBackupCompatibility.validateReferenceVersion(version,input.tables)
            var count=0L
            DOMAIN_TABLES.forEach {active();count=Math.addExact(count,input.tables.getJSONArray(it).length().toLong());require(count<=100000)}
            count
        }
    }
}
