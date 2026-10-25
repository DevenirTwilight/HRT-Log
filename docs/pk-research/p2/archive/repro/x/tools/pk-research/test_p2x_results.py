import unittest,json,math
from pathlib import Path
import numpy as np
import p2v_continuous_fit as v
import p2x_ensemble as x
import p2x_robustness as r

DATA=x.ROOT/'docs/pk-research/p2/p2x-conditional-ensemble-results.json'
ROB=x.ROOT/'docs/pk-research/p2/p2x-error-assumption-audit.json'

@unittest.skipUnless(DATA.exists() and ROB.exists(),'Generate outputs before testing')
class TestProducedEvidence(unittest.TestCase):
  @classmethod
  def setUpClass(cls):
    cls.d=json.loads(DATA.read_text());cls.r=json.loads(ROB.read_text());cls.rows=cls.d['rows']
  def test_grid_size(self):self.assertEqual(len(self.rows),5*2*4)
  def test_source_count(self):self.assertEqual(len(self.d['input_studies']),3)
  def test_doll_never_used(self):self.assertIs(self.d['Doll_used'],False)
  def test_zero_blind(self):self.assertEqual(self.d['independent_unseen_complete_SL_PK'],0)
  def test_positive_rates(self):
    for a in self.rows:
      p=a['parameter'];self.assertGreater(p['k_fast_per_h'],p['k_elim_per_h']);self.assertGreater(p['k_slow_per_h'],0);self.assertTrue(0<=p['effective_slow_weight']<=1)
  def test_unit_normalization(self):
    for a in self.rows:
      self.assertAlmostEqual(float(v.shape([1],a['parameter'])[0]),1,places=7)
  def test_sorted_troughs(self):
    for a in self.rows:
      t=a['trough_index_per_1mg_q6_q12_q24'];self.assertGreater(t['6'],t['12']);self.assertGreater(t['12'],t['24']);self.assertGreater(t['24'],0)
  def test_price_baseline_exactly_fixed(self):
    for a in self.rows:self.assertEqual(a['price_fixed_baseline_pg_ml'],a['fit']['Price1997_figure1']['baseline'])
  def test_main_loss_best_reconciled(self):
    self.assertAlmostEqual(self.d['summary']['global_best_loss'],min(a['score'] for a in self.rows))
  def test_bounds_manual_auc_gap_positive(self):
    au=self.r['figure_author_interval_envelope'];self.assertEqual(au['auc_total_raw_range_pg_h_ml'],[1390.,1785.5]);self.assertAlmostEqual(au['gap_at_least_if_all_bands_and_b_nonnegative_pg_h_ml'],323.5)
  def test_trough_sensitivity_not_small(self):
    b=self.d['summary']['slices']['additive_pseudoloss_0.1'];self.assertGreater(b['q24_fold_max_min'],50);self.assertGreater(len(b['source_baseline_grid_values']),1)
  def test_weak_slice_only_price_24(self):
    b=self.d['summary']['slices']['additive_pseudoloss_0.03'];self.assertEqual(b['source_baseline_grid_values'],[24.0])
  def test_compare_error_scenarios(self):
    self.assertEqual(len(self.r['alternative_assumptions']),12)
  def test_no_mutating_production(self):self.assertTrue(self.d['production_model_untouched'])

if __name__=='__main__':unittest.main()
