"""Independent deterministic numerical checks for ANSM depot research counterexample."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ansm_testosterone_depot as a


class AnsmTDepotTest(unittest.TestCase):
    def test_parent_vs_ester_masses_are_not_interchanged(self):
        self.assertAlmostEqual(a.RCP["ANDROTARDYL_TE_IM"]["parent_mg"], 250 * .72)
        self.assertAlmostEqual(a.RCP["NEBIDO_TU_IM"]["parent_mg"], 1000 * .6315)

    def test_both_first_peaks_can_be_forced_by_construction(self):
        for s in a.RCP.values():
            k = a.fit_zero_baseline_shape(s)
            self.assertAlmostEqual(a.tmax(k.k_abs_h, k.k_release_h), s["first_peak_at_h"], delta=1e-4)
            self.assertAlmostEqual(k.concentration(s["ester_mg"], s["first_peak_at_h"]), s["first_total_peak_ng_ml"], delta=1e-9)

    def test_androtardyl_zero_background_misses_label_tail_bounds(self):
        s = a.RCP["ANDROTARDYL_TE_IM"]
        k = a.fit_zero_baseline_shape(s)
        # Expected negative result: the first-peak fit is not also a tail fit.
        for hour, lower in s["reported_total_lower_bounds"]:
            self.assertLess(k.concentration(s["ester_mg"], hour), lower)

    def test_nebido_first_peak_fit_does_not_predict_reported_steady_peak(self):
        result = a.evaluate()
        s = result["nebido"]
        self.assertFalse(s["model_matches_both_first_and_steady"])
        self.assertGreater(s["model_fifth_peak_ng_ml"], 2 * s["label_steady_total_peak_ng_ml"])
        self.assertGreater(s["model_fifth_trough_ng_ml"], 2 * s["label_steady_total_trough_ng_ml"])

    def test_no_production_claim_or_dose_guidance(self):
        r = a.evaluate()
        self.assertEqual(r["status"], "RESEARCH_ONLY_NOT_VALIDATED_NO_PRODUCTION_PROMOTION")
        self.assertIn("unknown", r["conclusion"].lower())
        self.assertNotIn("recommended", str(r).lower())


if __name__ == "__main__":
    unittest.main()
