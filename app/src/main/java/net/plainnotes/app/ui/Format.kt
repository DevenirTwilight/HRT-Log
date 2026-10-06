package net.plainnotes.app.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.plainnotes.app.R
import net.plainnotes.app.ScheduleSummary
import net.plainnotes.app.domain.RuleKind
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Locale-aware pattern from a skeleton such as "MMMdEEE" or "Hm". */
@Composable private fun pattern(skeleton: String): DateTimeFormatter {
    val locale = currentLocale()
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}
@Composable fun formatTime(time: LocalTime): String {
    val h24 = DateFormat.is24HourFormat(LocalContext.current)
    return time.format(pattern(if (h24) "Hm" else "hm"))
}
@Composable fun formatTime(instant: Instant): String = formatTime(instant.atZone(ZoneId.systemDefault()).toLocalTime())
@Composable fun formatDate(date: LocalDate): String = date.format(pattern("yMMMdEEE"))
@Composable fun formatShortDate(date: LocalDate): String = date.format(pattern("MMMdEEE"))
@Composable fun formatDateTime(instant: Instant): String {
    val z = instant.atZone(ZoneId.systemDefault())
    return formatShortDate(z.toLocalDate()) + " " + formatTime(z.toLocalTime())
}

private val doseFormat = ThreadLocal.withInitial { DecimalFormat("0.###", DecimalFormatSymbols(Locale.ROOT)) }
/** Prefill for editable number fields: always "." so parsing never depends on the UI language. */
fun inputNumber(value: Double): String = doseFormat.get()!!.format(value)
/** Numbers shown to the user, with the UI language's decimal separator (1,5 in French) and no trailing zeros. */
@Composable fun displayNumber(value: Double, maxDecimals: Int = 3): String {
    val locale = currentLocale()
    return java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = maxDecimals; isGroupingUsed = false }.format(value)
}
@Composable fun formatDose(value: Double, unit: String?): String = if (unit == null) displayNumber(value) else stringResource(R.string.dose_display, displayNumber(value), choiceLabel(unit))

@Composable fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, currentLocale())

@Composable fun scheduleText(s: ScheduleSummary?): String {
    if (s == null) return stringResource(R.string.schedule_none)
    val times = s.times.map { formatTime(it) }.joinToString(" · ")
    return when (s.kind) {
        RuleKind.EVERY_N_HOURS -> stringResource(R.string.schedule_hours, s.interval)
        RuleKind.EVERY_N_DAYS -> if (s.interval == 1) stringResource(R.string.schedule_daily, times) else stringResource(R.string.schedule_days, s.interval, times)
        RuleKind.WEEKLY -> {
            val days = DayOfWeek.entries.filter { it in s.weekdays }.map { weekdayShort(it) }.joinToString(" ")
            if (s.interval == 1) stringResource(R.string.schedule_weekly, days, times) else stringResource(R.string.schedule_weeks, s.interval, days, times)
        }
    }
}

@Composable fun choiceLabel(value: String): String = stringResource(choiceRes(value))
fun choiceRes(value: String): Int = when (value) {
    "E2" -> R.string.choice_e2; "T" -> R.string.choice_t; "P4" -> R.string.choice_p4; "CPA" -> R.string.choice_cpa; "SPI" -> R.string.choice_spi
    "BICA" -> R.string.choice_bica; "FIN" -> R.string.choice_fin; "DUT" -> R.string.choice_dut; "DHT" -> R.string.choice_dht; "CMA" -> R.string.choice_cma
    "NOMAC" -> R.string.choice_nomac; "TRIP" -> R.string.choice_trip; "ORAL" -> R.string.choice_oral; "SUBLINGUAL" -> R.string.choice_sublingual
    "GEL" -> R.string.choice_gel; "PATCH" -> R.string.choice_patch; "INJECTION" -> R.string.choice_injection; "EV" -> R.string.choice_ev
    "EC" -> R.string.choice_ec; "EB" -> R.string.choice_eb; "EN" -> R.string.choice_en; "EU" -> R.string.choice_eu; "MG" -> R.string.choice_mg
    "TABLET" -> R.string.choice_tablet; "ML" -> R.string.choice_ml; "PUMP" -> R.string.choice_pump
    "EVERY_N_DAYS" -> R.string.choice_every_n_days; "EVERY_N_HOURS" -> R.string.choice_every_n_hours; "WEEKLY" -> R.string.choice_weekly
    "ENDO" -> R.string.appt_endo; "GP" -> R.string.appt_gp; "LAB" -> R.string.appt_lab; "PSY" -> R.string.appt_psy; "SURGERY" -> R.string.appt_surgery
    else -> R.string.choice_other
}
/** Unit label used after a number; the PATCH unit reads as a count, not the route. */
@Composable fun unitLabel(unit: String): String = if (unit == "PATCH") stringResource(R.string.unit_patch) else choiceLabel(unit)

/**
 * Common brand names in China and France for a molecule or ester code, shown next to the generic name so people
 * recognise their medicine. Proper nouns, identical in every language; null when none is listed.
 */
fun brandNames(code: String): String? = when (code) {
    "EV" -> "补佳乐 / Progynova"
    "E2" -> "Provames · Oestrodose · Estreva · 爱斯妥 / Oestrogel"
    "EC" -> "Depo-Estradiol"
    "CPA" -> "色普龙 / Androcur"
    "SPI" -> "安体舒通 / Aldactone"
    "P4" -> "安琪坦 / Utrogestan"
    "BICA" -> "康士得 / Casodex"
    "FIN" -> "保列治 / Proscar · Propecia"
    "DUT" -> "安福达 / Avodart"
    "DHT" -> "Andractim"
    "CMA" -> "Lutéran"
    "NOMAC" -> "Lutényl"
    "TRIP" -> "达菲林 / Décapeptyl"
    "T" -> "Androtardyl"
    else -> null
}
