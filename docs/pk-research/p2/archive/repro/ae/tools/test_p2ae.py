#!/usr/bin/env python3
"""Standalone P2-AE consistency tests, entirely offline and synthetic."""
from __future__ import annotations
import json,math,unittest
from pathlib import Path
import numpy as np
from p2v_continuous_fit import bateman,erlang,shape
import p2ae_tail_gain_profile as m
import p2ae_cross_model_profiles as cross
import p2ae_drift_stress as drift

ROOT=Path(__file__).resolve().parents[1]

class P2AEScienceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dataset=json.loads(m.DATA.read_text());cls.rows=m.select(cls.dataset)
        cls.ident=json.loads((ROOT/'data'/'p2ae-identifiability.json').read_text())
        cls.cross=json.loads((ROOT/'data'/'p2ae-cross-candidate.json').read_text())
        cls.sensitivity=json.loads((ROOT/'data'/'p2ae-precision-sensitivity.json').read_text())
        cls.drift=json.loads((ROOT/'data'/'p2ae-drift-sensitivity.json').read_text())

    def test_01_freeze_doll_not_used(self):self.assertIs(self.dataset['Doll_used'],False)
    def test_02_original_40_fits(self):self.assertEqual(len(self.dataset['rows']),40)
    def test_03_source_pseudoloss_candidates(self):
        self.assertEqual(len(self.rows),15)
        self.assertEqual([r['id'] for r in self.rows],self.ident['selected_models'] and [r['id'] for r in self.ident['selected_models']])
    def test_04_mixing_identity(self):
        r=self.rows[0];t=np.array([0.,.1,1.,2.,8.,24.]);p=r['parameter'];w=p['effective_slow_weight'];ks=p['k_slow_per_h'];f,s=m.components(t,p,ks);f1,s1=m.components([1.],p,ks)
        calculated=((1-w)*f+w*s)/((1-w)*f1[0]+w*s1[0]);np.testing.assert_allclose(calculated,shape(t,p),rtol=1e-11,atol=1e-11)
    def test_05_normalize_one_hour(self):
        for r in self.rows:self.assertAlmostEqual(float(shape([1],r['parameter'])[0]),1.,places=12)
    def test_06_group_background_at_zero(self):
        for r in self.rows:self.assertAlmostEqual(float(m.anchor_total([0.],r)[0]),r['b'],places=10)
    def test_07_positive_profile_gains(self):
        r=self.rows[4];t=np.array(m.PROTOCOLS['through24_with_pre']);y=m.anchor_total(t,r);sd=m.sigma(y)
        cost,params=m.profile_one(t,y,sd,r['parameter'],r['parameter']['k_slow_per_h'],'free_fast_slow_gains')
        self.assertLess(cost,1e-8);self.assertTrue(np.all(params>=0.))
    def test_08_negative_observation_inputs_guard(self):
        r=self.rows[0]
        with self.assertRaises((ValueError,AssertionError)):m.components([-1],r['parameter'],.1)
    def test_09_rate_grid_positive(self):self.assertGreater(m.RATE_LOW,0);self.assertGreater(m.RATE_HIGH,m.RATE_LOW)
    def test_10_profile_truth_exact(self):
        for rr in self.ident['main']['through48_with_pre__free_fast_slow_gains']['per_candidate']:
            self.assertLess(rr['true_distance'],1e-7)
    def test_11_profile_all_contain_true_rate(self):
        for key,group in self.ident['main'].items():
            for rr in group['per_candidate']:
                self.assertLessEqual(rr['min_feasible_ks'],rr['true_ks_per_h']+1e-11)
                self.assertGreaterEqual(rr['max_feasible_ks'],rr['true_ks_per_h']-1e-11)
    def test_12_known_baseline_optimistic(self):
        for proto in m.PROTOCOLS:
            for fixed,known in zip(self.ident['main'][f'{proto}__free_fast_slow_gains']['per_candidate'],self.ident['main'][f'{proto}__known_background_free_gains']['per_candidate']):
                self.assertLessEqual(known['feasible_ratio'],fixed['feasible_ratio']+1e-8)
    def test_13_free_weight_widens_or_equal(self):
        for proto in m.PROTOCOLS:
            fixed=self.ident['main'][f'{proto}__fixed_slow_weight']['per_candidate'];free=self.ident['main'][f'{proto}__free_fast_slow_gains']['per_candidate']
            for a,b in zip(fixed,free):
                self.assertLessEqual(b['min_feasible_ks'],a['min_feasible_ks']+1e-8)
                self.assertGreaterEqual(b['max_feasible_ks'],a['max_feasible_ks']-1e-8)
    def test_14_free_shape_nested_distances(self):
        for study in self.cross['results'].values():
            for r in study['details']:
                self.assertLessEqual(r['free_gains_and_slow_rate']['conservative_distance'],r['free_fast_slow_gains_fixed_ks']['conservative_distance']+1e-7)
                self.assertLessEqual(r['free_fast_slow_gains_fixed_ks']['conservative_distance'],r['frozen_entire_shape']['conservative_distance']+1e-7)
    def test_15_pairwise_n_105(self):
        self.assertEqual(len(self.cross['results']['through48_with_pre']['details']),105)
    def test_16_original_frozen_distance_43(self):
        self.assertEqual(self.cross['results']['through24_with_pre']['summary']['frozen_entire_shape']['pairs_distance_ge_2'],43)
    def test_17_original_frozen_distance_46(self):
        self.assertEqual(self.cross['results']['through48_with_pre']['summary']['frozen_entire_shape']['pairs_distance_ge_2'],46)
    def test_18_refit_rate_destroys_ge2(self):
        for proto in self.cross['results']:
            self.assertEqual(self.cross['results'][proto]['summary']['free_gains_and_slow_rate']['pairs_distance_ge_2'],0)
    def test_19_hypothetical_precision_sensitivity(self):
        keys=['sd_floor_3.0_fractional_cv_0.125','sd_floor_1.0_fractional_cv_0.05','sd_floor_1.0_fractional_cv_0.02','sd_floor_0.5_fractional_cv_0.01']
        values=[self.sensitivity['results'][k]['threshold2_count'] for k in keys]
        self.assertEqual(values,[0,33,76,94])
    def test_20_censored_measurements_some_lost(self):
        x=self.ident['sensitivity']['sdfloor_3__drop_True__through24_with_pre']['per_candidate']
        self.assertTrue(any(r['total_usable']<len(m.PROTOCOLS['through24_with_pre']) for r in x))
    def test_21_sd_declared_not_from_paper(self):
        self.assertEqual(self.ident['assumptions']['noise_sd_formula'],'max(sd_floor_pgml, .125 * synthetic group total E2)')
    def test_22_drift_zero_equivalent(self):
        r=self.rows[1];times=np.array(m.PROTOCOLS['through24_with_pre']);y=m.anchor_total(times,r);sd=m.sigma(y)
        a=drift.profile_rate(times,y,sd,r['parameter'],0.05,limit=0.)[0]
        b=m.profile_one(times,y,sd,r['parameter'],.05,'free_fast_slow_gains')[0]
        self.assertAlmostEqual(a,b,places=9)
    def test_23_drift_profile_never_worse(self):
        r=self.rows[1];times=np.array(m.PROTOCOLS['through24_with_pre']);y=m.anchor_total(times,r);sd=m.sigma(y)
        cost0=drift.profile_rate(times,y,sd,r['parameter'],.05,limit=0.)[0]
        cost1=drift.profile_rate(times,y,sd,r['parameter'],.05,limit=.1)[0]
        self.assertLessEqual(cost1,cost0+1e-9)
    def test_24_baseline_nonnegative_even_with_negative_drift(self):
        r=self.rows[1];t=np.array(m.PROTOCOLS['through48_with_pre']);y=m.anchor_total(t,r);sd=m.sigma(y);f,s=m.components(t,r['parameter'],.08)
        dist,b,u,v=drift.fit(t,y,sd,f,s,-.1)
        self.assertGreaterEqual(b-.1*t.max(),-1e-8);self.assertGreaterEqual(min(u,v),-1e-8)
    def test_25_drift_widens_profile(self):
        for proto in ('through24_with_pre','through48_with_pre'):
            zero=self.drift['results'][f'{proto}__drift_0.0']['per_candidate'];greater=self.drift['results'][f'{proto}__drift_0.1']['per_candidate']
            for x,y in zip(zero,greater):self.assertGreaterEqual(y['rate_ratio']+1e-8,x['rate_ratio'])
    def test_26_sampled_post24_is_extrapolation(self):
        self.assertTrue(self.ident['assumptions']['after24_36_48_72h_are_extrapolations_not_Price_observed'])
    def test_27_identity_contains_zero_background_members(self):
        self.assertEqual(sum(r['b']==0. for r in self.rows),1)
    def test_28_model_gate_no_claims(self):
        for r in (self.ident,self.cross,self.sensitivity,self.drift):
            self.assertFalse(r.get('patient_specific_validated',False))
    def test_29_sigma_floors(self):
        np.testing.assert_allclose(m.sigma(np.array([0.,8.,100.]),3.),[3.,3.,12.5])
    def test_30_distances_nonnegative(self):
        for proto in self.cross['results'].values():
            for p in proto['details']:
                self.assertGreaterEqual(p['free_gains_and_slow_rate']['conservative_distance'],-1e-9)

if __name__=='__main__':unittest.main()
