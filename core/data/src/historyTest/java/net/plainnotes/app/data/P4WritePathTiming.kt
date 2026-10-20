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
        val invalidReasons=linkedSetOf<String>()
        fun invalidate(reason:String){invalid=true;invalidReasons.add(reason)}
    }
    private class Root(val label:String,val start:Long) {
        val threads=ConcurrentHashMap<Long,ThreadData>()
        @Volatile var owner:Long?=null
        @Volatile var closed=false
        var overflow=false
        val overflowReasons=ConcurrentHashMap.newKeySet<String>()
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
        val r=requireNotNull(root)
        return synchronized(r) {finishLocked(r)}
    }
    private fun finishLocked(r:Root):JSONObject {
        val stop=System.nanoTime();r.closed=true;root=null
        val thread=r.owner?.let{r.threads[it]}
        val out=JSONObject().put("scope",r.label).put("wall_ns",stop-r.start)
            .put("checkpoint_offsets_nested_ns",JSONObject(r.checkpoints as Map<*,*>))
            .put("owner_thread_observed",thread!=null).put("background_threads",r.threads.size-(if(thread==null)0 else 1))
            .put("trigger_exclusive_ns",JSONObject.NULL).put("pure_native_commit_ns",JSONObject.NULL)
            .put("cursor_iteration_timing","NOT_INSTRUMENTED_USE_EXISTING_READ_ONLY_EVIDENCE")
        if(thread==null)return out.put("accounting_valid",false)
        val spans=thread.spans.sortedBy{it.start}
        var previous=r.start
        for(s in spans) {if(s.start<previous || s.end<s.start || s.end>stop)thread.invalidate("overlapping_or_out_of_root_core");previous=s.end}
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
        if(residual<0)thread.invalidate("negative_residual")
        if(thread.frames.any{it.controlled})thread.invalidate("unfinished_controlled_transaction_frame")
        if(r.overflow)thread.invalidate("bounded_observer_overflow")
        if(r.threads.values.any{it.invalid})thread.invalidate("invalid_observed_thread")
        if(thread.auditsClosed!=spans.count{it.kind=="audit"})thread.invalidate("audit_finish_count_mismatch")
        return out.put("accounting_valid",!thread.invalid)
            .put("accounting_invalid_reasons",JSONArray(thread.invalidReasons.toList()+r.overflowReasons.toList().sorted()))
            .put("transaction_success_without_observed_begin",thread.untrackedTransactionSuccesses)
            .put("uncontrolled_frames_at_stop",thread.frames.count{!it.controlled})
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

    private fun context(entry:Boolean=false,forceStack:Boolean=false):Context? {
        val r=root ?: return null
        return synchronized(r) {contextLocked(r,entry,forceStack)}
    }
    private fun contextLocked(r:Root,entry:Boolean,forceStack:Boolean):Context? {
        if(r.closed)return null
        val before=System.nanoTime();val id=Thread.currentThread().id
        if(r.threads.size>=16 && !r.threads.containsKey(id)){r.overflow=true;r.overflowReasons.add("thread_limit");return null}
        val t=r.threads.getOrPut(id){ThreadData()}
        // The synchronous audit has a mandatory finish marker. Keep attribution until it
        // closes, without sampling a Java stack on every SQL query. Writes still invalidate
        // the read core in timed(); missing/exceptional finish markers reject accounting.
        if(!entry && !forceStack && t.current?.kind=="audit") {
            t.observer+=System.nanoTime()-before
            return Context(r,t,t.current,"audit",t.frames.firstOrNull()?.controlled==true,false)
        }
        val stack=Thread.currentThread().stackTrace
        val tag=when {
            stack.any{it.className==STORE && method(it,"auditStagedDataset")}->"audit"
            stack.any{it.className==INTEGRATION && method(it,"validate")}->"validation"
            stack.any{it.className==INTEGRATION && method(it,"ticket")}->"ticket"
            else->"other"
        }
        val caller=stack.firstOrNull{!it.className.startsWith(javaClass.name) && it.className!="java.lang.Thread" && it.className!="dalvik.system.VMStack" &&
            !method(it,"inTransaction")}
        val begins=entry && ((caller?.className==STORE && method(caller,"auditStagedDataset")) ||
            (caller?.className==INTEGRATION && method(caller,"validate")))
        if(begins || (entry && r.label=="ticket" && caller?.className==INTEGRATION && method(caller,"ticket"))) {
            // Only a transaction containing the real controlled method owns our COMMIT/DML.
            // Earlier Room work can finish on the same executor while the caller is waiting.
            if(t.frames.isEmpty())t.invalidate("controlled_entry_without_begin") else t.frames[0].controlled=true
        }
        if(tag!=t.current?.kind)t.current=null
        if(begins) {
            if(t.spans.size>=32){r.overflow=true;r.overflowReasons.add("core_limit")} else Span(tag,before).also{t.spans.add(it);t.current=it}
        }
        if((r.label=="ticket" && tag=="ticket") || (r.label!="ticket" && tag in setOf("validation","audit"))) {
            if(r.owner==null)r.owner=id else if(r.owner!=id)t.invalidate("controlled_owner_changed")
        }
        t.observer+=System.nanoTime()-before
        return Context(r,t,t.current,tag,t.frames.firstOrNull()?.controlled==true,
            tag=="audit" && stack.any{it.className=="net.plainnotes.app.data.ReferenceAuditReads" && method(it,"finish")})
    }
    private fun <T> timed(c:Context?,kind:String,sql:String="",action:()->T):T {
        if(c==null || c.root.closed)return action()
        // Only a static, bounded template digest; never SQL bindings, values or records.
        val values=synchronized(c.root) {
        if(c.root.closed)return action()
        val before=System.nanoTime()
        val digest=if(sql.isEmpty())"transaction" else c.thread.digests[sql] ?: ReferencePayload.sha(sql).also {
            if(c.thread.digests.size<256) {c.thread.digests[sql]=it;c.thread.prefixes[it]=sql.take(160)} else {c.root.overflow=true;c.root.overflowReasons.add("sql_template_limit")}
        }
        val observedKind=if(!c.controlled && kind in setOf("insert","update","delete"))"background_$kind" else kind
        val key=(if(c.span==null)"other" else c.span.kind)+"|$observedKind|"+digest
        val result=c.thread.driver[key] ?: if(c.thread.driver.size<1024)LongArray(2).also{c.thread.driver[key]=it} else {
            c.root.overflow=true;c.root.overflowReasons.add("driver_key_limit");null
        }
        c.thread.observer+=System.nanoTime()-before
        result
        }
        val start=System.nanoTime()
        try{return action()}finally {
            val end=System.nanoTime()
            synchronized(c.root) {if(!c.root.closed) {
                values?.let{it[0]++;it[1]+=end-start}
                c.span?.let{it.end=maxOf(it.end,end)}
                if(c.span!=null && kind in setOf("insert","update","delete","commit","rollback"))c.thread.invalidate("write_inside_read_core")
            }}
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
            val c=context();timed(c,kind,action=action)
            if(c!=null)synchronized(c.root){if(!c.root.closed)c.thread.frames.add(Frame())}
        }
        override fun beginTransaction()=begin("begin"){db.beginTransaction()}
        override fun beginTransactionNonExclusive()=begin("begin"){db.beginTransactionNonExclusive()}
        override fun beginTransactionWithListener(listener:SQLiteTransactionListener)=begin("begin"){db.beginTransactionWithListener(listener)}
        override fun beginTransactionWithListenerNonExclusive(listener:SQLiteTransactionListener)=begin("begin"){db.beginTransactionWithListenerNonExclusive(listener)}
        override fun setTransactionSuccessful() {
            val c=context();timed(c,"mark_success"){db.setTransactionSuccessful()}
            if(c!=null)synchronized(c.root){if(!c.root.closed)c.thread.frames.let {
                if(it.isEmpty()) {
                    c.thread.untrackedTransactionSuccesses++
                    if(c.span!=null)c.thread.invalidate("untracked_success_inside_core")
                } else it[it.lastIndex].successful=true
            }}
        }
        override fun endTransaction() {
            val c=context();val frames=c?.thread?.frames
            val kind=if(c==null)"finish_unknown" else synchronized(c.root) {
                when {frames==null || frames.isEmpty()->"finish_unknown";frames.size>1->"nested_finish"
                    !frames[0].controlled->"background_finish";frames[0].successful->"commit";else->"rollback"}
            }
            try{timed(c,kind){db.endTransaction()}}finally{if(c!=null)synchronized(c.root){
                if(!c.root.closed && !frames.isNullOrEmpty())frames.removeAt(frames.lastIndex)
            }}
        }
        private fun cursor(sql:String,open:()->Cursor):Cursor {
            val c=context(forceStack=sql=="PRAGMA schema_version");val opened=System.nanoTime();val cursor=timed(c,"query_open",sql,open)
            if(c==null)return cursor
            // checkedChain has one SELECT in the frozen source, including the audit hot path
            // whose repeated inTransaction calls intentionally do not collect stacks.
            synchronized(c.root){if(!c.root.closed && (sql==CHAIN_SELECT || sql==CHAIN_HEADER_SELECT))c.thread.chainChecks++}
            return object:CursorWrapper(cursor) {
                override fun close() {
                    timed(c,"cursor_close",sql){cursor.close()}
                    synchronized(c.root) {
                    if(c.auditFinish && sql=="PRAGMA schema_version" && !c.root.closed) {
                        c.thread.auditsClosed++
                        if(c.thread.current===c.span)c.thread.current=null
                    }
                    if(sql=="PRAGMA foreign_key_check" && c.tag=="audit" && !c.root.closed) {
                        if(c.thread.foreignKeys.size<32)c.thread.foreignKeys.add(System.nanoTime()-opened) else {c.root.overflow=true;c.root.overflowReasons.add("fk_window_limit")}
                    }
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
        private const val CHAIN_SELECT="SELECT operation_uuid,input_json FROM measured_operation"
        private val CHAIN_HEADER_SELECT="SELECT "+ReferenceSchema.columns.getValue("measured_operation").joinToString(","){"`$it`"}+" FROM measured_operation"
    }
}
