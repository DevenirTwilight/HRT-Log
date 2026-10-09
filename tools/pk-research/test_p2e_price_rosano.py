"""P2-E studies are previously seen aggregates, not new locked human validation."""
import math
import unittest
import p2e_price_rosano as p
import models as m

class P2EMetricsTest(unittest.TestCase):
    def test_price_trapezoid_has_correct_dimensions(self):
        self.assertAlmostEqual(p.sampled_auc(lambda t:1),24)
        self.assertAlmostEqual(p.sampled_auc(lambda t:2*t),576)
    def test_rosano_cohort_and_statistical_bounds(self):
        self.assertEqual(1980-2*468,1044)
        self.assertEqual(456+2*115,686)
        self.assertGreater(1044/((456+2*115)/math.sqrt(25)),7.6)
    def test_price_dose_mean_not_exactly_linear(self):
        self.assertGreater(2109/970,2)
        self.assertLess(970/825,2)
    def test_first_order_theoretical_limit(self):
        for rate in [.1,.32,1,4,12]:
            for decay in [.1,.41,1,4]:
                k=m.Dual(1,rate,.32,decay,.1)
                self.assertLess(k(40/60)/k(20/60),2)
    def test_fixed_grid_and_model_integrator(self):
        self.assertEqual(p.PRICE_GRID,(0,1,2,3,4,6,8,12,18,24))
        self.assertAlmostEqual(m.simpson(lambda t:2*t,24,4000),576,places=7)

if __name__=='__main__':
    unittest.main()
