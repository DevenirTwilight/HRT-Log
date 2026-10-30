package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.export.CsvExport
import net.plainnotes.app.export.ExportData
import net.plainnotes.app.export.reviewLines
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream

/** UI-09 (REQUIREMENTS §146) source guards: what stays centred, and the 4 dp grid on the pages this round touched. */
class Ui09SourceGuardTest {
    private val ui = File("src/main/java/net/plainnotes/app/ui")
    private val centring = Regex("""TextAlign\.Center|CenterHorizontally|Arrangement\.Center\b""")

    /** Every centred text left in the app is one of the listed exceptions (report 75 §1). */
    @Test fun onlyTheListedExceptionsStayCentred() {
        val found = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().filter { centring.containsMatchIn(it) }.map { f.name } }.groupingBy { it }.eachCount()
        val allowed = mapOf(
            "CalendarScreen.kt" to 6,     // (lines) month title between arrows, weekday header over day columns, day / week / last-7-days cells
            "MedicalRecordsScreen.kt" to 1, // the date chip of the next visit (month / day / weekday inside a chip)
            "VisitsScreen.kt" to 1,       // UI-14: the date tile of a visit card (month / day / weekday inside a tile), the same exception as the medical chip above
            "WellbeingDaily.kt" to 1,     // the date between the previous / next day arrows
            "AdaptiveControls.kt" to 1,   // a segmented control's label
            "LockScreen.kt" to 1,         // the PIN pad screen, centred around the keypad
        )
        assertEquals(allowed, found)
        assertFalse("EmptyState is start-aligned", File(ui, "Components.kt").readText().substringAfter("fun EmptyState(").substringBefore("\n}\n").let { centring.containsMatchIn(it) })
    }

    /** Spacing literals (padding, spacedBy, PaddingValues, Spacer) on the touched pages are 4 dp steps. */
    @Test fun spacingOnTheTouchedPagesIsOnTheFourDpGrid() {
        val files = listOf("TodayCard.kt", "CalendarScreen.kt", "UnconfirmedCard.kt", "StockScreen.kt", "LongitudinalScreen.kt", "MedicalRecordsScreen.kt",
            "VisitsScreen.kt", "WellbeingDaily.kt", "WellbeingScreen.kt", "WellbeingHub.kt", "HistoryScreen.kt", "StageReviewScreen.kt")
        val call = Regex("""(padding|spacedBy|PaddingValues|Spacer\(Modifier\.(?:height|width))\(""")
        val off = mutableListOf<String>()
        for (name in files) {
            val s = File(ui, name).readText()
            call.findAll(s).forEach { m ->
                var j = m.range.last + 1; var depth = 1
                while (depth > 0 && j < s.length) { if (s[j] == '(') depth++ else if (s[j] == ')') depth--; j++ }
                Regex("""(?<![\w.])(\d+)\.dp""").findAll(s.substring(m.range.last + 1, j)).forEach { d ->
                    if (d.groupValues[1].toInt() % 4 != 0) off += "$name:${s.substring(0, m.range.first).count { it == '\n' } + 1}: ${d.value}" }
            }
        }
        assertTrue(off.joinToString("\n"), off.isEmpty())
        assertEquals(16f, Grid.margin.value); assertEquals(4f, Grid.baseline.value); assertEquals(4, Grid.COLUMNS)
        assertTrue(Grid.steps.all { it.value.toInt() % 4 == 0 })
    }

    /** The visit detail's sections below the visit card are numbered, card-less sections with thin rules. */
    @Test fun visitDetailUsesNumberedSections() {
        val s = File(ui, "VisitsScreen.kt").readText().substringAfter("private fun VisitDetail(").substringBefore("private fun QuestionDialog(")
        listOf("visit-section:labs", "visit-section:questions", "visit-section:pack").forEach { assertTrue(it, s.contains("NumberedSection(") && s.contains(it)) }
        assertFalse(s.contains("SectionCard(stringResource(R.string.visit_questions))")); assertFalse(s.contains("SectionCard(stringResource(R.string.visit_pack_title))"))
    }

    /**
     * The numbering is display only. UI-09 does not touch the export code; Exports.kt is not pinned here because PDF-01
     * (REQUIREMENTS §145) changes its PDF layout under its own approval — that the CSV and review lines carry no numbering is
     * checked on the output in Ui09ScreenTest, and the shared review text below stays the base.
     */
    @Test fun exportCodeIsTheBase() {
        fun sha(f: File) = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        assertFalse(File("src/main/java/net/plainnotes/app/export/Exports.kt").readText().contains("NumberedHeading"))
        assertEquals("df0d246ba308f43dd869c131b5d781dfba4125ec940df9fd55dcc15ddb1c497b", sha(File("src/main/java/net/plainnotes/app/export/WellbeingSummary.kt")))
    }
}

