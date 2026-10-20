package net.plainnotes.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.CursorWrapper
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import androidx.sqlite.db.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/** TEST ONLY. Observe the real Room/SQLCipher path; never changes SQL, transactions or validation.
 * Driver times include native triggers. Java method windows are explicitly LOWER bounds between
 * observed entry/last driver return, not invented exact method-exit timings. No bindings are retained.
 */
internal class P4WritePathTiming {
    private class Span(val kind:String,val start:Long) {var end=start}
    private class Frame(var successful:Boolean=false,var controlled:Boolean=false)
    private class ThreadData {
        val frames=ArrayList<Frame>()
        val spans=ArrayList<Span>()
        var current:Span?=null
        val driver=linkedMapOf<String,LongArray>()
        val digests=HashMap<String,String>()
        val prefixes=HashMap<String,String>()
        val foreignKeys=ArrayList<Long>()
        var observer=0L;var chainChecks=0;var invalid=false;var untrackedTransactionSuccesses=0;var auditsClosed=0
    }
    private class Root(val label:String,val start:Long) {
        val threads=ConcurrentHashMap<Long,ThreadData>()
        @Volatile var owner:Long?=null
        @Volatile var closed=false
        var overflow=false
        val checkpoints=linkedMapOf<String,Long>()
    }
    private data class Context(val root:Root,val thread:ThreadData,val span:Span?,val tag:String,val controlled:Boolean,val auditFinish:Boolean)
    // Kotlin internal functions carry a module suffix; Robolectric prefixes instrumented methods.
    // Match the exact '$'-delimited original method, not a caller substring or sampled frame count.
    private fun method(frame:StackTraceElement,name:String):Boolean {
        val value=frame.methodName;val at=value.indexOf(name);val end=at+name.length
        return at>=0 && (at==0 || value[at-1]=='$') && (end==value.length || value[end]=='$')
    }
    @Volatile private var root:Root?=null

    fun start(label:String) {check(root==null);root=Root(label,System.nanoTime())}
    fun checkpoint(name:String) {root?.let {r->synchronized(r.checkpoints){check(r.checkpoints.size<16);r.checkpoints[name]=System.nanoTime()-r.start}}}
    fun finish():JSONObject {
        val stop=System.nanoTime();val r=requireNotNull(root);r.closed=true;root=null
        val thread=r.owner?.let{r.threads[it]}
        val out=JSONObject().put("scope",r.label).put("wall_ns",stop-r.start)
            .put("checkpoint_offsets_nested_ns",JSONObject(r.checkpoints as Map<*,*>))
            .put("owner_thread_observed",thread!=null).put("background_threads",r.threads.size-(if(thread==null)0 else 1))
            .put("trigger_exclusive_ns",JSONObject.NULL).put("pure_native_commit_ns",JSONObject.NULL)
            .put("cursor_iteration_timing","NOT_INSTRUMENTED_USE_EXISTING_READ_ONLY_EVIDENCE")
        if(thread==null)return out.put("accounting_valid",false)
        val spans=thread.spans.sortedBy{it.start}
        var previous=r.start
        for(s in spans) {if(s.start<previous || s.end<s.start || s.end>stop)thread.invalid=true;previous=s.end}
        val retainedKinds=setOf("insert","update","delete","commit","rollback","nested_finish","begin","background_finish","finish_unknown")
        val selected=thread.driver.entries.filter{it.key.split('|')[1] in retainedKinds}.take(40)+
            thread.driver.entries.filter{it.key.split('|')[1] !in retainedKinds}.sortedByDescending{it.value[1]}.take(8)
        val details=JSONObject();selected.forEach{(k,v)->details.put(k,JSONObject().put("calls",v[0]).put("ns",v[1]).put("sql_prefix",thread.prefixes[k.substringAfterLast('|')] ?: ""))}
        val audit=spans.filter{it.kind=="audit"}.sumOf{it.end-it.start}
        val validation=spans.filter{it.kind=="validation"}.sumOf{it.end-it.start}
        fun outside(kind:String)=thread.driver.filterKeys{it.startsWith("other|$kind|")}.values.sumOf{it[1]}
        val inserts=outside("insert");val updates=outside("update");val deletes=outside("delete")
        val commit=outside("commit");val rollback=outside("rollback")
        val allOutside=thread.driver.filterKeys{it.startsWith("other|")}.values.sumOf{it[1]}
        val residual=stop-r.start-audit-validation-allOutside
        if(residual<0 || thread.frames.isNotEmpty() || r.overflow || thread.auditsClosed!=spans.count{it.kind=="audit"})thread.invalid=true
        return out.put("accounting_valid",!thread.invalid)
            .put("transaction_success_without_observed_begin",thread.untrackedTransactionSuccesses)
            .put("audit_finish_markers",thread.auditsClosed)
            .put("audit_calls",spans.count{it.kind=="audit"}).put("validation_calls",spans.count{it.kind=="validation"})
            .put("chain_checks",thread.chainChecks).put("audit_core_lower_bound_ns",audit)
            .put("validation_core_lower_bound_ns",validation)
            .put("insert_including_triggers_ns",inserts).put("update_including_triggers_ns",updates)
            .put("delete_including_triggers_ns",deletes).put("commit_driver_ns",commit).put("rollback_driver_ns",rollback)
            .put("other_driver_outside_cores_ns",allOutside-inserts-updates-deletes-commit-rollback)
            .put("unattributed_java_read_cursors_dispatch_observer_and_method_tails_ns",residual)
            .put("observer_accounting_lower_bound_ns",thread.observer)
            .put("foreign_key_query_windows_nested_ns",JSONArray(thread.foreignKeys))
            .put("driver_detail_omitted_keys",thread.driver.size-selected.size)
            .put("driver_detail_nested_do_not_add_to_cores",details)
            .put("core_windows",JSONArray(spans.map{JSONObject().put("kind",it.kind).put("start_ns",it.start-r.start).put("end_ns",it.end-r.start)}))
    }

