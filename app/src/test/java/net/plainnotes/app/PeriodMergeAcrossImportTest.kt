package net.plainnotes.app

import net.plainnotes.app.data.*
import net.plainnotes.app.domain.RecordLabel
import net.plainnotes.app.timeline.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/**
 * Build 19 reproduction (synthetic data only): the same sublingual twice-daily regimen before and after the 10-06
 * import boundary must be one period, whichever medication entry, ester spelling or confirmation state the history has.
 */
class PeriodMergeAcrossImportTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val now=Instant.parse("2026-10-20T04:00:00Z")
    private val day0=LocalDate.of(2026,9,1)
    private val boundary=LocalDate.of(2026,10,6)
    private val cut=boundary.atTime(12,0).atZone(zone).toInstant()
    private val appMed=1L
    private fun med(id:Long,name:String)=MedicationEntity(id,name,"E2","SUBLINGUAL","MG",2.0,40.0,site_rotation=false,notifications_on=false,active=true,sort_order=id.toInt())
    private fun snapshot(id:Long,ester:String?)=MedicationSnapshot.encode(med(id,if(id==appMed)"Synthetic app E2" else "Synthetic imported E2"),ester?.let{ProfileEntity(id,it,"sublingual",sl_tier=2)})
    private fun at(day:LocalDate,hour:Int)=day.atTime(hour,0).atZone(zone).toInstant().toEpochMilli()

    enum class Entry{SAME,IMPORT_CREATED}
    enum class Esters(val imported:String?,val app:String?){IMPORT_MISSING(null,"E2"),SAME("E2","E2"),APP_MISSING("E2",null)}
    enum class Before{PENDING,CONFIRMED_UNTIL_BOUNDARY,CONFIRMED_OPEN}

    private class Case(val records:List<RecordEntity>,val extra:NotesViewModel.ExtraState,val before:List<Long>,val after:List<Long>)

    private fun case(entry:Entry,esters:Esters,before:Before,appCadence:String="EVERY_N_DAYS",appDose:Double=2.0,appRoute:String="SUBLINGUAL"):Case {
        val htMed=if(entry==Entry.SAME)appMed else 2L
        val htJson=snapshot(htMed,esters.imported)
        val appJson=MedicationSnapshot.encode(med(appMed,"Synthetic app E2").copy(route=appRoute,dose_per_intake=appDose),esters.app?.let{ProfileEntity(appMed,it,appRoute.lowercase(),sl_tier=2)})
        var id=1L
        // HRT Tracker history twice daily until the boundary morning, with occasional single logs.
        val ht=generateSequence(day0){it.plusDays(1)}.takeWhile{it<=boundary}.flatMap{d->
            val hours=if(d==boundary)listOf(8) else if(d.dayOfMonth%9==0)listOf(8) else listOf(8,20)
            hours.map{h->RecordEntity(id++,htMed,taken_utc=at(d,h),taken_zone=zone.id,actual_dose=2.0,status="ON_TIME",origin="IMPORT_HT",
                source_record_key="ht:synthetic:merge:$d:$h",revision=1,config_snapshot=htJson)}
        }.toList()
        val definition=if(appCadence=="EVERY_N_HOURS")RegimenDefinition(appJson,"EVERY_N_HOURS",12,0,appDose,zone.id,null,cut.toEpochMilli(),emptyList())
            else RegimenDefinition(appJson,"EVERY_N_DAYS",1,0,appDose,zone.id,boundary.toString(),null,listOf("08:00:00" to null,"20:00:00" to null))
        val version=RegimenVersionEntity(1,appMed,cut.toEpochMilli(),null,zone.id,definition.json(),definition.signature(),"APP",cut.toEpochMilli())
        val app=generateSequence(boundary){it.plusDays(1)}.takeWhile{it<LocalDate.of(2026,10,20)}.flatMap{d->
            listOf(8,20).filter{h->at(d,h)>=cut.toEpochMilli()}.map{h->RecordEntity(id++,appMed,scheduled_utc=at(d,h),scheduled_zone=zone.id,planned_dose=appDose,
                taken_utc=at(d,h)+300_000,taken_zone=zone.id,actual_dose=appDose,status="ON_TIME",origin="APP",revision=1,config_snapshot=appJson)}
        }.toList()
        val standard=net.plainnotes.app.domain.TherapyStandard("E2",esters.imported,"SUBLINGUAL","MG",null,"EVERY_N_DAYS",1,0,listOf(2.0,2.0))
        val periods=if(before==Before.PENDING)emptyList() else listOf(HistoryPeriodEntity(1,"00000000-0000-4000-8000-000000000019",1,HistoryPeriods.CONFIRMED,htMed,htJson,
            HistoryPeriods.standardJson(standard),day0.toString(),if(before==Before.CONFIRMED_UNTIL_BOUNDARY)boundary.toString() else null,zone.id,"{}",created_utc=1L))
        val extra=NotesViewModel.ExtraState(records=ht+app,regimens=listOf(version),historyPeriods=periods)
        return Case(ht+app,extra,ht.map{it.id},app.map{it.id})
    }

    private fun check(c:Case,before:Before,label:String) {
        val view=PeriodTimelineProjection.build(c.extra,emptyList(),now)
        assertEquals("$label: one period",1,view.projection.periods.size)
        val period=view.projection.periods.single()
        val ids=view.projection.standards.single().rawVersionIds
        assertTrue("$label: saved plan in the period",1L in ids)
        if(before!=Before.PENDING)assertTrue("$label: confirmed part in the period",ids.any(ObservedTreatmentHistory::isConfirmedSpan))
        // Every record, from either source, belongs to that one period.
        c.records.forEach{r->assertEquals("$label: record ${r.id}",period.key,view.projection.periodAt(Instant.ofEpochMilli(r.taken_utc!!))?.key)}
        val labels=HistoryLabels.build(c.records,view,emptyList(),zone)
        c.after.forEach{assertNull("$label: app record $it",labels[it])}
        c.before.forEach{id->
            if(before==Before.PENDING)assertNull("$label: imported $id",labels[id])
            else assertNull("$label: imported $id",labels[id])
        }
    }

    @Test fun sameRegimenAcrossTheImportBoundaryIsOnePeriodInEveryVariant() {
        val failures=mutableListOf<String>()
        for(entry in Entry.entries)for(esters in Esters.entries)for(before in Before.entries) {
            val label="$entry/$esters/$before"
            runCatching{check(case(entry,esters,before),before,label)}.onFailure{failures+=it.message ?: label}
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }

    @Test fun twelveHourlyAppPlanEqualsTwiceDailyHistory() {
        for(before in Before.entries)check(case(Entry.IMPORT_CREATED,Esters.SAME,before,"EVERY_N_HOURS"),before,"12h/$before")
    }

    @Test fun realChangesAtTheBoundaryStillStartANewPeriod() {
        for(before in Before.entries)for(entry in Entry.entries) {
            for((name,changed) in listOf("dose" to case(entry,Esters.SAME,before,appDose=3.0),"route" to case(entry,Esters.SAME,before,appRoute="ORAL"))) {
                val view=PeriodTimelineProjection.build(changed.extra,emptyList(),now)
                fun periodOf(id:Long)=view.projection.periodAt(Instant.ofEpochMilli(changed.records.single{it.id==id}.taken_utc!!))!!.key
                val label="$name/$entry/$before"
                assertTrue(label,view.projection.periods.size>=2)
                assertNotEquals(label,periodOf(changed.before.first()),periodOf(changed.after.last()))
                assertEquals(label,setOf(view.projection.periods.last().key),changed.after.drop(2).map(::periodOf).toSet())
                // Same entry: the plan cuts at noon; another entry with another route may overlap for the rest of 10-06.
                if(entry==Entry.SAME || name=="dose")assertEquals(label,2,view.projection.periods.size)
            }
        }
    }
}
