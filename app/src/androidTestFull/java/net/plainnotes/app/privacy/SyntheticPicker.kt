package net.plainnotes.app.privacy

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry

/** Actual ActivityResultRegistry, synthetic SAF result: never a user's document/provider/account. */
object SyntheticPicker {
    fun result(action:String,uri:Uri?,launch:()->Unit) {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val filter=IntentFilter(action).apply{addCategory(Intent.CATEGORY_OPENABLE);addDataType("*/*")}
        val response=Instrumentation.ActivityResult(if(uri==null)Activity.RESULT_CANCELED else Activity.RESULT_OK,
            uri?.let{Intent().setData(it)})
        val monitor=instrument.addMonitor(filter,response,true)
        try{launch();instrument.waitForIdleSync()}finally{instrument.removeMonitor(monitor)}
    }
}
