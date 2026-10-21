package net.plainnotes.app.data

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit CI preflight only. No database, KDF, fixture, or production configuration change. */
class NativeCiHeapProbe {
    @Test fun actualProcessHeapMatchesFrozen192MiBBudget() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val requested=InstrumentationRegistry.getArguments().getString("ansmExpectedHeapMb")
        assumeTrue("Manual synthetic CI heap preflight",requested!=null)
        assertEquals("Only the existing frozen heap is permitted","192",requested)
        val actual=Runtime.getRuntime().maxMemory()
        val report=JSONObject().put("scope","SYNTHETIC_CI_ACTUAL_HEAP_PREFLIGHT")
            .put("api",android.os.Build.VERSION.SDK_INT).put("actual_max_heap_bytes",actual)
            .put("expected_max_heap_bytes",192L*1024*1024)
        val folder=File(instrumentation.context.getExternalFilesDir(null),"native-ci-environment").apply{mkdirs()}
        File(folder,"heap.json").writeText(report.toString())
        android.util.Log.i("ANSM_CI_HEAP",report.toString())
        assertEquals("Actual process heap must match original frozen budget",192L*1024*1024,actual)
    }
}
