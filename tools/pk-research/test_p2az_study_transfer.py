"""P2-AZ source/checkpoint and synthetic group-deletion regression tests."""
import json
import math
import unittest
from pathlib import Path
from p2az_study_transfer import (
    DESIGN, FROZEN, KOM, PRICE, ROSANO, STUDIES, analyze,
    choose, load_study_data, make_grids, mse_for_study,
    run, shape_data, study_scores,
)
from p2aw_sampling_design import frozen_candidates, legacy_normalized, read_inputs

class P2AZTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.design,cls.raw,cls.kom_base=load_study_data()
        cls.p2x,cls.old=read_inputs(FROZEN)
        cls.models=frozen_candidates(cls.p2x)

    def test_study_count_independence_unit(self):
        self.assertEqual(STUDIES,("Price1997","Rosano1997","Komesaroff1998"))
        self.assertEqual(len(self.models),15)

    def test_original_aggregates_not_subject_rows(self):
        self.assertEqual(len(self.raw["Price1997"]["P2-I"]),9)
        self.assertEqual(len(self.raw["Rosano1997"]),4)
        self.assertEqual(len(self.raw["Komesaroff1998"]),2)
        self.assertAlmostEqual(self.kom_base,89.4)

    def test_candidate_counts_and_distinct_structure(self):
        grids=make_grids(self.design)
        self.assertEqual((len(grids["M1"]),len(grids["M2"])),(32,216))
        self.assertTrue(all(p["effective_slow_weight"]==0 for p in grids["M1"]))
        self.assertTrue(all(p["effective_slow_weight"]>0 for p in grids["M2"]))

    def test_baseline_normalization(self):
        scores=shape_data(self.raw,None,20,100,self.kom_base,"P2-I")
        self.assertEqual(tuple(len(scores[k]) for k in STUDIES),(8,3,1))
        self.assertAlmostEqual(scores["Komesaroff1998"][0][1],
                               (486.6-89.4)/(1969-89.4))

    def test_both_graphs_same_price_cohort(self):
        scores_i=shape_data(self.raw,None,0,0,self.kom_base,"P2-I")
        scores_aj=shape_data(self.raw,None,0,0,self.kom_base,"P2-AJ")
        self.assertEqual(len(scores_i["Price1997"]),len(scores_aj["Price1997"]))
        self.assertNotEqual(scores_i["Price1997"],scores_aj["Price1997"])
        self.assertEqual(scores_i["Rosano1997"],scores_aj["Rosano1997"])

    def test_invalid_assumed_baseline_rejected(self):
        with self.assertRaises(ValueError):
            shape_data(self.raw,None,1000,0,self.kom_base,"P2-I")
        with self.assertRaises(ValueError):
            shape_data(self.raw,None,0,5000,self.kom_base,"P2-I")

    def test_negative_tail_increment_is_not_clipped(self):
        # P2-AJ manual Price18h 23.85 pg/mL minus assumed b=24.
        # Small negative deltas from uncertainty should not become
        # fictitious positive or zero observed postdose responses.
        s=shape_data(self.raw,None,24,0,self.kom_base,"P2-AJ")
        observed=next(v for t,v in s["Price1997"] if t==18)
        self.assertLess(observed,0.0)
        self.assertAlmostEqual(observed,(-0.15)/(451.72-24),places=10)

    def test_zero_loss_for_matching_shape(self):
        points=((.25,.25),(.5,.5))
        self.assertAlmostEqual(mse_for_study(points,lambda t:t,1),0.0)

    def test_frozen_legacy_matches_prior_checkpoint(self):
        self.assertAlmostEqual(legacy_normalized(1,self.old),1)
        self.assertAlmostEqual(legacy_normalized(2,self.old),.73591577,places=7)

    def test_training_selection_isolated_from_test_fold_mutation(self):
        data=shape_data(self.raw,None,0,0,self.kom_base,"P2-I")
        grid=make_grids(self.design)
        scores=study_scores(data,self.old,{"M1":grid["M1"]},[])
        # Deliberately sabotage *held-out* Price observations. Selection must
        # only reference Rosano+Komesaroff.
        tampered=dict(data)
        tampered["Price1997"]=tuple((t,20*v+100) for t,v in data["Price1997"])
        scores_changed=study_scores(tampered,self.old,{"M1":grid["M1"]},[])
        tr=("Rosano1997","Komesaroff1998")
        self.assertEqual(choose(scores["M1"],tr)["model"],
                         choose(scores_changed["M1"],tr)["model"])
        self.assertNotEqual(choose(scores["M1"],tr)["losses"]["Price1997"],
                            choose(scores_changed["M1"],tr)["losses"]["Price1997"])

    def test_train_fit_no_worse_than_grid_first(self):
        data=shape_data(self.raw,None,0,0,self.kom_base,"P2-I")
        grids=make_grids(self.design)
        scores=study_scores(data,self.old,grids,self.models)
        tr=("Price1997","Rosano1997")
        sel=choose(scores["M2"],tr)
        val=lambda row:sum(row["losses"][s] for s in tr)
        self.assertLessEqual(val(sel),val(scores["M2"][0])+1e-12)

    def test_two_fold_surrogate_is_not_blind_validation(self):
        ans=run()
        self.assertTrue(ans["source_exposed_previous_design_leakage"])
        self.assertTrue(ans["frozen15_candidate_set_was_exposed_to_all_three_studies_UPSTREAM"])
        self.assertFalse(ans["individual_level_unseen_validation"])
        self.assertEqual(ans["source_scenarios"],18)
        self.assertEqual(len(ans["folds"]),54)
        self.assertTrue(ans["P2_C_HOLD"])
        self.assertFalse(ans["aicc_formal_model_comparison_possible"])

    def test_three_heldout_sets_preserve_cohort_grouping(self):
        ans=run()
        for fold in ans["folds"]:
            self.assertNotIn(fold["heldout_study"],fold["training_studies"])
            self.assertEqual(len(set(fold["training_studies"])),2)
            self.assertEqual(len(set((*fold["training_studies"],fold["heldout_study"]))),3)

if __name__=="__main__":
    unittest.main()
