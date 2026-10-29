"""SL-CAL-02 guards: the parity fixture is reproducible from the research reference, and the new production file is registered byte-locked."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import check_protocol as guard  # noqa: E402

FIXTURE = ROOT / 'app/src/test/resources/sl-cal-02/parity.json'
ENGINE = 'app/src/main/java/net/plainnotes/app/conc/TotalE2Calibration.kt'


class SlCal02Tests(unittest.TestCase):
    def test_parity_fixture_is_reproduced_by_the_reference_to_numerical_tolerance(self):
        # Byte equality is not stable across numpy/scipy builds (CI differs from a laptop in the last bits), so structure must match exactly
        # and every number to 1e-7 relative (the fit stops at a step below 1e-8, so optimizer-converged values can differ in the 9th digit between builds); the Kotlin engine is compared with the committed fixture separately (TotalE2CalibrationParityTest).
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / 'parity.json'
            subprocess.run([sys.executable, str(HERE / 'sl_cal/make_parity_fixture.py'), '--out', str(out)], check=True, capture_output=True)
            self.assertSameShape(json.loads(out.read_text()), json.loads(FIXTURE.read_text()), '$')

    def assertSameShape(self, got, want, path):
        if isinstance(want, dict):
            self.assertIsInstance(got, dict, path); self.assertEqual(sorted(got), sorted(want), path)
            for key in want:
                self.assertSameShape(got[key], want[key], f'{path}.{key}')
        elif isinstance(want, list):
            self.assertIsInstance(got, list, path); self.assertEqual(len(got), len(want), path)
            for i, (g, w) in enumerate(zip(got, want)):
                self.assertSameShape(g, w, f'{path}[{i}]')
        elif isinstance(want, float) and not isinstance(want, bool):
            self.assertAlmostEqual(got, want, delta=1e-7 * max(1.0, abs(want)), msg=path)
        else:
            self.assertEqual(got, want, path)

    def test_the_engine_is_the_only_file_outside_the_archived_classification_and_the_archive_stays_untouched(self):
        # The archived guard classification is immutable (research_archive.verify_records), so the engine is NOT registered there;
        # it is an ordinary product file protected by the parity and rule tests. Nothing else may appear unclassified.
        cls = guard.classification()
        baseline = json.loads((ROOT / 'docs/pk-research/p2/production-baseline.json').read_text())
        self.assertEqual(guard.unclassified(cls, baseline), [ENGINE])
        self.assertNotIn(ENGINE, cls['live_lock_additional'])
        import research_archive
        self.assertGreater(research_archive.verify_records(), 0)

    def test_no_protected_production_file_is_among_the_files_this_phase_added(self):
        # The engine reads production constants only through tests; it imports nothing from LIVE_LOCK files.
        text = (ROOT / ENGINE).read_text()
        for banned in ('import net.plainnotes.app.pk', 'LabFit', 'ConcentrationCalculator', 'CalibrationEligibility'):
            self.assertNotIn(banned, text.replace('Production `LabFit.SIGMA_LAB`', '').replace('`LabFit.SIGMA_LAB`', '').replace('P1-B `CalibrationEligibility`', ''))


if __name__ == '__main__':
    unittest.main()
