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
    replace('        val file=File("build/reports/ansm-p4-performance/large-ten-year.json").apply{parentFile.mkdirs()}', '''        val file=File(context.getExternalFilesDir(null),"ansm-performance/large-ten-year.json").apply{parentFile.mkdirs()}
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
    # Reflection calls the unchanged private full auditor without the optional row cache;
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
            val projectionAuditor=MeasuredConsumptionStore::class.java.declaredMethods.single{it.name=="auditStagedProjection" && it.parameterTypes.size==4}.apply{isAccessible=true}
            component("cached_operations_with_setup_ms") {
                val reads=ReferenceAuditReads(raw)
                try{operationsAuditor.invoke(MeasuredConsumptionStore,reads,presentationConstructor.newInstance())}finally{reads.finish()}
            }
            component("cached_projections_with_setup_ms") {
                val reads=ReferenceAuditReads(raw)
                try {
                    val presentations=presentationConstructor.newInstance()
                    val identities=reads.query("SELECT DISTINCT i.uuid,i.dataset_uuid FROM reference_identity i JOIN reference_event e ON e.subject_uuid=i.uuid JOIN reference_snapshot s ON s.uuid=e.snapshot_uuid WHERE i.kind='CONTAINER' AND s.kind='PRESENTATION' ORDER BY i.uuid").use { c ->
                        buildList{while(c.moveToNext())add(c.getString(0) to c.getString(1))}
                    }
                    val values=identities.map { (uuid,dataset) ->
                        val result=projectionAuditor.invoke(MeasuredConsumptionStore,reads,dataset,uuid,presentations)
                        require(result is MeasureResult.Success<*>);result.value as AuditedStockProjection
                    }
                    assertEquals(rawProjection,values)
                } finally{reads.finish()}
            }
            components.put("status","COMPLETE")
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
