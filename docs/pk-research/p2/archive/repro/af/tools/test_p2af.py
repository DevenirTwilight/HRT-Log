"""Research-only mathematical and reproducibility tests: never individual PK validation."""
import json, unittest, math
from pathlib import Path
import numpy as np
from scipy.integrate import simpson
import p2af_joint_ke_ks as m
import p2af_grid_stress as g
from p2v_continuous_fit import bateman,erlang,shape

DATA=json.loads(m.DATA.read_text())
ROWS=m.rows_selected(DATA)
OUT=json.loads((m.ROOT/'data/p2af-joint-parameter-functions.json').read_text())
GRID=json.loads((m.ROOT/'data/p2af-grid-stress.json').read_text())

class FrozenInputTests(unittest.TestCase):
    def test_no_doll_used_in_source(self):self.assertIs(DATA['Doll_used'],False)
    def test_selected_exact15(self):self.assertEqual(len(ROWS),15)
    def test_input_source_still40(self):self.assertEqual(len(DATA['rows']),40)
    def test_15_ids_unique(self):self.assertEqual(len({r['id'] for r in ROWS}),15)
    def test_no_individual_claim(self):self.assertFalse(OUT['has_new_human_data']);self.assertTrue(OUT['not_human_CI_posterior_or_prediction_bands'])
    def test_early_and_late_synthetic_times_explicit(self):self.assertEqual(m.PROTOCOLS['to08_pre'],[0,1,2,4,8]);self.assertEqual(m.PROTOCOLS['to48_pre'][-1],48.)

class RateAndConvolutionTests(unittest.TestCase):
    def test_bateman_exact_absorption_elimination_swap_under_free_gain(self):
        # B(ks,ke)=(ks/ke)*B(ke,ks), a structural symmetry independent of measurement noise.
        for ks,ke in ((.05,1.25),(.8,.55),(.25,.25),(.02,2.)):
            t=np.array([0,.1,.25,1,5,12,24,72])
            np.testing.assert_allclose(bateman(t,ks,ke),ks/ke*bateman(t,ke,ks),rtol=2e-12,atol=1e-13)
    def test_two_inputs_unit_mass_integrals(self):
        for r in ROWS[::3]:
            p=r['params'];ke=p['k_elim_per_h'];ks=p['k_slow_per_h']
            t=np.linspace(0,360,7201)
            f=erlang(t,p['n_fast'],p['k_fast_per_h'],ke)
            slow=bateman(t,ks,ke)
            self.assertAlmostEqual(simpson(f,x=t),1/ke,delta=1e-4)
            self.assertAlmostEqual(simpson(slow,x=t),1/ke,delta=1e-4)
    def test_normalization_at1h(self):
        for r in ROWS:self.assertAlmostEqual(shape(np.array([1.]),r['params'])[0],1.,places=12)
    def test_true_2d_own_solution_is_zero(self):
        for r in ROWS[::4]:
            prof,meta=m.sample_grid(r,'to24_pre',3.,.125,central_free=True)
            self.assertLess(meta['min_distance'],1e-6)
            self.assertTrue((prof[:,3:]>=0).all())
    def test_free_ke_contains_fixed_ke_in_grid(self):
        r=ROWS[0];free,_=m.sample_grid(r,'to48_pre',3.,.125,central_free=True)
        fixed,_=m.sample_grid(r,'to48_pre',3.,.125,central_free=False)
        self.assertGreaterEqual(len(free),len(fixed))
        self.assertAlmostEqual(free[:,0].max(),max(free[:,0]))
    def test_invalid_elimination_rejected(self):
        with self.assertRaises(ValueError):erlang(np.array([1.]),8,12.,12.)
    def test_negative_time_rejected(self):
        with self.assertRaises(ValueError):bateman(np.array([-1.]),.25,1.)
    def test_raw_convolution_at_zero(self):
        for r in ROWS:
            p=r['params'];f,s=m.functions(np.array([0]),p['n_fast'],p['k_fast_per_h'],p['k_elim_per_h'],p['k_slow_per_h'])
            self.assertAlmostEqual(f[0],0.);self.assertAlmostEqual(s[0],0.)

