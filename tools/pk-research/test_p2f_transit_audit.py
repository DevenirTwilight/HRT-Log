import math
import unittest
import p2f_transit_audit as a

class TransitEvidenceAudit(unittest.TestCase):
    def test_low_order_gamma_strict_upper_bounds(self):
        for k in (.1,.5,1,4,8):
            self.assertLess(a.gamma_ratio_double(2,k,1/3),2)
            self.assertLess(a.gamma_ratio_double(3,k,1/3),4)
    def test_rosano_20_to_40_exceeds_gamma3_max(self):
        self.assertGreater(1980/468,4)
        self.assertGreater(a.corrected_ratio(1980,468,100),1980/468)
    def test_unknown_baseline_threshold(self):
        b=(234*1980-468**2)/(234+1980-2*468)
        self.assertAlmostEqual(b,191.1549295774648,8)
        self.assertAlmostEqual((468-b)**2,(234-b)*(1980-b),7)
    def test_single_gamma_sensitivity(self):
        v={10:234,20:468,40:1980,60:2124}
        zero=a.gamma_shape_from_three_later_points(v,0)
        self.assertAlmostEqual(zero["gamma_order_continuous"],5.76977782245,8)
        self.assertAlmostEqual(zero["predicted_C20_over_C10_increment_ratio"],10.7431778944,8)
        self.assertEqual(zero["observed_C20_over_C10_increment_ratio"],2)
        high=a.gamma_shape_from_three_later_points(v,225)
        self.assertGreater(high["gamma_order_continuous"],zero["gamma_order_continuous"])
        self.assertLess(high["predicted_C20_over_C10_increment_ratio"],high["observed_C20_over_C10_increment_ratio"])
    def test_invalid_assumed_baseline_rejected(self):
        with self.assertRaises(ValueError):a.corrected_ratio(1980,468,468)
        with self.assertRaises(ValueError):a.gamma_shape_from_three_later_points({10:234,20:468,40:1980,60:2124},234)

if __name__=="__main__":unittest.main()
