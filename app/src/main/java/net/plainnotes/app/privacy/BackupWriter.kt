package net.plainnotes.app.privacy

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.OutputStream

/** All user-requested encrypted exports share the close -> record boundary and cancellation rollback. */
class BackupWriter(private val history:BackupHistory) {
    companion object { private val mutex=Mutex() }
    suspend fun write(password:CharArray,encrypt:suspend(CharArray)->ByteArray,openOutput:()->OutputStream?) {
        try {
            mutex.withLock {
                val previous=history.previous()
                var attempted=false
                try {
                    withContext(Dispatchers.IO) {
                        val bytes=try{encrypt(password)}finally{password.fill('\u0000')}
                        (openOutput() ?: throw IOException("Output unavailable")).use{it.write(bytes)}
                        ensureActive()
                        attempted=true
                        history.record(previous)
                        ensureActive()
                    }
                } catch(e:Throwable) {
                    if(attempted)withContext(NonCancellable+Dispatchers.IO){history.rollback(previous)}
                    throw e
                }
            }
        } finally { password.fill('\u0000') }
    }
}
