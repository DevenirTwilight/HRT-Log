import hashlib
import json
import math
import random
import shutil
import tempfile
import unittest
from pathlib import Path
import check_protocol as guard
import models as m

P2=guard.P2
class ResearchMathTest(unittest.TestCase):
    def test_exact_equal_rate_limit(self):
        for k in [1e-6,1e-3,0.1,1,100,1000]:
            for t in [0,1e-9,1/k,24,768]:
                self.assertEqual(m.q(t,k,k),k*t*math.exp(-k*t))
    def test_crossing_critical_rate_continuity(self):
        for k in [1e-6,0.1,1,1000]:
            for t in [1e-6,1/k,24]:
                center=m.q(t,k,k)
                for delta in [-1e-12,-1e-8,1e-12,1e-8]:
                    value=m.q(t,k*(1+delta),k)
                    self.assertTrue(math.isfinite(value) and value >= 0)
                    self.assertLessEqual(abs(value-center),1e-6*max(1e-15,center))
    def test_dual_independent_rk4_oracle(self):
        for kam,kag,ke in [(4,.32,.41),(1,1,1),(.41,4,.32),(.32,.32,.32),(1.00000000001,1,1)]:
            kernel=m.Dual(620,kam,kag,ke,.15)
            for t in [.25,1,1.5,8,24]:
                expected=m.ode_oracle(t,kam,kag,ke,620*.85,620*.15,12000)
                self.assertAlmostEqual(kernel(t),expected,delta=1e-6*max(1e-9,expected))
    def test_analytic_integrals_against_simpson(self):
        for kernel in [m.Gamma(350,.25),m.Gamma(350,4),m.Dual(620,1,1,1,.5),m.Dual(620,4,.32,.41,.1)]:
            for upper in [1e-5,.25,1,8,24]:
                self.assertAlmostEqual(kernel.integral(upper),m.simpson(kernel,upper,8000),delta=max(1e-10,kernel.integral(upper)*1e-6))
    def test_gamma_normalization_and_true_model_peak(self):
        for k in [.25,1,4]:
            a=m.Gamma(100,k)
            self.assertAlmostEqual(a.integral(100/k),100)
            self.assertGreater(a(1/k),a(.99/k))
            self.assertGreater(a(1/k),a(1.01/k))
    def test_stress_finite_and_nonnegative_without_clipping(self):
        rng=random.Random(20261009)
        for _ in range(3000):
            rates=[10**rng.uniform(-6,3) for _ in range(3)]
            t=10**rng.uniform(-9,3)
            kernel=m.Dual(10**rng.uniform(-3,6),*rates,rng.random())
            value=kernel(t)
            self.assertTrue(math.isfinite(value) and value >= 0)
            gamma=m.Gamma(10**rng.uniform(-3,6),rates[0])
            self.assertTrue(math.isfinite(gamma(t)) and gamma(t) >= 0)
    def test_dose_units_and_rejected_inputs(self):
        self.assertAlmostEqual(m.pg_ml(367.1,'pmol/L'),100)
        self.assertAlmostEqual(m.active_e2_mg(2,'EV'),2*272.39/356.51)
        for f,args in [(m.q,(1,0,1)),(m.q,(math.nan,1,1)),(m.Gamma,(-1,1)),(m.Dual,(1,1,1,1,1.1)),(m.pg_ml,(1,'ng/mL')),(m.active_e2_mg,(-1,)),(m.population,(m.Gamma(1,1),[(0,-1)],1))]:
            with self.assertRaises(ValueError):f(*args)
    def test_thirty_days_superposition_skip_correction_stop(self):
        for kernel in [m.Gamma(350,1),m.Dual(620,4,.32,.41,.1)]:
            for interval in [6,12,24]:
                events=m.repeat_events(interval)
                at=events[-1][0]+46/60
                total=m.population(kernel,events,at)
                expected=math.fsum(2*kernel(at-h) for h,_ in events)
                self.assertEqual(total,expected)
                self.assertAlmostEqual(m.population(kernel,events[:-1],at),total-2*kernel(46/60),delta=1e-10)
                corrected=events[:-1]+[(events[-1][0],4)]
                self.assertAlmostEqual(m.population(kernel,corrected,at),total+2*kernel(46/60),delta=1e-10)
                self.assertGreaterEqual(total,0)
                self.assertLess(m.population(kernel,events,at+48),total)
                for dose in [1,2,4,10]:
                    scaled=[(h,dose) for h,_ in events]
                    self.assertAlmostEqual(m.population(kernel,scaled,at),total*dose/2,delta=1e-9)
    def test_frozen_history_has_no_current_profile_dependency(self):
        events=[(0,2),(12,1)]
        kernel=m.Gamma(350,1)
        result=m.population(kernel,events,13)
        alternate_current_profile={'route':'oral','dose':4}
        self.assertEqual(result,m.population(kernel,events,13))
        self.assertEqual(alternate_current_profile['route'],'oral')
    def test_distinct_microscopic_sets_same_trajectory(self):
        groups=[(.1,1,.03,160),(.2,.5,.03375,160),(.2,.25,.016875,80)]
        kernels=[m.from_micro(*g) for g in groups]
        for t in [0,.25,.5,1,4,8,12,24]:
            for kernel in kernels[1:]:self.assertAlmostEqual(kernels[0](t),kernel(t),delta=1e-10)
    def test_flip_flop_single_depot_counterexample(self):
        for t in [.25,1,4,8,24]:
            self.assertAlmostEqual(100*m.q(t,.1,1),10*m.q(t,1,.1),delta=1e-12)
    def test_convex_amplitude_multi_start_synthetic(self):
        for unit in [.001,1,100]:
            result=m.amplitude_fit(unit,unit*350,[.001,1e6],[.001,1,1000,1e6])
            self.assertEqual(result['amplitude'],350)
            for trial in result['starts']:self.assertAlmostEqual(trial['solution'],350,delta=1e-9)
    def test_boundary_failure_is_reported(self):
        result=m.amplitude_fit(.001,1e7,[.001,1e6],[1])
        self.assertEqual(result['status'],'amplitude_outside_prespecified_bounds')
    def test_one_human_point_cannot_identify_shape(self):
        # Synthetic target only: all preset shapes solve exactly, rates differ.
        for k in [.25,.5,1,2,4]:
            exposure=123/m.Gamma(1,k)(1)
            self.assertAlmostEqual(m.Gamma(exposure,k)(1),123)
        self.assertNotAlmostEqual(m.Gamma(123/m.Gamma(1,.25)(1),.25)(12),m.Gamma(123/m.Gamma(1,4)(1),4)(12))

