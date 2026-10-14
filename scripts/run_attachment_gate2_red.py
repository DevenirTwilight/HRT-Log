#!/usr/bin/env python3
"""Deterministic negative coordinator control; NOT a production-defect claim. Then verifies fixed control."""
import json
import subprocess
from pathlib import Path
root=Path(__file__).resolve().parent.parent
package='net.plainnotes.app.data.test'
assert subprocess.check_output(['adb','shell','getprop','ro.kernel.qemu'],text=True).strip()=='1'
subprocess.check_call(['adb','install','-r','-t',str(root/'core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk')])
base=['adb','shell','am','instrument','-w','-e','class','net.plainnotes.app.data.Gate2StoreAndroidTest#postCommitAckLossRetainsDurableActiveAndDoesNotRepeatCas']
runner=package+'/androidx.test.runner.AndroidJUnitRunner'
negative=subprocess.run(base+['-e','gate2_red','true',runner],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=90)
assert 'Committed active must survive lost acknowledgement' in negative.stdout and 'Failures: 1' in negative.stdout,negative.stdout
positive=subprocess.run(base+[runner],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=90)
assert positive.returncode==0 and 'OK (1 test)' in positive.stdout,positive.stdout
out=root/'core/data/build/reports/gate2';out.mkdir(parents=True,exist_ok=True)
(out/'red.txt').write_text(negative.stdout);(out/'green.txt').write_text(positive.stdout)
(out/'red-green.json').write_text(json.dumps({'scope':'test-only deliberate unsafe cleanup control; no production defect claimed','red':'expected one failure','green':'OK (1 test)'},indent=2))
metrics=subprocess.check_output(['adb','logcat','-d','-s','GATE2_METRIC:I'],text=True)
(out/'final-process-memory-samples.txt').write_text(metrics)
print('PASS: deterministic unsafe control red=1 failure; fixed coordinator green=1 pass')
