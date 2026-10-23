package net.plainnotes.attachmentpoc

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** TEST_ONLY independent candidate decoder. No archive reader/writer, encryption or runtime path.
 * Strict byte grammar, not canonicality inferred by calling the reference encoder.
 */
internal object PortableCanonicalReview {
    const val MAX_BYTES=8*1024*1024
    fun decode(bytes:ByteArray):Map<String,Any?> {
        require(bytes.size<=MAX_BYTES)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val parser=Parser(text);val value=parser.value(0);require(parser.finished())
        val root=obj(value,listOf("version","dataset","epoch","logicalVault","records","documents","versions","links","portableEnvelopes"))
        require(root["version"]==1L)
        listOf("dataset","epoch","logicalVault").forEach {uuid(root[it])}
        val records=rows(root["records"]);val docs=rows(root["documents"]);val versions=rows(root["versions"]);val links=rows(root["links"]);val envelopes=rows(root["portableEnvelopes"])
        require(records.size.toLong()+docs.size+versions.size+links.size<=100000 && versions.size<=1000)
        fun ordered(rows:List<Map<String,Any?>>,key:String) {
            val ids=rows.map {uuid(it[key])};require(ids.zipWithNext().all {(a,b)->a<b})
        }
        ordered(records,"uuid");ordered(docs,"uuid");ordered(links,"uuid");ordered(envelopes,"version")
        records.forEach {r->obj(r,listOf("uuid","kind","sourceId","state","trashId","evidenceSha256"));uuid(r["uuid"])
            require(r["kind"] in setOf("APPOINTMENT","LAB","DOSE_RECORD"));positive(r["sourceId"])
            require(r["state"] in setOf("ACTIVE","TRASHED","PURGED"));nullablePositive(r["trashId"]);hex(r["evidenceSha256"],64)}
        docs.forEach {d->obj(d,listOf("uuid","revision","title","category","state","activeVersion","deletedUtc"));uuid(d["uuid"]);positive(d["revision"])
            val title=d["title"] as? String ?: error("Missing text");require(title.isNotBlank() && title.length<=256)
            require(d["category"] in setOf("REPORT","PRESCRIPTION","CERTIFICATE","RECORD","OTHER"));require(d["state"] in setOf("ACTIVE","TRASHED","PURGE_PENDING","PURGED"))
            uuid(d["activeVersion"]);require(d["deletedUtc"]==null || d["deletedUtc"] is Long)}
        var sum=0L
        val ordering=versions.map {v->obj(v,listOf("document","ordinal","context","plaintextBytes","ciphertextBytes","plaintextSha256","ciphertextSha256","mime"))
            val document=uuid(v["document"]);val ordinal=positive(v["ordinal"])
            val c=obj(v["context"],listOf("logicalVault","objectId","version","attempt","dekHandle"));c.values.forEach(::uuid)
            require(c["logicalVault"]==root["logicalVault"])
            val plain=v["plaintextBytes"] as? Long ?: error("Length type");val cipher=v["ciphertextBytes"] as? Long ?: error("Length type")
            require(plain in 0..16L*1024*1024 && cipher in 56..16L*1024*1024+65536)
            sum=Math.addExact(sum,cipher);require(sum<=512L*1024*1024)
            hex(v["plaintextSha256"],64);hex(v["ciphertextSha256"],64)
            require(v["mime"] in setOf("application/pdf","image/png","image/jpeg","application/octet-stream"))
            document to ordinal}
        require(ordering.zipWithNext().all {(a,b)->a.first<b.first || a.first==b.first && a.second<b.second})
        links.forEach {l->obj(l,listOf("uuid","record","document","revision","state"));listOf("uuid","record","document").forEach {uuid(l[it])};positive(l["revision"]);require(l["state"] in setOf("ACTIVE","RETRACTED","QUARANTINED"))}
        envelopes.forEach {e->obj(e,listOf("version","wrappedDek"));uuid(e["version"]);hex(e["wrappedDek"],120)}
        val ids=versions.map {obj(it["context"],listOf("logicalVault","objectId","version","attempt","dekHandle"))["version"]}
        require(ids.size==ids.toSet().size && envelopes.map {it["version"]}.toSet()==ids.toSet())
        // This is wire syntax/limits only. Existing manifest/preflight/business/Tink checks must
        // independently prove semantics, membership and authentication before any future restore.
        return root
    }
    private fun uuid(v:Any?):String {val s=v as? String ?: error("UUID type");require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(s));return s}
    private fun hex(v:Any?,n:Int) {require(v is String && v.length==n && v.all {it in '0'..'9' || it in 'a'..'f'})}
    private fun positive(v:Any?):Long {require(v is Long && v>0);return v}
    private fun nullablePositive(v:Any?) {if(v!=null)positive(v)}
    @Suppress("UNCHECKED_CAST") private fun obj(v:Any?,keys:List<String>):Map<String,Any?> {
        require(v is LinkedHashMap<*,*> && v.keys.toList()==keys);return v as Map<String,Any?>
    }
    @Suppress("UNCHECKED_CAST") private fun rows(v:Any?):List<Map<String,Any?>> {
        require(v is List<*> && v.all {it is LinkedHashMap<*,*>});return v as List<Map<String,Any?>>
    }
    private class Parser(val s:String) {
        var at=0
        private var nodes=0
        fun finished()=at==s.length
        private fun take(c:Char) {require(at<s.length && s[at++]==c)}
        fun value(depth:Int):Any? {
            // Engineering guard above the <=100k typed-row grammar's maximum value count;
            // not proof that a DOM decoder fits the Android joint memory budget.
            require(++nodes<=1_000_000 && depth<=8 && at<s.length)
            return when(s[at]) {
                '"'->string()
                '{'->{at++;val m=LinkedHashMap<String,Any?>();if(at<s.length && s[at]=='}') {at++;m} else {
                    while(true) {require(m.size<32);val k=string();require(!m.containsKey(k));take(':');m[k]=value(depth+1)
                        require(at<s.length);if(s[at]=='}') {at++;break};take(',')};m}}
                '['->{at++;val a=ArrayList<Any?>();if(at<s.length && s[at]==']') {at++;a} else {
                    while(true) {require(a.size<100000);a+=value(depth+1);require(at<s.length);if(s[at]==']') {at++;break};take(',')};a}}
                'n'->{require(s.startsWith("null",at));at+=4;null}
                else->{val start=at;if(s[at]=='-')at++;require(at<s.length && s[at] in '0'..'9')
                    if(s[at]=='0')at++ else while(at<s.length && s[at] in '0'..'9')at++
                    val token=s.substring(start,at);require(token!="-0");token.toLongOrNull() ?: error("Integer overflow")}
            }
        }
        private fun string():String {
            take('"');val out=StringBuilder()
            while(true) {require(at<s.length);val c=s[at++]
                when(c) {
                    '"'->return out.toString()
                    '\\'->{require(at<s.length);when(val e=s[at++]) {
                        '"','\\'->out.append(e)
                        'u'->{require(at+4<=s.length);val h=s.substring(at,at+4);require(h.startsWith("00") && h.all {it in '0'..'9' || it in 'a'..'f'});val n=h.toInt(16);require(n<32);out.append(n.toChar());at+=4}
                        else->error("Noncanonical escape")}}
                    else->{require(c.code>=32);out.append(c)}
                }
            }
        }
    }
}
