package net.plainnotes.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RuleKind
import net.plainnotes.app.domain.TherapyStandard
import net.plainnotes.app.timeline.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.Locale
import java.util.TimeZone

/**
 * REQUIREMENTS §38 regression guard (must pass before any signed delivery): periods depend only on treatment facts.
 * Golden data follows the structure of the user's build 23 diagnostics (sources, standards, timestamps only); every
 * record is synthetic. Each non-treatment operation runs through the real repository path on a real database, and the
 * periods, their standards, every record's period and the History labels must be identical before and after.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class PeriodStabilityTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="period-stability.db"
    private lateinit var db:NotesDatabase;private lateinit var repo:NotesRepository
    private val paris=ZoneId.of("Europe/Paris")
    private val now=Instant.parse("2026-10-20T10:00:00Z")
    private val twice=listOf(LocalTime.of(8,0),LocalTime.of(20,0))
    private val base=MedicationEntity(name="Synthetic E2",molecule="E2",route="SUBLINGUAL",unit="MG",dose_per_intake=2.0,container_capacity=60.0,expiry_days_after_open=90,
        soon_alert_minutes=15,late_after_minutes=60,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private fun open(){db=Room.databaseBuilder(context,NotesDatabase::class.java,name).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})}
    private val originalZone=TimeZone.getDefault();private val originalLocale=Locale.getDefault()
    @Before fun start(){TimeZone.setDefault(TimeZone.getTimeZone(paris));context.deleteDatabase(name);open()}
    @After fun stop(){db.close();context.deleteDatabase(name);Locale.setDefault(originalLocale);TimeZone.setDefault(originalZone)}

    // ---- what must not change ----
    data class Snapshot(val periods:List<Triple<String,Instant,Instant?>>,val standards:List<List<TherapyStandard>>,val recordPeriods:Map<Long,String?>,val labels:Map<Long,Set<net.plainnotes.app.domain.RecordLabel>>)
    private suspend fun snapshot():Snapshot {
        var extra=NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),historyPeriods=repo.historyPeriods(),annotations=repo.annotations())
        TimelineV2Migration.plan(extra,now)?.let{repo.migrateTimeline(it.replace,it.rows,now);extra=extra.copy(historyPeriods=repo.historyPeriods())}
        val v=PeriodTimelineProjection.build(extra,emptyList(),now)
        val p=v.projection;val taken=extra.records.filter{it.deleted_at_utc==null && it.status in listOf("ON_TIME","LATE") && it.taken_utc!=null}
        return Snapshot(p.periods.map{Triple(it.key,it.from,it.until)},p.periods.map{d->p.standards.filter{it.key in d.finalStandardSpanKeys}.map{it.standard}.sortedBy{it.toString()}},
            taken.associate{it.id to p.periodAt(Instant.ofEpochMilli(it.taken_utc!!))?.key},HistoryLabels.build(extra.records,v,extra.annotations,paris))
    }

    // ---- data sets ----
    private var med=0L
    private fun at(day:String,time:String)=LocalDate.parse(day).atTime(LocalTime.parse(time)).atZone(paris).toInstant()
    private suspend fun taken(instant:Instant,dose:Double=2.0,origin:String="IMPORT_HT",key:String?=null)=db.dao().record(RecordEntity(medication_id=med,taken_utc=instant.toEpochMilli(),
        taken_zone=paris.id,actual_dose=dose,status="ON_TIME",origin=origin,source_record_key=if(origin=="APP")null else key ?: "ht:synthetic:stable:${instant.toEpochMilli()}",revision=1,
        config_snapshot=MedicationSnapshot.encode(base.copy(id=med),ProfileEntity(med,"E2","sublingual",sl_tier=2))))
    private suspend fun dailyRecords(from:String,until:String,dose:(LocalDate)->Double={2.0},hours:List<String> = listOf("08:05","20:10"),origin:(LocalDate)->String={"IMPORT_HT"}) {
        var d=LocalDate.parse(from);while(d<LocalDate.parse(until)){val day=d;hours.forEach{h->taken(at(day.toString(),h),dose(day),origin(day))};d=d.plusDays(1)}
    }
    private suspend fun save(at:Instant,interval:Int=1,times:List<LocalTime> =twice,m:MedicationEntity=base.copy(id=med),dose:Double=2.0)=
        repo.saveMedication(m.copy(dose_per_intake=dose),"E2",RuleKind.EVERY_N_DAYS,interval,times,emptySet(),at,ProfileEntity(med,"E2","sublingual",sl_tier=2))
    private val standard=TherapyStandard("E2","E2","SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))

    /** Golden: confirmed open period from 02-26; plan saved as every 11 days at 14:32:04.220Z, every day at 15:36:20.865Z, re-saved 10-07 14:15:59.405Z. */
    private suspend fun golden() {
        med=db.dao().insertMedication(base)
        dailyRecords("2026-02-26","2026-10-06")
        taken(at("2026-10-06","08:05"));taken(Instant.parse("2026-10-06T15:00:00Z"),origin="APP") // one dose under the every-11-days version
        save(Instant.parse("2026-10-06T14:32:04.220Z"),interval=11)
        save(Instant.parse("2026-10-06T15:36:20.865Z"))
        save(Instant.parse("2026-10-07T14:15:59.405Z"),times=listOf(LocalTime.of(8,30),LocalTime.of(20,0)))
        dailyRecords("2026-10-07","2026-10-19",origin={"APP"})
        repo.confirmHistoryPeriod(null,med,standard,LocalDate.parse("2026-02-26"),null,paris,"{}","{}",Instant.parse("2026-10-08T09:00:00Z"))
        repo.addContainers(med,60.0,1,true)
    }
    /** A: imported twice-daily history with partial days, nothing confirmed. */
    private suspend fun historyOnly() {
        med=db.dao().insertMedication(base)
        dailyRecords("2026-05-01","2026-09-01",hours=listOf("08:00","20:00"))
        (0..20).forEach{taken(at("2026-09-01","08:00").plus(Duration.ofDays(it*2L)))}
    }
    /** B: an app plan with a real dose change after 40 days, records throughout. */
    private suspend fun doseChange() {
        med=db.dao().insertMedication(base)
        save(at("2026-06-01","09:00"));dailyRecords("2026-06-01","2026-07-11",origin={"APP"})
        save(at("2026-07-11","09:00"),dose=3.0);dailyRecords("2026-07-11","2026-09-01",dose={3.0},origin={"APP"})
    }
    /** C: across the 2026-03-29 Paris DST change, doses near midnight, a stop of a week, and a user merge. */
    private suspend fun dstAndEdits() {
        med=db.dao().insertMedication(base)
        dailyRecords("2026-03-10","2026-04-20",hours=listOf("00:20","23:50"))
        save(at("2026-04-20","12:00"));dailyRecords("2026-04-20","2026-05-10",hours=listOf("00:20","23:50"),origin={"APP"})
        save(at("2026-05-10","12:00"),m=base.copy(id=med,active=false))
        save(at("2026-05-17","12:00"));dailyRecords("2026-05-17","2026-06-10",hours=listOf("00:20","23:50"),origin={"APP"})
        val v=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),historyPeriods=repo.historyPeriods()),emptyList(),now)
        val first=v.projection.periods.first{it.finalStandardSpanKeys.isNotEmpty()};val r=TimelineEdits.rangeOf(first,paris)
        repo.editTimeline(emptyList(),listOf(TimelineEditRow(HistoryPeriods.PERIOD,med,standard,r.from,r.until,paris,"{}")))
    }
    private val datasets:List<Pair<String,suspend ()->Unit>> = listOf("golden" to {golden()},"history only" to {historyOnly()},"dose change" to {doseChange()},"DST and edits" to {dstAndEdits()},"edited period" to {editedHistory()})

    /** §40: a fixed user override stays fixed after all the same real non-treatment paths. */
    private suspend fun editedHistory() {
        doseChange()
        val e=NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),historyPeriods=repo.historyPeriods())
        val p=PeriodTimelineProjection.build(e,emptyList(),now).projection
        val edit=TimelineEdits.saveV2(e.historyPeriods,p,med,standard.copy(doses=listOf(4.0,4.0)),
            TimelineEdits.Range(LocalDate.parse("2026-06-10"),LocalDate.parse("2026-07-01")),MedicationSnapshot.encode(base.copy(id=med),ProfileEntity(med,"E2","sublingual",sl_tier=2)))
        repo.editTimeline(edit.replace,edit.rows,t)
    }

    // ---- non-treatment operations (REQUIREMENTS §38 item 2) ----
    private var tick=0L
    /** Each operation happens a minute after the previous one, as a user would do them. */
    private val t get()=Instant.parse("2026-10-20T08:00:00Z").plusSeconds(60*(++tick))
    private suspend fun current()=db.dao().medication(med)
    private suspend fun resave(change:(MedicationEntity)->MedicationEntity,times:List<LocalTime>?=null) {
        val m=change(current());val rule=db.dao().rules().lastOrNull{it.medication_id==med && it.effective_until_utc==null}
        if(rule==null){db.dao().updateMedication(m);return} // no plan saved: an entry edit only
        repo.saveMedication(m,"E2",RuleKind.valueOf(rule.kind),rule.interval,times ?: db.dao().times(rule.id).map{LocalTime.parse(it.local_time)},emptySet(),t,ProfileEntity(med,"E2","sublingual",sl_tier=2))
    }
    private val operations:List<Pair<String,suspend ()->Unit>> = listOf(
        "set remaining stock" to {val c=repo.containers().firstOrNull() ?: run{repo.addContainers(med,60.0,1,true);repo.containers().first()};repo.setRemaining(c.id,17.0)},
        "add packages" to {repo.addContainers(med,28.0,2,false)},
        "replace package" to {repo.replaceContainer(med,30.0)},
        "discard package" to {repo.addContainers(med,28.0,1,false);repo.discardContainer(repo.containers().last().id)},
        "package capacity" to {resave({it.copy(container_capacity=56.0)})},
        "expiry" to {resave({it.copy(expiry_days_after_open=30)})},
        "medication name" to {resave({it.copy(name="Renamed synthetic")})},
        "day note" to {repo.setNote(LocalDate.parse("2026-10-06"),"synthetic note")},
        "reminder times" to {resave({it},listOf(LocalTime.of(7,30),LocalTime.of(19,30)))},
        "reminder lead and late threshold" to {resave({it.copy(soon_alert_minutes=30,late_after_minutes=120)})},
        "notifications" to {resave({it.copy(notifications_on=!it.notifications_on)})},
        "language and region" to {Locale.setDefault(Locale.FRANCE)},
        "device time zone" to {TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))},
        "backup export and restore" to {val pwd="synthetic-pass".toCharArray();repo.restoreBackup(repo.exportBackup(pwd),pwd)},
        "process restart" to {db.close();open()},
        // §39 recycle bin: deleting and restoring, or deleting for good, content that is not a treatment fact.
        "bin: milestone delete and restore" to {repo.saveMilestone(MilestoneEntity(date="2026-07-01",title="Synthetic"));repo.deleteMilestone(db.dao().milestones().last().id);repo.restoreTrash(repo.trash().first().id)},
        "bin: milestone delete for good" to {repo.saveMilestone(MilestoneEntity(date="2026-07-02",title="Synthetic"));repo.deleteMilestone(db.dao().milestones().last().id);repo.purgeTrash(repo.trash().first().id)},
        "bin: lab delete, restore, delete for good" to {repo.saveLab(LabValueEntity(analyte_code="E2",value=100.0,unit="pg/mL",sampled_utc=at("2026-07-03","09:00").toEpochMilli(),sampled_zone=paris.id))
            val lab=db.dao().labs().last().id;repo.deleteLab(lab);repo.restoreTrash(repo.trash().first().id);repo.deleteLab(lab);repo.purgeTrash(repo.trash().first().id)},
        "bin: day note and score" to {val item=repo.checkinItems().first().id;repo.setNote(LocalDate.parse("2026-07-04"),"n");repo.setScore(LocalDate.parse("2026-07-04"),item,3)
            repo.setNote(LocalDate.parse("2026-07-04"),"");repo.setScore(LocalDate.parse("2026-07-04"),item,null);repo.trash().forEach{repo.restoreTrash(it.id)}
            repo.setNote(LocalDate.parse("2026-07-04"),"");repo.trash().forEach{repo.purgeTrash(it.id)}},
        "bin: intake delete and restore" to {val r=repo.records().filter{it.taken_utc!=null}.maxBy{it.taken_utc!!};repo.deleteRecord(r.id);repo.restoreTrash(repo.trash().first{it.kind==Trash.RECORD}.id)},
        "bin: period delete and restore" to {
            val v=PeriodTimelineProjection.build(NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),historyPeriods=repo.historyPeriods()),emptyList(),now)
            val p=v.projection.periods.first{it.finalStandardSpanKeys.isNotEmpty()}
            TimelineEdits.delete(repo.historyPeriods(),v.projection,p,{"{}"})?.let{repo.deletePeriod(it.replace,it.rows)}
            repo.restoreTrash(repo.trash().first{it.kind==Trash.PERIOD}.id)},
    )

    private fun run(dataset:suspend ()->Unit,ops:List<suspend ()->Unit>):Snapshot=runBlocking {
        context.deleteDatabase(name);db.close();open();TimeZone.setDefault(TimeZone.getTimeZone(paris));Locale.setDefault(Locale.ROOT)
        dataset();snapshot();ops.forEach{it()};TimeZone.setDefault(TimeZone.getTimeZone(paris));snapshot()
    }

    @Test fun goldenDiagnosticsStructureIsOnePeriodWithEveryRecordInIt()=runBlocking {
        golden()
        val s=snapshot()
        assertEquals(s.periods.toString(),1,s.periods.count{p->s.standards[s.periods.indexOf(p)].isNotEmpty()})
        val key=s.periods.single{p->s.standards[s.periods.indexOf(p)].isNotEmpty()}.first
        assertTrue(s.recordPeriods.values.all{it==key})
        assertTrue(s.labels.toString(),s.labels.values.none{net.plainnotes.app.domain.RecordLabel.REGIMEN_UNKNOWN in it || net.plainnotes.app.domain.RecordLabel.PENDING_PERIOD in it})
    }

    @Test fun nonTreatmentOperationsNeverChangePeriodsOrLabels() {
        val failures=mutableListOf<String>()
        datasets.forEach{(dataName,dataset)->
            val before=run(dataset,emptyList())
            operations.forEach{(opName,op)->val after=run(dataset,listOf(op));if(after!=before)failures+="$dataName / $opName:\n  before=$before\n  after=$after"}
            // All of them, one after another.
            val all=run(dataset,operations.map{it.second});if(all!=before)failures+="$dataName / all operations:\n  before=$before\n  after=$all"
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }

    @Test fun orderOfStockEditsAndConfirmationsDoesNotMatter() {
        val confirm:suspend ()->Unit={repo.confirmHistoryPeriod(null,med,standard,LocalDate.parse("2026-05-01"),LocalDate.parse("2026-09-01"),paris,"{}","{}",t)}
        val stock:suspend ()->Unit={repo.addContainers(med,60.0,1,true);repo.setRemaining(repo.containers().first().id,10.0)}
        val name:suspend ()->Unit={resave({it.copy(name="Renamed")})}
        val orders=listOf(listOf(stock,confirm,name),listOf(confirm,stock,name),listOf(name,confirm,stock))
        val results=orders.map{run({historyOnly()},it)}
        assertEquals(results[0],results[1]);assertEquals(results[0],results[2])
        assertTrue(results[0].standards.any{it.isNotEmpty()})
    }

    @Test fun dstChangeAndDosesAroundMidnightStayInOnePeriodBeforeTheStop()=runBlocking {
        med=db.dao().insertMedication(base)
        dailyRecords("2026-03-10","2026-04-20",hours=listOf("00:20","23:50"))
        val s=snapshot()
        assertEquals(s.periods.toString(),1,s.standards.count{it.isNotEmpty()})
        val key=s.periods[s.standards.indexOfFirst{it.isNotEmpty()}].first
        assertTrue(s.recordPeriods.values.all{it==key})
        // The DST day (23 hours) keeps both doses on their own local day.
        assertEquals(2,repo.records().count{Instant.ofEpochMilli(it.taken_utc!!).atZone(paris).toLocalDate()==LocalDate.parse("2026-03-29")})
    }

    /** §38 root cause: build 23's one-tap "delete this part" wrote a whole-day deletion of 10-06. */
    @Test fun aDeletedDayIsNeverAPlanWithoutPartsAndAnExactDeleteKeepsTheDay()=runBlocking {
        golden()
        val day=TimelineEditRow(HistoryPeriods.DELETED,med,null,LocalDate.parse("2026-10-06"),LocalDate.parse("2026-10-07"),paris,"{}")
        val group=repo.editTimeline(emptyList(),listOf(day))
        val extra=NotesViewModel.ExtraState(records=repo.records(),regimens=db.dao().regimens(),historyPeriods=repo.historyPeriods())
        val v=PeriodTimelineProjection.build(extra,emptyList(),now);val p=v.projection
        // Every period with a plan has at least one part, and the deleted day itself shows no plan.
        p.periods.filter{it.finalStandardSpanKeys.isNotEmpty()}.forEach{d->assertTrue(d.toString(),p.raw.any{it.span.from<(d.until ?: Instant.MAX) && (it.span.until ?: Instant.MAX)>d.from})}
        assertTrue(p.periodAt(at("2026-10-06","12:00"))!!.finalStandardSpanKeys.isEmpty())
        val labels=HistoryLabels.build(extra.records,v,emptyList(),paris)
        extra.records.filter{it.taken_utc!=null && Instant.ofEpochMilli(it.taken_utc!!).atZone(paris).toLocalDate()==LocalDate.parse("2026-10-06")}
            .forEach{assertEquals(setOf(net.plainnotes.app.domain.RecordLabel.REGIMEN_UNKNOWN),labels[it.id])}
        // The diagnostics explain the boundary instead of "no plan part starts at this period's start".
        val text=MergeDiagnostics.text(v,extra,p.periodAt(at("2026-10-06","12:00"))!!,24)
        assertTrue(text,text.contains("user_edit kind=DELETED") && text.contains("user DELETED starts") && !text.contains("no plan part starts"))
        // Restoring brings back one period.
        repo.undoTimelineEdit(group);assertEquals(1,snapshot().standards.count{it.isNotEmpty()})
        // Deleting exactly the every-11-days version (14:32-15:36 UTC) removes only that hour, not the day.
        val v1=db.dao().regimens().minBy{it.effective_from_utc}
        repo.editTimeline(emptyList(),listOf(day.copy(exactFromUtc=v1.effective_from_utc,exactUntilUtc=v1.effective_until_utc)))
        val exact=snapshot()
        assertEquals(exact.periods.toString(),2,exact.standards.count{it.isNotEmpty()})
        val morning=repo.records().first{it.taken_utc!=null && it.taken_utc==at("2026-10-06","08:05").toEpochMilli()}
        assertNotNull(exact.recordPeriods[morning.id]);assertNull(exact.labels[morning.id])
    }
}
