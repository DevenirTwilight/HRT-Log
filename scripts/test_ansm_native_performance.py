import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

class NativePerformanceIsolationTest(unittest.TestCase):
    def test_generation_keeps_full_large_contract_and_sqlcipher_only_in_test_source(self):
        prepare = module('prepare_ansm_native_performance')
        text = prepare.native_source(prepare.SOURCE.read_text())
        self.assertIn('src/androidTest/', str(prepare.TARGET))
        self.assertNotIn('robolectric', text.lower())
        self.assertNotIn('Room.inMemoryDatabaseBuilder', text)
        for marker in ('SupportOpenHelperFactory', 'val count=2000', 'val legacy=14600-count',
                       'full_encrypted_restore', 'repo.recordMeasuredUse', '5000.0',
                       '192L*1024*1024', '256L*1024*1024', '80L*1024*1024',
                       'physical_device_acceptance','NOT_RUN'):
            self.assertIn(marker, text)
        self.assertNotIn('setLargeHeap', text)
        self.assertNotIn('fallbackToDestructiveMigration', text)

    def test_template_drift_fails_before_generating_a_misleading_native_test(self):
        prepare = module('prepare_ansm_native_performance')
        with self.assertRaises(ValueError):
            prepare.native_source(prepare.SOURCE.read_text().replace('sampler.join(1000);db.close()', 'other cleanup'))

    def test_collector_accepts_only_the_single_synthetic_report_path(self):
        collect = module('collect_ansm_native_performance')
        valid = '/sdcard/Android/data/net.plainnotes.app.data.test/files/ansm-performance/large-ten-year.json'
        self.assertEqual([valid], collect.report_paths([valid]))
        for invalid in ('/sdcard/health.json', valid+'.backup', valid.replace('data.test','data.attacker'),
                        valid.replace('large-ten-year.json','backup.pnbak'), valid+'/../health.json'):
            self.assertEqual([], collect.report_paths([invalid]))


class NativePerformanceExecutionTest(unittest.TestCase):
    def test_failed_test_still_collects_before_shutdown_without_becoming_success(self):
        from tempfile import TemporaryDirectory
        from types import SimpleNamespace
        runner = module('run_ansm_native_performance')
        with TemporaryDirectory() as directory:
            runner.ROOT = Path(directory)
            for test_status, collection_status in [(1,0),(0,1),(1,1),(0,0)]:
                calls=[]
                def execute(command, **kwargs):
                    calls.append(command)
                    return SimpleNamespace(returncode=test_status if len(calls)==1 else collection_status)
                self.assertEqual(test_status or collection_status,runner.run(execute))
                self.assertEqual(2,len(calls))
                self.assertIn('connectedDebugAndroidTest',calls[0][1])
                self.assertTrue(calls[1][1].endswith('collect_ansm_native_performance.py'))


class NativeAvdConfigurationTest(unittest.TestCase):
    def test_correct_vm_heap_property_is_unique_and_other_avd_fields_are_preserved(self):
        configure=module('configure_ansm_low_memory_avd').configure
        source='hw.ramSize=1536\nvm.heapSize=384\nhw.cpu.ncore=2\navd.ini.displayname=SYNTHETIC\nvm.heapSize=256\n'
        result=configure(source)
        self.assertEqual(1,result.count('vm.heapSize='))
        self.assertIn('vm.heapSize=192\n',result)
        self.assertIn('hw.ramSize=512\n',result)
        self.assertIn('hw.cpu.ncore=2\n',result)
        self.assertIn('avd.ini.displayname=SYNTHETIC\n',result)
        self.assertEqual(result,configure(result))
