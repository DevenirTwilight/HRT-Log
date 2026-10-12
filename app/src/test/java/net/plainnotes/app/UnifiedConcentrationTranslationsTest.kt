package net.plainnotes.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UnifiedConcentrationTranslationsTest {
    @Test fun fourLocalesHaveAllKeysAndIdenticalFormatArguments() {
        fun strings(locale: String): Map<String,String> {
            val xml=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$locale/strings_unified_concentration.xml"))
            val nodes=xml.getElementsByTagName("string")
            return (0 until nodes.length).associate { (nodes.item(it) as org.w3c.dom.Element).let { n -> n.getAttribute("name") to n.textContent } }
        }
        val english=strings("values")
        for(locale in listOf("values","values-zh","values-b+zh+Hant","values-fr")) {
            assertFalse("Stale calibration instruction in $locale",strings(locale).getValue("uc_original_research_note").contains("Legacy",ignoreCase=true))
        }
        for(locale in listOf("values-zh","values-b+zh+Hant","values-fr")) {
            val translated=strings(locale);assertEquals(locale,english.keys,translated.keys)
            for((key,text) in english) {
                assertTrue("$locale $key",translated.getValue(key).isNotBlank())
                val placeholders=Regex("""%[0-9]+\$[sd]""")
                assertEquals("$locale $key",placeholders.findAll(text).map {it.value}.toList(),placeholders.findAll(translated.getValue(key)).map {it.value}.toList())
            }
        }
        assertFalse(File("src/main/res/values/strings_unified_concentration.xml").readText().contains("Legacy PK (official model)"))
    }
}
