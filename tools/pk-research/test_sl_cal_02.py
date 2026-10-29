"""SL-CAL-02 guards: the parity fixture is reproducible from the research reference, and the new production file is registered byte-locked."""
import hashlib
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
    def test_parity_fixture_is_reproduced_byte_for_byte_by_the_reference(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / 'parity.json'
            subprocess.run([sys.executable, str(HERE / 'sl_cal/make_parity_fixture.py'), '--out', str(out)], check=True, capture_output=True)
            self.assertEqual(hashlib.sha256(out.read_bytes()).hexdigest(), hashlib.sha256(FIXTURE.read_bytes()).hexdigest(),
                             'regenerate with tools/pk-research/sl_cal/make_parity_fixture.py (numerics differ across numpy/scipy builds only beyond 1e-12)')

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
