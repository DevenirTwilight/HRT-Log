package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

/** Synthetic data for the UI-06 card tests: one medication, placeholder name, records relative to [now]. */
internal object Ui06Fixture {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = LocalDate.of(2026, 3, 10)
    val now: Instant = today.atTime(18, 0).atZone(zone).toInstant()
    val med = MedicationEntity(1, "药物 A", "E2", "ORAL", "MG", 2.0, 30.0, null, 15, 120, false, null, true, true, 0)
    fun auto(id: Long, at: Instant, status: String = "MISSED", origin: String = "AUTO_MISSED", deleted: Long? = null) = RecordEntity(id, 1,
        scheduled_utc = at.toEpochMilli(), scheduled_zone = zone.id, planned_dose = 2.0, taken_utc = if (status == "MISSED") null else at.toEpochMilli(),
        status = status, origin = origin, revision = 1, deleted_at_utc = deleted, config_snapshot = MedicationSnapshot.encode(med, null))
}

/** UI-06 C: the home "no entry yet" card on the real CalendarScreen. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Ui06ConfirmCardTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)
    private val f = Ui06Fixture
    private var backfill: RecordEntity? = null; private var confirmed: RecordEntity? = null; private var more: List<RecordEntity>? = null

    private val texts = mutableMapOf<String, String>()
    private fun show(records: List<RecordEntity>) {
        val state = NotesState(listOf(f.med), emptyList(), emptyList(), loading = false, calendarStart = f.today)
        ui.setContent { CompositionLocalProvider(LocalTodayClock provides { f.now }) { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            texts["t2"] = formatTime(f.now.minusSeconds(2 * 3600)); texts["t26"] = formatTime(f.now.minusSeconds(26 * 3600)); texts["dose"] = formatDose(2.0, "MG")
            CalendarScreen(state, f.today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = records),
                onBackfill = { backfill = it }, onConfirmMissed = { confirmed = it }, onUnconfirmedMore = { more = it })
        } } } }
        ui.waitForIdle()
    }

    @Test fun windowIsTheLast48HoursInclusive() {
        val w = UNCONFIRMED_WINDOW
        assertEquals(Duration.ofHours(48), w)
        val rs = listOf(f.auto(1, f.now.minus(w)), f.auto(2, f.now.minus(w).minusMillis(1)), f.auto(3, f.now), f.auto(4, f.now.plusMillis(1)),
            f.auto(5, f.now.minusSeconds(3600), deleted = 1L), f.auto(6, f.now.minusSeconds(3600), origin = "APP"),
            f.auto(7, f.now.minusSeconds(3600), status = "ON_TIME", origin = "APP"), f.auto(8, f.now.minusSeconds(7200)))
        assertEquals(listOf(3L, 8L, 1L), recentUnconfirmed(rs, f.now).map { it.id })
    }

    @Test fun atMostTwoThenMoreLeadsToTheHistory() {
        val rs = listOf(f.auto(1, f.now.minusSeconds(40 * 3600)), f.auto(2, f.now.minusSeconds(2 * 3600)), f.auto(3, f.now.minusSeconds(26 * 3600)))
        show(rs)
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("unconfirmed-card"))
        ui.onNodeWithTag("unconfirmed-row:2").assertExists(); ui.onNodeWithTag("unconfirmed-row:3").assertExists()
        ui.onNodeWithTag("unconfirmed-row:1").assertDoesNotExist()
        ui.onNodeWithTag("unconfirmed-title:2").assertTextEquals(s(R.string.confirm_title_today, texts.getValue("t2")))
        ui.onNodeWithTag("unconfirmed-title:3").assertTextEquals(s(R.string.confirm_title_yesterday, texts.getValue("t26")))
        ui.onNodeWithTag("unconfirmed-body:2").assertTextEquals(s(R.string.confirm_body_with, "药物 A · " + texts.getValue("dose")))
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("unconfirmed-more"))
        ui.onNodeWithTag("unconfirmed-more").assertTextEquals(s(R.string.confirm_more, 1) + " → " + s(R.string.nav_history)).performClick()
        assertEquals(listOf(2L, 3L, 1L), more?.map { it.id })
    }

    @Test fun buttonsUseTheExistingPathsAndWriteNothingThemselves() {
        val r = f.auto(4, f.now.minusSeconds(3 * 3600)); show(listOf(r))
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("unconfirmed-card"))
        ui.onNodeWithTag("unconfirmed-took:4").assertTextEquals(s(R.string.confirm_took)).performClick(); assertEquals(r, backfill); assertNull(confirmed)
        ui.onNodeWithTag("unconfirmed-not-taken:4").assertTextEquals(s(R.string.confirm_not_taken)).performClick(); assertEquals(r, confirmed)
        ui.onNodeWithTag("unconfirmed-more").assertDoesNotExist()
    }

    @Test fun nothingWhenNothingIsUnconfirmed() {
        show(listOf(f.auto(1, f.now.minusSeconds(49 * 3600)), f.auto(2, f.now.minusSeconds(3600), status = "ON_TIME", origin = "APP"), f.auto(3, f.now.minusSeconds(3600), origin = "APP")))
        ui.onNodeWithTag("unconfirmed-card").assertDoesNotExist()
        ui.onAllNodesWithText(s(R.string.confirm_took)).assertCountEquals(0)
    }

    @Test fun simpleModeHidesTheName() {
        val state = NotesState(listOf(f.med), emptyList(), emptyList(), loading = false, calendarStart = f.today)
        ui.setContent { CompositionLocalProvider(LocalTodayClock provides { f.now }, LocalSimpleMode provides true) { NotesTheme(ThemeMode.LIGHT) { androidx.compose.material3.Surface {
            texts["dose"] = formatDose(2.0, "MG")
            CalendarScreen(state, f.today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = listOf(f.auto(1, f.now.minusSeconds(3600)))))
        } } } }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("unconfirmed-card"))
        ui.onNodeWithTag("unconfirmed-body:1").assertTextEquals(s(R.string.confirm_body_with, texts.getValue("dose")))
    }
}

/**
 * UI-06 accessibility in en / zh-CN / zh-TW / fr, light and dark, 320 dp at 2× font: the card's reading order (title →
 * medication → "I took it" → "I didn't take it"), 48 dp targets, a heading, and calendar days that say their state in words.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35])
class Ui06AccessibilityTest(private val locale: String, private val mode: ThemeMode) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}") fun params() =
        listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l -> listOf(ThemeMode.LIGHT, ThemeMode.DARK).map { arrayOf<Any>(l, it) } } }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)
    private val f = Ui06Fixture

    private fun show() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h1800dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f)
        val records = listOf(f.auto(1, f.now.minusSeconds(2 * 3600)), f.auto(2, f.today.minusDays(3).atTime(8, 0).atZone(f.zone).toInstant(), origin = "APP"),
            f.auto(3, f.now.minusSeconds(26 * 3600)), f.auto(4, f.now.minusSeconds(30 * 3600)))
        val state = NotesState(listOf(f.med), emptyList(), emptyList(), loading = false, calendarStart = f.today)
        ui.setContent { CompositionLocalProvider(LocalTodayClock provides { f.now }) { NotesTheme(mode) { androidx.compose.material3.Surface {
            CalendarScreen(state, f.today, {}, {}, {}, {}, PaddingValues(), extra = NotesViewModel.ExtraState(records = records),
                initialFull = true)  // UI-07 F: the month grid and legend are in the full calendar, opened explicitly
        } } } }
        ui.waitForIdle()
    }
    private fun tagsInOrder(n: SemanticsNode): List<String> = listOfNotNull(n.config.getOrNull(SemanticsProperties.TestTag)) + n.children.flatMap(::tagsInOrder)

    @Test fun cardReadsInOrderWithLargeTargets() {
        show()
        val list = ui.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasTestTag("unconfirmed-row:1"))
        assertEquals(listOf("unconfirmed-title:1", "unconfirmed-body:1", "unconfirmed-took:1", "unconfirmed-not-taken:1"),
            // UI-07 A: the row's ? is now a StatusIcon node (decorative, no words — Ui07StatusIconTest checks it is silent); it is not read.
            tagsInOrder(ui.onNodeWithTag("unconfirmed-row:1").fetchSemanticsNode()).drop(1).filterNot { it.startsWith("unconfirmed-icon:") })
        ui.onNodeWithTag("unconfirmed-title:1").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val density = context.resources.displayMetrics.density
        for (tag in listOf("unconfirmed-took:1", "unconfirmed-not-taken:1", "unconfirmed-took:3", "unconfirmed-not-taken:3", "unconfirmed-more")) {
            list.performScrollToNode(hasTestTag(tag))
            val b = ui.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag ${b.height / density}dp tall", b.height / density >= 47.5f); assertTrue("$tag ${b.width / density}dp wide", b.width / density >= 47.5f)
        }
        // Nothing is cut off at 320 dp and 2× font: every button sits inside the card's width.
        val card = ui.onNodeWithTag("unconfirmed-card").fetchSemanticsNode().boundsInRoot
        listOf("unconfirmed-took:1", "unconfirmed-not-taken:1").forEach { t -> val b = ui.onNodeWithTag(t).fetchSemanticsNode().boundsInRoot; assertTrue(t, b.left >= card.left && b.right <= card.right + 0.5f) }
    }

    @Test fun calendarDaysSayTheirStateInWords() {
        show()
        val l = context.resources.configuration.locales[0]
        fun description(d: LocalDate) = ui.onNode(hasContentDescription(d.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(l, "yMMMMdEEEE"), l)), substring = true) and hasClickAction())
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description(f.today.minusDays(1)).contains(s(R.string.status_unconfirmed)))
        assertTrue(description(f.today.minusDays(3)).contains(s(R.string.legend_missed)))
        // The legend lists every state in words, unconfirmed included; "reminder not delivered" is never on the calendar.
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("legend:UNCONFIRMED"))
        ui.onNodeWithTag("legend:UNCONFIRMED", useUnmergedTree = true).assertExists()
        ui.onAllNodesWithText(s(R.string.legend_partial), useUnmergedTree = true).assertCountEquals(1)
    }
}

/** UI-06 D: shapes. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class Ui06ShapeTest {
    @Test fun tagAndPillReplaceBadge() {
        assertEquals(RoundedCornerShape(10.dp), Corners.tag)
        assertEquals(RoundedCornerShape(percent = 50), Corners.pill)
        assertTrue(Corners::class.java.declaredMethods.none { it.name.contains("Badge", ignoreCase = true) })
        val src = Ui06Sources.main
        assertTrue(src.none { (_, t) -> t.contains("Corners.badge") })
        // StatusPill, StatusChip and the period/progress pills are fully rounded.
        assertTrue(Ui06Sources.file("Components.kt").substringAfter("fun StatusPill(").substringBefore("\n}").contains("Corners.pill"))
        assertTrue(Ui06Sources.file("StatusUi.kt").substringAfter("fun StatusChip(").substringBefore("\n}").contains("shape = Corners.pill"))
    }
}

internal object Ui06Sources {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val ui = File(root, "app/src/main/java/net/plainnotes/app/ui")
    val main: List<Pair<String, String>> get() = ui.walk().filter { it.extension == "kt" }.map { it.name to it.readText() }.toList()
    fun file(name: String) = File(ui, name).readText()
}

/**
 * UI-06 global guard: the status screens never draw a state in red. Calendar, today card, intake history, timeline, the status
 * helpers and the confirmation card use no error/errorContainer colours at all; the one real error message on the timeline
 * (saving failed) is the only allowed line. Stock notes use attention.
 */
