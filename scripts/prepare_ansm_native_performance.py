#!/usr/bin/env python3
"""Generate an isolated synthetic Android test from the reviewed manual performance template."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'docs/ansm-integration/p4/performance/LargeInventoryPerformanceProbe.kt'
TARGET = ROOT / 'core/data/src/androidTest/java/net/plainnotes/app/data/LargeInventoryPerformanceProbe.kt'
MARKER = '// GENERATED MANUAL NATIVE PROBE: synthetic data only, never production.\n'

def native_source(text):
    def replace(old, new):
        nonlocal text
        if text.count(old) != 1:
            raise ValueError('Performance template drift: expected one occurrence of ' + old[:70])
        text = text.replace(old, new, 1)
    replace('import org.robolectric.RobolectricTestRunner\n', '')
    replace('import org.robolectric.annotation.Config\n', '')
    replace('@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])', '')
    replace('        val db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build()', '')
    replace('Robolectric API35 framework SQLite; not SQLCipher/low-memory API26/hardware', 'Native API26 SQLCipher synthetic fixture; emulator, not physical hardware')
    replace('        val file=File("build/reports/ansm-p4-performance/large-ten-year.json").apply{parentFile.mkdirs()}', '''        val file=File(context.filesDir,"ansm-performance/large-ten-year.json").apply{parentFile.mkdirs()}
        val traceSessionToken=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("ansmTraceToken")
        report.put("diagnostic_run_token",traceSessionToken ?: JSONObject.NULL)
        // Test-only transport admission, before the database/fixture and original acceptance budgets.
        val preflightToken=requireNotNull(traceSessionToken){"Synthetic transport requires host nonce"}
        File(file.parentFile,"transport-probe.bin").writeBytes(byteArrayOf(0,1,127,-128,-1,10,13,0))
        val preflight=JSONObject().put("scope","SYNTHETIC_TRANSPORT_PREFLIGHT_V1").put("token",preflightToken).put("bytes",8)
        File(file.parentFile,"transport-preflight.json").writeText(preflight.toString())
        report.put("stage","TRANSPORT_PREFLIGHT");file.writeText(report.toString(2))
        android.util.Log.i("ANSM_TRACE",preflight.toString())
        val preflightAck=File(file.parentFile,"transport-preflight.ack")
        val preflightDeadline=android.os.SystemClock.elapsedRealtime()+15000
        fun preflightAcknowledged()=runCatching{preflightAck.readText()==preflightToken}.getOrDefault(false)
        while(!preflightAcknowledged() && android.os.SystemClock.elapsedRealtime()<preflightDeadline)Thread.sleep(100)
        if(!preflightAcknowledged()) {
            report.put("status","TRANSPORT_PREFLIGHT_FAILED").put("performance","NOT_RUN")
            file.writeText(report.toString(2))
            throw AssertionError("Synthetic transport preflight failed before performance fixture")
        }
        report.put("transport_preflight","PASS")
        report.put("stage","BEFORE_SQLCIPHER_LOAD");file.writeText(report.toString(2))
        System.loadLibrary("sqlcipher")
        report.put("stage","BEFORE_DATABASE_OPEN");file.writeText(report.toString(2))
        val db=Room.databaseBuilder(context,NotesDatabase::class.java,"synthetic-large-performance.db")
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(ByteArray(32){(it+41).toByte()}))
            .allowMainThreadQueries().addCallback(SchemaGuards).build()''')
    replace('        val running=AtomicBoolean(true);val peak=AtomicLong(0)', '''        val running=AtomicBoolean(true);val peak=AtomicLong(0);val diskPeak=AtomicLong(0)
        val memory=android.app.ActivityManager.MemoryInfo()
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(memory)
        report.put("device_total_ram_bytes",memory.totalMem).put("process_max_heap_bytes",Runtime.getRuntime().maxMemory())
        report.put("database_encryption","SQLCipher").put("api",android.os.Build.VERSION.SDK_INT)''')
    replace('peak.accumulateAndGet(r.totalMemory()-r.freeMemory(),::maxOf);Thread.sleep(10)', '''peak.accumulateAndGet(r.totalMemory()-r.freeMemory(),::maxOf)
                val files=context.getDatabasePath("synthetic-large-performance.db").parentFile?.listFiles().orEmpty()
                diskPeak.accumulateAndGet(files.filter{it.name.startsWith("synthetic-large-performance.db")}.sumOf{it.length()},::maxOf)
                Thread.sleep(10)''')
    replace('report.put("physical_device_acceptance","NOT_RUN").put("api26_low_memory_acceptance","NOT_RUN")', '''report.put("physical_device_acceptance","NOT_RUN").put("api26_low_memory_acceptance","PENDING_BUDGET")
            report.put("peak_observed_database_disk_bytes",diskPeak.get())''')
    replace('if(peak.get()>768L*1024*1024)', 'if(peak.get()>192L*1024*1024)')
    replace('            report.put("budget_exceeded",JSONArray(exceeded))', '''            if(Runtime.getRuntime().maxMemory()>256L*1024*1024 || memory.totalMem>768L*1024*1024)exceeded.add("low_memory_environment")
            if(diskPeak.get()>80L*1024*1024)exceeded.add("database_disk_budget")
            report.put("api26_low_memory_acceptance",if(exceeded.isEmpty())"EMULATOR_BUDGET_PASS" else "FAIL")
            report.put("budget_exceeded",JSONArray(exceeded))''')
    replace('sampler.join(1000);db.close()', 'sampler.join(1000);db.close();context.deleteDatabase("synthetic-large-performance.db")')
    replace('fun stage(name:String){report.put("stage",name);file.writeText(report.toString(2))}',
            'fun stage(name:String){report.put("stage",name).put("observed_heap_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).put("peak_observed_jvm_heap_bytes",peak.get()).put("peak_observed_database_disk_bytes",diskPeak.get());file.writeText(report.toString(2))}')
    # Diagnostics run only AFTER original timed phases and budget/peak capture.
    # Reflection calls the real private full auditor without the optional row cache;
    # it is test-only and never participates in production acceptance or writes.
    replace('file.writeText(report.toString(2));assertEquals("Diagnostic budgets; device acceptance remains NOT_RUN",emptyList<String>(),exceeded)', '''file.writeText(report.toString(2))
            running.set(false);sampler.join(1000)
            val components=JSONObject().put("scope","TEST_ONLY_POST_BUDGET_READ_ONLY")
                .put("acceptance_metrics_frozen_before_diagnostics",true)
            report.put("post_budget_components",components)
            suspend fun component(name:String, block:()->Unit) {
                val start=System.nanoTime()
                db.withTransaction {block()}
                components.put(name,(System.nanoTime()-start)/1_000_000.0)
                file.writeText(report.toString(2))
            }
            val rawAuditor=MeasuredConsumptionStore::class.java.declaredMethods.single {
                it.name=="auditReadSnapshot" && it.parameterTypes.contentEquals(arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java))
            }.apply{isAccessible=true}
            fun rawAudit():List<*> {
                BackupValidation.validateCandidateStoredColumns(raw)
                return rawAuditor.invoke(MeasuredConsumptionStore,raw) as List<*>
            }
            var rawProjection:List<*>?=null
            component("raw_full_first_ms"){rawProjection=rawAudit();assertEquals(count,rawProjection!!.size)}
            component("cached_full_second_ms"){assertEquals(rawProjection,MeasuredConsumptionStore.auditStagedDataset(raw))}
            component("cached_full_first_ms"){assertEquals(rawProjection,MeasuredConsumptionStore.auditStagedDataset(raw))}
            component("raw_full_second_ms"){assertEquals(rawProjection,rawAudit())}
            component("stored_columns_ms"){BackupValidation.validateCandidateStoredColumns(raw)}
            component("raw_references_ms"){ReferenceSchema.validateCandidateReferences(raw)}
            component("raw_state_including_references_ms"){BackupValidation.validateMeasuredCandidateState(raw)}
            component("raw_operation_chain_ms"){MeasuredOperationChain.validate(raw)}
            component("row_cache_construction_finish_ms"){ReferenceAuditReads(raw).finish()}
            component("cached_references_including_construction_ms") {
                val reads=ReferenceAuditReads(raw)
                try{ReferenceSchema.validateCandidateReferences(reads)}finally{reads.finish()}
            }
            // Time the actual private audit methods; these are diagnostics, never acceptance substitutes.
            val presentationType=MeasuredConsumptionStore::class.java.declaredClasses.single{it.simpleName=="PresentationAudit"}
            val presentationConstructor=presentationType.getDeclaredConstructor().apply{isAccessible=true}
            val operationsAuditor=MeasuredConsumptionStore::class.java.declaredMethods.single{it.name=="auditOperations"}.apply{isAccessible=true}
            val projectionAuditor=MeasuredConsumptionStore::class.java.declaredMethods.single{it.name=="auditStagedProjection" && it.parameterTypes.size==5}.apply{isAccessible=true}
            component("cached_operations_with_setup_ms") {
                val reads=ReferenceAuditReads(raw)
                try{operationsAuditor.invoke(MeasuredConsumptionStore,reads,presentationConstructor.newInstance())}finally{reads.finish()}
            }
            // Null capturedBox deliberately retains the original standalone SQL path for this component diagnostic.
            component("cached_projections_with_setup_ms") {
                val reads=ReferenceAuditReads(raw)
                try {
                    val presentations=presentationConstructor.newInstance()
                    val identities=reads.query("SELECT DISTINCT i.uuid,i.dataset_uuid FROM reference_identity i JOIN reference_event e ON e.subject_uuid=i.uuid JOIN reference_snapshot s ON s.uuid=e.snapshot_uuid WHERE i.kind='CONTAINER' AND s.kind='PRESENTATION' ORDER BY i.uuid").use { c ->
                        buildList{while(c.moveToNext())add(c.getString(0) to c.getString(1))}
                    }
                    val values=identities.map { (uuid,dataset) ->
                        val result=projectionAuditor.invoke(MeasuredConsumptionStore,reads,dataset,uuid,presentations,null)
                        require(result is MeasureResult.Success<*>);result.value as AuditedStockProjection
                    }
                    assertEquals(rawProjection,values)
                } finally{reads.finish()}
            }
            // Verify the actual API26 stream plan after all original metrics are frozen.
            component("stream_query_plan_audit_ms") {
                var planned=0
                val plans=JSONArray();val windows=JSONArray()
                var windowStatus="UNAVAILABLE"
                val observed=object:androidx.sqlite.db.SupportSQLiteDatabase by raw {
                    override fun query(sql:String):android.database.Cursor {
                        if(sql.startsWith("SELECT i.uuid,t.id,t.dose_record_id")) {
                            planned++
                            raw.query("EXPLAIN QUERY PLAN "+sql).use { c ->
                                while(c.moveToNext())plans.put(c.getString(c.getColumnIndexOrThrow("detail")))
                            }
                        }
                        val cursor=raw.query(sql)
                        if(!sql.startsWith("SELECT i.uuid,t.id,t.dose_record_id"))return cursor
                        components.put("projection_stream_cursor_type",cursor.javaClass.name)
                        val windowed=cursor as? net.zetetic.database.AbstractWindowedCursor ?: return cursor
                        return object:android.database.CursorWrapper(cursor) {
                            private var lastStart:Int?=null
                            private fun observe(result:Boolean):Boolean {
                                if(result) {
                                    val window=windowed.window
                                    if(window!=null && lastStart!=window.startPosition) {
                                        windowStatus="AVAILABLE";lastStart=window.startPosition
                                        windows.put(JSONObject().put("start_row",window.startPosition)
                                            .put("rows",window.numRows).put("driver_reported_size_bytes",window.windowSizeBytes))
                                    }
                                }
                                return result
                            }
                            override fun moveToFirst()=observe(super.moveToFirst())
                            override fun moveToNext()=observe(super.moveToNext())
                        }
                    }
                }
                assertEquals(rawProjection,MeasuredConsumptionStore.auditStagedDataset(observed))
                assertEquals(1,planned)
                components.put("projection_stream_query_plan",plans)
                components.put("projection_stream_cursor_windows_status",windowStatus)
                components.put("projection_stream_cursor_windows",windows)
            }
            // Diagnostic only: quantify Android decimal-pattern compilation cost before considering reuse.
            val numericTokens=arrayOf("0.5","1","2.00","bad","0,5","1e3")
            val numericPattern="""[0-9]+(?:\\.[0-9]+)?"""
            val numericRegex=Regex(numericPattern)
            component("decimal_regex_reuse_12000_ms") {
                var matches=0;repeat(12000){if(numericRegex.matches(numericTokens[it%numericTokens.size]))matches++}
                assertEquals(6000,matches)
            }
            component("decimal_regex_compile_12000_ms") {
                var matches=0;repeat(12000){if(Regex(numericPattern).matches(numericTokens[it%numericTokens.size]))matches++}
                assertEquals(6000,matches)
            }
            // Measure the actual stale-write guard, never bypass it or use this ticket for a write.
            var diagnosticTicket:MeasuredWriteTicket?=null
            fun scalar(sql:String):Long=raw.query(sql).use{c->check(c.moveToFirst());c.getLong(0)}
            fun readOnlyTicket(action:()->Unit) {
                val beforeChanges=scalar("SELECT total_changes()")
                val beforeSchema=scalar("PRAGMA schema_version")
                action()
                assertEquals(beforeChanges,scalar("SELECT total_changes()"))
                assertEquals(beforeSchema,scalar("PRAGMA schema_version"))
            }
            fun captureTicket()=readOnlyTicket {
                diagnosticTicket=MeasuredUseIntegration.ticket(raw,requireNotNull(ReferenceStore.existingScope(raw)),m)
            }
            fun validateTicket()=readOnlyTicket {
                MeasuredUseIntegration.validate(raw,requireNotNull(diagnosticTicket))
            }
            component("write_ticket_capture_ms"){captureTicket()}
            component("write_ticket_validate_ms"){validateTicket()}
            // Synthetic test only. Time driver calls, excluding processing between cursor calls.
            // Keys are closed source SQL templates; bind arguments and row values are never recorded.
            val sqlReads=linkedMapOf<String,LongArray>()
            fun <T> sqlTimed(sql:String,opening:Boolean=false,action:()->T):T {
                val counters=sqlReads[sql] ?: run {
                    check(sqlReads.size<256)
                    LongArray(3).also{sqlReads[sql]=it}
                }
                counters[if(opening)0 else 1]++
                val start=System.nanoTime()
                try{return action()}finally{counters[2]+=System.nanoTime()-start}
            }
            fun timedCursor(sql:String,open:()->android.database.Cursor):android.database.Cursor {
                val cursor=sqlTimed(sql,true,open)
                return object:android.database.CursorWrapper(cursor) {
                    override fun getCount()=sqlTimed(sql){cursor.count}
                    override fun moveToPosition(position:Int)=sqlTimed(sql){cursor.moveToPosition(position)}
                    override fun moveToFirst()=sqlTimed(sql){cursor.moveToFirst()}
                    override fun moveToNext()=sqlTimed(sql){cursor.moveToNext()}
                    override fun getString(column:Int):String?=sqlTimed(sql){cursor.getString(column)}
                    override fun getLong(column:Int)=sqlTimed(sql){cursor.getLong(column)}
                    override fun getDouble(column:Int)=sqlTimed(sql){cursor.getDouble(column)}
                    override fun getType(column:Int)=sqlTimed(sql){cursor.getType(column)}
                    override fun isNull(column:Int)=sqlTimed(sql){cursor.isNull(column)}
                    override fun getBlob(column:Int):ByteArray?=sqlTimed(sql){cursor.getBlob(column)}
                    override fun close()=sqlTimed(sql){cursor.close()}
                }
            }
            val profiled=object:androidx.sqlite.db.SupportSQLiteDatabase by raw {
                override fun query(sql:String)=timedCursor(sql){raw.query(sql)}
                override fun query(sql:String,bindArgs:Array<out Any?>)=timedCursor(sql){raw.query(sql,bindArgs)}
                override fun query(query:androidx.sqlite.db.SupportSQLiteQuery)=timedCursor(query.sql){raw.query(query)}
                override fun query(query:androidx.sqlite.db.SupportSQLiteQuery,cancellationSignal:android.os.CancellationSignal?)=
                    timedCursor(query.sql){raw.query(query,cancellationSignal)}
            }
            component("sql_driver_timing_audit_ms") {readOnlyTicket {
                assertEquals(rawProjection,MeasuredConsumptionStore.auditStagedDataset(profiled))
            }}
            check(sqlReads.isNotEmpty())
            val sqlTimings=JSONArray()
            sqlReads.entries.sortedByDescending{it.value[2]}.forEach{(sql,counters)->
                check(counters[0]>0 && counters[1]>0 && counters[2]>=0)
                sqlTimings.put(JSONObject().put("sql_sha256",ReferencePayload.sha(sql))
                    .put("sql_prefix",sql.take(512)).put("sql_prefix_truncated",sql.length>512)
                    .put("queries",counters[0]).put("cursor_calls",counters[1]).put("driver_ns",counters[2]))
            }
            components.put("sql_driver_timings",sqlTimings)
                .put("sql_driver_timing_scope","SYNTHETIC_POST_BUDGET_DRIVER_CALL_TIMING")
            val trace=File(file.parentFile,"audit-sampling.trace")
            android.os.Debug.startMethodTracingSampling(trace.absolutePath,16*1024*1024,1000)
            try {
                component("sampled_cached_full_audit_ms") {
                    assertEquals(rawProjection,MeasuredConsumptionStore.auditStagedDataset(raw))
                }
                component("sampled_write_ticket_capture_ms"){captureTicket()}
                component("sampled_write_ticket_validate_ms"){validateTicket()}
            } finally{android.os.Debug.stopMethodTracing()}
            val traceToken=traceSessionToken ?: UUID.randomUUID().toString()
            components.put("sampling_trace",JSONObject().put("file","audit-sampling.trace").put("bytes",trace.length()).put("token",traceToken)
                .put("interval_us",1000).put("scope","TEST_ONLY_POST_BUDGET_METHOD_STACKS_NO_ARGUMENTS"))
            components.put("status","COMPLETE")
            file.writeText(report.toString(2))
            File(file.parentFile,"trace-ready.json").writeText(JSONObject().put("scope","SYNTHETIC_ART_INTERNAL_TRACE_READY_V1")
                .put("token",traceToken).put("bytes",trace.length()).toString())
            android.util.Log.i("ANSM_TRACE",JSONObject().put("scope","SYNTHETIC_ART_INTERNAL_TRACE_READY_V1")
                .put("token",traceToken).put("bytes",trace.length()).toString())
            val ack=File(file.parentFile,"trace-collected.ack")
            val deadline=android.os.SystemClock.elapsedRealtime()+30000
            fun acknowledged()=runCatching{ack.readText()==traceToken}.getOrDefault(false)
            while(!acknowledged() && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100)
            components.put("sampling_host_ack",acknowledged())
            file.writeText(report.toString(2));assertEquals("Diagnostic budgets; device acceptance remains NOT_RUN",emptyList<String>(),exceeded)''')
    # AGP may uninstall the synthetic test APK after a process crash, deleting
    # its external-files report. Mirror only the compact timing metadata to logcat.
    text = text.replace('file.writeText(report.toString(2))',
                        'file.writeText(report.toString(2));android.util.Log.i("ANSM_PERF",report.toString())')
    return MARKER + text

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--emit', type=Path, help='Write outside source tree for inspection without modifying build inputs')
    parser.add_argument('--clean', action='store_true')
    args = parser.parse_args()
    if args.clean:
        if TARGET.exists():
            if not TARGET.read_text().startswith(MARKER):
                raise SystemExit('Refusing to remove an unrecognized source')
            TARGET.unlink()
    else:
        target = args.emit or TARGET
        if target.exists():
            raise SystemExit('Refusing to overwrite existing source')
        text = native_source(SOURCE.read_text())
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
        print(json.dumps({'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                          'native_sha256': hashlib.sha256(text.encode()).hexdigest(), 'status': 'GENERATED_NOT_RUN'}))
