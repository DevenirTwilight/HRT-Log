package net.plainnotes.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Read-only input views. A row is used synchronously, never cached by the restore pipeline. */
internal interface BackupRows {
    fun length():Int
    fun getJSONObject(index:Int):JSONObject
    companion object {
        fun of(rows:JSONArray)=object:BackupRows {
            override fun length()=rows.length()
            override fun getJSONObject(index:Int)=rows.getJSONObject(index)
        }
    }
}
internal interface BackupTables {
    fun keys():Set<String>
    fun has(name:String)=name in keys()
    fun optJSONArray(name:String):BackupRows?
    fun getJSONArray(name:String)=requireNotNull(optJSONArray(name))
    companion object {
        fun of(tables:JSONObject)=object:BackupTables {
            override fun keys()=tables.keys().asSequence().toSet()
            override fun optJSONArray(name:String)=if(!tables.has(name))null else BackupRows.of(tables.getJSONArray(name))
        }
    }
}
