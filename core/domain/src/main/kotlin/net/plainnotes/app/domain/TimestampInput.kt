package net.plainnotes.app.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Explicit offsets preserve actual instants in the second occurrence of a DST overlap. */
object TimestampInput {
    fun format(instant: Instant, zone: ZoneId): String = instant.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    fun parse(value: String, zone: ZoneId): Instant = try {
        OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (_: DateTimeParseException) {
        ScheduleEngine.wallInstant(LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME), zone)
    }
}
