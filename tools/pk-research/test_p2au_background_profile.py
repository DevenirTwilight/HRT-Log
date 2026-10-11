"""P2-AU tests use synthetic, non-human observations and public aggregates only."""
from __future__ import annotations

import json
import math
from pathlib import Path
import unittest

from p2au_background_profile import (
    BUDGETS, EXPECTED_LEGACY_SHA256, EXPECTED_P2X_SHA256,
    compatible, required_background_share, scenario_model_counts,
)

DUMMY_M2 = {
    'n_fast':9,'k_fast_per_h':14.4401992388,
    'k_elim_per_h':1.3518070626,'k_slow_per_h':0.2499701211,
    'effective_slow_weight':0.5139380957,
}
DUMMY_LEGACY = {
    'E2_SL':{'swallowed_share':0.1,'ka_per_h':2.,'terms':[{'A_per_mg':1.,'lambda_per_h':0.5}]},
    'E2_ORAL':{'ka_per_h':2.,'terms':[{'A_per_mg':0.2,'lambda_per_h':0.05}]},
}


class BackgroundProfileTests(unittest.TestCase):
    def test_exact_algebraic_solution(self):
        pre,post,sp,so=120.,350.,0.25,1.4
        alpha=required_background_share(pre,post,sp,so)
        gain=(post-pre)/(so-sp)
        background=pre-gain*sp
        self.assertAlmostEqual(alpha,background/pre)
        self.assertAlmostEqual(background+gain*sp,pre)
        self.assertAlmostEqual(background+gain*so,post)

    def test_infeasible_negative_background_rejected(self):
        self.assertIsNone(required_background_share(120,350,.75,2))

    def test_zero_carryover_null_requires_all_predose_as_background(self):
        for pre,post in ((10,20),(100,150),(1,1.1)):
            self.assertEqual(required_background_share(pre,post,0,1),1.)

    def test_zero_background_requires_precise_ratio(self):
        self.assertAlmostEqual(required_background_share(100,200,1,2),0.)

    def test_alpha_budget_reverses_feasibility(self):
        a=required_background_share(120,350,.25,1.4)
        self.assertTrue(compatible(a,1.))
        self.assertFalse(compatible(a,0.1))

    def test_budget_monotonic_for_fixed_response(self):
        a=required_background_share(120,350,.25,1.4)
        outcomes=[compatible(a,b) for b in BUDGETS]
        self.assertEqual(outcomes,sorted(outcomes))

    def test_invalid_data_rejected(self):
        for args in ((0,10,0,1),(1,1,0,1),(5,3,0,1),(1,2,2,1),(1,2,-1,1),
                     (math.nan,2,0,1),(1,math.inf,0,1)):
            with self.assertRaises(ValueError):required_background_share(*args)

    def test_invalid_budget_rejected(self):
        for b in (-0.1,1.1):
            with self.assertRaises(ValueError):compatible(0.3,b)

    def _synthetic_people(self):
        # Made-up values; no actual people, no medical data from source files.
        return {f'NONHUMAN_FAKE_{i}':[(3,100.,300.),(6,100.,400.)] if i<9 else [(6,100.,400.)] for i in range(11)}

    def _synthetic_parameters(self):
        return {'rows':[{'score':0.,'parameter':DUMMY_M2} for _ in range(15)],
                'summary':{'global_best_loss':0.}}

    def test_wrong_roster_rejected(self):
        with self.assertRaises(ValueError):scenario_model_counts({},self._synthetic_parameters(),DUMMY_LEGACY)

    def test_wrong_candidate_count_rejected(self):
        p=self._synthetic_parameters();p['rows'].pop()
        with self.assertRaises(ValueError):scenario_model_counts(self._synthetic_people(),p,DUMMY_LEGACY)

    def test_bad_observed_sign_rejected(self):
        p=self._synthetic_people();p['NONHUMAN_FAKE_0']=[(3,500.,100.),(6,100.,400.)]
        with self.assertRaises(ValueError):scenario_model_counts(p,self._synthetic_parameters(),DUMMY_LEGACY)

    def test_synthetic_aggregates_only_and_envelope(self):
        d=scenario_model_counts(self._synthetic_people(),self._synthetic_parameters(),DUMMY_LEGACY)
        text=json.dumps(d,ensure_ascii=False)
        self.assertNotIn('NONHUMAN_FAKE',text)
        self.assertEqual(d['frozen_m2_candidate_count'],15)
        self.assertEqual(len(d['scenarios']),5)
        self.assertEqual(len(d['cross_scenario_envelopes']),5)
        for stage in d['scenarios'].values():
            last=-1
            for b in BUDGETS:
                v=stage['required_background_upper_bounds'][f'{b:.2f}']
                self.assertGreaterEqual(v['legacy_compatible_visits'],last)
                last=v['legacy_compatible_visits']
                self.assertLessEqual(v['m2_compatible_visits_min'],v['m2_compatible_visits_max'])
        self.assertFalse(d['production_replacement_authorized'])
        self.assertFalse(d['independent_human_external_pk_validation'])

    def test_public_result_provenance_privacy_and_against_legacy_p2ar(self):
        root=Path(__file__).resolve().parents[2]
        p=root/'docs/pk-research/p2/p2au-background-profile/p2au-aggregates.json'
        d=json.loads(p.read_text(encoding='utf-8'))
        old=json.loads((root/'docs/pk-research/p2/p2ar-real/p2ar-real-aggregate.json').read_text(encoding='utf-8'))
        self.assertEqual(d['frozen_p2x_json_sha256'],EXPECTED_P2X_SHA256)
        self.assertEqual(d['frozen_legacy_snapshot_sha256'],EXPECTED_LEGACY_SHA256)
        self.assertEqual(d['paired_unique_people'],11)
        self.assertEqual(d['paired_visits'],20)
        self.assertTrue(d['background_budget_is_assumption_not_measured_endogenous_fraction'])
        self.assertFalse(d['independent_human_external_pk_validation'])
        for name,items in d['scenarios'].items():
            v=items['required_background_upper_bounds']['1.00']
            prior=old['hypothetical_schedule_results'][name]
            self.assertEqual(v['legacy_compatible_visits'],prior['legacy_compatible_visits'])
            self.assertEqual(v['m2_compatible_visits_min'],prior['m2_compatible_visits_min'])
            self.assertEqual(v['m2_compatible_visits_max'],prior['m2_compatible_visits_max'])
        for key in ('patient_id','subject_id','participant_id','visit_rows','pre_post_ratios','individual_measurements','birth_date'):
            self.assertNotIn('"'+key+'"',p.read_text(encoding='utf-8'))


if __name__=='__main__':unittest.main()
