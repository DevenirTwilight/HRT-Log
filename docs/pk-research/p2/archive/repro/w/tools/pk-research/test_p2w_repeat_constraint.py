import unittest,math,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import p2w_repeat_constraint as w

class RepeatEvidence(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.o=w.analyze()
    def test_no_doll(self): self.assertFalse(self.o['Doll_used'])
    def test_no_product_mutation(self): self.assertFalse(self.o['production_model_modified'])
    def test_no_population_validation_claim(self): self.assertTrue(self.o['not_a_population_PK_or_real_external_validation'])
    def test_distinct_mixed_stats(self): self.assertTrue(self.o['Yaish_mean_is_not_median'])
    def test_distinct_cortez_groups(self): self.assertTrue(self.o['Cortez_arms_are_separate_people'])
    def test_all_models_present(self): self.assertEqual(len(self.o['models']),5)
    def test_positive_indices(self):
        for m in self.o['models'].values():
            self.assertTrue(all(x>0 for x in m['dimensionless_trough_index_per_mg'].values()))
    def test_monotonic_trough_spacing(self):
        for m in self.o['models'].values():
            v=m['dimensionless_trough_index_per_mg'];self.assertGreater(v['6'],v['12']);self.assertGreater(v['12'],v['24'])
    def test_no_clinical_concentration_from_shape_alone(self):
        for m in self.o['models'].values():
            self.assertNotIn('validated_personal_concentration',m)
    def test_q6_ratio_below_one(self):
        for m in self.o['models'].values():
            self.assertGreater(m['dimensionless_post_90min_index_per_mg_q6'],m['dimensionless_trough_index_per_mg']['6'])
    def test_predose_monotonic_with_elapsed_time(self):
        for m in self.o['models'].values():
            for intvl in ('6','12','24'):
                s=m['timing_sensitivity_indices'][intvl]
                self.assertGreater(s['-2.0'],s['2.0'])
    def test_background_sensitivity_gain_decreases_as_background_grows(self):
        for m in self.o['models'].values():
            self.assertGreater(m['Cortez_required_gain_sensitivity']['0']['once']['required_1h_increment_per_mg_if_shared_shape'],m['Cortez_required_gain_sensitivity']['50pg']['once']['required_1h_increment_per_mg_if_shared_shape'])
    def test_study_observation_is_positive(self):
        m=next(iter(self.o['models'].values()))
        self.assertGreater(m['Cortez_group_mean_6mo_ratio_twice_over_once_raw'],0)
    def test_invalid_interval(self):
        p=next(iter(self.o['models'].values()))['parameters']
        with self.assertRaises(ValueError):w.accumulation(p,0)
    def test_negative_timing_horizon_rejected(self):
        p=next(iter(self.o['models'].values()))['parameters']
        with self.assertRaises(ValueError):w.accumulation(p,6,-6)
    def test_gain_bad_background(self):
        with self.assertRaises(ValueError):w.cohort_gain(50,60,.5,1.)
    def test_lognormal_gain_mean_over_median(self):
        self.assertAlmostEqual(w.lognormal_gain_moment_ratio(0.),1.)
        self.assertGreater(w.lognormal_gain_moment_ratio(.8),1.)
    def test_price_zero_sensitivity_distinct_from_main(self):
        m=self.o['models'];self.assertNotAlmostEqual(m['P2V_M2_extended_n9']['dimensionless_trough_index_per_mg']['24'],m['P2V_M2_Price_baseline0']['dimensionless_trough_index_per_mg']['24'],places=5)

if __name__=='__main__':unittest.main()
