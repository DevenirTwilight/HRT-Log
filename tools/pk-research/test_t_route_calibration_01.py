"""Independent contract checks for the research-only T route prototype."""
import math
import unittest
import t_route_calibration_01 as pk


class TRouteResearchTest(unittest.TestCase):
    def test_kernel_causality_and_sign(self):
        for kernel in (lambda t:pk.bateman(t,.3),
                       lambda t:pk.transit2(t,.2),
                       lambda t:pk.nebido_kernel(t,.14,.3)):
            self.assertEqual(kernel(-1),0)
            self.assertEqual(kernel(0),0)
            for t in (.001,.1,1,7,70,400):
                self.assertGreaterEqual(kernel(t),0)
                self.assertTrue(math.isfinite(kernel(t)))

    def test_equal_rate_limit(self):
        x=2*math.exp(-2)
        self.assertAlmostEqual(pk.bateman(2,1,1),x,places=8)
        self.assertAlmostEqual(pk.transit2(2,1,1),x,places=8)

    def test_androtardyl_naive_fails_tmax(self):
        d=pk.androtardyl()
        self.assertLess(d['naive_single_exponential_peak_day'],1)
        self.assertGreaterEqual(d['two_input_peak_day'],1.5)
        self.assertLessEqual(d['two_input_peak_day'],3)

    def test_androtardyl_tail_baseline_is_conditional(self):
        d=pk.androtardyl()
        self.assertGreater(d['minimum_assumed_baseline_to_pass_ng_ml'],1)
        self.assertLess(d['minimum_assumed_baseline_to_pass_ng_ml'],2)
        self.assertFalse(d['baseline_sensitivity'][0]['passes_both_lower_bounds'])
        self.assertTrue(d['baseline_sensitivity'][-1]['passes_both_lower_bounds'])

    def test_androgel_multiple_distinct_shapes(self):
        p=pk.androgel()['shape_family']
        self.assertEqual(len(p),3)
        self.assertGreater(p[0]['post_last_application_day11_to14_ng_ml']['13'],
                           p[2]['post_last_application_day11_to14_ng_ml']['13'])

    def test_nebido_positive_slow_component(self):
        d=pk.nebido()
        self.assertEqual(d['candidate_count'],126)
        self.assertGreater(d['minimum_score_candidate']['slow_input_mass_weight'],0)

    def test_nebido_residual_retained(self):
        d=pk.nebido()['minimum_score_candidate']
        self.assertGreater(d['sse'],0)
        self.assertEqual(len(d['residual_nmol_l']),4)
        self.assertTrue(all(math.isfinite(x) for x in d['residual_nmol_l']))

    def test_t_scientific_status(self):
        d=pk.build()
        self.assertTrue(d['research_only'])
        for k in ('ANDROGEL_50','ANDROTARDYL','NEBIDO'):
            self.assertNotIn('VALIDATED',d[k]['status'].replace('NOT_VALIDATED',''))

if __name__=='__main__':
    unittest.main()
