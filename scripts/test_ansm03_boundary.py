import unittest
from pathlib import Path
import ansm03_report as report


class MeasurementIsolationTest(unittest.TestCase):
    def test_no_production_callers_or_io_dependencies(self):
        self.assertEqual('PASS', report.isolation()['status'])

    def test_core_and_synthetic_protocol_only_in_test_source(self):
        frozen = {'MeasurementCore.kt', 'MeasurementCoreTest.kt', 'LegacyBoundaryTest.kt', 'SyntheticHistoryContractTest.kt'}
        paths = list((report.ROOT / report.MODEL).glob('*.kt'))
        self.assertEqual(frozen | {'ProductionMeasurementTest.kt'}, {p.name for p in paths})
        self.assertEqual(4, len([p for p in paths if p.name in frozen]))
        self.assertTrue(all('src/test/' in str(p) for p in paths))
        production = report.ROOT / report.APPROVED_CORE
        self.assertEqual([production], list(production.parent.glob('*.kt')))
        self.assertIn('ansm03-exact-v2', production.read_text())
        self.assertIn('ansm03-exact-v1', (report.ROOT / report.MODEL / 'MeasurementCore.kt').read_text())
        self.assertIn('kotlin.exclude("net/plainnotes/app/domain/measurement/MeasurementCore.kt")', (report.ROOT / 'core/domain/build.gradle.kts').read_text())

    def test_frozen_contract_and_production_gate_are_separate(self):
        docs = report.ROOT / 'docs/ansm03'
        self.assertIn('ansm03-exact-v1', (docs / 'measurement-contract.md').read_text())
        self.assertIn('NOT_IMPLEMENTED', (docs / 'production-integration-proposal.md').read_text())
        self.assertIn('不接入库存', (docs / 'README.md').read_text())

    def test_unreviewed_callers_and_io_in_reviewed_core_still_fail(self):
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            frozen = root / report.MODEL / 'MeasurementCore.kt'
            frozen.parent.mkdir(parents=True)
            frozen.write_text((report.ROOT / report.MODEL / 'MeasurementCore.kt').read_text())
            core = root / report.APPROVED_CORE
            core.parent.mkdir(parents=True)
            original = (report.ROOT / report.APPROVED_CORE).read_text()
            core.write_text(original)
            with patch.object(report, 'ROOT', root):
                self.assertEqual('PASS', report.isolation()['status'])
                for token in report.FORBIDDEN_CORE_APIS:
                    core.write_text(original + '\n' + token)
                    self.assertEqual('FAIL', report.isolation()['status'], token)
                core.write_text(original)
                rogue = root / 'app/src/main/kotlin/Unreviewed.kt'
                rogue.parent.mkdir(parents=True)
                rogue.write_text('import net.plainnotes.app.domain.measurement.Measurements')
                self.assertEqual('FAIL', report.isolation()['status'])
                rogue.unlink()
                extra = core.parent / 'Shadow.kt'
                extra.write_text('package net.plainnotes.app.domain.measurement')
                self.assertEqual('FAIL', report.isolation()['status'])
                extra.unlink()
                core.unlink()
                self.assertEqual('FAIL', report.isolation()['status'])


if __name__ == '__main__':
    unittest.main()
