"""Regression tests for retrospective P2-AA/P2-AR arithmetic."""
import math
import unittest

from p2_missing_stages_reanalysis import profile_two_point, reanalyse, rmse


class P2MissingStagesTest(unittest.TestCase):
    def test_rounded_p2at_numbers(self):
        output = reanalyse()
        self.assertAlmostEqual(output["price_1997_legacy_rmse_rounded"], 0.11664909986793726)
        self.assertAlmostEqual(output["rosano_1997_legacy_rmse_rounded"], 0.2936275077940303)
        self.assertEqual(output["independent_external_subjects"], 0)
        self.assertFalse(output["individual_osf_records_used"])

    def test_nonunique_two_point_profiles(self):
        a = profile_two_point(120.0, 350.0, 0.25, 1.4)
        b = profile_two_point(120.0, 350.0, 0.5, 1.8)
        self.assertTrue(a.feasible)
        self.assertTrue(b.feasible)
        self.assertNotAlmostEqual(a.gain, b.gain)
        for f, p, q in [(a, 0.25, 1.4), (b, 0.5, 1.8)]:
            self.assertAlmostEqual(f.background + f.gain * p, 120)
            self.assertAlmostEqual(f.background + f.gain * q, 350)

    def test_negative_background_refuses_shape(self):
        f = profile_two_point(120, 350, 0.75, 2)
        self.assertLess(f.background, 0)
        self.assertFalse(f.feasible)

    def test_invalid_observations(self):
        with self.assertRaises(ValueError):
            rmse((1,), ())
        with self.assertRaises(ValueError):
            rmse((math.inf,), (1,))
        with self.assertRaises(ValueError):
            profile_two_point(1, 2, 0.5, 0.5)
        with self.assertRaises(ValueError):
            profile_two_point(math.nan, 2, 0.5, 1.5)


if __name__ == "__main__":
    unittest.main()
