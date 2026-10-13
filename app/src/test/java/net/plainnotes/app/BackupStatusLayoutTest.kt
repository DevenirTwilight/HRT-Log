package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import net.plainnotes.app.ui.BackupStatus
import net.plainnotes.app.privacy.BackupHistory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class BackupStatusLayoutTest(private val locale:String,private val scale:Float,private val width:Int,private val dark:Boolean) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}@{1}/{2}/dark={3}")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr-rFR").flatMap{l->listOf(1f,1.3f,2f).flatMap{s->listOf(320,411).flatMap{w->listOf(false,true).map{d->arrayOf<Any>(l,s,w,d)}}}}}
    private val setup=TestRule{base,_->object:org.junit.runners.model.Statement(){override fun evaluate(){RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h640dp-xxhdpi");RuntimeEnvironment.setFontScale(scale);base.evaluate()}}}
    private val ui=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain=RuleChain.outerRule(setup).around(ui)
    @Test fun metadataReminderAndActionsFitEveryLocaleAndSize(){
        val prefs=ui.activity.getSharedPreferences("b2-synthetic-ui-history",0)
        val time=Instant.parse("2026-01-01T10:15:00Z")
        prefs.edit().clear().putLong(BackupHistory.LAST,time.toEpochMilli()).commit()
        val history=BackupHistory(prefs,Clock.fixed(time.plusSeconds(31*86400),ZoneOffset.UTC))
        history.configure(true,14)
        ui.setContent{MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()){
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)){BackupStatus(history)}
        }}
        ui.onNodeWithTag("backup-history-last").assertExists()
        val last=ui.onNodeWithTag("backup-history-last").fetchSemanticsNode()
        val layouts=mutableListOf<TextLayoutResult>()
        last.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(layouts)
        assertTrue("The local date must include the year",layouts.single().layoutInput.text.text.contains("2026"))
        ui.onNodeWithTag("backup-reminder-due").assertExists()
        ui.onAllNodes(hasText("",substring=true),useUnmergedTree=true).fetchSemanticsNodes().forEach { node ->
            val results=mutableListOf<TextLayoutResult>();node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach { r ->
                val end=r.lineCount-1
                assertEquals(r.layoutInput.text.length,r.getLineEnd(end,false))
                assertTrue((0..end).none{r.isLineEllipsized(it)})
                assertTrue((0..end).all{r.getLineRight(it)-r.getLineLeft(it)<=r.size.width})
                assertTrue(r.getLineBottom(end)<=r.size.height)
            }
        }
        ui.onNodeWithText(ui.activity.getString(R.string.backup_reminder_days,7)).performScrollTo().performClick()
        assertEquals(7,history.state.value.days)
        ui.onNodeWithText(ui.activity.getString(R.string.backup_reminder_title)).performScrollTo().performClick()
        assertFalse(history.state.value.reminderEnabled)
        ui.onNodeWithTag("backup-reminder-due").assertDoesNotExist()
        prefs.edit().clear().commit()
    }
}
