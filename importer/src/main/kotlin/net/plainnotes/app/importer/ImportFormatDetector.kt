package net.plainnotes.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Schema recognition, not filename/MIME guessing or permission to write. No new clinical rules. */
object ImportFormatDetector {
    enum class Source { HRT_TRACKER, TRANS_MEMO, MONA, FEATHERLINE, CHRYSALIDE, HRT_LOG }
    enum class Container { JSON, SQLITE, FEATHERLINE, HRT_LOG, UNKNOWN }
    sealed interface Parsed {
        class Tracker(val export:HrtTracker.Export):Parsed
        class Memo(val export:TmExport):Parsed
        class Mona(val export:net.plainnotes.app.importer.Mona.Export):Parsed
        class Chrysalide(val export:net.plainnotes.app.importer.Chrysalide.Export):Parsed
        class EncryptedFeatherline(val archive:Featherline.Archive):Parsed
    }
    class Unknown:Exception()
    class Damaged:Exception()
    class TooLarge:Exception()
    class Unsupported(val source:Source):Exception()

    /** Disjoint container rules; a SQLite/opaque encrypted file can never enter a JSON parser. */
    fun header(bytes:ByteArray):Container {
        fun starts(value:String)=bytes.size>=value.length && value.indices.all {bytes[it]==value[it].code.toByte()}
        if(starts("SQLite format 3\u0000"))return Container.SQLITE
        if(starts("HRTBKP1"))return Container.FEATHERLINE
        if(starts("PNBAK1"))return Container.HRT_LOG
        var offset=if(bytes.size>=3 && bytes[0]==0xef.toByte() && bytes[1]==0xbb.toByte() && bytes[2]==0xbf.toByte())3 else 0
        while(offset<bytes.size && bytes[offset].toInt() in listOf(9,10,13,32))offset++
        return if(offset<bytes.size && bytes[offset].toInt() in listOf(91,123))Container.JSON else Container.UNKNOWN
    }
    private interface JsonRule {
        val source:Source
        fun recognizes(root:Any):Boolean
        fun read(root:Any):Parsed
    }
    private object TrackerRule:JsonRule {
        override val source=Source.HRT_TRACKER
        override fun recognizes(root:Any):Boolean {
            val objectRoot=root as? JSONObject
            val meta=objectRoot?.optJSONObject("meta")
            // Current tagged exports can be recognized even when an event is damaged. Empty
            // untagged arrays/objects have no source identity and are deliberately ambiguous.
            if(meta?.has("version")==true && meta.has("exportedAt") && objectRoot.has("events") &&
                listOf("weight","labResults","gelProducts").any(objectRoot::has))return true
            val events=objectRoot?.optJSONArray("events") ?: root as? JSONArray ?: return false
            return events.length()>0 && (0 until events.length()).all {i->
                events.optJSONObject(i)?.let {e->listOf("timeH","doseMG","route","ester").all(e::has)}==true
            }
        }
        override fun read(root:Any):Parsed {
            (root as? JSONObject)?.optJSONObject("meta")?.let {meta->
                val version=meta.opt("version")
                if(version !is Number || version.toDouble() !in setOf(1.0,2.0))throw Unsupported(source)
            }
            return try {Parsed.Tracker(HrtTracker.readRoot(root))}catch(_:HrtTracker.InvalidExport){throw Damaged()}catch(_:org.json.JSONException){throw Damaged()}
        }
    }
    private object MonaRule:JsonRule {
        override val source=Source.MONA
        override fun recognizes(root:Any):Boolean {
            val obj=root as? JSONObject ?: return false
            val meta=obj.optJSONObject("metadata") ?: return false
            val data=obj.optJSONObject("data") ?: return false
            return listOf("app_version","database_version","export_date").all(meta::has) &&
                listOf("medication_intakes","medication_schedules","supply_items","blood_tests").all(data::has)
        }
        override fun read(root:Any):Parsed = try {
            Parsed.Mona(Mona.readRoot(root as JSONObject))
        } catch(e:Mona.InvalidExport) {
            when(e.code) {
                "UNSUPPORTED_DATABASE_VERSION"->throw Unsupported(source)
                "TOO_MANY_ROWS"->throw TooLarge()
                else->throw Damaged()
            }
        }
    }
    private object ChrysalideRule:JsonRule {
        override val source=Source.CHRYSALIDE
        override fun recognizes(root:Any):Boolean {
            val o=root as? JSONObject ?: return false
            val version=o.opt("version")
            if(version !is Number) return false
            if(o.opt("exportedAt") !is String) return false
            val required=listOf("medications","medicationLogs","bloodTests","physicalProgress","appointments","reminders","userProfile","journalEntries","objectives","milestones","treatmentChanges","practitioners","acts","actTodos")
            if(required.any { o.opt(it) !is JSONArray }) return false
            val logs=o.optJSONArray("medicationLogs")!!
            val labs=o.optJSONArray("bloodTests")!!
            // Empty tables are valid only when another table carries the characteristic schema.
            val logEvidence=(0 until logs.length()).any { i->logs.optJSONObject(i)?.let {it.opt("id") is Number && it.opt("medicationId") is Number && it.opt("timestamp") is String && it.opt("taken") is Boolean}==true}
            val labEvidence=(0 until labs.length()).any { i->labs.optJSONObject(i)?.let {it.opt("id") is Number && it.opt("date") is String && it.opt("results") is JSONArray}==true}
            val medEvidence=(0 until (o.optJSONArray("medications")?.length()?:0)).any { i->o.optJSONArray("medications")?.optJSONObject(i)?.let {it.opt("id") is Number && it.opt("name") is String && it.opt("type") is String && it.opt("method") is String && it.opt("unit") is String}==true}
            return logEvidence || labEvidence || medEvidence
        }
        override fun read(root:Any):Parsed=try { Parsed.Chrysalide(Chrysalide.readRoot(root as JSONObject)) }
        catch(e:Chrysalide.InvalidExport) { when(e.reason){"UNSUPPORTED_VERSION"->throw Unsupported(source);"TOO_LARGE"->throw TooLarge();else->throw Damaged()} }
    }
    private val jsonRules=listOf<JsonRule>(TrackerRule,MonaRule,ChrysalideRule)
    fun json(text:String,onRecognized:(Source)->Unit={}):Parsed {
        bounds(text)
        val root=try {
            val tokens=JSONTokener(text.removePrefix("\uFEFF"));val value=tokens.nextValue()
            if(tokens.nextClean()!='\u0000' || value !is JSONObject && value !is JSONArray)throw Damaged()
            value
        }catch(_:Exception){throw Damaged()}
        val matches=jsonRules.filter {it.recognizes(root)}
        if(matches.size!=1)throw Unknown()
        val rule=matches.single();onRecognized(rule.source)
        return rule.read(root)
    }
    fun sqlite(source:SqlSource,onRecognized:(Source)->Unit={}):Parsed {
        if(!TransMemo.matchesSchema(source))throw Unknown()
        onRecognized(Source.TRANS_MEMO)
        if(source.userVersion()!=8)throw Unsupported(Source.TRANS_MEMO)
        return try {Parsed.Memo(TransMemo.read(source))}catch(_:InvalidExport){throw Damaged()}
    }
    /** Allocation/recursion guard before the existing JSON parser. This is not a second parser. */
    internal fun bounds(text:String,maximumContainers:Int=250000) {
        var depth=0;var containers=0;var entries=0;var quoted=false;var escaped=false
        for(c in text) {
            if(quoted) {if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}
            else when(c) {
                '"'->quoted=true
                ','->{entries++;if(entries>250000)throw TooLarge()}
                '[','{'->{depth++;containers++;if(depth>64 || containers>maximumContainers)throw TooLarge()}
                ']', '}'->{depth--;if(depth<0)throw Damaged()}
            }
        }
        if(depth!=0 || quoted)throw Damaged()
    }
}
