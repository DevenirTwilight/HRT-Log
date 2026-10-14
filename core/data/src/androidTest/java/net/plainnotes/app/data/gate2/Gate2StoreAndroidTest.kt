package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.*

/** Native SQLCipher/Keystore/filesystem evidence. All data/aliases belong to the library test APK. */
class Gate2StoreAndroidTest {
    @org.junit.Rule @JvmField val testName=org.junit.rules.TestName()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val bytes=ByteArray(140000){(it*31).toByte()}
    private fun fails(block:()->Unit):Throwable {
        try{block()}catch(t:Throwable){return t};throw AssertionError("Expected rejection")
    }
    private fun fixture(test:(Gate2Store)->Unit){val start=android.os.SystemClock.elapsedRealtime();val s=Gate2Store(context);try{test(s);s.assertProductionOff()}finally{val info=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(info);android.util.Log.i("GATE2_METRIC","method=${testName.methodName} api=${android.os.Build.VERSION.SDK_INT} elapsed_ms=${android.os.SystemClock.elapsedRealtime()-start} final_pss_kib=${info.totalPss} sample=final_not_peak");s.destroy()}}
    private fun initial(s:Gate2Store):Gate2Store.Operation=s.prepare().also{s.import(it,ByteArrayInputStream(bytes))}

