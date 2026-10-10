import sys, math, unittest, json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import legacy_audit as a

class LegacyAuditTests(unittest.TestCase):
    def test_transcription_is_official_v01(self): self.assertEqual(a.PARAM['parameter_version'],'0.1.0-literature-draft')
    def test_sha_kotlin_engine_identity(self): self.assertEqual(a.PARAM['source_files'][1]['git_blob_sha1'],'460e5d89464837738cbd92bf804b876400610bea')
    def test_source_is_fixed_ref(self): self.assertEqual(a.PARAM['base_sha'],'f69d892d47ca64250db7b35eb95c5e1d0f8a01d8')
    def test_1h_exact(self): self.assertAlmostEqual(a.default_sl(1),144.02380642618826,places=7)
    def test_24h_exact(self): self.assertAlmostEqual(a.default_sl(24),.03305841648477258,places=8)
    def test_auc0_8_exact(self): self.assertAlmostEqual(a.auc(0,8),390.6484170483408,places=7)
    def test_zero(self): self.assertEqual(a.default_sl(0),0)
    def test_negative(self): self.assertEqual(a.default_sl(-1),0)
    def test_repeated_dose_linearity(self): self.assertAlmostEqual(a.default_sl(3,2),2*a.default_sl(3))
    def test_dose_0(self): self.assertEqual(a.default_sl(1,0),0)
    def test_reference_relative_1(self): self.assertAlmostEqual(a.rel(1),1)
    def test_tier_default(self): self.assertAlmostEqual(a.default_sl(1,tier=2),a.PARAM['E2_SL']['checks']['cmax_1mg'])
    def test_tier_monotonic(self): self.assertTrue(all(a.default_sl(1,tier=i)<a.default_sl(1,tier=i+1) for i in range(3)))
    def test_tier_limit(self): self.assertAlmostEqual(a.default_sl(1,tier=3)/a.default_sl(1,tier=2),1.0022481096375513)
    def test_auc_positive(self): self.assertTrue(a.auc(0,24)>a.auc(0,8)>0)
    def test_nonnegative_default_grid(self): self.assertTrue(all(a.default_sl(t)>=0 for t in [i/4 for i in range(200)]))
    def test_contribution_oral(self): self.assertAlmostEqual(a.default_sl(24),a.component(24,a.PARAM['E2_SL'])+a.PARAM['E2_SL']['swallowed_share']*a.component(24,a.PARAM['E2_ORAL']))
    def test_legacy_dose_24h_extrapolation(self): self.assertGreater(24,a.PARAM['E2_SL']['calibrated_hours'])
    def test_price_9_observations(self): self.assertEqual(len(a.PRICE['digitized_points']),9)
    def test_price_digitization_h1(self): self.assertAlmostEqual(a.PRICE['digitized_points'][0]['value_pg_ml'],451.72414)
    def test_price_trapezoid_from_frozen(self): self.assertAlmostEqual(a.PRICE['figure1_trapezoid_raw_0h_assumed_zero_pg_h_ml'],1550.5664)
    def test_price_observation_8h_index(self): self.assertEqual(a.PRICE['digitized_points'][5]['hour'],8)
    def test_price_8h_gap_b0(self): self.assertGreater(a.stats_price(0)['mean_ratio_obs_to_model_8h'],12)
    def test_price_8h_gap_b24(self): self.assertGreater(a.stats_price(24)['mean_ratio_obs_to_model_8h'],6)
    def test_price_model_2h_over(self): self.assertTrue(all(a.stats_price(b)['points'][1]['residual_pred_minus_observed']>0 for b in [0,6,12,18,24]))
    def test_price_model_8h_under(self): self.assertTrue(all(a.stats_price(b)['points'][5]['residual_pred_minus_observed']<0 for b in [0,6,12,18,24]))
    def test_price_rmse_stable_across_baselines(self): self.assertTrue(all(.11<a.stats_price(b)['rmse_t2_to24']<.13 for b in [0,6,12,18,24]))
    def test_price_baseline_at_18h_negative_observation_possible(self): self.assertLess(a.stats_price(24)['points'][7]['observed_relative'],0)
    def test_rosano_early_under_both(self): self.assertTrue(all(a.rosano(b)['data'][0]['legacy_relative']>a.rosano(b)['data'][0]['observed_relative'] for b in [0,100,225]))
    def test_rosano_last_1h(self): self.assertEqual(a.rosano(0)['data'][-1]['observed_relative'],1)
    def test_rosano_40min_close_b0(self): self.assertLess(abs(a.rosano(0)['data'][2]['legacy_relative']-a.rosano(0)['data'][2]['observed_relative']),.01)
    def test_kom_b0_from_first_sample(self): self.assertEqual(a.komesaroff()['published_time_0_15_30min_pmol_l'][0],89.4)
    def test_kom_15to30_overprediction(self): self.assertGreater(a.komesaroff()['predicted_over_observed_ratio'],3)
    def test_kom_15min_measured_sem_not_sd(self): self.assertIn('SEM',a.OBS['Komesaroff1998_n10']['published_dispersion'])
    def test_legacy_repeated_q6(self): self.assertGreater(a.repeated(6,6),a.default_sl(6))
    def test_legacy_repeated_q12(self): self.assertLess(a.repeated(12,12),a.repeated(6,6))
    def test_invalid_m2_rows_dont_increase(self): self.assertEqual(len([r for r in a.M2['rows'] if r['score']<=a.M2['summary']['global_best_loss']+.10+1e-10]),15)
    def test_m2_row_oracle(self):
        r=a.M2['summary']['best']
        self.assertAlmostEqual(a.m2_relative(8,r),r['normalized_curve']['8'],places=10)
        self.assertAlmostEqual(a.m2_relative(.25,r),r['normalized_curve']['0.25'],places=10)
    def test_m2_price_all_frozen_have_lower_in_sample_rmse(self):
        for b in (0,6,12,18,24):
            self.assertLess(a.m2_price_metrics(b)['rmse_max'],a.stats_price(b)['rmse_t2_to24'])
    def test_not_independent_clinical_claim(self):
        d=json.loads((a.ROOT/'results/p2as-legacy-audit.json').read_text())
        self.assertTrue(d['no_unseen_external_human_data'])
        self.assertTrue(d['not_clinical_validation'])
    def test_source_hash_figure(self): self.assertEqual(a.PRICE['source_crop_sha256'],'25ff6138da10a0a7a68d0de36b045c32528d715cf128f61cf3f9794a3aa02db0')
    def test_robustness_2h_8h(self):
        for b in [0,6,12,18,24]:
            f=a.stats_price(b)
            self.assertGreater(f['points'][1]['residual_pred_minus_observed'],.20)
            self.assertLess(f['points'][5]['residual_pred_minus_observed'],-.03)

    def test_price_visual_2h_outside_old(self):
        v=next(x for x in a.price_visual_extreme() if x['h']==2)
        self.assertTrue(v['out_of_manually_defined_envelope']);self.assertGreater(v['legacy_relative'],v['max_observed_relative'])
    def test_price_visual_8h_outside_old(self):
        v=next(x for x in a.price_visual_extreme() if x['h']==8)
        self.assertTrue(v['out_of_manually_defined_envelope']);self.assertLess(v['legacy_relative'],v['min_observed_relative'])
    def test_yaish_exposed_cohort_only(self): self.assertEqual(a.yaish_90min_sensitivity()['verified_subject_count'],11)
    def test_yaish_paired_deltas(self):
        d=a.yaish_90min_sensitivity()['observed_paired_median_delta_pmol_l']
        self.assertEqual(d['3_month'],753.5);self.assertEqual(d['6_month'],1473.)
    def test_yaish_hypothesis_counts(self): self.assertEqual(len(a.yaish_90min_sensitivity()['counterfactual_0p5mg_four_doses_day']),5)
    def test_yaish_q6_model_pmol(self): self.assertAlmostEqual(a.yaish_90min_sensitivity()['counterfactual_0p5mg_four_doses_day'][0]['legacy_delta_pmol_l'],232.757,delta=.1)
    def test_yaish_prior_history_missing(self): self.assertFalse(a.YAISH['actual_prior_dose_history_per_person'])

if __name__ == '__main__': unittest.main(verbosity=2)

