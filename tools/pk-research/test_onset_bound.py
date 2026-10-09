import math
import unittest
import onset_bound as m
class EarlyRiseTest(unittest.TestCase):
    def test_reported_group_mean_ratio(self):
        ratio=(1969-89.4)/(486.6-89.4)
        self.assertAlmostEqual(ratio,4.732124874118831)
        self.assertGreater(ratio,2.0)
        self.assertAlmostEqual(60*(ratio*.25-.5)/(ratio-1),10.980841878035617)
    def test_positive_instantaneous_first_order_bound(self):
        for ka in [.01,.32,1,4,12,100]:
            for ke in [.01,.41,1,4,100]:
                self.assertGreater(m.q(.25,ka,ke),0)
                self.assertLessEqual(m.q(.5,ka,ke)/m.q(.25,ka,ke),2+1e-12)
                self.assertTrue(math.isfinite(m.q(.5,ka,ke)))
    def test_delay_is_mathematical_counterexample_not_estimate(self):
        def delayed(t):
            x=max(0,t-12.5/60)
            return x*math.exp(-x)
        self.assertGreater(delayed(.5)/delayed(.25),2)
    def test_canonical_models_without_refit(self):
        p={'models':{'E2_SL':{'ka_per_h':1.0005,'terms':[{'A_per_mg':391432.56698842294,'lambda_per_h':.9995}],'swallowed_share':.0024079912862392887},
            'E2_ORAL':{'ka_per_h':.31109807764375685,'terms':[{'A_per_mg':45.13254431413082,'lambda_per_h':.049510512897138946}]}}}
        s={'candidate_A':{'canonical_k':1},'candidate_B':{'canonical':[4,.32,.41,.1]}}
        meta={'mucosal_fraction':.11,'fast_bioavailability':1,'oral_bioavailability':.03,'volume_l_per_kg':2,
          'k_fast_per_h':1.8,'k_oral_per_h':.32,'k_elimination_per_h':.41}
        e={'id':'Komesaroff1998_Table1_E2_2mg','observations':[
          {'minutes':0,'mean_pmol_l':89.4},{'minutes':15,'mean_pmol_l':486.6},{'minutes':30,'mean_pmol_l':1969}]}
        r=m.compare(e,p,s,meta)
        self.assertFalse(r['statistical_rejection_established'])
        self.assertAlmostEqual(r['conditional_model_predictions']['HRT_current']['increment_30_pg_ml'],237.44185229375506,8)
        self.assertAlmostEqual(r['conditional_model_predictions']['Candidate_B']['increment_15_pg_ml'],233.84530736487105,8)
        self.assertAlmostEqual(r['conditional_model_predictions']['Featherline_80kg']['increment_30_pg_ml'],771.108907810782,8)
if __name__=='__main__':unittest.main()
