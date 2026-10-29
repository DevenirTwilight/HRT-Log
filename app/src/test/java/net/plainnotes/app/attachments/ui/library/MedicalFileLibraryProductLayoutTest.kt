package net.plainnotes.app.attachments.ui.library

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.*
import net.plainnotes.app.data.MedicalAttachmentOperations as Ops
import net.plainnotes.app.ui.NotesTheme
import net.plainnotes.app.ui.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** Production content/controller with scripted metadata only; screenshots are Compose, not APK/native acceptance. */
@RunWith(ParameterizedRobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=android.app.Application::class)
class MedicalFileLibraryProductLayoutTest(private val locale:String,private val width:Int,private val font:Float,private val dark:Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}dp/{2}x/dark={3}")
        fun params()=listOf("en","zh-rCN","zh-rTW","fr").flatMap {l->listOf(arrayOf<Any>(l,320,2f,false),arrayOf<Any>(l,320,2f,true),arrayOf<Any>(l,411,1f,false),arrayOf<Any>(l,411,1f,true))}
    }
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun actualProductProjectionSearchAndStoredTypeFiltersRemainUsable() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h891dp-xxhdpi");RuntimeEnvironment.setFontScale(font)
        val pdf=Ops.Document(UUID.randomUUID(),1,"SYNTHETIC report","application/pdf","ACTIVE","ACTIVE",1,1024,1000)
        val image=Ops.Document(UUID.randomUUID(),1,"SYNTHETIC photo","image/png","ACTIVE","ACTIVE",0,512,2000)
        val gateway=MedicalFileLibraryControllerTest.Gateway().apply {docs=listOf(pdf,image)}
        val controller=MedicalFileLibraryController(gateway,CoroutineScope(Dispatchers.Unconfined),Dispatchers.Unconfined)
        controller.refresh()
        var add=0
        try {
            ui.setContent {NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT) {
                MedicalFileLibraryContent(controller,{add++},emptyList(),emptyList(),emptyList(),{}, {}, {}, {}, {}, {_,done->done(null)})
            }}
            ui.onNodeWithTag("library-add").assertIsEnabled().performClick();assertEquals(1,add)
            ui.onNodeWithTag("library-search").assertIsEnabled()
            val view=ui.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap));val directory=File("build/reports/med03-product-shots").apply {mkdirs()}
                directory.resolve("library_${locale}_${width}dp_${font}x_${if(dark)"dark" else "light"}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }finally {bitmap.recycle()}
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-type:PDF"))
            ui.onNodeWithTag("library-type:PDF").assertHasClickAction().performClick()
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${pdf.document}"))
            ui.onNodeWithTag("library-doc:${pdf.document}").assertExists()
            ui.onAllNodesWithTag("library-doc:${image.document}").assertCountEquals(0)
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-search"))
            ui.onNodeWithTag("library-search").performTextInput("no matching synthetic record")
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-empty"));ui.onNodeWithTag("library-empty").assertExists()
        }finally {controller.close()}
    }

    /** MED-04 through the real host: tiles and filters come from what the user typed, editing writes through the controller, nothing is inferred. */
    @Test fun actualHostFiltersByTypedCategorySearchesInstitutionAndEditsThroughTheController() {
        RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h891dp-xxhdpi");RuntimeEnvironment.setFontScale(font)
        val pdf=Ops.Document(UUID.randomUUID(),1,"SYNTHETIC report","application/pdf","ACTIVE","ACTIVE",1,1024,1000)
        val image=Ops.Document(UUID.randomUUID(),1,"SYNTHETIC photo","image/png","ACTIVE","ACTIVE",0,512,2000)
        val gateway=MedicalFileLibraryControllerTest.Gateway().apply {docs=listOf(pdf,image);stored=mapOf(pdf.document to Ops.Detail("LAB_REPORT","2026-10-07","SYNTHETIC Clinic",1))}
        val controller=MedicalFileLibraryController(gateway,CoroutineScope(Dispatchers.Unconfined),Dispatchers.Unconfined)
        controller.refresh()
        fun s(id:Int,vararg a:Any)=ui.activity.getString(id,*a)
        try {
            ui.setContent {NotesTheme(if(dark)ThemeMode.DARK else ThemeMode.LIGHT) {
                MedicalFileLibraryContent(controller,{},emptyList(),emptyList(),emptyList(),{}, {}, {}, {}, {}, {_,done->done(null)})
            }}
            // Only the typed category has a tile with files; the file with nothing typed is the one that still needs sorting.
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-category:LAB_REPORT"))
            ui.onNodeWithTag("library-category:LAB_REPORT").assertIsEnabled()
            ui.onNodeWithTag("library-category:PRESCRIPTION").assertIsNotEnabled()
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-unsorted"))
            ui.onNodeWithTag("library-unsorted").assert(hasText(ui.activity.resources.getQuantityString(net.plainnotes.app.R.plurals.med04_to_sort_body,1,1),substring=true))
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${pdf.document}"))
            ui.onAllNodes(hasText("SYNTHETIC Clinic",substring=true) and hasAnyAncestor(hasTestTag("library-doc:${pdf.document}"))).assertCountEquals(1)
            ui.onAllNodes(hasText(s(net.plainnotes.app.R.string.med04_date_unknown),substring=true) and hasAnyAncestor(hasTestTag("library-doc:${image.document}"))).assertCountEquals(1)
            // Filter by the tile, clear, then search by institution and by category name.
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-category:LAB_REPORT"))
            ui.onNodeWithTag("library-category:LAB_REPORT").performClick()
            ui.onNodeWithTag("library-clear-filter").assertExists()
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${pdf.document}"))
            ui.onAllNodesWithTag("library-doc:${image.document}").assertCountEquals(0)
            ui.onNodeWithTag("library-clear-filter").performClick()
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-search"))
            ui.onNodeWithTag("library-search").performTextInput("clinic")
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${pdf.document}"))
            ui.onAllNodesWithTag("library-doc:${image.document}").assertCountEquals(0)
            ui.onNodeWithTag("library-search").performTextReplacement(s(net.plainnotes.app.R.string.lib_cat_lab))
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${pdf.document}"))
            ui.onAllNodesWithTag("library-doc:${image.document}").assertCountEquals(0)
            ui.onNodeWithTag("library-search").performTextReplacement("")
            // Edit the untyped file through the production editor.
            ui.onNodeWithTag("library").performScrollToNode(hasTestTag("library-doc:${image.document}"))
            ui.onNodeWithTag("library-doc:${image.document}").performClick()
            ui.onNodeWithTag("file-detail-edit-info").performScrollTo().performClick()
            ui.onNodeWithTag("file-info-category:PRESCRIPTION").performScrollTo().performClick()
            ui.onNodeWithTag("file-info-institution").performScrollTo().performTextReplacement("SYNTHETIC Pharmacy")
            ui.onNodeWithTag("file-info-save").performScrollTo().performClick()
            ui.waitForIdle()
            assertEquals(Ops.Detail("PRESCRIPTION",null,"SYNTHETIC Pharmacy",1),gateway.stored[image.document])
            assertEquals(MedicalFileLibraryController.DetailOutcome.SAVED,controller.state.value.detailOutcome)
            ui.onNodeWithTag("library-detail-result").assertExists()
            ui.onNode(hasText("SYNTHETIC Pharmacy") and hasAnyAncestor(hasTestTag("file-detail-facts"))).performScrollTo().assertExists()
            // The original's own revision never moved.
            assertEquals(1L,controller.state.value.documents.first {it.document==image.document}.revision)
            // An open editor keeps its own draft even if a later list refresh sees another writer.
            // Cover both a changed document title/revision and a detail-only concurrent change.
            for(changeTitle in listOf(true,false)) {
                val frozenDocument=controller.state.value.documents.single {it.document==image.document}
                val frozenDetail=controller.state.value.details.getValue(image.document)
                ui.onNodeWithTag("file-detail-edit-info").performScrollTo().performClick()
                ui.onNodeWithTag("file-info-title").assert(hasText(frozenDocument.title))
                ui.onNodeWithTag("file-info-title").performTextReplacement("SYNTHETIC drafted name")
                val currentDocument=if(changeTitle)frozenDocument.copy(title="SYNTHETIC concurrent name",revision=frozenDocument.revision+1)else frozenDocument
                val currentDetail=frozenDetail.copy(institution="SYNTHETIC concurrent clinic",revision=frozenDetail.revision+1)
                ui.runOnIdle {
                    gateway.docs=gateway.docs.map {if(it.document==image.document)currentDocument else it}
                    gateway.stored=gateway.stored+(image.document to currentDetail)
                    controller.refresh()
                }
                ui.waitForIdle()
                ui.onNodeWithTag("file-info-title").assert(hasText("SYNTHETIC drafted name"))
                ui.onNodeWithTag("file-info-save").performScrollTo().performClick()
                ui.waitForIdle()
                assertEquals(frozenDocument.revision to frozenDetail.revision,gateway.lastInfoRevisions)
                assertEquals(MedicalFileLibraryController.DetailOutcome.CONFLICT,controller.state.value.detailOutcome)
                assertEquals(currentDocument,gateway.docs.single {it.document==image.document})
                assertEquals(currentDetail,gateway.stored.getValue(image.document))
            }
        }finally {controller.close()}
    }
}