class EvidenceGuardTest(unittest.TestCase):
    def test_lock_and_production_and_immutable_old_results(self):
        guard.check()
    def test_all_input_mutations_fail_including_unit_statistic_source(self):
        for name in ['evidence-catalog.json','source-manifest.json','split-manifest.json','validation-protocol.md','experiment-settings.json','production-baseline.json']:
            with tempfile.TemporaryDirectory() as directory:
                p=Path(directory)
                for f in P2.glob('*'):
                    if f.is_file():shutil.copy(f,p/f.name)
                (p/name).write_text((p/name).read_text()+' ')
                with self.assertRaises(ValueError):guard.check(p)
        for key,value in [('unit','ng/mL'),('statistic','individual true Cmax'),('source_location','invented table')]:
            with tempfile.TemporaryDirectory() as directory:
                p=Path(directory)
                for f in P2.glob('*'):
                    if f.is_file():shutil.copy(f,p/f.name)
                data=json.loads((p/'evidence-catalog.json').read_text());data['records'][0][key]=value
                (p/'evidence-catalog.json').write_text(json.dumps(data))
                with self.assertRaises(ValueError):guard.check(p)
    def test_forged_protocol_sha_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)
            for f in P2.glob('*'):
                if f.is_file():shutil.copy(f,p/f.name)
            lock=json.loads((p/'protocol-lock.json').read_text());lock['protocol_sha256']='0'*64
            (p/'protocol-lock.json').write_text(json.dumps(lock))
            with self.assertRaises(ValueError):guard.check(p)
    def test_study_unit_roles_and_no_fake_blind_validation(self):
        data=json.loads((P2/'evidence-catalog.json').read_text());split=json.loads((P2/'split-manifest.json').read_text())
        self.assertEqual(len(data['studies']),7)
        self.assertEqual(split['roles']['LOCKED_EXTERNAL'],[])
        self.assertEqual(split['train_record_ids'],['doll_sl_peak'])
        for r in data['records']:
            self.assertFalse(r['independent_validation'])
            self.assertEqual(r['role'],next(s['role'] for s in data['studies'] if s['id']==r['study_id']))
    def test_unknown_statistic_and_sampling_not_filled(self):
        data=json.loads((P2/'evidence-catalog.json').read_text());records={r['id']:r for r in data['records']}
        self.assertEqual(records['pines_60min']['spread_type'],'unverified')
        self.assertNotIn('sd',records['pines_60min'])
        self.assertIsNone(records['casper_30min_fold']['time_h'])
        self.assertIsNone(records['cortez_qd_6mo']['dose_mg'])
        self.assertEqual(records['doll_auc_ratio']['unit'],'ratio')
    def test_full_text_levels_are_not_http_success(self):
        data=json.loads((P2/'evidence-catalog.json').read_text())
        for s in data['studies']:
            if s['id'] not in ['Yaish2023','Cortez2024']:self.assertEqual(s['evidence_level'],'primary_abstract')
        req=json.loads((P2/'source-manifest.json').read_text())['requests']
        self.assertTrue(any(r['http_status']==200 and r['content_level']=='browser_challenge_not_article' for r in req))
    def test_current_reference_against_p0_goldens_and_default_params(self):
        params=json.loads((guard.ROOT/'pk-engine/src/main/resources/pk-params.json').read_text())
        data=json.loads((P2/'evidence-catalog.json').read_text())
        self.assertAlmostEqual(2*m.current(46/60,params),278.86477960569,delta=1e-8)
        self.assertAlmostEqual(2*m.feather(46/60,data['comparison_model']),914.262352,delta=1e-6)
        self.assertEqual(guard.digest(guard.ROOT/'pk-engine/src/main/resources/pk-params.json'),data['production_params_sha256'])
    def test_reference_tier_formula_matches_explicit_engine_rule(self):
        params=json.loads((guard.ROOT/'pk-engine/src/main/resources/pk-params.json').read_text());sl=params['models']['E2_SL']
        for minutes in [2,5,10,15]:
            mucosal=min(1,(1-sl['swallowed_share'])*minutes/10)
            expected=mucosal/(1-sl['swallowed_share'])*m.fitted(1,sl)+(1-mucosal)*m.fitted(1,params['models']['E2_ORAL'])
            self.assertAlmostEqual(m.current(1,params,minutes),expected)
    def test_no_production_dependency_imports(self):
        baseline=json.loads((P2/'production-baseline.json').read_text())
        for name in baseline['files']:
            if name.endswith('.kt'):
                self.assertNotIn('tools/pk-research',(guard.ROOT/name).read_text())

if __name__=='__main__':unittest.main()
