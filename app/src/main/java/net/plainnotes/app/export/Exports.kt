package net.plainnotes.app.export

import androidx.core.graphics.withSave
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
    val containers:List<ContainerEntity> = emptyList(),val symptoms:List<SymptomCheckEntity> = emptyList(),val reviews:List<StageReviewEntity> = emptyList(),val labContexts:List<LabContextEntity> = emptyList(),
    val regimens:List<RegimenVersionEntity> = emptyList(),val milestones:List<MilestoneEntity> = emptyList(),val appointments:List<AppointmentEntity> = emptyList(),
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
            file("intakes.csv", StringBuilder(line("taken_at", "planned_at", "medication", "molecule", "ester", "route", "actual_dose", "planned_dose", "unit", "status", "site", "origin", "context_snapshot")).apply {
                d.records.filter { it.deleted_at_utc == null }.sortedBy { it.taken_utc ?: it.scheduled_utc }.forEach { r -> val m = MedicationSnapshot.decode(r.config_snapshot,r.medication_id)
                    append(line(ts(r.taken_utc, r.taken_zone), ts(r.scheduled_utc, r.scheduled_zone), m?.name, m?.molecule, m?.profile?.ester, m?.route,
                        r.actual_dose, r.planned_dose, m?.unit, if(r.unconfirmed)"UNCONFIRMED" else r.status, r.site, r.origin,r.config_snapshot)) }
            })
            file("wellbeing.csv", StringBuilder(line("date", "item", "value_1_to_5", "note")).apply {
                val items = d.items.associateBy { it.id }
                (d.scores.map { it.date } + d.notes.map { it.date }).distinct().sorted().forEach { date ->
                    val note = d.notes.firstOrNull { it.date == date }?.text
                    val day = d.scores.filter { it.date == date }
                    if (day.isEmpty()) append(line(date, "", "", note)) else day.forEachIndexed { i, s -> append(line(date, items[s.item_id]?.let(d.itemLabel), s.value, if (i == 0) note else "")) }
                }
            })
            file("packages.csv",StringBuilder(line("id","medication","capacity","used_amount","state","source_note","batch")).apply{d.containers.forEach{c->append(line(c.id,meds[c.medication_id]?.name,c.capacity,c.used_amount,c.state,c.source_note,c.batch))}})
            file("symptoms.csv",StringBuilder(line("date","symptom_group","note","context_snapshot")).apply{d.symptoms.sortedBy{it.date}.forEach{append(line(it.date,it.group_id,it.note,it.context_snapshot))}})
            file("reviews.csv",StringBuilder(line("date","effects_json","tolerance_note","risk_note","smoking","systolic_mmHg","diastolic_mmHg","weight_kg","satisfaction_1_to_5","satisfaction_note")).apply{d.reviews.sortedBy{it.date}.forEach{r->append(line(r.date,r.effects_json,r.tolerance_note,r.risk_note,r.smoking,r.systolic,r.diastolic,r.weight_kg,r.satisfaction,r.satisfaction_note))}})
            file("lab_contexts.csv",StringBuilder(line("lab_id","revision","captured_at","origin","context_snapshot")).apply{d.labContexts.sortedWith(compareBy<LabContextEntity>{it.lab_id}.thenBy{it.revision}).forEach{append(line(it.lab_id,it.revision,Instant.ofEpochMilli(it.captured_utc).toString(),it.origin,it.context_json))}})
            file("labs.csv", StringBuilder(line("sampled_at", "analyte", "value", "unit", "report_lower", "report_upper", "report_unit", "laboratory", "note")).apply {
                d.labs.sortedBy { it.sampled_utc }.forEach { l -> append(line(ts(l.sampled_utc, l.sampled_zone), l.analyte_code, l.value, l.unit, l.reference_lower, l.reference_upper, l.reference_unit, l.laboratory, l.note)) }
            })
        }
    }
}

/**
 * A4 report for a prescriber: medications, adherence, lab results, optional concentration estimate. Plain text, no interpretation.
 *
 * PDF-01 (REQUIREMENTS §145): Swiss layout only, the words are unchanged. A4 with 48 pt side, 56 pt top and 64 pt bottom
 * margins; a 12-column grid (12 pt gutters) on a 4 pt baseline; Roboto / Noto CJK in regular and bold only (26 / 13 / 10 /
 * 8 pt); black on white with one accent (#006A63) for section numbers and the rule under the header; left aligned, numbers
 * right aligned in tables. Sections are numbered 01, 02 … in the order they appear (the only new text), separated by space
 * alone; the only lines are the header rule, table rules and the footer rule.
 */
