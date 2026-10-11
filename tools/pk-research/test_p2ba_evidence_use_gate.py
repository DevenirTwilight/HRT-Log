"""P2-BA: public-source checks, evidence counting and safe-view regression."""
import json
import tempfile
import unittest
from pathlib import Path
from p2ba_evidence_use_gate import (
    SOURCE, SOURCE_BLOB_SHA1, GROUPS, PERMISSIONS, blob_sha, load_source,
    choose_permission, summarize
)

class P2BATests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source=load_source()
        cls.results=summarize(cls.source)

    def test_source_exact_identity(self):
        self.assertEqual(blob_sha(SOURCE.read_bytes()),SOURCE_BLOB_SHA1)

    def test_mutated_source_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            bad=Path(td)/"bad.json"
            bad.write_bytes(SOURCE.read_bytes()+b" ")
            with self.assertRaises(ValueError):
                load_source(bad)

    def test_independent_study_count_not_54(self):
        self.assertEqual(self.results["independent_study_count"],3)
        self.assertEqual(self.results["study_deletion_evaluations_total_but_not_independent"],54)
        self.assertEqual(self.results["sensitivity_scenarios_per_study"],18)

    def test_all_three_legacy_study_robustness(self):
        self.assertEqual(self.results["all_three_study_wins_m2_legacy_scenarios"],18)
        for study in GROUPS:
            self.assertEqual(self.results["per_study"][study]["M2_over_Legacy"]["wins_sensitivity_scenarios"],18)

    def test_m1_counterexample_kept(self):
        self.assertEqual(self.results["all_three_study_wins_m2_m1_scenarios"],12)
        self.assertEqual(self.results["per_study"]["Rosano1997"]["M2_over_M1"]["wins_sensitivity_scenarios"],12)
        self.assertGreater(self.results["per_study"]["Rosano1997"]["M2_over_M1"]["max"],1)

    def test_development_leakage_is_not_hidden(self):
        self.assertTrue(self.results["development_exposure_of_all_three_studies"])
        self.assertTrue(self.results["frozen_p2x15_external_performance_unproven"])
        self.assertFalse(self.results["human_patient_absolute_predictions_validated"])

    def test_normalized_dual_model_comparison_requires_opt_in(self):
        key="legacy_vs_m2_normalized"
        self.assertFalse(choose_permission(key,opt_in=False,label_unverified=True)["allowed"])
        view=choose_permission(key,opt_in=True,label_unverified=True)
        self.assertTrue(view["allowed"])
        self.assertIn("BOTH",view["units"])

    def test_existing_legacy_only_with_uncertainty_label(self):
        self.assertFalse(choose_permission("legacy_pgml_historical")["allowed"])
        self.assertTrue(choose_permission("legacy_pgml_historical",label_unverified=True)["allowed"])

    def test_experimental_m2_shape_only_with_opt_in(self):
        self.assertFalse(choose_permission("m2_normalized_research")["allowed"])
        self.assertTrue(choose_permission("m2_normalized_research",opt_in=True)["allowed"])

    def test_no_clinical_false_approval_even_optin(self):
        forbidden=[k for k,v in PERMISSIONS.items() if not v["allow"]]
        self.assertGreaterEqual(len(forbidden),5)
        for name in forbidden:
            with self.subTest(name=name):
                self.assertFalse(choose_permission(name,opt_in=True,label_unverified=True)["allowed"])

    def test_unknown_product_feature_fail_closed(self):
        with self.assertRaises(ValueError):
            choose_permission("magic_future_function")

    def test_duplicate_group_fold_rejected(self):
        altered=json.loads(json.dumps(self.source))
        altered["folds"][0]["heldout_study"]=altered["folds"][1]["heldout_study"]
        with self.assertRaises(ValueError):
            summarize(altered)

    def test_heldout_cannot_appear_in_training(self):
        altered=json.loads(json.dumps(self.source))
        altered["folds"][0]["training_studies"]=[altered["folds"][0]["heldout_study"],"Price1997"]
        with self.assertRaises(ValueError):
            summarize(altered)

    def test_comparative_score_not_confidence_interval(self):
        self.assertTrue(self.results["complexity_penalties_not_aic_bic_or_bayes"])
        self.assertTrue(self.results["P2_C_HOLD"])
        self.assertEqual(self.results["no_new_independent_human_PK_cohorts"],0)

    def test_existing_machine_result_if_present(self):
        expected=SOURCE.parent.parent/"p2ba-evidence-use/p2ba-results.json"
        if expected.exists():
            self.assertEqual(self.results,json.loads(expected.read_text(encoding="utf-8")))

if __name__=="__main__":
    unittest.main()
