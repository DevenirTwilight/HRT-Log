import unittest
import importlib.util
from pathlib import Path
import numpy as np
PATH=Path(__file__).with_name('p2v_continuous_fit.py')
spec=importlib.util.spec_from_file_location('p2v',PATH)
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class ResearchModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import json
        cls.dataset=json.loads(p.DATA.read_text())
        cls.studies=p.make_sources(cls.dataset)
    def test_normalized(self):
        for fam,n in [('M0',1),('M1',5),('M2',5)]:
            x=next(p.seeds(fam,n));params=p.unpack(x,fam,n)
            self.assertAlmostEqual(float(p.shape([1.],params)[0]),1.,places=12)
            self.assertAlmostEqual(float(p.shape([0.],params)[0]),0.,places=12)
    def test_first_order_limit(self):
        tt=np.linspace(0,10,20)
        self.assertTrue(np.all(p.bateman(tt,.4,.4)>=0))
        self.assertTrue(np.allclose(p.bateman(tt,.4,.4),.4*tt*np.exp(-.4*tt)))
    def test_transit_formula(self):
        self.assertTrue(np.isfinite(p.erlang(np.array([.2,2.,24.]),8,12.,1.)).all())
        self.assertRaises(ValueError,p.erlang,np.array([1.]),4,.2,.6)
    def test_two_input_to_one(self):
        x=next(p.seeds('M2',5));a=p.unpack(x,'M2',5);b=dict(a,effective_slow_weight=0.)
        m=dict(b,family='M1')
        self.assertTrue(np.allclose(p.shape([.2,1.,5.],b),p.shape([.2,1.,5.],m)))
    def test_real_study_metadata(self):
        self.assertEqual(len(self.studies),3)
        self.assertEqual(len(self.studies['Rosano1997_PK25']['obs']),4)
        self.assertEqual(len(self.studies['Price1997_figure1']['obs']),9)
        self.assertTrue(self.dataset['Doll_observations_used'] is False)
    def test_gls_baseline_amplitude_bounds(self):
        s=self.studies['Rosano1997_PK25'];fit=p.profile_study(np.array([.1,.3,.8,1.]),s)
        self.assertGreaterEqual(fit['baseline'],0)
        self.assertLessEqual(fit['baseline'],225)
        self.assertGreaterEqual(fit['amplitude_at_1h'],0)
    def test_correlation_positive_covariance(self):
        for rho in [0.,.65,.9]:
            s=p.make_sources(self.dataset,rho=rho)['Price1997_figure1']
            self.assertTrue(np.all(np.linalg.eigvalsh(s['precision'])>0))
    def test_correlation_out_of_range(self):
        self.assertRaises(ValueError,p.make_sources,self.dataset,.15,1.)
    def test_time_scaling_changes_shape(self):
        x=next(p.seeds('M1',5));z=p.unpack(x,'M1',5)
        self.assertNotAlmostEqual(p.shape([.4],z)[0],p.shape([.5],z)[0])
    def test_q6_positive_finite(self):
        z=p.unpack(next(p.seeds('M2',5)),'M2',5)
        self.assertGreater(p.repeated_index(z),0)
        self.assertTrue(np.isfinite(p.repeated_index(z)))
    def test_score_returns_details(self):
        x=next(p.seeds('M0',1))
        q,pro,pa=p.score(x,'M0',1,self.studies,detail=True)
        self.assertTrue(np.isfinite(q));self.assertEqual(len(pro),3)
    def test_price_not_double_counted(self):
        x=next(p.seeds('M2',5));z=p.unpack(x,'M2',5)
        d=p.price_checks(z,self.studies,self.dataset)
        self.assertTrue(d['same_cohort_fig_and_table_not_independent'])
    def test_candidate_families_parameter_count(self):
        self.assertEqual(len(p.bounds_for('M0')),2)
        self.assertEqual(len(p.bounds_for('M1')),2)
        self.assertEqual(len(p.bounds_for('M2')),4)
    def test_hypothetical_correlation_not_hidden(self):
        aa=p.make_sources(self.dataset,rho=0.)['Rosano1997_PK25']
        bb=p.make_sources(self.dataset,rho=.65)['Rosano1997_PK25']
        self.assertNotAlmostEqual(aa['precision'][0,1],bb['precision'][0,1])
    def test_study_anchored_multi_event_superposition(self):
        z=p.unpack(next(p.seeds('M2',5)),'M2',5)
        one=p.simulate_study_anchored(2,z,[(0,1)],10.,100.)
        two=p.simulate_study_anchored(2,z,[(0,1),(1,1)],10.,100.)
        self.assertAlmostEqual(two-one,100.,places=10)
        self.assertAlmostEqual(p.simulate_study_anchored(2,z,[(4,1)],10.,100.),10.)
    def test_invalid_research_amplitude_or_dose(self):
        z=p.unpack(next(p.seeds('M2',5)),'M2',5)
        self.assertRaises(ValueError,p.simulate_study_anchored,1,z,[(0,1)],0.,-4.)
        self.assertRaises(ValueError,p.simulate_study_anchored,1,z,[(0,-1)],0.,4.)
    def test_optimizer_single_family(self):
        r=p.optimize('M0',self.studies,fast=True)
        self.assertTrue(np.isfinite(r['loss']))
        self.assertGreater(r['n_optimizations'],0)
        self.assertEqual(r['parameters']['n_fast'],1)
    def test_bounded_price_and_ros(self):
        ss=p.make_sources(self.dataset,ros_baseline_cap=0.,price_baseline_cap=0.)
        for n in ['Rosano1997_PK25','Price1997_figure1']:
            r=p.profile_study(np.ones(len(ss[n]['time'])),ss[n])
            self.assertEqual(r['baseline'],0.)
if __name__=='__main__':unittest.main()
