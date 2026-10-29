package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

/** Candidate-only allocator bookkeeping. Retained trash/tombstone IDs must not be reused after
 * reconstruction; no clinical row or portable format changes, and no source-provided SQL. */
internal object LibraryRestoreSequences {
    fun reserve(db:SupportSQLiteDatabase,records:List<AttachmentBackupManifest.Record>,checkActive:()->Unit) {
        check(db.inTransaction())
        val columns=linkedMapOf<String,String>()
        // Identify actual AUTOINCREMENT primary keys from OUR installed schema, not guessed names.
        db.query("SELECT name,sql FROM sqlite_master WHERE type='table'").use {c->while(c.moveToNext()) {
            val table=c.getString(0)
            if(table in DOMAIN_TABLES && c.getString(1).contains("AUTOINCREMENT",ignoreCase=true)) {
                val pk=db.query("PRAGMA table_info(`$table`)").use {p->buildList {while(p.moveToNext())if(p.getInt(5)>0)add(p.getString(1))}}
                check(pk.size==1);columns[table]=pk.single()
            }
        }}
        val highest=mutableMapOf<String,Long>()
        fun retain(table:String,id:Long) {require(id>0);check(table in columns);highest[table]=maxOf(highest[table] ?: 0,id)}
        records.forEach {r->checkActive();retain(when(r.kind) {
            AttachmentBackupManifest.Kind.APPOINTMENT->"appointment"
            AttachmentBackupManifest.Kind.LAB->"lab_value"
            AttachmentBackupManifest.Kind.LAB_PANEL->"lab_panel"
            AttachmentBackupManifest.Kind.DOSE_RECORD->"dose_record"
        },r.sourceId)}
        db.query("SELECT payload_json FROM trash_item").use {c->while(c.moveToNext()) {
            checkActive()
            val tables=JSONObject(c.getString(0)).optJSONObject("tables") ?: continue
            for((table,column) in columns) {
                val rows=tables.optJSONArray(table) ?: continue
                for(i in 0 until rows.length()) {
                    checkActive();val id=rows.getJSONObject(i).get(column).toString().toLongOrNull()
                        ?: error("Retained primary key is not an exact integer")
                    retain(table,id)
                }
            }
        }}
        for((table,id) in highest) {
            checkActive()
            val old=db.query("SELECT seq FROM sqlite_sequence WHERE name=?",arrayOf(table)).use {c->
                if(!c.moveToFirst())null else c.getLong(0).also {check(!c.moveToNext())}
            }
            if(old==null)db.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES (?,?)",arrayOf(table,id))
            else if(old<id)db.execSQL("UPDATE sqlite_sequence SET seq=? WHERE name=?",arrayOf(id,table))
        }
    }
}
