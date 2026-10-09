"""P2-Q: synthetic model invariants and non-clinical evidentiary gates."""
import json
import math
import unittest
import p2q_identifiability as p


class IdentifiabilityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg=json.loads(p.CONFIG.read_text())
        cls.result=p.evaluate(cls.cfg)

    def test_p2o_source_pool_reproduced_not_a_new_cohort(self):
        self.assertEqual(self.result['candidate_count'],75)
        self.assertEqual(self.result['independent_locked_external_human_datasets'],0)
        self.assertFalse(self.result['Doll_144_used_for_refit'])

    def test_structural_F_over_V_symmetry(self):
        dose=1.;F=.3;V=100.;shape=.8
        self.assertAlmostEqual(dose*F/V*shape,dose*(5*F)/(5*V)*shape)

    def test_slow_rate_disappears_exactly_when_weight_zero(self):
        c=p.synthetic_truth_cases()['transit_only']
        d=dict(c,k_slow_h=.02)
        for t in (0,.1,.4,1,3,8,24):
            self.assertAlmostEqual(p.normalized_case(c,t),p.normalized_case(d,t),places=12)

    def test_first_order_absorption_elimination_flip_flop(self):
        c={'n_fast':1,'k_fast_h':1.8,'k_elim_h':.4,'k_slow_h':.2,'slow_effective_weight':0}
        d=dict(c,k_fast_h=.4,k_elim_h=1.8)
        for t in (.01,.25,1,2,6,18,24):
            self.assertAlmostEqual(p.normalized_case(c,t),p.normalized_case(d,t),places=11)

    def test_two_time_points_with_free_intercept_and_gain(self):
        # b=30,a=150, truth profile [0.3,1]; alternative profile [0.25,1]
        y=[30+150*.3,30+150*1]
        fit=p.nonnegative_linear_fit([.25,1],y,[5,5],'unknown_b_a',30,150)
        self.assertAlmostEqual(fit['fixed_reference_weighted_sse'],0,places=11)
        self.assertAlmostEqual(fit['baseline'],40)
        self.assertAlmostEqual(fit['gain'],140)

    def test_nuisance_profile_never_increases_score(self):
        x=[.05,.4,1,.15];y=[35,66,173,47];sigma=[5,6,12,5]
        s0=p.nonnegative_linear_fit(x,y,sigma,'known_b_a',30,150)['fixed_reference_weighted_sse']
        s1=p.nonnegative_linear_fit(x,y,sigma,'unknown_a',30,150)['fixed_reference_weighted_sse']
        s2=p.nonnegative_linear_fit(x,y,sigma,'unknown_b_a',30,150)['fixed_reference_weighted_sse']
        self.assertGreaterEqual(s0+1e-9,s1)
        self.assertGreaterEqual(s1+1e-9,s2)

    def test_boundary_optimum_nonnegative(self):
        z=p.nonnegative_linear_fit([0,1,2],[40,10,5],[1,1,1],'unknown_b_a',10,2)
        self.assertGreaterEqual(z['baseline'],0)
        self.assertGreaterEqual(z['gain'],0)
        self.assertAlmostEqual(z['gain'],0,places=10)

    def test_candidate_comparison_counts_and_nuisance_penalty(self):
        lookup={(r['assumed_assay'],r['schedule']):r for r in self.result['uncertain_nuisance_survey']}
        e=lookup['idealized_precision_only','early']
        self.assertEqual(e['n_ordered_different_model_comparisons'],5550)
        self.assertEqual([e['unresolved_synthetic_ordered_comparisons'][key] for key in
                          ('known_b_a','unknown_a','unknown_b_a')],[527,654,1249])
        r=lookup['idealized_precision_only','predose_and_late']
        self.assertEqual(r['unresolved_synthetic_ordered_comparisons']['unknown_b_a'],294)
        self.assertEqual(lookup['idealized_precision_only','early_and_late']['unresolved_synthetic_ordered_comparisons']['unknown_b_a'],372)

    def test_assay_assumption_controls_pseudo_identifiability(self):
        lookup={(r['assumed_assay'],r['schedule']):r for r in self.result['uncertain_nuisance_survey']}
        self.assertEqual(lookup['large_error_illustration','early_and_late']['unresolved_synthetic_ordered_comparisons']['unknown_b_a'],5550)
        self.assertGreater(lookup['small_error_illustration','early_and_late']['unresolved_synthetic_ordered_comparisons']['unknown_b_a'],
                           lookup['idealized_precision_only','early_and_late']['unresolved_synthetic_ordered_comparisons']['unknown_b_a'])
        self.assertEqual(lookup['idealized_precision_only','early_and_late']['of_those_q6_trough_diverges_2x']['unknown_b_a'],74)

    def test_parsimony_not_universally_superior_or_inferior(self):
        lookup={(r['error_scenario'],r['synthetic_truth_family']):r
                for r in self.result['synthetic_holdout_family_comparison']}
        perfect_simple=lookup['idealized_precision_only','one_input']
        self.assertEqual(perfect_simple['winner_count']['penalty']['one_input'],40)
        perfect_complex=lookup['idealized_precision_only','dual_slow_input']
        self.assertGreater(perfect_complex['winner_count']['penalty']['dual_slow_input'],30)
        low_precision_complex=lookup['large_error_illustration','dual_slow_input']
        self.assertEqual(low_precision_complex['winner_count']['penalty']['dual_slow_input'],0)
        # Aggressive penalty can sacrifice an actually complex truth.
        self.assertGreater(low_precision_complex['median_oracle_standardized_holdout_mse']['penalty'],
                           low_precision_complex['median_oracle_standardized_holdout_mse']['no_penalty'])

    def test_positive_error_floor(self):
        y=[0,10,100]
        e={'absolute_floor_pg_ml':5.,'proportional_fraction':.1}
        self.assertEqual(p.fixed_error_sigma(y,e)[0],5)
        self.assertAlmostEqual(p.fixed_error_sigma(y,e)[2],math.sqrt(125))

    def test_invalid_negative_inputs_protected(self):
        with self.assertRaises(ValueError):p.nonnegative_linear_fit([1],[2],[0],'unknown_b_a',1,1)
        with self.assertRaises(ValueError):p.nonnegative_linear_fit([1],[2],[1],'unknown_b_a',-1,1)
        with self.assertRaises(ValueError):p.nonnegative_linear_fit([1],[float('nan')],[1],'unknown_b_a',1,1)
        with self.assertRaises(ValueError):p.nonnegative_linear_fit([1],[2],[1],'extra',1,1)

    def test_no_production_or_clinical_claims(self):
        z=self.result
        self.assertTrue(z['model_comparison_not_official_IC'])
        self.assertFalse(z['actual_human_prediction_coverage_established'])
        self.assertFalse(z['production_change_authorized'])
        self.assertTrue(z['source_assay_errors_are_hypothetical'])

if __name__=='__main__':unittest.main()
