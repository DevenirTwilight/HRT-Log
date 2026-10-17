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
