import json, math, tempfile, unittest
from pathlib import Path
from p2ai_primary_source_consistency import FROZEN, ROOT, TABLE, run, trapz, trapezoid_weights, logmean

class PriceSourceConstraintsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.out=run()
        cls.b={s['baseline_pg_ml']:s for s in cls.out['baseline_scenarios']}
    def test_source_scope(self):
        self.assertFalse(self.out['personal_pk_validated'])
        self.assertFalse(self.out['trapezoid_audit']['journal_original_figure_pixels_available'])
        self.assertTrue(self.out['trapezoid_audit']['no_claim_of_independent_original_figure_digitization'])
        self.assertEqual(self.out['research_status'],'PROVENANCE_CONSTRAINTS_ONLY')
    def test_frozen_data_exact(self):
        p=json.loads(FROZEN.read_text())['studies']['Price1997_figure1']
        self.assertEqual(p['hours'],[1,2,3,4,6,8,12,18,24])
        self.assertEqual(p['y'],[450,225,116,85,56,45,34,25,24])
        self.assertEqual(p['unit'],'pg/mL')
    def test_weight_identity(self):
        t=[0,1,2,3,4,6,8,12,18,24]
        self.assertEqual(trapezoid_weights(t),[.5,1,1,1,1.5,2,3,5,6,3])
        self.assertEqual(sum(trapezoid_weights(t)),24)
    def test_auc_raw_point_area(self):
        self.assertAlmostEqual(self.b[0]['trapezoid_baseline_corrected_auc'],1557.5)
        self.assertAlmostEqual(trapz([0,1,2],[0,450,225]),562.5)
    def test_baseline_case_study(self):
        for b in [0,3,6,12,18,24]:self.assertAlmostEqual(self.b[b]['trapezoid_baseline_corrected_auc'],1557.5-23.5*b)
    def test_table_goal(self):
        self.assertEqual(TABLE[0]['auc0_24_pg_h_ml'],2109)
        self.assertEqual(TABLE[0]['auc_sd_pg_h_ml'],1031)
        self.assertEqual(TABLE[0]['cmax_pg_ml'],451)
    def test_zero_predose_not_observed(self):
        self.assertTrue(self.out['trapezoid_audit']['source_0h_predose_unknown'])
    def test_baseline_makes_gap_worse(self):
        gap=[s['table_minus_figure_auc'] for s in self.out['baseline_scenarios']]
        self.assertEqual(sorted(gap),gap)
    def test_baseline_24(self):
        self.assertAlmostEqual(self.b[24]['trapezoid_baseline_corrected_auc'],993.5)
        self.assertAlmostEqual(self.b[24]['table_minus_figure_auc'],1115.5)
    def test_read_width_definition_not_se(self):
        self.assertIn('analyst readings',self.out['source_data_warning'].lower())
        self.assertGreater(self.out['trapezoid_audit']['max_all_manual_point_width_auc'],0)
    def test_exact_read_width_auc(self):
        self.assertAlmostEqual(self.out['trapezoid_audit']['max_all_manual_point_width_auc'],185.25)
    def test_max_perturb_insufficient(self):
        for b,x in self.b.items():
            self.assertGreater(x['table_minus_figure_auc'],self.out['trapezoid_audit']['max_all_manual_point_width_auc'])
    def test_minimum_uniform_scale_factors(self):
        w=self.out['trapezoid_audit']['max_all_manual_point_width_auc']
        for b,x in self.b.items():
            self.assertAlmostEqual(x['min_max_manual_read_width_multipliers_simultaneously'] * w,x['table_minus_figure_auc'],places=5)
        self.assertAlmostEqual(self.b[0]['min_max_manual_read_width_multipliers_simultaneously'],2.97705803)
    def test_cauchy_schwarz_lower_bound(self):
        t=self.out['point_contributions']; denom=math.sqrt(sum((x['maximum_read_width_auc_effect'])**2 for x in t))
        self.assertAlmostEqual(self.b[0]['min_euclidean_norm_of_width_scaled_point_offsets']*denom,551.5,places=5)
    def test_individual_point_leverage(self):
        for h in [12,18,24]:
            x=self.b[0][f'need_one_point_increase_pg_ml_if_only_{h}h_misread']
            w=next(q['auc_weight_h'] for q in self.out['point_contributions'] if q['time_h']==h)
            self.assertAlmostEqual(x*w,551.5,places=5)
    def test_24h_point_not_enough_even_large(self):
        self.assertGreater(self.b[0]['need_one_point_increase_pg_ml_if_only_24h_misread'],180)
    def test_early_hypothetical_not_observed(self):
        self.assertAlmostEqual(self.b[0]['hypothetical_half_hour_unobserved_level_pg_ml'],1328)
        self.assertGreater(self.b[24]['hypothetical_half_hour_unobserved_level_pg_ml'],2400)
    def test_mean_trapezoid_commute(self):
        t=[0,1,4]; a=[2,10,3];b=[4,15,8]
        out=trapz(t,[(x+y)/2 for x,y in zip(a,b)])
        self.assertAlmostEqual(out,(trapz(t,a)+trapz(t,b))/2)
    def test_log_trapezoid_smaller_than_linear(self):
        for a,b in [(1,2),(10,100),(400,200),(40,20),(1e-7,1e6)]:
            self.assertLessEqual(logmean(a,b),(a+b)/2+1e-9)
    def test_logmean_equal_case(self):
        self.assertEqual(logmean(2,2),2)
    def test_logmean_domain(self):
        self.assertIsNone(logmean(0,2))
    def test_table_arms_complete(self):
        self.assertEqual(len(self.out['table1_reported_study_arms']),5)
        self.assertEqual({a['route'] for a in TABLE},{'SL','PO'})
    def test_table_mean_sl_auc_per_mg(self):
        self.assertEqual([x['auc_per_mg'] for x in self.out['table1_reported_study_arms'][:3]],[2109,1940,3300])
    def test_sl_ratio_1mg_half(self):
        x=self.out['table1_sl_dose_ratio_contrasts'][0]
        self.assertAlmostEqual(x['auc_mean_ratio'],2109/970,places=6)
        self.assertAlmostEqual(x['cmax_mean_ratio'],451/245,places=6)
    def test_sl_ratio_half_quarter(self):
        x=self.out['table1_sl_dose_ratio_contrasts'][1]
        self.assertAlmostEqual(x['auc_mean_ratio'],970/825,places=6)
        self.assertAlmostEqual(x['cmax_mean_ratio'],245/294,places=6)
        self.assertLess(x['auc_mean_ratio'],2)
    def test_group_sd_not_a_se_or_significance_test(self):
        self.assertEqual(TABLE[1]['auc_sd_pg_h_ml'],440)
        self.assertEqual(TABLE[2]['auc_sd_pg_h_ml'],401)
        self.assertIn('cannot establish',self.out['dose_conclusion'])
    def test_provenance_url(self):
        sources=self.out['study_source_citations']
        self.assertIn('academia.edu',sources['body_transcription_not_original_scan'])
        self.assertIn('commons.wikimedia.org',sources['secondary_2018_webplotdigitizer_replot_not_primary_or_independent_cohort'])
    def test_observed_8h_to24h_auc(self):
        vals=json.loads(FROZEN.read_text())['studies']['Price1997_figure1']
        self.assertAlmostEqual(trapz([8,12,18,24],[45,34,25,24]),482)
    def test_unchanged_data_against_old_p2ah(self):
        other=Path('/mnt/data/p2ah_stage/data/p2u-observed-aggregates.json')
        if other.exists():self.assertEqual(FROZEN.read_bytes(),other.read_bytes())
    def test_roundtrip_json(self):
        self.assertEqual(json.loads(json.dumps(self.out)),self.out)
    def test_model_replacement_not_approved(self):
        self.assertIn('Do not use Table1 AUC',self.out['blocking_conclusion'])

if __name__=='__main__':unittest.main()
