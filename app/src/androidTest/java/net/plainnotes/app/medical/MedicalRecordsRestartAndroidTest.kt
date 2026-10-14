package net.plainnotes.app.medical

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.*

/** Synthetic named stores only; no real application database or clinical files are cleared. */
class MedicalRecordsRestartAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val names=listOf("synthetic-med01-primary","synthetic-med01-legacy")
    private val backup get()=File(context.filesDir,"synthetic-med01-backup.pnbak")
    private val pid get()=File(context.filesDir,"synthetic-med01-pid")
    private val at=Instant.parse("2025-01-10T23:30:00Z")
    private val now=Instant.parse("2026-01-01T12:00:00Z")
    private fun db(name:String)=Room.databaseBuilder(context,NotesDatabase::class.java,name).allowMainThreadQueries().addCallback(SchemaGuards).build()
    private suspend fun stores(block:suspend(NotesRepository)->Unit) {
        val primary=db(names[0]);val legacy=db(names[1])
        try {block(NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=if(space==Space.PRIMARY)primary else legacy}).pinnedTo(Space.PRIMARY))}
        finally {primary.close();legacy.close()}
    }
    @Test fun persistedMedicalRecords()=runBlocking<Unit> {
        when(InstrumentationRegistry.getArguments().getString("med01_phase")) {
            "prepare"->prepare()
            "verify"->verify(true)
            null->{prepare();verify(false)}
            else->error("Unknown synthetic phase")
        }
    }
    private suspend fun prepare() {
        names.forEach{context.deleteDatabase(it)}
        stores{r->
            val draft=AppointmentEntity(type="ENDO",at_utc=at.toEpochMilli(),at_zone="Pacific/Kiritimati",practitioner="Synthetic doctor",location="Synthetic clinic",note="Synthetic record",remind_minutes_before=0)
            val v=r.saveHistoricalVisit(draft,now=now)
            r.saveVisitQuestion(VisitQuestionEntity(appointment_id=v.id,sort_order=0,text="Synthetic question",status="ASKED",answer_note="Synthetic answer"))
            r.saveLab(LabValueEntity(analyte_code="E2",value=100.0,unit="pg/mL",sampled_utc=at.toEpochMilli(),sampled_zone="UTC"))
            r.saveHistoricalVisit(v.copy(practitioner="Edited synthetic doctor"),v,now)
            val mistake=r.saveHistoricalVisit(draft.copy(type="GP",note="Erroneous entry"),now=now)
            r.deleteHistoricalVisit(mistake,now)
            r.pinnedTo(Space.DECOY).saveHistoricalVisit(draft.copy(note="Separate synthetic legacy"),now=now)
            backup.writeBytes(r.exportBackup("synthetic-med01-password".toCharArray()))
            pid.writeText(android.os.Process.myPid().toString())
        }
    }
    private suspend fun verify(fresh:Boolean) {
        if(fresh)assertNotEquals(pid.readText().toInt(),android.os.Process.myPid())
        try {stores{r->
            val v=r.appointments().single();assertEquals("Edited synthetic doctor",v.practitioner);assertEquals(at.toEpochMilli(),v.at_utc);assertEquals("Pacific/Kiritimati",v.at_zone);assertEquals(now.toEpochMilli(),v.completed_utc)
            assertEquals(v.id,r.visitQuestions().single().appointment_id);assertEquals("Synthetic answer",r.visitQuestions().single().answer_note)
            val labs=r.labs();val contexts=r.labContexts();assertEquals(1,labs.size);assertEquals(1,contexts.size)
            val other=r.pinnedTo(Space.DECOY).appointments();assertEquals("Separate synthetic legacy",other.single().note)
            assertEquals("PNBAK1",backup.readBytes().copyOfRange(0,6).toString(Charsets.UTF_8))
            r.restoreBackup(backup.readBytes(),"synthetic-med01-password".toCharArray())
            assertEquals(listOf(v),r.appointments());assertEquals(labs,r.labs());assertEquals(contexts,r.labContexts());assertEquals(other,r.pinnedTo(Space.DECOY).appointments())
            r.restoreTrash(r.trash().single().id,now);assertEquals(2,r.appointments().size);assertEquals("Synthetic answer",r.visitQuestions().single().answer_note)
            assertEquals(other,r.pinnedTo(Space.DECOY).appointments())
        }}finally {names.forEach{context.deleteDatabase(it)};backup.delete();pid.delete()}
    }
}
