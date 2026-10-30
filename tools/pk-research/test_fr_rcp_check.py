"""Tests for ANSM label cross-product comparison, without treating it as clinical validation."""
import importlib.util
import json
import math
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("fr_rcp_check.py")
spec = importlib.util.spec_from_file_location("fr_rcp_check", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FrenchRcpComparisonTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.params = json.loads(module.MODELS.read_text(encoding="utf-8"))["models"]
        cls.evidence = json.loads(module.EVIDENCE.read_text(encoding="utf-8"))
        cls.rows = module.evaluate(cls.params, cls.evidence)

    def test_evidence_is_official_and_route_matched(self):
        self.assertGreaterEqual(len(self.evidence["entries"]), 5)
        for entry in self.evidence["entries"]:
            self.assertTrue(entry["source"].startswith("https://base-donnees-publique.medicaments.gouv.fr/"))
            self.assertIn(entry["model"], self.params)
            self.assertIn(entry["regimen"], ("once_daily_steady", "single"))

    def test_reproducible_predictions_finite_and_nonnegative(self):
        for row in self.rows:
            self.assertTrue(math.isfinite(row["predicted"]))
            self.assertGreaterEqual(row["predicted"], 0)
            self.assertTrue(math.isfinite(row["delta"]))
            self.assertGreater(row["reference_for_comparison"], 0)

    def test_oromone_steady_two_mg_inside_published_variation(self):
        r = {row["statistic"]: row for row in self.rows if row["id"] == "OROMONE_E2_2MG"}
        self.assertTrue(r["cmax"]["within_reported_one_sd"])
        self.assertTrue(r["cmin"]["within_reported_one_sd"])
        self.assertTrue(r["auc24"]["within_reported_one_sd"])

    def test_utrogestan_discrepancy_is_visible_not_silently_marked_validated(self):
        r = {row["hour"]: row for row in self.rows if row["id"] == "UTROGESTAN_P4_200MG"}
        self.assertGreater(r[6]["ratio"], 3.0)
        self.assertGreater(r[8]["ratio"], 3.0)
        self.assertLess(abs(r[2]["ratio"] - 1), 0.1)
        for row in r.values():
            self.assertEqual(row["role"], "external_cross_product_conflict")
            self.assertIsNone(row["within_reported_one_sd"])

    def test_valerate_must_convert_pmol_and_not_relabel_estrone(self):
        r = {row["statistic"]: row for row in self.rows if row["id"] == "DUOVA_EV_2MG"}
        self.assertEqual(r["cmax"]["unit"], "pmol/L")
        self.assertLess(r["cmax"]["predicted"], r["cmax"]["reference_for_comparison"])
        self.assertLess(r["cmin"]["predicted"], r["cmin"]["reference_for_comparison"])


if __name__ == "__main__":
    unittest.main()
