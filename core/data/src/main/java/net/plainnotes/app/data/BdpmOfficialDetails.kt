package net.plainnotes.app.data

import org.json.JSONObject
import java.net.URI

/** Public French source text only. The saved medication association is never rewritten by these current details. */
data class BdpmOfficialDetails(val data:JSONObject,val provenance:JSONObject) {
    val cis:String get()=data.getString("cis")
    val name:String get()=data.getString("name")
    fun rows(key:String):List<JSONObject> = data.getJSONArray(key).let {a->(0 until a.length()).map {a.getJSONObject(it)}}
    fun sourceDate(dataset:String):String?=provenance.getJSONObject("inputs").getJSONObject(dataset).let {if(it.isNull("source_updated_on"))null else it.getString("source_updated_on")}
}

/** Never accept a source's arbitrary URL as a trusted official browser destination. Rechecked at confirmation. */
object BdpmOfficialLinks {
    private val hosts=setOf("base-donnees-publique.medicaments.gouv.fr","ansm.sante.fr","www.ansm.sante.fr","www.has-sante.fr","has-sante.fr","www.ema.europa.eu","ema.europa.eu")
    fun safe(url:String):Boolean=runCatching {
        val uri=URI(url)
        uri.scheme=="https" && uri.host in hosts && uri.rawUserInfo==null && uri.port in setOf(-1,443) &&
            url.none {it.code<=32 || it=='\\'} && !Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)",RegexOption.IGNORE_CASE).containsMatchIn(url)
    }.getOrDefault(false)
}