class Ui06SourceGuardTest {
    private val allowed = setOf("Text(stringResource(R.string.operation_error),color=MaterialTheme.colorScheme.error)")

    @Test fun noRedForStates() {
        val offenders = listOf("CalendarScreen.kt", "TodayCard.kt", "HistoryScreen.kt", "LongitudinalScreen.kt", "StatusUi.kt", "UnconfirmedCard.kt").flatMap { name ->
            Ui06Sources.file(name).lines().withIndex().filter { (_, l) -> !l.trim().startsWith("*") && !l.trim().startsWith("//") &&
                Regex("""colorScheme\.(error|errorContainer|onError|onErrorContainer)\b|\bc\.(error|errorContainer|onError|onErrorContainer)\b""").containsMatchIn(l) &&
                allowed.none { l.contains(it) } }.map { (i, l) -> "$name:${i + 1}: ${l.trim()}" }
        }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
        assertTrue(Ui06Sources.file("LongitudinalScreen.kt").contains(allowed.single()))
    }

    @Test fun stockNotesUseAttention() {
        val stock = Ui06Sources.file("StockScreen.kt")
        for (tag in listOf("stock-expiry-", "stock-none-open-")) {
            val line = stock.lines().single { it.contains("testTag(\"$tag") }
            assertTrue(line, line.contains("NotesTheme.semantic.attention.accent"))
        }
        assertFalse(Regex("""colorScheme\.error\b|\bc\.error\b""").containsMatchIn(stock))
    }

