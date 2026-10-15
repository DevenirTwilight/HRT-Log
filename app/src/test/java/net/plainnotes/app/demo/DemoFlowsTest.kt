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
import java.time.LocalDate

private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

/** MED-02B6 PR-B (JVM): every demo mutation, its validation, and that it never touches anything but the in-memory list. */
class DemoRepositoryFlowTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val repo = DemoRepository { today }
    private fun doc(n: Int) = repo.document(uuid(n))!!
    private val visit7 = "VISIT:${DemoFixtures.EPOCH_CURRENT}:7"
    private val lab12 = "LAB:${DemoFixtures.EPOCH_CURRENT}:12"
    private val retired12 = "LAB:${DemoFixtures.EPOCH_RETIRED}:12"

    @Test fun linkAndUnlinkAreExplicitAndRetiredIsRefused() {
        assertTrue(repo.link(uuid(2), visit7)); assertTrue(repo.link(uuid(2), lab12)); assertTrue(repo.link(uuid(2), lab12))   // idempotent
        assertEquals(listOf(visit7, lab12), doc(2).links)
        assertFalse("linking files it out of the inbox", doc(2).inInbox)
        assertFalse(repo.link(uuid(2), retired12)); assertFalse(retired12 in doc(2).links)
        assertFalse(repo.link(uuid(2), "LAB:${DemoFixtures.EPOCH_CURRENT}:999"))
        assertTrue(repo.unlink(uuid(2), visit7)); assertEquals(listOf(lab12), doc(2).links)
        assertNull("still uncategorised: linking does not categorise", doc(2).category)
        assertFalse("a binned document cannot be linked", repo.link(uuid(4), lab12))
    }

    @Test fun editValidatesAndChangesNothingOnError() {
        val before = doc(3)
        fun e(title: String = "Renamed", p: DatePrecision = DatePrecision.DAY, y: String = "2026", m: String = "9", d: String = "30", note: String = "") =
            DemoEdit(title, "  Synthetic Clinic X ", "LETTER", p, y, m, d, note)
        assertEquals(DemoEditError.TITLE_BLANK, repo.edit(uuid(3), e(title = "   ")))
        assertEquals(DemoEditError.TITLE_TOO_LONG, repo.edit(uuid(3), e(title = "x".repeat(121))))
        assertEquals(DemoEditError.YEAR, repo.edit(uuid(3), e(y = "1899")))
        assertEquals(DemoEditError.YEAR, repo.edit(uuid(3), e(y = "2027")))
        assertEquals(DemoEditError.MONTH, repo.edit(uuid(3), e(m = "13")))
        assertEquals(DemoEditError.DAY, repo.edit(uuid(3), e(m = "2", d = "30")))
        assertEquals(DemoEditError.FUTURE, repo.edit(uuid(3), e(m = "10", d = "16")))
        assertEquals(DemoEditError.FUTURE, repo.edit(uuid(3), e(p = DatePrecision.MONTH, m = "11")))
        assertEquals(DemoEditError.NOTE_TOO_LONG, repo.edit(uuid(3), e(note = "n".repeat(501))))
        assertEquals("nothing changed on any error", before, doc(3))
        assertNull(repo.edit(uuid(3), e()))
        val after = doc(3)
        assertEquals("Renamed", after.title); assertEquals("Synthetic Clinic X", after.source.institution); assertEquals("LETTER", after.category)
        assertEquals(ClinicalDate.day(LocalDate.of(2026, 9, 30)), after.clinicalDate)
        assertEquals("versions and links are not edited", before.versions, after.versions); assertEquals(before.links, after.links)
        assertTrue("still in the inbox until filed", after.inInbox)
        assertNull(repo.edit(uuid(3), e(p = DatePrecision.UNKNOWN, y = "", m = "", d = "")))
        assertEquals(ClinicalDate.UNKNOWN, doc(3).clinicalDate)
        assertNull(repo.edit(uuid(3), e(p = DatePrecision.YEAR, y = "2019", m = "x", d = "y")))
        assertEquals(ClinicalDate.year(2019), doc(3).clinicalDate)
        assertTrue(repo.file(uuid(3))); assertFalse(doc(3).inInbox)
    }

    @Test fun correctionAddsAVersionAndKeepsTheOldOnes() {
        val before = doc(5)                                      // corrupt original
        assertFalse(repo.addCorrection(uuid(5), "   "))
        assertTrue(repo.addCorrection(uuid(5), "Clinic sent an intact copy"))
        val after = doc(5)
        assertEquals(2, after.versions.size)
        assertEquals(before.versions.single().copy(state = VersionState.RETAINED), after.versions.single { it.seq == 1 })
        val v2 = after.current
        assertEquals(2, v2.seq); assertEquals(today, v2.importedOn); assertEquals("Clinic sent an intact copy", v2.correctionNote)
        assertEquals(DocumentStatus.TEST_FILE, after.status)
        assertEquals(1, after.versions.count { it.state == VersionState.CURRENT })
        assertTrue(repo.addCorrection(uuid(1), "Third issue"))
        assertEquals(listOf(1, 2, 3), doc(1).versions.map { it.seq }); assertEquals(3, doc(1).current.seq)
        assertEquals(doc(1).versions.map { it.uuid }.toSet().size, 3)
    }

    @Test fun binRestoreAndPermanentDeleteOnlyFromTheBin() {
        assertFalse("not from the library", repo.deleteForever(uuid(1)))
        assertTrue(repo.trash(uuid(1))); assertTrue(doc(1).trashed)
        assertTrue(repo.restore(uuid(1))); assertFalse(doc(1).trashed); assertEquals(3, doc(1).links.size)
        val count = repo.documents.value.size
        assertTrue(repo.deleteForever(uuid(4)))
        assertNull(repo.document(uuid(4))); assertEquals(count - 1, repo.documents.value.size)
        assertFalse(repo.restore(uuid(4)))
    }

    @Test fun resetBringsBackTheFixture() {
        repo.trash(uuid(1)); repo.link(uuid(2), visit7); repo.deleteForever(uuid(4)); repo.addCorrection(uuid(6), "x")
        repo.reset()
        assertEquals(DemoFixtures.documents(), repo.documents.value)
    }
}

