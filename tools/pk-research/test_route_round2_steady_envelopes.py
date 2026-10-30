import math
import unittest
import route_round2_steady_envelopes as m

class NewRouteEvidenceTests(unittest.TestCase):
    def test_analytic_average_and_extrema(self):
        for n, (high, low, avg) in m.SPRAY.items():
            for tmax in (18.,22.):
                d=m.envelope(high,low,avg,24.,tmax)
                self.assertAlmostEqual(d['analytic_average'],avg,places=8)
                self.assertAlmostEqual(d['points'][int(tmax*4)][1],high,places=5)
                self.assertAlmostEqual(d['points'][0][1],low,places=5)

    def test_nonunique_peak_phase_exact_same_summary(self):
        one=m.envelope(*m.SPRAY[1],24.,18.)
        two=m.envelope(*m.SPRAY[1],24.,22.)
        self.assertEqual((one['source_cmax'],one['source_cmin'],one['source_cavg']),
                         (two['source_cmax'],two['source_cmin'],two['source_cavg']))
        self.assertGreater(abs(one['points'][72][1]-two['points'][72][1]),2.0)

    def test_sc_label_mixed_dose_group_and_period(self):
        x=m.envelope(*m.SC_T,168,11.9,168)
        self.assertEqual(x['source_cavg'],553)
        self.assertEqual(x['period_hours'],168)
        self.assertAlmostEqual(x['analytic_average'],553,places=7)
        self.assertEqual(x['points'][0][1],436)

    def test_patch_exp_washout_70_minutes(self):
        r=m.research()['patch_post_removal_fraction_after_hours']
        self.assertAlmostEqual(r['1'],2**(-60/70),places=9)
        self.assertLess(r['24'],1e-5)

    def test_nasal_half_life_range_not_identified(self):
        a,b=m.research()['nasal_remaining_80min_after_peak_for_t12_10_100min']
        self.assertLess(a,0.01)
        self.assertGreater(b,0.5)

    def test_invalid_stats_rejected(self):
        for args in [(1,2,1.5,24,10),(2,0,3,24,10),(2,0,1,24,0)]:
            with self.assertRaises(ValueError):m.envelope(*args)

    def test_non_prospective_flag(self):
        d=m.research()
        self.assertTrue(d['not_personal_or_population_prospective_prediction'])
        self.assertTrue(all(a['status']=='ILLUSTRATIVE_STEADY_SUMMARY_ONLY' for grp in d['spray'].values() for a in grp.values()))

if __name__=='__main__':unittest.main()
