"""P2-P regression tests for explicitly conditional, model-generated designs."""
import json
import math
import unittest
import p2p_conditional_sampling as a

class ConditionalSamplingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inputs=[json.loads(p.read_text()) for p in (a.CFG,a.SCAN,a.PRICE,a.ROSANO)]
        cls.output=a.evaluate(*cls.inputs)
        cls.scenarios={x["Price_baseline_pg_ml_HYPOTHETICAL"]:x
                       for x in cls.output["scenarios"]}
    def threshold(self,b,t=.02):
        return next(x for x in self.scenarios[b]["threshold_sensitivity"]
                    if x["difference_threshold_normalized"]==t)
    def single(self,b,h,t=.02):
        return next(x for x in self.threshold(b,t)["single_time"] if x["time_h"]==h)
    def two(self,b,x,y,t=.02):
        return next(z for z in self.threshold(b,t)["two_time_preselected"]
                    if z["time_h"]==[x,y])
    def test_grid_and_baseline_membership_identical_to_prior_scan(self):
        self.assertEqual(self.output["source_grid_evaluated"],11088)
        self.assertEqual([self.scenarios[b]["model_candidates_from_P2O"] for b in (0,25,50)],[75,97,62])
    def test_unordered_model_pairs_are_not_subject_counts(self):
        self.assertEqual(self.scenarios[0]["total_unordered_candidate_pairs"],2775)
        self.assertEqual(self.scenarios[25]["total_unordered_candidate_pairs"],4656)
        self.assertEqual(self.scenarios[50]["total_unordered_candidate_pairs"],1891)
    def test_late_24h_not_best_at_assumed_absolute_separability(self):
        self.assertEqual(self.single(0,24)["unresolved_pairs"],2467)
        self.assertEqual(self.single(0,8)["unresolved_pairs"],1318)
        self.assertEqual(self.single(0,24)["unresolved_with_trough_divergence_at_least_2x"],761)
    def test_6_and_12h_conditional_late_only_information(self):
        v=self.two(0,6,12)
        self.assertEqual(v["unresolved_pairs"],1006)
        self.assertEqual(v["unresolved_with_trough_divergence_at_least_2x"],36)
        self.assertAlmostEqual(v["max_trough_divergence_among_unresolved"],2.8733079254412703)
    def test_optimal_time_changes_with_prior_and_objective(self):
        self.assertEqual(self.threshold(0)["best_after_4h_by_ambiguity_count"]["time_h"],[6,12])
        self.assertEqual(self.threshold(25)["best_after_4h_by_ambiguity_count"]["time_h"],[6,10])
        self.assertEqual(self.threshold(50)["best_after_4h_by_ambiguity_count"]["time_h"],[6,10])
        self.assertEqual(self.threshold(0)["best_any_pair_by_ambiguity_count"]["time_h"],[4,10])
    def test_measurement_floor_changes_ranking_and_resolution(self):
        self.assertLess(self.single(0,8,.01)["unresolved_pairs"],self.single(0,8,.02)["unresolved_pairs"])
        self.assertLess(self.single(0,8,.02)["unresolved_pairs"],self.single(0,8,.05)["unresolved_pairs"])
        self.assertEqual(self.single(0,24,.05)["unresolved_pairs"],2775)
    def test_higher_hypothetical_baseline_degrades_late_discrimination(self):
        self.assertEqual(self.single(50,24)["unresolved_pairs"],1891)
        self.assertEqual(self.two(25,6,12)["unresolved_pairs"],2375)
    def test_identical_profiles_remain_unresolved(self):
        rows=[{"time_values":[1,0.1],"q6_relative_to_1mg_1h":.01},
              {"time_values":[1,0.1],"q6_relative_to_1mg_1h":.1}]
        v=a.pair_summary(rows,[0,1],0.02)
        self.assertEqual(v["unresolved_pairs"],1)
        self.assertEqual(v["unresolved_with_trough_divergence_at_least_2x"],1)
        self.assertAlmostEqual(v["max_trough_divergence_among_unresolved"],10)
    def test_invalid_cutoffs_rejected(self):
        rows=[{"time_values":[1],"q6_relative_to_1mg_1h":1}]
        with self.assertRaises(ValueError):a.pair_summary(rows,[],-1)
        with self.assertRaises(ValueError):a.pair_summary(rows,[0],float("nan"))
        with self.assertRaises(ValueError):a.pair_summary(rows,[0],.02,1)
    def test_explicitly_not_clinical_or_doll_refit(self):
        r=self.output
        self.assertFalse(r["clinical_blood_draw_schedule_prescribed"])
        self.assertFalse(r["Doll144_used_as_amplitude_anchor"])
        self.assertFalse(r["clinical_accuracy_established"])
        self.assertFalse(r["production_change_authorized"])
        self.assertEqual(r["new_LOCKED_EXTERNAL_human_data_sets"],0)
if __name__=="__main__":unittest.main()
