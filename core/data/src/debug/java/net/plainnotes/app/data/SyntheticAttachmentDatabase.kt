package net.plainnotes.app.data

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.UUID

/** Debug-only fixture constructor; no release class, real database alias, or arbitrary path. */
object SyntheticAttachmentDatabase {
    fun open(app:Context,id:UUID):DatabaseAccess {
        check(Build.HARDWARE in setOf("ranchu","goldfish") || Build.FINGERPRINT.contains("generic")) {"Synthetic emulator only"}
        val directory=File(app.applicationContext.noBackupFilesDir,"synthetic-medflow-$id")
        if(!directory.exists())Os.mkdir(directory.path,448)
        val stat=Os.lstat(directory.path)
        check(OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid==Os.getuid() && stat.st_mode and 63==0)
        val context=object:ContextWrapper(app.applicationContext) {
            override fun getApplicationContext():Context=this
            override fun getNoBackupFilesDir()=directory
            override fun getDatabasePath(name:String)=File(directory,name)
        }
        return DatabaseAccess(context,id)
    }
}
