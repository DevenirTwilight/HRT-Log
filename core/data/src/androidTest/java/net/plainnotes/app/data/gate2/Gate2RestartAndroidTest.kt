package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch

/** External script kills only standalone synthetic test APK; ordinary run tests rollback without killing. */
class Gate2RestartAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val marker get()=File(context.filesDir,"synthetic-gate2-restart-ready")
    private val fixture="synthetic-gate2-restart"
    private val payload=ByteArray(150000){(it*17).toByte()}
    @Test fun journalAfterInterruptedRealFileAndSqlCommit() {
        val args=InstrumentationRegistry.getArguments();val seam=Gate2Store.Seam.valueOf(args.getString("gate2_seam")?:"IN_COMMIT")
        when(args.getString("gate2_phase")){
            "prepare"->prepare(seam,true)
            "verify"->verify(seam,true)
            null->{prepare(seam,false);verify(seam,false)}
            else->error("Unknown synthetic phase")
        }
    }
    private fun prepare(seam:Gate2Store.Seam,block:Boolean) {
        marker.delete();File(context.filesDir,"synthetic-gate2-restart-result").delete();Gate2Store(context,fixture).destroy()
        val s=Gate2Store(context,fixture)
        try {
            val old=s.prepare();s.import(old,ByteArrayInputStream(payload));val next=s.prepare()
            val state=JSONObject().put("pid",android.os.Process.myPid()).put("process",s.session.processIdentity().toString())
                .put("old",old.id).put("next",next.id).put("dataset",s.scope.dataset).put("epoch",s.scope.epoch).put("seam",seam.name)
            try{s.import(next,ByteArrayInputStream(payload),fault={if(it==seam){marker.writeText(state.toString());if(block)CountDownLatch(1).await() else throw IOException("Synthetic ordinary rollback")}})}
            catch(e:IOException){if(block)throw e}
        }finally{s.close()}
    }
    private fun verify(seam:Gate2Store.Seam,fresh:Boolean) {
        val state=JSONObject(marker.readText());val s=Gate2Store(context,fixture)
        try {
            if(fresh)assertNotEquals(state.getInt("pid"),android.os.Process.myPid())
            assertNotEquals(state.getString("process"),s.session.processIdentity().toString())
            assertEquals(state.getString("dataset"),s.scope.dataset);assertEquals(state.getString("epoch"),s.scope.epoch)
            val committed=seam==Gate2Store.Seam.AFTER_COMMIT
            assertEquals(state.getString(if(committed)"next" else "old"),s.active());assertEquals(if(committed)2L else 1L,s.revision())
            val result=s.reconcile();assertEquals("KEEP_ACTIVE",result[s.active()])
            val incomplete=seam==Gate2Store.Seam.WRITE||seam==Gate2Store.Seam.AFTER_CREATE
            if(fresh&&!committed)assertEquals(if(incomplete)"CLEANED_OWNED_INCOMPLETE" else "QUARANTINE_COMPLETE_ORPHAN",result[state.getString("next")])
            // A real crash orphan keeps its quota until an explicit confirmed release, which deletes only its own file.
            var release="n/a"
            if(fresh&&seam==Gate2Store.Seam.IN_COMMIT){val next=state.getString("next");val orphan=s.fileOf(next);assertTrue(orphan.exists())
                val held=s.reservedBytes()
                release=s.releaseQuarantined(next,confirmed=true);assertEquals("CLEANED",release);assertFalse(orphan.exists());assertEquals("RETAIN_TERMINAL",s.reconcile()[next])
                // Idempotent: a repeated release is refused and frees nothing twice; repeated recovery is stable.
                val freed=s.reservedBytes();assertTrue(freed<held)
                try{s.releaseQuarantined(next,confirmed=true);fail("Released twice")}catch(e:IllegalStateException){assertEquals("Not a quarantined orphan",e.message)}
                assertEquals(freed,s.reservedBytes());assertEquals("RETAIN_TERMINAL",s.reconcile()[next])}
            s.open().use{assertEquals(payload.size.toLong(),it.size())};s.assertProductionOff()
            s.raw.query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}
            File(context.filesDir,"synthetic-gate2-restart-result").writeText(JSONObject().put("previous_pid",state.getInt("pid")).put("fresh_pid",android.os.Process.myPid()).put("fresh_process_asserted",fresh).put("api",android.os.Build.VERSION.SDK_INT).put("abi",android.os.Build.SUPPORTED_ABIS.first()).put("seam",seam.name).put("revision",s.revision()).put("reconcile",JSONObject(result)).put("orphan_release",release).put("production_enabled",0).toString())
            android.util.Log.i("GATE2","api=${android.os.Build.VERSION.SDK_INT} fresh_pid=$fresh seam=$seam revision=${s.revision()} state=$result production_enabled=0")
        }finally{s.destroy();marker.delete()}
    }
}
