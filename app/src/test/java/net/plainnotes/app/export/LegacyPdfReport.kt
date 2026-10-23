package net.plainnotes.app.export

import androidx.core.graphics.withSave
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * PDF-01 (REQUIREMENTS §145) control: PdfReport exactly as it was before PDF-01 (base d2223c0a), frozen here so the tests
 * can show the new layout keeps every word. Mechanical changes only: pages go to a [PdfReport.Sink] instead of a
 * PdfDocument, "now" is passed in, and each text / row / footer / chart call reports what it writes through [trace].
 */
internal object LegacyPdfReport {
    private const val W = 595; private const val H = 842; private const val M = 48f

    /** [visit] limits the output to the chosen parts; parts that are not chosen never appear. */
    fun render(context: Context, d: ExportData, days: Int, conc: ConcentrationResult?, sink: PdfReport.Sink, period0:Pair<LocalDate,LocalDate>?=null,
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
        val title = TextPaint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK; isAntiAlias = true }
        val head = TextPaint().apply { textSize = 13f; isFakeBoldText = true; color = Color.rgb(0, 0x6A, 0x63); isAntiAlias = true }
        val body = TextPaint().apply { textSize = 10f; color = Color.BLACK; isAntiAlias = true }
        val small = TextPaint().apply { textSize = 8.5f; color = Color.DKGRAY; isAntiAlias = true }
        fun newPage() { if (started) sink.finishPage(); started = true; pageNo++; canvas = sink.startPage(pageNo); y = M
            val footer = if(period==null)context.getString(R.string.report_footer,pageNo)else context.getString(R.string.wb_summary_footer)+" · "+pageNo
            trace("footer", footer); canvas!!.drawText(footer, M, H - 24f, small) }
        fun text(s:String,p:TextPaint,gap:Float=4f) {
            trace("text", s)
            var remaining=s
            do {
                val layout=StaticLayout.Builder.obtain(remaining,0,remaining.length,p,(W-2*M).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                if(y+layout.getLineBottom(0)>H-48f)newPage()
                val fit=(0 until layout.lineCount).lastOrNull{y+layout.getLineBottom(it)<=H-48f}?:0
                val end=layout.getLineEnd(fit)
                canvas!!.withSave{translate(M,y);clipRect(0f,0f,W-2*M,layout.getLineBottom(fit).toFloat());layout.draw(this)}
                y+=layout.getLineBottom(fit)+gap
                remaining=remaining.substring(end)
                if(remaining.isNotEmpty())newPage()
            } while(remaining.isNotEmpty())
        }
        fun row(cols: List<String>, widths: List<Float>, p: TextPaint) {
            trace("row", cols.joinToString("\n"))
            if (y + 14f > H - 48f) newPage()
            var x = M; cols.forEachIndexed { i, c -> canvas!!.drawText(TextUtilsEllipsize(c, p, widths[i] - 6f), x, y + 10f, p); x += widths[i] }; y += 14f
        }
        newPage()
        text(context.getString(if(visit!=null)R.string.visit_pack_title else if(period==null)R.string.report_title else R.string.wb_summary), title, 2f)
        visit?.appointment?.let{a->text(listOfNotNull(context.getString(net.plainnotes.app.ui.choiceRes(a.type.ifBlank{"OTHER"}))+" · "+Instant.ofEpochMilli(a.at_utc).atZone(zone).format(dtFmt),a.practitioner,a.location).joinToString(" · "),body,2f)}
        text(context.getString(R.string.report_period, from.format(dateFmt), to.format(dateFmt)) + " · " + context.getString(R.string.report_generated, now.format(dtFmt)), small, if(digest==null)12f else 2f)
        digest?.let{text(context.getString(R.string.visit_digest,it.take(12)),small,12f)}

        if(on(VisitSection.REGIMEN)){
        text(context.getString(R.string.report_current_plan), head)
        d.medications.filter { it.active }.forEach { m ->
            val ester = d.profiles[m.id]?.ester
            text("• ${m.name} — ${listOfNotNull(m.molecule, ester?.takeIf { it != "E2" }, m.route).joinToString(" / ")} — ${fmt(m.dose_per_intake)} ${m.unit} · ${d.scheduleText[m.id] ?: ""}", body, 2f)
        }
        if(visit!=null){
            // Frozen regimen versions overlapping the range, described from their own snapshots.
            text(context.getString(R.string.visit_regimen_versions),head)
            val versions=d.regimens.filter{it.effective_from_utc<until && (it.effective_until_utc?:Long.MAX_VALUE)>since}.sortedWith(compareBy({it.effective_from_utc},{it.id}))
            if(versions.isEmpty())text(context.getString(R.string.report_none),body)
            versions.forEach{v->text("• "+Instant.ofEpochMilli(v.effective_from_utc).atZone(zone).format(dtFmt)+" → "+(v.effective_until_utc?.let{Instant.ofEpochMilli(it).atZone(zone).format(dtFmt)}?:context.getString(R.string.epoch_ongoing))+
                (if(v.origin=="LEGACY_RULE")" · "+context.getString(R.string.epoch_reconstructed) else "")+"\n"+(visit.regimenLabels[v.id]?:visit.unknownName),body,2f)}
        }
        y += 8f
        }
        if(visit!=null && facts!=null && on(VisitSection.FACTS)){
            text(context.getString(R.string.visit_facts_title),head)
            net.plainnotes.app.visit.factLines(context.resources,facts,visit.unknownName).forEach{text(it,body,2f)}
            y+=8f
        }
        if(on(VisitSection.INTAKES)){
        text(context.getString(R.string.adherence_title), head)
        val inRange = d.records.filter { it.deleted_at_utc == null && (it.taken_utc ?: it.scheduled_utc ?: 0) in since until until }
        text(context.getString(R.string.report_recorded_contexts),head)
        inRange.map{MedicationSnapshot.decode(it.config_snapshot,it.medication_id)}.distinct().forEach{snapshot->
            val description=snapshot?.let{listOfNotNull(it.name,it.molecule,it.profile?.ester,it.route,it.unit).joinToString(" · ")}
            text(description?.takeIf{it.isNotBlank()} ?: context.getString(R.string.history_context_unknown),body)
        }
        d.medications.filter { m -> inRange.any { it.medication_id == m.id } }.forEach { m ->
            val r = inRange.filter { it.medication_id == m.id && it.scheduled_utc != null }
            val (onTime, late, missed, skipped) = net.plainnotes.app.visit.scheduledCounts(inRange.filter { it.medication_id == m.id })
            if(r.any{it.unconfirmed})text(context.getString(R.string.unconfirmed_count,r.count{it.unconfirmed}),body)
            text("• ${m.name}: " + context.getString(R.string.report_adherence_line_v2, onTime, late, missed, skipped), body, 2f)
            // REQUIREMENTS §35a: records matching their period are not counted separately; no count is based on a missing scheduled time.
            val counts = net.plainnotes.app.visit.labelCounts(inRange.filter { it.medication_id == m.id && it.status in listOf("ON_TIME", "LATE") }.map { it.id }, labels)
            if (counts.any { it > 0 }) text(context.getString(R.string.visit_fact_labels, counts[0], counts[1], counts[2], counts[3]), body, 2f)
        }
        y += 8f
        }
        if(on(VisitSection.LABS)){
        val labs = d.labs.filter { it.sampled_utc in since until until }.sortedBy { it.sampled_utc }
        text(context.getString(R.string.labs), head)
        if (labs.isEmpty()) text(context.getString(R.string.report_none), body) else {
            val widths = listOf(120f, 90f, 90f, 120f, 79f)
            row(listOf(context.getString(R.string.report_col_date), context.getString(R.string.lab_analyte), context.getString(R.string.lab_value),
                context.getString(R.string.report_col_range), context.getString(R.string.report_col_since_dose)), widths, small.apply { isFakeBoldText = true })
            small.isFakeBoldText = false
            labs.forEach { l ->
                val saved=d.labContexts.filter{it.lab_id==l.id}.maxByOrNull{it.revision}
                val ingredient=when(l.analyte_code){"E2","T"->l.analyte_code;"P4","P4_IA","P4_MS"->"P4";else->null}
                val actual=saved?.let{LabContext.validate(it.context_json).getJSONArray("actual")}
                val last=if(ingredient==null || actual==null)"" else (0 until actual.length()).map{actual.getJSONObject(it)}.firstOrNull{it.optString("ingredient")==ingredient}?.let{r->val m=r.getLong("elapsed_ms")/60000;"${m/60} h ${m%60} min"}.orEmpty()
                val range = if (l.reference_lower != null || l.reference_upper != null) "${l.reference_lower?.let(::fmt) ?: "–"} – ${l.reference_upper?.let(::fmt) ?: "–"} ${l.reference_unit ?: ""}" else ""
                row(listOf(Instant.ofEpochMilli(l.sampled_utc).atZone(zone).format(dtFmt), l.analyte_code, "${fmt(l.value)} ${l.unit}", range, last), widths, body)
                if(saved==null)text(context.getString(R.string.lab_context_not_saved),small)
                else net.plainnotes.app.ui.labContextLines(context,saved).forEach{text(it,small)}
            }
        }
        y += 8f
        }
        // D-2 (REQUIREMENTS §67 item 5): no retired Legacy sublingual E2 curve or band in a new PDF, and no combined
        // total E2 for mixed routes; the report says why. Other-route-only E2 keeps its existing chart.
        val e2Plan = e2ReportPlan(net.plainnotes.app.conc.SublingualEstimatePolicy.pdfE2(conc, d.records))
        if (e2Plan.drawChart && y + 260f > H - 48f) newPage()
        if (e2Plan.drawChart || e2Plan.messages.isNotEmpty()) text(context.getString(R.string.report_conc_title), head)
        e2Plan.messages.forEach { text(context.getString(it), body) }
        if (e2Plan.drawChart) {
            text(context.getString(R.string.pk_disclaimer_short), small)
            trace("chart", "${W - 2 * M}x180"); drawChart(canvas!!, conc!!, M, y, W - 2 * M, 180f, from.atStartOfDay(zone).toEpochSecond() / 3600.0, conc.nowH)
            y += 196f
        }
        val scored = d.scores.filter { it.date >= from.toString() && it.date<=to.toString() }
        if (scored.isNotEmpty() && on(VisitSection.DAILY)) {
            text(context.getString(R.string.wellbeing), head)
            d.items.forEach { item -> scored.filter { it.item_id == item.id }.takeIf { it.isNotEmpty() }?.let { s ->
                text("• ${d.itemLabel(item)}: ${context.getString(R.string.wb_recorded_days,s.size)}", body, 2f) } }
        }
        val summary=WellbeingSummary(d,from,to)
        if(period!=null){
            if(on(VisitSection.SYMPTOMS)){text(context.getString(R.string.wb_symptoms),head)
            symptomLines(context,summary.symptoms).forEach{text(it,body)}}
            if(on(VisitSection.REVIEWS)){text(context.getString(R.string.wb_reviews),head)
            summary.reviews.forEach{r->reviewLines(context,r).forEach{text(it,body)}}}
            if(on(VisitSection.DAILY))d.items.forEach{item->val pts=summary.scores.filter{it.item_id==item.id}.sortedBy{it.date}
                if(pts.isNotEmpty()){
                    if(y+100>H-48)newPage()
                    text(d.itemLabel(item)+" · "+context.getString(R.string.wb_recorded_days,pts.size),body)
                    val paint=Paint().apply{color=Color.DKGRAY;strokeWidth=1f;isAntiAlias=true}
                    val first=from.toEpochDay();val span=(to.toEpochDay()-first).coerceAtLeast(1)
                    var last:Pair<Float,Float>?=null
                    pts.forEach{point->val x=M+(LocalDate.parse(point.date).toEpochDay()-first).toFloat()/span*(W-2*M);val py=y+65f-(point.value-1)*15f
                        last?.let{canvas!!.drawLine(it.first,it.second,x,py,paint)};canvas!!.drawCircle(x,py,2f,paint);last=x to py}
                    canvas!!.drawText("1",M,y+66f,small);canvas!!.drawText("5",M,y+6f,small);y+=82f
                }
            }
            if(on(VisitSection.DAILY))summary.notes.forEach{text(it.date+" · "+it.text,body)}
        }
        if(visit!=null && on(VisitSection.QUESTIONS)){
            text(context.getString(R.string.visit_questions),head)
            if(visit.questions.isEmpty())text(context.getString(R.string.report_none),body)
            visit.questions.sortedWith(compareBy({it.sort_order},{it.id})).forEachIndexed{i,q->
                text("${i+1}. "+q.text+" · "+context.getString(if(q.status=="ASKED")R.string.visit_question_asked else R.string.visit_question_open),body,2f)
                q.answer_note?.let{text(context.getString(R.string.visit_answer)+": "+it,small)}
            }
        }
        if(visit!=null && on(VisitSection.MILESTONES)){
            text(context.getString(R.string.visit_milestones),head)
            val rows=d.milestones.filter{LocalDate.parse(it.date) in from..to}.sortedBy{it.date}
            if(rows.isEmpty())text(context.getString(R.string.report_none),body)
            rows.forEach{m->text(listOfNotNull(m.date,context.getString(when(m.kind){"STARTED"->R.string.milestone_started;"ROUTE"->R.string.milestone_route;"SURGERY"->R.string.appt_surgery;"PAUSED"->R.string.milestone_paused;"STOPPED"->R.string.milestone_stopped;"RESUMED"->R.string.milestone_resumed;else->R.string.milestone_custom}),m.title,m.note).joinToString(" · "),body)}
        }
        if(on(VisitSection.PACKAGES)){
        text(context.getString(R.string.wb_package_info),head)
        if(visit!=null)text(context.getString(R.string.visit_packages_current),small)
        d.containers.filter{it.source_note!=null||it.batch!=null}.forEach{c->text(listOfNotNull(d.medications.firstOrNull{it.id==c.medication_id}?.name,"#"+c.id,c.source_note,c.batch).joinToString(" · "),body)}
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

