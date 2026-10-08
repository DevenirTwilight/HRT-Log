package net.plainnotes.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * REQUIREMENTS §39 recycle bin. Deleted content leaves every screen and export and waits here until it is restored or
 * deleted for good. Content the user made is kept as row snapshots ([payload_json]) and really removed when purged.
 * Records, periods and corrections keep their own soft-delete/append-only rows; a purge that cannot remove them
 * physically (the stock ledger or a frozen reference still needs them) hides them for good instead (state HIDDEN).
 */
object Trash {
    const val TRASHED="TRASHED"
    const val HIDDEN="HIDDEN"
    // Snapshot kinds (rows live only in the payload while trashed).
    const val MILESTONE="MILESTONE";const val LAB="LAB";const val APPOINTMENT="APPOINTMENT";const val REVIEW="REVIEW"
    const val SYMPTOM="SYMPTOM";const val SCORE="SCORE";const val NOTE="NOTE"
    // Reference kinds (rows stay in their own table).
    const val RECORD="RECORD";const val PERIOD="PERIOD";const val CORRECTION="CORRECTION"
    val SNAPSHOT_KINDS=listOf(MILESTONE,LAB,APPOINTMENT,REVIEW,SYMPTOM,SCORE,NOTE)
    val KINDS=SNAPSHOT_KINDS+listOf(RECORD,PERIOD,CORRECTION)

    /** Rows of [table] matching [where], as JSON objects with every column (same encoding as backups). */
    fun rows(db:SupportSQLiteDatabase,table:String,where:String,args:Array<Any?>):JSONArray = JSONArray().also{out->
        db.query("SELECT * FROM `$table` WHERE $where",args).use{c->while(c.moveToNext())out.put(JSONObject().apply{
            for(i in 0 until c.columnCount)when(c.getType(i)){
                Cursor.FIELD_TYPE_NULL->put(c.getColumnName(i),JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER->put(c.getColumnName(i),c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT->put(c.getColumnName(i),c.getDouble(i))
                else->put(c.getColumnName(i),c.getString(i))
            }
        })}
    }
    /** Puts snapshot rows back, parents first, with their original keys. */
    fun reinsert(db:SupportSQLiteDatabase,payload:JSONObject) {
        val tables=payload.getJSONObject("tables");val order=payload.getJSONArray("order")
        for(i in 0 until order.length()){val t=order.getString(i);require(t in DOMAIN_TABLES);val rows=tables.getJSONArray(t)
            for(j in 0 until rows.length()){val r=rows.getJSONObject(j);val cv=ContentValues()
                r.keys().forEach{k->when(val v=r.get(k)){JSONObject.NULL->cv.putNull(k);is Int->cv.put(k,v.toLong());is Long->cv.put(k,v);is Double->cv.put(k,v);else->cv.put(k,v.toString())}}
                db.insert(t,SQLiteDatabase.CONFLICT_ABORT,cv)}}
    }
    fun payload(vararg parts:Pair<String,JSONArray>)=JSONObject().put("order",JSONArray(parts.map{it.first})).put("tables",JSONObject().apply{parts.forEach{(t,rows)->put(t,rows)}})

    fun insert(db:SupportSQLiteDatabase,kind:String,ref:String,date:String,payload:JSONObject,now:Long) {
        db.insert("trash_item",SQLiteDatabase.CONFLICT_ABORT,ContentValues().apply{put("kind",kind);put("ref",ref);put("item_date",date);put("deleted_utc",now);put("payload_json",payload.toString());put("state",TRASHED)})
    }

    /**
     * Upgrade (migration 8→9 and restore of an older backup): soft-deleted records and active user deletions of
     * periods already exist; they move into the bin. Nothing else changes.
     */
    fun backfill(db:SupportSQLiteDatabase) {
        db.execSQL("""INSERT INTO trash_item(kind,ref,item_date,deleted_utc,payload_json,state)
            SELECT 'RECORD',CAST(r.id AS TEXT),strftime('%Y-%m-%d',COALESCE(r.taken_utc,r.scheduled_utc,r.deleted_at_utc)/1000,'unixepoch'),r.deleted_at_utc,'{}','TRASHED' FROM dose_record r
            WHERE r.deleted_at_utc IS NOT NULL AND NOT EXISTS (SELECT 1 FROM trash_item t WHERE t.kind='RECORD' AND t.ref=CAST(r.id AS TEXT))""")
        db.execSQL("""INSERT INTO trash_item(kind,ref,item_date,deleted_utc,payload_json,state)
            SELECT 'PERIOD',h.group_key,MIN(h.from_date),MAX(h.created_utc),'{}','TRASHED' FROM history_period_revision h
            WHERE h.kind='DELETED' AND h.state='CONFIRMED' AND h.group_key IS NOT NULL
              AND h.revision=(SELECT MAX(revision) FROM history_period_revision x WHERE x.period_key=h.period_key)
              AND NOT EXISTS (SELECT 1 FROM trash_item t WHERE t.kind='PERIOD' AND t.ref=h.group_key)
            GROUP BY h.group_key""")
    }
}
