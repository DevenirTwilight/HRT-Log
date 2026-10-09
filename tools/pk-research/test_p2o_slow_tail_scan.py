"""P2-O: property, provenance, and cross-model ambiguity checks."""
import json
import math
import unittest
import p2o_slow_tail_scan as p

class IdentifiabilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config=json.loads(p.CFG.read_text())
        cls.price=json.loads(p.PRICE.read_text())
        cls.rosano=json.loads(p.ROSANO.read_text())
        cls.result=p.summarize(cls.config,cls.price,cls.rosano)
    def test_grid_includes_rate_equality_case(self):
        self.assertEqual(self.result["total_grid"],11088)
        self.assertEqual(self.result["equal_slow_and_elimination_rate_cases_included"],462)
    def test_accept_counts_including_baseline_sensitivity(self):
        self.assertEqual([(s["hypothetical_price_baseline"],s["accepted"]) for s in self.result["scenarios"]],
                         [(0,75),(25,97),(50,62)])
    def test_predose_spread_large_even_given_early_constraints(self):
        first=self.result["scenarios"][0]
        self.assertAlmostEqual(first["q6_predose_min"],.023973920234120597,places=7)
        self.assertAlmostEqual(first["q6_predose_max"],.1652483890926226,places=7)
        self.assertGreater(first["q6_predose_max_over_min"],6)
    def test_early_matching_pair_with_divergent_tail(self):
        pair=self.result["scenarios"][0]["best_pair_1percent_metrics"]
        self.assertLess(pair["worst_early_metric_relative_separation"],.01)
        self.assertGreater(pair["predose_ratio"],4)
        self.assertAlmostEqual(pair["predose_ratio"],4.596081865066499,delta=.002)
    def test_24h_auc_range_and_matched_pair(self):
        s=self.result["scenarios"][0]
        self.assertAlmostEqual(s["auc_discrete_min"],2.1984919755839214,places=5)
        self.assertAlmostEqual(s["auc_discrete_max"],3.292994472058313,places=5)
        a=s["best_pair_1percent_metrics"]["A"]
        b=s["best_pair_1percent_metrics"]["B"]
        self.assertGreater(abs(a["auc_0_24_discrete_over_h1_h"]-b["auc_0_24_discrete_over_h1_h"]),.7)
    def test_exact_mass_balance_continuous_vs_infinite(self):
        for example in (self.result["scenarios"][0]["best_pair_1percent_metrics"]["A"],
                        self.result["scenarios"][0]["best_pair_1percent_metrics"]["B"]):
            case=example["parameters"]
            expected=1/(case["k_elim_h"]*p.model.unnormalized(1,case))
            self.assertAlmostEqual(example["auc_0_infinity_over_h1_h"],expected,places=10)
            self.assertLess(example["auc_0_24_continuous_over_h1_h"],expected+1e-8)
            self.assertGreater(example["auc_0_24_continuous_over_h1_h"],0)
    def test_convolution_equal_rates_is_finite(self):
        equal=p.cfg_to_case(5,8,.4,.4,.5)
        v=p.model.unnormalized(1,equal)
        self.assertTrue(math.isfinite(v) and v>0)
    def test_repeated_dosing_converges_and_omission_changes_value(self):
        pair=self.result["scenarios"][0]["best_pair_1percent_metrics"]
        for record in (pair["A"],pair["B"]):
            case=record["parameters"]
            a=p.predose_q6(case,120)
            self.assertAlmostEqual(a,p.predose_q6(case,240),places=6)
            last=.5*p.model.unnormalized(6,case)/p.model.unnormalized(1,case)
            self.assertGreater(a-last,0)
    def test_doll_and_clinical_accuracy_not_claimed(self):
        x=self.result
        self.assertFalse(x["Doll144_used_for_model_fit"])
        self.assertEqual(x["new_blind_human_PK_dataset_count"],0)
        self.assertFalse(x["clinical_accuracy_established"])
        self.assertFalse(x["90_percent_human_interval_coverage_established"])
        self.assertFalse(x["production_model_replacement_authorized"])
    def test_invalid_negative_time_and_event_count(self):
        c=p.cfg_to_case(5,8,.8,.2,.1)
        with self.assertRaises(ValueError):p.auc_continuous(-1,c)
        with self.assertRaises(ValueError):p.predose_q6(c,0)
if __name__=="__main__":unittest.main()
