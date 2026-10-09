"""Compare mathematical strategies; B is production, A remains research only."""
import math
import unittest
from sublingual_p1a_compare import normalized
from sublingual_compare import fitted

class StrategyTest(unittest.TestCase):
    def test_a_preserves_population_and_is_continuous_at_rate_crossing(self):
        a,ka,lam=391432.57,1.0005,.9995
        model={'ka_per_h':ka,'terms':[{'A_per_mg':a,'lambda_per_h':lam}]}
        crossing=ka/lam
        for t in [.25,.5,.75,46/60,1,1.5,2,3,4,6,8,12,24]:
            self.assertAlmostEqual(normalized(t,a,ka,lam,1),fitted(t,model),places=8)
            limit=a*(ka-lam)*t*math.exp(-ka*t)
            for r in [math.nextafter(crossing,0),crossing,math.nextafter(crossing,math.inf)]:
                self.assertAlmostEqual(normalized(t,a,ka,lam,r),limit,places=8)
            for r in [1e-12,.9,1,1.1,1e12]:
                value=normalized(t,a,ka,lam,r)
                self.assertTrue(math.isfinite(value) and value>=0)

    def test_a_has_no_singular_or_sign_flipping_rate_sensitivity(self):
        vals=[normalized(46/60,391432.57,1.0005,.9995,r) for r in [.9,1,1.0005/.9995,1.1]]
        self.assertTrue(all(a>b>0 for a,b in zip(vals,vals[1:])))
        self.assertEqual(normalized(-1,391432.57,1.0005,.9995,1),0)
