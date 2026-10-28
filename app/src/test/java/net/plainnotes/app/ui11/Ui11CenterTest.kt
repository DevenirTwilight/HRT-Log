package net.plainnotes.app.ui11

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import net.plainnotes.app.R
import net.plainnotes.app.data.MedicationEntity
import net.plainnotes.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * UI-11 (REQUIREMENTS §159): the drug reference centre. Synthetic medications only. Four languages × 320 dp at 2× font and
 * 411 dp at 1× × light and dark. Shots (no pixel assertions) are written when UI11_SHOTS=<label> is set.
 */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class Ui11CenterTest(private val locale: String, private val width: Int, private val font: Float, private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}dp/{2}x/dark={3}")
        fun params() = listOf("en", "zh-rCN", "zh-rTW", "fr").flatMap { l ->
            listOf(arrayOf<Any>(l, 320, 2f, false), arrayOf<Any>(l, 320, 2f, true), arrayOf<Any>(l, 411, 1f, false), arrayOf<Any>(l, 411, 1f, true)) }
        /** The accessible name of the shared back button, independent of the resources under test. */
        val BACK = mapOf("en" to "Back", "zh-rCN" to "返回", "zh-rTW" to "返回", "fr" to "Retour")
        fun contrast(a: Color, b: Color): Double { val x = a.luminance().toDouble() + 0.05; val y = b.luminance().toDouble() + 0.05; return maxOf(x, y) / minOf(x, y) }
    }
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val mode get() = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
    private fun s(id: Int, vararg a: Any) = ui.activity.getString(id, *a)
    private fun setup() { RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if (width == 320) 640 else 891}dp-xxhdpi"); RuntimeEnvironment.setFontScale(font) }

    private fun med(id: Long, name: String, active: Boolean = true) = MedicationEntity(id = id, name = name, molecule = "OTHER", unit = "MG", dose_per_intake = 1.0,
        container_capacity = 1.0, soon_alert_minutes = 0, late_after_minutes = 60, site_rotation = false, notifications_on = false, active = active, sort_order = id.toInt())
    private val official = med(1, "SYNTHETIC official-linked medication")
    private val mine = med(2, "SYNTHETIC user-entered-linked medication")
    private val bare = med(3, "SYNTHETIC unlinked medication")
    private val archived = med(4, "SYNTHETIC archived medication", active = false)
    private val meds = listOf(official, mine, bare, archived)
    private val links = mapOf(1L to DrugLinkStatus.OFFICIAL, 2L to DrugLinkStatus.USER_ENTERED, 3L to DrugLinkStatus.NONE, 4L to DrugLinkStatus.OFFICIAL)

    private fun shot(name: String, dialog: Boolean = false) {
        val dir = System.getenv("UI11_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        ui.waitForIdle()
        val view = if (dialog) requireNotNull(org.robolectric.shadows.ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }?.window).decorView else ui.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        File("build/ui11-shots/$dir").apply { mkdirs() }.resolve("${name}_${locale}_${width}dp_${if (font == 1f) "1x" else "2x"}_${if (dark) "dark" else "light"}.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun scrollTo(tag: String) = ui.onNodeWithTag("drug-center-list").performScrollToNode(hasTestTag(tag))

    @Test fun backButtonIsNamedInFourLanguagesAndGoesBack() {
        setup()
        var dismissed = 0
        ui.setContent { NotesTheme(mode) { DrugReferenceCenterDialog(meds, {}, {}, null, 0) { dismissed++ } } }
        val back = ui.onNodeWithTag("drug-center-close")
        back.assert(hasContentDescription(BACK.getValue(locale))).assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        assertEquals("the new name comes from nav_back", BACK.getValue(locale), s(R.string.nav_back))
        assertNotEquals("not the Cancel word", s(R.string.cancel), s(R.string.nav_back))
        ui.onAllNodesWithText(s(R.string.cancel)).assertCountEquals(0)
        shot("center_top", dialog = true)
        back.performClick()
        assertEquals(1, dismissed)
    }

    @Test fun planAndStockButtonsAreGone() {
        setup()
        ui.setContent { NotesTheme(mode) { DrugReferenceCenterDialog(meds, {}, {}, null, 0) {} } }
        for (tag in listOf("drug-center-plans", "drug-center-stock")) {
            ui.onAllNodesWithTag(tag).assertCountEquals(0)
            for (anchor in listOf("drug-center-catalogue", "drug-center-source-user", "drug-center-recalls")) { scrollTo(anchor); ui.onAllNodesWithTag(tag).assertCountEquals(0) }
        }
    }

    @Test fun mainCardCarriesRegionTitleScopeAndAPillSearchEntry() {
        setup()
        var browsed = 0
        ui.setContent { NotesTheme(mode) { DrugReferenceCenterScreen(meds, { browsed++ }, {}, PaddingValues(), links) } }
        ui.onNodeWithTag("drug-center-hero").assert(hasAnyDescendant(hasText(s(R.string.drug_center_region))))
            .assert(hasAnyDescendant(hasText(s(R.string.drug_center_find_title)))).assert(hasAnyDescendant(hasText(s(R.string.drug_center_scope))))
        ui.onNodeWithTag("drug-center-catalogue").assertHasClickAction().assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(1, browsed)
        shot("center_page")
    }

    @Test fun medicationsShowThreeLinkStatesAndTheArchivedTag() {
        setup()
        val tapped = mutableListOf<Long>()
        ui.setContent { NotesTheme(mode) { DrugReferenceCenterScreen(meds, {}, { tapped += it.id }, PaddingValues(), links) } }
        scrollTo("drug-center-medication:1")
        // The tag is part of the tappable row: its words join the row's spoken text; its tone is read from the tag itself.
        fun row(id: Long) = ui.onNodeWithTag("drug-center-medication:$id")
        fun tone(id: Long, value: String) = ui.onNodeWithTag("drug-center-link-tag:$id", useUnmergedTree = true).assert(SemanticsMatcher.expectValue(DrugTagToneKey, value))
        scrollTo("drug-center-medication:1")
        row(1).assert(hasText(s(R.string.drug_link_official))); tone(1, "official")
        scrollTo("drug-center-medication:2")
        row(2).assert(hasText(s(R.string.drug_link_user))); tone(2, "user")
        scrollTo("drug-center-medication:3")
        row(3).assert(hasText(s(R.string.drug_link_none))); tone(3, "neutral")
        ui.onAllNodesWithTag("drug-center-archived-tag:3", useUnmergedTree = true).assertCountEquals(0)
        row(3).assert(hasText(s(R.string.archived)).not())
        scrollTo("drug-center-medication:4")
        row(4).assert(hasText(s(R.string.drug_link_official))).assert(hasText(s(R.string.archived)))
        ui.onNodeWithTag("drug-center-archived-tag:4", useUnmergedTree = true).assert(SemanticsMatcher.expectValue(DrugTagToneKey, "neutral"))
        // The three statuses are told apart by their words, not by colour alone.
        assertEquals(3, listOf(R.string.drug_link_official, R.string.drug_link_user, R.string.drug_link_none).map { s(it) }.toSet().size)
        // The whole row is the tap target and reaches the existing reference flow with the medication itself.
        for (m in meds) { scrollTo("drug-center-medication:${m.id}"); ui.onNodeWithTag("drug-center-medication:${m.id}").assertHasClickAction().assertHeightIsAtLeast(56.dp).performClick() }
        assertEquals(meds.map { it.id }, tapped)
        // Group shape: 24 dp outer ends, 6 dp where items meet.
        scrollTo("drug-center-medication:1")
        ui.onNodeWithTag("drug-center-medication:1").assert(SemanticsMatcher.expectValue(GroupedShapeKey, groupedShape(0, 4)))
        scrollTo("drug-center-medication:2")
        ui.onNodeWithTag("drug-center-medication:2").assert(SemanticsMatcher.expectValue(GroupedShapeKey, groupedShape(1, 4)))
        scrollTo("drug-center-medication:4")
        ui.onNodeWithTag("drug-center-medication:4").assert(SemanticsMatcher.expectValue(GroupedShapeKey, groupedShape(3, 4)))
        shot("center_medications")
    }

    @Test fun tagColoursAreSemanticTokensWithReadableText() {
        val scheme = notesColorScheme(dark); val semantic = semanticColors(scheme, dark)
        val official = drugTagColors(DrugLinkStatus.OFFICIAL, semantic, scheme)
        val user = drugTagColors(DrugLinkStatus.USER_ENTERED, semantic, scheme)
        val none = drugTagColors(DrugLinkStatus.NONE, semantic, scheme)
        assertEquals(semantic.success.container to semantic.success.onContainer, official)
        assertEquals(semantic.attention.container to semantic.attention.onContainer, user)
        assertEquals(scheme.surfaceContainerHighest to scheme.onSurfaceVariant, none)
        for ((name, pair) in listOf("official" to official, "user" to user, "neutral" to none))
            assertTrue("$name tag text ≥ 4.5:1 (dark=$dark): ${contrast(pair.first, pair.second)}", contrast(pair.first, pair.second) >= 4.5)
        assertNotEquals("official and user-entered are different tones", official.first, user.first)
        // Never the danger colour.
        for (pair in listOf(official, user, none)) { assertNotEquals(semantic.danger.container, pair.first); assertNotEquals(scheme.errorContainer, pair.first) }
    }

    @Test fun sourcesNeverInventAVersionOrACount() {
        setup()
        var info by androidx.compose.runtime.mutableStateOf(DrugSourceInfo())
        ui.setContent { NotesTheme(mode) { DrugReferenceCenterScreen(meds, {}, {}, PaddingValues(), links, info) } }
        scrollTo("drug-center-source-bdpm")
        ui.onNodeWithTag("drug-center-source-bdpm").assert(hasAnyDescendant(hasText(s(R.string.drug_src_bdpm_title)))).assert(hasAnyDescendant(hasText(s(R.string.drug_src_bdpm_info_plain))))
            .assert(hasAnyDescendant(hasText(s(R.string.drug_src_official_tag))))
        scrollTo("drug-center-source-user")
        ui.onNodeWithTag("drug-center-source-user").assert(hasAnyDescendant(hasText(s(R.string.drug_src_user_info_plain)))).assert(hasAnyDescendant(hasText(s(R.string.drug_src_unverified_tag))))
        ui.runOnIdle { info = DrugSourceInfo("2026-10-05", 3, false) }
        scrollTo("drug-center-source-bdpm")
        ui.onNodeWithTag("drug-center-source-bdpm").assert(hasAnyDescendant(hasText(s(R.string.drug_src_bdpm_info, "2026-10-05"))))
        scrollTo("drug-center-source-user")
        ui.onNodeWithTag("drug-center-source-user").assert(hasAnyDescendant(hasText(s(R.string.drug_src_user_info, 3))))
        ui.runOnIdle { info = DrugSourceInfo("2026-10-05", 200, true) }
        ui.onNodeWithTag("drug-center-source-user").assert(hasAnyDescendant(hasText(s(R.string.drug_src_user_info_many, 200))))
        shot("center_sources")
    }

    @Test fun descriptionRowsStartCollapsedAndOpenAndCloseIndependently() {
        setup()
        ui.setContent { NotesTheme(mode) { DrugReferenceCenterScreen(meds, {}, {}, PaddingValues(), links) } }
        val tags = listOf("drug-center-identity", "drug-center-packaging", "drug-center-states")
        for (t in tags) { scrollTo(t); ui.onNodeWithTag("$t-toggle").assertHasClickAction().assertHeightIsAtLeast(56.dp).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s(R.string.drug_state_collapsed))); ui.onAllNodesWithTag("$t-body").assertCountEquals(0) }
        scrollTo("drug-center-identity"); ui.onNodeWithTag("drug-center-identity-toggle").performClick()
        ui.onNodeWithTag("drug-center-identity-body").assert(hasText(s(R.string.drug_center_identity_body)))
        ui.onNodeWithTag("drug-center-identity-toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s(R.string.drug_state_expanded)))
        scrollTo("drug-center-packaging"); ui.onAllNodesWithTag("drug-center-packaging-body").assertCountEquals(0)
        ui.onNodeWithTag("drug-center-packaging-toggle").performClick(); ui.onNodeWithTag("drug-center-packaging-body").assertExists()
        shot("center_learn_open")
        scrollTo("drug-center-identity"); ui.onNodeWithTag("drug-center-identity-toggle").performClick()
        ui.onAllNodesWithTag("drug-center-identity-body").assertCountEquals(0)
    }

    @Test fun expandedRowsSurviveSavedStateRestoration() {
        setup()
        val restoration = StateRestorationTester(ui)
        restoration.setContent { NotesTheme(mode) { DrugReferenceCenterScreen(meds, {}, {}, PaddingValues(), links) } }
        scrollTo("drug-center-packaging"); ui.onNodeWithTag("drug-center-packaging-toggle").performClick()
        restoration.emulateSavedInstanceStateRestore()
        scrollTo("drug-center-packaging"); ui.onNodeWithTag("drug-center-packaging-body").assertExists()
        scrollTo("drug-center-identity"); ui.onAllNodesWithTag("drug-center-identity-body").assertCountEquals(0)
    }

    @Test fun notYetOpenModulesAreRowsNotButtons() {
        setup()
        ui.setContent { NotesTheme(mode) { DrugReferenceCenterScreen(meds, {}, {}, PaddingValues(), links) } }
        for(tag in listOf("drug-center-rcp","drug-center-notice","drug-center-safety","drug-center-supply")) {
            scrollTo(tag);ui.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
        }
        val future = mapOf("drug-center-recalls" to R.string.center_recalls)
        for ((tag, title) in future) {
            scrollTo(tag)
            ui.onNodeWithTag(tag).assertIsDisplayed().assert(hasClickAction().not())
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(s(R.string.drug_not_open_desc, s(title)))))
            ui.onNodeWithTag(tag).performClick()
            ui.onAllNodesWithTag("center-unavailable-dialog").assertCountEquals(0)
            assertTrue("$tag shows the tag", ui.onAllNodesWithText(s(R.string.drug_not_open_tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        // The row's own semantics are one spoken name; the visible tag is a descendant in the unmerged tree.
        // (a lazy list composes only the visible rows, so each row is checked as it is scrolled to)
        scrollTo("drug-center-recalls"); shot("center_future")
    }
}
