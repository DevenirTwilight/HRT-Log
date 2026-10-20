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
    fun validate(db:SupportSQLiteDatabase):Map<String,Node> = checkedChain(db,false).first
    private fun checkedChain(db:SupportSQLiteDatabase,retainCanonical:Boolean,headers:HeaderCapture?=null):Pair<Map<String,Node>,Map<String,String>> {
        check(db.inTransaction())
        data class Captured(val uuid:String,val json:String,val chain:JSONObject,val sequence:Int)
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
                val input=JSONObject(text);require(ReferencePayload.canonical(input)==text)
                val link=read(input);val chain=input.getJSONObject("chain");val sequence=link.sequence
                if(row!=null)headers!!.accept(uuid,row)
                add(Captured(uuid,text,chain,sequence))
            } }
        }.sortedBy{it.sequence}
        val result=LinkedHashMap<String,Node>();val canonical=if(retainCanonical)LinkedHashMap<String,String>() else null;var previous:Node?=null
        for((index,value) in values.withIndex()) {
            require(value.sequence==index+1)
            val chain=value.chain
            if(previous==null)require(chain.isNull("previous_operation_uuid") && chain.isNull("previous_payload_sha256"))
            else {
                require(chain.get("previous_operation_uuid") is String && chain.getString("previous_operation_uuid")==previous.operationUuid)
                require(chain.get("previous_payload_sha256") is String && chain.getString("previous_payload_sha256")==previous.payloadSha256)
            }
            val node=Node(value.uuid,value.sequence,ReferencePayload.sha(value.json));require(result.put(node.operationUuid,node)==null)
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
