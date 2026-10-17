#!/usr/bin/env python3
"""Manual synthetic audit query counts. TEST_ONLY; no user-device or production writes.
Generates one temporary test from the reviewed fixture and removes it in finally.
"""
from pathlib import Path
import subprocess,shutil
root=Path(__file__).resolve().parents[1];source=root/'docs/ansm-integration/p4/performance/LargeInventoryPerformanceProbe.kt';target=root/'core/data/src/test/java/net/plainnotes/app/data/LargeInventoryQueryProbe.kt'
s=source.read_text().replace('class LargeInventoryPerformanceProbe','class LargeInventoryQueryProbe').replace('large-ten-year.json','query-diagnostic.json')
extra='''
private class AuditQueryCounter(private val actual:androidx.sqlite.db.SupportSQLiteDatabase):androidx.sqlite.db.SupportSQLiteDatabase by actual {
    private val counts=linkedMapOf<String,Long>()
    private val bytes=linkedMapOf<String,Long>()
    private fun capture(sql:String,action:()->android.database.Cursor):android.database.Cursor {
        counts[sql]=(counts[sql] ?: 0L)+1
        val cursor=action()
        return object:android.database.CursorWrapper(cursor) {
            override fun getString(index:Int):String? {
                val value=super.getString(index)
                if(value!=null) {val name=getColumnName(index);bytes[name]=(bytes[name] ?: 0L)+value.toByteArray(Charsets.UTF_8).size}
                return value
            }
        }
    }
    override fun query(sql:String)=capture(sql){actual.query(sql)}
    override fun query(sql:String,bindArgs:Array<out Any?>)=capture(sql){actual.query(sql,bindArgs)}
    override fun query(query:androidx.sqlite.db.SupportSQLiteQuery)=capture(query.sql){actual.query(query)}
    override fun query(query:androidx.sqlite.db.SupportSQLiteQuery,cancellationSignal:android.os.CancellationSignal?)=capture(query.sql){actual.query(query,cancellationSignal)}
    fun report()=JSONObject().put("scope","DIAGNOSTIC_COUNTED_SQL_NOT_ACCEPTANCE").put("queries",counts.values.sum())
        .put("top_query_templates",JSONArray(counts.entries.sortedByDescending{it.value}.take(30).map{JSONObject().put("sql",it.key).put("count",it.value)}))
        .put("string_utf8_bytes_by_column",JSONObject(bytes))
}
'''
s+='\n'+extra
needle='            report.put("peak_observed_jvm_heap_bytes",peak.get())'
s=s.replace(needle,'            val counter=AuditQueryCounter(raw)\n            db.withTransaction{assertEquals(count,MeasuredConsumptionStore.auditStagedDataset(counter).size)}\n            report.put("diagnostic_query_stats",counter.report())\n'+needle)
assert not target.exists()
result=root/'core/data/build/reports/ansm-p4-performance/query-diagnostic.json'
result.unlink(missing_ok=True)
assert needle in source.read_text()
try:
 target.write_text(s)
 cmd=[str(root/'gradlew'),'--no-daemon','-Dorg.gradle.jvmargs=-Xmx1536m','-Pkotlin.compiler.execution.strategy=in-process','--max-workers=1','--console=plain',':core:data:testDebugUnitTest','--tests','*LargeInventoryQueryProbe']
 with open('/tmp/ansm-p4-query-probe.log','w') as log:r=subprocess.run(cmd,cwd=root,stdout=log,stderr=subprocess.STDOUT)
 result=root/'core/data/build/reports/ansm-p4-performance/query-diagnostic.json'
 if result.exists():shutil.copyfile(result,'/tmp/ansm-p4-query-diagnostic.json')
 raise SystemExit(r.returncode)
finally:target.unlink(missing_ok=True)
