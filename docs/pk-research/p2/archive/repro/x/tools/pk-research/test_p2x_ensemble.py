import json,unittest, math
from pathlib import Path
import numpy as np
import p2x_ensemble as x
import p2v_continuous_fit as v

class TestP2X(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=json.loads(x.DATA.read_text());cls.s=v.make_sources(cls.data)
    def test_necessary_sources(self):self.assertEqual(len(self.s),3)
    def test_nonnegative_price_fit(self):
        q=x.conditional_profile(np.array([1.,.5,.25]),{'obs':np.array([500.,300.,200.]),'precision':np.eye(3)},10.)
        self.assertEqual(q['baseline'],10.);self.assertGreaterEqual(q['amplitude_at_1h'],0)
    def test_forced_baseline_0(self):
        q=x.conditional_profile(np.array([1.,.5]),{'obs':np.array([100.,60.]),'precision':np.eye(2)},0.)
        self.assertEqual(q['baseline'],0)
    def test_bad_baseline(self):
        with self.assertRaises(ValueError):x.conditional_profile(np.ones(2),{},25.)
    def test_h1_is_one(self):
        p={'n_fast':4,'k_fast_per_h':8.,'k_elim_per_h':.5,'k_slow_per_h':.15,'effective_slow_weight':.4}
        self.assertAlmostEqual(float(v.shape([1.],p)[0]),1)
    def test_trough_positive(self):
        p={'n_fast':4,'k_fast_per_h':8.,'k_elim_per_h':.5,'k_slow_per_h':.15,'effective_slow_weight':.4}
        z={str(t):float(np.sum(v.shape(t*np.arange(1,241,dtype=float),p))) for t in (6,12,24)}
        self.assertTrue(z['6']>z['12']>z['24']>0)
    def test_cortez_ratio_constant(self):
        self.assertAlmostEqual(x.cortez_required_gain_ratio({'12':1.,'24':.5}),56/69.5)
    def test_zero_tail_rejected(self):self.assertIsNone(x.cortez_required_gain_ratio({'12':0,'24':.5}))
    def test_fixed_price_score_finite(self):
        s=x.fit_one(self.s,4,0.)
        self.assertTrue(math.isfinite(s['score'])); self.assertEqual(s['fit']['Price1997_figure1']['baseline'],0.)
    def test_summary_extremes(self):
        z={'score':.05,'price_fixed_baseline_pg_ml':0,'n_fast':4,'trough_index_per_1mg_q6_q12_q24':{'6':.1,'12':.01,'24':.001},'normalized_increment_auc_0_24_h':2,'relative_1mg_peak_time_h':1,'conditional_gain_ratio_twice_over_once':.1}
        q=dict(z);q['score']=.5;q['trough_index_per_1mg_q6_q12_q24']={'6':.2,'12':.02,'24':.002}
        s=x.summaries([z,q]);self.assertEqual(s['slices']['additive_pseudoloss_0.1']['count'],1)
    def test_source_mean_sem_mismatch_documented(self):
        self.assertEqual(x.CORTEZ['once']['reported_plus_minus'],10.5)
    def test_baseline_grid_not_beyond_24(self):
        self.assertEqual(x.PRICE_BASELINES[-1],24.)
    def test_price_data_are_digitized_not_raw(self):
        self.assertIn('MANUAL_DIGITIZATION',self.data['studies']['Price1997_figure1']['published_dispersion'])

if __name__=='__main__':unittest.main()
