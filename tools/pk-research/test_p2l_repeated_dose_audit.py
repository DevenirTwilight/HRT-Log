"""P2-L synthetic sensitivity and evidence provenance gates."""
import json
import math
import unittest
import p2l_repeated_dose_audit as p

class DoseContextTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.study=json.loads(p.STUDY.read_text())
        cls.params=json.loads(p.PARAMS.read_text())
        cls.catalog=json.loads(p.CATALOG.read_text())
        cls.report=p.audit(cls.study,cls.params,cls.catalog)
    def test_source_is_previously_seen_yaish_and_cohort_dedup(self):
        self.assertTrue(self.report["yaish_study_is_prior_P0_evidence"])
        self.assertTrue(self.report["baron2024_and_2026_not_separate_people_assumed"])
        self.assertEqual(self.report["new_independent_blinded_pk_sets"],0)
    def test_one_mg_1h_legacy_reference_unmodified(self):
        kernels=p.make_kernels(self.params,self.catalog)
        self.assertAlmostEqual(kernels["HRT_frozen"](1),144.02380642617644,places=7)
        self.assertAlmostEqual(kernels["Featherline_80kg_frozen"](1),480.7587361508129,places=7)
    def test_q6_canonical_current_and_comparator(self):
        models=self.report["models"]
        a=models["HRT_frozen"]["perfect_6h_schedule"]
        b=models["Featherline_80kg_frozen"]["perfect_6h_schedule"]
        self.assertAlmostEqual(a["predose_pg_ml"],33.07243612065076,places=7)
        self.assertAlmostEqual(a["90min_postdose_pg_ml"],96.4768002124546,places=7)
        self.assertAlmostEqual(b["predose_pg_ml"],94.82631838824157,places=7)
        self.assertAlmostEqual(b["90min_postdose_pg_ml"],302.96375015109084,places=7)
    def test_omission_lowers_concentration(self):
        for model in self.report["models"].values():
            regular=model["perfect_6h_schedule"]
            omitted=model["missing_previous_6h_dose"]
            self.assertLess(omitted["predose_pg_ml"],regular["predose_pg_ml"])
            self.assertLess(omitted["90min_postdose_pg_ml"],regular["90min_postdose_pg_ml"])
    def test_baseline_addition_exact(self):
        for kernel in p.make_kernels(self.params,self.catalog).values():
            r0=p.repeat_dose_scenario(kernel,base_pg_ml=0)
            r30=p.repeat_dose_scenario(kernel,base_pg_ml=30)
            self.assertAlmostEqual(r30["predose_pg_ml"]-r0["predose_pg_ml"],30)
            self.assertAlmostEqual(r30["90min_postdose_pg_ml"]-r0["90min_postdose_pg_ml"],30)
    def test_median_vs_mean_cannot_form_a_paired_ratio(self):
        self.assertAlmostEqual(self.report["yaish_measured_90min_median_pg_ml"],1721/3.671)
        self.assertAlmostEqual(self.report["yaish_measured_predose_group_mean_pg_ml"],204.5/3.671)
        self.assertTrue(self.report["yaish_median_and_mean_are_NOT_same_statistic"])
    def test_e1_e2_aggregate_ratio_not_a_ratio_of_means(self):
        self.assertAlmostEqual(self.report["kari_ratio_of_two_reported_means"],2340/613)
        self.assertNotAlmostEqual(self.report["kari_ratio_of_two_reported_means"],6.88,places=1)
        self.assertTrue(self.report["kari_excluded_extreme_draws_and_unknown_times_assigned_mid"])
    def test_clinical_endpoint_not_pk_prediction(self):
        self.assertFalse(self.report["clinical_accuracy_established"])
        self.assertTrue(self.report["baron_2026_measures_coagulation_NOT_PK_curve"])
        self.assertTrue(self.report["published_biomarker_change_does_NOT_estimate_clinical_vte_risk"])
    def test_invalid_inputs_rejected(self):
        f=lambda t:100*math.exp(-t) if t>0 else 0
        with self.assertRaises(ValueError):p.repeat_dose_scenario(f,interval_h=0)
        with self.assertRaises(ValueError):p.repeat_dose_scenario(f,base_pg_ml=-1)
if __name__=="__main__":unittest.main()