    @Test fun allPrecommitFaultsLeaveOldCurrentAndRealSqlRollback()=fixture { s ->
        val old=initial(s)
        for(seam in Gate2Store.Seam.entries.filter{it!=Gate2Store.Seam.AFTER_COMMIT}) {
            val next=s.prepare();val revision=s.revision()
            fails{s.import(next,ByteArrayInputStream(bytes),fault={if(it==seam)throw IOException("Synthetic injected $seam")})}
            assertEquals(old.id,s.active());assertEquals(revision,s.revision());assertEquals("ABORTED",s.phase(next.id));assertFalse(s.file(next).exists())
            s.open().use {assertEquals(bytes.size.toLong(),it.size())}
        }
    }
    @Test fun postCommitAckLossRetainsDurableActiveAndDoesNotRepeatCas()=fixture { s ->
        val old=initial(s);val next=s.prepare()
        fails{s.import(next,ByteArrayInputStream(bytes),fault={if(it==Gate2Store.Seam.AFTER_COMMIT)throw IOException("Synthetic ack lost")},
            unsafeCleanupForRedTest=InstrumentationRegistry.getArguments().getString("gate2_red")=="true")}
        assertEquals(next.id,s.active());assertTrue("Committed active must survive lost acknowledgement",s.file(next).exists())
        assertEquals("COMMITTED",s.phase(next.id));assertEquals(2L,s.revision());assertTrue(s.file(old).exists())
        assertEquals("KEEP_ACTIVE",s.reconcile()[next.id]);assertEquals(2L,s.revision())
    }
    @Test fun idempotencyReservationAndRetriesHaveFreshCryptographicIdentities()=fixture { s ->
        val token=UUID.randomUUID();val one=s.prepare(token);val duplicate=s.prepare(token)
        assertEquals(one.id,duplicate.id);assertEquals(one.attempt,duplicate.attempt)
        fails{s.import(one,object:InputStream(){override fun read():Int=throw IOException("Synthetic source failure")})}
        val retry=s.prepare();assertNotEquals(one.attempt,retry.attempt);assertNotEquals(one.version,retry.version)
        assertFalse(one.context.contentEquals(retry.context))
        s.prepare(reservation=17L*1024*1024);s.prepare(reservation=17L*1024*1024)
        fails{s.prepare(reservation=17L*1024*1024)}
        s.raw.query("SELECT count(*) FROM synthetic_gate2_operation").use{assertTrue(it.moveToFirst());assertEquals(4,it.getInt(0))}
    }
    @Test fun twoRealSqlWritersOneCasWinner()=fixture { first ->
        initial(first);val second=Gate2Store(context,first.fixture)
        val a=first.prepare();val b=second.prepare();val barrier=CyclicBarrier(2);val pool=Executors.newFixedThreadPool(2)
        try {
            val futures=listOf(first to a,second to b).map{(s,op)->pool.submit(Callable {
                try{s.import(op,ByteArrayInputStream(bytes),fault={if(it==Gate2Store.Seam.BEFORE_COMMIT)barrier.await(20,TimeUnit.SECONDS)});true}catch(e:IllegalStateException){false}
            })}
            assertEquals(1,futures.count{it.get(40,TimeUnit.SECONDS)});assertEquals(2L,first.revision())
            assertEquals(setOf("COMMITTED","ABORTED"),setOf(first.phase(a.id),first.phase(b.id)));first.open().close()
        }finally{pool.shutdownNow();second.close()}
    }
    @Test fun revokedBeforeCommitCannotPublishAndReadDeliveryIsRevoked()=fixture { s ->
        val old=initial(s);val lease=s.open();val next=s.prepare()
        fails{s.import(next,ByteArrayInputStream(bytes),fault={if(it==Gate2Store.Seam.BEFORE_COMMIT)s.session.revoke()})}
        assertEquals(old.id,s.active());assertEquals(1L,s.revision())
        val output=ByteBuffer.allocate(20);fails{lease.readAt(0,output)};assertEquals(0,output.position());lease.close()
    }
    @Test fun inCommitRevokeWaitsForEntireNativeCommit()=fixture { s ->
        val op=s.prepare();val inCommit=CountDownLatch(1);val leave=CountDownLatch(1);val revoked=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(2)
        try{
            val write=pool.submit {s.import(op,ByteArrayInputStream(bytes),fault={if(it==Gate2Store.Seam.IN_COMMIT){inCommit.countDown();check(leave.await(20,TimeUnit.SECONDS))}})}
            assertTrue(inCommit.await(20,TimeUnit.SECONDS))
            val revoke=pool.submit{s.session.revoke();revoked.countDown()}
            assertFalse(revoked.await(100,TimeUnit.MILLISECONDS));leave.countDown();write.get(20,TimeUnit.SECONDS);revoke.get(20,TimeUnit.SECONDS)
            assertEquals(op.id,s.active());assertEquals("COMMITTED",s.phase(op.id));fails{s.open()}
        }finally{leave.countDown();pool.shutdownNow()}
    }
    @Test fun cleanupFailureReconcilesOnlyOwnedFileAndGcRespectsLease()=fixture { s ->
        val old=initial(s);val foreign=File(s.root,"foreign-synthetic").apply{writeText("not owned")}
        val failed=s.prepare();fails{s.import(failed,ByteArrayInputStream(bytes),fault={if(it==Gate2Store.Seam.WRITE)throw IOException("Injected ENOSPC only")},cleanup={false})}
        assertEquals("CLEANUP_PENDING",s.phase(failed.id));assertTrue(s.file(failed).exists())
        assertEquals("CLEANED_OWNED_INCOMPLETE",s.reconcile()[failed.id]);assertTrue(foreign.exists())
        val held=s.open(old.id);val next=initial(s);assertEquals(next.id,s.active());assertFalse(s.gc(old.id));held.close();held.close()
        assertTrue(s.gc(old.id));assertTrue(foreign.exists());fails{s.gc(next.id)}
    }
    @Test fun exclusiveCreateAndNoFollowNeverOverwriteExistingOrSymlinkTargets()=fixture { s ->
        val op=s.prepare();val f=s.file(op);f.parentFile!!.mkdirs();f.writeText("synthetic existing")
        fails{s.import(op,ByteArrayInputStream(bytes))};assertEquals("synthetic existing",f.readText())
        val other=s.prepare();val link=s.file(other);link.parentFile!!.mkdirs();Files.createSymbolicLink(link.toPath(),f.toPath())
        fails{s.import(other,ByteArrayInputStream(bytes))};assertEquals("synthetic existing",f.readText());assertTrue(Files.isSymbolicLink(link.toPath()))
    }
    @Test fun trustedCommitmentTamperAndLostKeystoreAliasFailClosedAcrossReopen() {
        val s=Gate2Store(context);val fixture=s.fixture
        try {
            val op=initial(s)
            s.raw.execSQL("UPDATE synthetic_gate2_operation SET cipher_hash=? WHERE id=?",arrayOf(ByteArray(32),op.id));fails{s.open()}
            s.removeActiveAlias();fails{s.open()};s.close()
            val reopened=Gate2Store(context,fixture)
            try{fails{reopened.open()};assertEquals(op.id,reopened.active());reopened.assertProductionOff()}finally{reopened.destroy()}
        }catch(t:Throwable){runCatching{s.destroy()};throw t}
    }
    @Test fun realReadLimitStalledSourceAndCancelledOwnerNeverCommit()=fixture { s ->
        val stalled=s.prepare();fails{s.import(stalled,object:InputStream(){override fun read()=0;override fun read(b:ByteArray,off:Int,len:Int)=0})}
        assertEquals("ABORTED",s.phase(stalled.id))
        val over=s.prepare(reservation=17L*1024*1024);var count=0L
        fails{s.import(over,object:InputStream(){override fun read()=1;override fun read(b:ByteArray,off:Int,len:Int):Int{val limit=16L*1024*1024+1;if(count==limit)return -1;val n=minOf(len.toLong(),limit-count).toInt();b.fill(1,off,off+n);count+=n;return n}})}
        assertNull(s.active());assertFalse(s.file(over).exists())
        val cancelled=s.prepare();val cap=s.session.issue();cap.close();fails{s.import(cancelled,ByteArrayInputStream(bytes),cap)};assertNull(s.active())
    }
    @Test fun actualPnBakNineRestoreRetiresSqlIdentityAndQuarantinesExistingCipherAcrossReopen() {
        val s=Gate2Store(context);val name=s.fixture
        try {
            val op=initial(s)
            s.raw.execSQL("INSERT INTO appointment(id,type,at_utc,at_zone,note,remind_minutes_before) VALUES (1,'ENDO',1000,'UTC','synthetic gate2 record',0)")
            val oldIdentity=s.db.runInTransaction(Callable{AttachmentIdentity.bind(s.raw,s.scope,"APPOINTMENT",1)})
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=s.db})
            val password="synthetic-gate2-password".toCharArray()
            kotlinx.coroutines.runBlocking {
                val backup=repo.exportBackup(password);val clear=BackupCodec.decrypt(backup,password)
                try{val j=org.json.JSONObject(String(clear,Charsets.UTF_8));assertEquals(9,j.getInt("schema"));assertFalse(j.getJSONObject("tables").keys().asSequence().any{it.startsWith("attachment_")||it.startsWith("synthetic_")})}finally{clear.fill(0)}
                repo.restoreBackup(backup,password)
            };password.fill('\u0000')
            val fresh=AttachmentIdentity.current(s.raw)!!;assertNotEquals(s.scope.dataset,fresh.dataset);assertNotEquals(s.scope.epoch,fresh.epoch)
            val newIdentity=s.db.runInTransaction(Callable{AttachmentIdentity.bind(s.raw,fresh,"APPOINTMENT",1)});assertNotEquals(oldIdentity,newIdentity)
            fails{s.open()};assertEquals("QUARANTINED_EPOCH",s.reconcile()[op.id]);assertTrue(s.file(op).exists());s.close()
            val reopened=Gate2Store(context,name)
            try{fails{reopened.open()};assertEquals("QUARANTINED_EPOCH",reopened.reconcile()[op.id]);reopened.assertProductionOff()}finally{reopened.destroy()}
        }catch(t:Throwable){runCatching{s.destroy()};throw t}
    }
    @Test fun stagingActiveRolesAndCrossObjectWrappedKeysCannotBeSubstituted()=fixture { s ->
        val op=initial(s)
        s.raw.execSQL("UPDATE synthetic_gate2_operation SET active_wrap=stage_wrap WHERE id=?",arrayOf(op.id));fails{s.open()}
        val a=s.prepare();val b=s.prepare()
        s.raw.execSQL("UPDATE synthetic_gate2_operation SET stage_wrap=(SELECT stage_wrap FROM synthetic_gate2_operation WHERE id=?) WHERE id=?",arrayOf(a.id,b.id))
        fails{s.import(b,ByteArrayInputStream(bytes))};assertEquals("PREPARED",s.phase(b.id));assertFalse(s.file(b).exists())
    }
    @Test fun parentReplacementCannotDeleteForeignCipherOnFailure()=fixture { s ->
        val op=s.prepare();val target=s.file(op);var foreign:File?=null
        fails{s.import(op,ByteArrayInputStream(bytes),fault={if(it==Gate2Store.Seam.BEFORE_DIR_SYNC){
            val moved=File(s.root,"owned-moved-${op.physical}");check(target.parentFile!!.renameTo(moved));check(target.parentFile!!.mkdirs())
            foreign=target.apply{writeText("synthetic foreign replacement")}
        }})}
        assertEquals("CLEANUP_PENDING",s.phase(op.id));assertEquals("synthetic foreign replacement",foreign!!.readText())
        assertEquals("QUARANTINE_FOREIGN_PATH",s.reconcile()[op.id]);assertEquals("synthetic foreign replacement",foreign!!.readText())
        // Persisted inode/device ownership survives cleanup retries; no pathname-based deletion.
    }

    @Test fun foreignSessionCannotCommitIntoOtherSqlCipherDataset()=fixture { s ->
        val other=Gate2Store(context)
        try{val op=s.prepare();fails{s.import(op,ByteArrayInputStream(bytes),other.session.issue())};assertNull(s.active());assertFalse(s.file(op).exists())
            fails{other.session.issue(primary=false)};assertNotEquals(s.scope.dataset,other.scope.dataset);other.assertProductionOff()
        }finally{other.destroy()}
    }

    @Test fun nativeEmptyOneAndSegmentBoundariesAndCipherOverheadReservation()=fixture { s ->
        for(n in listOf(0,1,65480,65481,65536,65537)){
            val op=s.prepare();s.import(op,ByteArrayInputStream(ByteArray(n){(it*7).toByte()}))
            s.open().use{assertEquals(n.toLong(),it.size());if(n>0){val sample=ByteBuffer.allocate(1);assertEquals(1,it.readAt(n-1L,sample));assertEquals(((n-1)*7).toByte(),sample.array()[0])}}
        }
        val old=s.active();val overflow=s.prepare(reservation=8192)
        fails{s.import(overflow,ByteArrayInputStream(ByteArray(8192)))}
        assertEquals(old,s.active());assertEquals("ABORTED",s.phase(overflow.id));assertFalse(s.file(overflow).exists())
    }

}
