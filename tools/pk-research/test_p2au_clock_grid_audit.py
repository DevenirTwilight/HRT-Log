"""All test data here are synthetic. No personal VNC54 records."""
import unittest
from p2au_clock_grid_audit import GAP_CHOICES,clock_grid,score_thresholds

class ClockGridTests(unittest.TestCase):
    def test_grid_count_and_bounds(self):
        c=clock_grid()
        self.assertEqual(len(c),64)
        self.assertEqual(len(set(c)),64)
        self.assertEqual(c[0],(0,3,6,9))
        self.assertIn((0,6,12,18),c)
        self.assertIn((0,4,8,12),c)
        self.assertIn((0,3,6,10),c)
        for clock in c:
            gaps=tuple(b-a for a,b in zip(clock,clock[1:]))
            self.assertTrue(set(gaps)<=set(GAP_CHOICES))
            self.assertTrue(6<=24-clock[-1]<=15)

    def test_invalid_cohort_rejected(self):
        with self.assertRaises(ValueError):score_thresholds({}, {}, {})

    def test_private_identifiers_not_exported(self):
        import json
        from pathlib import Path
        f=Path(__file__).resolve().parents[2]/'docs/pk-research/p2/p2au-clock-grid/aggregate.json'
        result=json.loads(f.read_text())
        text=json.dumps(result)
        self.assertNotIn('patient_id',text)
        self.assertNotIn('SYNTHETIC_',text)
        self.assertEqual(result['clock_grid_size'],64)
        self.assertEqual(result['n_pair_visits'],20)
        self.assertEqual(result['n_distinct_people'],11)
        self.assertFalse(result['schedule_weighting_or_patient_prior'])
        self.assertFalse(result['external_human_validation'])
        self.assertFalse(result['model_replacement_authorized'])

    def test_m2_frozen_grid_shape(self):
        import json
        from pathlib import Path
        r=json.loads((Path(__file__).resolve().parents[2]/'docs/pk-research/p2/p2au-clock-grid/aggregate.json').read_text())
        c=r['m2_class_envelope']
        self.assertEqual(len(c['per_candidate_all20_common_clock_counts']),15)
        self.assertEqual(c['per_candidate_all20_common_clock_counts'], sorted(c['per_candidate_all20_common_clock_counts']))
        self.assertTrue(0<=c['min_compatible_visits_over_all_candidates_all_clocks']<=20)
        self.assertTrue(0<=c['max_compatible_visits_over_all_candidates_all_clocks']<=20)
        self.assertTrue(0<=r['legacy_clock_summary']['all20_common_clock_count']<=64)

if __name__=='__main__':unittest.main()
