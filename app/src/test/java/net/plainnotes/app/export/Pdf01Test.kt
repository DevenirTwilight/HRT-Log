package net.plainnotes.app.export

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import net.plainnotes.app.R
import net.plainnotes.app.conc.ConcentrationCalculator
import net.plainnotes.app.conc.ConcentrationResult
import net.plainnotes.app.data.*
import net.plainnotes.app.visit.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest
import java.time.*
import java.util.Locale

/** One recorded canvas call, in page points. */
internal class Op(val page: Int, val kind: String, val text: String, val x: Float, val y: Float, val x2: Float, val y2: Float, val width: Float,
                  val size: Float, val style: Int, val fakeBold: Boolean, val color: Int, val align: Paint.Align, val stroke: Float, val chart: Boolean)

/**
 * PDF-01 test sink: every page is a bitmap canvas that records text, lines and shapes with their paint, and every
 * [trace] call with its page. [scale] > 1 keeps sharper bitmaps for the sample PNGs.
 */
internal class Recorder(private val scale: Float = 1f, private val keep: Boolean = false) : PdfReport.Sink {
    val ops = mutableListOf<Op>(); val traces = mutableListOf<Triple<Int, String, String>>(); val bitmaps = mutableListOf<Bitmap>()
    var page = 0; private var chart = false
    val trace: (String, String) -> Unit = { kind, s -> chart = kind == "chart"; traces += Triple(page, kind, s) }
    val pages get() = page
    private inner class Rec(b: Bitmap) : Canvas(b) {
        private fun pt(x: Float, y: Float) = floatArrayOf(x, y).also { @Suppress("DEPRECATION") matrix.mapPoints(it); it[0] /= scale; it[1] /= scale }
        private fun add(kind: String, s: String, x: Float, y: Float, x2: Float, y2: Float, p: Paint) {
            val a = pt(x, y); val b = pt(x2, y2)
            ops += Op(page, kind, s, a[0], a[1], b[0], b[1], if (kind == "text") p.measureText(s) else 0f, p.textSize, p.typeface?.style ?: Typeface.NORMAL, p.isFakeBoldText,
                p.color, p.textAlign, p.strokeWidth, chart)
        }
        private fun text(s: String, x: Float, y: Float, p: Paint) = add("text", s, x, y, x, y, p)
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) { text(text, x, y, paint); super.drawText(text, x, y, paint) }
        override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) { text(text.substring(start, end), x, y, paint); super.drawText(text, start, end, x, y, paint) }
        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) { text(text.subSequence(start, end).toString(), x, y, paint); super.drawText(text, start, end, x, y, paint) }
        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) { text(String(text, index, count), x, y, paint); super.drawText(text, index, count, x, y, paint) }
        override fun drawTextRun(text: CharArray, index: Int, count: Int, contextIndex: Int, contextCount: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
            text(String(text, index, count), x, y, paint); super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint) }
        override fun drawTextRun(text: CharSequence, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
            text(text.subSequence(start, end).toString(), x, y, paint); super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint) }
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) { add("line", "", startX, startY, stopX, stopY, paint); super.drawLine(startX, startY, stopX, stopY, paint) }
        override fun drawPath(path: Path, paint: Paint) { add("path", "", 0f, 0f, 0f, 0f, paint); super.drawPath(path, paint) }
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) { add("circle", "", cx, cy, cx, cy, paint); super.drawCircle(cx, cy, radius, paint) }
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) { add("rect", "", left, top, right, bottom, paint); super.drawRect(left, top, right, bottom, paint) }
    }
    override fun startPage(number: Int): Canvas {
        page = number; chart = false
        val b = Bitmap.createBitmap((595 * scale).toInt(), (842 * scale).toInt(), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        if (keep) bitmaps += b
        return Rec(b).apply { scale(scale, scale) }
    }
    override fun finishPage() {}
}

