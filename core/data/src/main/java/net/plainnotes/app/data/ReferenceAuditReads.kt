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
        val rows=if(name=="measured_allocation" || name=="record_reference_capture")LinkedHashMap<String,Array<Any?>>() else HashMap<String,Array<Any?>>()
        val ledger=HashMap<Long,Array<Any?>>()
        var secondary:SortedRows?=null
        val firstEvent=HashMap<String,Array<Any?>>()
        var complete=true
        fun cursor(row:Array<Any?>?)=MatrixCursor(columns.copyOf(),if(row==null)0 else 1).apply {if(row!=null)addRow(row)}
    }
    /** References existing immutable rows; no duplicated strings, composite keys or per-group maps. */
    private class SortedRows(table:Table,keys:Array<String>,filter:(Array<Any?>)->Boolean) {
        private data class Entry(val row:Array<Any?>,val ordinal:Int)
        private val fields=keys.map{table.columns.indexOf(it)}
        private val rows=table.rows.values.withIndex().filter{filter(it.value)}.map{Entry(it.value,it.index)}.toTypedArray()
        private fun compare(row:Array<Any?>,key:Array<out Any?>):Int {
            for(i in key.indices) {
                val result=when(val value=row[fields[i]]) {is String->value.compareTo(key[i] as String);is Long->value.compareTo(key[i] as Long);else->error("Invalid secondary key")}
                if(result!=0)return result
            }
            return 0
        }
        init {rows.sortWith {a,b->
            var result=0
            for(field in fields) {
                result=when(val value=a.row[field]){is String->value.compareTo(b.row[field] as String);is Long->value.compareTo(b.row[field] as Long);else->error("Invalid secondary key")}
                if(result!=0)break
            }
            if(result==0)a.ordinal.compareTo(b.ordinal) else result
        }}
        private fun bound(key:Array<out Any?>,upper:Boolean):Int {
            var low=0;var high=rows.size
            while(low<high) {val middle=(low+high) ushr 1;val c=compare(rows[middle].row,key);if(c<0 || upper && c==0)low=middle+1 else high=middle}
            return low
        }
        fun count(key:Array<out Any?>)=(bound(key,true)-bound(key,false)).toLong()
        fun values(key:Array<out Any?>):List<Array<Any?>> = rows.asList().subList(bound(key,false),bound(key,true)).sortedBy{it.ordinal}.map{it.row}
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
                    val secondaryCost=if(name=="measured_allocation" || name=="record_reference_capture")32L else 0L
                    val cost=192L+values.size*16L+newStrings.sumOf{112L+it.length*2L}+secondaryCost
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
        // Primitive rows have priority. Never displace them to make room for secondary indexes.
        for(name in listOf("measured_allocation","record_reference_capture")) {
            val table=tables[name] ?: continue
            val cost=512L+table.rows.size*128L // entry/reference arrays plus sort workspace, conservatively reserved
            if(!table.complete || cost>byteBudget-estimatedBytes)continue
            estimatedBytes+=cost
            val fields=if(name=="measured_allocation")arrayOf("operation_uuid","container_uuid") else arrayOf("record_uuid","record_revision","container_uuid")
            val kind=table.columns.indexOf("kind")
            table.secondary=SortedRows(table,fields){kind<0 || it[kind]=="CONFIRM"}
        }
    }
    private fun checkRead(sql:String) {
        check(active && actual.inTransaction()) {"Audit read scope ended"}
        val text=sql.trim()
        check(text.startsWith("SELECT ",ignoreCase=true) || text=="PRAGMA foreign_key_check" || text=="PRAGMA table_info(supply_container)") {"Audit accepts trusted read queries only"}
    }
    private fun secondary(sql:String,args:Array<out Any?>):Cursor? {
        val allocation=tables["measured_allocation"]
        if(allocation?.complete==true && allocation.secondary!=null) {
            if(sql=="SELECT COUNT(*) FROM measured_allocation WHERE operation_uuid=? AND container_uuid=?" && args.size==2 && args.all{it is String}) {
                val count=allocation.secondary!!.count(args)
                return MatrixCursor(arrayOf("COUNT(*)"),1).apply{addRow(arrayOf(count))}
            }
            val ordered=sql=="SELECT uuid FROM measured_allocation WHERE operation_uuid=? ORDER BY uuid"
            if((ordered || sql=="SELECT uuid FROM `measured_allocation` WHERE operation_uuid=?") && args.size==1 && args[0] is String) {
                val column=allocation.columns.indexOf("uuid")
                val rows=allocation.secondary!!.values(args)
                val values=rows.map{it[column] as String}.let{if(ordered)it.sorted() else it}
                return MatrixCursor(arrayOf("uuid"),values.size).apply{values.forEach{addRow(arrayOf(it))}}
            }
        }
        val captures=tables["record_reference_capture"]
        if(captures?.complete==true && captures.secondary!=null && sql=="SELECT uuid FROM `record_reference_capture` WHERE record_uuid=? AND record_revision=? AND kind='CONFIRM' AND container_uuid=?" && args.size==3 && args[0] is String && args[2] is String) {
            val revision=when(val n=args[1]){is Long->n;is Int->n.toLong();else->return null}
            val rows=captures.secondary!!.values(arrayOf(args[0],revision,args[2]))
            val column=captures.columns.indexOf("uuid")
            return MatrixCursor(arrayOf("uuid"),rows.size).apply{rows.forEach{addRow(arrayOf(it[column]))}}
        }
        return null
    }
    private fun cached(sql:String,args:Array<out Any?>):Cursor? {
        secondary(sql,args)?.let{return it}
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
