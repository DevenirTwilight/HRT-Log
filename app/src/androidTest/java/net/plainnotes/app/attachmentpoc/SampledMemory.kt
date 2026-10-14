package net.plainnotes.app.attachmentpoc

import android.os.Debug
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Actual sampled process observations; cannot establish the absolute instantaneous peak. */
class SampledMemory:Closeable {
    private val running=AtomicBoolean(true)
    val javaBytes=AtomicLong(0)
    val nativeBytes=AtomicLong(0)
    val pssKib=AtomicLong(0)
    val samples=AtomicLong(0)
    private fun sample() {
        val runtime=Runtime.getRuntime()
        javaBytes.accumulateAndGet(runtime.totalMemory()-runtime.freeMemory(),Math::max)
        nativeBytes.accumulateAndGet(Debug.getNativeHeapAllocatedSize(),Math::max)
        pssKib.accumulateAndGet(Debug.getPss().toLong(),Math::max)
        samples.incrementAndGet()
    }
    private val worker=Thread({while(running.get()){sample();Thread.sleep(20)}} ,"synthetic-memory-observer").apply{isDaemon=true;start()}
    override fun close(){running.set(false);worker.join(5000);check(!worker.isAlive);sample()}
    fun fields()="sampled_java_max_bytes=${javaBytes.get()} sampled_native_max_bytes=${nativeBytes.get()} sampled_pss_max_kib=${pssKib.get()} memory_samples=${samples.get()}"
}
