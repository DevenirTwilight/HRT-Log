import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.AtomicFile
import net.plainnotes.app.security.AppLock
import java.io.File
import java.lang.reflect.Proxy

fun main() {
    val dir=java.nio.file.Files.createTempDirectory("med02-synthetic-atomic-").toFile()
    val prefs=Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)) {_,m,_->
        when(m.returnType) {java.lang.Boolean.TYPE->false;java.lang.Integer.TYPE->0;java.lang.Long.TYPE->0L;else->null}
    } as SharedPreferences
    val context=object:ContextWrapper(null) {
        override fun getNoBackupFilesDir()=dir
        override fun getSharedPreferences(name:String,mode:Int)=prefs
    }
    val base=File(dir,"lock.bin")
    // Synthetic bytes stand in for the existing encrypted credential. No keys/PIN are exercised.
    base.writeBytes(ByteArray(60) {0x41})
    check(AppLock(context).enabled)
    class SimulatedProcessDeath:RuntimeException()
    val faultBase=object:File(dir,"lock.bin") {
        override fun renameTo(destination:File):Boolean {
            val renamed=super.renameTo(destination)
            if(renamed)throw SimulatedProcessDeath()
            return renamed
        }
    }
    // Real Android 8 AtomicFile, with a fault immediately AFTER the actual backup rename.
    // This is deterministic boundary injection on a host JVM, not an Android kill/reboot test.
    try {AtomicFile(faultBase).startWrite();error("backup rename seam not hit")}
    catch(_:SimulatedProcessDeath) {println("Injected death immediately after Android 8 AtomicFile backup rename")}
    val pinEnabled=AppLock(context).enabled
    check(!pinEnabled && File(base.path+".bak").exists())
    println("Interrupted-update fixture: base=${base.exists()} backup=${File(base.path+".bak").exists()} AppLock.enabled=$pinEnabled")
    val recovered=AtomicFile(base).readFully()
    check(recovered.contentEquals(ByteArray(60){0x41}))
    println("Android 8 AtomicFile.readFully recovered original credential: true")
    dir.listFiles()?.forEach {it.delete()};dir.delete()
}
