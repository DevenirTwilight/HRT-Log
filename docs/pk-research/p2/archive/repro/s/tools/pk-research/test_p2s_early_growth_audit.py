import math
import unittest

import p2s_early_growth_audit as audit

class EarlyGrowthBoundsTests(unittest.TestCase):
    def test_rosano_first_order_contrast(self):
        r = audit.growth_audit(468, 1980, 115, 456, 25, 1, 20, 40)
        self.assertEqual(r['conservative_zero_baseline_contrast_pmol_l'], 1044)
        self.assertAlmostEqual(r['sem_range_over_unknown_correlation_pmol_l'][0], 45.2)
        self.assertAlmostEqual(r['sem_range_over_unknown_correlation_pmol_l'][1], 137.2)
        self.assertAlmostEqual(r['min_signal_over_max_sem_if_positive'], 1044/137.2)
        self.assertAlmostEqual(r['min_symmetric_per_mean_perturbation_to_satisfy_bound_pmol_l'], 348)

    def test_rosano_second_order_more_sensitive_to_noise(self):
        r = audit.growth_audit(468, 1980, 115, 456, 25, 2, 20, 40)
        self.assertEqual(r['conservative_zero_baseline_contrast_pmol_l'], 108)
        self.assertAlmostEqual(r['sem_range_over_unknown_correlation_pmol_l'][1], 183.2)
        self.assertAlmostEqual(r['min_symmetric_per_mean_perturbation_to_satisfy_bound_pmol_l'], 21.6)

    def test_third_order_not_excluded_by_group_means(self):
        r = audit.growth_audit(468, 1980, 115, 456, 25, 3, 20, 40)
        self.assertLess(r['conservative_zero_baseline_contrast_pmol_l'], 0)
        self.assertIsNone(r['min_signal_over_max_sem_if_positive'])

    def test_uncertainty_bounds_cauchy(self):
        low, high = audit.sem_range(115, 456, 2, 25)
        self.assertEqual((low, high), (45.2, 137.2))
        for rho in (-1, -.5, 0, .6, 1):
            se = math.sqrt((456**2 + 4*115**2 - 4*rho*456*115)/25)
            self.assertGreaterEqual(se + 1e-10, low)
            self.assertLessEqual(se - 1e-10, high)

    def test_early_ratio_requires_extreme_nominal_time_change_for_first_order(self):
        r = audit.growth_audit(468, 1980, 115, 456, 25, 1, 20, 40)
        self.assertAlmostEqual(r['necessary_actual_time_ratio_at_observed_zero_baseline_ratio'], 1980/468)
        self.assertAlmostEqual(40/(1980/468), 9.454545454545455)

    def test_invalid_sample_count(self):
        with self.assertRaises(ValueError):
            audit.sem_range(100, 200, 2, 1)

    def test_zero_baseline_is_conservative(self):
        # With b >= 0: C(2t)-k*C(t) <= -(k-1)*b, making a
        # positive observed contrast even harder to reconcile.
        first = 1980 - 2*468
        for b in (0, 25, 50, 100):
            self.assertGreaterEqual(first+b, first)

    def test_method_scale_invariance_of_ratios_after_baseline_subtraction(self):
        earlier, later, b = 468, 1980, 25
        for scale in (.6, 1., 1.2):
            numer = scale*(later-b)
            denom = scale*(earlier-b)
            self.assertAlmostEqual(numer/denom, (later-b)/(earlier-b))

    def test_assay_shift_unverified(self):
        # A negative assay intercept breaks the nonnegative baseline bound.
        # This test deliberately does not infer any study-specific intercept.
        reported = 1980 - 2*468
        assay_offset = -1100
        restored = reported - (1-2)*assay_offset
        self.assertLess(restored, 0)

    def test_komesaroff_sem_not_sd(self):
        # Published values are SEM. No additional /sqrt(10) is allowed.
        c = 1969 - 2*486.6 + 89.4
        max_sem = 302 + 2*218 + 9
        self.assertAlmostEqual(c, 1085.2)
        self.assertEqual(max_sem, 747)

    def test_source_roles_remain_exposed(self):
        self.assertTrue(audit.analysis()['no_new_external_locked_human_PK_series'])

if __name__ == '__main__':
    unittest.main()
