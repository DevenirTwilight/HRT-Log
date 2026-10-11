"""Only synthetic fixtures; never includes participant E2 rows or identifiers."""
import json
import math
import unittest
from pathlib import Path
from p2ar_real_visit_audit import (
    SCHEDULES, analyze, legacy_normalized, lower_gamma_integer,
    m2_normalized, response_pre_post, subject_visits_from_grid, visit_profile,
)

DUMMY_M2 = {
    'family':'M2','n_fast':9,'k_fast_per_h':14.4401992388,
    'k_elim_per_h':1.3518070626,'k_slow_per_h':0.2499701211,
    'effective_slow_weight':0.5139380957,
}
DUMMY_LEGACY = {
    'E2_SL':{'swallowed_share':0.1,'ka_per_h':2.,'terms':[{'A_per_mg':1.,'lambda_per_h':0.5}]},
    'E2_ORAL':{'ka_per_h':2.,'terms':[{'A_per_mg':0.2,'lambda_per_h':0.05}]},
}


class RealVisitAuditTests(unittest.TestCase):
    def test_lower_incomplete_gamma_bounds(self):
        for n in (1,2,4,9,10):
            for z in (0.,1e-6,0.1,1.,24.9,25.,100.):
                val=lower_gamma_integer(n,z)
                self.assertGreaterEqual(val,0.)
                self.assertLessEqual(val,1.)
        self.assertAlmostEqual(lower_gamma_integer(1,1.),1.-math.exp(-1.))
        self.assertAlmostEqual(lower_gamma_integer(2,1.),1.-2/math.e)

    def test_invalid_gamma(self):
        for n,z in ((0,1.),(2,-1.),(2,math.nan)):
            with self.assertRaises(ValueError):lower_gamma_integer(n,z)

    def test_m2_1h_normalized(self):
        self.assertAlmostEqual(m2_normalized(1.,DUMMY_M2),1.)
        self.assertEqual(m2_normalized(-1,DUMMY_M2),0.)
        self.assertGreater(m2_normalized(.25,DUMMY_M2),0.)
        self.assertGreater(m2_normalized(24,DUMMY_M2),0.)

    def test_invalid_frozen_candidate(self):
        bad={**DUMMY_M2,'k_fast_per_h':1.0}
        with self.assertRaises(ValueError):m2_normalized(1.5,bad)

    def test_legacy_1h_normalized(self):
        self.assertAlmostEqual(legacy_normalized(1.,DUMMY_LEGACY),1.)
        self.assertEqual(legacy_normalized(-1.,DUMMY_LEGACY),0.)

    def test_two_point_noiseless_profile_feasible(self):
        a=visit_profile(120,350,.25,1.4)
        b=visit_profile(120,350,.50,1.8)
        self.assertTrue(a['nonnegative_background_feasible'])
        self.assertTrue(b['nonnegative_background_feasible'])
        self.assertNotAlmostEqual(a['gain'],b['gain'])
        for x,spre,spost in ((a,.25,1.4),(b,.5,1.8)):
            self.assertAlmostEqual(x['background']+x['gain']*spre,120.)
            self.assertAlmostEqual(x['background']+x['gain']*spost,350.)

    def test_two_point_negative_background_rejected(self):
        c=visit_profile(120.,350.,.75,2.0)
        self.assertFalse(c['nonnegative_background_feasible'])
        self.assertLess(c['background'],0.)

    def test_reject_nonincreasing_pairs(self):
        for args in ((100,100,.1,.5),(100,90,.1,.5),(100,150,.2,.2),(-2,150,.2,.5)):
            with self.assertRaises(ValueError):visit_profile(*args)

    def test_five_schedules_are_explicitly_hypothetical(self):
        self.assertEqual(len(SCHEDULES),5)
        self.assertEqual(SCHEDULES['every_6h'],(0,6,12,18))
        self.assertEqual(SCHEDULES['night_gap_14h'],(0,3,6,10))
        for clock in SCHEDULES.values():
            before,after=response_pre_post(lambda t:m2_normalized(t,DUMMY_M2),clock)
            self.assertGreaterEqual(before,0.)
            self.assertGreater(after,before)

    def test_bad_schedule_rejected(self):
        with self.assertRaises(ValueError):response_pre_post(lambda t:.5,(0,6,12,24))

    def _synthetic_people(self):
        header=[None]*130
        for index,name in {0:'Pt #',2:'Tx',36:'Tx',75:'3-E2 ',76:'3-E2 post',78:'Tx',120:'6-E2 ',121:'6-post E2'}.items():header[index]=name
        records=[header]
        for i in range(22):
            row=[None]*130;row[0]=f'SYNTHETIC_NONHUMAN_{i}'; row[2]=row[36]=row[78]=1 if i<11 else 0
            if i<9:row[75]=100.;row[76]=400.
            if i<11:row[120]=100.;row[121]=400.
            records.append(row)
        people=subject_visits_from_grid(records)
        self.assertEqual(len(people),11)
        self.assertEqual(sum(len(v) for v in people.values()),20)
        return people

    def test_loader_strict_schema_no_real_records(self):
        people=self._synthetic_people()
        self.assertEqual(len(people),11)

    def test_public_aggregate_does_not_export_row_keys(self):
        people=self._synthetic_people()
        ensemble={'rows':[{'score':0.,'parameter':DUMMY_M2} for _ in range(15)],'summary':{'global_best_loss':0.}}
        result=analyze(people,ensemble,DUMMY_LEGACY,'synthetic_sha256_placeholder')
        text=json.dumps(result)
        self.assertNotIn('SYNTHETIC_NONHUMAN_',text)
        self.assertEqual(result['paired_visits'],20)
        self.assertEqual(result['paired_unique_people'],11)
        self.assertTrue(result['no_patient_rows_or_identifiers_exported'])
        self.assertFalse(result['independent_external_clinical_validation'])


    def test_committed_public_aggregate_provenance_and_gates(self):
        root=Path(__file__).resolve().parents[2]
        f=root/'docs/pk-research/p2/p2ar-real/p2ar-real-aggregate.json'
        d=json.loads(f.read_text(encoding='utf-8'))
        self.assertEqual(d['source_vnc54_xlsx_sha256'],
            '038a935f699ddadc9f0d843e125e5ee8047132773d1d6485085d6302066867dd')
        self.assertEqual(d['paired_visits'],20)
        self.assertEqual(d['paired_unique_people'],11)
        self.assertEqual(d['model_candidates_frozen_before_this_audit'],15)
        self.assertEqual(len(d['hypothetical_schedule_results']),5)
        self.assertTrue(d['no_patient_rows_or_identifiers_exported'])
        self.assertFalse(d['independent_external_clinical_validation'])
        self.assertFalse(d['model_production_replacement_authorized'])
        text=f.read_text(encoding='utf-8')
        for personal_key in ('patient_id','subject_id','participant_id','birth_date','married','height_cm','individual_measurements'):
            self.assertNotIn('"'+personal_key+'"',text)


if __name__=='__main__':unittest.main()