/** Synthetic fixtures for the three PDF kinds (no real data). */
internal object Pdf01Fixture {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = LocalDate.now(zone)
    val now: LocalDateTime = today.atTime(9, 30)
    fun at(d: LocalDate, h: Int = 8) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    fun med(id: Long, name: String = "Synthetic medication $id") = MedicationEntity(id, name, "E2", "ORAL", "MG", 2.0, 40.0, site_rotation = false, notifications_on = true, active = true, sort_order = id.toInt())
    private val profile = ProfileEntity(1, "EV", "oral")
    fun records(m: MedicationEntity, days: Int): List<RecordEntity> = (1..days).map { i ->
        val d = today.minusDays(i.toLong()); val status = when (i % 7) { 3 -> "LATE"; 5 -> "MISSED"; 6 -> "SKIPPED"; else -> "ON_TIME" }
        val taken = status == "ON_TIME" || status == "LATE"
        RecordEntity(m.id * 1000 + i, m.id, rule_version_id = 1, slot_key = "k${m.id}-$i", scheduled_utc = at(d), scheduled_zone = zone.id, planned_dose = 2.0,
            taken_utc = if (taken) at(d, 9) else null, taken_zone = if (taken) zone.id else null, actual_dose = if (taken) 2.0 else null, status = status, origin = "APP", revision = 1,
            config_snapshot = MedicationSnapshot.encode(m, if (m.id == 1L) profile else null))
    }
    fun labs(n: Int) = (1..n).map { i -> LabValueEntity(i.toLong(), if (i % 3 == 0) "T" else "E2", 100.0 + i, if (i % 3 == 0) "nmol/L" else "pg/mL", at(today.minusDays((i % 80).toLong() + 1), 7), zone.id,
        reference_lower = if (i % 2 == 0) 50.0 else null, reference_upper = if (i % 2 == 0) 300.0 else null, reference_unit = if (i % 2 == 0) "pg/mL" else null) }
    val items = listOf(CheckinItemEntity(1, "DAY_MOOD", null, true, 0), CheckinItemEntity(2, "DAY_ENERGY", null, true, 1))
    val appointment = AppointmentEntity(10, "ENDO", at(today.plusDays(3), 10), zone.id, location = "Synthetic clinic", practitioner = "Synthetic practitioner", remind_minutes_before = 60)

    /** [full] fills every part; otherwise only the plan and a few intakes. [meds] / [labCount] stretch the layout over pages. */
    fun data(full: Boolean, meds: Int = 2, labCount: Int = 6): ExportData {
        val ms = (1..meds).map { med(it.toLong()) }
        return ExportData(ms, mapOf(1L to profile), ms.take(2).flatMap { records(it, if (full) 40 else 5) }, if (full) labs(labCount) else emptyList(),
            if (full) items else emptyList(), if (full) (1..20).flatMap { i -> items.map { CheckinScoreEntity(today.minusDays(i.toLong()).toString(), it.id, 1 + i % 5) } } else emptyList(),
            if (full) listOf(DayNoteEntity(today.minusDays(2).toString(), "Synthetic day note"), DayNoteEntity(today.minusDays(9).toString(), "Synthetic second note")) else emptyList(),
            ms.associate { it.id to "Synthetic schedule ${it.id}" }, { "Synthetic item ${it.id}" },
            if (full) listOf(ContainerEntity(1, 1, 28.0, 0.0, 4.0, state = "IN_USE", source_note = "Synthetic pharmacy", batch = "Synthetic batch")) else emptyList(),
            if (full) listOf(SymptomCheckEntity(today.minusDays(4).toString(), "A", note = "Synthetic symptom note"), SymptomCheckEntity(today.minusDays(4).toString(), "B")) else emptyList(),
            if (full) listOf(StageReviewEntity(1, today.minusDays(6).toString(), """{"breast":"NOTICED","breast:note":"Synthetic effect note"}""", tolerance_note = "Synthetic tolerance",
                risk_note = "Synthetic risk", smoking = "NO", systolic = 120, diastolic = 80, weight_kg = 60.5, satisfaction = 4, satisfaction_note = "Synthetic satisfaction")) else emptyList(),
            emptyList(),
            if (full) listOf(RegimenVersionEntity(1, 1, at(today.minusDays(120)), at(today.minusDays(20)), zone.id, "{}", "a".repeat(64), "APP"),
                RegimenVersionEntity(2, 1, at(today.minusDays(20)), null, zone.id, "{}", "b".repeat(64), "LEGACY_RULE")) else emptyList(),
            if (full) listOf(MilestoneEntity(1, today.minusDays(30).toString(), "STARTED", "Synthetic milestone", "Synthetic milestone note")) else emptyList(),
            listOf(appointment))
    }
    val questions = listOf(VisitQuestionEntity(1, 10, 0, "Synthetic question one", "ASKED", "Synthetic answer"), VisitQuestionEntity(2, 10, 1, "Synthetic question two"))
    fun spec(sections: Set<VisitSection>, q: List<VisitQuestionEntity> = questions) =
        VisitPackSpec(appointment, today.minusDays(60), today, sections, q, mapOf(1L to "Synthetic regimen label"), "Synthetic unknown")
    fun conc(d: ExportData): ConcentrationResult = ConcentrationCalculator.compute(d.medications.take(1), d.profiles, d.records.filter { it.medication_id == 1L }, emptyList(), emptyList(), 70.0,
        today.atTime(9, 0).atZone(zone).toInstant())

