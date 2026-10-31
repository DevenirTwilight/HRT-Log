"""Regression guards for the research-only SC-cypionate counterexample."""
import unittest
import t_sc_cypionate_partial as p

class TScCypionateCounterexampleTest(unittest.TestCase):
    def test_published_anchor_identity(self):
        self.assertEqual(p.OBS,{0.:497.,.25:656.,5.:621.,7.:477.})
        self.assertEqual(p.BASELINE_ASSUMED,487)

    def test_all_shapes_exactly_match_only_two_fitted_points(self):
        for s in p.check()['scenario_curves']:
            self.assertAlmostEqual(s['model_at_reported_times_ng_dl']['0.25'],656,places=3)
            self.assertAlmostEqual(s['model_at_reported_times_ng_dl']['5.0'],621,places=3)

    def test_periodic_endpoint_cannot_equal_distinct_observations(self):
        for s in p.check()['scenario_curves']:
            self.assertEqual(s['endpoint_deviation_day0_ng_dl'],-10)
            self.assertEqual(s['endpoint_deviation_day7_ng_dl'],10)

    def test_outlandish_peak_exposes_non_identifiability(self):
        s=p.check()['scenario_curves']
        self.assertLess(s[0]['implied_peak_total_ng_dl'],700)
        self.assertGreater(s[-1]['implied_peak_total_ng_dl'],10000)
        self.assertFalse(p.run()['independent_validation'])

if __name__=='__main__':unittest.main()
