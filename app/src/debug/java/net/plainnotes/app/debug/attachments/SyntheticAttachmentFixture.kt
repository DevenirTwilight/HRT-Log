package net.plainnotes.app.debug.attachments

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.system.Os
import androidx.lifecycle.SavedStateHandle
import net.plainnotes.app.data.*
import net.plainnotes.app.runtime.RuntimeLibrarySession
import java.io.File
import java.util.UUID

/** Dedicated synthetic encrypted namespace; NEVER uses NotesApp's database, PIN or user records. */
internal class SyntheticAttachmentFixture(app:Context,val id:UUID=UUID.randomUUID(),saved:SavedStateHandle=SavedStateHandle()) {
    init {check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.HARDWARE in setOf("ranchu","goldfish")) {"Synthetic emulator only"}}
    val directory=File(app.noBackupFilesDir,"synthetic-medflow-$id")
    val context=object:ContextWrapper(app.applicationContext) {
        override fun getApplicationContext():Context=this
        override fun getNoBackupFilesDir()=directory
        override fun getDatabasePath(name:String)=File(directory,name)
    }
    init {if(!directory.exists())Os.mkdir(directory.path,448)}
    val access=SyntheticAttachmentDatabase.open(app,id)
    val repository=NotesRepository(access).pinnedTo(Space.PRIMARY)
    val runtime=RuntimeLibrarySession(repository,saved)
    lateinit var target:MedicalAttachmentOperations.Target
    suspend fun initialize() {
        if(repository.appointments().isEmpty())repository.saveAppointment(AppointmentEntity(type="SYNTHETIC",at_utc=1000,at_zone="UTC",note="SYNTHETIC operation flow",remind_minutes_before=0))
        target=MedicalAttachmentOperations.Target(MedicalAttachmentOperations.Kind.APPOINTMENT,repository.appointments().single().id)
        runtime.initialize()
    }
    fun close() {runtime.close();access.close(Space.PRIMARY)}
}