class FunctionalEnvelopeTests(unittest.TestCase):
    def test_scenarios_have_fifteen_truths(self):
        for case,records in OUT['results'].items():
            self.assertEqual(len(records),15)
            self.assertEqual({r['id'] for r in records},{r['id'] for r in ROWS})
    def test_all_shapes_positive_and_auc_ordered(self):
        for rows in OUT['results'].values():
            for z in rows:
                v=z['metric_envelopes']
                for key,stat in v.items():
                    self.assertTrue(math.isfinite(stat['min']) and math.isfinite(stat['max']))
                    self.assertLessEqual(stat['min'],stat['median']);self.assertLessEqual(stat['median'],stat['max'])
                self.assertGreaterEqual(v['auc_0_inf_h']['max'],v['auc_0_24_h']['max']-1e-3)
                self.assertGreaterEqual(v['auc_0_24_h']['max'],v['auc_0_8_h']['max']-1e-3)
                self.assertGreaterEqual(v['tail_area_fraction_after24']['min'],-1e-5)
                self.assertLessEqual(v['tail_area_fraction_after24']['max'],1.)
    def test_q24_truncation_60_vs_120(self):
        r=ROWS[5];p=r['params'];ks=.025;ke=.5
        def q(n):
            t=24*np.arange(1,n+1)
            f,s=m.functions(t,p['n_fast'],p['k_fast_per_h'],ke,ks)
            f1,s1=m.functions(np.array([1.]),p['n_fast'],p['k_fast_per_h'],ke,ks)
            return np.sum(.4*f+.6*s)/(.4*f1[0]+.6*s1[0])
        self.assertAlmostEqual(q(60),q(120),delta=1e-12)
    def test_fast_early_area_less_sensitive_than_q24(self):
        for name in ('24h_nominal','48h_nominal','48h_high_precision'):
            agg=OUT['aggregate'][name]['metrics']
            self.assertLess(agg['auc_0_8_h']['median_internal_spread_ratio'],2.)
            self.assertGreater(agg['q24_predose_index']['median_internal_spread_ratio'],agg['auc_0_8_h']['median_internal_spread_ratio'])
    def test_freer_central_rate_expands_q24_feasible(self):
        a=OUT['results']['48h_nominal'];b=OUT['results']['48h_nominal_fixed_ke']
        for x,y in zip(a,b):
            self.assertEqual(x['id'],y['id'])
            self.assertLessEqual(x['metric_envelopes']['q24_predose_index']['min'],y['metric_envelopes']['q24_predose_index']['min']+1e-8)
            self.assertGreaterEqual(x['metric_envelopes']['q24_predose_index']['max'],y['metric_envelopes']['q24_predose_index']['max']-1e-8)
    def test_grid_refinement_is_nested(self):self.assertTrue(GRID['all_coarse_feasible_are_also_fine'])
    def test_fine_grid_does_not_shrink_envelope(self):
        for metric,value in GRID['results'].items():
            for entry in value['rows']:
                self.assertLessEqual(entry['fine_min'],entry['coarse_min']+1e-10)
                self.assertGreaterEqual(entry['fine_max'],entry['coarse_max']-1e-10)
    def test_grid_refinement_keeps_order_of_magnitude(self):
        q=GRID['results']['q24_predose_index'];a=GRID['results']['auc_0_8_h']
        self.assertGreater(q['refined_median_ratio'],10.)
        self.assertLess(a['refined_median_ratio'],2.)
    def test_positive_amplitudes_but_not_identified(self):
        self.assertTrue(all(z['gain_nonneg_verified'] for records in OUT['results'].values() for z in records))

if __name__=='__main__': unittest.main()