/** The PR-B flows in the real demo UI, four languages at 320dp with 2× font (Robolectric). */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk = [35], application = android.app.Application::class)
class DemoFlowsUiTest(private val locale: String) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr-rFR").map { arrayOf<Any>(it) } }
    private val setup = TestRule { base, _ -> object : org.junit.runners.model.Statement() { override fun evaluate() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xxhdpi"); RuntimeEnvironment.setFontScale(2f); base.evaluate() } } }
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(setup).around(ui)
    private val repo = DemoRepository { LocalDate.of(2026, 10, 15) }
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun inList(list: String, m: SemanticsMatcher): SemanticsNodeInteraction { ui.onNodeWithTag(list).performScrollToNode(m); return ui.onNode(m) }
    private fun open(n: Int, tab: String = "ALL") { ui.onNodeWithTag("demo-tab-$tab").performClick(); inList("demo-list", hasTestTag("demo-doc:${uuid(n)}")).performClick() }
    private fun action(tag: String) = inList("demo-detail", hasTestTag(tag))
    private fun show() { ui.setContent { NotesTheme(ThemeMode.LIGHT) { DemoArchiveApp(repo) } } }

    @Test fun linkThenUnlinkAVisitAndTwoLabs() {
        show(); open(2, "INBOX")
        action("demo-link-manage").performClick()
        for (k in listOf("VISIT:epoch-2026:7", "LAB:epoch-2026:12", "LAB:epoch-2026:13")) inList("demo-link-list", hasTestTag("demo-link-toggle:$k")).performClick()
        inList("demo-link-list", hasTestTag("demo-link-blocked:LAB:epoch-2025-retired:12")).assertExists()
        ui.onAllNodesWithTag("demo-link-toggle:LAB:epoch-2025-retired:12").assertCountEquals(0)
        ui.onNodeWithTag("demo-link-close").performClick()
        assertEquals(3, repo.document(uuid(2))!!.links.size)
        action("demo-link:LAB:epoch-2026:12").assertIsDisplayed()
        action("demo-unlink:LAB:epoch-2026:12").performClick()
        assertEquals(listOf("VISIT:epoch-2026:7", "LAB:epoch-2026:13"), repo.document(uuid(2))!!.links)
        ui.onAllNodesWithTag("demo-link:LAB:epoch-2026:12").assertCountEquals(0)
    }

    @Test fun editCancelValidationThenSave() {
        show(); open(3, "INBOX")
        val before = repo.document(uuid(3))
        action("demo-edit").performClick()
        ui.onNodeWithTag("demo-edit-title").performTextReplacement("Edited synthetic scan")
        ui.onNodeWithTag("demo-edit-cancel").performClick()
        assertEquals("cancel writes nothing", before, repo.document(uuid(3)))
        action("demo-edit").performClick()
        ui.onNodeWithTag("demo-edit-title").performTextReplacement("Edited synthetic scan")
        ui.onNodeWithTag("demo-edit-prec:DAY").performScrollTo().performClick()
        ui.onNodeWithTag("demo-edit-month").performScrollTo().performTextReplacement("2")
        ui.onNodeWithTag("demo-edit-day").performScrollTo().performTextReplacement("30")
        ui.onNodeWithTag("demo-edit-save").performClick()
        ui.onNodeWithTag("demo-edit-error").performScrollTo().assertTextEquals(s(R.string.demo_err_day))
        assertEquals("invalid keeps the record", before, repo.document(uuid(3)))
        ui.onNodeWithTag("demo-edit-title").performScrollTo().assertTextContains("Edited synthetic scan")     // draft kept
        ui.onNodeWithTag("demo-edit-day").performScrollTo().performTextReplacement("28")
        ui.onNodeWithTag("demo-edit-cat:LETTER").performScrollTo().performClick()
        ui.onNodeWithTag("demo-edit-institution").performScrollTo().performTextReplacement("Synthetic Clinic Q")
        ui.onNodeWithTag("demo-edit-note").performScrollTo().performTextReplacement("Edited in the demo")
        ui.onNodeWithTag("demo-edit-save").performClick()
        ui.onNodeWithTag("demo-edit-form").assertDoesNotExist()
        val after = repo.document(uuid(3))!!
        assertEquals("Edited synthetic scan", after.title); assertEquals("LETTER", after.category); assertEquals("Synthetic Clinic Q", after.source.institution)
        assertEquals(ClinicalDate.day(LocalDate.of(2025, 2, 28)), after.clinicalDate); assertEquals("Edited in the demo", after.note)
        action("demo-clinical-date").assertTextContains("2025", substring = true)
    }

    @Test fun correctionThenBinRestoreThenDoubleConfirmedDelete() {
        show(); open(6)
        action("demo-correct").performClick()
        ui.onNodeWithTag("demo-correct-save").assertIsNotEnabled()
        ui.onNodeWithTag("demo-correct-note").performTextInput("Found the original printout")
        ui.onNodeWithTag("demo-correct-save").performClick()
        action("demo-version:${uuid(602)}").assertExists()
        assertEquals(DocumentStatus.TEST_FILE, repo.document(uuid(6))!!.status)
        action("demo-trash").performClick()
        assertTrue(repo.document(uuid(6))!!.trashed)
        action("demo-restore").performClick(); assertFalse(repo.document(uuid(6))!!.trashed)
        action("demo-trash").performClick()
        action("demo-delete").performClick()
        ui.onNodeWithTag("demo-delete-cancel1").performClick(); assertNotNull(repo.document(uuid(6)))
        action("demo-delete").performClick(); ui.onNodeWithTag("demo-delete-confirm1").performClick()
        ui.onNodeWithTag("demo-delete-cancel2").performClick(); assertNotNull("second step can still back out", repo.document(uuid(6)))
        action("demo-delete").performClick(); ui.onNodeWithTag("demo-delete-confirm1").performClick(); ui.onNodeWithTag("demo-delete-confirm2").performClick()
        assertNull(repo.document(uuid(6)))
        ui.onNodeWithTag("demo-detail").assertDoesNotExist(); ui.onNodeWithTag("demo-list").assertExists()
    }

    @Test fun resetDemoRestoresTheFixture() {
        repo.trash(uuid(1)); repo.deleteForever(uuid(4))
        show()
        inList("demo-list", hasTestTag("demo-reset")).performClick()
        ui.onNodeWithTag("demo-reset-confirm").performClick()
        assertEquals(DemoFixtures.documents(), repo.documents.value)
        ui.onNodeWithTag("demo-tab-TRASH").performClick()
        inList("demo-list", hasTestTag("demo-doc:${uuid(4)}")).assertExists()
    }

    @Test fun missingCorruptQuarantinedAreShownAndNeverOpen() {
        show()
        for ((n, label) in listOf(6 to R.string.demo_status_missing, 5 to R.string.demo_status_corrupt, 7 to R.string.demo_status_quarantined)) {
            open(n)
            action("demo-status").assert(hasAnyDescendant(hasText(s(label))))
            ui.onAllNodesWithText(s(R.string.demo_preview_test)).assertCountEquals(0)
            action("demo-back").performClick()
        }
    }
}
