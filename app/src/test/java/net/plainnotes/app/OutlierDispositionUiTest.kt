package net.plainnotes.app
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.conc.*
import net.plainnotes.app.pk.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk=[35],application=android.app.Application::class)
class OutlierDispositionUiTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 private fun check(){
  val all=DispositionFixture.run(DispositionFixture.single);val partial=DispositionFixture.run(DispositionFixture.partial)
  var scale by mutableFloatStateOf(1f);var dark by mutableStateOf(false);var showPartial by mutableStateOf(false)
  var opens=0
  ui.setContent{val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,scale)){MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){CalibrationCard(if(showPartial)partial else all,ConcSettings(false,true,CalibrationMode.CAUSAL),{}, {opens++},{"%.0f".format(java.util.Locale.US,it)},"pg/mL")}}}}
  for(font in listOf(1f,1.3f,2f))for(theme in listOf(false,true)){
   ui.runOnIdle{scale=font;dark=theme;showPartial=false};ui.waitForIdle()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_fit_counts,1,1,0)).assertExists()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_warning_used)).assertExists()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_warning_used)).performScrollTo()
   val details=ui.onNodeWithText(ui.activity.getString(R.string.calib_history_details));details.assertHasClickAction()
   val layouts=mutableListOf<TextLayoutResult>()
   ui.onAllNodes(hasText(ui.activity.getString(R.string.calib_warning_used)),useUnmergedTree=true).fetchSemanticsNodes().forEach{it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)}
   Assert.assertTrue("Visible warning must expose text layout",layouts.isNotEmpty())
   // TextLayoutResult's integer size rounds physical pixels; inspect actual glyph bounds with 1px tolerance.
   layouts.forEach { layout ->
    for(i in 0 until layout.lineCount){Assert.assertFalse(layout.isLineEllipsized(i));Assert.assertTrue(layout.getLineRight(i)<=layout.size.width+1f);Assert.assertTrue(layout.getLineLeft(i)>=-1f)}
    Assert.assertTrue(layout.getLineBottom(layout.lineCount-1)<=layout.size.height+1f)
    Assert.assertEquals(layout.layoutInput.text.length,layout.getLineEnd(layout.lineCount-1,visibleEnd=true))
   }
   details.performScrollTo().assertHasClickAction()
   Assert.assertTrue(details.fetchSemanticsNode().boundsInRoot.height/ui.activity.resources.displayMetrics.density>=48f-.1f)
   details.performClick()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_residual_warning)).assertExists()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_fit_used)).assertExists()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_fit_excluded)).assertDoesNotExist()
   ui.onNodeWithText("200 pg/mL").assertExists()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_history_done)).performClick()
   ui.runOnIdle{showPartial=true};ui.waitForIdle()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_fit_counts,4,3,1)).assertExists()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_warning_used)).assertDoesNotExist()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_history_details)).performScrollTo().performClick()
   ui.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText(ui.activity.getString(R.string.calib_fit_excluded)))
   ui.onNodeWithText(ui.activity.getString(R.string.calib_fit_excluded)).assertExists()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_residual_warning)).assertExists()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_history_done)).performClick()
   ui.onNodeWithText(ui.activity.getString(R.string.calib_manage_labs)).performScrollTo().assertHasClickAction().performClick()
  }
  Assert.assertEquals(6,opens)
 }
 @Test @Config(qualifiers="en-rUS-w320dp-h891dp-xxhdpi") fun english320()=check()
 @Test @Config(qualifiers="en-rUS-w411dp-h891dp-xxhdpi") fun english411()=check()
 @Test @Config(qualifiers="zh-rCN-w320dp-h891dp-xxhdpi") fun simplified320()=check()
 @Test @Config(qualifiers="zh-rCN-w411dp-h891dp-xxhdpi") fun simplified411()=check()
 @Test @Config(qualifiers="b+zh+Hant-w320dp-h891dp-xxhdpi") fun traditional320()=check()
 @Test @Config(qualifiers="b+zh+Hant-w411dp-h891dp-xxhdpi") fun traditional411()=check()
 @Test @Config(qualifiers="fr-rFR-w320dp-h891dp-xxhdpi") fun french320()=check()
 @Test @Config(qualifiers="fr-rFR-w411dp-h891dp-xxhdpi") fun french411()=check()
}