    fun localized(tag: String): Context { val app = ApplicationProvider.getApplicationContext<Context>()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }) }
    val LOCALES = listOf("zh-CN", "en", "fr", "zh-Hant-TW")
}

/** The cases: report, visit pack and wellbeing summary, each with every part and with a partial set. */
internal class Pdf01Case(val name: String, val d: ExportData, val conc: ConcentrationResult?, val period: Pair<LocalDate, LocalDate>? = null,
                         val visit: VisitPackSpec? = null, val facts: VisitFacts? = null, val digest: String? = null) {
    fun renderNew(c: Context, r: Recorder) = PdfReport.render(c, d, 90, conc, r, period, visit, facts, digest, emptyMap(), Pdf01Fixture.now, r.trace)
    fun renderOld(c: Context, r: Recorder) = LegacyPdfReport.render(c, d, 90, conc, r, period, visit, facts, digest, emptyMap(), Pdf01Fixture.now, r.trace)
    companion object {
        fun all(): List<Pdf01Case> = with(Pdf01Fixture) {
            val full = data(true); val part = data(false); val conc = conc(full)
            fun pack(name: String, d: ExportData, sections: Set<VisitSection>): Pdf01Case { val s = spec(sections); val f = VisitFacts.build(d, appointment, s.from, s.to, zone)
                return Pdf01Case(name, d, conc, visit = s, facts = f, digest = VisitDigest.compute(d, s, f, "en", zone)) }
            listOf(Pdf01Case("report_full", full, conc), Pdf01Case("report_partial", part, null),
                pack("visit_full", full, VisitSection.entries.toSet()), pack("visit_partial", part, setOf(VisitSection.FACTS, VisitSection.QUESTIONS)),
                Pdf01Case("summary_full", full, null, period = today.minusDays(29) to today), Pdf01Case("summary_partial", part, null, period = today.minusDays(29) to today))
        }
    }
}

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35], application = android.app.Application::class)
class Pdf01Test {
    private val P = PdfReport
    private fun atoms(r: Recorder) = r.traces.filter { it.second == "text" || it.second == "row" }
        .flatMap { it.third.split(" · ", "\n") }.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
    private fun footers(r: Recorder) = r.traces.filter { it.second == "footer" }.map { it.third }
    private fun footerFor(c: Context, case: Pdf01Case, n: Int) = if (case.period == null && case.visit == null) c.getString(R.string.report_footer, n) else c.getString(R.string.wb_summary_footer) + " · " + n

