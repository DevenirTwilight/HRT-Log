"""SL-CAL-01 research tests: constants tie back to production sources, determinism, fitting maths against an independent solver,
policy behaviour, fail-closed inputs, and the research guard (no LIVE_LOCK file touched, no unlisted production dependency)."""
import copy
import hashlib
import json
import math
import os
import re
import sys
import unittest
from pathlib import Path

import numpy as np
from scipy.optimize import minimize_scalar

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / 'sl_cal'))
import check_protocol as guard  # noqa: E402
import sl_cal_core as c  # noqa: E402
import run_sl_cal_01 as study  # noqa: E402
import eligibility_rules as rules  # noqa: E402
import phase_guard as phase  # noqa: E402

ROOT = guard.ROOT
RESULTS = ROOT / 'docs/pk-research/sl-cal/results'


class ConstantsTieBackToProduction(unittest.TestCase):
    def test_labfit_constants_match_kotlin_source(self):
        text = (ROOT / 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/LabFit.kt').read_text()
        self.assertIn('sqrt(ln(1 + 0.299 * 0.299))', text)
        self.assertIn('PRIOR_SD_AMPLITUDE_DEFAULT = sqrt(ln(1 + 0.48 * 0.48))', text)
        self.assertIn('PRIOR_SD_RATE = sqrt(ln(1 + 0.29 * 0.29))', text)
        self.assertAlmostEqual(c.SIGMA_LAB, math.sqrt(math.log(1 + 0.299 ** 2)), 15)
        self.assertAlmostEqual(c.PRIOR_SD_P1A, math.sqrt(math.log(1 + 0.48 ** 2)), 15)
        self.assertIn('const val PMOL_PER_PG = 3.671', (ROOT / 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/Units.kt').read_text())

    def test_m2_shape_is_the_frozen_product_representative(self):
        p, a = c.m2_shape()
        self.assertEqual(p['id'], 'p2x-39')
        self.assertAlmostEqual(a, 434.835722, places=5)       # product lock: Price B24 amplitude
        lock = json.loads((ROOT / 'docs/design/pr19-calibration-evidence/product-lock.json').read_text())
        self.assertEqual(lock['M2']['runtime_signature'].split('|')[0], 'p2x-39')

    def test_m2_one_mg_curve_matches_independent_formula(self):
        p, a = c.m2_shape()
        t = np.array([0.0, 0.5, 1.0, 2.0, 8.0])
        got = c.sl_increment(t, [(0.0, 1.0)], (p, a))
        self.assertAlmostEqual(got[2], a, 9)                  # response(1h) is 1 by construction
        self.assertEqual(got[0], 0.0)
        double = c.sl_increment(t, [(0.0, 2.0)], (p, a))
        np.testing.assert_allclose(double, 2 * got)
        self.assertTrue(np.all(c.sl_increment(np.array([-1.0]), [(0.0, 1.0)], (p, a)) == 0))


class FittingMaths(unittest.TestCase):
    def setUp(self):
        self.shape = c.m2_shape()
        self.regimens = c.regimens()

    def _observations(self, name, g_sl, g_nsl, times):
        reg = self.regimens[name]
        basis = c.Basis(reg, self.shape)
        t = np.array(times, float)
        mu = c.BACKGROUND_PGML + g_sl * basis.S(t) + (g_nsl * basis.N(t) if reg.mixed else 0.0)
        return basis, t, mu

    def test_noise_free_one_coefficient_is_recovered_with_flat_enough_prior(self):
        basis, t, mu = self._observations('SL_1mg_q12', 2.0, 1.0, [297.0, 321.0, 309.9])
        fit = c.fit_method('A_sl_only', basis, t, mu, np.full(3, 0.01), 5.0)
        self.assertAlmostEqual(fit.ks, 2.0, places=3)

    def test_matches_an_independent_scalar_solver(self):
        basis, t, mu = self._observations('SL_1mg_q12', 0.5, 1.0, [297.5, 298.2, 320.5, 310.0])
        rng = np.random.default_rng(3)
        y = mu * np.exp(0.2 * rng.standard_normal(4))
        sigma = np.full(4, c.SIGMA_LAB)
        fit = c.fit_method('A_sl_only', basis, t, y, sigma, c.PRIOR_SD_P1A)
        s = basis.S(t)
        def obj(u):
            m = c.BACKGROUND_PGML + math.exp(u) * s
            return 0.5 * (u * u / c.PRIOR_SD_P1A ** 2 + float(np.sum(((np.log(y) - np.log(m)) / sigma) ** 2)))
        ref = minimize_scalar(obj, bounds=(-5, 5), method='bounded', options={'xatol': 1e-12})
        self.assertAlmostEqual(math.log(fit.ks), ref.x, places=5)
        h = 1e-4
        curvature = (obj(ref.x + h) - 2 * obj(ref.x) + obj(ref.x - h)) / h ** 2
        # Laplace sd uses the Gauss-Newton information (as production LabFit.solveAmplitude does), so it differs from the exact curvature by the residual term only
        self.assertAlmostEqual(fit.sl_sd, math.sqrt(1 / curvature), delta=0.03 * fit.sl_sd)

    def test_no_labs_returns_the_prior_not_a_fit(self):
        basis = c.Basis(self.regimens['SL_1mg_q12'], self.shape)
        fit = c.fit_method('A_sl_only', basis, np.array([]), np.array([]), np.array([]), 0.7)
        self.assertEqual((fit.ks, fit.kn), (1.0, 1.0))
        self.assertEqual(fit.sl_sd, 0.7)

    def test_two_coefficients_are_not_separable_from_trough_only_labs(self):
        # Trough labs in the q12 oral-mixed regimen carry almost only non-SL information: the SL coefficient stays at its prior.
        basis, t, mu = self._observations('MIX_SL1mg_q12_oral2mg', 3.0, 0.5, [223.6, 247.6, 271.6, 295.6, 319.6])
        fit = c.fit_method('B_two_independent', basis, t, mu, np.full(5, c.SIGMA_LAB), 0.9)
        self.assertGreater(fit.sl_sd, 0.85)           # prior sd 0.9 barely reduced
        self.assertLess(fit.nsl_sd, 0.35)

    def test_pooled_prior_ties_the_two_coefficients(self):
        basis, t, mu = self._observations('MIX_SL1mg_q12_oral2mg', 2.0, 2.0, [297.5, 298.0, 321.0])
        pooled = c.fit_method('B2_two_pooled', basis, t, mu, np.full(3, c.SIGMA_LAB), 0.9)
        self.assertLess(abs(math.log(pooled.ks) - math.log(pooled.kn)), 0.5)

    def test_common_and_p1a_agree_for_sublingual_only(self):
        basis, t, mu = self._observations('SL_2mg_q24', 1.7, 1.0, [297.5, 321.5, 310.0])
        a = c.fit_method('C_common', basis, t, mu, np.full(3, c.SIGMA_LAB), c.PRIOR_SD_P1A)
        d = c.fit_method('D_p1a_framework', basis, t, mu, np.full(3, c.SIGMA_LAB), c.PRIOR_SD_P1A)
        self.assertAlmostEqual(a.ks, d.ks, places=8)

    def test_fail_closed_inputs(self):
        basis = c.Basis(self.regimens['SL_1mg_q12'], self.shape)
        good_t, sigma = np.array([300.0]), np.array([0.3])
        for y in (np.array([0.0]), np.array([-5.0]), np.array([math.nan]), np.array([math.inf])):
            with self.assertRaises(ValueError):
                c.fit_method('A_sl_only', basis, good_t, y, sigma, 0.5)
        with self.assertRaises(ValueError):
            c.fit_method('A_sl_only', basis, np.array([math.nan]), np.array([50.0]), sigma, 0.5)
        with self.assertRaises(ValueError):
            c.fit_method('A_sl_only', basis, good_t, np.array([50.0]), np.array([0.0]), 0.5)


class PoliciesAndDeterminism(unittest.TestCase):
    def setUp(self):
        self.basis = c.Basis(c.regimens()['SL_1mg_q12'], c.m2_shape())

    def test_exclude_policy_drops_peak_labs_and_can_leave_none(self):
        t = np.array([297.0 + 0.9, 297.0 + 5.0, 309.0 + 1.0])    # 0.9 h and 1.0 h after SL doses, 5 h after one
        keep, _ = study.apply_policy('exclude_2.5h', self.basis, t, np.full(3, 80.0), c.SIGMA_LAB)
        self.assertEqual(list(keep), [1])
        keep, _ = study.apply_policy('exclude_2.5h', self.basis, t[[0, 2]], np.full(2, 80.0), c.SIGMA_LAB)
        self.assertEqual(len(keep), 0)

    def test_delta_policy_downweights_steep_slopes_only(self):
        t = np.array([296.0 + 0.4, 296.0 + 7.0])      # 0.4 h after a dose (steep rise) vs 7 h after it
        _, sigma = study.apply_policy('delta', self.basis, t, np.full(2, 80.0), c.SIGMA_LAB)
        self.assertGreater(sigma[0], 2 * sigma[1])
        self.assertGreater(sigma[1], c.SIGMA_LAB - 1e-12)

    def test_hours_since_last_dose_is_known_or_infinite_never_guessed(self):
        reg = c.regimens()['SL_1mg_q12']
        self.assertTrue(math.isinf(reg.hours_since_last_sl([1.0])[0]))
        self.assertAlmostEqual(reg.hours_since_last_sl([10.0])[0], 2.0)

    def test_a_cell_is_deterministic_and_independent_of_execution_order(self):
        job = ('baseline', {}, 2, 'random', 3, 3, ('all', 'delta'), True)
        first = study.run_cell(job)
        study.run_cell(('baseline', {}, 0, 'peak', 2, 2, ('all',), False))     # unrelated work in between
        self.assertEqual(first, study.run_cell(job))
        self.assertTrue(all(r['users'] == 3 for r in first))

    def test_oracle_has_the_lowest_curve_error_when_nothing_is_misspecified(self):
        rows = study.run_cell(('baseline', {}, 0, 'random', 3, 8, ('all',), False))
        by = {r['method']: r['curve_med_abs_log_median'] for r in rows}
        self.assertLessEqual(by['oracle'], min(by[m] for m in by if m != 'oracle') + 1e-9)
        self.assertGreater(by['uncalibrated'], by['oracle'])


class EligibilityRules(unittest.TestCase):
    def test_every_reason_has_text_in_both_languages_and_codes_are_unique(self):
        self.assertEqual(len(set(rules.REASON_TEXT)), len(rules.REASON_TEXT))
        for code, text in rules.REASON_TEXT.items():
            self.assertTrue(text['zh'].strip() and text['en'].strip(), code)
            self.assertNotIn('剂量调整', text['zh'])                  # never dose advice
            self.assertNotIn('wrong', text['en'].lower())             # never blames the lab

    def test_policy_examples(self):
        ok = dict(time_known_to_minute=True, last_sl_dose_known=True, hours_since_last_sl=6.0, history_gate_passed=True)
        self.assertEqual(rules.assess_lab(**ok).decision, 'USE')
        self.assertEqual(rules.assess_lab(**{**ok, 'time_known_to_minute': False}).reasons, ['LAB_TIME_NOT_PRECISE'])
        self.assertEqual(rules.assess_lab(**{**ok, 'last_sl_dose_known': False, 'hours_since_last_sl': None}).reasons, ['SL_LAST_DOSE_UNKNOWN'])
        self.assertEqual(rules.assess_lab(**{**ok, 'history_gate_passed': False}).reasons, ['HISTORY_NOT_ELIGIBLE'])
        near_peak = rules.assess_lab(**{**ok, 'hours_since_last_sl': 0.9})
        self.assertEqual(near_peak.decision, 'DOWNWEIGHT')
        self.assertEqual(rules.assess_lab(**{**ok, 'hours_since_last_sl': 0.9}, peak_policy='EXCLUDE').decision, 'REJECT')

    def test_set_level_refusals(self):
        self.assertEqual(rules.assess_set(0, 0.9).reasons, ['NO_USABLE_LAB'])
        self.assertEqual(rules.assess_set(3, 0.2).reasons, ['INSUFFICIENT_INFORMATION'])
        self.assertEqual(rules.assess_set(3, 0.8).decision, 'CALIBRATE')


class ResearchGuard(unittest.TestCase):
    def test_no_live_lock_or_frozen_file_was_touched(self):
        cls = guard.classification()
        base = json.loads((ROOT / 'docs/pk-research/p2/production-baseline.json').read_text())
        self.assertEqual([n for n in cls['live_lock_baseline'] if guard.digest(ROOT / n) != base['files'].get(n)], [])
        self.assertEqual([n for n, h in cls['live_lock_additional'].items() if guard.digest(ROOT / n) != h], [])
        self.assertEqual(guard.check()['protocol_sha256'], guard.digest(guard.P2 / 'validation-protocol.md'))

    def test_new_research_files_only_depend_on_protected_production_inputs(self):
        cls = copy.deepcopy(guard.classification())
        cls['dependency_scan']['globs'] = ['tools/pk-research/sl_cal/*.py']
        self.assertEqual(guard.dependency_scan(cls), [])
        probe = ROOT / 'tools/pk-research/sl_cal/_probe_offender.py'
        probe.write_text("X = 'app/src/main/java/net/plainnotes/app/ui/SettingsScreen.kt'\n")
        try:
            with self.assertRaises(AssertionError):
                self.assertEqual(guard.dependency_scan(cls), [])
        finally:
            probe.unlink()
        self.assertEqual(guard.dependency_scan(cls), [])

    def test_research_code_never_imports_production_code_or_the_network(self):
        banned = re.compile(r'^\s*(import|from)\s+(requests|urllib|http|socket|subprocess|sqlite3)\b', re.M)
        for path in (HERE / 'sl_cal').glob('*.py'):
            self.assertIsNone(banned.search(path.read_text()), path.name)

    def test_the_new_files_sit_outside_every_unclassified_production_prefix(self):
        cls = guard.classification()
        prefixes = tuple(cls['unclassified_scan']['prefixes'])
        new = [str(p.relative_to(ROOT)) for p in list((HERE / 'sl_cal').glob('*.py')) + list((ROOT / 'docs/pk-research/sl-cal').rglob('*')) if p.is_file()]
        self.assertTrue(new)
        self.assertEqual([n for n in new if n.startswith(prefixes)], [])
        self.assertEqual(guard.unclassified(cls, json.loads((ROOT / 'docs/pk-research/p2/production-baseline.json').read_text())), [])

    def test_phase_matcher_rejects_production_ui_export_schema_and_workflow_paths(self):
        for bad in ('app/src/main/java/net/plainnotes/app/conc/ConcentrationCalculator.kt', 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/LabFit.kt',
                    'pk-engine/src/main/resources/pk-params.json', 'app/src/main/java/net/plainnotes/app/export/Exports.kt',
                    'app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt', 'core/data/schemas/x/22.json', '.github/workflows/android.yml',
                    'docs/design/UI_STYLE_AUTHORITY.md', 'tools/pk-research/research.py'):
            self.assertEqual(phase.violations([bad]), [bad], bad)
        for good in ('docs/pk-research/sl-cal/sl-cal-01-report.md', 'tools/pk-research/sl_cal/sl_cal_core.py', 'tools/pk-research/test_sl_cal_01.py',
                     'docs/REQUIREMENTS.md', 'docs/HANDOFF.md', 'docs/STATUS.md', 'docs/audits/product-quality-2026-10/78-sl-cal-01-research.md'):
            self.assertEqual(phase.violations([good]), [], good)

    def test_this_phase_branch_touches_only_allowed_paths(self):
        paths = phase.phase_diff_paths()
        head = os.environ.get('GITHUB_HEAD_REF', '')
        if head.startswith('research/sl-cal-01'):
            self.assertIsNotNone(paths, 'phase range must be resolvable on the phase PR')
        if paths is not None:
            self.assertEqual(phase.violations(paths), [])

class OwnerLocalCheck(unittest.TestCase):
    """The optional owner-side script must never read repository data, write anything, or echo individual values."""
    def setUp(self):
        import tempfile
        self.dir = Path(tempfile.mkdtemp(prefix='slcal-owner-'))
        (self.dir / 'doses.csv').write_text('time,mg,route\n' + ''.join(f'2026-10-{1 + i // 2:02d}T{8 + 12 * (i % 2):02d}:00:00+08:00,1.0,SL\n' for i in range(28)))
        (self.dir / 'labs.csv').write_text('time,value,unit\n2026-10-14T09:00:00+08:00,152.5,pg/mL\n2026-10-14T15:30:00+08:00,61.0,pg/mL\n2026-10-13T19:50:00+08:00,48.0,pg/mL\n')

    def test_summary_is_aggregate_only_and_writes_nothing(self):
        import owner_local_check as oc
        before = sorted(p.name for p in self.dir.iterdir())
        text = '\n'.join(oc.summarize(*oc.load(self.dir / 'doses.csv', self.dir / 'labs.csv'), True))
        self.assertEqual(sorted(p.name for p in self.dir.iterdir()), before)
        for secret in ('152.5', '61.0', '48.0', '2026-10', 'T09:00'):
            self.assertNotIn(secret, text)
        self.assertIn('set decision', text)

    def test_without_the_history_statement_nothing_is_calibrated(self):
        import owner_local_check as oc
        text = '\n'.join(oc.summarize(*oc.load(self.dir / 'doses.csv', self.dir / 'labs.csv'), False))
        self.assertIn('HISTORY_NOT_ELIGIBLE', text)
        self.assertIn('no test that can be used', text)

    def test_refuses_repository_paths_and_zone_less_times(self):
        import owner_local_check as oc
        with self.assertRaises(SystemExit):
            oc.outside_repo(ROOT / 'README.md')
        (self.dir / 'bad.csv').write_text('time,mg,route\n2026-10-01T08:00:00,1.0,SL\n')
        with self.assertRaises(SystemExit):
            oc.load(self.dir / 'bad.csv', self.dir / 'labs.csv')


class RecordedResults(unittest.TestCase):
    def test_result_files_are_synthetic_and_claim_no_clinical_accuracy(self):
        for name in ('sl-cal-01-synthetic.json', 'sl-cal-01-identifiability.json', 'sl-cal-01-external-diagnostic.json'):
            doc = json.loads((RESULTS / name).read_text())
            self.assertTrue(doc.get('synthetic_only') or doc.get('not_a_validation'), name)
            self.assertIs(doc.get('clinical_accuracy_established', False), False, name)
            self.assertIs(doc.get('real_health_data_used', False), False, name)

    def test_recorded_study_reproduces_for_a_sample_of_cells(self):
        doc = json.loads((RESULTS / 'sl-cal-01-synthetic.json').read_text())
        cfg = doc['config']
        job = ('baseline', {}, 3, 'random', 5, cfg['users_baseline'], study.POLICIES_BASELINE, True)
        fresh = {(r['policy'], r['method']): r for r in study.run_cell(job)}
        for r in doc['rows']:
            if (r['sweep'], r['regimen'], r['truth'], r['scheme'], r['n_labs']) == ('baseline', 'MIX_SL1mg_q12_oral2mg', 'distinct', 'random', 5):
                new = fresh[(r['policy'], r['method'])]
                for k, v in r.items():
                    if isinstance(v, float):
                        self.assertAlmostEqual(v, new[k], delta=2e-3, msg=f'{k} {r["policy"]} {r["method"]}')


if __name__ == '__main__':
    unittest.main()
