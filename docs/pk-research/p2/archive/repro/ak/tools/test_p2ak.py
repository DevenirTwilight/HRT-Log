import json, math, sys, tempfile, unittest
from pathlib import Path
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from p2ak_interval_audit import SOURCE,audit,trapezoid_weights,linear_weighted,auc_from_predose_baseline,interval_witness

class TestP2AK(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.s=json.loads(SOURCE.read_text());cls.r=audit(cls.s)
    def test_01_schema(self): self.assertEqual(self.s['schema_version'],1)
    def test_02_arms(self): self.assertEqual(set(self.s['dose_arms']),{'0.5mg_SL','0.25mg_SL'})
    def test_03_no_imputation_zero_measurement(self): self.assertNotIn(0,self.s['times_postdose_h'])
    def test_04_sorted_sampling(self): self.assertEqual(self.s['times_postdose_h'],[1,2,3,4,6,8,12,18,24])
    def test_05_non_equidistant_sampling(self): self.assertNotEqual(trapezoid_weights([0]+self.s['times_postdose_h'])[1],trapezoid_weights([0]+self.s['times_postdose_h'])[7])
    def test_06_weights_all(self): self.assertEqual(trapezoid_weights([0]+self.s['times_postdose_h']),[.5,1,1,1,1.5,2,3,5,6,3])
    def test_07_weights_total(self): self.assertEqual(sum(trapezoid_weights([0]+self.s['times_postdose_h'])),24)
    def test_08_postdose_weights_sum(self): self.assertEqual(sum(self.r['postdose_trapezoid_weights_h']),23.5)
    def test_09_constant_curve_trapezoid(self): self.assertEqual(linear_weighted(trapezoid_weights([0,1,3]),[1,1,1]),3)
    def test_10_reject_bad_grid(self):
        with self.assertRaises(AssertionError): trapezoid_weights([0,1,1,2])
    def test_11_reject_bad_length(self):
        with self.assertRaises(AssertionError): linear_weighted([1,2],[3])
    def test_12_reject_negative_baseline(self):
        with self.assertRaises(AssertionError): auc_from_predose_baseline(2,-1,23.5)
    def test_13_baseline_subtraction(self): self.assertEqual(auc_from_predose_baseline(100,4,23.5),6)
    def test_14_baseline_zero(self): self.assertEqual(auc_from_predose_baseline(100,0,23.5),100)
    def test_15_source_pdf_hash_is_nonempty(self): self.assertEqual(len(self.s['source_pdf_sha256']),64)
    def test_16_broken_axis(self): self.assertTrue(self.s['axis']['broken'])
    def test_17_05_center(self): self.assertEqual(self.r['arms']['0.5mg_SL']['visual_conditional_auc_zero_baseline']['center'],832)
    def test_18_025_center(self): self.assertEqual(self.r['arms']['0.25mg_SL']['visual_conditional_auc_zero_baseline']['center'],765.5)
    def test_19_05_low_high(self): self.assertEqual((self.r['arms']['0.5mg_SL']['visual_conditional_auc_zero_baseline']['low'],self.r['arms']['0.5mg_SL']['visual_conditional_auc_zero_baseline']['high']),(626.5,1155))
    def test_20_025_low_high(self): self.assertEqual((self.r['arms']['0.25mg_SL']['visual_conditional_auc_zero_baseline']['low'],self.r['arms']['0.25mg_SL']['visual_conditional_auc_zero_baseline']['high']),(488.5,1198.5))
    def test_21_05_table_inside_zero_baseline_envelope(self): self.assertTrue(self.r['arms']['0.5mg_SL']['scenario_results'][0]['table_auc_within_conditional_visual_envelope'])
    def test_22_025_table_inside_zero_baseline_envelope(self): self.assertTrue(self.r['arms']['0.25mg_SL']['scenario_results'][0]['table_auc_within_conditional_visual_envelope'])
    def test_23_05_baseline8_no_witness(self): self.assertFalse(self.r['arms']['0.5mg_SL']['scenario_results'][3]['table_auc_within_conditional_visual_envelope'])
    def test_24_025_baseline16_no_witness(self): self.assertFalse(self.r['arms']['0.25mg_SL']['scenario_results'][5]['table_auc_within_conditional_visual_envelope'])
    def test_25_critical_05(self): self.assertAlmostEqual(self.r['arms']['0.5mg_SL']['critical_nonnegative_baseline_upper_feasibility_pg_ml'],(1155-970)/23.5,places=5)
    def test_26_critical_025(self): self.assertAlmostEqual(self.r['arms']['0.25mg_SL']['critical_nonnegative_baseline_upper_feasibility_pg_ml'],(1198.5-825)/23.5,places=5)
    def test_27_witness_05(self):
        a=self.r['arms']['0.5mg_SL'];w=a['scenario_results'][0]['witness_curve_at_table_if_feasible'];self.assertAlmostEqual(linear_weighted(self.r['postdose_trapezoid_weights_h'],w),970,places=3)
    def test_28_witness_025(self):
        a=self.r['arms']['0.25mg_SL'];w=a['scenario_results'][0]['witness_curve_at_table_if_feasible'];self.assertAlmostEqual(linear_weighted(self.r['postdose_trapezoid_weights_h'],w),825,places=3)
    def test_29_impossible_target_returns_none(self): self.assertIsNone(interval_witness([1,2],[2,3],100,[1,1]))
    def test_30_visual_envelopes_are_nonnegative(self):
        for a in self.s['dose_arms'].values():
            self.assertTrue(all(0<=p['low']<=p['center']<=p['high'] for p in a['points']))
    def test_31_monotonic_readings(self):
        for a in self.s['dose_arms'].values():
            for k in ['low','center','high']:
                xs=[p[k] for p in a['points']]
                self.assertTrue(all(x>=y for x,y in zip(xs,xs[1:])),k)
    def test_32_1mg_reference_separately_traced(self): self.assertGreater(self.r['one_mg_reference']['table_above_subjective_1mg_upper_at_b0_pg_h_ml'],319)
    def test_33_no_claim_of_validated_bounds(self): self.assertIn('subjective',self.r['limitations'][0])
    def test_34_table_cmax_not_fitted_to_curve(self):
        for a in self.s['dose_arms'].values():self.assertNotEqual(a['table_cmax_mean_pg_ml'],a['points'][0]['center'])
    def test_35_study_timing_no_negative(self): self.assertGreaterEqual(self.s['times_postdose_h'][0],0)
    def test_36_criticality_transition(self):
        for a in self.r['arms'].values():
            seq=[row['table_auc_within_conditional_visual_envelope'] for row in a['scenario_results']]
            self.assertEqual(seq, sorted(seq,reverse=True))
    def test_37_new_observation_not_claimed(self): self.assertIn('not new empirical human data',self.s['dose_interpretation'])
    def test_38_1mg_reference_correct(self): self.assertEqual(self.r['one_mg_reference']['digitized_auc_zero_baseline_pg_h_ml'],1550.5664)
    def test_39_source_pdf_reused(self):
        import hashlib
        pdf=Path('/mnt/data/price_original/Single_Dose_Pharmacokinetics_of_Sublingu(1).pdf')
        if pdf.exists(): self.assertEqual(hashlib.sha256(pdf.read_bytes()).hexdigest(),self.s['source_pdf_sha256'])
    def test_40_fig_crop_reused(self):
        import hashlib
        crop=SOURCE.parent/'Price_1997_Figure_1_原始扫描裁剪.png'
        self.assertEqual(hashlib.sha256(crop.read_bytes()).hexdigest(),self.s['original_crop_sha256'])

if __name__=='__main__':unittest.main()
