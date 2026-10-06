package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.importer.HrtTracker
import net.plainnotes.app.ui.HtImportWizard
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tracker import dialog with exact duplicates: every choice must be selectable and Import must go through. */
@RunWith(RobolectricTestRunner::class)
// Default Robolectric screen size: at phone size a TextField inside a dialog never idles under Robolectric (not on devices).
@Config(sdk = [35], qualifiers = "zh-rCN")
class HtImportWizardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun choosingDuplicatesThenImporting() {
        val ev = { id: String, t: Double -> """{"id":"$id","route":"sublingual","timeH":$t,"doseMG":2,"ester":"E2","weightKG":70,"extras":{"sublingualTier":2}}""" }
        val x = HrtTracker.read("""{"meta":{"version":2},"weight":70,"events":[${ev("a", 491000.0)},${ev("b", 491000.0)},${ev("c", 491012.5)}]}""")
        val p = HrtTracker.preview(x); assertEquals(1, p.duplicates)
        var result: HrtTracker.Duplicates? = null; var targets: Map<HrtTracker.Group, Long?>? = null
        rule.setContent { NotesTheme(ThemeMode.LIGHT) { HtImportWizard(x, p, emptyList(), emptyMap(), {}) { d, t, _, _ -> result = d; targets = t } } }
        rule.onNodeWithText("导入").assertIsNotEnabled()
        rule.onNodeWithText("新建药物").performScrollTo().assertIsSelected()
        rule.onNodeWithText("每组只保留一条", substring = true).performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("导入").assertIsEnabled().performClick()
        assertEquals(HrtTracker.Duplicates.MERGE, result); assertEquals(listOf<Long?>(null), targets!!.values.toList())
    }
}
