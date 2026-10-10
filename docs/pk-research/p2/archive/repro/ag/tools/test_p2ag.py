#!/usr/bin/env python3
"""P2-AG safeguards: retrospective shape holdout, not clinical validation."""
import json, math, sys, unittest
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
import p2v_continuous_fit as v
import p2ag_cross_source as a
import p2ag_diagnostics as diag
DATA=json.loads((ROOT/'data/p2u-observed-aggregates.json').read_text())
RESULT=json.loads((ROOT/'data/p2ag-cross-source.json').read_text())
SUMMARY=json.loads((ROOT/'data/p2ag-compact-summary.json').read_text())

class InputAndLeakage(unittest.TestCase):
    def test_no_doll_and_sources_are_previously_exposed(self):
        self.assertEqual(set(DATA['studies']),{'Rosano1997_PK25','Komesaroff1998_n10','Price1997_figure1'})
        self.assertTrue(RESULT['previously_seen_sources']);self.assertFalse(RESULT['price_in_training'])
        self.assertFalse(DATA['Doll_observations_used'])
    def test_source_units_not_silent_conversion(self):
        self.assertEqual(DATA['studies']['Rosano1997_PK25']['unit'],'pmol/L')
        self.assertEqual(DATA['studies']['Price1997_figure1']['unit'],'pg/mL')
    def test_price_data_never_in_training_sources(self):
        for cap in a.CAPS:
            ss=a.early_sources(DATA,cap)
            self.assertEqual(set(ss),{'Rosano1997_PK25','Komesaroff1998_n10'})
    def test_training_observations_only_before_or_at_one_hour(self):
        for cap in a.CAPS:
            ss=a.early_sources(DATA,cap)
            self.assertEqual(sum(len(s['obs']) for s in ss.values()),7)
            self.assertTrue(all(max(s['time'])<=1 for s in ss.values()))
    def test_previously_used_models_have_more_unknowns_than_early_data(self):
        self.assertEqual(SUMMARY['training_n_timepoints'],7)
        self.assertEqual(SUMMARY['n_training_study_specific_nuisance']+SUMMARY['M2_n_shared_shape_parameters'],8)
    def test_all_price_times_after_one_hour_are_holdout(self):
        s=DATA['studies']['Price1997_figure1']['hours']
        self.assertEqual(s,[1,2,3,4,6,8,12,18,24]);self.assertEqual(len(s)-1,8)
    def test_bounded_model_family_and_predeclared_grids(self):
        self.assertEqual(a.GRID_N,(4,6,8,10));self.assertEqual(a.CAPS,(100.,225.))
        self.assertEqual(a.BASES,(0.,6.,12.,18.,24.))
    def test_modifying_heldout_data_cannot_affect_training_fits(self):
        modified=json.loads(json.dumps(DATA));modified['studies']['Price1997_figure1']['y']=[9999]*9
        ss0=a.early_sources(DATA,100);ss1=a.early_sources(modified,100)
        for key in ss0:
            np.testing.assert_array_equal(ss0[key]['obs'],ss1[key]['obs'])
            np.testing.assert_array_equal(ss0[key]['time'],ss1[key]['time'])
            np.testing.assert_array_equal(ss0[key]['precision'],ss1[key]['precision'])
        fit0=a.fit_all(DATA,100,'M2',orders=(4,))
        fit1=a.fit_all(modified,100,'M2',orders=(4,))
        self.assertEqual(fit0[0]['train_pseudoloss'],fit1[0]['train_pseudoloss'])
        self.assertEqual(fit0[0]['parameters'],fit1[0]['parameters'])

