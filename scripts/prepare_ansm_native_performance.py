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
