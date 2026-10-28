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
}
