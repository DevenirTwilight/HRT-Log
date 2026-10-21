package net.plainnotes.app.data

import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Synthetic test-only observations. Never installed in the app or given real health data. */
internal class RestoreMemoryProbe(private val publish:(JSONObject)->Unit):AutoCloseable {
    private val started=System.nanoTime()
    private val running=AtomicBoolean(true)
    private val heapPeak=AtomicLong(0);private val mallocPeak=AtomicLong(0);private val rssPeak=AtomicLong(0)
    private val points=JSONArray()
    private fun sample():JSONObject {
        val runtime=Runtime.getRuntime();val heap=runtime.totalMemory()-runtime.freeMemory()
        val malloc=android.os.Debug.getNativeHeapAllocatedSize()
        heapPeak.accumulateAndGet(heap,::maxOf);mallocPeak.accumulateAndGet(malloc,::maxOf)
        val values=runCatching{File("/proc/self/status").readLines().mapNotNull {line->
            val key=line.substringBefore(':');if(key !in setOf("VmRSS","VmHWM"))null else key to (line.substringAfter(':').trim().substringBefore(' ').toLong()*1024)
        }.toMap()}.getOrDefault(emptyMap())
        values["VmRSS"]?.let{rssPeak.accumulateAndGet(it,::maxOf)}
        return JSONObject().put("elapsed_ns",System.nanoTime()-started).put("java_heap_bytes",heap)
            .put("native_malloc_bytes",malloc).put("current_rss_bytes",values["VmRSS"] ?: JSONObject.NULL)
            .put("process_lifetime_hwm_bytes",values["VmHWM"] ?: JSONObject.NULL)
    }
    private val sampler=Thread {
        while(running.get()) {
            runCatching{sample()}
            try{Thread.sleep(100)}catch(_:InterruptedException){break}
        }
    }.apply{isDaemon=true;name="synthetic-restore-memory";start()}
    init {checkpoint("BEFORE_DECRYPT")}
    fun checkpoint(stage:String) {check(points.length()<32);points.put(sample().put("stage",stage));publish(summary())}
    private fun summary()=JSONObject().put("scope","TEST_ONLY_ACTUAL_RESTORE")
        .put("sample_period_ms",100).put("observed_java_heap_peak_bytes",heapPeak.get())
        .put("observed_native_malloc_peak_bytes",mallocPeak.get()).put("observed_rss_peak_bytes",rssPeak.get())
        .put("rss_available",rssPeak.get()>0).put("points",points)
        .put("limits","100ms sampled peaks can miss transients; malloc is not RSS; HWM includes pre-restore fixture/export; capture and file IO included in restore budget")
    override fun close() {running.set(false);sampler.interrupt();sampler.join(2000);checkpoint("INPUT_AND_STAGING_RELEASED")}
}
