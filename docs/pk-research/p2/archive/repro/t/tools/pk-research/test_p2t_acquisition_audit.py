import copy
import json
import unittest
import p2t_acquisition_audit as a

class TestP2TAcquisitionAudit(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ledger = json.loads(a.LEDGER.read_text(encoding='utf-8'))
        cls.o = a.audit(cls.ledger)
        cls.byid = {x['id']:x for x in cls.o['report_time_coverage']}

    def test_research_not_clinically_validated(self):
        self.assertEqual(self.o['retrieved_qualified_pure_sl_subject_series'],0)
        self.assertEqual(self.o['locked_unseen_external_full_sl_pk_cohorts'],0)
        self.assertFalse(self.o['model_upgrade_authorized'])

    def test_preliminary_final_doll_not_double_counted(self):
        self.assertEqual(self.o['multi_report_cohorts']['NCT04036500'],
            ['DOLL_2020_PRELIMINARY','DOLL_2022_FINAL'])

    def test_transprep_not_double_counted(self):
        self.assertEqual(set(self.o['multi_report_cohorts']['NCT03652623']),
             {'YAGER_2022_TRANSPREP','ABDELMAWLA_2023_THESIS'})

    def test_registry_ipd_request_not_equated_to_download(self):
        self.assertEqual(self.o['registered_ipd_contact_leads'],['DOLL_2022_FINAL'])
        self.assertFalse(self.byid['DOLL_2022_FINAL']['subject_series_retrieved'])

    def test_doll_missing_subhour_and_twelve_hour(self):
        d=self.byid['DOLL_2022_FINAL']
        self.assertTrue(d['has_0min_sampling'])
        self.assertFalse(d['has_postdose_early_under_60min'])
        self.assertFalse(d['has_sample_at_or_after_12h'])

    def test_rosano_early_but_no_predose_or_late(self):
        d=self.byid['ROSANO_1997_PK25']
        self.assertTrue(d['has_postdose_early_under_60min'])
        self.assertFalse(d['has_0min_sampling'])
        self.assertFalse(d['has_sample_at_or_after_12h'])

    def test_price_late_but_not_early(self):
        d=self.byid['PRICE_1997']
        self.assertTrue(d['has_sample_at_or_after_12h'])
        self.assertFalse(d['has_postdose_early_under_60min'])

    def test_no_published_schedule_spans_early_and_late(self):
        self.assertEqual(self.o['early_and_late_same_report_count'],0)

    def test_planned_terminated_study_is_not_observed(self):
        d=self.byid['NCT05428215_TERMINATED']
        self.assertFalse(d['has_0min_sampling'])
        self.assertFalse(d['subject_series_retrieved'])

    def test_osf_registry_claim_not_verified_file(self):
        self.assertEqual(self.o['osf_file_lists_confirmed'],[])
        self.assertFalse(self.byid['NCT07145281_OSF_CLAIM']['subject_series_retrieved'])

    def test_chronic_treat_not_serial(self):
        self.assertEqual(self.byid['CORTEZ_2024_TREAT']['published_or_documented_times_min'],[])
        self.assertFalse(self.byid['CORTEZ_2024_TREAT']['usable_unseen_external_holdout'])

    def test_duplicate_report_ids_fail_closed(self):
        d=copy.deepcopy(self.ledger); d['sources'][1]['id']=d['sources'][0]['id']
        with self.assertRaises(ValueError): a.audit(d)

    def test_illegal_sampling_values_fail_closed(self):
        d=copy.deepcopy(self.ledger); d['sources'][0]['timepoints_min']=[0,60,60]
        with self.assertRaises(ValueError): a.audit(d)

    def test_unverified_ipd_acquisition_requires_ledger_revision(self):
        d=copy.deepcopy(self.ledger); d['sources'][1]['actual_pure_sl_subject_series_retrieved']=True
        with self.assertRaises(ValueError): a.audit(d)

if __name__ == '__main__': unittest.main()