    /** §3: the words are the words of the old layout, in four languages, for every case; the only new text is the section numbers. */
    @Test fun textIsIdenticalToTheOldLayout() {
        for (tag in Pdf01Fixture.LOCALES) { val c = Pdf01Fixture.localized(tag)
            for (case in Pdf01Case.all()) {
                val old = Recorder().also { case.renderOld(c, it) }; val new = Recorder().also { case.renderNew(c, it) }
                val label = "$tag/${case.name}"
                assertTrue(label, atoms(old).isNotEmpty())
                assertEquals(label, atoms(old), atoms(new))
                assertEquals(label, (1..old.pages).map { footerFor(c, case, it) }, footers(old))
                assertEquals(label, (1..new.pages).map { footerFor(c, case, it) }, footers(new))
                assertEquals(label, old.traces.count { it.second == "chart" }, new.traces.count { it.second == "chart" })
                // Every drawn piece of text is part of something the old layout wrote too (or a section number / chart label).
                val sources = new.traces.filter { it.second != "number" }.map { it.third }
                val numbers = new.traces.filter { it.second == "number" }.map { it.third }.toSet()
                new.ops.filter { it.kind == "text" && !it.chart }.map { it.text.trim().removeSuffix("…").trim() }.filter { it.isNotEmpty() }.forEach { s ->
                    assertTrue("$label: '$s' is new text", s in numbers || s == "1" || s == "5" || sources.any { src -> s in src || s.replace("\n", "") in src.replace("\n", "") })
                }
            }
        }
    }

    /** The visit-pack check code does not depend on the layout: same inputs, same digest, and the PDF shows the same 12 characters. */
    @Test fun digestIsUnchanged() {
        val case = Pdf01Case.all().first { it.name == "visit_full" }
        val again = with(Pdf01Fixture) { VisitDigest.compute(case.d, case.visit!!, case.facts!!, "en", zone) }
        assertEquals(case.digest, again); assertEquals(64, again.length)
        val c = Pdf01Fixture.localized("en")
        val old = Recorder().also { case.renderOld(c, it) }; val new = Recorder().also { case.renderNew(c, it) }
        val shown = c.getString(R.string.visit_digest, again.take(12))
        assertEquals(1, old.traces.count { it.third.contains(shown) }); assertEquals(1, new.traces.count { it.third == shown })
        // The template version that goes into the digest and the code computing it are the base.
        assertEquals("56d5e5a098205b117f2de2a336221675ac00463ed51b1263639078cba6c5f2a3", sha(File("src/main/java/net/plainnotes/app/visit/VisitPack.kt")))
    }

    /** PK display regression: the concentration chart is the same drawChart call with the same size; only its page position moved. */
    @Test fun chartIsDrawnTheSameWay() {
        val case = Pdf01Case.all().first { it.name == "report_full" }; val c = Pdf01Fixture.localized("en")
        val old = Recorder().also { case.renderOld(c, it) }; val new = Recorder().also { case.renderNew(c, it) }
        val a = old.ops.filter { it.chart }; val b = new.ops.filter { it.chart }
        assertTrue(a.isNotEmpty()); assertEquals(a.size, b.size)
        val dy = b.first().y - a.first().y
        a.zip(b).forEach { (o, n) -> assertEquals(o.kind, n.kind); assertEquals(o.text, n.text); assertEquals(o.color, n.color); assertEquals(o.stroke, n.stroke, 0f); assertEquals(o.size, n.size, 0f)
            assertEquals(o.x, n.x, 0.01f); if (o.kind != "path") assertEquals(o.y + dy, n.y, 0.01f) }
    }

    /** §2 layout rules, checked on every case and language. */
    @Test fun layoutFollowsTheGrid() {
        for (tag in Pdf01Fixture.LOCALES) { val c = Pdf01Fixture.localized(tag)
            for (case in Pdf01Case.all() + Pdf01Case("report_long", Pdf01Fixture.data(true, meds = 30, labCount = 70), null)) {
                val r = Recorder().also { case.renderNew(c, it) }; checkLayout("$tag/${case.name}", c, r, case)
            }
        }
    }

    /** No section title is left at a page foot: title plus two lines always share a page, whatever the length before it. */
    @Test fun noOrphanSectionTitles() {
        val c = Pdf01Fixture.localized("en")
        for (meds in 0..48) {
            val r = Recorder(); PdfReport.render(c, Pdf01Fixture.data(true, meds = maxOf(meds, 1), labCount = 3), 90, null, r, now = Pdf01Fixture.now, trace = r.trace)
            orphans("meds=$meds", r)
        }
    }