    private fun context(entry:Boolean=false):Context? {
        val r=root ?: return null;if(r.closed)return null
        val before=System.nanoTime();val id=Thread.currentThread().id
        if(r.threads.size>=16 && !r.threads.containsKey(id)){r.overflow=true;return null}
        val t=r.threads.getOrPut(id){ThreadData()}
        val stack=Thread.currentThread().stackTrace
        val tag=when {
            stack.any{it.className==STORE && method(it,"auditStagedDataset")}->"audit"
            stack.any{it.className==INTEGRATION && method(it,"validate")}->"validation"
            stack.any{it.className==INTEGRATION && method(it,"ticket")}->"ticket"
            else->"other"
        }
        val caller=stack.firstOrNull{!it.className.startsWith(javaClass.name) && it.className!="java.lang.Thread" && it.className!="dalvik.system.VMStack" &&
            !(it.className=="net.plainnotes.app.data.ReferenceAuditReads" && method(it,"inTransaction"))}
        if(entry && caller?.className==CHAIN && method(caller,"checkedChain"))t.chainChecks++
        val begins=entry && ((caller?.className==STORE && method(caller,"auditStagedDataset")) ||
            (caller?.className==INTEGRATION && method(caller,"validate")))
        if(begins || (entry && r.label=="ticket" && caller?.className==INTEGRATION && method(caller,"ticket"))) {
            // Only a transaction containing the real controlled method owns our COMMIT/DML.
            // Earlier Room work can finish on the same executor while the caller is waiting.
            if(t.frames.isEmpty())t.invalid=true else t.frames[0].controlled=true
        }
        if(tag!=t.current?.kind)t.current=null
        if(begins) {
            if(t.spans.size>=32)r.overflow=true else Span(tag,before).also{t.spans.add(it);t.current=it}
        }
        if((r.label=="ticket" && tag=="ticket") || (r.label!="ticket" && tag in setOf("validation","audit"))) {
            if(r.owner==null)r.owner=id else if(r.owner!=id)t.invalid=true
        }
        t.observer+=System.nanoTime()-before
        return Context(r,t,t.current,tag,t.frames.firstOrNull()?.controlled==true,
            tag=="audit" && stack.any{it.className=="net.plainnotes.app.data.ReferenceAuditReads" && method(it,"finish")})
    }
    private fun <T> timed(c:Context?,kind:String,sql:String="",action:()->T):T {
        if(c==null || c.root.closed)return action()
        // Only a static, bounded template digest; never SQL bindings, values or records.
        val before=System.nanoTime()
        val digest=if(sql.isEmpty())"transaction" else c.thread.digests[sql] ?: ReferencePayload.sha(sql).also {
            if(c.thread.digests.size<256) {c.thread.digests[sql]=it;c.thread.prefixes[it]=sql.take(160)} else c.root.overflow=true
        }
        val observedKind=if(!c.controlled && kind in setOf("insert","update","delete"))"background_$kind" else kind
        val key=(if(c.span==null)"other" else c.span.kind)+"|$observedKind|"+digest
        val values=c.thread.driver[key] ?: if(c.thread.driver.size<1024)LongArray(2).also{c.thread.driver[key]=it} else {
            c.root.overflow=true;null
        }
        c.thread.observer+=System.nanoTime()-before
        val start=System.nanoTime()
        try{return action()}finally {
            val end=System.nanoTime()
            if(!c.root.closed) {
                values?.let{it[0]++;it[1]+=end-start}
                c.span?.let{it.end=maxOf(it.end,end)}
                if(c.span!=null && kind in setOf("insert","update","delete","commit","rollback"))c.thread.invalid=true
            }
        }
    }
    private fun kind(sql:String):String {
        val s=sql.trimStart().uppercase(java.util.Locale.ROOT)
        return when {s.startsWith("INSERT")->"insert";s.startsWith("UPDATE")->"update";s.startsWith("DELETE")->"delete";else->"exec_other"}
    }
    fun factory(delegate:SupportSQLiteOpenHelper.Factory):SupportSQLiteOpenHelper.Factory = object:SupportSQLiteOpenHelper.Factory {
        override fun create(configuration:SupportSQLiteOpenHelper.Configuration):SupportSQLiteOpenHelper {
            val helper=delegate.create(configuration)
            return object:SupportSQLiteOpenHelper by helper {
                val wrappers=IdentityHashMap<SupportSQLiteDatabase,SupportSQLiteDatabase>()
                fun wrap(db:SupportSQLiteDatabase):SupportSQLiteDatabase=synchronized(wrappers){wrappers.getOrPut(db){Database(db)}}
                override val writableDatabase:SupportSQLiteDatabase get()=wrap(helper.writableDatabase)
                override val readableDatabase:SupportSQLiteDatabase get()=wrap(helper.readableDatabase)
            }
        }
    }
    private inner class Database(private val db:SupportSQLiteDatabase):SupportSQLiteDatabase by db {
        override fun inTransaction():Boolean {
            // The full auditor performs ~50,000 hot checks. They still execute unchanged, but
            // do not capture stacks/counters. The final read-only finish fence closes this span.
            val r=root
            if(r!=null && !r.closed && r.threads[Thread.currentThread().id]?.current?.kind=="audit")return db.inTransaction()
            val c=context(true);return timed(c,"in_transaction"){db.inTransaction()}
        }
        private fun begin(kind:String,action:()->Unit) {
            val c=context();timed(c,kind,action=action);c?.thread?.frames?.add(Frame())
        }
        override fun beginTransaction()=begin("begin"){db.beginTransaction()}
        override fun beginTransactionNonExclusive()=begin("begin"){db.beginTransactionNonExclusive()}
        override fun beginTransactionWithListener(listener:SQLiteTransactionListener)=begin("begin"){db.beginTransactionWithListener(listener)}
        override fun beginTransactionWithListenerNonExclusive(listener:SQLiteTransactionListener)=begin("begin"){db.beginTransactionWithListenerNonExclusive(listener)}
        override fun setTransactionSuccessful() {
            val c=context();timed(c,"mark_success"){db.setTransactionSuccessful()}
            c?.thread?.frames?.let {
                if(it.isEmpty()) {
                    c.thread.untrackedTransactionSuccesses++
                    if(c.span!=null)c.thread.invalid=true
                } else it[it.lastIndex].successful=true
            }
        }
        override fun endTransaction() {
            val c=context();val frames=c?.thread?.frames
            val kind=when {frames==null || frames.isEmpty()->"finish_unknown";frames.size>1->"nested_finish"
                !frames[0].controlled->"background_finish";frames[0].successful->"commit";else->"rollback"}
            try{timed(c,kind){db.endTransaction()}}finally{if(!frames.isNullOrEmpty())frames.removeAt(frames.lastIndex)}
        }
        private fun cursor(sql:String,open:()->Cursor):Cursor {
            val c=context();val opened=System.nanoTime();val cursor=timed(c,"query_open",sql,open)
            if(c==null)return cursor
            return object:CursorWrapper(cursor) {
                override fun close() {
                    timed(c,"cursor_close",sql){cursor.close()}
                    if(c.auditFinish && sql=="PRAGMA schema_version" && !c.root.closed) {
                        c.thread.auditsClosed++
                        if(c.thread.current===c.span)c.thread.current=null
                    }
                    if(sql=="PRAGMA foreign_key_check" && c.tag=="audit" && !c.root.closed) {
                        if(c.thread.foreignKeys.size<32)c.thread.foreignKeys.add(System.nanoTime()-opened) else c.root.overflow=true
                    }
                }
            }
        }
        override fun query(sql:String)=cursor(sql){db.query(sql)}
        override fun query(sql:String,bindArgs:Array<out Any?>)=cursor(sql){db.query(sql,bindArgs)}
        override fun query(query:SupportSQLiteQuery)=cursor(query.sql){db.query(query)}
        override fun query(query:SupportSQLiteQuery,cancellationSignal:CancellationSignal?)=cursor(query.sql){db.query(query,cancellationSignal)}
        override fun compileStatement(sql:String):SupportSQLiteStatement {
            val stmt=timed(context(),"prepare",sql){db.compileStatement(sql)}
            // Always wrap: Room may cache a statement before the recorder is enabled.
            return object:SupportSQLiteStatement by stmt {
                override fun execute()=timed(context(),kind(sql),sql){stmt.execute()}
                override fun executeInsert()=timed(context(),"insert",sql){stmt.executeInsert()}
                override fun executeUpdateDelete()=timed(context(),kind(sql),sql){stmt.executeUpdateDelete()}
                override fun simpleQueryForLong()=timed(context(),"scalar",sql){stmt.simpleQueryForLong()}
                override fun simpleQueryForString()=timed(context(),"scalar",sql){stmt.simpleQueryForString()}
            }
        }
        override fun execSQL(sql:String)=timed(context(),kind(sql),sql){db.execSQL(sql)}
        override fun execSQL(sql:String,bindArgs:Array<out Any?>)=timed(context(),kind(sql),sql){db.execSQL(sql,bindArgs)}
        override fun insert(table:String,conflictAlgorithm:Int,values:ContentValues)=timed(context(),"insert","INSERT $table"){db.insert(table,conflictAlgorithm,values)}
        override fun update(table:String,conflictAlgorithm:Int,values:ContentValues,whereClause:String?,whereArgs:Array<out Any?>?)=
            timed(context(),"update","UPDATE $table"){db.update(table,conflictAlgorithm,values,whereClause,whereArgs)}
        override fun delete(table:String,whereClause:String?,whereArgs:Array<out Any?>?)=timed(context(),"delete","DELETE $table"){db.delete(table,whereClause,whereArgs)}
    }
    companion object {
        private const val STORE="net.plainnotes.app.data.MeasuredConsumptionStore"
        private const val INTEGRATION="net.plainnotes.app.data.MeasuredUseIntegration"
        private const val CHAIN="net.plainnotes.app.data.MeasuredOperationChain"
    }
}
