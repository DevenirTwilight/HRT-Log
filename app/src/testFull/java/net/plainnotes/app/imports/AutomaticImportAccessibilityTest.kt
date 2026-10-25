package net.plainnotes.app.imports

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.R
import net.plainnotes.app.debug.DataPrivacyTestActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Smallest viewport / double text, all four languages, both appearance qualifiers.
 * This validates accessibility semantics, not a claim of physical TalkBack execution. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class AutomaticImportAccessibilityTest(private val locale:String,private val night:String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x/{1}")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr-rFR").flatMap{l->listOf("night","notnight").map{arrayOf(l,it)}}
    }
    private val setup=TestRule {base,_->object:org.junit.runners.model.Statement(){override fun evaluate(){
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-$night-xxhdpi");RuntimeEnvironment.setFontScale(2f);base.evaluate()
    }}}
    private val ui=createAndroidComposeRule<DataPrivacyTestActivity>()
    @get:Rule val chain:RuleChain=RuleChain.outerRule(setup).around(ui)
    @Test fun oneImportActionRemainsReadableAndRestoreIsSeparate() {
        val label=ui.activity.getString(R.string.import_other_apps)
        val action=ui.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction().assertIsEnabled()
        val node=action.fetchSemanticsNode();val density=ui.activity.resources.displayMetrics.density
        assertTrue(node.touchBoundsInRoot.height>=48*density-1)
        val layouts=mutableListOf<TextLayoutResult>()
        ui.onAllNodes(hasText(label),useUnmergedTree=true).fetchSemanticsNodes().forEach {
            it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        }
        assertTrue(layouts.isNotEmpty())
        // Match the established settings audit: Robolectric's aggregate overflow flag has
        // known CJK false positives; verify every actual line and visible character instead.
        layouts.forEach {layout->
            val last=layout.lineCount-1
            assertFalse(layout.isLineEllipsized(last))
            assertEquals(label.length,layout.getLineEnd(last,visibleEnd=false))
            assertTrue("bottom clipping",layout.getLineBottom(last)-layout.size.height<=2*density)
            assertTrue("horizontal clipping",(0..last).maxOf{layout.getLineRight(it)-layout.getLineLeft(it)}-layout.size.width<=density)
        }
        ui.onNodeWithText(ui.activity.getString(R.string.import_ht)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.import_tm)).assertDoesNotExist()
        ui.onNodeWithText(ui.activity.getString(R.string.backup_restore)).performScrollTo().assertHasClickAction()
        ui.runOnUiThread {ui.activity.model.cancelAutomaticImport()}
        ui.onNodeWithText(ui.activity.getString(R.string.import_cancelled)).performScrollTo().assertIsDisplayed()
    }
    @Test fun monaReviewKeepsExplicitConfirmationAndReadableReasons() {
        val model=ui.activity.model
        ui.waitUntil(30000){model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready}
        val export=net.plainnotes.app.importer.Mona.read(java.io.File("../importer/src/test/resources/mona23_synthetic.json").readText())
        val plan=net.plainnotes.app.importer.MonaMigration.plan(export)
        val reviews=plan.doses.map{net.plainnotes.app.data.MonaItemReview("INTAKE",it.sourceId,"",net.plainnotes.app.data.MonaItemStatus.NEW)}+
            plan.labs.map{net.plainnotes.app.data.MonaItemReview("BLOOD_TEST",it.sourceId,it.analyte,net.plainnotes.app.data.MonaItemStatus.NEW)}
        ui.runOnUiThread {model.dataJob.value=net.plainnotes.app.NotesViewModel.DataJob.MonaReady(export,
            net.plainnotes.app.data.MonaImportPreview(plan,reviews),
            (model.librarySession.value as net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready).scope)}
        val label=ui.activity.getString(R.string.mona_confirm)
        val confirm=ui.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
        val density=ui.activity.resources.displayMetrics.density
        assertTrue(confirm.fetchSemanticsNode().touchBoundsInRoot.height>=48*density-1)
        val layouts=mutableListOf<TextLayoutResult>()
        ui.onAllNodes(hasText(label),useUnmergedTree=true).fetchSemanticsNodes().forEach {
            it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {layout->
            val last=layout.lineCount-1
            assertFalse(layout.isLineEllipsized(last));assertEquals(label.length,layout.getLineEnd(last,visibleEnd=false))
            assertTrue(layout.getLineBottom(last)-layout.size.height<=2*density)
            assertTrue((0..last).maxOf{layout.getLineRight(it)-layout.getLineLeft(it)}-layout.size.width<=density)
        }
        ui.onNodeWithText(ui.activity.getString(R.string.cancel)).assertHasClickAction().performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.mona_preview)).assertDoesNotExist()
        assertTrue(model.dataJob.value is net.plainnotes.app.NotesViewModel.DataJob.ImportCancelled)
    }    @Test fun featherlineReviewKeepsExplicitConfirmationAndReadableReasons() {
        val model=ui.activity.model
        ui.waitUntil(30000){model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready}
        val export=net.plainnotes.app.importer.Featherline.read(java.io.File("../core/data/src/test/resources/featherline/clinical-s7.json").readText())
        val reviews=export.doses.map{net.plainnotes.app.data.FeatherlineItemReview("LOG",it.id,net.plainnotes.app.data.FeatherlineItemStatus.NEW)}+
            export.labs.map{net.plainnotes.app.data.FeatherlineItemReview("RESULT",it.id,net.plainnotes.app.data.FeatherlineItemStatus.NEW)}
        ui.runOnUiThread {model.dataJob.value=net.plainnotes.app.NotesViewModel.DataJob.FeatherlineReady(
            net.plainnotes.app.data.FeatherlineImportPreview(export,reviews),
            (model.librarySession.value as net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready).scope)}
        val label=ui.activity.getString(R.string.featherline_confirm)
        val confirm=ui.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
        val density=ui.activity.resources.displayMetrics.density
        assertTrue(confirm.fetchSemanticsNode().touchBoundsInRoot.height>=48*density-1)
        val layouts=mutableListOf<TextLayoutResult>()
        ui.onAllNodes(hasText(label),useUnmergedTree=true).fetchSemanticsNodes().forEach {
            it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {layout->
            val last=layout.lineCount-1
            assertFalse(layout.isLineEllipsized(last));assertEquals(label.length,layout.getLineEnd(last,visibleEnd=false))
            assertTrue(layout.getLineBottom(last)-layout.size.height<=2*density)
            assertTrue((0..last).maxOf{layout.getLineRight(it)-layout.getLineLeft(it)}-layout.size.width<=density)
        }
        ui.onNodeWithText(ui.activity.getString(R.string.cancel)).assertHasClickAction().performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.featherline_preview)).assertDoesNotExist()
        assertTrue(model.dataJob.value is net.plainnotes.app.NotesViewModel.DataJob.ImportCancelled)
    }
    @Test fun featherlinePasswordHasAccessibleControlsAndCancellation() {
        val model=ui.activity.model
        ui.waitUntil(30000){model.librarySession.value is net.plainnotes.app.runtime.RuntimeLibrarySession.State.Ready}
        val file=java.io.File.createTempFile("synthetic-featherline-ui-",".json",ui.activity.cacheDir)
        try {
            file.writeBytes(java.io.File("../core/data/src/test/resources/featherline/clinical-v3.cipher").readBytes())
            ui.runOnUiThread{model.openImport(android.net.Uri.fromFile(file))}
            ui.waitUntil(30000){
                // This host fixture has a paused Android looper; explicitly drain the
                // real dispatcher return just as the scoped import flow tests do.
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                model.dataJob.value is net.plainnotes.app.NotesViewModel.DataJob.FeatherlinePassword || model.dataJob.value is net.plainnotes.app.NotesViewModel.DataJob.Failed
            }
            assertTrue(model.dataJob.value is net.plainnotes.app.NotesViewModel.DataJob.FeatherlinePassword)
            val confirm=ui.onNodeWithText(ui.activity.getString(R.string.featherline_unlock)).assertIsDisplayed().assertHasClickAction()
            assertTrue(confirm.fetchSemanticsNode().touchBoundsInRoot.height>=48*ui.activity.resources.displayMetrics.density-1)
            ui.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed().assertHasClickAction()
            ui.onNodeWithText(ui.activity.getString(R.string.cancel)).assertIsDisplayed().performClick()
            assertTrue(model.dataJob.value is net.plainnotes.app.NotesViewModel.DataJob.ImportCancelled)
        }finally{file.delete()}
    }

}
