"""P2-AW public, non-personal mathematical and freeze/provenance regression tests."""
import importlib.util
import json
import math
from pathlib import Path
import tempfile
import unittest
import zipfile

from p2aw_sampling_design import (
    ARCHIVE_SHA256, FROZEN_LEGACY_SHA256, FROZEN_M2_SHA256,
    GRID_EARLY, GRID_EXTENDED, affine_profile_distance, frozen_candidates,
    legacy_normalized, m2_normalized, profile_schedule, ranked_one_extra,
    read_inputs, regularized_lower_gamma_integer, run_study, sha256,
)

REPO_ROOT = Path(__file__).resolve().parents[2]
FROZEN_PATH = REPO_ROOT / 'docs/pk-research/p2/history-2026-10-10/source-packages/P2-AS-research-source.zip'
OUTPUT = REPO_ROOT / 'docs/pk-research/p2/p2aw-identifiability-design/p2aw-geometry.json'


class P2AWResearchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.p2x, cls.legacy = read_inputs(FROZEN_PATH)
        cls.m2 = frozen_candidates(cls.p2x)
        cls.result = json.loads(OUTPUT.read_text(encoding='utf-8'))

    def test_original_archive_hash(self):
        self.assertEqual(sha256(FROZEN_PATH.read_bytes()), ARCHIVE_SHA256)

    def test_exact_frozen_inside_json_hashes(self):
        with zipfile.ZipFile(FROZEN_PATH) as z:
            self.assertEqual(sha256(z.read('P2-AS/inputs/p2x-conditional-ensemble-results.json')), FROZEN_M2_SHA256)
            self.assertEqual(sha256(z.read('P2-AS/inputs/legacy_source_snapshot.json')), FROZEN_LEGACY_SHA256)

    def test_archive_mutation_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / 'fake.zip'
            out.write_bytes(b'not original')
            with self.assertRaises(ValueError):
                read_inputs(out)

    def test_unmodified_frozen_selection(self):
        self.assertEqual(len(self.p2x['rows']), 40)
        self.assertEqual(len(self.m2), 15)
        best = self.p2x['summary']['global_best_loss']
        for row in self.p2x['rows']:
            self.assertEqual(row['parameter'] in self.m2, row['score'] <= best + .1 + 1e-10)

    def test_integer_gamma_reference(self):
        self.assertAlmostEqual(regularized_lower_gamma_integer(1, 1), 1 - math.exp(-1))
        self.assertAlmostEqual(regularized_lower_gamma_integer(2, 1), 1 - 2 / math.e)
        for n in (1, 2, 6, 10):
            for z in (0, 1e-8, .25, 1, 24.9, 25, 80):
                v = regularized_lower_gamma_integer(n, z)
                self.assertGreaterEqual(v, 0)
                self.assertLessEqual(v, 1)

    def test_invalid_gamma_rejected(self):
        for n, z in ((0, 1), (3, -1), (3, math.nan)):
            with self.assertRaises(ValueError):
                regularized_lower_gamma_integer(n, z)

    def test_legacy_one_hour_matches_snapshot(self):
        value = self.legacy['E2_SL']['checks']['cmax_1mg']
        self.assertAlmostEqual(value, 144.02380642618826, places=6)
        self.assertAlmostEqual(legacy_normalized(1, self.legacy), 1)
        self.assertEqual(legacy_normalized(0, self.legacy), 0)

    def test_m2_one_hour_normalization(self):
        for p in self.m2:
            self.assertAlmostEqual(m2_normalized(1, p), 1, places=12)
            self.assertEqual(m2_normalized(0, p), 0)
            self.assertGreater(m2_normalized(.25, p), 0)

    def test_invalid_shape_parameters_rejected(self):
        bad = {**self.m2[0], 'k_fast_per_h': 0.1}
        with self.assertRaises(ValueError):
            m2_normalized(2, bad)

    def test_two_point_nuisance_fit_zero_information(self):
        r = profile_schedule((0, 1), self.legacy, self.m2)
        self.assertEqual(r['legacy_v_m2_min'], 0)
        self.assertEqual(r['m2_pairs_min'], 0)
        self.assertEqual(r['m2_pairs_median'], 0)

    def test_affine_invariance_and_symmetry(self):
        a = [0., .3, 1., .5]
        b = [4 + 2*x for x in a]
        self.assertLess(affine_profile_distance(a, b), 5e-8)
        c = [.2, .5, 1., .1]
        self.assertAlmostEqual(affine_profile_distance(a, c), affine_profile_distance(c, a), places=12)

    def test_negative_gain_is_not_free_to_fit(self):
        self.assertEqual(affine_profile_distance([0, 1, 2], [2, 1, 0]), 1)

    def test_bad_shape_sample_rejected(self):
        for times in ((0, 0, 1), (-.2, 1), (1,), (0, 1, 1)):
            with self.assertRaises(ValueError):
                profile_schedule(times, self.legacy, self.m2)

    def test_early_sample_contrast_specific_to_models(self):
        self.assertAlmostEqual(legacy_normalized(.25, self.legacy), .5292096822254967, places=9)
        a = [m2_normalized(.25, p) for p in self.m2]
        self.assertTrue(all(0.05 < x < 0.11 for x in a))

    def test_24h_tail_is_not_identified_by_early_shape(self):
        a = [m2_normalized(24, p) for p in self.m2]
        self.assertGreater(max(a) / min(a), 60)
        self.assertLess(max(a) / min(a), 80)
        self.assertLess(legacy_normalized(24, self.legacy), min(a))

    def test_nominal_best_one_extra_and_jitter_flip(self):
        records = ranked_one_extra(self.legacy, self.m2)
        self.assertEqual(records[0]['hours'][-1], .25)
        self.assertGreater(records[0]['legacy_v_m2_min'], .44)
        self.assertEqual(records[0]['scenario_jitter_pm_15min_worst_min'], 0)
        two = next(x for x in records if x['hours'][-1] == 2)
        self.assertGreater(two['scenario_jitter_pm_15min_worst_min'], .22)
        self.assertGreater(two['scenario_jitter_pm_15min_worst_min'], records[0]['scenario_jitter_pm_15min_worst_min'])

    def test_reproducible_frozen_public_result(self):
        from p2aw_sampling_design import run_study
        new = run_study(self.p2x, self.legacy)
        encoded = json.dumps(new, ensure_ascii=False, indent=2, sort_keys=True, allow_nan=False) + '\n'
        self.assertEqual(encoded.encode(), OUTPUT.read_bytes())
        self.assertEqual(new['independent_new_human_PK_series'], 0)
        self.assertFalse(new['clinical_model_replacement_authorized'])

    def test_public_only_registry_no_personal_health_rows(self):
        data = self.result
        self.assertTrue(data['no_personal_data_used'])
        self.assertEqual(data['frozen_m2_count'], 15)
        self.assertEqual(data['frozen_total_fit_count'], 40)
        serialized = OUTPUT.read_text(encoding='utf-8')
        for forbidden in ('participant_id', 'patient_id', 'date_of_birth', 'individual_E2', 'VNC54_user_rows'):
            self.assertNotIn('"'+forbidden+'"', serialized)

    def test_overlapping_grid_has_no_invented_real_times(self):
        self.assertEqual(GRID_EARLY, GRID_EXTENDED[:len(GRID_EARLY)])
        self.assertEqual(len(GRID_EXTENDED), 12)
        self.assertTrue(self.result['t_hours_are_synthetic_design_grid_not_observed_data'])

    def test_top_three_slots_are_not_all_model_identifying(self):
        v = self.result['three_extra_best_0_8h'][0]
        self.assertEqual(v['hours'], [0., 1., .25, .5, 2.])
        self.assertGreater(v['legacy_v_m2_min'], .48)
        self.assertLess(v['m2_pairs_min'], .01)


if __name__ == '__main__':
    unittest.main()
