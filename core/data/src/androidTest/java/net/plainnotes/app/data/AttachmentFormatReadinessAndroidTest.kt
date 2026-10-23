package net.plainnotes.app.data

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** TEST_ONLY resource evidence for the single unapproved tuple. Not a production password API,
 * full archive, joint memory budget or format approval. No low-cost fallback.
 */
class AttachmentFormatReadinessAndroidTest {
    @Test fun proposedArgonTupleMatchesIndependentReferenceAndRecordsIsolatedResourceCost() {
        val runtime=Runtime.getRuntime()
        System.gc() // Explicit isolated cold-KDF probe; does not model simultaneous SQL/manifest use.
        val running=AtomicBoolean(true);val peakHeap=AtomicLong(0);val peakPss=AtomicLong(0)
        fun sample() {peakHeap.updateAndGet {maxOf(it,runtime.totalMemory()-runtime.freeMemory())};peakPss.updateAndGet {maxOf(it,Debug.getPss().toLong()*1024)}}
        sample();val baselineHeap=peakHeap.get();val baselinePss=peakPss.get()
        val sampler=Thread {while(running.get()) {sample();Thread.sleep(10)}}.apply {start()}
        val input="SYNTHETIC PNMEDBK1 resource probe".toByteArray(Charsets.UTF_8)
        val salt=ByteArray(16) {it.toByte()};val output=ByteArray(32);val start=SystemClock.elapsedRealtimeNanos()
        try {
            val generator=Argon2BytesGenerator()
            generator.init(Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(65536).withIterations(3).withParallelism(1).withSalt(salt).build())
            generator.generateBytes(input,output)
            val elapsed=SystemClock.elapsedRealtimeNanos()-start;sample()
            val hex=output.joinToString("") {"%02x".format(it.toInt() and 255)}
            // argon2-cffi/libargon2 independent v1.3 reference, synthetic fixed input above.
            assertEquals("809cb297135320843754f44d846dc5674bb8164bf7ae15a9026a1dc84a87d467",hex)
            val report=JSONObject().put("api",Build.VERSION.SDK_INT).put("memory_kib",65536).put("iterations",3).put("parallelism",1)
                .put("elapsed_ns",elapsed).put("heap_limit_bytes",runtime.maxMemory()).put("baseline_heap_bytes",baselineHeap)
                .put("sampled_peak_heap_bytes",peakHeap.get()).put("baseline_pss_bytes",baselinePss).put("sampled_peak_pss_bytes",peakPss.get())
                .put("sample_interval_ms",10).put("independent_kat",true).put("scope","TEST_ONLY_ISOLATED_KDF")
                .put("formal_format_approved",false).put("joint_restore_budget_verified",false)
            File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"synthetic-med02-format-readiness.json").writeText(report.toString())
            android.util.Log.i("MED02_FORMAT_RESOURCE",report.toString())
        }finally {running.set(false);sampler.join(5000);check(!sampler.isAlive);input.fill(0);salt.fill(0);output.fill(0)}
    }
}
