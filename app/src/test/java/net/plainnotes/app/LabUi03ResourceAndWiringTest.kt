package net.plainnotes.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Checks real production entry wiring and the dedicated resource files missed by the legacy strings.xml-only guard. */
class LabUi03ResourceAndWiringTest {
    @Test fun newHomeHasOneProductionDestinationAndAllFourLocales() {
        val shell = File("src/main/java/net/plainnotes/app/ui/AppShell.kt").readText()
        assertEquals(1, Regex("Destination\\.LABS -> LabsScreen\\(").findAll(shell).count())
        assertTrue(shell.contains("{ destination = Destination.LABS }")) // concentration
        assertTrue(shell.contains("openMedicalChild(Destination.LABS)"))
        val home = File("src/main/java/net/plainnotes/app/ui/LabsScreen.kt").readText()
        assertTrue(home.contains("testTag(\"labs-home\")"))
        assertFalse(home.contains("LabRow("))
        assertTrue(home.contains("LabHomeCard(d,"))
        fun entries(locale: String): Map<String, List<String>> {
            val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$locale/lab_home_strings.xml"))
            val nodes = xml.getElementsByTagName("string")
            return (0 until nodes.length).associate { i -> val n = nodes.item(i)
                n.attributes.getNamedItem("name").nodeValue to Regex("%[0-9]+\\$[sd]").findAll(n.textContent).map { it.value }.toList()
            }
        }
        val base = entries("values")
        assertTrue(base.isNotEmpty())
        listOf("values-zh", "values-b+zh+Hant", "values-fr").forEach { assertEquals("Locale $it", base, entries(it)) }
    }
}
