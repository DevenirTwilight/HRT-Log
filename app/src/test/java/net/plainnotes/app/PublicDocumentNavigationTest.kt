package net.plainnotes.app

import android.content.ActivityNotFoundException
import android.content.Intent
import net.plainnotes.app.reference.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class PublicDocumentNavigationTest {
    private val url="https://base-donnees-publique.medicaments.gouv.fr/medicament/00000001/extrait"
    @Test fun onlyExplicitAuthorizedActionCreatesPublicViewIntentWithoutHealthExtras() {
        var captured:Intent?=null
        assertEquals(DocumentOpenResult.OPENED,PublicDocumentNavigation.openChecked(url,"00000001",9,true,{it==9L}){captured=it})
        val intent=requireNotNull(captured);assertEquals(Intent.ACTION_VIEW,intent.action);assertEquals(url,intent.dataString)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE),intent.categories);assertNull(intent.extras);assertNull(intent.clipData);assertNull(intent.component);assertNull(intent.`package`);assertEquals(0,intent.flags)
    }
    @Test fun backgroundLockedStalePrivateAndMissingGestureNeverLaunch() {
        val forbidden:(Intent)->Unit={fail("Unauthorized external intent")}
        assertEquals(DocumentOpenResult.NOT_AUTHORIZED,PublicDocumentNavigation.openChecked(url,"00000001",9,false,{true},forbidden))
        assertEquals(DocumentOpenResult.NOT_AUTHORIZED,PublicDocumentNavigation.openChecked(url,"00000001",null,true,{true},forbidden))
        assertEquals(DocumentOpenResult.NOT_AUTHORIZED,PublicDocumentNavigation.openChecked(url,"00000001",9,true,{false},forbidden))
        assertEquals(DocumentOpenResult.INVALID_SOURCE,PublicDocumentNavigation.openChecked(url+"?record=PRIVATE","00000001",9,true,{true},forbidden))
    }
    @Test fun missingBrowserOrActivitySecurityFailureHasRecoverableResult() {
        for(error in listOf(ActivityNotFoundException("SYNTHETIC missing browser"),SecurityException("SYNTHETIC denied"))) {
            assertEquals(DocumentOpenResult.BROWSER_UNAVAILABLE,PublicDocumentNavigation.openChecked(url,"00000001",9,true,{true}){throw error})
        }
    }
}
