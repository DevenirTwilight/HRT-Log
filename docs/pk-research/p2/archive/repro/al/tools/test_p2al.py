"""P2-AL regression and mathematical-bounds tests. Python stdlib only."""
import csv
import json
import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent))
import p2al_acceptance as p

class PairedModelAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inputs=p.read_inputs()
        cls.result=p.evaluate(cls.inputs)

    def test_expected_scenarios(self):
        self.assertEqual(len(self.result['scenario_scores']),10)

    def test_no_new_data(self):
        self.assertTrue(self.result['historical_selection_leakage'])
        self.assertTrue(self.inputs['ag']['previously_seen_sources'])

    def test_price_never_used_for_training_selection(self):
        self.assertFalse(self.inputs['ag']['price_in_training'])
        self.assertTrue(self.inputs['ag']['price_1h_used_only_for_heldout_study_amplitude'])

    def test_price_later_times_held_out(self):
        self.assertEqual(p.HOLDOUT,(2,3,4,6,8,12,18,24))
        self.assertEqual(self.inputs['ag_summary']['training_n_timepoints'],7)

    def test_grid(self):
        self.assertEqual(p.CAPS,(100,225))
        self.assertEqual(p.BACKGROUNDS,(0,6,12,18,24))

    def test_readings_exactly_one_set(self):
        self.assertEqual(len(self.inputs['aj']['digitized_points']),9)
        self.assertAlmostEqual(self.inputs['aj']['digitized_points'][0]['value_pg_ml'],451.72414)

    def test_shapes_fit_anchor(self):
        for cap in p.CAPS:
            for b in p.BACKGROUNDS:
                r,s=p.prepared(self.inputs['ag'],self.inputs['aj'],cap,b)
                for f in ('M1','M2'):
                    self.assertEqual(len(s[f]),8)
                    self.assertTrue(all(math.isfinite(k) for k in s[f].values()))

    def test_new_anchor_norm(self):
        b=24;a=451.72414
        self.assertAlmostEqual(p.predict_ratio(1,a,b),a)

    def test_nominal_mse_nonnegative(self):
        for cap in p.CAPS:
            for b in p.BACKGROUNDS:
                r,s=p.prepared(self.inputs['ag'],self.inputs['aj'],cap,b)
                for f in ('M1','M2'):
                    self.assertGreaterEqual(p.mse(r,s,f,b,r[1]['value_pg_ml']),0)

    def test_difference_is_actual_paired_mse(self):
        r,s=p.prepared(self.inputs['ag'],self.inputs['aj'],100,12)
        self.assertAlmostEqual(p.difference(r,s,12,r[1]['value_pg_ml']),
                               p.mse(r,s,'M2',12,r[1]['value_pg_ml'])-p.mse(r,s,'M1',12,r[1]['value_pg_ml']),places=7)

    def test_exact_bounded_nominal_in_range(self):
        for cap in p.CAPS:
            for b in p.BACKGROUNDS:
                r,s=p.prepared(self.inputs['ag'],self.inputs['aj'],cap,b)
                a=r[1]['value_pg_ml']
                lo=p.bounded_delta(r,s,b,True)['delta_mse']
                hi=p.bounded_delta(r,s,b,False)['delta_mse']
                self.assertLessEqual(lo,p.difference(r,s,b,a)+1e-7)
                self.assertLessEqual(p.difference(r,s,b,a),hi+1e-7)

    def test_rectangular_corner_bounds_at_anchor_grid(self):
        # Confirm analytical extrema contain explicitly enumerated all 2^8
        # observation corners at five anchor values (including the endpoints).
        for cap,b in ((100,0),(100,24),(225,12)):
            r,s=p.prepared(self.inputs['ag'],self.inputs['aj'],cap,b)
            aa=r[1]['value_pg_ml'];ww=r[1]['visual_window_pg_ml']
            lo=p.bounded_delta(r,s,b,True)['delta_mse']
            hi=p.bounded_delta(r,s,b,False)['delta_mse']
            for frac in (0,.25,.5,.75,1):
                a=aa-ww+2*ww*frac
                for mask in range(1<<len(p.HOLDOUT)):
                    ys={t:r[t]['value_pg_ml']+r[t]['visual_window_pg_ml']*(1 if mask&(1<<i) else -1) for i,t in enumerate(p.HOLDOUT)}
                    d=p.difference(r,s,b,a,ys)
                    self.assertGreaterEqual(d,lo-1e-7)
                    self.assertLessEqual(d,hi+1e-7)

    def test_extrema_are_reachable(self):
        for cap in p.CAPS:
            for b in p.BACKGROUNDS:
                r,s=p.prepared(self.inputs['ag'],self.inputs['aj'],cap,b)
                for minimum in (True,False):
                    x=p.bounded_delta(r,s,b,minimum)
                    a=x['at_anchor_pg_ml'];ys={}
                    for t in p.HOLDOUT:
                        h1,h2=s['M1'][t],s['M2'][t]
                        sign=(1 if (h2>h1)==minimum else -1)
                        ys[t]=r[t]['value_pg_ml']+sign*r[t]['visual_window_pg_ml']
                    self.assertAlmostEqual(x['delta_mse'],p.difference(r,s,b,a,ys),places=5)

    def test_24h_pressure_nominal(self):
        self.assertAlmostEqual(self.result['identifiability']['P2_AF_refined_q24_median_span_ratio'],37.57778154923879)

    def test_early_robustness_nominal(self):
        self.assertAlmostEqual(self.result['identifiability']['P2_AF_refined_auc08_median_span_ratio'],1.2891987508961766)

    def test_long_tail_all15_span_gt2(self):
        self.assertTrue(self.result['identifiability']['P2_AF_refined_q24_all15_gt2'])

    def test_long_tail_many_span_gt5(self):
        self.assertEqual(self.result['identifiability']['P2_AF_refined_q24_gt5_count'],13)

    def test_auc_conflict(self):
        self.assertAlmostEqual(self.result['price_auc']['P2_AJ_raw_b0_trapezoid'],1550.5664)
        self.assertEqual(self.result['price_auc']['published_table1_auc'],2109)

    def test_stress_gap_positive(self):
        self.assertGreater(self.result['price_auc']['minimum_gap_even_under_subjective_upward_windows_b0'],300)

    def test_scenario_outcomes_counts(self):
        self.assertEqual(self.result['wins'],{
            'M1_all_reading_corners':2,'M2_all_reading_corners':5,
            'undetermined_by_intervals':3})

    def test_m1_cap_100_early_background(self):
        x=[r for r in self.result['scenario_scores'] if r['rosano_baseline_cap_pmol_l']==100]
        self.assertEqual([v['interval_rank_result'] for v in x[:2]],['M1_all_reading_corners']*2)
        self.assertTrue(all(v['interval_rank_result']=='undetermined_by_intervals' for v in x[2:]))

    def test_m2_cap225(self):
        x=[r for r in self.result['scenario_scores'] if r['rosano_baseline_cap_pmol_l']==225]
        self.assertTrue(all(v['interval_rank_result']=='M2_all_reading_corners' for v in x))

    def test_model_choice_not_uniform(self):
        self.assertEqual(len([v for v in self.result['wins'].values() if v>0]),3)

    def test_clinical_gates_blocked(self):
        gates={g['id']:g for g in self.result['decision_gates']}
        self.assertEqual(gates['P2']['status'],'blocked')
        for key in ('R1','R2','R3','R4'):
            self.assertEqual(gates[key]['status'],'failed')
        for key in ('R5','R6'):
            self.assertEqual(gates[key]['status'],'missing')

    def test_no_release_guarantee(self):
        self.assertIn('Integration into new release requires fresh validation',self.result['decision_gates'][1]['notes'])

    def test_1h_anchor_not_scored(self):
        self.assertNotIn(1,p.HOLDOUT)

    def test_gaussian_not_assumed(self):
        self.assertIn('not CI',self.result['uncertainty_rule'])

    def test_repeated_evaluation_equal(self):
        self.assertEqual(p.evaluate(self.inputs),p.evaluate(self.inputs))

    def test_no_network_in_standard_module(self):
        source=Path(p.__file__).read_text()
        self.assertNotIn('requests.',source)
        self.assertNotIn('urllib.',source)
        self.assertNotIn('github.',source)

    def test_output_replay_if_present(self):
        fp=p.OUTPUT/'p2al-audit.json'
        if fp.exists():
            self.assertEqual(json.loads(fp.read_text()),self.result)

if __name__=='__main__': unittest.main(verbosity=2)
