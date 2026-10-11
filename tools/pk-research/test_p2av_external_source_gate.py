"""P2-AV public metadata test suite: all patient values forbidden."""
import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from p2av_external_source_gate import audit

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'docs/pk-research/p2/p2av-external-source-gate'


class P2AVAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = json.loads((BASE / 'p2av-source-registry.json').read_text(encoding='utf-8'))
        cls.out = audit(cls.original['publications'])

    def test_original_cohort_deduplication(self):
        self.assertEqual(self.out['publication_records'], 12)
        self.assertEqual(self.out['unique_canonical_cohorts'], 8)

    def test_treat_protocol_results_congress_same_cohort(self):
        treat = next(c for c in self.out['canonical_cohorts'] if c['cohort_id'] == 'NCT05010707')
        self.assertEqual(len(treat['publication_ids']), 3)

    def test_doll_interim_and_final_same_cohort(self):
        doll = next(c for c in self.out['canonical_cohorts'] if c['cohort_id'] == 'NCT04036500')
        self.assertEqual(len(doll['publication_ids']), 2)

    def test_transprep_reanalysis_not_new_cohort(self):
        transprep = next(c for c in self.out['canonical_cohorts'] if c['cohort_id'] == 'NCT03652623')
        self.assertEqual(len(transprep['publication_ids']), 2)

    def test_treat_months_never_convert_to_hours(self):
        treat = next(r for r in self.out['source_rows'] if r['publication_id'] == 'TREAT_results_2024')
        self.assertEqual(treat['reported_followup_months_not_dose_hours'], [1, 6])
        self.assertEqual(treat['actually_available_postdose_times_h'], [])
        self.assertIn('LESS_THAN_3_ACCESSIBLE_POSTDOSE_TIMES', treat['blockers'])

    def test_doll_planned_sampling_is_not_recovered_observations(self):
        doll = next(r for r in self.out['source_rows'] if r['publication_id'] == 'Doll_article_2022')
        self.assertEqual(doll['planned_postdose_times_h'], [1,2,3,4,6,8])
        self.assertEqual(doll['actually_available_postdose_times_h'], [1])
        self.assertFalse(doll['serial_shape_evaluation_eligible'])

    def test_exposed_group_curves_cannot_become_locked_external(self):
        price = next(r for r in self.out['source_rows'] if r['publication_id'] == 'Price_1997')
        self.assertGreaterEqual(len(price['actually_available_postdose_times_h']), 3)
        self.assertFalse(price['locked_external_shape_evaluation_eligible'])
        self.assertIn('PREVIOUSLY_EXPOSED_TO_MODEL_DEVELOPMENT', price['blockers'])

    def test_no_claim_of_new_independent_human_pk_data(self):
        self.assertEqual(self.out['locked_external_eligible_count'], 0)
        self.assertEqual(self.out['individual_absolute_eligible_count'], 0)
        self.assertEqual(self.out['new_independent_human_PK_profiles_obtained'], 0)
        self.assertFalse(self.out['production_model_replacement_authorized'])

    def test_cannot_borrow_actual_sample_times_from_visit_months(self):
        r = copy.deepcopy(self.original['publications'][1])
        r['usable_postdose_times_hours'] = [1,6]
        with self.assertRaises(ValueError):
            audit([r])

    def test_reject_duplicate_ids(self):
        r = self.original['publications'][0]
        with self.assertRaises(ValueError):
            audit([r,r])

    def test_reject_malformed_clocks(self):
        r = copy.deepcopy(self.original['publications'][0])
        r['sampling_plan_hours'] = [2,1]
        with self.assertRaises(ValueError):
            audit([r])

    def test_synthetic_qualifying_example_is_not_real_subject_evidence(self):
        fake=copy.deepcopy(self.original['publications'][0])
        fake.update(id='SYNTHETIC_ONLY', cohort_id='SYNTHETIC_ONLY',
                    planned_postdose_times_hours=None,
                    sampling_plan_hours=[0, .5, 1, 8],
                    usable_postdose_times_hours=[.5, 1, 8],
                    study_followup_months=[],
                    route_resolution='SL_SEPARATE',
                    assay_verified=True, predose_baseline_available=True,
                    dose_history_verified=True, sample_clock_verified=True,
                    data_reuse_authorized=True,
                    exposed_to_development=False, independence_verified=True,
                    patient_time_series_available=True,
                    holdout_protocol_frozen_before_data_access=True,absolute_scale_precommitted=False)
        out=audit([fake]); self.assertEqual(out['locked_external_eligible_count'],1)
        self.assertEqual(out['long_tail_eligible_count'],1)
        self.assertEqual(out['individual_absolute_eligible_count'],0)
        fake['absolute_scale_precommitted']=True
        self.assertEqual(audit([fake])['individual_absolute_eligible_count'],1)
        fake['exposed_to_development']=True
        out=audit([fake]);self.assertEqual(out['locked_external_eligible_count'],0)

    def test_pooled_route_cannot_be_validated_as_sl(self):
        fake=copy.deepcopy(self.original['publications'][0])
        fake.update(id='SYNTH_POOLED', sampling_plan_hours=[0,1,2,8], usable_postdose_times_hours=[1,2,8],
                    assay_verified=True, predose_baseline_available=True, dose_history_verified=True,
                    sample_clock_verified=True, data_reuse_authorized=True, independent=True,
                    independence_verified=True, exposed_to_development=False,
                    route_resolution='SL_AND_ORAL_POOLED')
        out=audit([fake]);self.assertEqual(out['serial_shape_eligible_count'],0)

    def test_checked_in_output_matches_pure_reanalysis(self):
        expected=json.loads((BASE/'p2av-gate-results.json').read_text(encoding='utf-8'))
        self.assertEqual(expected,self.out)
        forbidden=('patient_id','participant_id','subject_id','dob','birth_date','individual_e2','person_level')
        self.assertFalse(any(k in json.dumps(expected).lower() for k in forbidden))


if __name__ == '__main__':
    unittest.main()
