import json,math,unittest
import p2m_cross_study_shape as p
class P2MTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
    cls.docs=[json.loads(q.read_text()) for q in p.PATHS]
    cls.output=p.audit(*cls.docs)
 def test_gamma_norm(self):
    for n in (2,3,4,8):
     self.assertAlmostEqual(p.gamma(1,n,5),1)
     self.assertEqual(p.gamma(0,n,5),0)
 def test_gamma_2x_bound(self):
    for n in (2,3,8):
     self.assertLess(p.gamma(2/3,n,1)/p.gamma(1/3,n,1),2**(n-1))
 def test_SD_vs_SEM(self):
    a,b,baseline=p.sources(self.docs[1],self.docs[2])
    self.assertAlmostEqual(a[0][2],56/5)
    self.assertEqual(b[0][2],218)
    self.assertEqual(baseline,89.4)
 def test_assumed_baseline_changes_shape(self):
    x=self.output["winning_shape_by_Rosano_assumed_baseline_cap"]
    self.assertEqual(x[0]["best"]["order"],3)
    self.assertEqual(x[-1]["best"]["order"],8)
    self.assertEqual(x[-1]["best"]["Rosano"]["assumed_baseline"],225)
 def test_cross_study_price_inconsistency(self):
    z=self.output
    self.assertLess(z["high_baseline_winner_gamma_2h_over_1h_ratio"],.05)
    self.assertGreater(z["Price_figure_2h_over_1h_hypothetical_baselines"][-1]["ratio"],.4)
 def test_early_mean_ratio_and_uncertainty(self):
    z=self.output
    self.assertGreater(z["Rosano_40_over_20_total_ratio"],4)
    s=z["Rosano_marginal_1SE_endpoint_ratio_sensitivity"]
    self.assertLess(s[0],4)
    self.assertGreater(s[1],4)
 def test_equal_amplitude_fitting_only_shape(self):
    z=self.output["frozen_model_shape_checks"]
    self.assertLess(z["HRT"]["growth_20_to_40"],2)
    self.assertLess(z["Featherline_80kg"]["growth_20_to_40"],2)
 def test_no_Doll_anchoring_or_clinical_accuracy(self):
    z=self.output
    self.assertEqual(z["new_locked_external"],0)
    self.assertFalse(z["clinical_accuracy_established"])
    self.assertFalse(z["production_change_authorized"])
 def test_zero_amplitude_fit(self):
    v=p.ampfit([(1,0,1),(2,0,1)],0,lambda t:t)
    self.assertEqual(v["amplitude"],0)
    self.assertEqual(v["marginal_SE_pseudoloss"],0)
 def test_invalids_rejected(self):
    with self.assertRaises(ValueError):p.gamma(.2,0,1)
    with self.assertRaises(ValueError):p.gamma(-1,4,1)
    with self.assertRaises(ValueError):p.ampfit([(1,1,0)],0,lambda t:1)
    with self.assertRaises(ValueError):p.ampfit([(1,1,1)],-1,lambda t:1)
if __name__=="__main__":unittest.main()
