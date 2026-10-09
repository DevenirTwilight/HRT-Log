"""Research harness checks. No hypothesis about which app must be more accurate."""
import json
import math
import unittest
from pathlib import Path
import sublingual_compare as audit

class ArithmeticAndEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.p=json.loads((audit.ROOT/'pk-engine/src/main/resources/pk-params.json').read_text())
        cls.e=json.loads((audit.ROOT/'pk-engine/src/test/resources/sublingual-literature-validation.json').read_text())
        cls.m=cls.e['comparison_model']

    def test_population_reproduction_is_not_a_fixed_ui_readout(self):
        self.assertAlmostEqual(2*audit.current(46/60,self.p),278.86477960569,places=7)
        self.assertAlmostEqual(2*audit.feather(46/60,self.m),914.2623521590754,places=7)
        self.assertNotEqual(277,2*audit.current(46/60,self.p))

    def test_general_bateman_equal_rate_limit_and_mass_area(self):
        for t in [0,.01,1,10,100000]:
            self.assertAlmostEqual(audit.bateman(t,1,1),t*math.exp(-t),places=10)
            self.assertTrue(math.isfinite(audit.bateman(t,.32,.41)))
        self.assertAlmostEqual(audit.simpson(lambda t:audit.bateman(t,1.8,.41),upper=100,n=20000),1/.41,places=7)
        self.assertEqual(0,audit.bateman(-1,1.8,.41))

    def test_mg_pg_volume_and_weight_scaling(self):
        a=audit.feather(1,self.m,80)
        self.assertAlmostEqual(audit.feather(1,self.m,40),2*a)
        self.assertEqual(1e9,1000*1e6)

    def test_external_data_are_original_and_not_fitted_model_outputs(self):
        records={r['id']:r for r in self.e['records']}
        for key,value in [('pines_60min',1759),('yaish_90min',1994),('burnier_1h_fold',26)]:
            r=records[key];self.assertTrue(r['independent_validation']);self.assertFalse(r['used_in_fitting'])
            self.assertEqual(value,r['value']);self.assertTrue(r['source_location'])
        self.assertEqual('unverified',records['pines_60min']['spread_type'])
        self.assertEqual(1457,records['yaish_90min']['sd'])
        self.assertFalse(records['doll_sl_peak']['independent_validation'])
        # Existing empirical model substantially differs from Pines' independent fixed-time mean.
        # This asserts the audit honestly records disagreement; it does not assert clinical failure/success.
        self.assertGreater(records['pines_60min']['value']-4*audit.current(1,self.p),1000)

    def test_same_proxy_metrics_allow_very_different_structural_shares(self):
        profiles=[p for p in audit.surrogate_profiles(self.p) if 'swallowed_coefficient' in p]
        self.assertGreaterEqual(len(profiles),4)
        self.assertGreater(max(p['swallowed_coefficient'] for p in profiles)-min(p['swallowed_coefficient'] for p in profiles),.3)
        for p in profiles:
            self.assertAlmostEqual(p['c_at_1h'],144,places=7)
            self.assertAlmostEqual(p['derivative_at_1h'],0,places=5)
            self.assertAlmostEqual(p['auc0_8_proxy'],self.p['models']['E2_SL']['checks']['auc0_8'],places=7)

if __name__=='__main__':unittest.main()