    private fun orphans(label: String, r: Recorder) {
        val content = r.ops.filter { it.kind == "text" && !it.chart && it.y < P.CONTENT_BOTTOM }
        content.forEachIndexed { i, op -> if (op.size == 13f && op.color == Color.BLACK && op.style == Typeface.BOLD) {
            val next = content.drop(i + 1).firstOrNull { it.size != 13f || it.color == Color.BLACK && it.style != Typeface.BOLD }
            // A part with nothing under it (e.g. no package details) may end the document; otherwise its first line follows on the same page.
            assertTrue("$label: '${op.text}' is the last thing on page ${op.page}", next == null || next.page == op.page)
            assertTrue("$label: no room for two lines under '${op.text}'", op.y + 4 + 2 * 16 <= P.CONTENT_BOTTOM)
        } }
    }

    private fun checkLayout(label: String, c: Context, r: Recorder, case: Pdf01Case) {
        val ops = r.ops.filter { !it.chart }
        val text = ops.filter { it.kind == "text" && it.text.isNotBlank() }
        // Sizes: title 26, section 13, body 10, meta / footer / table head 8. Body never below 10.
        assertEquals(label, emptySet<Float>(), text.map { it.size }.toSet() - setOf(26f, 13f, 10f, 8f))
        val headerRule = ops.single { it.kind == "line" && it.color == P.ACCENT }
        assertEquals(label, 1, headerRule.page); assertEquals(label, 1.5f, headerRule.stroke, 0f)
        text.filter { it.size == 8f }.forEach { op ->
            val footer = op.y > P.CONTENT_BOTTOM; val meta = op.page == 1 && op.y < headerRule.y && op.color == P.GREY
            val tableHead = op.style == Typeface.BOLD && op.color == Color.BLACK; val plotAxis = op.text == "1" || op.text == "5"
            assertTrue("$label: 8 pt '${op.text}' outside footer/meta/table head", footer || meta || tableHead || plotAxis)
        }
        // Two real weights; no fake bold.
        assertTrue(label, text.none { it.fakeBold }); assertEquals(label, emptySet<Int>(), text.map { it.style }.toSet() - setOf(Typeface.NORMAL, Typeface.BOLD))
        // Black, grey and the one accent only (the chart keeps its own colours, unchanged).
        assertEquals(label, emptySet<Int>(), ops.map { it.color }.toSet() - setOf(Color.BLACK, P.GREY, P.ACCENT, P.RULE_LIGHT))
        text.filter { it.color == P.ACCENT }.forEach { assertTrue("$label: accent '${it.text}'", Regex("\\d\\d").matches(it.text) && kotlin.math.abs(it.x - P.colX(0)) < 0.5f) }
        // Nothing centred: left aligned paints; every text starts on a grid column or table column, or ends on a right edge.
        assertTrue(label, text.all { it.align == Paint.Align.LEFT })
        val widths = listOf(P.span(3) + P.GUTTER, P.span(1) + P.GUTTER, P.span(2) + P.GUTTER, P.span(2) + P.GUTTER, P.span(2))
        val lefts = (0 until 12).map { P.colX(it) } + widths.runningFold(P.colX(2)) { a, w -> a + w } + (P.colX(2) + 16f)
        val rights = listOf(595 - 48f) + widths.runningFold(P.colX(2)) { a, w -> a + w }.zipWithNext { a, b -> b - 6f }
        text.forEach { op -> assertTrue("$label: '${op.text}' at x=${op.x} is off the grid", lefts.any { kotlin.math.abs(it - op.x) < 0.5f } || rights.any { kotlin.math.abs(it - (op.x + op.width)) < 0.5f } ||
            op.text == "1" || op.text == "5") }
        // Section numbers 01, 02 … in order.
        val numbers = r.traces.filter { it.second == "number" }.map { it.third }
        assertEquals(label, (1..numbers.size).map { "%02d".format(it) }, numbers)
        assertEquals(label, numbers, text.filter { it.color == P.ACCENT }.map { it.text })
        // Lines: the header rule, one footer rule per page, table rules, plot strokes — and nothing between sections.
        val lines = ops.filter { it.kind == "line" }
        val footerRules = lines.filter { it.color == P.RULE_LIGHT && it.y == 842 - P.BOTTOM + 16f }
        assertEquals(label, (1..r.pages).toList(), footerRules.map { it.page })
        val tableRules = lines.filter { it !in footerRules && (it.color == Color.BLACK && it.stroke == 0.5f || it.color == P.RULE_LIGHT && it.stroke == 0.25f) &&
            kotlin.math.abs(it.x - P.colX(2)) < 0.5f && kotlin.math.abs(it.x2 - (P.colX(2) + P.span(10))) < 0.5f }
        val plot = lines.filter { it.color == P.GREY && it.stroke == 1f }
        assertEquals("$label: a line that is not header / footer / table / plot", emptyList<Op>(), lines - setOf(headerRule) - footerRules.toSet() - tableRules.toSet() - plot.toSet())
        val labRows = r.traces.count { it.second == "row" } - (if (r.traces.any { it.second == "row" }) 1 else 0)
        val headerDraws = text.count { it.size == 8f && it.style == Typeface.BOLD && it.text == c.getString(R.string.report_col_date) }
        assertEquals(label, labRows + headerDraws, tableRules.size)
        // The table header repeats on every page that carries rows.
        val rowPages = r.traces.filter { it.second == "row" }.map { it.first }.toSet()
        val heads = text.filter { it.size == 8f && it.style == Typeface.BOLD && it.text == c.getString(R.string.report_col_date) }
        assertTrue("$label: rows on ${rowPages - heads.map { it.page }.toSet()} without a header", heads.map { it.page }.containsAll(rowPages))
        assertEquals("$label: one header per page", heads.size, heads.map { it.page }.toSet().size)
        // On each page the header comes before every row of the table.
        val dateCells = r.traces.filter { it.second == "row" }.drop(1).map { it.third.substringBefore("\n") }.toSet()
        heads.forEach { h -> assertTrue(label, text.none { it.page == h.page && it.size == 10f && it.text in dateCells && it.y < h.y }) }
        if (case.name == "report_long") assertTrue("$label: the long table should span pages", rowPages.size > 1)
        orphans(label, r)
        // No fills: no rectangles at all outside the chart.
        assertTrue(label, ops.none { it.kind == "rect" })
    }

