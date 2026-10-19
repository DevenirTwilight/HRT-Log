#!/usr/bin/env python3
"""Reproduce the synthetic post-budget ledger query diagnostic; never production data."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

root=Path(__file__).resolve().parents[4]
base=root/'docs/ansm-integration/p4/performance'
target=root/'core/data/src/test/java/net/plainnotes/app/data/LargeInventoryPerformanceProbe.kt'
if target.exists():
    raise SystemExit('Refusing to overwrite an existing test source')
source=(base/'LargeInventoryPerformanceProbe.kt').read_text()
patch=(base/'post-budget-query-diagnostic.patch').read_text()
added='\n'.join(line[1:] for line in patch.splitlines() if line.startswith('+') and not line.startswith('+++'))+'\n'
added+=(base/'ledger-stream-query-plan.fragment.kt.txt').read_text()
needle='            report.put("budget_exceeded",JSONArray(exceeded))'
if source.count(needle)!=1:
    raise SystemExit('Reviewed fixture insertion point changed')
probe=source.replace(needle,added+needle)
parser=argparse.ArgumentParser()
parser.add_argument('--emit',type=Path,help='Only emit the generated diagnostic test outside the build')
args=parser.parse_args()
if args.emit:
    if args.emit.exists():
        raise SystemExit('Refusing to overwrite an existing emitted source')
    args.emit.write_text(probe)
    print(hashlib.sha256(probe.encode()).hexdigest())
    raise SystemExit(0)
report=root/'core/data/build/reports/ansm-p4-performance/large-ten-year.json'
try:
    target.write_text(probe)
    report.unlink(missing_ok=True)
    command=[str(root/'gradlew'),'--no-daemon','-Dorg.gradle.jvmargs=-Xmx1536m',
        '-Pkotlin.compiler.execution.strategy=in-process','-ProbolectricDir='+str(root/'build/robolectric-jars'),
        '--max-workers=1','--console=plain',':core:data:testDebugUnitTest','--tests','*LargeInventoryPerformanceProbe']
    result=subprocess.run(command,cwd=root)
    if result.returncode:
        raise SystemExit(result.returncode)
    data=json.loads(report.read_text())
    assert data['diagnostic_query_stats']['full_projection_equality'] is True
    assert data['diagnostic_stream_query_plan']['scope']=='TEST_ONLY_POST_BUDGET_READ_ONLY'
    print(json.dumps({'scope':'JVM_DIAGNOSTIC_NOT_NATIVE_ACCEPTANCE','report':str(report),
        'production_sha256':hashlib.sha256((root/'core/data/src/main/java/net/plainnotes/app/data/MeasuredConsumptionStore.kt').read_bytes()).hexdigest(),
        'probe_sha256':hashlib.sha256(probe.encode()).hexdigest(),
        'queries':data['diagnostic_query_stats']['queries']}))
finally:
    target.unlink(missing_ok=True)
