"""Deterministic structural checks for P2-AD research-only source-to-results audit."""
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path
import numpy as np
from scipy.optimize import nnls

sys.path.insert(0,str(Path(__file__).resolve().parent))
import p2ad_baseline_tail_audit as m

class P2ADTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dataset=json.loads(m.DATA.read_text(encoding='utf-8'))
        cls.rows=m.select_rows(cls.dataset)
        cls.report=m.analyze(cls.dataset)

    def test_locked_exposed_sources_only(self):
        self.assertFalse(self.dataset['Doll_used'])
        self.assertEqual(len(self.dataset['rows']),40)
        self.assertEqual(self.report['source'],'P2-X frozen 40 conditional exposed-fit rows and P2-V identical mathematical shape')

    def test_fifteen_membership_and_ids(self):
        self.assertEqual([r['id'] for r in self.rows],['p2x-'+str(i) for i in [14,15,17,18,19,22,26,29,30,33,34,35,37,38,39]])
        self.assertEqual(self.report['price_baseline_counts'],{'0':1,'6':1,'12':2,'18':5,'24':6})

    def test_pseudoloss_rule(self):
        best=self.dataset['summary']['global_best_loss']
        for row in self.rows:self.assertLessEqual(row['pseudo_loss'],best+.1+1e-12)
        for i,row in enumerate(self.dataset['rows']):
            self.assertEqual(row['score']<=best+.1+1e-12, 'p2x-'+str(i) in self.report['candidate_ids'])

    def test_shape_normalization_and_baseline(self):
        total,incr,h=m.curves(self.rows,[0,1,2,24])
        np.testing.assert_allclose(h[:,0],0,atol=1e-12)
        np.testing.assert_allclose(h[:,1],1,atol=1e-12)
        np.testing.assert_allclose(total[:,0],[r['assumed_price_baseline_pgml'] for r in self.rows],atol=1e-11)
        np.testing.assert_allclose(total-incr, np.broadcast_to(np.array([r['assumed_price_baseline_pgml'] for r in self.rows])[:,None], total.shape),atol=1e-12)

    def test_no_cross_study_concentration_transfer(self):
        for r in self.rows:
            self.assertTrue(0<=r['assumed_price_baseline_pgml']<=24)
            self.assertGreater(r['price_effective_one_hour_amplitude_pgml'],400)
            self.assertLess(r['price_effective_one_hour_amplitude_pgml'],500)

    def test_24h_total_and_increment_confounded(self):
        t=self.report['time_ranges']['24']
        self.assertAlmostEqual(t['min_total_pgml'],19.03198308,places=6)
        self.assertAlmostEqual(t['max_total_pgml'],24.78715869,places=6)
        self.assertAlmostEqual(t['min_increment_pgml'],0.28841852,places=6)
        self.assertAlmostEqual(t['max_increment_pgml'],19.88712917,places=6)
        self.assertGreater(t['increment_max_min_ratio'],60)
        self.assertLess(t['spread_total_pgml'],6)

    def test_24_36_subtraction_cancels_baseline_exactly(self):
        a,b,h=m.curves(self.rows,[24,36])
        np.testing.assert_allclose(a[:,0]-a[:,1],b[:,0]-b[:,1],atol=1e-12)

    def test_24_36_late_absolute_delta_is_small_for_short_tail(self):
        L={r['id']:r for r in self.report['per_candidate_late_differences']}
        self.assertLess(L['p2x-19']['C24_minus_C36_pgml'],.3)
        self.assertGreater(L['p2x-22']['C24_minus_C36_pgml'],9.0)
        self.assertAlmostEqual(L['p2x-19']['prob_C24_greater_than_C36_in_noise_scenario'],.52581760,places=6)
        self.assertAlmostEqual(L['p2x-22']['prob_C24_greater_than_C36_in_noise_scenario'],.98483875,places=6)

    def test_assay_sensitivity_is_not_noise_sigma(self):
        self.assertNotEqual(m.ASSAY_SENSITIVITY_PGML,m.ASSUMED_ABSOLUTE_SD_FLOOR_PGML)
        self.assertEqual(self.report['hypothesis_measurement_error']['absolute_sd_floor_pgml'],3)
        self.assertEqual(self.report['hypothesis_measurement_error']['sensitivity_limit_pgml_1997_RIA'],8)

    def test_conservative_refit_residual_minimizes_at_truth(self):
        cs,inc,h=m.curves(self.rows,[0,1,2,4,8,24,48])
        for i in (0,5,14):
            sigma=m.assumed_sigma(cs[i])
            score,pars=m.directed_profiled_distance(cs[i],h[i],sigma)
            self.assertLess(score,1e-10)
            self.assertAlmostEqual(pars[0],self.rows[i]['assumed_price_baseline_pgml'],places=6)
            self.assertAlmostEqual(pars[1],self.rows[i]['price_effective_one_hour_amplitude_pgml'],places=6)

    def test_competitor_scale_and_background_reprofile_is_essential(self):
        ts=[0,1,2,4,8,24]; y,inc,h=m.curves(self.rows,ts)
        idx=14
        sigma=m.assumed_sigma(y[idx]); competitor=5
        naive=float(np.linalg.norm((y[idx]-y[competitor])/sigma))
        profiled,_=m.directed_profiled_distance(y[idx],h[competitor],sigma)
        self.assertLessEqual(profiled,naive+1e-10)

    def test_number_of_hypothetical_model_pairs(self):
        for v in self.report['hypothetical_designs'].values():
            self.assertEqual(v['n_candidate_pairs'],105)
            self.assertEqual(v['n_same_price_baseline_pairs']+v['n_different_price_baseline_pairs'],105)
            self.assertEqual(len(v['pair_details']),105)

    def test_additional_measurement_increases_least_squares_information(self):
        # Same response model, weights, and nested protocol: profile distance cannot decrease.
        a=self.report['hypothetical_designs']['to_24_no_baseline']['pair_details']
        b=self.report['hypothetical_designs']['to_36_no_baseline']['pair_details']
        for old,new in zip(a,b):self.assertGreaterEqual(new['conservative_distance']+1e-7,old['conservative_distance'])

    def test_baseline_observation_changes_some_distinguishability(self):
        a=self.report['hypothetical_designs']['to_24_no_baseline']['pairs_distance_ge_2']
        b=self.report['hypothetical_designs']['to_24_with_baseline']['pairs_distance_ge_2']
        self.assertEqual(a,7);self.assertEqual(b,43)

    def test_no_claims_of_real_power_probabilities(self):
        self.assertFalse(self.report['individual_PK_validated'])
        self.assertFalse(self.report['production_engine_modified'])
        self.assertIn('NOT population probabilities',self.report['major_caveat'])

    def test_invalid_shapes_rejected(self):
        for v in ([-1],[math.nan],[math.inf]):
            with self.assertRaises(ValueError):m.curves(self.rows,v)
        with self.assertRaises(ValueError):m.assumed_sigma(np.array([1.]),floor=0)

    def test_deterministic_recalculation(self):
        a=m.analyze(self.dataset)
        b=self.report
        self.assertEqual(json.dumps(a,sort_keys=True),json.dumps(b,sort_keys=True))

    def test_atomic_output_refuses_overwrite(self):
        import subprocess
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'out.json';p.write_text('SENTINEL')
            result=subprocess.run([sys.executable,str(Path(m.__file__)),'--out',str(p)],capture_output=True,text=True,env={**__import__('os').environ,'OPENBLAS_NUM_THREADS':'1'})
            self.assertNotEqual(result.returncode,0)
            self.assertIn('FileExistsError',result.stderr)
            self.assertEqual(p.read_text(),'SENTINEL')

if __name__=='__main__':unittest.main()