/** UI-09 on screen: start-aligned empty state, 16 dp page margins, thin rules, numbers that are shown but not read. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h2400dp")
class Ui09ScreenTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val density get() = ui.activity.resources.displayMetrics.density
    private fun left(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot.left / density

    @Test fun emptyStateIsStartAligned() {
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { EmptyState(Icons.Outlined.History, "Synthetic title", "Synthetic body that wraps over more than one line on a phone screen to show the alignment.") } }
        // The column's content starts 16 dp in (the grid margin); title and body start there too, not centred.
        val box = left(ui.onNodeWithTag("empty-state"))
        assertEquals(16f, box, 0.6f)
        assertEquals(box, left(ui.onNodeWithText("Synthetic title")), 0.6f)
        assertEquals(box, left(ui.onNodeWithTag("empty-state-body")), 0.6f)
    }

    @Test fun historyDayHasARuleAndSixteenDpMargins() {
        val zone = java.time.ZoneId.systemDefault(); val day = LocalDate.now().minusDays(1)
        val med = MedicationEntity(1, "Synthetic", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
        val r = RecordEntity(1, 1, scheduled_utc = day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), scheduled_zone = zone.id, planned_dose = 2.0,
            taken_utc = day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), taken_zone = zone.id, actual_dose = 2.0, status = "ON_TIME", origin = "APP", revision = 1,
            config_snapshot = MedicationSnapshot.encode(med, null))
        ui.setContent { NotesTheme(ThemeMode.LIGHT) { HistoryScreen(NotesState(listOf(med), loading = false), listOf(r), {}, {}, PaddingValues()) } }
        ui.onNodeWithTag("history-records").performScrollToNode(hasTestTag("history-day-rule:$day"))
        ui.onNodeWithTag("history-day-rule:$day").assertExists()
        assertEquals(16f, left(ui.onNodeWithTag("history-item:1")), 0.6f)
    }

    @Test fun numberedSectionDrawsTheNumberAndUsesOutlineInHighContrast() {
        var c: androidx.compose.material3.ColorScheme? = null
        ui.setContent { NotesTheme(ThemeMode.LIGHT, contrast = Contrast.HIGH) { c = MaterialTheme.colorScheme; Column {
            NumberedSection(1, "First") { androidx.compose.material3.Text("a") }
            NumberedSection(2, "Second") { androidx.compose.material3.Text("b") } } } }
        ui.onAllNodesWithTag("section-rule", useUnmergedTree = true).assertCountEquals(1)
        assertEquals(c!!.outline, ui.onNodeWithTag("section-rule", useUnmergedTree = true).fetchSemanticsNode().config[RuleColorKey])
        ui.onNodeWithText("Second").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        ui.onAllNodes(hasText("02"), useUnmergedTree = true).assertCountEquals(0)
        // Drawn left of its title, on the same line.
        val n = ui.onAllNodesWithTag("section-number", useUnmergedTree = true).fetchSemanticsNodes().first { it.config[SectionNumberKey] == 2 }.boundsInRoot
        val t = ui.onNodeWithText("Second").fetchSemanticsNode().boundsInRoot
        assertTrue(n.right <= t.left && n.bottom > t.top)
    }

    @Test fun exportsCarryNoNumbering() {
        val review = StageReviewEntity(date = "2026-02-10", effects_json = "{\"FAT\":\"NOTICED\"}", tolerance_note = "Synthetic tolerance", risk_note = "Synthetic risk", satisfaction = 4)
        val lines = reviewLines(ui.activity, review)
        assertTrue(lines.joinToString("\n"), lines.none { Regex("""^0[1-4]\b""").containsMatchIn(it) })
        val d = ExportData(emptyList(), emptyMap(), emptyList(), emptyList(), listOf(CheckinItemEntity(1, "DAY_MOOD", null, true, 0)),
            listOf(CheckinScoreEntity("2026-02-10", 1, 4)), emptyList(), emptyMap(), { "Synthetic mood" }, reviews = listOf(review))
        val out = ByteArrayOutputStream(); CsvExport.write(d, out)
        val text = buildString { ZipInputStream(out.toByteArray().inputStream()).use { z -> while (z.nextEntry != null) append(z.readBytes().decodeToString()) } }
        listOf("01 ", "02 ", "03 ", "04 ").forEach { assertFalse(it, text.lines().any { l -> l.startsWith(it) }) }
    }
}

/** UI-09 D on the stage review dialog (default window: a very tall test window keeps the dialog relaying out). */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui09StageReviewTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun stageReviewPartsAreNumberedRuledAndNotReadAloud() {
        var c: androidx.compose.material3.ColorScheme? = null
        val existing = StageReviewEntity(id = 1, date = LocalDate.now().toString(), effects_json = "{}")
        ui.setContent { MaterialTheme { c = MaterialTheme.colorScheme; StageReviewDialog(existing, listOf(existing), emptyList(), emptyList(), {}, {}) } }
        // Numbers 1–4 are drawn ("01"–"04") but their nodes carry no text or description, so TalkBack never reads "01".
        val numbers = ui.onAllNodesWithTag("section-number", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(listOf(1, 2, 3, 4), numbers.map { it.config[SectionNumberKey] })
        numbers.forEach { n -> assertNull(n.config.getOrNull(SemanticsProperties.Text)); assertNull(n.config.getOrNull(SemanticsProperties.ContentDescription)) }
        listOf("01", "02", "03", "04").forEach { ui.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0) }
        (1..4).forEach { ui.onNodeWithTag("review-part:$it", useUnmergedTree = true).assertExists() }
        ui.onNodeWithText(ui.activity.getString(R.string.wb_tolerance)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        (2..4).forEach { assertEquals(c!!.outlineVariant, ui.onNodeWithTag("review-rule:$it", useUnmergedTree = true).fetchSemanticsNode().config[RuleColorKey]) }
    }
}
