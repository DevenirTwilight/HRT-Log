package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable

/** Uses only synthetic originals. Source aliases are actually destroyed before destination read;
 * destination has another SQLCipher key/dataset/Keystore. No production deletion/activation path.
 */
class MedicalArchiveAndroidTest:MedicalArchiveContract() {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext
    override fun canonical()=instrumentation.context.assets.open("portable-review/canonical.bin").use {it.readBytes()}
    private fun business(f:AttachmentBackupPreparationAndroidTest.Fixture)=f.db.runInTransaction(Callable {MeasuredBackupJson.encode(f.raw,Instant.EPOCH)})
    private fun allRows(f:AttachmentBackupPreparationAndroidTest.Fixture):String =buildString {
        val tables=f.raw.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use {c->buildList {while(c.moveToNext())add(c.getString(0))}}
        tables.forEach {t->append(t);f.raw.query("SELECT * FROM \"${t.replace("\"","\"\"")}\" ORDER BY rowid").use {c->while(c.moveToNext()) {
            for(i in 0 until c.columnCount) {append(c.getType(i));append(':');append(if(c.isNull(i))"null" else if(c.getType(i)==android.database.Cursor.FIELD_TYPE_BLOB)android.util.Base64.encodeToString(c.getBlob(i),android.util.Base64.NO_WRAP) else org.json.JSONObject.quote(c.getString(i)));append(';')};append('\n')
        }}}
    }
    @Test fun productionExportSurvivesLossOfAllSourceAliasesAndReconstructsHistoryTrashAndTypedGraph() {
        val archive=File.createTempFile("synthetic-portable-",".cipher",app.cacheDir)
        var original:ByteArray?=null;lateinit var manifest:AttachmentBackupManifest;lateinit var aliases:List<String>
        try {
            AttachmentBackupPreparationAndroidTest.Fixture(app).use {source->
                AttachmentBusinessRestoreAndroidTest().build(source)
                AttachmentBackupPreparation(source.context,source.db).capture(source.scope,source.session.issue(),created=Instant.EPOCH).use {snapshot->
                    manifest=snapshot.manifest();snapshot.records {original=it.copyOf()}
                    aliases=source.raw.query("SELECT wrapping_key_alias FROM attachment_key_envelope").use {c->buildList {while(c.moveToNext())add(c.getString(0))}}
                    val before=allRows(source);val p="SYNTHETIC 移动 archive password".toCharArray()
                    val receipt=MedicalArchiveWriter.write(snapshot,source.session.issue(),p,archive.outputStream())
                    assertEquals(archive.length(),receipt.bytes);assertEquals(4,receipt.objects);assertFalse(receipt.activatable)
                    assertTrue(p.all {it=='\u0000'});assertEquals(before,allRows(source))
                    // This fixture deliberately trashes appointment 1. The simple preparation
                    // fixture's safety() assumes it is live, so check the actual retained meaning.
                    assertEquals(0L,source.number("SELECT feature_enabled FROM attachment_store WHERE store_state='CURRENT'"))
                    source.raw.query("PRAGMA foreign_key_check").use {assertEquals(0,it.count)}
                    assertEquals(0L,source.number("SELECT COUNT(*) FROM appointment WHERE id=1"))
                    assertEquals("TRASHED",manifest.records.single {it.kind==AttachmentBackupManifest.Kind.APPOINTMENT && it.sourceId==1L}.state)
                }
            }
            val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            assertTrue(aliases.isNotEmpty());aliases.forEach {assertFalse(ks.containsAlias(it))}
            AttachmentBackupPreparationAndroidTest.Fixture(app,ByteArray(32) {81}).use {destination->
                assertNotEquals(manifest.dataset.toString(),destination.scope.dataset)
                val before=allRows(destination);val old=business(destination)
                MedicalArchiveReader.read(destination.session.issue(),"SYNTHETIC 移动 archive password".toCharArray(),FileInputStream(archive).channel).use {reader->
                    assertTrue(reader.wholeArchiveAuthenticated);assertFalse(reader.activatable)
                    assertEquals(AttachmentCandidateMetadata.normalized(manifest),reader.manifest())
                    reader.records {assertArrayEquals(original,it)}
                    val ns=AttachmentRestorePreflight.Namespace(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())
                    val targets=manifest.records.map {AttachmentBackupManifest.Target(it.uuid,it.kind,if(it.state=="PURGED")null else it.sourceId)}
                    val rows=manifest.records.filter {it.state!="PURGED"}.map {AttachmentRestorePreflight.TargetRow(it.uuid,it.kind,it.sourceId,it.state)}
                    val plan=reader.preflight(ns,targets,rows,setOf(UUID.fromString(destination.scope.dataset),UUID.fromString(destination.scope.epoch)),
                        AttachmentRestorePreflight.Resources(1024L*1024*1024,4096,100,100))
                    reader.reconstruct(destination.context,destination.db,destination.scope,plan).use {candidate->
                        assertFalse(candidate.activatable);assertEquals(4,candidate.objects.objects.size)
                        assertEquals(AttachmentCandidateMetadata.normalized(manifest.copy(dataset=ns.dataset,epoch=ns.epoch)),candidate.manifest())
                        candidate.records {assertArrayEquals(original,it)}
                        // Real destination key envelopes/active generation are never inserted by a candidate.
                        assertEquals(before,allRows(destination));assertArrayEquals(old,business(destination));destination.safety()
                    }
                    reject {reader.preflight(AttachmentRestorePreflight.Namespace(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()),targets,rows,emptySet(),
                        AttachmentRestorePreflight.Resources(0,4096,0,0))}
                }
                assertEquals(before,allRows(destination));old.fill(0)
            }
        } finally {original?.fill(0);check(archive.delete())}
    }
    @Test fun missingRetainedOriginalAfterCaptureAndCancelledConsumerNeverReportExportSuccess() {
        AttachmentBackupPreparationAndroidTest.Fixture(app).use {f->
            val bytes=ByteArray(70123) {5};val r=f.request(bytes)
            f.store().save(f.scope,f.session.issue(),r) {java.io.ByteArrayInputStream(bytes)}
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue()).use {snapshot->
                val m=snapshot.manifest();val original=File(f.directory,"attachment-objects-v1/${m.versions.single().context.objectId}")
                val hidden=File(f.directory,"synthetic-hidden-cipher");assertTrue(original.renameTo(hidden))
                val output=object:ByteArrayOutputStream() {var closed=false;override fun close() {closed=true;super.close()}}
                val p="SYNTHETIC failed export".toCharArray();val before=allRows(f)
                reject {MedicalArchiveWriter.write(snapshot,f.session.issue(),p,output)}
                assertTrue(p.all {it=='\u0000'});assertTrue(output.closed);assertEquals(before,allRows(f));assertTrue(hidden.renameTo(original))
            }
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue()).use {snapshot->
                val before=allRows(f)
                reject {snapshot.records {it[0]=0}} // Cannot export caller-mutated borrowed facts.
                val p="SYNTHETIC mutated snapshot".toCharArray()
                reject {MedicalArchiveWriter.write(snapshot,f.session.issue(),p,ByteArrayOutputStream())}
                assertTrue(p.all {it=='\u0000'});assertEquals(before,allRows(f))
            }
            AttachmentBackupPreparation(f.context,f.db).capture(f.scope,f.session.issue()).use {snapshot->
                val cap=f.session.issue();lateinit var borrowed:ByteArray
                reject {snapshot.member(snapshot.manifest().versions.single().context.version) {_,key->
                    borrowed=key;f.session.revoke();cap.check()
                }}
                assertTrue(borrowed.all {it==0.toByte()})
            }
        }
    }
    @Test fun collectionWriterAndIndependentReaderPreserveTwoTypedLibrariesAfterSourceKeyLoss() {
        val file=File.createTempFile("synthetic-collection-",".cipher",app.cacheDir)
        val samples=org.json.JSONArray()
        fun sample(stage:String) {
            val vm=Runtime.getRuntime();val memory=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(memory)
            val status=File("/proc/self/status").readLines()
            fun kib(name:String)=status.firstOrNull {it.startsWith("$name:")}?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()
            samples.put(org.json.JSONObject().put("stage",stage).put("javaUsedBytes",vm.totalMemory()-vm.freeMemory())
                .put("javaMaxBytes",vm.maxMemory()).put("nativeHeapAllocatedBytes",android.os.Debug.getNativeHeapAllocatedSize())
                .put("totalPssKiB",memory.totalPss).put("rssKiB",kib("VmRSS") ?: org.json.JSONObject.NULL).put("processHwmKiB",kib("VmHWM") ?: org.json.JSONObject.NULL))
            File(app.filesDir,"synthetic-collection-memory.json").writeText(org.json.JSONObject().put("api",android.os.Build.VERSION.SDK_INT)
                .put("pid",android.os.Process.myPid()).put("sampledNotPeak",true).put("samples",samples).toString())
        }
        sample("before_export")
        val active=UUID(0,1);val retained=UUID(0,2)
        val expected=mutableMapOf<UUID,AttachmentBackupManifest>();val records=mutableMapOf<UUID,ByteArray>();val aliases=mutableListOf<String>()
        try {
            AttachmentBackupPreparationAndroidTest.Fixture(app).use {first->
                AttachmentBackupPreparationAndroidTest.Fixture(app,ByteArray(32) {89}).use {second->
                    AttachmentBusinessRestoreAndroidTest().build(first) // PDF/PNG/binary, replacement, trash, typed graph.
                    val bytes=byteArrayOf(8,5,3,2,1)
                    second.store().save(second.scope,second.session.issue(),second.request(bytes)) {java.io.ByteArrayInputStream(bytes)}
                    AttachmentBackupPreparation(first.context,first.db).capture(first.scope,first.session.issue(),created=Instant.EPOCH).use {a->
                        AttachmentBackupPreparation(second.context,second.db).capture(second.scope,second.session.issue(),created=Instant.EPOCH).use {b->
                            for((id,snapshot) in listOf(active to a,retained to b)) {expected[id]=snapshot.manifest();snapshot.records {records[id]=it.copyOf()}}
                            for(f in listOf(first,second))f.raw.query("SELECT wrapping_key_alias FROM attachment_key_envelope").use {c->while(c.moveToNext())aliases+=c.getString(0)}
                            val before=listOf(allRows(first),allRows(second));val password="SYNTHETIC collection 密码".toCharArray()
                            val receipt=MedicalCollectionWriter.write(listOf(
                                MedicalCollectionWriter.Source(retained,MedicalCollectionIndex.Role.RETAINED,b),
                                MedicalCollectionWriter.Source(active,MedicalCollectionIndex.Role.ACTIVE,a)),active,first.session.issue(),password,file.outputStream())
                            sample("after_export")
                            assertEquals(2,receipt.libraries);assertEquals(5,receipt.objects);assertEquals(file.length(),receipt.bytes);assertFalse(receipt.activatable)
                            assertTrue(password.all {it=='\u0000'});assertEquals(before,listOf(allRows(first),allRows(second)))
                        }
                    }
                }
            }
            sample("after_source_close")
            val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)};assertTrue(aliases.isNotEmpty());aliases.forEach {assertFalse(ks.containsAlias(it))}
            val session=session()
            try {
                val pass="SYNTHETIC collection 密码".toCharArray()
                MedicalCollectionReader.read(session.issue(),pass,FileInputStream(file).channel).use {archive->
                    sample("after_independent_authentication")
                    assertTrue(pass.all {it=='\u0000'});assertTrue(archive.wholeArchiveAuthenticated);assertFalse(archive.activatable)
                    assertEquals(active,archive.index().active);assertEquals(2,archive.index().libraries.size)
                    for(id in listOf(active,retained)) {
                        val m=archive.manifest(id);assertEquals(AttachmentCandidateMetadata.normalized(expected.getValue(id)),m)
                        archive.records(id) {assertArrayEquals(records.getValue(id),it)}
                        val source=archive.source(id)
                        assertEquals(m.versions.map {it.manifest},source.members())
                        for(v in m.versions) {
                            val key=source.takeDek(v.context.version)
                            try {AttachmentObjectStreams.primitive(key).newDecryptingStream(source.open(v.context.version),v.context.encode()).use {input->
                                val sha=java.security.MessageDigest.getInstance("SHA-256");val buffer=ByteArray(4096);var count=0L
                                while(true) {val n=input.read(buffer);if(n<0)break;require(n>0);sha.update(buffer,0,n);count+=n}
                                assertEquals(v.manifest.plaintextBytes,count);assertEquals(v.manifest.plaintextSha256,MedicalArchiveCrypto.hex(sha.digest()));buffer.fill(0)
                            }}finally {key.fill(0)}
                        }
                    }
                    sample("after_all_original_hashes")
                    reject {archive.source(UUID.randomUUID())}
                }
                reject {MedicalCollectionReader.read(session.issue(),"wrong password".toCharArray(),FileInputStream(file).channel)}
                val corrupt=File.createTempFile("synthetic-collection-corrupt-",".cipher",app.cacheDir)
                try {
                    val bytes=file.readBytes();bytes[bytes.size-1]=(bytes.last().toInt() xor 1).toByte();corrupt.writeBytes(bytes)
                    reject {MedicalCollectionReader.read(session.issue(),"SYNTHETIC collection 密码".toCharArray(),FileInputStream(corrupt).channel)}
                    corrupt.writeBytes(file.readBytes().dropLast(1).toByteArray())
                    reject {MedicalCollectionReader.read(session.issue(),"SYNTHETIC collection 密码".toCharArray(),FileInputStream(corrupt).channel)}
                }finally {check(corrupt.delete())}
                MedicalCollectionReader.read(session.issue(),"SYNTHETIC collection 密码".toCharArray(),FileInputStream(file).channel).use {archive->
                    val old=archive.source(active);session.revoke();reject {old.takeDek(expected.getValue(active).versions.first().context.version)}
                }
            }finally {session.revoke()}
        }finally {records.values.forEach {it.fill(0)};check(file.delete())}
    }

}
