package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.data.*
import net.plainnotes.app.export.ExportData
import net.plainnotes.app.ui.VisitPackDialog
import net.plainnotes.app.visit.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w411dp-h891dp-xxhdpi",application=android.app.Application::class)
class VisitPackTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val zone=ZoneId.of("Europe/Paris")
    private fun at(date:String,time:String="10:00")=LocalDateTime.parse(date+"T"+time).atZone(zone).toInstant().toEpochMilli()
    private fun visit(id:Long,date:String,done:Boolean)=AppointmentEntity(id,"ENDO",at(date),zone.id,remind_minutes_before=60,completed_utc=if(done)at(date,"12:00") else null)
    private val med=MedicationEntity(1,"Synthetic current name","E2","ORAL","MG",2.0,40.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private val snapshot=MedicationSnapshot.encode(med.copy(name="Synthetic name at the time"),ProfileEntity(1,"EV","oral"))
    private fun taken(id:Long,date:String,time:String,status:String="ON_TIME",origin:String="APP",deleted:Boolean=false)=RecordEntity(id,1,taken_utc=at(date,time),taken_zone=zone.id,actual_dose=2.0,status=status,
        origin=origin,revision=1,config_snapshot=snapshot,deleted_at_utc=if(deleted)1L else null)
    private fun notTaken(id:Long,date:String,time:String,status:String,origin:String="APP")=RecordEntity(id,1,rule_version_id=1,slot_key="k$id",scheduled_utc=at(date,time),scheduled_zone=zone.id,planned_dose=2.0,
        status=status,origin=origin,revision=1,config_snapshot=snapshot)
    private val target=visit(10,"2026-05-20",false)
    private fun data(records:List<RecordEntity>,extraLabs:List<LabValueEntity> = emptyList(),containers:List<ContainerEntity> = emptyList())=ExportData(listOf(med),emptyMap(),records,
        listOf(LabValueEntity(1,"E2",100.0,"pg/mL",at("2026-03-01","23:59"),zone.id),LabValueEntity(2,"T",1.0,"nmol/L",at("2026-05-21","00:00"),zone.id))+extraLabs,
        emptyList(),emptyList(),listOf(DayNoteEntity("2026-03-02","Synthetic note")),mapOf(1L to "Synthetic schedule"),{"x"},containers,
        listOf(SymptomCheckEntity("2026-03-05","A"),SymptomCheckEntity("2026-03-05","B"),SymptomCheckEntity("2026-04-01","A"),SymptomCheckEntity("2026-02-28","C")),
        listOf(StageReviewEntity(1,"2026-04-02")),emptyList(),
        listOf(RegimenVersionEntity(1,1,at("2026-01-01"),at("2026-04-01"),zone.id,"{}","a".repeat(64),"APP"),RegimenVersionEntity(2,1,at("2026-04-01"),null,zone.id,"{}","b".repeat(64),"APP")),
        listOf(MilestoneEntity(1,"2026-03-03","SURGERY")),listOf(visit(1,"2026-03-01",true),visit(2,"2026-04-15",false),target))

    @Test fun defaultRangeStartsOnlyAtAnEarlierConfirmedVisit() {
        val today=LocalDate.of(2026,5,10)
        val all=listOf(visit(1,"2026-01-10",true),visit(2,"2026-03-01",true),visit(3,"2026-04-15",false),visit(4,"2026-06-01",true),target)
        VisitPlanning.defaultRange(target,all,today,zone).let{assertEquals(LocalDate.of(2026,3,1),it.from);assertEquals(today,it.to);assertEquals(2L,it.previous?.id)}
        VisitPlanning.defaultRange(target,all,LocalDate.of(2026,6,30),zone).let{assertEquals(LocalDate.of(2026,5,20),it.to)}
        // Past but unconfirmed visits never become the start; without any, the suggestion is flagged.
        VisitPlanning.defaultRange(target,listOf(visit(3,"2026-04-15",false),target),today,zone).let{assertNull(it.previous);assertEquals(today.minusDays(89),it.from)}
    }

    @Test fun importedOnTimeWithoutScheduledTimeIsNotCountedAsOnTime() {
        // REQUIREMENTS §35a: imports store ON_TIME, but without an original scheduled time that is not "on time".
        val imported=taken(1,"2026-03-10","08:00",origin="IMPORT_HT")
        val scheduled=notTaken(2,"2026-03-10","20:00","ON_TIME").copy(taken_utc=at("2026-03-10","20:05"),taken_zone=zone.id,actual_dose=2.0)
        val unscheduledApp=taken(3,"2026-03-11","08:00")
        assertNull(imported.scheduled_utc);assertEquals("ON_TIME",imported.status)
        assertEquals(listOf(1,0,0,0),net.plainnotes.app.visit.scheduledCounts(listOf(imported,scheduled,unscheduledApp)))
        assertEquals(listOf(0,0,0,0),net.plainnotes.app.visit.scheduledCounts(listOf(imported)))
        val f=VisitFacts.build(data(listOf(imported,scheduled,unscheduledApp)),target,LocalDate.of(2026,3,1),LocalDate.of(2026,3,31),zone)
        assertEquals(3,f.medications.single().taken);assertFalse(f.json().toString().contains("\"imported\""))
    }

    @Test fun factsAttributeByActualOrScheduledTimeAndKeepUnconfirmedSeparate() {
        val d=data(listOf(taken(1,"2026-03-01","00:00"),taken(2,"2026-03-31","23:59","LATE"),taken(3,"2026-04-01","00:00"),taken(4,"2026-03-10","08:00",origin="IMPORT_HT"),
            taken(5,"2026-03-11","08:00",deleted=true),taken(6,"2026-02-28","23:59"),
            notTaken(7,"2026-03-12","08:00","MISSED"),notTaken(8,"2026-03-13","08:00","MISSED","AUTO_MISSED"),notTaken(9,"2026-03-14","08:00","SKIPPED"),notTaken(10,"2026-04-02","08:00","MISSED","AUTO_MISSED")))
        val f=VisitFacts.build(d,target,LocalDate.of(2026,3,1),LocalDate.of(2026,3,31),zone)
        assertEquals(31L,f.days);assertEquals(0,f.regimenStarted);assertEquals(0,f.regimenEnded)
        val m=f.medications.single()
        assertEquals("Synthetic name at the time",m.name)
        // Record 2 is LATE without an original scheduled time, so it is taken but not counted as late; the import is not counted apart.
        assertEquals(listOf(3,0,1,1,1),listOf(m.taken,m.late,m.confirmedMissed,m.skipped,m.unconfirmed))
        assertEquals(1,f.labs);assertEquals(listOf("E2"),f.analytes);assertEquals(1,f.symptomDays);assertEquals(2,f.symptomGroups)
        assertEquals(0,f.reviews);assertEquals(1,f.noteDays);assertEquals(1,f.otherAppointments);assertEquals(1,f.milestones)
        val april=VisitFacts.build(d,target,LocalDate.of(2026,4,1),LocalDate.of(2026,5,21),zone)
        assertEquals(1,april.regimenStarted);assertEquals(1,april.regimenEnded);assertEquals(1,april.reviews);assertEquals(listOf("T"),april.analytes)
        assertEquals(1,april.medications.single().unconfirmed);assertEquals(0,april.medications.single().confirmedMissed)
        // The facts JSON stored with the pack never contains names or free text.
        assertFalse(f.json().toString().contains("Synthetic"))
    }

    @Test fun digestCoversOnlyChosenPartsAndIsStable() {
        val d=data(listOf(taken(1,"2026-03-02","08:00")))
        fun digest(data:ExportData,sections:Set<VisitSection>,questions:List<VisitQuestionEntity> = emptyList()):String {
            val spec=VisitPackSpec(target,LocalDate.of(2026,3,1),LocalDate.of(2026,3,31),sections,questions,emptyMap(),"?")
            return VisitDigest.compute(data,spec,VisitFacts.build(data,target,spec.from,spec.to,zone),"en",zone)
        }
        val base=setOf(VisitSection.FACTS,VisitSection.LABS)
        val first=digest(d,base);assertEquals(64,first.length);assertEquals(first,digest(d,base))
        assertNotEquals(first,digest(d,base+VisitSection.INTAKES))
        // Package details are not chosen, so changing them does not change the pack.
        val withBox=data(listOf(taken(1,"2026-03-02","08:00")),containers=listOf(ContainerEntity(1,1,30.0,0.0,0.0,state="SEALED",batch="Synthetic batch")))
        assertEquals(first,digest(withBox,base));assertNotEquals(digest(d,base+VisitSection.PACKAGES),digest(withBox,base+VisitSection.PACKAGES))
        val edited=data(listOf(taken(1,"2026-03-02","08:00")),extraLabs=listOf(LabValueEntity(3,"E2",50.0,"pg/mL",at("2026-03-20"),zone.id)))
        assertNotEquals(first,digest(edited,base))
        val q=VisitQuestionEntity(1,10,0,"Synthetic question")
        assertEquals(digest(d,base),digest(d,base,listOf(q)));assertNotEquals(digest(d,base+VisitSection.QUESTIONS),digest(d,base+VisitSection.QUESTIONS,listOf(q)))
    }

    @Test fun dialogKeepsPrivatePartsOffAndPreviewsFacts() {
        val c=ApplicationProvider.getApplicationContext<android.content.Context>()
        val today=LocalDate.now()
        val previous=AppointmentEntity(1,"ENDO",today.minusDays(30).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),ZoneId.systemDefault().id,remind_minutes_before=60,completed_utc=1L)
        val current=AppointmentEntity(2,"ENDO",today.atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),ZoneId.systemDefault().id,practitioner="Synthetic clinic",remind_minutes_before=60)
        val state=NotesState(appointments=listOf(previous,current),loading=false)
        ui.setContent{MaterialTheme{VisitPackDialog(current,state,NotesViewModel.ExtraState(),{}){_,_->}}}
        ui.onNodeWithText(c.getString(R.string.visit_range_none)).assertDoesNotExist()
        fun box(label:Int)=ui.onNode(isToggleable() and hasText(c.getString(label)))
        box(R.string.visit_section_packages).assertIsOff();box(R.string.visit_section_milestones).assertIsOff()
        box(R.string.visit_section_facts).assertIsOn();box(R.string.visit_section_questions).assertIsOn()
        ui.onNodeWithText(c.getString(R.string.visit_fact_days,31L)).performScrollTo().assertExists()
        ui.onNodeWithText(c.getString(R.string.visit_fact_unconfirmed_note)).assertExists()
        ui.waitForIdle()
        ui.onAllNodes(isToggleable()).onFirst().performScrollTo()
        val view=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.WHITE);view.draw(android.graphics.Canvas(image))
        val file=java.io.File("build/screenshots/visit_pack_dialog.png");file.parentFile!!.mkdirs()
        file.outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
