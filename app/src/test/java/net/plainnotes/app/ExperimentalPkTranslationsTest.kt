package net.plainnotes.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The experimental page keeps its strings outside the P2-frozen strings.xml; they must still be complete in all four languages. */
class ExperimentalPkTranslationsTest {
    private val entry = Regex("""<string name="([^"]+)">(.*?)</string>""")
    private val placeholder = Regex("""%\d+\$[sd]""")
    private fun read(file: File) = entry.findAll(file.readText()).associate { it.groupValues[1] to placeholder.findAll(it.groupValues[2]).map { m -> m.value }.sorted().toList() }

    @Test fun translationsAreCompleteAndNeverUseConcentrationUnits() {
        val res = File("src/main/res")
        val base = read(File(res, "values/strings_experimental_pk.xml"))
        assertEquals(49, base.size)
        for (dir in listOf("values", "values-zh", "values-b+zh+Hant", "values-fr")) {
            val file = File(res, "$dir/strings_experimental_pk.xml")
            val translated = read(file)
            assertEquals("$dir keys", base.keys, translated.keys)
            base.forEach { (key, args) -> assertEquals("$dir placeholders of $key", args, translated[key]) }
            val text = file.readText()
            assertFalse("$dir must not label relative curves with concentration units", Regex("pg/mL|pmol/L", RegexOption.IGNORE_CASE).containsMatchIn(text))
        }
        // The zh warning is the exact sentence the product owner specified.
        assertEquals(true, File(res, "values-zh/strings_experimental_pk.xml").readText()
            .contains("实验模型仅用于研究相对曲线形状。尚不能准确预测个人血清雌二醇浓度，不应用于自行调整给药剂量。"))
    }
}
