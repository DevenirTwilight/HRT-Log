package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

/** Execution order is business evidence, not a timestamp or SQLite rowid. Never stores the device epoch. */
internal object MeasuredOperationChain {
    data class Node(val operationUuid:String,val sequence:Int,val payloadSha256:String)
    private val sequencePattern=Regex("[1-9][0-9]{0,5}")
    private val hashPattern=Regex("[0-9a-f]{64}")
    /** Exact canonical text, not a hash-only authorization. Owned by one whole read-only audit. Never logged or serialized. */
    internal class AuditProof private constructor(val nodes:Map<String,Node>,private val canonical:Map<String,String>) {
        fun matches(operationUuid:String,text:String)=canonical[operationUuid]==text
        companion object {
            fun capture(db:SupportSQLiteDatabase):AuditProof {
                val checked=checkedChain(db,true)
                return AuditProof(java.util.Collections.unmodifiableMap(checked.first),checked.second)
            }
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
    private fun checkedChain(db:SupportSQLiteDatabase,retainCanonical:Boolean):Pair<Map<String,Node>,Map<String,String>> {
        check(db.inTransaction())
        data class Captured(val uuid:String,val json:String,val chain:JSONObject,val sequence:Int)
        var retainedBytes=0L
        val values=db.query("SELECT operation_uuid,input_json FROM measured_operation").use { c ->
            require(c.count<=BackupLimits.MAX_ROWS)
            buildList {while(c.moveToNext()) {
                val uuid=c.getString(0);require(ReferencePayload.uuid(uuid))
                val text=c.getString(1);val size=ReferencePayload.utf8Length(text);require(size<=ReferencePayload.MAX_BYTES)
                if(retainCanonical){retainedBytes+=size;require(retainedBytes<=BackupLimits.MAX_JSON_BYTES)}
                val input=JSONObject(text);require(ReferencePayload.canonical(input)==text)
                val link=read(input);val chain=input.getJSONObject("chain");val sequence=link.sequence
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
