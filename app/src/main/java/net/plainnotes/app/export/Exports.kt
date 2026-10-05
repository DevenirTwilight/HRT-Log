package net.plainnotes.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.data.*
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Plain data handed to exporters; nothing here reads the database itself. */
class ExportData(
    val medications: List<MedicationEntity>, val profiles: Map<Long, ProfileEntity>, val records: List<RecordEntity>, val labs: List<LabValueEntity>,
    val items: List<CheckinItemEntity>, val scores: List<CheckinScoreEntity>, val notes: List<DayNoteEntity>,
    val scheduleText: Map<Long, String>, val itemLabel: (CheckinItemEntity) -> String,
)

object CsvExport {
    private fun esc(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    private fun line(vararg v: Any?) = v.joinToString(",") { esc(it) } + "\r\n"
    private val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private fun ts(ms: Long?, zone: String?) = ms?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(zone ?: ZoneId.systemDefault().id)).format(iso) }

    /** Writes intakes.csv, wellbeing.csv and labs.csv into one zip (UTF-8 with BOM so spreadsheets read accents and CJK). */
    fun write(d: ExportData, out: OutputStream) {
        val meds = d.medications.associateBy { it.id }
        ZipOutputStream(out).use { zip ->
            fun file(name: String, body: StringBuilder) { zip.putNextEntry(ZipEntry(name)); zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())); zip.write(body.toString().toByteArray()); zip.closeEntry() }
            file("intakes.csv", StringBuilder(line("taken_at", "planned_at", "medication", "molecule", "ester", "route", "actual_dose", "planned_dose", "unit", "status", "site", "origin")).apply {
                d.records.filter { it.deleted_at_utc == null }.sortedBy { it.taken_utc ?: it.scheduled_utc }.forEach { r -> val m = meds[r.medication_id]
                    append(line(ts(r.taken_utc, r.taken_zone), ts(r.scheduled_utc, r.scheduled_zone), m?.name, m?.molecule, d.profiles[r.medication_id]?.ester, m?.route,
                        r.actual_dose, r.planned_dose, m?.unit, r.status, r.site, r.origin)) }
            })
            file("wellbeing.csv", StringBuilder(line("date", "item", "value_1_to_5", "note")).apply {
                val items = d.items.associateBy { it.id }
                (d.scores.map { it.date } + d.notes.map { it.date }).distinct().sorted().forEach { date ->
                    val note = d.notes.firstOrNull { it.date == date }?.text
                    val day = d.scores.filter { it.date == date }
                    if (day.isEmpty()) append(line(date, "", "", note)) else day.forEachIndexed { i, s -> append(line(date, items[s.item_id]?.let(d.itemLabel), s.value, if (i == 0) note else "")) }
                }
            })
            file("labs.csv", StringBuilder(line("sampled_at", "analyte", "value", "unit", "report_lower", "report_upper", "report_unit", "laboratory", "note")).apply {
                d.labs.sortedBy { it.sampled_utc }.forEach { l -> append(line(ts(l.sampled_utc, l.sampled_zone), l.analyte_code, l.value, l.unit, l.reference_lower, l.reference_upper, l.reference_unit, l.laboratory, l.note)) }
            })
        }
    }
}

/** A4 report for a prescriber: medications, adherence, lab results, optional concentration estimate. Plain text, no interpretation. */
object PdfReport {
    private const val W = 595; private const val H = 842; private const val M = 48f

