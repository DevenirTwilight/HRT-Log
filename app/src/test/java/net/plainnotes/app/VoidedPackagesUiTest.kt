package net.plainnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.plainnotes.app.data.*
import net.plainnotes.app.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoidedPackagesUiTest(private val locale:String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}") fun locales()=listOf(arrayOf("en-rUS"),arrayOf("zh-rCN"),arrayOf("b+zh+Hant"),arrayOf("fr-rFR"))
    }
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val med=MedicationEntity(1,"Synthetic", "E2","ORAL","MG",2.0,30.0,site_rotation=false,notifications_on=true,active=true,sort_order=0)
    private val hidden=ContainerEntity(3,1,30.0,0.0,28.0,"2026-01-01","VOIDED",source_note="VOID_SENTINEL")
    @Before fun configure(){RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xxhdpi");RuntimeEnvironment.setFontScale(2f)}
    private fun text(id:Int,vararg args:Any)=ui.activity.getString(id,*args)

    @Test fun normalStockCountExcludesVoided() {
        val real=ContainerEntity(1,1,30.0,0.0,10.0,"2026-01-01","IN_USE")
        val sealed=ContainerEntity(2,1,30.0,0.0,0.0,null,"SEALED")
        ui.setContent{NotesTheme(ThemeMode.LIGHT){StockScreen(NotesState(listOf(med),loading=false),listOf(real,sealed,hidden),emptyList(),{},{},{},PaddingValues())}}
        // UI-01 D: the section header counts the same packages; management opens from inside it.
        ui.onNodeWithTag("stock-packages-1").performScrollTo().assertTextContains(text(R.string.stock_packages_section,2)).performClick()
        ui.onNodeWithText(text(R.string.stock_packages_section,3)).assertDoesNotExist()
        ui.onNodeWithTag("stock-manage-1").performScrollTo().assertTextContains(text(R.string.stock_manage,2))
        ui.onNodeWithText(text(R.string.stock_manage,3)).assertDoesNotExist()
        ui.onNodeWithText("VOID_SENTINEL",substring=true).assertDoesNotExist()
    }
    @Test fun onlyVoidedIsAnUntrackedEmptyStateWithAdvancedAccess() {
        ui.setContent{NotesTheme(ThemeMode.LIGHT){StockScreen(NotesState(listOf(med),loading=false),listOf(hidden,hidden.copy(id=4),hidden.copy(id=5)),emptyList(),{},{},{},PaddingValues())}}
        ui.onNodeWithText(text(R.string.stock_not_tracked)).assertExists()
        ui.onNodeWithText(text(R.string.stock_no_active)).assertDoesNotExist()
        ui.onNodeWithTag("stock-check-1").assertDoesNotExist()
        ui.onNodeWithTag("stock-manage-1").assertTextContains(text(R.string.stock_manage_plain))
    }
    @Test fun hiddenRecordsAppearOnlyAfterExplicitAdvancedAction() {
        var restored:Long?=null
        ui.setContent{NotesTheme(ThemeMode.LIGHT){PackagesDialog(med,listOf(hidden),emptySet(),{},{},voided=listOf(hidden),onRestore={restored=it.id},onUndo={fail("No voided package in normal groups")})}}
        ui.onNodeWithTag("voided-package-3").assertDoesNotExist()
        ui.onNodeWithText("VOID_SENTINEL",substring=true).assertDoesNotExist()
        ui.onNodeWithTag("view-voided").performScrollTo().performClick()
        ui.onNodeWithTag("voided-package-3").assertExists()
        assertNull(restored)
        ui.onNodeWithTag("restore-package-3").performScrollTo().performClick()
        assertEquals(3L,restored)
    }
    @Test fun voidNeedsSecondConfirmation() {
        var confirmed=0
        ui.setContent{NotesTheme(ThemeMode.LIGHT){PackageCorrectionConfirmation(false,{},label="Synthetic · 2 mg / 30 mg",onConfirm={confirmed++})}}
        assertEquals(0,confirmed)
        ui.onNodeWithText("Synthetic · 2 mg / 30 mg").assertExists()
        ui.onNodeWithText(text(R.string.pkg_undo_body)).assertExists()
        val node=ui.onNodeWithTag("confirm-package-correction").fetchSemanticsNode()
        assertTrue(node.boundsInRoot.height >= 48*ui.activity.resources.displayMetrics.density-1)
        ui.onNodeWithTag("confirm-package-correction").performClick();assertEquals(1,confirmed)
    }
    @Test fun restoreCancellationDoesNotSubmit() {
        var confirmed=0;var dismissed=0
        ui.setContent{NotesTheme(ThemeMode.LIGHT){PackageCorrectionConfirmation(true,{dismissed++},onConfirm={confirmed++})}}
        ui.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(0,confirmed);assertEquals(1,dismissed)
    }
}
