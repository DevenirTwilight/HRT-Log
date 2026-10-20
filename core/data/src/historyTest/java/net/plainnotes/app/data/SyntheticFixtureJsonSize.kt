package net.plainnotes.app.data

import org.json.JSONObject

/** TEST_ONLY fixture metadata. Same platform row encoding, without a second whole document.
 * Never used by backup export/restore or shipped in the application.
 */
internal object SyntheticFixtureJsonSize {
    fun utf8Bytes(tables:JSONObject,limit:Int=BackupLimits.MAX_JSON_BYTES):Int {
        require(limit in 0..BackupLimits.MAX_JSON_BYTES)
        var size=0L
        fun add(count:Int) {
            size+=count
            if(size>limit)throw BackupCodec.TooLarge()
        }
        fun text(value:String) {add(value.toByteArray(Charsets.UTF_8).size)}
        add(2) // enclosing object braces
        var firstTable=true
        tables.keys().forEach {table->
            if(!firstTable)add(1)
            firstTable=false
            text(JSONObject.quote(table));add(3) // colon and array brackets
            val rows=tables.getJSONArray(table)
            for(index in 0 until rows.length()) {
                if(index>0)add(1)
                text(rows.getJSONObject(index).toString())
            }
        }
        return size.toInt()
    }
}
