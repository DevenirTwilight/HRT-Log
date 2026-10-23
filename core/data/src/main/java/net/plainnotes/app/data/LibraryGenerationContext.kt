package net.plainnotes.app.data

import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants.*
import java.io.File
import java.util.UUID

/** Kernel-held private generation directory. SQLite, key.wrap and inner ciphertext stay anchored
 * to this FD for the lifetime of every SQLCipher connection. No rename or deletion is performed. */
internal class LibraryGenerationContext private constructor(base:Context,val generation:UUID,
    private val physical:File,private val held:ParcelFileDescriptor):ContextWrapper(base),AutoCloseable {
    private val stat=Os.fstat(held.fileDescriptor)
    private val anchor=File("/proc/self/fd/${held.fd}")
    val databaseAlias="notes.gen.db.$generation"
    val device get()=stat.st_dev
    val inode get()=stat.st_ino
    override fun getApplicationContext():Context=this
    override fun getDatabasePath(name:String):File {require(name=="notes.db");checkCurrent();return File(anchor,name)}
    override fun getNoBackupFilesDir():File {checkCurrent();return anchor}
    fun checkCurrent() {
        val current=Os.lstat(physical.path)
        requirePrivate(current)
        check(current.st_dev==stat.st_dev && current.st_ino==stat.st_ino) {"Generation directory replaced"}
    }
    fun sync() {checkCurrent();Os.fsync(held.fileDescriptor)}
    override fun close()=held.close()
    companion object {
        private fun requirePrivate(s:android.system.StructStat) {
            check(S_ISDIR(s.st_mode) && s.st_uid==Os.getuid() && s.st_mode and 63==0) {"Unknown generation directory"}
        }
        fun directory(base:Context)=File(base.noBackupFilesDir,"library-generations-v1")
        fun open(base:Context,id:UUID,create:Boolean,expected:Pair<Long,Long>?=null):LibraryGenerationContext {
            val root=directory(base)
            if(create && !root.exists()) {Os.mkdir(root.path,448);val parent=Os.open(base.noBackupFilesDir.path,O_RDONLY or O_NOFOLLOW or O_CLOEXEC,0);try {Os.fsync(parent)}finally {Os.close(parent)}}
            val parent=Os.open(root.path,O_RDONLY or O_NOFOLLOW or O_CLOEXEC,0)
            try {
                requirePrivate(Os.fstat(parent))
                val physical=File(root,id.toString())
                // Do not borrow a closed descriptor. The parent FD itself remains held here.
                val parentOwned=ParcelFileDescriptor.dup(parent)
                val fd=try {
                    val path="/proc/self/fd/${parentOwned.fd}/$id"
                    if(create) {Os.mkdir(path,448);Os.fsync(parent)}
                    Os.open(path,O_RDONLY or O_NOFOLLOW or O_CLOEXEC,0)
                }finally {parentOwned.close()}
                try {
                    val s=Os.fstat(fd);requirePrivate(s)
                    if(expected!=null)check(s.st_dev==expected.first && s.st_ino==expected.second) {"Unrecognized generation ownership"}
                    val owned=ParcelFileDescriptor.dup(fd)
                    try {return LibraryGenerationContext(base.applicationContext,id,physical,owned).also {it.checkCurrent()}}
                    catch(e:Throwable) {owned.close();throw e}
                } finally {Os.close(fd)}
            } finally {Os.close(parent)}
        }
    }
}
