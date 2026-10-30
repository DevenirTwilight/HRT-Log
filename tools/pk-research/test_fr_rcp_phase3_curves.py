"""Eight deterministic checks of research-only phase-3 PK shape algorithms."""
import importlib.util
import math
import pathlib
import unittest
SPEC=importlib.util.spec_from_file_location('phase3_curves',pathlib.Path(__file__).with_name('fr_rcp_phase3_curves.py'))
M=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)

class CurvesTest(unittest.TestCase):
  def test_analytic_peak_wiemeyer(self):
    self.assertAlmostEqual(M.peak_time(1.505/24,.12445/24)/24,1.8055442746509542,places=10)
  def test_tc_fda_rate_solution(self):
    k=M.rise_for_peak(71.7,192)
    self.assertAlmostEqual(k,.035483523290851776,places=10)
    self.assertAlmostEqual(M.shape(71.7,k,math.log(2)/192),1,places=10)
  def test_guard_invalid_rates(self):
    for rise,decay in [(0,.1),(.1,.1),(.02,.1)]:
      with self.assertRaises(ValueError):M.peak_time(rise,decay)
    with self.assertRaises(ValueError):M.rise_for_peak(50,10)
  def test_zero_and_late_decay(self):
    self.assertEqual(M.shape(0,.5,.01),0)
    self.assertEqual(M.shape(-1,.5,.01),0)
    self.assertLess(M.shape(1000,.5,.01),1)
  def test_all_model_families_and_validation(self):
    D=M.build();self.assertEqual(len(D['scenarios']),37);self.assertEqual(len(D['unsupported']),8)
    self.assertEqual(len({p['id'] for p in D['scenarios']}),15)
    self.assertEqual(len([p for p in D['scenarios'] if p['mode']=='baseline_corrected_absolute']),1)
    for p in D['scenarios']:
      k=p['effective_rise_per_h'];l=p['effective_tail_per_h']
      self.assertLess(l,k)
      self.assertAlmostEqual(M.peak_time(k,l),p['assumed_tpeak_h'],places=7)
      self.assertEqual(len(p['points']),241)
      self.assertTrue(all(0<=v['relative']<=1.0000001 for v in p['points']))
      if p['id']=='E2_EU_IM':self.assertIn('HYPOTHETICAL',p['evidence'])
  def test_source_guard_estradiol_sl_ev(self):
    D=M.build()
    self.assertTrue(any(p['id']=='E2_EV_SUBLINGUAL' for p in D['unsupported']))
  def test_fda_tc_is_dose_specific(self):
    p=next(p for p in M.build()['scenarios'] if p['id']=='T_TC_IM')
    self.assertEqual(p['mode'],'baseline_corrected_absolute')
    self.assertIn('absolute_200mg_TC_ng_dL',p)
    self.assertEqual(max(p['absolute_200mg_TC_ng_dL'])>755,True)
  def test_en_published_rates_preserved(self):
    p=next(p for p in M.build()['scenarios'] if p['id']=='E2_EN_IM')
    self.assertAlmostEqual(p['effective_rise_per_h'],1.505/24,places=10)
    self.assertAlmostEqual(p['effective_tail_per_h'],.12445/24,places=10)

if __name__=='__main__':unittest.main()
