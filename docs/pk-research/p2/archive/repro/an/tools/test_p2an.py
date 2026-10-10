"""P2-AN synthetic-only QA. Run: python3 -m unittest discover -s tools -p 'test_p2an.py' -v"""
import importlib.util
import json
import math
import pathlib
import shutil
import tempfile
import unittest

P=pathlib.Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('p2an_audit',P/'p2an_audit.py')
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class RatioTest(unittest.TestCase):
    def test_formula(self): self.assertAlmostEqual(m.ratio(25,225,85),.3)
    def test_invariance_offset(self):self.assertAlmostEqual(m.ratio(125,325,185),.3)
    def test_invariance_scale(self):self.assertAlmostEqual(m.ratio(5,45,17),.3)
    def test_invariance_general(self):
        d=m.affine_assay_invariance();self.assertLess(d['max_affine_abs_delta'],1e-12)
    def test_nonlinear_bias_not_cancelled(self):
        d=m.affine_assay_invariance();self.assertGreater(d['max_nonlinear_abs_delta'],.04)
    def test_time_drift_not_cancelled(self):
        d=m.affine_assay_invariance();self.assertAlmostEqual(d['max_time_variable_abs_delta'],.04)
    def test_reject_zero_denominator(self):
        with self.assertRaises(ValueError):m.ratio(25,25,18)
    def test_reject_negative_denominator(self):
        with self.assertRaises(ValueError):m.ratio(25,24,18)
    def test_reject_nonfinite(self):
        with self.assertRaises(ValueError):m.ratio(25,float('nan'),18)

class CovarianceTest(unittest.TestCase):
    def test_dimensions(self):
        c=m.covariance_experiment()['covariance_delta_approx'];self.assertEqual([len(row) for row in c],[3,3,3])
    def test_symmetric(self):
        c=m.covariance_experiment()['covariance_delta_approx']
        for i in range(3):
            for j in range(3):self.assertAlmostEqual(c[i][j],c[j][i])
    def test_diagonal(self):
        c=m.ratio_covariance(25,225,[145,85,45],5,5,[5,5,5]);self.assertAlmostEqual(c[0][0],.00095)
    def test_positive_offdiagonal_shared_anchor(self):
        c=m.ratio_covariance(25,225,[145,85,45],5,5,[5,5,5]);self.assertGreater(c[0][1],0)
    def test_offdiagonal_vanish_if_baseline_and_anchor_known(self):
        c=m.ratio_covariance(25,225,[145,85],0,0,[5,5]);self.assertEqual(c[0][1],0)
    def test_anchor_only_derivative_at_unit_ratio(self):
        c=m.ratio_covariance(25,225,[225],5,5,[0]);self.assertAlmostEqual(c[0][0],25/40000)
    def test_invalid_sd(self):
        with self.assertRaises(ValueError):m.ratio_covariance(25,225,[85],-1,5,[5])
    def test_mismatched_length(self):
        with self.assertRaises(ValueError):m.ratio_covariance(25,225,[85,45],5,5,[5])
    def test_monte_carlo_matches_delta(self):
        r=m.covariance_experiment();self.assertLess(r['max_elementwise_relative_covariance_delta_mc'],.03)
    def test_correlated(self):
        r=m.covariance_experiment();self.assertGreater(r['correlation_r2_r4_delta'],.25)

class WeakAnchorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.r=m.weak_anchor_ratio_sensitivity(trials=30000)
    def test_23_passes_pilot_gate(self):
        self.assertTrue(self.r['scenarios']['23']['passes_p2am_3sigma_rule'])
    def test_23_has_broader_quantile_interval(self):
        d=self.r['scenarios'];w=lambda z:z['accepted_ratio_97_5_percentile']-z['accepted_ratio_2_5_percentile']
        self.assertGreater(w(d['23']),w(d['200'])*5)
    def test_23_has_nonnegligible_denominator_failures(self):
        self.assertGreater(self.r['scenarios']['23']['crossed_or_zero_denominator_fraction'],0)
    def test_noisy_observed_gate_rejects_many_weak_anchor_cases(self):
        self.assertLess(self.r['scenarios']['23']['observed_p2am_quality_gate_retention_fraction'],.7)
    def test_observed_gate_still_broad_at_weak_anchor(self):
        z=self.r['scenarios']['23']
        self.assertGreater(z['observed_gate_ratio_97_5_percentile']-z['observed_gate_ratio_2_5_percentile'],.5)
    def test_observed_gate_limits_positive_ratio_denominator(self):
        z=self.r['scenarios']['23']
        self.assertGreater(z['observed_gate_ratio_2_5_percentile'],-1.0)
    def test_200_remains_near_point45(self):
        self.assertAlmostEqual(self.r['scenarios']['200']['accepted_ratio_median'],.45,delta=.005)
    def test_same_seed_same_output(self):
        self.assertEqual(m.weak_anchor_ratio_sensitivity(trials=1200),m.weak_anchor_ratio_sensitivity(trials=1200))

class MissingnessTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.c=m.construct_cohort()
    def test_cohort(self):self.assertEqual(len(self.c),120)
    def test_deterministic(self):self.assertEqual(self.c,m.construct_cohort())
    def test_complete_covers_all(self):
        r=m.evaluate(self.c,'complete');self.assertEqual(r['subjects'],120);self.assertEqual(r['scored_points'],600)
    def test_full_mockA_beats_B(self):
        self.assertGreater(m.evaluate(self.c,'complete')['subjects_equal_weight_delta_mockB_minus_mockA'],0)
    def test_time_pattern_reverses(self):
        self.assertLess(m.evaluate(self.c,'drop_late_evaluations')['subjects_equal_weight_delta_mockB_minus_mockA'],0)
    def test_mnar_pattern_reverses(self):
        self.assertLess(m.evaluate(self.c,'signal_dependent_mnar')['subjects_equal_weight_delta_mockB_minus_mockA'],0)
    def test_mcar_preserves_sign_seed(self):
        self.assertGreater(m.evaluate(self.c,'mcar')['subjects_equal_weight_delta_mockB_minus_mockA'],0)
    def test_time_dependent_remaining(self):
        r=m.evaluate(self.c,'drop_late_evaluations')
        self.assertEqual(r['retained_by_time']['2.0'],120)
        self.assertLess(r['retained_by_time']['8.0'],25)
    def test_exclusion_report(self):
        r=m.evaluate(self.c,'mcar');self.assertEqual(r['excluded_subjects'],120-r['subjects'])
    def test_equal_weight_vs_point_weight_differ_when_miss(self):
        r=m.evaluate(self.c,'drop_late_evaluations')
        self.assertNotAlmostEqual(r['points_weighted_delta'],r['subjects_equal_weight_delta_mockB_minus_mockA'],places=6)
    def test_partial_bound_contains_complete_delta(self):
        full=m.evaluate(self.c,'complete')['subjects_equal_weight_delta_mockB_minus_mockA']
        z=m.partial_identification_interval(self.c,'complete')
        self.assertAlmostEqual(z['full_schedule_delta_lower'],full)
        self.assertAlmostEqual(z['full_schedule_delta_upper'],full)
        self.assertEqual(z['missing_evaluations'],0)
    def test_missing_bounds_contain_hidden_complete_truth(self):
        truth=m.evaluate(self.c,'complete')['subjects_equal_weight_delta_mockB_minus_mockA']
        for name in ['mcar','drop_late_evaluations','signal_dependent_mnar']:
            z=m.partial_identification_interval(self.c,name)
            self.assertLessEqual(z['full_schedule_delta_lower'],truth+1e-12)
            self.assertGreaterEqual(z['full_schedule_delta_upper'],truth-1e-12)
    def test_partial_identification_reveals_rank_uncertainty(self):
        z=m.partial_identification_interval(self.c,'drop_late_evaluations')
        self.assertEqual(z['identifies_sign'],'UNDETERMINED')
    def test_absolute_loss_difference_sharp_bounds(self):
        a,b=.3,.35
        for y in [0.,.2,.3,.4,100.]:
            self.assertLessEqual(abs(abs(b-y)-abs(a-y)),abs(b-a)+1e-12)
    def test_unknown_pattern(self):
        with self.assertRaises(ValueError):m.retain_mask(self.c[0],'unknown',123)
    def test_subject_boot_ci(self):
        r=m.evaluate(self.c,'complete');ci=m.bootstrap_subject_ci(r['people'],n=250)
        self.assertLess(ci[0],ci[1]);self.assertGreater(ci[0],0)
    def test_subject_ci_deterministic(self):
        r=m.evaluate(self.c,'complete');self.assertEqual(m.bootstrap_subject_ci(r['people'],300),m.bootstrap_subject_ci(r['people'],300))
    def test_tiny_group_rejected(self):
        r=m.evaluate(self.c,'complete');
        with self.assertRaises(ValueError):m.bootstrap_subject_ci(r['people'][:1])
    def test_bad_stratum_rejected(self):
        r=m.evaluate(self.c,'complete');
        with self.assertRaises(ValueError):m.stratified_subject_ci(r['people'][:1])
    def test_strata_ci(self):
        r=m.evaluate(self.c,'complete');ci=m.stratified_subject_ci(r['people'],n=250)
        self.assertLess(ci[0],ci[1])
    def test_percentile(self):self.assertEqual(m.percentile([0,1,2],.5),1)

class ReferenceTest(unittest.TestCase):
    def test_reference_fixture_stays_synthetic(self):
        r=m.reference_gate();self.assertEqual(r['origin'],'SYNTHETIC');self.assertFalse(r['clinical_validation'])
    def test_reference_fixture_8_subjects(self):
        r=m.reference_gate();self.assertEqual((r['subjects'],r['scored_samples']),(8,40))
    def test_reference_absolute_gate_blocked(self):
        r=m.reference_gate();self.assertIn('BLOCKED',r['absolute_pgml_gate'])
    def test_future_human_case_rejects_incomplete_manifest(self):
        import p2am_protocol_reference as q;
        with self.assertRaises(ValueError):q.validate_manifest({'phase':'P2-AM','origin':'EXTERNAL_HUMAN'})
    def test_future_real_nonquantified_sample_fails_closed(self):
        import p2am_protocol_reference as q;root=P.parent
        with tempfile.TemporaryDirectory() as t:
            z=pathlib.Path(t)
            for name in ('manifest.json','subjects.csv','doses.csv','samples.csv','predictions.csv'):
                shutil.copy2(root/'inputs'/name,z/name)
            path=z/'samples.csv';s=path.read_text();s=s.replace(',YES,5,3,',',NO,5,3,',1);path.write_text(s)
            with self.assertRaisesRegex(ValueError,'below-quantification-limit'):
                q.load_and_score(z)

if __name__=='__main__':unittest.main()
