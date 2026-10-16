import unittest
from pathlib import Path
import ansm03_report as report


class MeasurementIsolationTest(unittest.TestCase):
    def test_no_production_callers_or_io_dependencies(self):
        self.assertEqual('PASS', report.isolation()['status'])

    def test_core_and_synthetic_protocol_only_in_test_source(self):
        paths = list((report.ROOT / report.MODEL).glob('*.kt'))
        self.assertEqual(4, len(paths))
        self.assertTrue(all('src/test/' in str(p) for p in paths))
        self.assertFalse((report.ROOT / 'core/domain/src/main/kotlin/net/plainnotes/app/domain/measurement').exists())

    def test_frozen_contract_and_production_gate_are_separate(self):
        docs = report.ROOT / 'docs/ansm03'
        self.assertIn('ansm03-exact-v1', (docs / 'measurement-contract.md').read_text())
        self.assertIn('NOT_IMPLEMENTED', (docs / 'production-integration-proposal.md').read_text())
        self.assertIn('不接入库存', (docs / 'README.md').read_text())


if __name__ == '__main__':
    unittest.main()