object PdfReport {
    private const val W = 595; private const val H = 842; private const val M = 48f
    internal const val TOP = 56f; internal const val BOTTOM = 64f; internal const val GUTTER = 12f; internal const val COLUMNS = 12
    /** One grid column (499 pt content, 11 gutters). */
    internal const val COL = (W - 2 * M - GUTTER * (COLUMNS - 1)) / COLUMNS
    internal fun colX(i: Int) = M + i * (COL + GUTTER)
    internal fun span(n: Int) = n * COL + (n - 1) * GUTTER
    internal val ACCENT = Color.rgb(0, 0x6A, 0x63)
    internal val GREY = Color.rgb(0x61, 0x61, 0x61)
    internal val RULE_LIGHT = Color.rgb(0xBD, 0xBD, 0xBD)
    /** The last baseline area for content; the footer sits below it. */
    internal const val CONTENT_BOTTOM = H - BOTTOM

    /** Where pages go: a PdfDocument in the app, a recording canvas in tests. */
    interface Sink { fun startPage(number: Int): Canvas; fun finishPage() }

    /** [visit] limits the output to the chosen parts; parts that are not chosen never appear. */
    fun write(context: Context, d: ExportData, days: Int, conc: ConcentrationResult?, out: OutputStream, period0:Pair<LocalDate,LocalDate>?=null,
              visit: net.plainnotes.app.visit.VisitPackSpec? = null, facts: net.plainnotes.app.visit.VisitFacts? = null, digest: String? = null,
              labels: Map<Long, Set<net.plainnotes.app.domain.RecordLabel>> = emptyMap()) {
        val doc = PdfDocument(); var page: PdfDocument.Page? = null
        render(context, d, days, conc, object : Sink {
            override fun startPage(number: Int): Canvas { page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create()); return page!!.canvas }
            override fun finishPage() { doc.finishPage(page) }
        }, period0, visit, facts, digest, labels)
        doc.writeTo(out); doc.close()
    }

    private fun round4(v: Float) = kotlin.math.ceil(v / 4f) * 4f

    /**
     * Lays the report out on [sink]. [trace] receives each piece of text as it is written (before wrapping or ellipsis) and
     * each section number, so tests can compare the words with the pre-PDF-01 report.
     */
    internal fun render(context: Context, d: ExportData, days: Int, conc: ConcentrationResult?, sink: Sink, period0:Pair<LocalDate,LocalDate>?=null,
              visit: net.plainnotes.app.visit.VisitPackSpec? = null, facts: net.plainnotes.app.visit.VisitFacts? = null, digest: String? = null,
              labels: Map<Long, Set<net.plainnotes.app.domain.RecordLabel>> = emptyMap(), now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
              trace: (String, String) -> Unit = { _, _ -> }) {
        val period = visit?.let { it.from to it.to } ?: period0
        fun on(section: VisitSection) = visit == null || section in visit.sections
        val zone = ZoneId.systemDefault()
        val locale = context.resources.configuration.locales[0]
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val dtFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
        val to = period?.second?:LocalDate.now(); val from = period?.first?:to.minusDays(days.toLong() - 1)
        require(from<=to)
        val until=to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val since = from.atStartOfDay(zone).toInstant().toEpochMilli()
        var pageNo = 0; var started = false; var canvas: Canvas? = null; var y = 0f
        val regular = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        val bold = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        fun paint(size: Float, face: android.graphics.Typeface, c: Int = Color.BLACK) = TextPaint().apply { textSize = size; typeface = face; color = c; isAntiAlias = true }
        val title = paint(26f, bold)
        val head = paint(13f, bold)
        val sub = paint(10f, bold)
        val body = paint(10f, regular)
        val meta = paint(8f, regular, GREY)
        val tableHead = paint(8f, bold)
        val number = paint(13f, bold, ACCENT).apply { fontFeatureSettings = "tnum" }
        fun line(c: Int, width: Float) = Paint().apply { color = c; strokeWidth = width; isAntiAlias = true }
        val contentX = colX(2); val contentW = span(10); val fullW = span(12)
        var atTop = true
        fun newPage() {
            if (started) sink.finishPage(); started = true; pageNo++; canvas = sink.startPage(pageNo); y = TOP; atTop = true
            // Footer: the same words as before, the note on the left and the page on the right, under a 0.25 pt rule.
            val footer = if(period==null)context.getString(R.string.report_footer,pageNo)else context.getString(R.string.wb_summary_footer)+" · "+pageNo
            trace("footer", footer)
            val pagePart = if (period == null) footer.split(" · ").first() else pageNo.toString()
            val note = if (period == null) footer.split(" · ").drop(1).joinToString(" · ") else context.getString(R.string.wb_summary_footer)
            canvas!!.drawLine(M, H - BOTTOM + 16f, W - M, H - BOTTOM + 16f, line(RULE_LIGHT, 0.25f))
            canvas!!.drawText(TextUtilsEllipsize(note, meta, span(9)), M, H - BOTTOM + 32f, meta)
            canvas!!.drawText(pagePart, W - M - meta.measureText(pagePart), H - BOTTOM + 32f, meta)
        }
        /** Left-aligned block at [x] / [width]; heights round up to the 4 pt baseline; long text continues on the next page. */
        fun block(s: String, p: TextPaint, x: Float, width: Float, gap: Float = 4f, record: Boolean = true) {
            if (record) trace("text", s)
            var remaining = s
            do {
                val layout = StaticLayout.Builder.obtain(remaining, 0, remaining.length, p, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                if (y + layout.getLineBottom(0) > CONTENT_BOTTOM) newPage()
                val fit = (0 until layout.lineCount).lastOrNull { y + layout.getLineBottom(it) <= CONTENT_BOTTOM } ?: 0
                val end = layout.getLineEnd(fit)
                canvas!!.withSave { translate(x, y); clipRect(0f, 0f, width, layout.getLineBottom(fit).toFloat()); layout.draw(this) }
                y += round4(layout.getLineBottom(fit).toFloat()) + gap
                atTop = false
                remaining = remaining.substring(end)
                if (remaining.isNotEmpty()) newPage()
            } while (remaining.isNotEmpty())
        }
        fun text(s: String, p: TextPaint = body, gap: Float = 4f) = block(s, p, contentX, contentW, gap)
        fun lineHeight(p: TextPaint) = round4(StaticLayout.Builder.obtain("Ag", 0, 2, p, contentW.toInt()).build().getLineBottom(0).toFloat())
        var sectionNo = 0
        /** 01, 02 … in the accent at the left two columns, the bold title on the right ten; never alone at a page foot. */
        fun section(titleText: String) {
            val need = 12f + lineHeight(head) + 4f + 2 * (lineHeight(body) + 4f)
            if (!atTop) { if (y + need > CONTENT_BOTTOM) newPage() else y += 12f }
            sectionNo++
            val n = "%02d".format(sectionNo); trace("number", n)
            val layout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, head, contentW.toInt()).build()
            canvas!!.drawText(n, colX(0), y + layout.getLineBaseline(0), number)
            block(titleText, head, contentX, contentW, 4f)
        }
        fun endSection() { y += 16f }
        /** A table on the right ten columns: 8 pt bold header over a 0.5 pt rule, rows split by 0.25 pt light rules; [numeric] columns align right. */
        class Table(val header: List<String>, val widths: List<Float>, val numeric: Set<Int>)
        fun drawRow(t: Table, cols: List<String>, p: TextPaint) {
            val h = lineHeight(p) + 4f
            var x = contentX
            cols.forEachIndexed { i, c -> val cell = TextUtilsEllipsize(c, p, t.widths[i] - 6f)
                val cx = if (i in t.numeric) x + t.widths[i] - 6f - p.measureText(cell) else x
                canvas!!.drawText(cell, cx, y + h - 6f, p); x += t.widths[i] }
            y += h
        }
        fun tableHeader(t: Table) {
            drawRow(t, t.header, tableHead)
            canvas!!.drawLine(contentX, y, contentX + contentW, y, line(Color.BLACK, 0.5f))
        }
        fun tableRow(t: Table, cols: List<String>) {
            trace("row", cols.joinToString("\n"))
            if (y + lineHeight(body) + 4f > CONTENT_BOTTOM) { newPage(); tableHeader(t) }
            drawRow(t, cols, body)
            canvas!!.drawLine(contentX, y, contentX + contentW, y, line(RULE_LIGHT, 0.25f))
        }

        newPage()
        // Header: title, then period / generated / check code in three four-column cells, the visit on its own line, an accent rule.
        block(context.getString(if(visit!=null)R.string.visit_pack_title else if(period==null)R.string.report_title else R.string.wb_summary), title, M, fullW, 8f)
        val metaCells = listOfNotNull(context.getString(R.string.report_period, from.format(dateFmt), to.format(dateFmt)),
            context.getString(R.string.report_generated, now.format(dtFmt)), digest?.let { context.getString(R.string.visit_digest, it.take(12)) })
        val metaTop = y; var metaBottom = y
        metaCells.forEachIndexed { i, cell -> y = metaTop; block(cell, meta, colX(4 * i), span(4), 0f); metaBottom = maxOf(metaBottom, y) }
        y = metaBottom + 8f
        visit?.appointment?.let{a->block(listOfNotNull(context.getString(net.plainnotes.app.ui.choiceRes(a.type.ifBlank{"OTHER"}))+" · "+Instant.ofEpochMilli(a.at_utc).atZone(zone).format(dtFmt),a.practitioner,a.location).joinToString(" · "),body,M,fullW,8f)}
        canvas!!.drawLine(M, y, W - M, y, line(ACCENT, 1.5f)); y += 16f; atTop = true

        if(on(VisitSection.REGIMEN)){
        section(context.getString(R.string.report_current_plan))
        d.medications.filter { it.active }.forEach { m ->
            val ester = d.profiles[m.id]?.ester
            text("• ${m.name} — ${listOfNotNull(m.molecule, ester?.takeIf { it != "E2" }, m.route).joinToString(" / ")} — ${fmt(m.dose_per_intake)} ${m.unit} · ${d.scheduleText[m.id] ?: ""}", body, 4f)
        }
        if(visit!=null){
            // Frozen regimen versions overlapping the range, described from their own snapshots.
            y += 4f; text(context.getString(R.string.visit_regimen_versions),sub)
            val versions=d.regimens.filter{it.effective_from_utc<until && (it.effective_until_utc?:Long.MAX_VALUE)>since}.sortedWith(compareBy({it.effective_from_utc},{it.id}))
            if(versions.isEmpty())text(context.getString(R.string.report_none))
            versions.forEach{v->text("• "+Instant.ofEpochMilli(v.effective_from_utc).atZone(zone).format(dtFmt)+" → "+(v.effective_until_utc?.let{Instant.ofEpochMilli(it).atZone(zone).format(dtFmt)}?:context.getString(R.string.epoch_ongoing))+
                (if(v.origin=="LEGACY_RULE")" · "+context.getString(R.string.epoch_reconstructed) else "")+"\n"+(visit.regimenLabels[v.id]?:visit.unknownName),body,4f)}
        }
        endSection()
        }
        if(visit!=null && facts!=null && on(VisitSection.FACTS)){
            section(context.getString(R.string.visit_facts_title))
            net.plainnotes.app.visit.factLines(context.resources,facts,visit.unknownName).forEach{text(it,body,4f)}
            endSection()
        }
        if(on(VisitSection.INTAKES)){
        section(context.getString(R.string.adherence_title))
        val inRange = d.records.filter { it.deleted_at_utc == null && (it.taken_utc ?: it.scheduled_utc ?: 0) in since until until }
        text(context.getString(R.string.report_recorded_contexts),sub)
        inRange.map{MedicationSnapshot.decode(it.config_snapshot,it.medication_id)}.distinct().forEach{snapshot->
            val description=snapshot?.let{listOfNotNull(it.name,it.molecule,it.profile?.ester,it.route,it.unit).joinToString(" · ")}
            text(description?.takeIf{it.isNotBlank()} ?: context.getString(R.string.history_context_unknown))
        }
        d.medications.filter { m -> inRange.any { it.medication_id == m.id } }.forEach { m ->
            val r = inRange.filter { it.medication_id == m.id && it.scheduled_utc != null }
            val (onTime, late, missed, skipped) = net.plainnotes.app.visit.scheduledCounts(inRange.filter { it.medication_id == m.id })
            if(r.any{it.unconfirmed})text(context.getString(R.string.unconfirmed_count,r.count{it.unconfirmed}))
            text("• ${m.name}: " + context.getString(R.string.report_adherence_line_v2, onTime, late, missed, skipped), body, 4f)
            // REQUIREMENTS §35a: records matching their period are not counted separately; no count is based on a missing scheduled time.
            val counts = net.plainnotes.app.visit.labelCounts(inRange.filter { it.medication_id == m.id && it.status in listOf("ON_TIME", "LATE") }.map { it.id }, labels)
            if (counts.any { it > 0 }) text(context.getString(R.string.visit_fact_labels, counts[0], counts[1], counts[2], counts[3]), body, 4f)
        }
        endSection()
        }
        if(on(VisitSection.LABS)){
        val labs = d.labs.filter { it.sampled_utc in since until until }.sortedBy { it.sampled_utc }
        section(context.getString(R.string.labs))
        if (labs.isEmpty()) text(context.getString(R.string.report_none)) else {
            // Columns on the grid: date 3, analyte 1, value 2 (right aligned), report range 2, time since dose 2.
            val table = Table(listOf(context.getString(R.string.report_col_date), context.getString(R.string.lab_analyte), context.getString(R.string.lab_value),
                context.getString(R.string.report_col_range), context.getString(R.string.report_col_since_dose)),
                listOf(span(3) + GUTTER, span(1) + GUTTER, span(2) + GUTTER, span(2) + GUTTER, span(2)), setOf(2))
            trace("row", table.header.joinToString("\n"))
            if (y + 2 * (lineHeight(body) + 4f) > CONTENT_BOTTOM) newPage()
            tableHeader(table)
            labs.forEach { l ->
                val saved=d.labContexts.filter{it.lab_id==l.id}.maxByOrNull{it.revision}
                val ingredient=when(l.analyte_code){"E2","T"->l.analyte_code;"P4","P4_IA","P4_MS"->"P4";else->null}
                val actual=saved?.let{LabContext.validate(it.context_json).getJSONArray("actual")}
                val last=if(ingredient==null || actual==null)"" else (0 until actual.length()).map{actual.getJSONObject(it)}.firstOrNull{it.optString("ingredient")==ingredient}?.let{r->val m=r.getLong("elapsed_ms")/60000;"${m/60} h ${m%60} min"}.orEmpty()
                val range = if (l.reference_lower != null || l.reference_upper != null) "${l.reference_lower?.let(::fmt) ?: "–"} – ${l.reference_upper?.let(::fmt) ?: "–"} ${l.reference_unit ?: ""}" else ""
                tableRow(table, listOf(Instant.ofEpochMilli(l.sampled_utc).atZone(zone).format(dtFmt), l.analyte_code, "${fmt(l.value)} ${l.unit}", range, last))
                y += 4f
                if(saved==null)text(context.getString(R.string.lab_context_not_saved))
                else net.plainnotes.app.ui.labContextLines(context,saved).forEach{text(it)}
            }
        }
        endSection()
        }
        // D-2 (REQUIREMENTS §67 item 5): no retired Legacy sublingual E2 curve or band in a new PDF, and no combined
        // total E2 for mixed routes; the report says why. Other-route-only E2 keeps its existing chart.
        val e2Plan = e2ReportPlan(net.plainnotes.app.conc.SublingualEstimatePolicy.pdfE2(conc, d.records))
        if (e2Plan.drawChart && y + 12f + 260f > CONTENT_BOTTOM) newPage()
        if (e2Plan.drawChart || e2Plan.messages.isNotEmpty()) section(context.getString(R.string.report_conc_title))
        e2Plan.messages.forEach { text(context.getString(it)) }
        if (e2Plan.drawChart) {
            text(context.getString(R.string.pk_disclaimer_short))
            // The chart itself is drawn by the unchanged drawChart, full text width as before; only its place on the page moved.
            if (y + 196f > CONTENT_BOTTOM) newPage()
            trace("chart", "${W - 2 * M}x180"); drawChart(canvas!!, conc!!, M, y, W - 2 * M, 180f, from.atStartOfDay(zone).toEpochSecond() / 3600.0, conc.nowH)
            y += 196f
        }
        if (e2Plan.drawChart || e2Plan.messages.isNotEmpty()) endSection()
        val scored = d.scores.filter { it.date >= from.toString() && it.date<=to.toString() }
        if (scored.isNotEmpty() && on(VisitSection.DAILY)) {
            section(context.getString(R.string.wellbeing))
            d.items.forEach { item -> scored.filter { it.item_id == item.id }.takeIf { it.isNotEmpty() }?.let { s ->
                text("• ${d.itemLabel(item)}: ${context.getString(R.string.wb_recorded_days,s.size)}", body, 4f) } }
            endSection()
        }
        val summary=WellbeingSummary(d,from,to)
        if(period!=null){
            if(on(VisitSection.SYMPTOMS)){section(context.getString(R.string.wb_symptoms))
            symptomLines(context,summary.symptoms).forEach{text(it)}; endSection()}
            if(on(VisitSection.REVIEWS)){section(context.getString(R.string.wb_reviews))
            summary.reviews.forEach{r->reviewLines(context,r).forEach{text(it)}}; endSection()}
            if(on(VisitSection.DAILY))d.items.forEach{item->val pts=summary.scores.filter{it.item_id==item.id}.sortedBy{it.date}
                if(pts.isNotEmpty()){
                    if(y+100>CONTENT_BOTTOM)newPage()
                    text(d.itemLabel(item)+" · "+context.getString(R.string.wb_recorded_days,pts.size))
                    val paint=Paint().apply{color=Color.DKGRAY;strokeWidth=1f;isAntiAlias=true}
                    val first=from.toEpochDay();val span=(to.toEpochDay()-first).coerceAtLeast(1)
                    var last:Pair<Float,Float>?=null
                    val plotX=contentX+16f; val plotW=contentW-16f
                    pts.forEach{point->val x=plotX+(LocalDate.parse(point.date).toEpochDay()-first).toFloat()/span*plotW;val py=y+64f-(point.value-1)*15f
                        last?.let{canvas!!.drawLine(it.first,it.second,x,py,paint)};canvas!!.drawCircle(x,py,2f,paint);last=x to py}
                    canvas!!.drawText("1",contentX,y+66f,meta);canvas!!.drawText("5",contentX,y+6f,meta);y+=84f
                }
            }
            if(on(VisitSection.DAILY))summary.notes.forEach{text(it.date+" · "+it.text)}
        }
        if(visit!=null && on(VisitSection.QUESTIONS)){
            section(context.getString(R.string.visit_questions))
            if(visit.questions.isEmpty())text(context.getString(R.string.report_none))
            visit.questions.sortedWith(compareBy({it.sort_order},{it.id})).forEachIndexed{i,q->
                text("${i+1}. "+q.text+" · "+context.getString(if(q.status=="ASKED")R.string.visit_question_asked else R.string.visit_question_open),body,4f)
                q.answer_note?.let{text(context.getString(R.string.visit_answer)+": "+it)}
            }
            endSection()
        }
        if(visit!=null && on(VisitSection.MILESTONES)){
            section(context.getString(R.string.visit_milestones))
            val rows=d.milestones.filter{LocalDate.parse(it.date) in from..to}.sortedBy{it.date}
            if(rows.isEmpty())text(context.getString(R.string.report_none))
            rows.forEach{m->text(listOfNotNull(m.date,context.getString(when(m.kind){"STARTED"->R.string.milestone_started;"ROUTE"->R.string.milestone_route;"SURGERY"->R.string.appt_surgery;"PAUSED"->R.string.milestone_paused;"STOPPED"->R.string.milestone_stopped;"RESUMED"->R.string.milestone_resumed;else->R.string.milestone_custom}),m.title,m.note).joinToString(" · "))}
            endSection()
        }
        if(on(VisitSection.PACKAGES)){
        section(context.getString(R.string.wb_package_info))
        if(visit!=null)text(context.getString(R.string.visit_packages_current))
        d.containers.filter{it.source_note!=null||it.batch!=null}.forEach{c->text(listOfNotNull(d.medications.firstOrNull{it.id==c.medication_id}?.name,"#"+c.id,c.source_note,c.batch).joinToString(" · "))}
        endSection()
        }
        if (started) sink.finishPage()
    }

    private fun fmt(v: Double, decimals: Int = 3) = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = decimals; isGroupingUsed = false }.format(v)

    private fun TextUtilsEllipsize(s: String, p: TextPaint, w: Float) = android.text.TextUtils.ellipsize(s, p, w, android.text.TextUtils.TruncateAt.END).toString()

    private fun drawChart(c: Canvas, r: ConcentrationResult, x: Float, y: Float, w: Float, h: Float, startH: Double, endH: Double) {
        val idx = r.timeH.indices.filter { r.timeH[it] in startH..endH }
        if (idx.size < 2) return
        val maxY = (idx.maxOf { r.bandOuter?.second?.get(it) ?: r.e2[it] }.coerceAtLeast(1.0)) * 1.1
        val axis = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f }
        val label = Paint().apply { color = Color.DKGRAY; textSize = 8f; isAntiAlias = true }
        c.drawLine(x, y + h, x + w, y + h, axis); c.drawLine(x, y, x, y + h, axis)
        listOf(0.0, maxY / 2, maxY).forEach { v -> c.drawText("${v.toInt()} pg/mL", x + 2, (y + h - v / maxY * h).toFloat() - 2, label) }
        fun px(t: Double) = (x + (t - startH) / (endH - startH) * w).toFloat()
        fun py(v: Double) = (y + h - v / maxY * h).toFloat()
        // New dynamic exports share the causal discontinuities; never rewrite saved PDF bytes.
        net.plainnotes.app.ui.ChartViewport.segments(r.timeH,r.calibrationBreaks).forEach { range ->
            val points=range.filter{r.timeH[it] in startH..endH}
            r.bandOuter?.let { (lo, hi) -> val band = Path(); var first = true
                points.forEach { i -> if (first) { band.moveTo(px(r.timeH[i]), py(hi[i])); first = false } else band.lineTo(px(r.timeH[i]), py(hi[i])) }
                points.reversed().forEach { i -> band.lineTo(px(r.timeH[i]), py(lo[i])) }; band.close()
                c.drawPath(band, Paint().apply { color = Color.argb(40, 0, 0x6A, 0x63); style = Paint.Style.FILL }) }
            val line = Path(); points.forEachIndexed { k, i -> if (k == 0) line.moveTo(px(r.timeH[i]), py(r.e2[i])) else line.lineTo(px(r.timeH[i]), py(r.e2[i])) }
            c.drawPath(line, Paint().apply { color = Color.rgb(0, 0x6A, 0x63); style = Paint.Style.STROKE; strokeWidth = 1.4f; isAntiAlias = true })
        }
        r.labs.filter { it.first in startH..endH }.forEach { (t, v) -> c.drawCircle(px(t), py(v), 2.8f, Paint().apply { color = Color.rgb(0x8E, 0x4D, 0x35); isAntiAlias = true }) }
        c.drawLine(px(endH), y, px(endH), y + h, Paint().apply { color = Color.GRAY; pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) })
    }
}

/** D-2: what the concentration section of a new PDF contains for each decision (pure, unit-tested). */
internal class E2ReportPlan(val drawChart: Boolean, val messages: List<Int>)
internal fun e2ReportPlan(decision: net.plainnotes.app.conc.SublingualEstimatePolicy.PdfE2) = when (decision) {
    net.plainnotes.app.conc.SublingualEstimatePolicy.PdfE2.DRAW -> E2ReportPlan(true, emptyList())
    net.plainnotes.app.conc.SublingualEstimatePolicy.PdfE2.SUBLINGUAL_UNAVAILABLE -> E2ReportPlan(false, listOf(R.string.lab_estimate_sl_unavailable))
    net.plainnotes.app.conc.SublingualEstimatePolicy.PdfE2.MIXED_UNAVAILABLE -> E2ReportPlan(false, listOf(R.string.lab_estimate_sl_unavailable, R.string.report_mixed_no_total_e2))
    net.plainnotes.app.conc.SublingualEstimatePolicy.PdfE2.NONE -> E2ReportPlan(false, emptyList())
}
