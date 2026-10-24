package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import net.plainnotes.app.data.*
import net.plainnotes.app.reminder.ReminderCoordinator
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val TOP = "top"
private const val DIVIDER = "|"

/** UI-03 A1: the drawer grouped by where the information comes from, in display order (the [Destination] enum is unchanged). */
private val EXPECTED: List<Any> = listOf(
    Destination.CALENDAR, Destination.TIMELINE,
    "journal", Destination.HISTORY, Destination.WELLBEING,
    "medication", Destination.MEDICATIONS, Destination.STOCK, Destination.CONCENTRATION,
    "records", Destination.MEDICAL, Destination.VISITS, Destination.LABS,
    DIVIDER, Destination.SETTINGS, Destination.ABOUT,
)

/**
 * UI-03A: drawer order and groups (A1, A6.1), headings with nothing before the first two entries (A6.2), and headings
 * whose spoken text differs from every entry (A2, A6.5), in 4 languages × 320/411dp × font 1/2.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui03DrawerTest(private val locale: String, private val width: Int, private val font: Float) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").flatMap { l -> listOf(320, 411).flatMap { w -> listOf(1f, 2f).map { arrayOf<Any>(l, w, it) } } }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun s(id: Int) = ui.activity.getString(id)
    private var picked: Destination? = null

    @Before fun show() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp"); RuntimeEnvironment.setFontScale(font)
        ui.setContent { MaterialTheme { DrawerContent(Destination.CALENDAR) { picked = it } } }
        ui.waitForIdle()
    }
    private fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
    private fun tag(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.TestTag).orEmpty()

    /** The drawer's direct semantics children, read as headings (group key), the divider, or the entry they name. */
    private fun sequence(): List<Any> {
        val byTitle = Destination.entries.associateBy { s(it.title) }
        return ui.onNodeWithTag("drawer-content").fetchSemanticsNode().children.mapNotNull { n ->
            when {
                tag(n).startsWith("drawer-group:") -> tag(n).removePrefix("drawer-group:")
                tag(n) == "drawer-divider" -> DIVIDER
                text(n) == s(R.string.app_name) || text(n).isEmpty() -> null   // the drawer header (name and icon), not an entry
                else -> byTitle[text(n)] ?: "unknown:${text(n)}"
            }
        }
    }

    @Test fun orderAndGroupsMatchTheDecision() {
        assertEquals(EXPECTED, sequence())
        assertEquals(Destination.entries.toSet(), EXPECTED.filterIsInstance<Destination>().toSet())
        assertEquals(12, EXPECTED.count { it is Destination })
    }

    @Test fun threeHeadingsAndNothingBeforeTheTopEntries() {
        val headings = ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
        assertEquals(listOf("drawer-group:journal", "drawer-group:medication", "drawer-group:records"), headings.map(::tag))
        assertEquals(listOf(s(R.string.drawer_group_journal), s(R.string.drawer_group_medication), s(R.string.drawer_group_records)), headings.map(::text))
        val seq = sequence()
        assertEquals(listOf<Any>(Destination.CALENDAR, Destination.TIMELINE), seq.take(2))   // no heading or divider before them
        assertEquals(1, seq.count { it == DIVIDER })
    }

    @Test fun headingsReadDifferentlyFromEveryEntry() {
        val entries = Destination.entries.map { s(it.title) }
        val headings = listOf(R.string.drawer_group_journal, R.string.drawer_group_medication, R.string.drawer_group_records).map(::s)
        for (h in headings) assertFalse("heading \"$h\" reads the same as an entry in $locale", entries.any { it.equals(h, ignoreCase = true) })
        // The records heading sits directly above "Medical records": TalkBack must not read the same words twice in a row.
        assertNotEquals(s(R.string.medical_records), s(R.string.drawer_group_records))
        assertEquals(headings.size, headings.toSet().size)
        assertEquals(entries.size, entries.toSet().size)
    }

    @Test fun everyEntryIsReachableAndPicksItsDestination() {
        for (d in Destination.entries) {
            ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(s(d.title)))
            ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(d.title))).assertIsDisplayed().performClick()
            ui.runOnIdle { assertEquals(d, picked) }
        }
    }
}

/** UI-03A A6.3/A6.4 on the real shell: each drawer entry lands on its page, and the top bar shows the same name as the drawer. */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class Ui03ShellTitleTest(private val locale: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").map { arrayOf<Any>(it) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ui.activity
    private lateinit var db: NotesDatabase
    private lateinit var model: NotesViewModel
    private fun s(id: Int) = context.getString(id)

    @Before fun open() {
        RuntimeEnvironment.setQualifiers("$locale-w411dp-h891dp")
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()
        val repo = NotesRepository(object : DatabaseAccess(context) { override fun get(space: Space) = net.plainnotes.app.syntheticLibrary(db) })
        model = NotesViewModel(repo, ReminderCoordinator(context, repo), context).also {net.plainnotes.app.awaitRuntimeLibrary(it)}
        UiPrefs(context).disclaimerAccepted = true
    }
    @After fun close() { androidx.lifecycle.ViewModelStore().apply { put("model", model); clear() }; db.close() }

    @Test fun drawerEntriesLandOnTheirPageWithTheSameTitle() {
        val appearance = UiPrefs(context).appearance
        ui.setContent { MaterialTheme { NotesApp(model, appearance, {}) } }
        ui.waitForIdle()
        for (d in Destination.entries) {
            ui.onNodeWithContentDescription(s(R.string.menu)).performClick(); ui.waitForIdle()
            ui.onNodeWithTag("drawer-content").performScrollToNode(hasText(s(d.title)))
            ui.onNodeWithTag("drawer-content").onChildren().filterToOne(hasText(s(d.title))).performClick(); ui.waitForIdle()
            // The calendar keeps the app name in the top bar (unchanged); every other page shows its drawer name.
            ui.onNodeWithTag("top-title").assertTextEquals(s(if (d == Destination.CALENDAR) R.string.app_name else d.title))
            if (d == Destination.CALENDAR) ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertExists()
            else ui.onNodeWithContentDescription(s(R.string.calendar_pick_start)).assertDoesNotExist()
        }
        // The four renamed entries (A3).
        val expected = mapOf("en" to listOf("Dose log", "Treatment timeline", "Medication plans", "Estimated levels"),
            "zh-rCN" to listOf("服药记录", "治疗时间线", "药物方案", "浓度估算"), "zh-rTW" to listOf("服藥紀錄", "治療時間軸", "藥物方案", "濃度估算"),
            "fr-rFR" to listOf("Journal des prises", "Chronologie du traitement", "Plans de traitement", "Niveaux estimés"))
        assertEquals(expected.getValue(locale), listOf(Destination.HISTORY, Destination.TIMELINE, Destination.MEDICATIONS, Destination.CONCENTRATION).map { s(it.title) })
    }
}
