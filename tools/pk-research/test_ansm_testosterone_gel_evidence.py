"""ANSM testosterone gel evidence integrity and no-false-dose-response gates."""
import json
from pathlib import Path
import unittest

REGISTRY = Path(__file__).resolve().parents[2] / "docs/pk-research/france-ansm-testosterone-gels-2026-10-30.json"
DATA = json.loads(REGISTRY.read_text(encoding="utf-8"))
PRODUCTS = {x["id"]: x for x in DATA["products"]}


class TestosteroneGelRcpEvidenceTest(unittest.TestCase):
    def test_registry_is_not_mislabeled_as_clinically_validated_pk(self):
        self.assertEqual(DATA["status"], "RESEARCH_ONLY_NOT_A_POPULATION_PK_MODEL")
        self.assertEqual(DATA["retrieved_on"], "2026-10-30")

    def test_pump_quantity_and_mass_use_formulation_not_shared_defaults(self):
        testavan = PRODUCTS["TESTAVAN_20MG_G"]
        androgel = PRODUCTS["ANDROGEL_16_2MG_G"]
        self.assertAlmostEqual(testavan["gel_strength_mg_per_g"] * testavan["pump_gel_g"], testavan["pump_t_mg"])
        self.assertAlmostEqual(androgel["gel_strength_mg_per_g"] * androgel["pump_gel_g"], androgel["pump_t_mg"])
        self.assertNotEqual(testavan["pump_t_mg"], androgel["pump_t_mg"])

    def test_testavan_titrated_cross_section_not_monotonic_dose_response(self):
        trial = PRODUCTS["TESTAVAN_20MG_G"]["trial"]
        groups = trial["by_day90_dose"]
        self.assertTrue(trial["postbaseline_titration"])
        self.assertEqual([g["n"] for g in groups], [5, 45, 89])
        self.assertLess(groups[2]["cavg_mean"], groups[1]["cavg_mean"])
        self.assertLess(groups[2]["cmax_mean"], groups[1]["cmax_mean"])
        self.assertEqual(trial["analyte"], "serum total testosterone")

    def test_site_difference_is_product_specific_with_no_user_multiplier(self):
        testavan = PRODUCTS["TESTAVAN_20MG_G"]
        sites = testavan["site_comparison"]["sites"]
        self.assertGreater(sites[0]["cmax_mean_ng_dl"], sites[1]["cmax_mean_ng_dl"])
        self.assertGreater(sites[0]["cmax_mean_ng_dl"], sites[2]["cmax_mean_ng_dl"])
        self.assertIn("before quantitative individual site multipliers", testavan["site_comparison"]["note"])

    def test_androgel_is_increment_without_absolute_baseline(self):
        androgel = PRODUCTS["ANDROGEL_16_2MG_G"]
        e = androgel["example"]
        self.assertAlmostEqual(e["applied_gel_g"] * androgel["gel_strength_mg_per_g"], e["nominal_testosterone_mg"])
        self.assertEqual(e["mean_total_testosterone_increase_ng_ml"], 2.2)
        self.assertEqual(androgel["return_to_baseline_h_range"], [72, 96])
        self.assertIn("titrated", androgel["note"])


if __name__ == "__main__":
    unittest.main()
