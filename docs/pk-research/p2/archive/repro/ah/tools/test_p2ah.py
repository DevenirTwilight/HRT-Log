import json
import math
import unittest
from pathlib import Path
from p2ah_price_auc_audit import run, trapezoid, trapezoid_weights, baseline_corrected_auc, half_life_two_points
BASE=Path(__file__).resolve().parents[1]
class P2AHTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=json.loads((BASE/'data/p2u-observed-aggregates.json').read_text())
        cls.out=run(cls.raw)
    def test_baseline_method_from_stated_source(self):
        self.assertEqual(self.out['input']['method_confirmed'], 'predose corrected before PK computation; table AUC0-24; trapezoid; same 0,1,2,3,4,6,8,12,18,24h grid')
    def test_source_table_number(self): self.assertEqual(self.raw['price_author_table_auc_mean'],2109)
    def test_digitized_timing_complete(self): self.assertEqual(self.out['input']['grid_hours'],[0,1,2,3,4,6,8,12,18,24])
    def test_trapezoid_baseline_zero(self):self.assertEqual(self.out['scenarios'][0]['figure_baseline_corrected_auc_0_24_pg_h_ml'],1557.5)
    def test_trapezoid_baseline_24(self):self.assertEqual(self.out['scenarios'][-1]['figure_baseline_corrected_auc_0_24_pg_h_ml'],993.5)
    def test_raw_1_24(self):self.assertEqual(self.out['auc_digitized_raw_1_24_pg_h_ml'],1332.5)
    def test_raw_auc_changes_half_baseline(self):self.assertAlmostEqual(self.out['scenarios'][-1]['observed_figure_raw_auc_0_24_pg_h_ml'],1569.5)
    def test_corrected_auc_declines_by_24b(self):
        for r in self.out['scenarios']:
            b=r['assumed_predose_pg_ml']
            self.assertAlmostEqual(r['figure_baseline_corrected_auc_0_24_pg_h_ml'],1557.5-23.5*b)
            self.assertAlmostEqual(r['observed_figure_raw_auc_0_24_pg_h_ml'],1557.5+0.5*b)
    def test_all_scenario_gaps_positive(self):self.assertTrue(all(s['reported_minus_digitized_pg_h_ml']>0 for s in self.out['scenarios']))
    def test_read_width_max_still_gaps(self):self.assertTrue(all(s['reported_minus_upper_read_width']>0 for s in self.out['scenarios']))
    def test_width_envelope(self):self.assertAlmostEqual(self.out['maximum_simultaneous_auc_read_width_perturbation_pg_h_ml'],185.25)
    def test_implicit_zero_to_one(self):self.assertEqual(self.out['required_auc_0_1_to_match_table_if_raw_later_points_frozen_and_zero_baseline'],776.5)
    def test_actual_zero_one(self):self.assertEqual(self.out['actual_trapezoid_0_1_if_0h_zero_and_1h_450'],225)
    def test_weights(self):self.assertEqual(self.out['trapezoid_weights_hours'],[.5,1,1,1,1.5,2,3,5,6,3])
    def test_zero_baseline_late_app_half_life(self):self.assertAlmostEqual(self.out['baseline_sensitive_apparent_half_life'][0]['apparent_8_24_h'],17.6427014211831)
    def test_background_reduces_app_late_half_life(self):self.assertLess(self.out['baseline_sensitive_apparent_half_life'][-2]['apparent_8_24_h'],4)
    def test_background_equal_final_undefined(self):self.assertIsNone(self.out['baseline_sensitive_apparent_half_life'][-1]['apparent_8_24_h'])
    def test_mean_and_auc_commute(self):
        x=self.out['mean_auc_commutation_demonstration'];self.assertAlmostEqual(x['arithmetic_mean_of_individual_aucs'],x['auc_of_mean_curve'])
    def test_trapezoid_linear_in_input(self):
        x=[0,1,2,4];a=[10,20,30,40];b=[0,10,100,5]
        self.assertAlmostEqual(trapezoid(x,[2*a[i]+3*b[i] for i in range(4)]),2*trapezoid(x,a)+3*trapezoid(x,b))
    def test_negative_or_repeated_grid_rejected(self):
        with self.assertRaises(ValueError):trapezoid([0,1,1],[2,3,4])
    def test_nan_rejected(self):
        with self.assertRaises(ValueError):trapezoid([0,1],[2,math.nan])
    def test_inconsistent_baseline_rejected(self):
        with self.assertRaises(ValueError):baseline_corrected_auc([0,1],[0,450],24)
    def test_half_life_bad_time_rejected(self):
        with self.assertRaises(ValueError):half_life_two_points(24,8,50,20,0)
    def test_no_additional_patient_data(self):self.assertFalse(self.out['is_clinical_validation'])

if __name__=='__main__':unittest.main()
