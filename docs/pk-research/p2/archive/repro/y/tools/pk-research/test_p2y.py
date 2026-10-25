import unittest,math,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
import p2v_continuous_fit as v
import p2y_hierarchical_moment_audit as hm
import p2y_dose_clock_sensitivity as clock

class TestP2Y(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  d=json.loads(hm.IN.read_text());cls.rows=d['rows']; cls.best=min(r['score'] for r in cls.rows)
  cls.near=[r for r in cls.rows if r['score']<=cls.best+.1+1e-10]
 def test_mean_increment(self):self.assertEqual(hm.YO['post_90_mean_pmol_L']-hm.YO['pre_mean_pmol_L'],1789.5)
 def test_ratio_threshold(self):self.assertAlmostEqual(204.5/1789.5,0.11427773,delta=0.00001)
 def test_no_two_point_identifiability_at_same_baseline(self):self.assertIsNone(hm.terminal_half_life(25.,24.,24.))
 def test_nonzero_terminal_baseline_effect(self):self.assertLess(hm.terminal_half_life(25,24,20),hm.terminal_half_life(25,24,0))
 def test_terminal_at_b20(self):self.assertAlmostEqual(hm.terminal_half_life(25,24,20),18.637702317,places=5)
 def test_price_background_high_is_invalid(self):self.assertIsNone(hm.terminal_half_life(25,24,26))
 def test_original_shape_q6_equals_clock_q6(self):
  for row in self.near[::4]:
   p=row['parameter'];a,b=hm.sums(p);c,d=clock.repeated_schedule_shape(p,(0,6,12,18))
   self.assertAlmostEqual(a,c,places=8);self.assertAlmostEqual(b,d,places=8)
 def test_schedule_validation(self):
  with self.assertRaises(ValueError):clock.repeated_schedule_shape(self.near[0]['parameter'],(0,18,12,6))
 def test_no_future_dose_leakage(self):
  p=self.near[0]['parameter'];a,b=clock.repeated_schedule_shape(p,(0,4,8,12),post_h=0)
  self.assertAlmostEqual(a,b,places=10)
 def test_schedule_longer_overnight_lowers_trough(self):
  for row in self.near[::4]:
   p=row['parameter'];q6=clock.repeated_schedule_shape(p,(0,6,12,18))[0];q12=clock.repeated_schedule_shape(p,(0,4,8,12))[0]
   self.assertLess(q12,q6)
 def test_q6_rejects_all_near(self):
  self.assertEqual(sum(clock.match_published_means(r['parameter'],(0,6,12,18))['feasible'] for r in self.near),0)
 def test_q12_accepts_some_not_all(self):
  n=sum(clock.match_published_means(r['parameter'],(0,4,8,12))['feasible'] for r in self.near)
  self.assertEqual(n,11)
 def test_no_doll_in_inputs(self):
  d=json.loads(hm.IN.read_text());self.assertFalse(d['Doll_used']);self.assertEqual(len(d['rows']),40)
 def test_basal_gain_identity(self):
  row=self.near[0];p=row['parameter']; fit=clock.match_published_means(p,(0,4,8,12));a,b=clock.repeated_schedule_shape(p,(0,4,8,12));G=fit['effective_gain_pmol_per_mg']*.5
  self.assertAlmostEqual(fit['background_required']+G*a,204.5,places=4)
  self.assertAlmostEqual(fit['background_required']+G*b,1994.,places=4)
 def test_fail_closed_negative_concentration(self):
  with self.assertRaises(ValueError):clock.match_published_means(self.near[0]['parameter'],(0,4,8,12),pre=-1)
 def test_repeated_days_stability(self):
  p=self.near[0]['parameter'];a,b=clock.repeated_schedule_shape(p,(0,4,8,12),days=180);c,d=clock.repeated_schedule_shape(p,(0,4,8,12),days=300)
  self.assertAlmostEqual(a,c,places=8);self.assertAlmostEqual(b,d,places=8)
if __name__=='__main__':unittest.main()
