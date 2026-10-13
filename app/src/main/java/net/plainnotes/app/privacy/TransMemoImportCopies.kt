package net.plainnotes.app.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Private, short-lived SQLite copies. The same process-wide lock orders startup cleanup and every import. */
object TransMemoImportCopies {
    enum class CleanupState { WAITING_FOR_UNLOCK, CLEAN, UNAVAILABLE }
    @Volatile var startupCleanupState=CleanupState.WAITING_FOR_UNLOCK
        private set
    private val mutex=Mutex()
    private val owned=Regex("(?:transmemo|tm-import-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.db(?:-(?:wal|shm|journal))?")
    private val sidecars=listOf("", "-wal", "-shm", "-journal")

    /** No directory access while credential storage is locked. Only a neutral in-memory outcome is retained. */
    suspend fun startup(noBackupDirectory:()->File, unlocked:Boolean):CleanupState {
        if(!unlocked) return CleanupState.WAITING_FOR_UNLOCK.also{startupCleanupState=it}
        return withContext(Dispatchers.IO) {
            val state=try { cleanupStale(noBackupDirectory());CleanupState.CLEAN }
                catch(_:IOException){CleanupState.UNAVAILABLE}
                catch(_:SecurityException){CleanupState.UNAVAILABLE}
            startupCleanupState=state;state
        }
    }

    suspend fun cleanupStale(noBackupDirectory:File)=withContext(Dispatchers.IO) {
        mutex.withLock { cleanOwned(directory(noBackupDirectory)) }
    }

    /** [read] must close its SQLite handle and return materialized data; no file/handle escapes to the preview. */
    suspend fun <T> read(noBackupDirectory:File, openInput:()->InputStream?, read:(File)->T):T=withContext(Dispatchers.IO) {
        mutex.withLock {
            val coroutine=currentCoroutineContext()
            coroutine.ensureActive()
            val directory=directory(noBackupDirectory)
            cleanOwned(directory) // retry a failed/delayed startup sweep before opening a new input
            val copy=File(directory,"tm-import-${UUID.randomUUID()}.db")
            var created=false
            try {
                Files.newOutputStream(copy.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)
                    .also{created=true}.use { output ->
                        (openInput() ?: throw IOException("Input unavailable")).use { input ->
                            val buffer=ByteArray(64*1024)
                            while(true) {
                                coroutine.ensureActive()
                                val n=input.read(buffer)
                                coroutine.ensureActive()
                                if(n<0)break
                                if(n>0)output.write(buffer,0,n)
                            }
                        }
                    }
                coroutine.ensureActive()
                read(copy).also{coroutine.ensureActive()}
            } finally {
                // Synchronous filesystem calls still run when the coroutine is cancelled. Never delete a name
                // whose CREATE_NEW failed, and never recurse or follow a symlink to its target.
                if(created) {
                    var failure:IOException?=null
                    sidecars.forEach { suffix ->
                        try { Files.deleteIfExists(File(copy.path+suffix).toPath()) }
                        catch(e:IOException) { if(failure==null)failure=e else failure!!.addSuppressed(e) }
                    }
                    failure?.let{throw it}
                }
            }
        }
    }

    private fun directory(root:File):File {
        val directory=File(root.canonicalFile,"import")
        if(Files.isSymbolicLink(directory.toPath()))throw IOException("Temporary import directory unavailable")
        if(!Files.exists(directory.toPath(),LinkOption.NOFOLLOW_LINKS)&&!directory.mkdir())throw IOException("Temporary import directory unavailable")
        if(!Files.isDirectory(directory.toPath(),LinkOption.NOFOLLOW_LINKS))throw IOException("Temporary import directory unavailable")
        return directory
    }

    private fun cleanOwned(directory:File) {
        Files.newDirectoryStream(directory.toPath()).use { paths ->
            paths.forEach { path ->
                if(owned.matches(path.fileName.toString())&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
                    Files.deleteIfExists(path)
            }
        }
    }
}
