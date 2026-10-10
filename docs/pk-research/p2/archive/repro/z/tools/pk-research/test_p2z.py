"""P2-Z structural regression and source-role tests, not clinical validation."""
import math
import unittest
import numpy as np
import p2z_clock_envelope as z
import p2z_refine_and_validate as r

class TestP2Z(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows=__import__('json').loads(z.SOURCE.read_text())['rows']
        cls.model=cls.rows[0]['parameter']
    def test_clock_count(self):
        x,n=z.clock_design(4)
        self.assertEqual(x.shape,(21,4));self.assertEqual(len(n),21)
    def test_fixed_schedule_first(self):
        x,_=z.clock_design(3)
        self.assertEqual(list(x[0]),[0,6,12,18])
    def test_clock_increases(self):
        x,_=z.clock_design(4)
        self.assertTrue(np.all(np.diff(x,axis=1)>0))
    def test_clock_in_range(self):
        x,_=z.clock_design(4)
        self.assertTrue(np.all((x[:,1:]>=0)&(x[:,1:]<24)))
    def test_bad_qmc_size(self):
        with self.assertRaises(ValueError):z.clock_design(0)
    def test_bad_duplicate_clock(self):
        with self.assertRaises(ValueError):z.periodic_response(self.model,np.array([[0,1,1,3.]]))
    def test_bad_post_time_after_second_dose(self):
        with self.assertRaises(ValueError):z.periodic_response(self.model,np.array([[0,2.5,5,8.]]),3)
    def test_bad_history(self):
        with self.assertRaises(ValueError):z.periodic_response(self.model,np.array([[0,4,8,12.]]),days=2)
    def test_nonnegative_response(self):
        a,b=z.periodic_response(self.model,np.array([[0,4,8,12.]]))
        self.assertGreater(a[0],0);self.assertGreater(b[0],0)
    def test_history_converged(self):
        e=z.day_convergence(self.model,np.array([[0,4,8,12.]]))
        self.assertLess(e,1e-8)
    def test_mean_ratio_nominal(self):
        v=204.5/(1994-204.5)
        self.assertAlmostEqual(v,0.114277730,places=6)
    def test_feasibility_exact_boundary(self):
        z0,_,_=z.feasibility(np.array([.1, .2]),np.array([1.,1.]))
        self.assertTrue(bool(z0[0]));self.assertFalse(bool(z0[1]))
    def test_feasibility_negative(self):
        z0,rat,_=z.feasibility(np.array([2.]),np.array([1.]))
        self.assertFalse(bool(z0[0]));self.assertTrue(np.isinf(rat[0]))
    def test_feasibility_bad_observations(self):
        with self.assertRaises(ValueError):z.feasibility(np.array([1.]),np.array([2.]),400,300)
    def test_source_only_prior_model(self):
        d=__import__('json').loads(z.SOURCE.read_text())
        self.assertEqual(d['phase'],'P2-X');self.assertFalse(d['Doll_used'])
    def test_default_keeps_fifteen(self):
        a=z.analyze(log2_n=4,times=(1.5,))
        self.assertEqual(a['retained_candidate_models'],15)
    def test_strict_q6_excludes_nominal(self):
        a=z.analyze(log2_n=4,times=(1.5,))
        self.assertEqual(a['summaries']['1.5h_exact_means']['fixed_clocks_feasible_counts']['fixed_q6h'],0)
    def test_night12_reconciles_eleven(self):
        a=z.analyze(log2_n=4,times=(1.5,))
        self.assertEqual(a['summaries']['1.5h_exact_means']['fixed_clocks_feasible_counts']['fixed_night12h'],11)
    def test_sensitivity_changes_counts(self):
        a=z.analyze(log2_n=4,times=(1.25,1.5,1.75))
        self.assertNotEqual(a['summaries']['1.25h_exact_means']['fraction_of_arbitrary_grid_cells'],
                            a['summaries']['1.75h_exact_means']['fraction_of_arbitrary_grid_cells'])
    def test_one_se_is_not_probability(self):
        a=z.analyze(log2_n=4,times=(1.5,))
        self.assertTrue(a['design_clocks_not_observed_not_prior_or_posterior'])
        self.assertTrue(a['YAISH_SD_ambiguity_not_converted_to_clinical_likelihood'])
    def test_sd_difference_bounds(self):
        a=z.analyze(log2_n=4,times=(1.5,))
        self.assertEqual(a['YAISH_paired_difference_SD_possible_range_assuming_common_n_and_same_people'],[1393.5,1520.5])
    def test_no_prod_no_clinical(self):
        a=z.analyze(log2_n=4,times=(1.5,))
        self.assertFalse(a['clinical_validation_established']);self.assertTrue(a['production_untouched'])
    def test_refine_input(self):
        v=r.min_ratio(self.model,[2.5,2.5,2.5])
        self.assertTrue(math.isfinite(v) and v>=0)

if __name__=='__main__':unittest.main()
