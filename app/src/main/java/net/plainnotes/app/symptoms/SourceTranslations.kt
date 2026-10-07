package net.plainnotes.app.symptoms

import org.json.JSONObject
import java.util.Locale

/** Human-written translations kept separate from verbatim quotations. Never used to decide urgency. */
object SourceTranslations {
    private val data by lazy { JSONObject(SourceTranslations::class.java.getResourceAsStream("/wellbeing-translations.json")!!.bufferedReader().use{it.readText()}) }
    fun language(locale:Locale)=when(locale.language){"zh"->if(locale.script=="Hant"||locale.country in listOf("TW","HK"))"zh_Hant" else "zh";"fr"->"fr";else->"en"}
    fun translation(original:String,locale:Locale):String? {
        val entry=data.optJSONObject(original)?:return null
        val language=language(locale)
        return if(entry.getString("original_language")==language)null else entry.getString(language).takeIf{it!=original}
    }
    fun covers(original:String):Boolean=data.has(original)
}
