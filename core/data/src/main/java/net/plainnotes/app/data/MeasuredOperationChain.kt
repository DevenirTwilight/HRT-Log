package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

/** Execution order is business evidence, not a timestamp or SQLite rowid. Never stores the device epoch. */
internal object MeasuredOperationChain {
    data class Node(val operationUuid:String,val sequence:Int,val payloadSha256:String)
    private val fields=setOf("sequence","previous_operation_uuid","previous_payload_sha256")
    internal data class Link(val sequence:Int,val previousUuid:String?,val previousSha256:String?)
    fun read(input:JSONObject):Link {
        require(input.get("format") is Int && input.getInt("format")==2)
        val chain=input.getJSONObject("chain");require(chain.keys().asSequence().toSet()==fields)
        val n=chain.get("sequence");require(n is String && Regex("[1-9][0-9]{0,5}").matches(n))
        val sequence=n.toInt();require(sequence<=BackupLimits.MAX_ROWS)
        val previous=if(chain.isNull("previous_operation_uuid"))null else {require(chain.get("previous_operation_uuid") is String);chain.getString("previous_operation_uuid").also{require(ReferencePayload.uuid(it))}}
        val hash=if(chain.isNull("previous_payload_sha256"))null else {require(chain.get("previous_payload_sha256") is String);chain.getString("previous_payload_sha256").also{require(Regex("[0-9a-f]{64}").matches(it))}}
        require(if(sequence==1)previous==null && hash==null else previous!=null && hash!=null)
        return Link(sequence,previous,hash)
    }
    fun validate(db:SupportSQLiteDatabase):Map<String,Node> {
        check(db.inTransaction())
        data class Captured(val uuid:String,val json:String,val chain:JSONObject,val sequence:Int)
        val values=db.query("SELECT operation_uuid,input_json FROM measured_operation").use { c ->
            require(c.count<=BackupLimits.MAX_ROWS)
            buildList {while(c.moveToNext()) {
                val uuid=c.getString(0);require(ReferencePayload.uuid(uuid))
                val text=c.getString(1);require(text.toByteArray(Charsets.UTF_8).size<=ReferencePayload.MAX_BYTES)
                val input=JSONObject(text);require(ReferencePayload.canonical(input)==text)
                val link=read(input);val chain=input.getJSONObject("chain");val sequence=link.sequence
                add(Captured(uuid,text,chain,sequence))
            } }
        }.sortedBy{it.sequence}
        val result=LinkedHashMap<String,Node>();var previous:Node?=null
        for((index,value) in values.withIndex()) {
            require(value.sequence==index+1)
            val chain=value.chain
            if(previous==null)require(chain.isNull("previous_operation_uuid") && chain.isNull("previous_payload_sha256"))
            else {
                require(chain.get("previous_operation_uuid") is String && chain.getString("previous_operation_uuid")==previous.operationUuid)
                require(chain.get("previous_payload_sha256") is String && chain.getString("previous_payload_sha256")==previous.payloadSha256)
            }
            val node=Node(value.uuid,value.sequence,ReferencePayload.sha(value.json));require(result.put(node.operationUuid,node)==null)
            previous=node
        }
        return result
    }
    fun next(db:SupportSQLiteDatabase):JSONObject {
        val nodes=validate(db);require(nodes.size<BackupLimits.MAX_ROWS)
        val previous=nodes.values.lastOrNull()
        return JSONObject().put("sequence",(nodes.size+1).toString())
            .put("previous_operation_uuid",previous?.operationUuid ?: JSONObject.NULL)
            .put("previous_payload_sha256",previous?.payloadSha256 ?: JSONObject.NULL)
    }
}
