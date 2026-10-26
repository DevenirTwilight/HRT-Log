package net.plainnotes.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.getOrNull
import net.plainnotes.app.NotesState
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.*
import net.plainnotes.app.visit.VisitPrepLayout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Sample 08 selection (REQUIREMENTS §150): same defaults, honest tags, live count and contents, four languages, 320 dp/2× and 411 dp/1×. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class VisitPrepDialogTest(private val locale:String,private val width:Int,private val font:Float) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr").flatMap {l->listOf(arrayOf<Any>(l,320,2f),arrayOf<Any>(l,411,1f))}
    }
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val c get()=ui.activity
    private val zone=ZoneId.systemDefault();private val today=LocalDate.now()
    private fun at(d:LocalDate)=d.atTime(9,0).atZone(zone).toInstant().toEpochMilli()

    @Test fun selectionKeepsDefaultsAndShowsCountTagsAndContents() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h${if(width==320)640 else 891}dp-xxhdpi");RuntimeEnvironment.setFontScale(font)
        val previous=AppointmentEntity(1,"ENDO",at(today.minusDays(30)),zone.id,remind_minutes_before=60,completed_utc=1L)
        val current=AppointmentEntity(2,"ENDO",at(today),zone.id,remind_minutes_before=60)
        val extra=NotesViewModel.ExtraState(visitQuestions=listOf(VisitQuestionEntity(1,2,0,"Synthetic question one"),VisitQuestionEntity(2,2,1,"Synthetic question two"),VisitQuestionEntity(3,1,0,"Other visit")))
        ui.setContent {NotesTheme {VisitPackDialog(current,NotesState(appointments=listOf(previous,current),loading=false),extra,{}) {_,_->}}}
        ui.onNode(isDialog()).assertExists()
        ui.onNodeWithText(c.getString(R.string.visit_prep_title)).assertExists()
        ui.onNodeWithTag("export-privacy-notice").assertExists()
        fun box(s:VisitSection)=ui.onNodeWithTag("visit-prep:${s.name}")
        VisitSection.entries.forEach {if(it.defaultOn)box(it).assertIsOn() else box(it).assertIsOff()}
        // Count and contents follow the real selection.
        val defaults=VisitSection.entries.count {it.defaultOn}
        ui.onNodeWithTag("visit-prep-count").assertTextEquals(c.resources.getQuantityString(R.plurals.visit_prep_selected,defaults,defaults))
        fun label(s:VisitSection)=c.getString(sectionLabel(s))
        VisitPrepLayout.contents(VisitSection.entries.filter {it.defaultOn}.toSet()).forEachIndexed {i,s->ui.onNodeWithText("%02d  %s".format(i+1,label(s))).assertExists()}
        // Questions count only this visit's questions; the "off by default" tag sits on exactly the off-by-default parts.
        ui.onNodeWithText(c.resources.getQuantityString(R.plurals.visit_prep_questions,2,2)).assertExists()
        assertEquals(2,ui.onAllNodesWithText(c.getString(R.string.visit_prep_tag_default_off),useUnmergedTree=true).fetchSemanticsNodes().size)
        box(VisitSection.PACKAGES).performScrollTo().performClick();box(VisitSection.PACKAGES).assertIsOn()
        ui.onNodeWithTag("visit-prep-count").assertTextEquals(c.resources.getQuantityString(R.plurals.visit_prep_selected,defaults+1,defaults+1))
        ui.onNodeWithText("%02d  %s".format(defaults+1,label(VisitSection.PACKAGES))).assertExists()
        VisitSection.entries.forEach {if(box(it).fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState)==androidx.compose.ui.state.ToggleableState.On)box(it).performScrollTo().performClick()}
        ui.onNodeWithText(c.getString(R.string.visit_no_section)).assertExists()
        ui.onNodeWithTag("visit-prep-toc").assertDoesNotExist()
        ui.onNodeWithText(c.getString(R.string.export_pdf)).assertIsNotEnabled()
        ui.onNodeWithText(c.getString(R.string.visit_prep_external)).assertExists()
        System.getenv("VISIT_PREP_SHOTS")?.takeIf {it.isNotBlank()}?.let {dir->
            box(VisitSection.FACTS).performScrollTo().performClick();ui.onAllNodes(isToggleable()).onFirst().performScrollTo();ui.waitForIdle()
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(image))
            File("build/visit-prep-shots/$dir").apply {mkdirs()}.resolve("visit_prep_${locale}_${width}dp_${if(font==1f)"1x" else "2x"}.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
}
