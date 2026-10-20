import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
spec=importlib.util.spec_from_file_location('restore_memory_suite',ROOT/'scripts/run_ansm_restore_memory_suite.py')
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)

class RestoreMemorySuiteTest(unittest.TestCase):
    def test_original_native_failure_is_never_hidden_by_process_death_success(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(mod,'ROOT',Path(directory)),patch.object(mod,'verify_emulator') as verify,patch.object(mod.subprocess,'run',return_value=SimpleNamespace(returncode=1)) as run,patch.object(mod,'death_case',side_effect=[{'status':'PASS'},{'status':'PASS'}]) as death:
            self.assertEqual(1,mod.main());verify.assert_called_once();self.assertEqual(1,run.call_count)
            self.assertEqual([('ROWS_DELETED',),('ROWS_INSERTED',)],[c.args for c in death.call_args_list])
    def test_failure_in_one_death_case_does_not_skip_the_other_or_create_retries(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(mod,'ROOT',Path(directory)),patch.object(mod,'verify_emulator'),patch.object(mod.subprocess,'run',return_value=SimpleNamespace(returncode=0)),patch.object(mod,'death_case',side_effect=[AssertionError('synthetic'),{'status':'PASS'}]) as death:
            self.assertEqual(1,mod.main());self.assertEqual(2,death.call_count)
            result=json.loads((Path(directory)/'core/data/build/reports/ansm-native-performance/restore-process-death.json').read_text())
            self.assertEqual(['FAIL','PASS'],[c['status'] for c in result['cases']])
    def test_memory_probe_and_restore_hooks_are_only_synthetic_and_keep_the_frozen_workload(self):
        from prepare_ansm_native_performance import native_source,SOURCE
        text=native_source(SOURCE.read_text())
        self.assertIn('RestoreMemoryProbe',text)
        self.assertIn('repo.restoreMeasuredBackup(archive,password,repo.referencePreviewEpoch(),checkpoint=',text)
        self.assertIn('assertEquals("Frozen full fixture byte size",19660924,bytes)',text)
        for name in ('RestoreMemoryProbe','MeasuredRestoreDeathProbe'):
            self.assertFalse((ROOT/f'core/data/src/main/java/net/plainnotes/app/data/{name}.kt').exists())
        self.assertNotIn('System.gc()',text)
        workflow=(ROOT/'.github/workflows/ansm-inventory-performance.yml').read_text()
        for value in ('api-level: 26','ram-size: 512M','heap-size: 192M','-memory 512'):
            self.assertIn(value,workflow)

if __name__=='__main__':unittest.main()
