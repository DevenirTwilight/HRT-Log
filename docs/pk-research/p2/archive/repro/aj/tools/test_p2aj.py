import hashlib
import json
import math
import sys
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from p2aj_original_figure_audit import image_to_value,trapz,trapz_weights,analyze,SOURCE

SRC=json.loads(SOURCE.read_text(encoding='utf-8'))
RES=analyze(SRC)
PTS=RES['digitized_points']

class P2AJTests(unittest.TestCase):
 def test_source_pdf_digest_is_pinned(self):
  self.assertEqual(len(RES['source_pdf_sha256']),64)
 def test_crop_file_matches_declared_sha(self):
  f=ROOT/'data/Price_1997_Figure_1_原始扫描裁剪.png'
  self.assertEqual(hashlib.sha256(f.read_bytes()).hexdigest(),SRC['source_crop_sha256'])
 def test_exact_calibration_upper_500(self):
  self.assertEqual(image_to_value(49,'upper',SRC['axis_calibration']),500)
 def test_exact_calibration_upper_300(self):
  self.assertEqual(image_to_value(136,'upper',SRC['axis_calibration']),300)
 def test_exact_calibration_lower_180(self):
  self.assertEqual(image_to_value(207,'lower',SRC['axis_calibration']),180)
 def test_exact_calibration_lower_zero(self):
  self.assertEqual(image_to_value(841,'lower',SRC['axis_calibration']),0)
 def test_forbid_fake_single_axis(self):
  with self.assertRaises(ValueError):image_to_value(180,'middle',SRC['axis_calibration'])
 def test_times_are_published_sampling_times(self):
  self.assertEqual(RES['time_grid_hours'],[0,1,2,3,4,6,8,12,18,24])
 def test_broken_axis_top_then_lower(self):
  self.assertEqual([p['panel'] for p in PTS],['upper','upper']+['lower']*7)
 def test_first_point_figure_matches_reported_peak(self):
  self.assertAlmostEqual(PTS[0]['value_pg_ml'],451.72,delta=.2)
  self.assertAlmostEqual(PTS[0]['value_pg_ml'],SRC['table1_1mg_sl']['cmax_mean_pg_ml'],delta=3)
 def test_second_point_approx_older_reading(self):
  self.assertAlmostEqual(PTS[1]['value_pg_ml'],225,delta=8)
 def test_last_point_approx_24(self):
  self.assertAlmostEqual(PTS[-1]['value_pg_ml'],24,delta=1)
 def test_AUC_old_is_1557_5(self):
  self.assertEqual(RES['figure1_previous_p2u_0h_zero_pg_h_ml'],1557.5)
 def test_AUC_new_near_1551(self):
  self.assertAlmostEqual(RES['figure1_trapezoid_raw_0h_assumed_zero_pg_h_ml'],1550.5664,delta=.0001)
 def test_fresh_read_does_not_reconcile_auc(self):
  self.assertGreater(RES['zero_baseline_deficit_pg_h_ml'],550)
 def test_generous_visual_stress_still_below_table(self):
  self.assertLess(RES['zero_baseline_stress_upper_auc_pg_h_ml'],2109)
 def test_no_negative_baseline_could_close_gap(self):
  self.assertTrue(all(x['target_still_exceeds_upper_by_pg_h_ml']>0 for x in RES['baseline_scenarios']))
 def test_baseline_slope_minus_23_5(self):
  a,b=RES['baseline_scenarios'][:2]
  self.assertAlmostEqual((a['baseline_subtracted_auc_pg_h_ml']-b['baseline_subtracted_auc_pg_h_ml'])/6,23.5,places=5)
 def test_weights_sum_time_horizon(self):
  self.assertEqual(sum(RES['trapezoid_weights_hours']),24)
 def test_weight_12h_is_5(self):
  self.assertEqual(RES['trapezoid_weights_hours'][7],5)
 def test_weight_18h_is_6(self):
  self.assertEqual(RES['trapezoid_weights_hours'][8],6)
 def test_error_windows_not_statistical_sd(self):
  self.assertIn('NOT statistical',SRC['bound_semantics'])
 def test_unobserved_0h_is_not_represented_as_measured(self):
  self.assertIn('NOT READ FROM FIGURE',SRC['baseline_predose'])
 def test_trapz_linearity(self):
  t=[0,1,2,4];y=[0,10,5,2]
  self.assertAlmostEqual(trapz(t,[2*v for v in y]),2*trapz(t,y))
 def test_trapz_rejects_nonincreasing_times(self):
  with self.assertRaises(ValueError):trapz([0,1,1],[0,4,5])
 def test_trapz_rejects_mismatched_inputs(self):
  with self.assertRaises(ValueError):trapz([0,1],[0])
 def test_half_endpoint_weights(self):
  self.assertEqual(trapz_weights([0,1,2,4]),[.5,1,1.5,1])
 def test_stress_upper_is_weighted_sum_not_error_bars(self):
  total=sum(p['weighted_visual_window_pg_h_ml'] for p in PTS)
  self.assertAlmostEqual(total,239.0)
 def test_existing_source_and_figure_pins_are_different(self):
  self.assertNotEqual(RES['source_pdf_sha256'],RES['source_crop_sha256'])
 def test_true_independent_subject_validation_not_claimed(self):
  self.assertIn('no clinical personal concentration predictions',SRC['research_disposition'])

if __name__=='__main__':unittest.main()
