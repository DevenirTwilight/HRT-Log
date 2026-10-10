package net.plainnotes.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The experimental strings live outside the P2-frozen strings.xml; they must still be complete in all four languages. */
class ExperimentalPkTranslationsTest {
    private val entry = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""")
    private val placeholder = Regex("""%\d+\$[sd]""")
    private fun read(file: File) = entry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    /** Only the literature-scenario texts (and the pointer to the official page) may name a concentration unit. */
    private val unitAllowed = setOf("xpk_legacy_relative_note", "xpk_legend_study", "xpk_readout_study", "xpk_scale_study", "xpk_study_anchor",
        "xpk_study_cmax", "xpk_study_refused_title", "xpk_unit_study", "xpk_study_auc_model", "xpk_study_auc_figure", "xpk_study_auc_table")
    private val concentrationUnit = Regex("pg/mL|pmol/L", RegexOption.IGNORE_CASE)

    @Test fun translationsAreCompleteAndUnitsAppearOnlyInStudyScenarioTexts() {
        val res = File("src/main/res")
        val base = read(File(res, "values/strings_experimental_pk.xml"))
        assertEquals(96, base.size)
        for (dir in listOf("values", "values-zh", "values-b+zh+Hant", "values-fr")) {
            val translated = read(File(res, "$dir/strings_experimental_pk.xml"))
            assertEquals("$dir keys", base.keys, translated.keys)
            base.forEach { (key, text) ->
                assertEquals("$dir placeholders of $key", placeholder.findAll(text).map { it.value }.sorted().toList(),
                    placeholder.findAll(translated.getValue(key)).map { it.value }.sorted().toList())
                if (key !in unitAllowed) assertFalse("$dir $key must not label relative curves with concentration units", concentrationUnit.containsMatchIn(translated.getValue(key)))
            }
            assertTrue("$dir relative unit text must say it is not a blood level", translated.getValue("xpk_unit_relative").isNotBlank())
        }
        val zh = File(res, "values-zh/strings_experimental_pk.xml").readText()
        // Exact sentences the product owner specified.
        assertTrue(zh.contains("实验模型仅用于研究相对曲线形状。尚不能准确预测个人血清雌二醇浓度，不应用于自行调整给药剂量。"))
        assertTrue(zh.contains("文献研究情景，非个人血药浓度预测"))
    }
}
