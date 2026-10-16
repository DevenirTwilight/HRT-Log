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

    def test_quantum_adapter_has_a_narrow_pure_boundary(self):
        import tempfile
        from unittest.mock import patch
        original_root = report.ROOT
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in (report.MODEL / 'MeasurementCore.kt', report.APPROVED_CORE, report.APPROVED_STOCK_QUANTUM, report.APPROVED_PACKAGE_CODEC, report.APPROVED_CONTAINER_STORE, report.APPROVED_USE_PAYLOAD):
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text((original_root / relative).read_text())
            stock = root / report.APPROVED_STOCK_QUANTUM
            original = stock.read_text()
            with patch.object(report, 'ROOT', root):
                self.assertEqual('PASS', report.isolation()['status'])
                for token in report.FORBIDDEN_CORE_APIS:
                    stock.write_text(original + '\n' + token)
                    self.assertEqual('FAIL', report.isolation()['status'], token)
                stock.write_text(original)
                rogue = stock.parent / 'Unreviewed.kt'
                rogue.write_text('package net.plainnotes.app.domain.stock')
                self.assertEqual('FAIL', report.isolation()['status'])
                rogue.unlink()
                stock.write_text(original + '\nimport java.math.RoundingMode')
                self.assertEqual('FAIL', report.isolation()['status'])
                stock.write_text(original)
                codec = root / report.APPROVED_PACKAGE_CODEC
                content = codec.read_text()
                for token in report.FORBIDDEN_CORE_APIS:
                    codec.write_text(content + '\n' + token)
                    self.assertEqual('FAIL', report.isolation()['status'], token)
                codec.write_text(content)
                self.assertEqual('PASS', report.isolation()['status'])
                store = root / report.APPROVED_CONTAINER_STORE
                original_store = store.read_text()
                for token in report.STORE_FORBIDDEN_APIS:
                    store.write_text(original_store + '\n' + token)
                    self.assertEqual('FAIL', report.isolation()['status'], token)
                store.write_text(original_store)
                self.assertEqual('PASS', report.isolation()['status'])
                use_payload = root / report.APPROVED_USE_PAYLOAD
                original_use = use_payload.read_text()
                for token in report.FORBIDDEN_CORE_APIS:
                    use_payload.write_text(original_use + '\n' + token)
                    self.assertEqual('FAIL', report.isolation()['status'], token)
                use_payload.write_text(original_use)
                self.assertEqual('PASS', report.isolation()['status'])

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
