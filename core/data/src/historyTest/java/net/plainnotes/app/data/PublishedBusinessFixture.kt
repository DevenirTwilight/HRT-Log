package net.plainnotes.app.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Test-only reader for published pre-marker fixtures; never a production export/downgrade path. */
internal object PublishedBusinessFixture {
    fun dump(db:SupportSQLiteDatabase,tables:List<String> = DOMAIN_TABLES):JSONObject = JSONObject().apply {
        for(table in tables) {
            val columns=RecordBackupCompatibility.columns.getValue(table).filterNot{table=="supply_container" && it=="inventory_encoding"}
            val rows=JSONArray()
            db.query("SELECT ${columns.joinToString(","){"`$it`"}} FROM `$table`").use { c ->while(c.moveToNext())rows.put(JSONObject().apply {
                for(i in 0 until c.columnCount)put(c.getColumnName(i),when(c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL->JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER->c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT->c.getDouble(i)
                    Cursor.FIELD_TYPE_STRING->c.getString(i)
                    else->error("Unexpected published business fixture type")
                })
            }) }
            put(table,rows)
        }
    }
    fun publishedDdl(db:SupportSQLiteDatabase,table:String,type:String,sql:String?):String? {
        if(sql==null || table!="supply_container" || type!="table" || !InventoryEncoding.present(db))return sql
        val allowed=listOf(", inventory_encoding TEXT NOT NULL DEFAULT 'LEGACY_SCALAR'",", `inventory_encoding` TEXT NOT NULL DEFAULT 'LEGACY_SCALAR'")
        return sql.replace(allowed.single{sql.contains(it)},"")
    }
    fun stripLegacyEncoding(tables:JSONObject) {
        val rows=tables.optJSONArray("supply_container") ?: return
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            require(!row.has("inventory_encoding") || row.getString("inventory_encoding")==InventoryEncoding.LEGACY)
            row.remove("inventory_encoding")
        }
    }
}
