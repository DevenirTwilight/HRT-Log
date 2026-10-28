package net.plainnotes.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.UUID

/** Typed, bounded wire grammar; clinical closure remains the existing manifest/preflight/oracle.
 * Decoder consumes exact ordered tokens, independently of encoder (no re-encode acceptance test).
 */
internal object MedicalArchiveManifest {
    // Version 1 remains byte-for-byte unchanged for historical single-result archives.
    // Older independent readers reject version 2 before exposing clinical data.
    private fun wireVersion(m:AttachmentBackupManifest)=if(m.records.any {it.kind==AttachmentBackupManifest.Kind.LAB_PANEL} || m.documents.any {d->m.links.none {it.document==d.uuid}})2 else 1
    data class Decoded(val manifest:AttachmentBackupManifest,val envelopes:Map<UUID,String>)
    private val order=MedicalArchiveCrypto.uuidOrder
    fun encode(m:AttachmentBackupManifest,envelopes:Map<UUID,String>,checkActive:()->Unit={}):ByteArray {
        m.validateComplete(AttachmentBackupManifest.Limits(maximumCiphertextBytes=MedicalArchiveCrypto.MAX_CIPHER))
        require(m.versions.size<=1000 && envelopes.keys==m.versions.map {it.context.version}.toSet())
        envelopes.values.forEach {require(it.length==120 && it.all {c->c in '0'..'9' || c in 'a'..'f'})}
        val output=object:ByteArrayOutputStream() {
            fun wipe() {buf.fill(0);reset()}
            override fun write(b:Int) {require(count<MedicalArchiveCrypto.MAX_MANIFEST);super.write(b)}
            override fun write(b:ByteArray,off:Int,len:Int) {require(len<=MedicalArchiveCrypto.MAX_MANIFEST-count);super.write(b,off,len)}
        }
        val w=OutputStreamWriter(output,Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
        fun raw(s:String) {w.write(s)}
        fun str(s:String) {raw("\"");s.forEach {c->when {c=='"'->raw("\\\"");c=='\\'->raw("\\\\");c.code<32->raw("\\u00"+c.code.toString(16).padStart(2,'0'));else->w.write(c.code)}};raw("\"")}
        fun id(v:UUID)=str(v.toString())
        fun num(n:Long?)=raw(n?.toString()?:"null")
        fun <T> array(values:List<T>,write:(T)->Unit) {raw("[");values.forEachIndexed {i,v->checkActive();if(i>0)raw(",");write(v)};raw("]")}
        try {
        raw("{\"version\":${wireVersion(m)},\"dataset\":");id(m.dataset);raw(",\"epoch\":");id(m.epoch);raw(",\"logicalVault\":");id(m.logicalVault)
        raw(",\"records\":");array(m.records.sortedWith(compareBy(order) {it.uuid})) {r->
            raw("{\"uuid\":");id(r.uuid);raw(",\"kind\":");str(r.kind.name);raw(",\"sourceId\":");num(r.sourceId);raw(",\"state\":");str(r.state)
            raw(",\"trashId\":");num(r.trashId);raw(",\"evidenceSha256\":");str(r.evidenceSha256);raw("}")}
        raw(",\"documents\":");array(m.documents.sortedWith(compareBy(order) {it.uuid})) {d->
            raw("{\"uuid\":");id(d.uuid);raw(",\"revision\":");num(d.revision);raw(",\"title\":");str(d.title);raw(",\"category\":");str(d.category)
            raw(",\"state\":");str(d.state);raw(",\"activeVersion\":");id(d.activeVersion);raw(",\"deletedUtc\":");num(d.deletedUtc);raw("}")}
        raw(",\"versions\":");array(m.versions.sortedWith(compareBy<AttachmentBackupManifest.Version> {it.document.toString()}.thenBy {it.ordinal})) {v->
            raw("{\"document\":");id(v.document);raw(",\"ordinal\":");num(v.ordinal);raw(",\"context\":{\"logicalVault\":");id(v.context.logicalVault)
            raw(",\"objectId\":");id(v.context.objectId);raw(",\"version\":");id(v.context.version);raw(",\"attempt\":");id(v.context.attempt)
            raw(",\"dekHandle\":");id(v.context.dekHandle);raw("},\"plaintextBytes\":");num(v.manifest.plaintextBytes);raw(",\"ciphertextBytes\":");num(v.manifest.ciphertextBytes)
            raw(",\"plaintextSha256\":");str(v.manifest.plaintextSha256);raw(",\"ciphertextSha256\":");str(v.manifest.ciphertextSha256);raw(",\"mime\":");str(v.mime);raw("}")}
        raw(",\"links\":");array(m.links.sortedWith(compareBy(order) {it.uuid})) {l->
            raw("{\"uuid\":");id(l.uuid);raw(",\"record\":");id(l.record);raw(",\"document\":");id(l.document);raw(",\"revision\":");num(l.revision);raw(",\"state\":");str(l.state);raw("}")}
        raw(",\"portableEnvelopes\":");array(envelopes.keys.sortedWith(order)) {v->raw("{\"version\":");id(v);raw(",\"wrappedDek\":");str(envelopes.getValue(v));raw("}")}
        raw("}");w.close();checkActive();return output.toByteArray()
        } finally {output.wipe()}
    }
    fun decode(bytes:ByteArray,checkActive:()->Unit={}):Decoded {
        require(bytes.size<=MedicalArchiveCrypto.MAX_MANIFEST)
        return Parser(bytes,checkActive).use {it.decode()}
    }
    private class Parser(bytes:ByteArray,private val active:()->Unit):AutoCloseable {
        private val reader=InputStreamReader(ByteArrayInputStream(bytes),Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
        private var next=reader.read()
        private var total=0
        private fun take(c:Char) {require(next==c.code) {"Noncanonical manifest"};next=reader.read()}
        private fun text(max:Int):String {
            take('"');val s=StringBuilder()
            while(next!='"'.code) {
                require(next>=32 && s.length<max);val c=next.toChar();next=reader.read()
                if(c=='\\') {
                    when(next) {
                        '"'.code,'\\'.code->{s.append(next.toChar());next=reader.read()}
                        'u'.code->{next=reader.read();val h=StringBuilder(4);repeat(4) {require(next in '0'.code..'9'.code || next in 'a'.code..'f'.code);h.append(next.toChar());next=reader.read()}
                            require(h.startsWith("00"));val v=h.toString().toInt(16);require(v<32);s.append(v.toChar())}
                        else->error("Noncanonical escape")
                    }
                } else s.append(c)
            }
            take('"');return s.toString()
        }
        private fun key(name:String,first:Boolean=false) {if(!first)take(',');require(text(32)==name);take(':')}
        private fun number():Long {
            val b=StringBuilder(20)
            if(next=='-'.code) {b.append('-');next=reader.read()}
            require(next in '0'.code..'9'.code)
            if(next=='0'.code) {b.append('0');next=reader.read()} else while(next in '0'.code..'9'.code) {
                require(b.length<20);b.append(next.toChar());next=reader.read()
            }
            require(b.toString()!="-0");return requireNotNull(b.toString().toLongOrNull())
        }
        private fun nullable():Long? {if(next!='n'.code)return number();"null".forEach(::take);return null}
        private fun id():UUID {val s=text(36);require(s.length==36 && Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(s));return UUID.fromString(s)}
        private fun hex(n:Int):String=text(n).also {s->require(s.length==n && s.all {it in '0'..'9' || it in 'a'..'f'})}
        private fun <T> rows(max:Int=100000,metadata:Boolean=true,row:()->T):List<T> {
            take('[');val result=ArrayList<T>()
            if(next!=']'.code)while(true) {
                active();require(result.size<max);if(metadata)require(++total<=100000)
                result+=row();if(next==']'.code)break;take(',')
            }
            take(']');return Collections.unmodifiableList(result)
        }
        fun decode():Decoded {
            active();take('{');key("version",true);val version=number();require(version in 1L..2L);key("dataset");val dataset=id();key("epoch");val epoch=id();key("logicalVault");val vault=id()
            key("records");val records=rows {take('{');key("uuid",true);val uuid=id();key("kind");val kind=AttachmentBackupManifest.Kind.valueOf(text(16))
                key("sourceId");val source=number();key("state");val state=text(16);key("trashId");val trash=nullable();key("evidenceSha256");val hash=hex(64);take('}')
                AttachmentBackupManifest.Record(uuid,kind,source,state,trash,hash)}
            key("documents");val docs=rows {take('{');key("uuid",true);val uuid=id();key("revision");val revision=number();key("title");val title=text(256)
                key("category");val category=text(16);key("state");val state=text(16);key("activeVersion");val v=id();key("deletedUtc");val deleted=nullable();take('}')
                AttachmentBackupManifest.Document(uuid,revision,title,category,state,v,deleted)}
            key("versions");var sum=0L
            val versions=rows(1000) {take('{');key("document",true);val doc=id();key("ordinal");val ordinal=number();key("context");take('{')
                key("logicalVault",true);val lv=id();key("objectId");val obj=id();key("version");val v=id();key("attempt");val a=id();key("dekHandle");val d=id();take('}')
                val context=AttachmentObjectContext(lv,obj,v,a,d)
                key("plaintextBytes");val plain=number();key("ciphertextBytes");val cipher=number();require(cipher==MedicalArchiveCrypto.cipherLength(plain))
                sum=Math.addExact(sum,cipher);require(sum<=MedicalArchiveCrypto.MAX_CIPHER)
                key("plaintextSha256");val ph=hex(64);key("ciphertextSha256");val ch=hex(64);key("mime");val mime=text(24);take('}')
                AttachmentBackupManifest.Version(doc,ordinal,context,AttachmentObjectStreams.Manifest(context,plain,cipher,ph,ch),mime)}
            key("links");val links=rows {take('{');key("uuid",true);val uuid=id();key("record");val r=id();key("document");val d=id();key("revision");val revision=number();key("state");val state=text(16);take('}')
                AttachmentBackupManifest.Link(uuid,r,d,revision,state)}
            key("portableEnvelopes");val envelopes=rows(1000,false) {take('{');key("version",true);val v=id();key("wrappedDek");val w=hex(120);take('}');v to w}
            take('}');require(next==-1);active()
            fun <T> ordered(list:List<T>,key:(T)->UUID) {require(list.zipWithNext().all {(a,b)->order.compare(key(a),key(b))<0})}
            ordered(records) {it.uuid};ordered(docs) {it.uuid};ordered(links) {it.uuid};ordered(envelopes) {it.first}
            require(versions.zipWithNext().all {(a,b)->order.compare(a.document,b.document)<0 || a.document==b.document && a.ordinal<b.ordinal})
            val m=AttachmentBackupManifest(dataset,epoch,vault,records,docs,versions,links)
            require(version==2L || records.none {it.kind==AttachmentBackupManifest.Kind.LAB_PANEL}) {"Panel semantics require manifest version 2"}
            m.validateComplete(AttachmentBackupManifest.Limits(maximumCiphertextBytes=MedicalArchiveCrypto.MAX_CIPHER))
            require(envelopes.size==versions.size && envelopes.map {it.first}.toSet()==versions.map {it.context.version}.toSet())
            return Decoded(m,Collections.unmodifiableMap(envelopes.toMap()))
        }
        override fun close()=reader.close()
    }
}
