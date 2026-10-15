import unittest
import json
import tempfile
from pathlib import Path
import numpy as np
import p2u_joint_constraints as p

class JointConstraintsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=json.loads(p.OBS_FILE.read_text())
    def test_no_doll_used(self):
        self.assertFalse(self.data['Doll_observations_used'])
        self.assertEqual(set(self.data['studies']),{'Rosano1997_PK25','Komesaroff1998_n10','Price1997_figure1'})
    def test_kom_serum_sem(self):
        self.assertIn('SEM',self.data['studies']['Komesaroff1998_n10']['published_dispersion'])
        self.assertEqual(self.data['studies']['Komesaroff1998_n10']['nominal_se_or_read_width'][0],9)
    def test_rosano_se_sd_over_root_n(self):
        self.assertEqual(self.data['studies']['Rosano1997_PK25']['nominal_se_or_read_width'],[11.2,23,91.2,113.6])
    def test_dose_times(self):
        self.assertEqual(self.data['studies']['Komesaroff1998_n10']['hours'],[0,.25,.5])
        self.assertEqual(self.data['studies']['Price1997_figure1']['hours'][-1],24)
    def test_linear_first_order_onset_bound(self):
        for a in [0.7,1.8,6.]:
            for e in [0.2,0.85,1.1]:
                c=p.g(np.array([1/3,2/3]),1,a,e)
                self.assertLessEqual(c[1],2*c[0]+1e-10)
    def test_transit_early_order(self):
        ratio=p.g(np.array([0.000001,.000002]),4,9,.6)
        self.assertAlmostEqual(ratio[1]/ratio[0],16,places=2)
    def test_ka_ke_swap_normalized_first_order(self):
        t=np.array([.3,1.,2.,8.]);x=p.g(t,1,1.8,.4);y=p.g(t,1,.4,1.8)
        self.assertTrue(np.allclose(x/x[1],y/y[1],rtol=1e-12))
    def test_zero_effective_slow_weight_ignores_slow_rate(self):
        a=p.kernel(p.TIMES,(5,9.,.4,.05,0.));b=p.kernel(p.TIMES,(5,9.,.4,.4,0.))
        self.assertTrue(np.array_equal(a,b))
    def test_profile_can_recover_known_background_and_scale(self):
        h=np.array([0.,1.,.3,.04]);y=20+120*h
        r=p.profile(y,np.arange(4),h,(0,30),np.ones(4))
        self.assertAlmostEqual(r['baseline'],20,places=6)
        self.assertAlmostEqual(r['amplitude'],120,places=6)
    def test_profile_clips_baseline(self):
        h=np.array([0.,1.,2.]);y=40+60*h
        r=p.profile(y,np.arange(3),h,(0,20),np.ones(3))
        self.assertLessEqual(r['baseline'],20)
    def test_nonnegative_amplitude(self):
        h=np.array([0.,1.,2.]);y=np.array([100.,80.,70.]);r=p.profile(y,np.arange(3),h,(0,120),np.ones(3))
        self.assertGreaterEqual(r['amplitude'],0)
    def test_model_family_counts(self):
        from collections import Counter
        v=Counter(fam for fam,_ in p.candidates())
        self.assertEqual(v, {'M0':35,'M1':210,'M2':3360})
    def test_model_studies_aggregates_only(self):
        studies=p.sources(self.data,.15,225,24)
        self.assertEqual(set(studies),set(self.data['studies']))
        self.assertEqual(len(studies['Rosano1997_PK25']['y']),4)
    def test_sources_capped_individually(self):
        studies=p.sources(self.data,.15,0,0)
        self.assertEqual(studies['Rosano1997_PK25']['bounds'],(0.,0.))
        self.assertEqual(studies['Price1997_figure1']['bounds'],(0.,0.))
        self.assertEqual(studies['Komesaroff1998_n10']['bounds'],(0.,160.))
    def test_unseen_human_dataset_never_claimed(self):
        self.assertIn('PREVIOUSLY_EXPOSED',self.data['role'])
    def test_price_auc_raw_figure_vs_author(self):
        y=[20]+self.data['studies']['Price1997_figure1']['y']
        t=[0]+self.data['studies']['Price1997_figure1']['hours']
        self.assertAlmostEqual(float(np.trapezoid(y,t)),1567.5)
        self.assertNotAlmostEqual(float(np.trapezoid(y,t)),self.data['price_author_table_auc_mean'])
    def test_fixed_grid_output_one_scenario_is_stable(self):
        d=p.calculate(self.data,.15,225,24)
        self.assertEqual(d['M1']['n_evaluated'],210)
        self.assertEqual(d['M2']['n_evaluated'],3360)
        self.assertLess(d['M2']['objective_equal_cohort_pseudo_loss'],d['M1']['objective_equal_cohort_pseudo_loss'])
        self.assertLess(d['M1']['objective_equal_cohort_pseudo_loss'],d['M0']['objective_equal_cohort_pseudo_loss'])
        self.assertFalse('Doll' in str(d))
    def test_without_price_means_only_two_studies(self):
        d=p.calculate(self.data,.15,225,24,include_price=False)
        self.assertEqual(len(d['M2']['study_profiles']),2)
        self.assertIsNone(d['M2']['price_baseline_subtracted_auc0_24_h'])
    def test_repeated_dose_ratio_not_clinical_prediction(self):
        d=p.calculate(self.data,.15,225,24)
        self.assertGreater(d['M2']['repeat_q6_predose_relative_to_single_1mg_1h'],0)
        self.assertNotIn('pg_ml',d['M2'])

if __name__=='__main__':unittest.main()