    @Test fun calendarNeverShowsReminderNotDelivered() {
        val cal = Ui06Sources.file("CalendarScreen.kt") + Ui06Sources.file("UnconfirmedCard.kt") + Ui06Sources.file("StatusUi.kt")
        assertFalse(cal.contains("reminder_not_delivered") || cal.contains("not_delivered"))
    }
}

/**
 * UI-06 grayscale check: each coloured state's chip, drawn with the same (empty) words, still differs from every other one
 * once colour is removed, because of its symbol, outline or dash. Light and dark. The grayscale images are kept for the report.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class Ui06GrayscaleTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val states = listOf(DoseState.ON_TIME, DoseState.PARTIAL, DoseState.MISSED, DoseState.UNCONFIRMED, DoseState.SKIPPED, DoseState.OVERDUE)

    /** The node's pixels, drawn from the window (captureToImage does not settle under Robolectric here), as grayscale 0–255. */
    private fun shot(tag: String): android.graphics.Bitmap {
        val view = ui.activity.window.decorView
        val all = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888); view.draw(android.graphics.Canvas(all))
        val b = ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        return android.graphics.Bitmap.createBitmap(all, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
    }
    private fun gray(tag: String): IntArray { val img = shot(tag); val px = IntArray(img.width * img.height); img.getPixels(px, 0, img.width, 0, 0, img.width, img.height)
        return IntArray(px.size) { i -> val p = px[i]; ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000 } }

    @Test fun statesStayDistinctWithoutColour() {
        var mode by mutableStateOf(ThemeMode.LIGHT)
        ui.setContent { NotesTheme(mode) { androidx.compose.material3.Surface { Column { states.forEach { st ->
                androidx.compose.foundation.layout.Box(Modifier.testTag("g:$st")) { StatusChip(StatusStyles.of(st), text = "") }
            } } } } }
        for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            mode = m; ui.waitForIdle()
            val imgs = states.associateWith { gray("g:$it") }
            val sizes = imgs.values.map { it.size }.toSet()
            for (i in states.indices) for (j in i + 1 until states.size) {
                val a = imgs.getValue(states[i]); val b = imgs.getValue(states[j])
                val diff = if (a.size != b.size) Int.MAX_VALUE else a.indices.count { k -> kotlin.math.abs(a[k] - b[k]) > 24 }
                assertTrue("$mode ${states[i]} vs ${states[j]} look the same in grayscale ($diff px, sizes $sizes)", diff >= 20)
            }
            System.getProperty("ui06.grayscaleDir")?.let { dir -> File(dir).mkdirs()
                states.forEach { st -> val img = shot("g:$st")
                    val g = gray("g:$st"); val bmp = android.graphics.Bitmap.createBitmap(img.width, img.height, android.graphics.Bitmap.Config.ARGB_8888)
                    bmp.setPixels(IntArray(g.size) { (0xFF shl 24) or (g[it] shl 16) or (g[it] shl 8) or g[it] }, 0, img.width, 0, 0, img.width, img.height)
                    File(dir, "gray-${mode.name.lowercase()}-${st.name.lowercase()}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } } }
        }
    }
}

/**
 * UI-06 C on the real AppShell and an in-memory database: "I took it" opens the very dialog the intake history opens for the
 * same record, and "I didn't take it" leaves the record field-for-field as the history's own "confirm missed dose" does.
 */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui06ConfirmCardShellTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository
    private lateinit var model: NotesViewModel
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)

    @org.junit.Before fun open() {
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = db })
        model = NotesViewModel(repo, net.plainnotes.app.reminder.ReminderCoordinator(context, repo), context)
        UiPrefs(context).disclaimerAccepted = true
    }
    @org.junit.After fun close() { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() }; db.close() }

    private fun seed(vararg hoursAgo: Long): List<Long> = kotlinx.coroutines.runBlocking {
        val id = db.dao().insertMedication(Ui06Fixture.med.copy(id = 0))
        hoursAgo.map { h -> db.dao().record(Ui06Fixture.auto(0, Instant.now().minusSeconds(h * 3600)).copy(medication_id = id,
            config_snapshot = MedicationSnapshot.encode(Ui06Fixture.med.copy(id = id), null))) }
    }
    private fun show() {
        model.sync()
        ui.setContent { androidx.compose.material3.MaterialTheme { NotesApp(model, UiPrefs(context).appearance, {}) } }
        ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(10_000) { runCatching { ui.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasTestTag("unconfirmed-card")) }.isSuccess }
    }
    private fun dialogTexts(): List<String> = ui.onAllNodes(hasAnyAncestor(isDialog()) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString() }
    private fun closeDialog() { ui.onNode(hasAnyAncestor(isDialog()) and hasText(s(R.string.cancel)) and hasClickAction()).performClick(); ui.waitForIdle(); ui.onNode(isDialog()).assertDoesNotExist() }
    private fun history() {
        ui.onNodeWithContentDescription(s(R.string.menu)).performClick(); ui.waitForIdle()
        ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(s(Destination.HISTORY.title)))
        ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(Destination.HISTORY.title))).performClick(); ui.waitForIdle()
    }
    private fun historyMenu(id: Long, item: Int) {
        ui.onNodeWithTag("history-records").performScrollToNode(hasTestTag("history-record:$id"))
        ui.onNodeWithTag("history-record:$id").performClick(); ui.waitForIdle()
        ui.onNodeWithText(s(item)).performClick(); ui.waitForIdle()
    }

    @Test fun tookOpensTheSameDialogAsTheHistory() {
        val (id) = seed(3)
        show()
        ui.onNodeWithTag("unconfirmed-took:$id").performClick(); ui.waitForIdle()
        ui.onNode(isDialog()).assertExists()
        val fromCard = dialogTexts()
        assertTrue(fromCard.toString(), fromCard.contains(s(R.string.history_backfill)))
        closeDialog()
        val before = kotlinx.coroutines.runBlocking { db.dao().recordById(id) }
        history(); historyMenu(id, R.string.history_backfill)
        assertEquals(fromCard, dialogTexts())
        closeDialog()
        assertEquals(before, kotlinx.coroutines.runBlocking { db.dao().recordById(id) })   // opening and closing writes nothing
    }

    @Test fun notTakenMatchesTheHistoryConfirmFieldForField() {
        val (a, b) = seed(3, 4)
        show()
        ui.onNodeWithTag("unconfirmed-not-taken:$a").performClick(); ui.waitForIdle()
        ui.waitUntil(10_000) { kotlinx.coroutines.runBlocking { db.dao().recordById(a) }!!.unconfirmed.not() }
        history(); historyMenu(b, R.string.confirm_missed)
        ui.waitUntil(10_000) { kotlinx.coroutines.runBlocking { db.dao().recordById(b) }!!.unconfirmed.not() }
        val ra = kotlinx.coroutines.runBlocking { db.dao().recordById(a) }!!; val rb = kotlinx.coroutines.runBlocking { db.dao().recordById(b) }!!
        assertEquals(rb.copy(id = ra.id, scheduled_utc = ra.scheduled_utc), ra)
        assertEquals("APP", ra.origin); assertEquals(2, ra.revision); assertEquals("MISSED", ra.status)
        assertEquals(2, kotlinx.coroutines.runBlocking { db.dao().records() }.size)   // nothing new was written
    }
}
