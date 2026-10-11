#!/usr/bin/env python3
"""Synthetic and public-metadata-only P2-AX tests. No patient data."""
import json
import math
import unittest
from pathlib import Path
from p2ax_evidence_contract import (
    audit_record, audit_registry, profiled_shape_sse, locked_prediction_rmse
)

HERE = Path(__file__).resolve().parents[2]
REG = HERE / "docs/pk-research/p2/p2ax-evidence-contract/p2ax-source-registry.json"

def hypothetical():
    return {
        "cohort_id": "SYNTHETIC_ONLY",
        "source_url": "https://example.org/synthetic-not-human",
        "route": "SL_E2",
        "formulation": "ordinary_micronized",
        "numeric_serial_e2_available": True,
        "actual_dose_sample_clock_available": True,
        "assay_e2_traceable": True,
        "baseline_and_prior_doses_documented": True,
        "late_8h_e2_available": True,
        "reuse_permission_confirmed": True,
        "unseen_after_model_lock": True,
        "independent_cohort": True
    }

class SourceGateTests(unittest.TestCase):
    def test_actual_registry_still_on_hold(self):
        rows = json.loads(REG.read_text(encoding="utf-8"))["sources"]
        result = audit_registry(rows)
        self.assertEqual(result["metadata_candidates"], 0)
        self.assertEqual(result["late_tail_candidates"], 0)
        self.assertFalse(result["locked_external_validation_performed"])
        self.assertTrue(all(r["decision"] == "HOLD" for r in result["sources"]))
    def test_unique_source_ids(self):
        rows = json.loads(REG.read_text(encoding="utf-8"))["sources"]
        self.assertEqual(len({r["cohort_id"] for r in rows}), len(rows))
    def test_synthetic_complete_is_not_automatic_release(self):
        r = audit_record(hypothetical())
        self.assertEqual(r["decision"], "HUMAN_REVIEW_REQUIRED")
        self.assertTrue(r["late_tail_candidate"])
    def test_special_formulation_rejected(self):
        r = hypothetical(); r["formulation"] = "HPbetaCD"
        self.assertIn("FORMULATION_MISMATCH", audit_record(r)["blocked_by"])
    def test_oral_intensive_sampling_rejected(self):
        r = hypothetical(); r["route"] = "ORAL_EV"
        self.assertIn("ROUTE_NOT_SL_E2", audit_record(r)["blocked_by"])
    def test_no_late_data_not_full_tail(self):
        r = hypothetical(); r["late_8h_e2_available"] = False
        a = audit_record(r)
        self.assertTrue(a["metadata_candidate"])
        self.assertFalse(a["late_tail_candidate"])
        self.assertIn("NO_OBSERVED_LATE_8H_E2", a["blocked_by"])
    def test_not_blind_not_external(self):
        r = hypothetical(); r["unseen_after_model_lock"] = False
        self.assertIn("NOT_PRELOCK_BLIND", audit_record(r)["blocked_by"])
    def test_no_permission_not_external(self):
        r = hypothetical(); r["reuse_permission_confirmed"] = False
        self.assertIn("REUSE_NOT_CONFIRMED", audit_record(r)["blocked_by"])
    def test_duplicate_cohort_rejected(self):
        with self.assertRaises(ValueError):
            audit_registry([hypothetical(), hypothetical()])
    def test_flag_values_must_be_bool(self):
        r=hypothetical(); r["reuse_permission_confirmed"]="yes"
        with self.assertRaises(ValueError):
            audit_record(r)
    def test_missing_metadata_rejected(self):
        r=hypothetical(); del r["formulation"]
        with self.assertRaises(ValueError):
            audit_record(r)

class NumericalContractTests(unittest.TestCase):
    def test_true_affine_shape_zero_sse(self):
        r=profiled_shape_sse([0,1,2,3],[12,17,22,27])
        self.assertAlmostEqual(r["gain_diagnostic_only"],5)
        self.assertAlmostEqual(r["baseline_diagnostic_only"],12)
        self.assertAlmostEqual(r["weighted_sse_diagnostic_only"],0)
        self.assertEqual(r["residual_df"],2)
    def test_nonaffine_shape_misfit(self):
        r=profiled_shape_sse([0,1,2,3],[12,14,23,27])
        self.assertGreater(r["weighted_sse_diagnostic_only"],0)
    def test_two_points_cannot_check_shape(self):
        with self.assertRaises(ValueError):
            profiled_shape_sse([0,1],[12,17])
    def test_negative_gain_not_allowed(self):
        with self.assertRaises(ValueError):
            profiled_shape_sse([0,1,2],[3,2,1])
    def test_flat_shape_rejected(self):
        with self.assertRaises(ValueError):
            profiled_shape_sse([1,1,1],[2,3,4])
    def test_weights_positive(self):
        with self.assertRaises(ValueError):
            profiled_shape_sse([0,1,2],[1,2,3],[1,0,1])
    def test_predictive_rmse_no_refit(self):
        self.assertAlmostEqual(locked_prediction_rmse([0,2],[1,1],[1,1]),1)
        self.assertAlmostEqual(locked_prediction_rmse([0,2],[0,2],[1,1]),0)
    def test_invalid_sd_rejected(self):
        with self.assertRaises(ValueError):
            locked_prediction_rmse([1],[2],[0])
    def test_nonfinite_rejected(self):
        with self.assertRaises(ValueError):
            profiled_shape_sse([0,1,2],[1,2,math.nan])

if __name__ == "__main__":
    unittest.main()
