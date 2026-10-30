"""French ANSM official RCP characterization tests for existing and new HRT Log PK kernels.

These label-only comparisons do not establish clinical accuracy. Many cohorts
measure *total* hormone with unknown baseline, and formulations / assays differ.
Tests explicitly characterize known discrepancies; an improved future model
must update this evidence audit with the change, rather than silently replacing
the old dataset. Pure stdlib: run with unittest discover.
"""
import json
import math
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PARAMS_PATH = ROOT / "pk-engine/src/main/resources/pk-params.json"
PARAMS = json.loads(PARAMS_PATH.read_text(encoding="utf-8"))


def response(key, dose_mg, time_h):
    """Current FittedModel.response formula, with all calibration scales 1."""
    m = PARAMS["models"][key]
    ka = m["ka_per_h"]
    return dose_mg * sum(
        term["A_per_mg"] * (math.exp(-term["lambda_per_h"] * time_h) - math.exp(-ka * time_h))
        for term in m["terms"]
    )


def daily_steady(key, dose_mg, hour_since_last, doses=21):
    return sum(response(key, dose_mg, hour_since_last + 24 * day) for day in range(doses))


def daily_ss_profile(key, dose_mg):
    values = [daily_steady(key, dose_mg, i / 10) for i in range(241)]
    return {
        "max": max(values),
        "min": values[-1],
        # Trapezoidal average over the complete 24h steady-state interval
        "mean": (sum(values[1:-1]) + (values[0] + values[-1]) / 2) / 240,
    }


class FranceRcpExistingModelCharacterization(unittest.TestCase):
    """Clinical interpretation requires human review, not just test success."""

    def test_estreva_is_independent_product_specific_illustration(self):
        m = PARAMS["models"]["E2_GEL_ESTREVA"]
        self.assertTrue(m.get("illustrative"))
        self.assertFalse(m.get("no_product_data", False))
        self.assertIn("france_bdpm:ANSM_ESTREVA2026", m["basis"][0])
        self.assertIn("estreva", m["products"])
        self.assertNotIn("estreva", PARAMS["models"]["E2_GEL_OTHER"]["products"])
        self.assertTrue(PARAMS["models"]["E2_GEL_OTHER"]["no_product_data"])

    def test_estreva_three_anchors_are_a_fit_not_independent_validation(self):
        # ANSM CIS63216075 RCP 2026-07-06, baseline/covariates not resolved.
        peak = max(response("E2_GEL_ESTREVA", 1.5, i / 10) for i in range(241))
        steady = daily_ss_profile("E2_GEL_ESTREVA", 1.5)
        self.assertAlmostEqual(peak, 40.0, delta=2.0)
        self.assertAlmostEqual(steady["max"], 70.0, delta=2.0)
        self.assertAlmostEqual(steady["min"], 40.0, delta=2.0)

    def test_existing_oromone_oral_e2_matches_some_label_anchors_not_validation(self):
        # ANSM CIS60071379 RCP 2025-07-18, total E2 at steady state on 2mg.
        r = daily_ss_profile("E2_ORAL", 2)
        self.assertAlmostEqual(r["max"], 89.0, delta=16.0)
        self.assertAlmostEqual(r["min"], 35.0, delta=13.4)
        self.assertAlmostEqual(r["mean"] * 24, 1486.0, delta=374.0)

    def test_duova_repeated_ev_differs_from_total_serum_e2_anchors(self):
        # ANSM CIS62617300 / 66620660, estradiol valerate + MPA; pg/mL
        # -> pmol/L uses E2 factor 3.671, never mixes E1 or E1S into E2.
        r1 = daily_ss_profile("EV_ORAL", 1)
        r2 = daily_ss_profile("EV_ORAL", 2)
        self.assertLess(r1["mean"] * 3.671, 123.0)
        self.assertLess(r2["mean"] * 3.671, 228.0)
        # Dose-linear assumption in the existing kernel, not a clinical law.
        self.assertAlmostEqual(r2["mean"], 2 * r1["mean"], delta=1e-8)

    def test_utrogestan_oral_p4_tail_mismatch_is_not_ignored(self):
        # ANSM CIS61673424, 200mg oral progesterone; 0.13ng/mL baseline.
        # FDA Prometrium-derived model is illustrative; assay differences matter.
        r2 = response("P4_ORAL", 200, 2)
        r6 = response("P4_ORAL", 200, 6)
        r8 = response("P4_ORAL", 200, 8)
        self.assertAlmostEqual(r2, 11.75 - 0.13, delta=1.0)
        self.assertGreater(r6, 3 * (2.00 - 0.13))
        self.assertGreater(r8, 3 * (1.64 - 0.13))

    def test_no_unverified_full_testosterone_coverage_claim(self):
        # The evidence file is not a license to invent absolute total-T curves.
        self.assertFalse(any(k.startswith("T_") for k in PARAMS["models"]))
        self.assertTrue(all(k in PARAMS["models"] for k in (
            "E2_ORAL", "EV_ORAL", "EV_IM", "E2_PATCH",
            "E2_GEL_ESTREVA", "E2_GEL_ESTROGEL", "E2_GEL_DIVIGEL",
        )))


if __name__ == "__main__":
    unittest.main()
