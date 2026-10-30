"""Research-only guardrail tests for the ANSM RCP phase-1 record (stdlib)."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).parent))
from fr_rcp_phase1 import run, kernel, metrics, day_curve, self_test

ROOT = Path(__file__).resolve().parents[2]


class FranceRcpPhase1Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = run(ROOT / "pk-engine/src/main/resources/pk-params.json",
                         ROOT / "docs/pk-research/fr-rcp-phase1/anchors.json")

    def test_math_self_check(self):
        self_test()

    def test_existing_oral_e2_mean(self):
        p = self.report["comparisons"]["oral_E2"]
        self.assertAlmostEqual(p["ANSM_OROMONE_1"]["predicted_increment"]["cavg_24h"], 31.9374, delta=.002)
        self.assertAlmostEqual(p["ANSM_OROMONE_2"]["predicted_increment"]["cavg_24h"], 63.8748, delta=.002)

    def test_ev_unit_converted_from_pmol(self):
        p = self.report["comparisons"]["oral_EV"]
        self.assertAlmostEqual(p["ANSM_DUOVA_1"]["observed_total_pg_ml"]["cavg"], 123 / 3.671, delta=.00002)
        self.assertAlmostEqual(p["ANSM_DUOVA_2"]["predicted_increment_pg_ml"]["cavg_24h"], 47.99895, delta=.005)

    def test_estreva_provisional_comparison(self):
        p = self.report["comparisons"]["Estreva"]["models"]["E2_GEL_OTHER"]
        self.assertAlmostEqual(p["single_increment"]["cmax"], 39.01376, delta=.005)
        self.assertAlmostEqual(p["repeat_day22_increment"]["cmax"], 69.10787, delta=.005)

    def test_p4_negative_result_not_validated(self):
        p = self.report["comparisons"]["progesterone"]
        self.assertGreater(p["existing_sse"], 130)
        self.assertLess(p["exploratory_two_term_in_sample"]["sse"], 25)
        self.assertAlmostEqual(p["exploratory_two_term_in_sample"]["slow_A_per_mg"], 0., delta=1e-9)
        self.assertIn("TRAINING-ONLY", p["status"])

    def test_t_diagnostics_not_modeled_curve(self):
        p = self.report["comparisons"]["testosterone"]
        self.assertAlmostEqual(p["TE"]["simple_first_order_depot_with_iv_t_half_1h_tmax_h"], 6.81802, delta=.0001)
        self.assertLess(p["TU"]["pure_90day_exponential_max_peak_trough_ratio_q70days"], 2.2)
        self.assertIn("NO testosterone concentration models fitted", p["status"])

    def test_output_discloses_no_clinical_validation(self):
        self.assertIn("NOT_PRODUCTION", self.report["research_state"])
        self.assertTrue(any("no locked holdout" in x.lower() or "holdout" in x.lower()
                            for x in self.report["limitations"] + [
                                self.report["comparisons"]["progesterone"]["status"]]))


if __name__ == "__main__":
    unittest.main()
