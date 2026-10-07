package net.plainnotes.app.timeline

import net.plainnotes.app.NotesViewModel.ExtraState
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.symptoms.SymptomCatalog
import java.time.*

enum class EventKind { REGIMEN, DOSE, MISSED, UNCONFIRMED, SKIPPED, LAB, SYMPTOM, WELLBEING, REVIEW, APPOINTMENT, MILESTONE, PLANNED }
sealed interface EventSource {
    data class Regimen(val epoch:TreatmentEpoch):EventSource
    data class Intake(val record:RecordEntity,val plannedRegimenId:Long?):EventSource
    data class Unconfirmed(val records:List<RecordEntity>):EventSource
    data class Lab(val value:LabValueEntity):EventSource
    data class Symptom(val value:SymptomCheckEntity):EventSource
    data class Daily(val scores:List<CheckinScoreEntity>,val note:DayNoteEntity?):EventSource
    data class Review(val value:StageReviewEntity):EventSource
    data class Appointment(val value:AppointmentEntity):EventSource
    data class Milestone(val value:MilestoneEntity):EventSource
    data class Planned(val entry:TimelineEntry):EventSource
}
data class LongitudinalEvent(val key:String,val kind:EventKind,val at:Instant?,val date:LocalDate?,val epochs:EpochResolution,
    val medicationIds:Set<Long>,val source:EventSource)
data class LongitudinalRecord(val epochs:List<TreatmentEpoch>,val events:List<LongitudinalEvent>)

/** A view of saved facts, never a causal interpretation. Date-only observations have no invented instant. */
object LongitudinalProjection {
    fun build(extra:ExtraState,appointments:List<AppointmentEntity>,zone:ZoneId,includePlanned:Boolean=false,medicationId:Long?=null):LongitudinalRecord {
        val epochs=TreatmentEpochs.build(extra.regimens.map{it.span()})
        val versions=extra.regimens.associateBy{it.id};val links=extra.regimenLinks.associate{it.rule_id to it.regimen_id}
        val events=mutableListOf<LongitudinalEvent>()
        fun add(key:String,kind:EventKind,at:Instant?,day:LocalDate?,meds:Set<Long>,source:EventSource) {
            // Global observations remain visible; there is no inferred drug relationship.
            if(medicationId!=null && meds.isNotEmpty() && medicationId !in meds)return
            events+=LongitudinalEvent(key,kind,at,day ?: at?.atZone(zone)?.toLocalDate(),
                if(at!=null)TreatmentEpochs.at(epochs,at) else if(day!=null)TreatmentEpochs.onDate(epochs,day,zone) else EpochResolution(emptySet(),true),meds,source)
        }
        epochs.forEachIndexed{i,e->
            val ids=(e.regimenIds+epochs.getOrNull(i-1)?.regimenIds.orEmpty()).mapNotNull{versions[it]?.medication_id}.toSet()
            add(e.key,EventKind.REGIMEN,e.from,null,ids,EventSource.Regimen(e))
        }
        val records=extra.records.filter{it.deleted_at_utc==null && (medicationId==null || it.medication_id==medicationId)}
        val unconfirmed=records.filter{it.status=="MISSED" && it.origin=="AUTO_MISSED"}
        unconfirmed.groupBy{r->
            val at=r.scheduled_utc?.let(Instant::ofEpochMilli)
            at?.atZone(zone)?.toLocalDate() to at?.let{TreatmentEpochs.at(epochs,it).keys.singleOrNull()}
        }.forEach{(group,rows)->
            // Count is a daily summary; individual scheduled instants remain in the source.
            add("unconfirmed:${group.first}:${group.second}",EventKind.UNCONFIRMED,null,group.first,rows.map{it.medication_id}.toSet(),EventSource.Unconfirmed(rows))
        }
        records.filterNot{it in unconfirmed}.forEach{r->
            val kind=when(r.status){"ON_TIME","LATE"->EventKind.DOSE;"MISSED"->EventKind.MISSED;"SKIPPED"->EventKind.SKIPPED;else->return@forEach}
            val at=(if(kind==EventKind.DOSE)r.taken_utc else r.scheduled_utc)?.let(Instant::ofEpochMilli)
            add("dose:${r.id}",kind,at,null,setOf(r.medication_id),EventSource.Intake(r,r.rule_version_id?.let(links::get)))
        }
        extra.labs.forEach{add("lab:${it.id}",EventKind.LAB,Instant.ofEpochMilli(it.sampled_utc),null,emptySet(),EventSource.Lab(it))}
        extra.symptoms.forEach{add("symptom:${it.date}:${it.group_id}",EventKind.SYMPTOM,null,LocalDate.parse(it.date),SymptomCatalog.saved(it)?.medicationIds.orEmpty(),EventSource.Symptom(it))}
        val scores=extra.scores.groupBy{it.date};val notes=extra.notes.associateBy{it.date}
        (scores.keys+notes.keys).forEach{date->add("daily:$date",EventKind.WELLBEING,null,LocalDate.parse(date),emptySet(),EventSource.Daily(scores[date].orEmpty(),notes[date]))}
        extra.reviews.forEach{add("review:${it.id}",EventKind.REVIEW,null,LocalDate.parse(it.date),emptySet(),EventSource.Review(it))}
        appointments.forEach{add("appointment:${it.id}",EventKind.APPOINTMENT,Instant.ofEpochMilli(it.at_utc),null,emptySet(),EventSource.Appointment(it))}
        extra.milestones.forEach{add("milestone:${it.id}",EventKind.MILESTONE,null,LocalDate.parse(it.date),emptySet(),EventSource.Milestone(it))}
        if(includePlanned)extra.upcoming.forEach{add("planned:${it.slot.key}",EventKind.PLANNED,it.slot.at,null,setOf(it.slot.medicationId),EventSource.Planned(it))}
        return LongitudinalRecord(epochs,events.sortedWith(compareByDescending<LongitudinalEvent>{it.date}.thenByDescending{it.at}.thenBy{it.key}))
    }
}
