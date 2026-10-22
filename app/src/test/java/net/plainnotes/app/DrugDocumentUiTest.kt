package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.UserReferenceProduct
import net.plainnotes.app.reference.DocumentOpenResult
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrugDocumentUiTest(private val locale:String) {
    companion object {@JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/320dp/2x") fun locales()=listOf("en","fr-rFR","zh-rCN","zh-rTW").map{arrayOf<Any>(it)}}
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val product=UserReferenceProduct("SYNTHETIC source page",cis="00000001",sourceUrl="https://base-donnees-publique.medicaments.gouv.fr/medicament/00000001/extrait",sourceVersion="synthetic-v1",recordedOn="2026-10-17")
    @Before fun setup(){RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xxhdpi");RuntimeEnvironment.setFontScale(2f)}
    @Test fun explicitConfirmCancelAndBrowserFailureRemainRecoverable() {
        val launched=ArrayList<String>()
        ui.setContent{NotesTheme(ThemeMode.DARK){Surface {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DrugDocumentPanel(product)
            PublicProductPageAction(product){url,_,_,gesture->assertTrue(gesture);launched.add(url);DocumentOpenResult.BROWSER_UNAVAILABLE}
        }}}}
        ui.onNodeWithTag("drug-document:RCP").assertExists();ui.onNodeWithTag("drug-document:NOTICE").assertExists()
        assertTrue(launched.isEmpty())
        ui.onNodeWithTag("document-public-page").performScrollTo().performClick()
        ui.onNodeWithTag("document-open-cancel").performClick();assertTrue(launched.isEmpty())
        ui.onNodeWithTag("document-public-page").performScrollTo().performClick()
        ui.onNodeWithTag("document-open-confirm").performClick();assertEquals(listOf(product.sourceUrl),launched)
        ui.onNodeWithTag("document-open-error").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("document-public-page").performScrollTo().assertIsEnabled()
    }
    @Test fun malformedSourceNeverOffersAConfirmOrInvokesBrowser() {
        ui.setContent{NotesTheme(ThemeMode.LIGHT){Surface{PublicProductPageAction(product.copy(sourceUrl=product.sourceUrl+"?private=1")){_,_,_,_->fail("Untrusted source launched");DocumentOpenResult.OPENED}}}}
        ui.onNodeWithTag("document-invalid-link").assertIsDisplayed()
        ui.onNodeWithTag("document-public-page").assertDoesNotExist();ui.onNodeWithTag("document-open-confirm").assertDoesNotExist()
    }
    @Test fun defaultNavigationRejectsAContextWithoutLiveMainActivityAuthorization() {
        ui.setContent{NotesTheme(ThemeMode.LIGHT){Surface{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {PublicProductPageAction(product)}}}}
        ui.onNodeWithTag("document-public-page").performScrollTo().performClick()
        ui.onNodeWithTag("document-open-confirm").performClick()
        ui.onNodeWithTag("document-open-error").performScrollTo().assertIsDisplayed()
        assertNull(org.robolectric.Shadows.shadowOf(ui.activity).nextStartedActivity)
    }

}
