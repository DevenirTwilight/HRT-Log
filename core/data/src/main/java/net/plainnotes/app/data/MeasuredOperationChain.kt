package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import android.database.Cursor
import org.json.JSONObject

/** Execution order is business evidence, not a timestamp or SQLite rowid. Never stores the device epoch. */
internal object MeasuredOperationChain {
    data class Node(val operationUuid:String,val sequence:Int,val payloadSha256:String)
    private val sequencePattern=Regex("[1-9][0-9]{0,5}")
    private val hashPattern=Regex("[0-9a-f]{64}")
    /** Exact canonical text, not a hash-only authorization. Owned by one whole read-only audit. Never logged or serialized. */
    internal class AuditProof private constructor(
        private var nodeValues:Map<String,Node>,private var canonical:Map<String,String>,
        private var headers:Map<String,Array<Any?>>,private val complete:Boolean,
    ):AutoCloseable {
        private var active=true
        val nodes:Map<String,Node> get(){check(active);return nodeValues}
        val hasCompleteRows:Boolean get(){check(active);return complete}
        fun matches(operationUuid:String,text:String):Boolean {check(active);return canonical[operationUuid]==text}
        /** Fresh objects protect the captured primitive rows from a caller mutating a JSONObject. */
        fun row(operationUuid:String):JSONObject? {
            check(active)
            if(!complete)return null
            val values=headers[operationUuid] ?: return null
            return JSONObject().apply{operationColumns.forEachIndexed{i,name->put(name,values[i] ?: JSONObject.NULL)}}
        }
        override fun close(){active=false;headers=emptyMap();canonical=emptyMap();nodeValues=emptyMap()}
        companion object {
            // Conservative extra metadata estimate, not a process-RSS claim. Canonical text retains its original32MiB fence.
            const val MAX_ROW_BYTES=4L*1024*1024
            fun capture(db:SupportSQLiteDatabase,rowByteBudget:Long=MAX_ROW_BYTES):AuditProof {
                require(rowByteBudget in 0..MAX_ROW_BYTES)
                val rows=HeaderCapture(rowByteBudget)
                val checked=checkedChain(db,true,if(rowByteBudget==0L)null else rows)
                return AuditProof(java.util.Collections.unmodifiableMap(checked.first),checked.second,rows.rows,rows.complete)
            }
        }
    }
    private val operationColumns=ReferenceSchema.columns.getValue("measured_operation")
    /** Metadata shares the exact input String already retained by the proof; no second payload copy. */
    private class HeaderCapture(private val budget:Long) {
        val rows=HashMap<String,Array<Any?>>()
        var complete=budget>0
            private set
        private var estimated=0L
        fun accept(uuid:String,values:Array<Any?>) {
            if(!complete)return
            val cost=512L+values.size*32L+values.withIndex().filter{operationColumns[it.index]!="input_json"}
                .sumOf{(_,v)->if(v is String)112L+v.length*2L else 0L}
            if(cost>budget-estimated){complete=false;rows.clear();return}
            estimated+=cost;check(rows.put(uuid,values)==null)
        }
    }
    private val fields=setOf("sequence","previous_operation_uuid","previous_payload_sha256")
    internal data class Link(val sequence:Int,val previousUuid:String?,val previousSha256:String?)
    fun read(input:JSONObject):Link {
        require(input.get("format") is Int && input.getInt("format")==2)
        val chain=input.getJSONObject("chain");require(chain.keys().asSequence().toSet()==fields)
        val n=chain.get("sequence");require(n is String && sequencePattern.matches(n))
        val sequence=n.toInt();require(sequence<=BackupLimits.MAX_ROWS)
        val previous=if(chain.isNull("previous_operation_uuid"))null else {require(chain.get("previous_operation_uuid") is String);chain.getString("previous_operation_uuid").also{require(ReferencePayload.uuid(it))}}
        val hash=if(chain.isNull("previous_payload_sha256"))null else {require(chain.get("previous_payload_sha256") is String);chain.getString("previous_payload_sha256").also{require(hashPattern.matches(it))}}
        require(if(sequence==1)previous==null && hash==null else previous!=null && hash!=null)
        return Link(sequence,previous,hash)
    }
    /** Pure content codec, never a chain/database validity cache. No UUID, connection, epoch,
     * transaction, row header or acceptance result is retained. Every caller still reads every
     * current row and checks the complete sequence/predecessor graph below. */
    private data class CheckedInput(val link:Link,val sha256:String)
    private const val MAX_CHECKED_INPUT_BYTES=16L*1024*1024
    private val checkedInputLock=Any()
    private val checkedInputs=LinkedHashMap<String,CheckedInput>(16,0.75f,true)
    private var checkedInputByteBudget=MAX_CHECKED_INPUT_BYTES
    private var checkedInputBytes=0L
    // Resource ownership only: clearing forbids in-flight readers from retaining old plaintext.
    // This token never establishes database validity or changes any chain check.
    private var checkedInputLifetime=Any()
    /** Release owned plaintext references at close/destruction and before authenticated restore.
     * Clearing affects performance only; it neither establishes nor invalidates database trust. */
    internal fun clearPureInputMemo()=synchronized(checkedInputLock) {
        checkedInputs.clear();checkedInputBytes=0
    }
    private fun checkedInputCost(text:String,value:CheckedInput):Long {
        fun string(s:String?)=if(s==null)0L else 112L+s.length*2L
        // Complete UTF-16 key, two immutable DTOs, map node/table reserve and all retained strings.
        // This conservative allocation reservation is not an exact heap/RSS measurement.
        return 512L+string(text)+string(value.link.previousUuid)+string(value.link.previousSha256)+string(value.sha256)
    }
    private fun checkedInput(text:String,lifetime:Any):CheckedInput = synchronized(checkedInputLock) {
        if(lifetime===checkedInputLifetime)checkedInputs[text]?.let{return@synchronized it}
        val input=JSONObject(text);require(ReferencePayload.canonical(input)==text)
        val value=CheckedInput(read(input),ReferencePayload.sha(text))
        val cost=checkedInputCost(text,value)
        if(lifetime===checkedInputLifetime && cost<=checkedInputByteBudget) {
            val iterator=checkedInputs.entries.iterator()
            while(checkedInputBytes>checkedInputByteBudget-cost && iterator.hasNext()) {
                val entry=iterator.next();checkedInputBytes-=checkedInputCost(entry.key,entry.value);iterator.remove()
            }
            checkedInputs[text]=value;checkedInputBytes+=cost
        }
        value
    }
    fun validate(db:SupportSQLiteDatabase):Map<String,Node> = checkedChain(db,false).first
    private fun checkedChain(db:SupportSQLiteDatabase,retainCanonical:Boolean,headers:HeaderCapture?=null):Pair<Map<String,Node>,Map<String,String>> {
        check(db.inTransaction())
        val lifetime=synchronized(checkedInputLock){checkedInputLifetime}
        data class Captured(val uuid:String,val json:String,val checked:CheckedInput)
        var retainedBytes=0L
        val query=if(headers==null)"SELECT operation_uuid,input_json FROM measured_operation"
            else "SELECT "+operationColumns.joinToString(","){"`$it`"}+" FROM measured_operation"
        val values=db.query(query).use { c ->
            require(c.count<=BackupLimits.MAX_ROWS)
            buildList {while(c.moveToNext()) {
                val row=if(headers?.complete==true)Array<Any?>(operationColumns.size){i->when(c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL->null
                    Cursor.FIELD_TYPE_INTEGER->c.getLong(i)
                    Cursor.FIELD_TYPE_STRING->c.getString(i)
                    else->error("Unexpected reference storage type")
                }} else null
                val uuid=if(row!=null)row[0] as? String ?: error("Invalid operation UUID type") else c.getString(0)
                require(ReferencePayload.uuid(uuid))
                val text=if(row!=null)row[operationColumns.indexOf("input_json")] as? String ?: error("Invalid operation payload type")
                    else c.getString(if(headers==null)1 else operationColumns.indexOf("input_json"))
                val size=ReferencePayload.utf8Length(text);require(size<=ReferencePayload.MAX_BYTES)
                if(retainCanonical){retainedBytes+=size;require(retainedBytes<=BackupLimits.MAX_JSON_BYTES)}
                val checked=checkedInput(text,lifetime)
                if(row!=null)headers!!.accept(uuid,row)
                add(Captured(uuid,text,checked))
            } }
        }.sortedBy{it.checked.link.sequence}
        val result=LinkedHashMap<String,Node>();val canonical=if(retainCanonical)LinkedHashMap<String,String>() else null;var previous:Node?=null
        for((index,value) in values.withIndex()) {
            val link=value.checked.link
            require(link.sequence==index+1)
            if(previous==null)require(link.previousUuid==null && link.previousSha256==null)
            else {
                require(link.previousUuid==previous.operationUuid)
                require(link.previousSha256==previous.payloadSha256)
            }
            val node=Node(value.uuid,link.sequence,value.checked.sha256);require(result.put(node.operationUuid,node)==null)
            canonical?.put(value.uuid,value.json)
            previous=node
        }
        return result to (canonical ?: emptyMap())
    }
    fun next(db:SupportSQLiteDatabase):JSONObject {
        val nodes=validate(db);require(nodes.size<BackupLimits.MAX_ROWS)
        val previous=nodes.values.lastOrNull()
        return JSONObject().put("sequence",(nodes.size+1).toString())
            .put("previous_operation_uuid",previous?.operationUuid ?: JSONObject.NULL)
            .put("previous_payload_sha256",previous?.payloadSha256 ?: JSONObject.NULL)
    }
}
