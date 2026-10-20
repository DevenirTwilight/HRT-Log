package net.plainnotes.app.ui

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import net.plainnotes.app.domain.ScheduleEngine

/** Unchanged wall time is the original event, including the second occurrence of a DST overlap. */
internal data class LabSampleEdit(val utc:Long,val zone:String)
internal fun labSampleForEdit(originalUtc:Long,originalZone:String,edited:LocalDateTime,displayZone:ZoneId=ZoneId.systemDefault()):LabSampleEdit =
    if(edited==Instant.ofEpochMilli(originalUtc).atZone(displayZone).toLocalDateTime()) LabSampleEdit(originalUtc,originalZone)
    else LabSampleEdit(ScheduleEngine.wallInstant(edited,displayZone).toEpochMilli(),displayZone.id)
