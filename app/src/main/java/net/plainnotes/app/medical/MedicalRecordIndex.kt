package net.plainnotes.app.medical
import net.plainnotes.app.data.*
import java.time.*
object MedicalRecordIndex {
    fun past(rows:List<AppointmentEntity>,now:Instant)=rows.filter{it.at_utc<=now.toEpochMilli()}.sortedWith(compareByDescending<AppointmentEntity>{it.at_utc}.thenByDescending{it.id})
    fun labs(rows:List<LabValueEntity>,from:LocalDate,to:LocalDate,zone:ZoneId):List<LabValueEntity>{require(!to.isBefore(from));val start=from.atStartOfDay(zone).toInstant().toEpochMilli();val end=to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();return rows.filter{it.sampled_utc>=start && it.sampled_utc<end}.sortedWith(compareByDescending<LabValueEntity>{it.sampled_utc}.thenByDescending{it.id})}
}
/** No automatic DST gap shift or guessed overlap; unchanged records preserve their exact stored instant. */
object MedicalVisitTime {
    fun resolve(time:LocalDateTime,zone:ZoneId,original:AppointmentEntity?=null,chosen:ZoneOffset?=null):Instant? {
        if(original!=null && original.at_zone==zone.id && Instant.ofEpochMilli(original.at_utc).atZone(zone).toLocalDateTime()==time && (chosen==null || chosen==Instant.ofEpochMilli(original.at_utc).atZone(zone).offset))return Instant.ofEpochMilli(original.at_utc)
        val offsets=zone.rules.getValidOffsets(time)
        if(chosen!=null && chosen !in offsets)return null
        val offset=if(offsets.size==1)offsets.single() else chosen?.takeIf{it in offsets}
        return offset?.let{time.toInstant(it)}
    }
}