    /** Before / after PNG samples (rendered by the same canvas calls; Robolectric has no PdfDocument). Off unless PDF01_SAMPLES is set. */
    @Test fun samples() {
        val out = System.getenv("PDF01_SAMPLES"); org.junit.Assume.assumeTrue(!out.isNullOrBlank())
        val dir = File(out!!).apply { mkdirs() }
        for (tag in Pdf01Fixture.LOCALES) { val c = Pdf01Fixture.localized(tag)
            for (case in Pdf01Case.all().filter { it.name.endsWith("full") }) {
                for ((which, render) in listOf("before" to case::renderOld, "after" to case::renderNew)) {
                    val r = Recorder(scale = 2f, keep = true); render(c, r)
                    r.bitmaps.forEachIndexed { i, b -> File(dir, "${case.name}_${tag}_${which}_p${i + 1}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
            }
        }
    }

    /** PDF-01 rules in the source: real bold only, no centred text, no fills, the 12-column grid constants. */
    @Test fun sourceGuard() {
        val src = File("src/main/java/net/plainnotes/app/export/Exports.kt").readText()
        val pdf = src.substring(src.indexOf("object PdfReport"), src.indexOf("private fun drawChart"))
        listOf("isFakeBoldText", "ALIGN_CENTER", "Align.CENTER", "Style.FILL", "drawRect").forEach { assertFalse(it, it in pdf) }
        assertEquals(12, P.COLUMNS); assertEquals(12f, P.GUTTER, 0f); assertEquals(56f, P.TOP, 0f); assertEquals(64f, P.BOTTOM, 0f)
        assertEquals(595 - 2 * 48f, P.span(12), 0.01f)
    }

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
}
