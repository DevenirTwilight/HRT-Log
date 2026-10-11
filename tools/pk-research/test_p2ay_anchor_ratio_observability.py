#!/usr/bin/env python3
"""P2-AY no-patient-data mathematical regression and provenance tests."""
import copy
import json
import math
import tempfile
from pathlib import Path
import unittest

from p2ay_anchor_ratio_observability import (
    AW_SHA256, analyze, cross_ratio_covariance, envelope_gap,
    ratio_noise_factor, read_frozen_aw, scaled,
)

ROOT = Path(__file__).resolve().parents[2]
AW = ROOT / "docs/pk-research/p2/p2aw-identifiability-design/p2aw-geometry.json"
RESULT = ROOT / "docs/pk-research/p2/p2ay-anchor-ratio/p2ay-observability.json"


class P2AYTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = read_frozen_aw(AW)
        cls.output = analyze(cls.source)

    def test_sha_checked_frozen_source(self):
        self.assertEqual(len(AW_SHA256), 64)
        self.assertEqual(self.source["phase"], "P2-AW")

    def test_provenance_mutation_refused(self):
        with tempfile.TemporaryDirectory() as td:
            fake = Path(td) / "aw.json"
            fake.write_bytes(AW.read_bytes() + b" ")
            with self.assertRaises(ValueError):
                read_frozen_aw(fake)

    def test_expected_grid_and_count(self):
        self.assertEqual(len(self.output["rows"]), 9)
        self.assertEqual(self.source["frozen_m2_count"], 15)

    def test_zero_gap_at_one_hour_is_not_evidence(self):
        row = next(r for r in self.output["rows"] if r["time_h"] == "1")
        self.assertEqual(row["minimum_model_gap_micro"], 0)
        self.assertIsNone(row["delta_method_worst_sd_factor_micro"])
        self.assertEqual(row["passes_artificial_2sd_rule"], {})

    def test_ratio_invariant_to_baseline_and_positive_scale(self):
        for r in (0.05, 0.5, 0.9):
            a, b = 150, 25
            self.assertAlmostEqual(((b+a*r)-b)/((b+a)-b), r)
            self.assertAlmostEqual(((99+2*(b+a*r))-(99+2*b)) /
                                   ((99+2*(b+a))-(99+2*b)), r)

    def test_ratio_noise_factor_formula(self):
        self.assertAlmostEqual(ratio_noise_factor(0), math.sqrt(2))
        self.assertAlmostEqual(ratio_noise_factor(1), math.sqrt(2))
        self.assertAlmostEqual(ratio_noise_factor(0.5), math.sqrt(1.5))
        with self.assertRaises(ValueError):
            ratio_noise_factor(float("nan"))

    def test_shared_anchors_induce_covariance(self):
        r, s, amp, sd = 0.2, 0.8, 150, 3
        c = cross_ratio_covariance(r,s,amp,sd)
        self.assertAlmostEqual(c, (sd/amp)**2 *((r-1)*(s-1)+r*s))
        self.assertGreater(c, 0)
        diagonal = cross_ratio_covariance(r,r,amp,sd,True)
        self.assertAlmostEqual(diagonal, (sd/amp)**2 *ratio_noise_factor(r)**2)
        self.assertGreater(diagonal, c)

    def test_same_sample_mismatch_refused(self):
        with self.assertRaises(ValueError):
            cross_ratio_covariance(.1,.2,150,3,True)

    def test_flat_envelope_intersection(self):
        self.assertEqual(envelope_gap(.5,.4,.6),0)
        self.assertAlmostEqual(envelope_gap(.2,.4,.6),.2)
        self.assertAlmostEqual(envelope_gap(.8,.4,.6),.2)

    def test_insufficient_amplitude_rejected_by_rule(self):
        a=next(r for r in self.output["rows"] if r["time_h"]=="24")
        self.assertFalse(a["passes_artificial_2sd_rule"]["A450_sd1"])

    def test_early_late_gap_numerics(self):
        a={r["time_h"]:r for r in self.output["rows"]}
        self.assertGreater(a["0.25"]["minimum_model_gap_micro"], 400000)
        self.assertGreater(a["8"]["minimum_model_gap_micro"], 35000)
        self.assertLess(a["24"]["minimum_model_gap_micro"], 500)
        self.assertGreater(a["24"]["minimum_A1h_increment_pgml_milli_for_2sd"]["sd3"],10000000)

    def test_machine_result_exact(self):
        self.assertEqual(self.output, json.loads(RESULT.read_text(encoding="utf-8")))

    def test_scaling_is_nonnegative_int(self):
        self.assertEqual(scaled(.25),250000)
        with self.assertRaises(ValueError):
            scaled(-.1)

    def test_no_clinical_authorization(self):
        self.assertEqual(self.output["production_replacement_gate"],"P2-C_HOLD")
        self.assertFalse(self.output["individual_absolute_prediction_validated"])
        self.assertEqual(self.output["no_new_locked_external_human_cohorts"],0)

if __name__=="__main__":
    unittest.main()
