import json
import math
import unittest
import p2n_transit_convolution as p

class ConvolutionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg=json.loads(p.CONFIG.read_text())
        cls.input=[json.loads(f.read_text()) for f in (p.CONFIG,p.PRICE,p.ROSANO,p.KOM)]
        cls.out=p.report(*cls.input)
    def test_analytical_one_compartment_limit(self):
        self.assertAlmostEqual(p.erlang_input_response(1,1,1.8,.41),
                               p.first_order_response(1,1.8,.41),places=12)
    def test_identical_rates_n2_limit(self):
        t=1.3;k=.41;n=2
        x=math.exp(n*math.log(k*t)-math.lgamma(n+1)-k*t)
        self.assertAlmostEqual(p.erlang_input_response(t,n,k,k),x,places=12)
    def test_near_equal_positive_finite_and_continuous(self):
        a=p.erlang_input_response(1.5,3,.41,.41)
        b=p.erlang_input_response(1.5,3,.410001,.41)
        self.assertAlmostEqual(a,b,places=4)
        self.assertTrue(math.isfinite(b) and b>=0)
    def test_matematical_onset_bound(self):
        for n in (1,2,3,5):
            f=lambda t:p.erlang_input_response(t,n,.5,.41)
            self.assertLess(f(2/3)/f(1/3),2**n)
        g=lambda t:p.erlang_input_response(t,3,.5,.41)
        self.assertGreater(g(2/3)/g(1/3),4)
    def test_absorption_stage_not_concentration_gamma_stage(self):
        case=self.cfg["models"][2]
        self.assertEqual(case["n_fast"],5)
        self.assertGreater(p.normalized(2/3,case)/p.normalized(1/3,case),4)
    def test_normalized_at_one_hour_all_cases(self):
        for c in self.cfg["models"]:
            self.assertEqual(p.normalized(0,c),0.)
            self.assertAlmostEqual(p.normalized(1,c),1.,places=12)
    def test_fast_slow_examples_preserve_price_drop_and_early_rise(self):
        z={r["id"]:r for r in self.out["models"]}
        assert z["transit6_slow30pct"]["growth_20_to_40"]>4
        self.assertGreater(z["transit6_slow30pct"]["price_shape_4h_over_1h"],.15)
        self.assertLess(z["transit6_slow30pct"]["price_shape_4h_over_1h"],.20)
        self.assertGreater(z["transit5_fast_only"]["growth_20_to_40"],4)
    def test_discrete_grid_auc_and_q6_scenario(self):
        z={r["id"]:r for r in self.out["models"]}
        self.assertAlmostEqual(z["transit6_slow30pct"]["price_0_24h_sampled_AUC_per_1h_increment"],2.535,delta=.015)
        self.assertLess(z["transit6_slow30pct"]["repeat_0_5mg_q6h"]["predose_relative_to_1mg_at_1h"],.08)
        self.assertGreater(z["transit6_slow30pct"]["repeat_0_5mg_q6h"]["90min_after_relative_to_1mg_at_1h"],.39)
    def test_all_sources_exposed_no_doll_fitted(self):
        self.assertFalse(self.out["Doll_144_as_amplitude_anchor"])
        self.assertTrue(self.out["price_Figure1_vs_Table1_AUC_UNRESOLVED"])
        self.assertFalse(self.out["clinical_accuracy_established"])
        self.assertFalse(self.out["production_model_change_authorized"])
    def test_no_invalid_rates_or_unphysical_shares(self):
        with self.assertRaises(ValueError):p.erlang_input_response(2,4,-1,.5)
        with self.assertRaises(ValueError):p.erlang_input_response(-1,4,2,.5)
        with self.assertRaises(ValueError):p.poisson_upper_tail_at_least(0,2)
        with self.assertRaises(ValueError):p.unnormalized(1,dict(self.cfg["models"][0],slow_effective_weight=2))
if __name__=="__main__":unittest.main()
