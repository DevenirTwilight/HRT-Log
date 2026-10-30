"""Deterministic research-only verification of French ANSM RCP phase-2 bounds.

Run: python3 -m unittest tools/pk-research/test_fr_rcp_phase2.py
Tests verify only numeric arithmetic and evidence guardrails, not clinical accuracy.
"""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fr_rcp_phase2 import (
    bateman, bateman_tpeak, find_fast_for_tpeak,
    min_constant_baseline_to_reach_thresholds, run, self_test,
)

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs/pk-research/fr-rcp-phase1/phase2-ledger.json"


class AnsmPhase2Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.results = run(json.loads(SOURCE.read_text(encoding="utf8")))

    def test_math_invariants(self):
        self_test()
        self.assertAlmostEqual(bateman(24, .06, .006) / bateman(24, .06, .006), 1.)
        with self.assertRaises(ValueError):
            bateman(12, .003, .006)

    def test_te_peak_interval_candidates(self):
        te = self.results["TE"]["effective_rise_grid"]
        self.assertGreater(len(te), 5)
        inside = [r for r in te if 1.5 <= r["tmax_days"] <= 3.]
        self.assertGreaterEqual(len(inside), 4)
        for r in inside:
            self.assertGreaterEqual(r["min_nonnegative_baseline_ng_ml"], 0.)
            self.assertLess(r["min_nonnegative_baseline_ng_ml"], 20.)

    def test_te_no_claim_without_baseline(self):
        te = self.results["TE"]["effective_rise_grid"]
        r = next(x for x in te if x["fast_h_inv"] == .05)
        self.assertAlmostEqual(r["tmax_days"], 1.963, delta=.002)
        self.assertAlmostEqual(r["min_nonnegative_baseline_ng_ml"], .618, delta=.005)

    def test_tu_first_peak_is_invariant(self):
        for t in self.results["TU"]["q70d_first_peak7d_bateman_candidates"]:
            self.assertAlmostEqual(t["first_peak_h"], 168., delta=1e-6)

    def test_tu_long_half_life_increases_accumulation(self):
        ts = self.results["TU"]["q70d_first_peak7d_bateman_candidates"]
        peaks = [x["fifth_peak_total_B0"] for x in ts]
        self.assertTrue(all(a < b for a, b in zip(peaks, peaks[1:])))
        self.assertAlmostEqual(ts[3]["fifth_peak_total_B0"], 25.17, delta=.1)
        self.assertGreater(ts[3]["fifth_trough_total_B0"], 15.)

    def test_tu_steady_ratio_sensitive(self):
        ts = self.results["TU"]["q70d_first_peak7d_bateman_candidates"]
        ratios = [x["no_baseline_steady_peak_trough_ratio"] for x in ts]
        self.assertTrue(all(a > b for a, b in zip(ratios, ratios[1:])))
        self.assertGreater(ratios[0], 2.2)
        self.assertLess(ratios[3], 2.2)

    def test_identical_progesterone_sources_deduplicated(self):
        p = self.results["P4"]
        self.assertEqual(p["unique_underlying_rcp_cohorts"], 1)
        self.assertGreaterEqual(len(p["reused_brand_labels"]), 3)
        for s in p["baseline_scenarios_not_ci"]:
            self.assertLess(s["half_life_4to6h"], 1.2)
            self.assertGreater(s["half_life_6to8h"], 5.)

    def test_titrated_testavan_groups_not_dose_response(self):
        g = self.results["T_gels"]
        self.assertTrue(g["cannot_fit_monotonic_individual_dose_response"])
        self.assertLess(g["between_titrated_groups_cavg_69_over_46"], 1.)
        self.assertGreater(g["arm_shoulder_vs_abdomen_cmax_ratio"], 2.)

    def test_missing_clinical_validation_labeled(self):
        self.assertIn("RESEARCH_ONLY", self.results["state"])
        self.assertIn("not sufficient", self.results["admission"])


if __name__ == "__main__":
    unittest.main()
