package net.plainnotes.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every UI string must exist in English, Simplified Chinese and French with matching placeholders. */
class TranslationsTest {
    private val entry=Regex("""<string name="([^"]+)">(.*?)</string>""")
    private val placeholder=Regex("""%\d+\$[sd]""")
    private fun read(file:File)=entry.findAll(file.readText()).associate{it.groupValues[1] to placeholder.findAll(it.groupValues[2]).map{m->m.value}.sorted().toList()}

    @Test fun translationsAreComplete() {
        for(res in listOf(File("src/main/res"),File("src/full/res"),File("../core/reminder/src/main/res"))) {
            val base=read(File(res,"values/strings.xml"))
            for(locale in listOf("values-zh","values-b+zh+Hant","values-fr")) {
                val translated=read(File(res,"$locale/strings.xml"))
                assertEquals("$res/$locale keys",base.keys,translated.keys)
                base.forEach{(key,args)->assertEquals("$res/$locale placeholders of $key",args,translated[key])}
            }
        }
    }
}
