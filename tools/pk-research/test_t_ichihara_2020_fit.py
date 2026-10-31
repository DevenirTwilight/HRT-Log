"""Human mean sampling-point fit regression guards, research-only."""
import math
import unittest
import t_ichihara_2020_fit as study

class Ichihara2020StudyTest(unittest.TestCase):
    def test_source_identity_and_data(self):
        d=study.research()
        self.assertIn('10.1089/andro.2020.0002',d['source'])
        self.assertEqual(d['sampling_days'],[0,1,2,3,7,14,21,28])
        self.assertEqual(len(d['mean_total_testosterone_ng_ml']),8)
        self.assertEqual(d['fixed_group_mean_baseline_ng_ml'],.75)

    def test_heldout_disjoint(self):
        d=study.research()
        self.assertEqual(d['within_cohort_holdout_days'],[2,21])
        self.assertFalse(set(d['train_days']) & set(d['within_cohort_holdout_days']))

    def test_nonnegative_kernels(self):
        for fn in (study.first_order,study.two_stage):
            self.assertEqual(fn(0,.15),0.)
            self.assertEqual(fn(-1,.15),0.)
            for t in [1e-4,.1,1,2,7,14,28]:
                self.assertTrue(math.isfinite(fn(t,.15)))
                self.assertGreaterEqual(fn(t,.15),0.)

    def test_two_input_fit_and_holdout(self):
        d=study.research()
        fit=d['two_input']
        self.assertLess(fit['train_rmse_ng_ml'],.07)
        self.assertGreater(fit['within_cohort_holdout_rmse_ng_ml'],2.0)
        self.assertLess(fit['within_cohort_holdout_rmse_ng_ml'],3.5)
        self.assertGreater(abs(fit['predictions_ng_ml'][2]-21.69),3.)
        self.assertFalse(d['external_validation'])

if __name__=='__main__':
    unittest.main()