    fun write(context: Context, d: ExportData, days: Int, conc: ConcentrationResult?, out: OutputStream) {
        val zone = ZoneId.systemDefault()
        val locale = context.resources.configuration.locales[0]
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val dtFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
        val to = LocalDate.now(); val from = to.minusDays(days.toLong() - 1)
        val since = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val doc = PdfDocument()
        var pageNo = 0; var page: PdfDocument.Page? = null; var canvas: Canvas? = null; var y = 0f
        val title = TextPaint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK; isAntiAlias = true }
        val head = TextPaint().apply { textSize = 13f; isFakeBoldText = true; color = Color.rgb(0, 0x6A, 0x63); isAntiAlias = true }
        val body = TextPaint().apply { textSize = 10f; color = Color.BLACK; isAntiAlias = true }
        val small = TextPaint().apply { textSize = 8.5f; color = Color.DKGRAY; isAntiAlias = true }
        fun newPage() { page?.let(doc::finishPage); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()); canvas = page!!.canvas; y = M
            canvas!!.drawText(context.getString(R.string.report_footer, pageNo), M, H - 24f, small) }
        fun text(s: String, p: TextPaint, gap: Float = 4f) {
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, (W - 2 * M).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            if (y + layout.height > H - 48f) newPage()
            canvas!!.save(); canvas!!.translate(M, y); layout.draw(canvas!!); canvas!!.restore(); y += layout.height + gap
        }
        fun row(cols: List<String>, widths: List<Float>, p: TextPaint) {
            if (y + 14f > H - 48f) newPage()
            var x = M; cols.forEachIndexed { i, c -> canvas!!.drawText(TextUtilsEllipsize(c, p, widths[i] - 6f), x, y + 10f, p); x += widths[i] }; y += 14f
        }
        newPage()
        text(context.getString(R.string.report_title), title, 2f)
        text(context.getString(R.string.report_period, from.format(dateFmt), to.format(dateFmt)) + " · " + context.getString(R.string.report_generated, java.time.LocalDateTime.now().format(dtFmt)), small, 12f)

        text(context.getString(R.string.medications), head)
        d.medications.filter { it.active }.forEach { m ->
            val ester = d.profiles[m.id]?.ester
            text("• ${m.name} — ${listOfNotNull(m.molecule, ester?.takeIf { it != "E2" }, m.route).joinToString(" / ")} — ${fmt(m.dose_per_intake)} ${m.unit} · ${d.scheduleText[m.id] ?: ""}", body, 2f)
        }
        y += 8f
        text(context.getString(R.string.adherence_title), head)
        val inRange = d.records.filter { it.deleted_at_utc == null && (it.taken_utc ?: it.scheduled_utc ?: 0) >= since }
        d.medications.filter { m -> inRange.any { it.medication_id == m.id } }.forEach { m ->
            val r = inRange.filter { it.medication_id == m.id && it.scheduled_utc != null }
            val onTime = r.count { it.status == "ON_TIME" }; val late = r.count { it.status == "LATE" }; val missed = r.count { it.status == "MISSED" }; val skipped = r.count { it.status == "SKIPPED" }
            val unscheduled = inRange.count { it.medication_id == m.id && it.scheduled_utc == null && it.status in listOf("ON_TIME", "LATE") }
            text("• ${m.name}: " + context.getString(R.string.report_adherence_line, onTime, late, missed, skipped, unscheduled), body, 2f)
        }
        y += 8f
        val labs = d.labs.filter { it.sampled_utc >= since }.sortedBy { it.sampled_utc }
        text(context.getString(R.string.labs), head)
        if (labs.isEmpty()) text(context.getString(R.string.report_none), body) else {
            val widths = listOf(120f, 90f, 90f, 120f, 79f)
            row(listOf(context.getString(R.string.report_col_date), context.getString(R.string.lab_analyte), context.getString(R.string.lab_value),
                context.getString(R.string.report_col_range), context.getString(R.string.report_col_since_dose)), widths, small.apply { isFakeBoldText = true })
            small.isFakeBoldText = false
            val doseTimes = d.records.filter { r -> r.status in listOf("ON_TIME", "LATE") && r.deleted_at_utc == null && d.medications.firstOrNull { it.id == r.medication_id }?.molecule == "E2" }.mapNotNull { it.taken_utc }.sorted()
            labs.forEach { l ->
                val last = doseTimes.lastOrNull { it <= l.sampled_utc }?.let { val m = (l.sampled_utc - it) / 60000; "${m / 60} h ${m % 60} min" } ?: ""
                val range = if (l.reference_lower != null || l.reference_upper != null) "${l.reference_lower?.let(::fmt) ?: "–"} – ${l.reference_upper?.let(::fmt) ?: "–"} ${l.reference_unit ?: ""}" else ""
                row(listOf(Instant.ofEpochMilli(l.sampled_utc).atZone(zone).format(dtFmt), l.analyte_code, "${fmt(l.value)} ${l.unit}", range, last), widths, body)
            }
        }
        y += 8f
        if (conc != null && conc.timeH.isNotEmpty()) {
            if (y + 260f > H - 48f) newPage()
            text(context.getString(R.string.report_conc_title), head)
            text(context.getString(R.string.pk_disclaimer_short), small)
            drawChart(canvas!!, conc, M, y, W - 2 * M, 180f, from.atStartOfDay(zone).toEpochSecond() / 3600.0, conc.nowH)
            y += 196f
        }
        val scored = d.scores.filter { it.date >= from.toString() }
        if (scored.isNotEmpty()) {
            text(context.getString(R.string.wellbeing), head)
            d.items.forEach { item -> scored.filter { it.item_id == item.id }.takeIf { it.isNotEmpty() }?.let { s ->
                text("• ${d.itemLabel(item)}: ${context.getString(R.string.wb_average, fmt(s.map { it.value }.average(), 1))} (n = ${s.size})", body, 2f) } }
        }
        page?.let(doc::finishPage)
        doc.writeTo(out); doc.close()
    }

    private fun fmt(v: Double, decimals: Int = 3) = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = decimals; isGroupingUsed = false }.format(v)

    private fun TextUtilsEllipsize(s: String, p: TextPaint, w: Float) = android.text.TextUtils.ellipsize(s, p, w, android.text.TextUtils.TruncateAt.END).toString()

    private fun drawChart(c: Canvas, r: ConcentrationResult, x: Float, y: Float, w: Float, h: Float, startH: Double, endH: Double) {
        val idx = r.timeH.indices.filter { r.timeH[it] in startH..endH }
        if (idx.size < 2) return
        val maxY = (idx.maxOf { r.ci95?.second?.get(it) ?: r.e2[it] }.coerceAtLeast(1.0)) * 1.1
        val axis = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f }
        val label = Paint().apply { color = Color.DKGRAY; textSize = 8f; isAntiAlias = true }
        c.drawLine(x, y + h, x + w, y + h, axis); c.drawLine(x, y, x, y + h, axis)
        listOf(0.0, maxY / 2, maxY).forEach { v -> c.drawText("${v.toInt()} pg/mL", x + 2, (y + h - v / maxY * h).toFloat() - 2, label) }
        fun px(t: Double) = (x + (t - startH) / (endH - startH) * w).toFloat()
        fun py(v: Double) = (y + h - v / maxY * h).toFloat()
        r.ci95?.let { (lo, hi) -> val band = Path(); var first = true
            idx.forEach { i -> if (first) { band.moveTo(px(r.timeH[i]), py(hi[i])); first = false } else band.lineTo(px(r.timeH[i]), py(hi[i])) }
            idx.reversed().forEach { i -> band.lineTo(px(r.timeH[i]), py(lo[i])) }; band.close()
            c.drawPath(band, Paint().apply { color = Color.argb(40, 0, 0x6A, 0x63); style = Paint.Style.FILL }) }
        val line = Path(); idx.forEachIndexed { k, i -> if (k == 0) line.moveTo(px(r.timeH[i]), py(r.e2[i])) else line.lineTo(px(r.timeH[i]), py(r.e2[i])) }
        c.drawPath(line, Paint().apply { color = Color.rgb(0, 0x6A, 0x63); style = Paint.Style.STROKE; strokeWidth = 1.4f; isAntiAlias = true })
        r.labs.filter { it.first in startH..endH }.forEach { (t, v) -> c.drawCircle(px(t), py(v), 2.8f, Paint().apply { color = Color.rgb(0x8E, 0x4D, 0x35); isAntiAlias = true }) }
        c.drawLine(px(endH), y, px(endH), y + h, Paint().apply { color = Color.GRAY; pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) })
    }
}