class ModelAndHoldout(unittest.TestCase):
    def test_recomputed_frozen_model_scores_match(self):
        for key,o in RESULT['families'].items():
            cap=o['rosano_background_cap_pmol_l'];ss=a.early_sources(DATA,cap)
            z=o['selected'];par=z['parameters'];family=z['family']; n=z['n']
            x=[math.log(par['k_elim_per_h']),math.log(par['k_fast_per_h']-par['k_elim_per_h'])]
            if family=='M2':x+=[math.log(par['k_slow_per_h']),par['effective_slow_weight']]
            score=float(v.score(np.array(x),family,n,ss))
            self.assertAlmostEqual(score,z['train_pseudoloss'],delta=1e-7)
    def test_every_1h_price_anchor_reproduced_by_construction(self):
        for o in RESULT['families'].values():
            for q in o['price_1h_calibrated_heldout_scores_by_baseline'].values():
                self.assertAlmostEqual(q['predicted_pg_ml'][0],450.,delta=1e-8)
                self.assertEqual(q['heldout_all']['n'],8)
    def test_fixed_holdout_2h_observation_for_all_conditions(self):
        for o in RESULT['families'].values():
            for q in o['price_1h_calibrated_heldout_scores_by_baseline'].values():
                self.assertEqual(q['observed_pg_ml'][1],225)
                self.assertAlmostEqual(q['residual_pg_ml'][1],q['predicted_pg_ml'][1]-225,delta=1e-10)
    def test_heldout_rmse_excludes_1h_anchor(self):
        for o in RESULT['families'].values():
            for q in o['price_1h_calibrated_heldout_scores_by_baseline'].values():
                e=np.asarray(q['residual_pg_ml'][1:]);m=q['heldout_all']
                self.assertAlmostEqual(m['rmse_pg_ml'],np.sqrt(np.mean(e*e)),delta=1e-8)
    def test_heldout_scaled_rmse_weights_are_hypothetical(self):
        for o in RESULT['families'].values():
            for q in o['price_1h_calibrated_heldout_scores_by_baseline'].values():
                y=np.asarray(q['observed_pg_ml'][1:]);read=np.asarray(q['manual_read_width_pg_ml'][1:]);e=np.asarray(q['residual_pg_ml'][1:])
                sigma=np.maximum(read,.15*y)
                self.assertAlmostEqual(float(np.sqrt(np.mean(np.square(e/sigma)))),q['heldout_all']['standardized_rmse'],delta=1e-9)
    def test_early_only_m2_fits_early_nearly_perfectly_but_worse_holdout_cap100(self):
        s=RESULT['families'];m1=s['M1_rosano_cap_100'];m2=s['M2_rosano_cap_100']
        self.assertLess(m2['selected']['train_pseudoloss'],1e-8)
        self.assertGreater(m1['selected']['train_pseudoloss'],0.7)
        self.assertGreater(m2['price_1h_calibrated_heldout_scores_by_baseline']['24']['heldout_all']['rmse_pg_ml'],
                           m1['price_1h_calibrated_heldout_scores_by_baseline']['24']['heldout_all']['rmse_pg_ml'])
    def test_m2_early_train_not_proof_of_later_tail(self):
        s=RESULT['families']['M2_rosano_cap_100']['selected']
        self.assertAlmostEqual(s['parameters']['k_elim_per_h'],1.6,delta=1e-5)
        self.assertLess(s['train_pseudoloss'],1e-8)
    def test_price_b24_anchor_interval_gap_is_conditional(self):
        x=SUMMARY['anchor_interval_checks']
        self.assertTrue(x['manual_range_disjoint'])
        self.assertLess(x['m2_predicted_price_t2_range_pg_ml'][-1],x['manual_price_t2_read_range'][0])
        self.assertTrue(x['manual_figure_ranges_are_not_statistical_confidence_bands'])
    def test_b24_not_empirical_personal_baseline(self):
        self.assertTrue(DATA['studies']['Price1997_figure1']['published_dispersion'].startswith('MANUAL_'))
        self.assertEqual(RESULT['source']['baseline_hypotheses'],[0,6,12,18,24])
    def test_leaky_reference_declared_in_sample(self):
        q=RESULT['reference_price_in_sample_leaky']
        self.assertIn('NOT a holdout',q['source'])
        self.assertTrue(SUMMARY['leaky_in_sample_reference']['not_external_validation'])
    def test_summary_recomputed_independently(self):
        self.assertEqual(diag.summarize(RESULT),SUMMARY)

if __name__=='__main__':unittest.main()
