package net.plainnotes.app.settings

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.app.R
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale
import java.io.File

/** Real AndroidKeyStore PIN, not an enabled flag or fake lock.bin fixture. */
@RunWith(Parameterized::class)
class SettingsButtonsAndroidTest(private val locale:String,private val width:Int,private val scale:Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}/{1}dp/{2}")
        fun params()=listOf("en","zh-CN","zh-TW","fr").flatMap{l->listOf(320,411).flatMap{w->listOf(1f,1.3f,2f).map{s->arrayOf<Any>(l,w,s)}}}
    }
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun enableRealLock(){AppLock(ctx).disable();AppLock(ctx).setPin("8642");assertTrue(AppLock(ctx).enabled);assertTrue(AppLock(ctx).matches("8642"));UiPrefs(ctx).lockAfterMillis=30_000L}
    @After fun cleanup(){AppLock(ctx).disable();UiPrefs(ctx).lockAfterMillis=30_000L;UiPrefs(ctx).appearance=Appearance(ThemeMode.SYSTEM,false)}

    @Test fun allSettingsChoicesRemainButtonsWithALockActuallyEnabled() {
        val before=File(ctx.noBackupFilesDir,"lock.bin").readBytes()
        val configuration=Configuration(ui.activity.resources.configuration).apply{setLocales(LocaleList(Locale.forLanguageTag(this@SettingsButtonsAndroidTest.locale)));fontScale=scale}
        val localized=ui.activity.createConfigurationContext(configuration)
        val prefs=UiPrefs(ctx);prefs.appearance=Appearance(ThemeMode.SYSTEM,true,Contrast.STANDARD)
        var appearance by mutableStateOf(prefs.appearance)
        val density=ui.activity.resources.displayMetrics.widthPixels/width.toFloat()
        ui.setContent{CompositionLocalProvider(LocalActivityResultRegistryOwner provides ui.activity,LocalContext provides localized,LocalConfiguration provides configuration,LocalDensity provides Density(density,scale)){
            NotesTheme(ThemeMode.LIGHT,contrast=appearance.contrast){Surface(Modifier.fillMaxSize().testTag("settings-viewport")){
                SettingsScreen(appearance,{appearance=it;prefs.appearance=it},false,{},{},{},PaddingValues()){PrivacySection(1,false){}}
            }}
        }}
        assertEquals(width.toFloat(),ui.onNodeWithTag("settings-viewport").fetchSemanticsNode().size.width/density,1f)
        fun choice(label:String)=ui.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        fun group(ids:List<Int>,persist:(Int)->Unit) {
            val labels=ids.map(localized::getString)
            val nodes=labels.map{choice(it).assertHasClickAction().assertIsEnabled().fetchSemanticsNode()}
            val inline=nodes.maxOf{it.positionInRoot.y}-nodes.minOf{it.positionInRoot.y}<1f
            nodes.forEach{assertEquals(if(inline)Role.RadioButton else Role.Button,it.config[SemanticsProperties.Role])}
            assertTrue(nodes.maxOf{it.size.width}-nodes.minOf{it.size.width}<=1)
            assertTrue(nodes.maxOf{it.size.height}-nodes.minOf{it.size.height}<=1)
            labels.forEachIndexed{i,label->
                val option=choice(label).performScrollTo().assertIsDisplayed()
                val n=option.fetchSemanticsNode();assertTrue(n.touchBoundsInRoot.width>=48*density-1);assertTrue(n.touchBoundsInRoot.height>=48*density-1)
                val layouts=mutableListOf<TextLayoutResult>()
                ui.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Text,listOf(androidx.compose.ui.text.AnnotatedString(label))),useUnmergedTree=true).fetchSemanticsNodes().forEach{it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)}
                assertTrue(layouts.isNotEmpty());layouts.forEach{r->val last=r.lineCount-1
                    assertFalse("clipped $label",r.isLineEllipsized(last) || r.getLineEnd(last,visibleEnd=false)<label.length ||
                        r.getLineBottom(last)-r.size.height>2*density || (0..last).maxOf{r.getLineRight(it)-r.getLineLeft(it)}-r.size.width>density)
                }
                option.performClick().assertIsSelected()
                labels.forEachIndexed{j,l->if(i!=j)choice(l).assertIsNotSelected()}
                ui.onAllNodes(hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) and (hasText(labels[0]) or hasText(labels[1]) or hasText(labels[2]))).assertCountEquals(3)
                ui.runOnIdle{persist(i)}
            }
        }
        group(listOf(R.string.theme_system,R.string.theme_light,R.string.theme_dark)){assertEquals(ThemeMode.entries[it],UiPrefs(ctx).appearance.mode);assertTrue(UiPrefs(ctx).appearance.dynamic)}
        group(listOf(R.string.contrast_low,R.string.contrast_medium,R.string.contrast_high)){assertEquals(Contrast.entries[it],UiPrefs(ctx).appearance.contrast);assertEquals(ThemeMode.DARK,UiPrefs(ctx).appearance.mode)}
        group(listOf(R.string.lock_after_now,R.string.lock_after_30s,R.string.lock_after_5m)){assertEquals(listOf(0L,30_000L,300_000L)[it],UiPrefs(ctx).lockAfterMillis)}
        ui.onNodeWithText(localized.getString(R.string.lock_enable)).performScrollTo().assertIsOn()
        ui.onNodeWithText(localized.getString(R.string.lock_change_pin)).assertHasClickAction()
        assertTrue(AppLock(ctx).matches("8642"));assertArrayEquals(before,File(ctx.noBackupFilesDir,"lock.bin").readBytes())
    }
}
