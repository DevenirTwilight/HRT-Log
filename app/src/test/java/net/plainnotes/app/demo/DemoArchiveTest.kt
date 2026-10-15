package net.plainnotes.app.demo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.R
import net.plainnotes.app.debug.demo.*
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** MED-02B6 demo fixture and filters (JVM): deterministic synthetic data, independent inbox/uncategorised/unlinked
 * states, no cross-epoch linking, unknown dates stay unknown. */
class DemoFixtureTest {
    private val docs = DemoFixtures.documents()
    private fun doc(n: Int) = docs.single { it.uuid.endsWith("%012d".format(n)) }
    private val label: (String?) -> String = { it ?: "Uncategorised" }

    @Test fun fixtureIsDeterministicAndCoversEveryState() {
        assertEquals(docs, DemoFixtures.documents())
        assertTrue("20+ rows with stable keys", docs.size >= 20); assertEquals(docs.size, docs.map { it.uuid }.toSet().size)
        assertEquals(listOf(VersionState.CURRENT, VersionState.RETAINED), doc(1).versions.sortedByDescending { it.seq }.map { it.state })
        assertEquals(listOf("VISIT", "LAB", "LAB"), doc(1).links.map { it.substringBefore(':') })
        assertTrue(doc(2).inInbox && doc(2).category == null && doc(2).links.isEmpty())
        assertTrue(docs.any { it.clinicalDate.precision == DatePrecision.UNKNOWN && it.clinicalDate.text.isEmpty() })
        assertTrue(docs.any { it.trashed })
        assertEquals(setOf(StorageFailure.MISSING, StorageFailure.CORRUPT, StorageFailure.QUARANTINED), docs.mapNotNull { it.status.failure }.toSet())
        // Uncategorised and unlinked are independent: an uncategorised file can be linked, a categorised one unlinked.
        assertTrue(docs.any { it.category != null && it.links.isEmpty() })
    }

    @Test fun sameIntegerIdInAnotherEpochIsNeverLinked() {
        val retired = DemoFixtures.targets.single { it.retired }
        val current = DemoFixtures.targets.single { !it.retired && it.kind == retired.kind && it.intId == retired.intId }
        assertNotEquals(retired.key, current.key)
        assertTrue("No document links the retired-epoch record", docs.none { retired.key in it.links })
    }

    @Test fun tabsAndSearchFilterOnTheStatedFields() {
        val inbox = DemoRepository.visible(docs, DemoTab.INBOX, "", null, label)
        assertTrue(inbox.isNotEmpty() && inbox.all { it.inInbox && !it.trashed })
        assertEquals(listOf(doc(1).uuid), DemoRepository.visible(docs, DemoTab.ALL, "blood test", null, label).map { it.uuid })
        assertTrue(DemoRepository.visible(docs, DemoTab.ALL, "Synthetic Imaging", null, label).any { it.uuid == doc(5).uuid })   // institution
        assertTrue(DemoRepository.visible(docs, DemoTab.ALL, "2026-09-10", null, label).any { it.uuid == doc(1).uuid })        // clinical date
        assertTrue(DemoRepository.visible(docs, DemoTab.ALL, "2026-10-02", null, label).any { it.uuid == doc(2).uuid })        // import date
        assertTrue(DemoRepository.visible(docs, DemoTab.ALL, "", null, label).none { it.trashed })
        assertEquals(docs.filter { it.trashed }.map { it.uuid }.toSet(), DemoRepository.visible(docs, DemoTab.TRASH, "", null, label).map { it.uuid }.toSet())
        assertTrue(DemoRepository.visible(docs, DemoTab.CATEGORIES, "", DemoRepository.UNCATEGORISED, label).all { it.category == null })
        val all = DemoRepository.visible(docs, DemoTab.ALL, "", null, label)
        assertEquals("Order is deterministic", all, DemoRepository.visible(docs.reversed(), DemoTab.ALL, "", null, label))
    }
}

/** The demo UI in four languages at 320dp with 2x font in dark mode: banner always visible, inbox → search → detail
 * (versions, links, date precision, source claim) → back. Robolectric (JVM); the native run is the device test. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class DemoArchiveLayoutTest(private val locale: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").map { arrayOf<Any>(it) } }
    private val setup = TestRule { base, _ -> object : org.junit.runners.model.Statement() { override fun evaluate() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-night-xxhdpi"); RuntimeEnvironment.setFontScale(2f); base.evaluate() } } }
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(setup).around(ui)
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    /** Lazy rows exist only once scrolled into view: scroll the list itself to the target, then use it. */
    private fun inList(list: String, m: SemanticsMatcher): SemanticsNodeInteraction { ui.onNodeWithTag(list).performScrollToNode(m); return ui.onNode(m) }
    private fun row(tag: String) = inList("demo-list", hasTestTag(tag))
    private fun detail(m: SemanticsMatcher) = inList("demo-detail", m)

    @Test fun inboxSearchDetailAndBack() {
        ui.setContent { NotesTheme(ThemeMode.DARK) { DemoArchiveApp(DemoRepository()) } }
        ui.onNodeWithTag("demo-banner").assertIsDisplayed().assert(hasText(s(R.string.demo_banner)))
        ui.onNodeWithTag("demo-tab-INBOX").assertIsSelected()
        row("demo-doc:00000000-0000-4000-8000-000000000002").assertIsDisplayed()
        ui.onNodeWithTag("demo-tab-ALL").performClick()
        ui.onNodeWithTag("demo-search").performTextInput("Blood test")
        row("demo-doc:00000000-0000-4000-8000-000000000001").performClick()
        ui.onNodeWithTag("demo-detail").assertExists()
        ui.onNodeWithTag("demo-banner").assertIsDisplayed()
        detail(hasText(s(R.string.demo_version_current, 2))).assertIsDisplayed()
        detail(hasText(s(R.string.demo_version_retained, 1))).assertIsDisplayed()
        listOf("VISIT:epoch-2026:7", "LAB:epoch-2026:12", "LAB:epoch-2026:13").forEach { detail(hasTestTag("demo-link:$it")).assertIsDisplayed() }
        ui.onAllNodes(hasTestTag("demo-link:LAB:epoch-2025-retired:12")).assertCountEquals(0)
        detail(hasTestTag("demo-back")).performClick()
        ui.onNodeWithTag("demo-detail").assertDoesNotExist(); ui.onNodeWithTag("demo-list").assertExists()
    }

    @Test fun unknownDateAndFailureStatesAreExplicit() {
        ui.setContent { NotesTheme(ThemeMode.DARK) { DemoArchiveApp(DemoRepository()) } }
        row("demo-doc:00000000-0000-4000-8000-000000000007").performClick()   // quarantined, date unknown
        detail(hasTestTag("demo-clinical-date")).assert(hasText(s(R.string.demo_date_unknown)))
        detail(hasTestTag("demo-status")).assert(hasAnyDescendant(hasText(s(R.string.demo_status_quarantined))))
        detail(hasTestTag("demo-links-none")).assertIsDisplayed()
    }
}
