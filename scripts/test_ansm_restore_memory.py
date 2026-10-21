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
    def test_contract_only_mode_never_runs_or_claims_the_large_workload(self):
        with tempfile.TemporaryDirectory() as directory,patch.object(mod,'ROOT',Path(directory)),patch.object(mod,'verify_emulator'),patch.object(mod.subprocess,'run',return_value=SimpleNamespace(returncode=0)) as run,patch.object(mod,'death_case',return_value={'status':'PASS'}):
            apk=Path(directory)/'core/data/build/outputs/apk/androidTest/debug/synthetic-androidTest.apk'
            apk.parent.mkdir(parents=True);apk.touch()
            self.assertEqual(0,mod.main('restore-process-death-only'))
            self.assertFalse(any('run_ansm_native_performance.py' in str(c.args) for c in run.call_args_list))
            report=json.loads((Path(directory)/'core/data/build/reports/ansm-native-performance/restore-process-death.json').read_text())
            self.assertEqual('NOT_RUN_CONTRACT_ONLY',report['large_performance'])
        workflow=(ROOT/'.github/workflows/ansm-inventory-performance.yml').read_text()
        self.assertIn('restore-process-death-api26-only',workflow)
        self.assertIn('ansm-api26-restore-process-death-only',workflow)

    def test_write_lifecycle_mode_never_runs_large_or_repeats_restore_and_preserves_failures(self):
        for transcript,killed_exit,expected_exit in [('OK (17 tests)',0,0),('FAILURES!!!',0,1),('OK (16 tests)',0,1),('OK (17 tests)',1,1)]:
            with self.subTest(transcript=transcript,killed_exit=killed_exit),tempfile.TemporaryDirectory() as directory:
                apk=Path(directory)/'core/data/build/outputs/apk/androidTest/debug/synthetic-androidTest.apk'
                apk.parent.mkdir(parents=True);apk.touch()
                results=[SimpleNamespace(returncode=0),SimpleNamespace(returncode=0),
                         SimpleNamespace(returncode=0,stdout=transcript,stderr=''),SimpleNamespace(returncode=killed_exit)]
                with patch.object(mod,'ROOT',Path(directory)),patch.object(mod,'verify_emulator'),patch.object(mod.subprocess,'run',side_effect=results) as run,patch.object(mod,'death_case') as restore:
                    self.assertEqual(expected_exit,mod.main('p4-write-lifecycle-only'))
                    restore.assert_not_called()
                    self.assertFalse(any('run_ansm_native_performance.py' in str(c.args) for c in run.call_args_list))
                    self.assertEqual(4,run.call_count) # compile/install/selected contracts/write death; no retries
                    report=json.loads((Path(directory)/'core/data/build/reports/ansm-native-performance/write-lifecycle-only.json').read_text())
                    self.assertEqual('NOT_RUN_CONTRACT_ONLY_LEGACY_RESULT_UNCHANGED',report['large_performance'])
                    self.assertEqual('NOT_RERUN_ALREADY_VERIFIED',report['restore_process_death'])

    def test_memory_probe_and_restore_hooks_are_only_synthetic_and_keep_the_frozen_workload(self):
        from prepare_ansm_native_performance import native_source,SOURCE
        text=native_source(SOURCE.read_text())
        self.assertIn('RestoreMemoryProbe',text)
        self.assertIn('SyntheticRestoreDigest.read(raw)',text)
        self.assertIn('restore_business_equality',text)
        self.assertIn('repo.restoreMeasuredBackup(archive,password,repo.referencePreviewEpoch(),checkpoint=',text)
        self.assertIn('assertEquals("Frozen full fixture byte size",19660924,bytes)',text)
        for name in ('RestoreMemoryProbe','MeasuredRestoreDeathProbe','SyntheticRestoreDigest'):
            self.assertFalse((ROOT/f'core/data/src/main/java/net/plainnotes/app/data/{name}.kt').exists())
        self.assertNotIn('System.gc()',text)
        workflow=(ROOT/'.github/workflows/ansm-inventory-performance.yml').read_text()
        for value in ('api-level: 26','ram-size: 512M','heap-size: 192M','-memory 512'):
            self.assertIn(value,workflow)

if __name__=='__main__':unittest.main()
