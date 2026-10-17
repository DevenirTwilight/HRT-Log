package net.plainnotes.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement

/** Bounded primitive rows for one synchronous read-only audit. Never persisted, returned or reused across writes.
 * Unsupported queries and uncached keys always reach SQLite; incomplete caches never prove absence. */
internal class ReferenceAuditReads(
    private val actual:SupportSQLiteDatabase,
    private val byteBudget:Long=8L*1024*1024,
):SupportSQLiteDatabase by actual {
    private class Table(val name:String,val key:String,val columns:Array<String>) {
        val rows=HashMap<String,Array<Any?>>()
        val ledger=HashMap<Long,Array<Any?>>()
        val firstEvent=HashMap<String,Array<Any?>>()
        var complete=true
        fun cursor(row:Array<Any?>?)=MatrixCursor(columns.copyOf(),if(row==null)0 else 1).apply {if(row!=null)addRow(row)}
    }
    private val tables=HashMap<String,Table>()
    private val primaryQueries=HashMap<String,Table>()
    private val strings=HashMap<String,String>()
    private var estimatedBytes=0L
    private var active=true
    private val initialChanges:Long
    private val initialSchema:Long
    private fun scalar(sql:String)=actual.query(sql).use{check(it.moveToFirst());it.getLong(0)}
    init {
        check(actual.inTransaction());require(byteBudget>=0)
        initialChanges=scalar("SELECT total_changes()")
        initialSchema=scalar("PRAGMA schema_version")
        // Operation envelopes are deliberately not retained: they are large and mostly unique.
        if(byteBudget>0)for(name in listOf("reference_dataset","reference_identity","reference_event","reference_snapshot","record_reference_capture","measured_allocation")) {
            actual.query("SELECT * FROM `$name`").use { c ->
                val key=if(name=="reference_event")"operation_uuid" else "uuid"
                val table=Table(name,key,c.columnNames.copyOf());tables[name]=table
                primaryQueries["SELECT * FROM `$name` WHERE $key=?"]=table
                val primary=c.getColumnIndexOrThrow(key)
                val ledger=c.getColumnIndex("ledger_entry_id")
                val subject=c.getColumnIndex("subject_uuid");val revision=c.getColumnIndex("revision")
                while(c.moveToNext()) {
                    val values=Array<Any?>(c.columnCount){i->when(c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL->null
                        Cursor.FIELD_TYPE_INTEGER->c.getLong(i)
                        Cursor.FIELD_TYPE_STRING->c.getString(i)
                        else->error("Unexpected reference storage type")
                    }}
                    val newStrings=values.filterIsInstance<String>().filterNot{strings.containsKey(it)}.distinct()
                    // Conservative allocation estimate, not an assertion about process RSS or exact heap bytes.
                    val cost=192L+values.size*16L+newStrings.sumOf{112L+it.length*2L}
                    if(cost>byteBudget-estimatedBytes) {table.complete=false;continue}
                    estimatedBytes+=cost
                    for(i in values.indices)if(values[i] is String) {
                        val value=values[i] as String;values[i]=strings.getOrPut(value){value}
                    }
                    val id=values[primary] as? String ?: error("Invalid reference key")
                    check(table.rows.put(id,values)==null)
                    if(ledger>=0) {
                        val entry=values[ledger] as? Long ?: error("Invalid ledger key")
                        check(table.ledger.put(entry,values)==null)
                    }
                    if(subject>=0) {
                        val owner=values[subject] as? String ?: error("Invalid subject")
                        val version=values[revision] as? Long ?: error("Invalid revision")
                        val previous=table.firstEvent[owner]
                        if(previous==null || version<(previous[revision] as Long))table.firstEvent[owner]=values
                    }
                }
            }
        }
    }
    private fun checkRead(sql:String) {
        check(active && actual.inTransaction()) {"Audit read scope ended"}
        val text=sql.trim()
        check(text.startsWith("SELECT ",ignoreCase=true) || text=="PRAGMA foreign_key_check" || text=="PRAGMA table_info(supply_container)") {"Audit accepts trusted read queries only"}
    }
    private fun cached(sql:String,args:Array<out Any?>):Cursor? {
        if(args.size!=1)return null
        primaryQueries[sql]?.let { table ->
            val key=args[0] as? String ?: return null
            val row=table.rows[key]
            if(row!=null || table.complete)return table.cursor(row)
        }
        if(sql=="SELECT * FROM `measured_allocation` WHERE ledger_entry_id=?") {
            val key=when(val n=args[0]) {is Long->n;is Int->n.toLong();else->return null}
            tables["measured_allocation"]?.let {table->val row=table.ledger[key];if(row!=null || table.complete)return table.cursor(row)}
        }
        if(sql=="SELECT * FROM `reference_event` WHERE subject_uuid=? ORDER BY revision LIMIT 1") {
            val key=args[0] as? String ?: return null
            tables["reference_event"]?.let {table->if(table.complete)return table.cursor(table.firstEvent[key])}
        }
        return null
    }
    override fun query(sql:String):Cursor {checkRead(sql);return actual.query(sql)}
    override fun query(sql:String,bindArgs:Array<out Any?>):Cursor {checkRead(sql);return cached(sql,bindArgs) ?: actual.query(sql,bindArgs)}
    override fun query(query:SupportSQLiteQuery):Cursor {checkRead(query.sql);return actual.query(query)}
    override fun query(query:SupportSQLiteQuery,cancellationSignal:CancellationSignal?):Cursor {checkRead(query.sql);return actual.query(query,cancellationSignal)}
    /** Called in finally before the auditor can return results. All subsequent use is rejected. */
    fun finish() {
        check(active);active=false
        check(actual.inTransaction() && scalar("SELECT total_changes()")==initialChanges && scalar("PRAGMA schema_version")==initialSchema) {"Database changed during read-only audit"}
    }
    override fun execSQL(sql:String):Unit=error("Read-only audit")
    override fun execSQL(sql:String,bindArgs:Array<out Any?>):Unit=error("Read-only audit")
    override fun compileStatement(sql:String):SupportSQLiteStatement=error("Read-only audit")
    override fun insert(table:String,conflictAlgorithm:Int,values:ContentValues):Long=error("Read-only audit")
    override fun update(table:String,conflictAlgorithm:Int,values:ContentValues,whereClause:String?,whereArgs:Array<out Any?>?):Int=error("Read-only audit")
    override fun delete(table:String,whereClause:String?,whereArgs:Array<out Any?>?):Int=error("Read-only audit")
}
