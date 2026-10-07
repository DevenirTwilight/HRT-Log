package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class VisitPackDataTest {
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val at=Instant.parse("2026-05-01T09:00:00Z")
    private val visit=AppointmentEntity(type="ENDO",at_utc=at.toEpochMilli(),at_zone="Europe/Paris",practitioner=" Synthetic clinic ",remind_minutes_before=60)
    private fun pack(appointment:Long,digest:String="a".repeat(64),sections:String="FACTS,LABS")=VisitPackEntity(appointment_id=appointment,generated_utc=at.toEpochMilli(),zone="Europe/Paris",
        range_from="2026-02-01",range_to="2026-05-01",sections=sections,language="fr-FR",template_version=1,input_digest=digest,facts_json="{\"labs\":1}")
    @Before fun open(){val c=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})}
    @After fun close()=db.close()

    @Test fun editingKeepsConfirmationWhichIsOnlySetExplicitly()=runBlocking {
        val id=repo.saveAppointment(visit)
        assertNull(repo.appointments().single().completed_utc)
        assertEquals("Synthetic clinic",repo.appointments().single().practitioner)
        repo.setVisitCompleted(id,true,at.plusSeconds(3600))
        repo.saveAppointment(repo.appointments().single().copy(note="Synthetic note",completed_utc=null))
        assertEquals(at.plusSeconds(3600).toEpochMilli(),repo.appointments().single().completed_utc)
        repo.setVisitCompleted(id,false);assertNull(repo.appointments().single().completed_utc)
        // A new appointment can never arrive pre-confirmed.
        repo.saveAppointment(visit.copy(completed_utc=at.toEpochMilli()));assertNull(repo.appointments().last().completed_utc)
    }

    @Test fun questionsKeepDenseOrderAndRejectBlankText()=runBlocking {
        val id=repo.saveAppointment(visit)
        listOf("First synthetic","Second synthetic","Third synthetic").forEach{repo.saveVisitQuestion(VisitQuestionEntity(appointment_id=id,sort_order=0,text=" $it "))}
        val third=repo.visitQuestions().last();repo.moveVisitQuestion(third.id,up=true);repo.moveVisitQuestion(third.id,up=true);repo.moveVisitQuestion(third.id,up=true)
        assertEquals(listOf("Third synthetic","First synthetic","Second synthetic"),repo.visitQuestions().sortedBy{it.sort_order}.map{it.text})
        assertEquals(listOf(0,1,2),repo.visitQuestions().map{it.sort_order}.sorted())
        repo.saveVisitQuestion(third.copy(status="ASKED",answer_note=" Synthetic answer "))
        repo.visitQuestions().single{it.id==third.id}.let{assertEquals("ASKED",it.status);assertEquals("Synthetic answer",it.answer_note);assertEquals(0,it.sort_order)}
        assertThrows(IllegalArgumentException::class.java){runBlocking{repo.saveVisitQuestion(VisitQuestionEntity(appointment_id=id,sort_order=0,text="   "))}}
        assertThrows(IllegalArgumentException::class.java){runBlocking{repo.saveVisitQuestion(third.copy(status="DONE"))}}
        try{db.openHelper.writableDatabase.execSQL("UPDATE visit_question SET status='X' WHERE id=?",arrayOf(third.id));fail()}catch(_:Exception){}
    }

    @Test fun packRecordsAreImmutableAndDeletedWithTheirAppointment()=runBlocking {
        val id=repo.saveAppointment(visit);val other=repo.saveAppointment(visit.copy(at_utc=at.plusSeconds(86400).toEpochMilli()))
        repo.saveVisitQuestion(VisitQuestionEntity(appointment_id=id,sort_order=0,text="Synthetic question"))
        repo.recordVisitPack(pack(id));repo.recordVisitPack(pack(other))
        try{db.openHelper.writableDatabase.execSQL("UPDATE visit_pack SET range_from='2026-01-01'");fail()}catch(_:Exception){}
        assertThrows(IllegalArgumentException::class.java){runBlocking{repo.recordVisitPack(pack(id,sections="LABS,FACTS"))}}
        assertThrows(IllegalArgumentException::class.java){runBlocking{repo.recordVisitPack(pack(id,sections="FACTS,UNKNOWN"))}}
        try{repo.recordVisitPack(pack(id,digest="Z".repeat(64)));fail()}catch(_:Exception){}
        assertThrows(IllegalArgumentException::class.java){runBlocking{repo.recordVisitPack(pack(id).copy(range_from="2026-06-01"))}}
        repo.deleteAppointment(id)
        assertEquals(listOf(other),repo.appointments().map{it.id});assertEquals(listOf(other),repo.visitPacks().map{it.appointment_id});assertTrue(repo.visitQuestions().isEmpty())
    }

    @Test fun backupRoundTripAndOldSchemaAndMalformedPackRollBack()=runBlocking {
        val id=repo.saveAppointment(visit);repo.setVisitCompleted(id,true,at)
        repo.saveVisitQuestion(VisitQuestionEntity(appointment_id=id,sort_order=0,text="Synthetic question",status="ASKED",answer_note="Synthetic"))
        repo.recordVisitPack(pack(id))
        val appointments=repo.appointments();val questions=repo.visitQuestions();val packs=repo.visitPacks()
        val pwd="synthetic-pass".toCharArray();val backup=repo.exportBackup(pwd)
        repo.restoreBackup(backup,pwd)
        assertEquals(appointments,repo.appointments());assertEquals(questions,repo.visitQuestions());assertEquals(packs,repo.visitPacks())

        fun tamper(edit:(JSONObject)->Unit)=JSONObject(String(BackupCodec.decrypt(backup,pwd))).also(edit).toString().toByteArray()
        listOf<(JSONObject)->Unit>(
            {it.getJSONObject("tables").getJSONArray("visit_pack").getJSONObject(0).put("sections","FACTS,EVERYTHING")},
            {it.getJSONObject("tables").getJSONArray("visit_pack").getJSONObject(0).put("range_to","2026-13-40")},
            {it.getJSONObject("tables").getJSONArray("visit_pack").getJSONObject(0).put("input_digest","short")},
            {it.getJSONObject("tables").getJSONArray("visit_pack").getJSONObject(0).put("facts_json","not json")},
            {it.getJSONObject("tables").getJSONArray("visit_question").getJSONObject(0).put("appointment_id",999)},
        ).forEach{edit->
            try{repo.restoreBackup(BackupCodec.encrypt(tamper(edit),pwd),pwd);fail()}catch(_:Exception){}
            assertEquals(appointments,repo.appointments());assertEquals(packs,repo.visitPacks())
        }

        // A schema 5 backup has no visit tables and no completion column; it restores with nothing confirmed.
        val old=tamper{o->o.put("schema",5);o.getJSONObject("tables").apply{remove("visit_question");remove("visit_pack")
            getJSONArray("appointment").getJSONObject(0).remove("completed_utc")}}
        repo.restoreBackup(BackupCodec.encrypt(old,pwd),pwd)
        assertEquals(1,repo.appointments().size);assertNull(repo.appointments().single().completed_utc)
        assertTrue(repo.visitQuestions().isEmpty());assertTrue(repo.visitPacks().isEmpty())
        // Current schema requires the new tables.
        try{repo.restoreBackup(BackupCodec.encrypt(tamper{it.getJSONObject("tables").remove("visit_pack")},pwd),pwd);fail()}catch(_:Exception){}
    }
}
