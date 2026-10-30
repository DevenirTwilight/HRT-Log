"""Guardrails for purely exploratory NEBIDO two-timescale scenario scan."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from fr_rcp_phase2_mixture import normalized_depot, trial, run


class TuMixtureResearchTest(unittest.TestCase):
    def test_normalized_seven_day_peak(self):
        for half in (5, 25, 90):
            self.assertAlmostEqual(normalized_depot(half, 7), 1., delta=1e-10)
            self.assertAlmostEqual(normalized_depot(half, 0), 0., delta=1e-10)

    def test_math_domain(self):
        with self.assertRaises(ValueError):
            trial(25, .51, 3)
        with self.assertRaises(ValueError):
            trial(25, .025, 6)

    def test_fifth_peak_exceeds_first_when_residual(self):
        r = trial(25, 0, 3)
        self.assertGreater(r["pred_fifth_peak_ng_ml"], 11)
        self.assertAlmostEqual(r["pred_second_peak_ng_ml"], 13.7009, delta=.005)

    def test_pure_90d_significantly_accumulates(self):
        r = trial(90, 1e-6, 0)
        self.assertGreater(r["pred_fifth_peak_ng_ml"], 24)
        self.assertGreater(r["pred_fifth_trough_ng_ml"], 15)

    def test_no_locked_holdout_or_clinical_claim(self):
        scan = run()
        self.assertEqual(scan["n_scenarios"], 1260)
        self.assertTrue(scan["no_independent_holdout"])
        self.assertEqual(scan["best_minimum_sse"]["slow_90d_peak_weight"], 0.)
        self.assertAlmostEqual(scan["best_minimum_sse"]["sse_approx_group_anchors"], 3.8517, delta=.01)
        self.assertGreater(scan["best_with_positive_90day_component"]["slow_90d_peak_weight"], 0.)


if __name__ == "__main__":
    unittest.main()
