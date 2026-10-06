package net.plainnotes.app.disguise

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Guards the intended module boundary and the absence of accidentally introduced public entry points. */
class PrivateBoundaryTest {
    @Test fun privateImplementationDoesNotImportHealthServicesOrMainUi() {
        val files=File("src/full/java/net/plainnotes/app/disguise/privatenotes").listFiles()!!.filter{it.extension=="kt"}
        assertTrue(files.isNotEmpty())
        files.forEach{file->
            val text=file.readText()
            listOf("net.plainnotes.app.data","net.plainnotes.app.domain","net.plainnotes.app.pk","net.plainnotes.app.reminder","net.plainnotes.app.ui.NotesApp","net.plainnotes.app.MainActivity","NotesRepository","RecordEntity").forEach{assertFalse("${file.name} depends on $it",text.contains(it))}
            assertFalse(text.contains("startActivity("))
        }
    }
    @Test fun privateStoreAndEditorHaveNoSharedPreferencesOrSavedStatePersistence() {
        val root=File("src/full/java/net/plainnotes/app/disguise/privatenotes")
        root.listFiles()!!.filter{it.extension=="kt"}.forEach{file->
            val text=file.readText()
            listOf("getSharedPreferences", "rememberSaveable", "SavedStateHandle", "Log.").forEach{assertFalse("${file.name}: $it",text.contains(it))}
        }
    }
}
