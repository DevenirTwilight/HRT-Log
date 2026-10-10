import copy
import json
import unittest
from pathlib import Path
from p2ao_audit import CATALOG, assess, run, validate_record, verify_catalog

class AuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog=json.loads(CATALOG.read_text(encoding='utf-8'))
        cls.rows=cls.catalog['records']

    def test_catalog_valid(self):self.assertTrue(verify_catalog(self.catalog))
    def test_unique_ids(self):self.assertEqual(len({r['id'] for r in self.rows}),len(self.rows))
    def test_all_sources_verified(self):self.assertTrue(all(r['source_verified'] for r in self.rows))
    def test_none_verified_real_dataset(self):self.assertTrue(all(not r['ipd_bytes_verified'] for r in self.rows))
    def test_zero_external_eligible(self):self.assertEqual(run(self.catalog)['external_validation_eligible_count'],0)
    def test_accuracy_not_evaluated(self):self.assertEqual(run(self.catalog)['model_accuracy_claim'],'NOT_EVALUATED')
    def test_no_duplicate_doll_abstract(self):self.assertEqual(len([r for r in self.rows if r['family']=='doll-nct04036500']),1)
    def test_yaish_pre_exposed(self):self.assertEqual(next(r for r in self.rows if r['id']=='YAISH2023')['prior_exposure'],'known_exposed')
    def test_tlv_unresolved(self):self.assertEqual(next(r for r in self.rows if r['id']=='TLV_NCT07145281')['independence'],'unresolved')
    def test_tlv_osf_unverified(self):self.assertFalse(next(r for r in self.rows if r['id']=='TLV_NCT07145281')['ipd_bytes_verified'])
    def test_maine_sample_2(self):self.assertEqual(next(r for r in self.rows if r['id']=='MAINE_NCT05428215')['sample_size'],2)
    def test_cyclodextrin_not_equivalent(self):self.assertIn('route_or_formulation_not_directly_comparable',assess(next(r for r in self.rows if r['id']=='CYCLODEXTRIN1993'))['blockers'])
    def test_price_not_external(self):self.assertFalse(assess(self.rows[0])['external_score_eligible'])
    def test_repository_claim_not_download(self):
        c=next(r for r in self.rows if r['id']=='YAISH2023');self.assertFalse(assess(c)['external_score_eligible'])
    def test_missing_field_rejected(self):
        c=copy.deepcopy(self.rows[0]);del c['family'];self.assertRaises(ValueError,validate_record,c)
    def test_duplicate_id_rejected(self):
        c=copy.deepcopy(self.catalog);c['records'].append(copy.deepcopy(c['records'][0]));self.assertRaises(ValueError,verify_catalog,c)
    def test_bad_year_dose_not_claimed(self):self.assertFalse(any(r['id']=='DOLL2020_SEPARATE' for r in self.rows))
    def test_bad_exposure_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['prior_exposure']='independent';self.assertRaises(ValueError,validate_record,c)
    def test_bad_independence_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['independence']='maybe';self.assertRaises(ValueError,validate_record,c)
    def test_bad_claim_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['ipd_claim']='downloaded';self.assertRaises(ValueError,validate_record,c)
    def test_bad_url_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['publication']='http://fake';self.assertRaises(ValueError,validate_record,c)
    def test_bad_n_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['sample_size']=0;self.assertRaises(ValueError,validate_record,c)
    def test_bool_n_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['sample_size']=True;self.assertRaises(ValueError,validate_record,c)
    def test_duplicate_times_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['timepoints_h']=[0,1,1,2];self.assertRaises(ValueError,validate_record,c)
    def test_negative_times_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['timepoints_h']=[-1];self.assertRaises(ValueError,validate_record,c)
    def test_fake_verification_field_rejected(self):
        c=copy.deepcopy(self.rows[0]);c['ipd_bytes_verified']='yes';self.assertRaises(ValueError,validate_record,c)
    def test_repository_claim_requires_url(self):
        c=copy.deepcopy(self.rows[0]);c['ipd_claim']='declared_available_repository';self.assertRaises(ValueError,validate_record,c)
    def test_known_exposed_cannot_be_independent(self):
        c=copy.deepcopy(self.rows[0]);c['independence']='verified_independent';self.assertRaises(ValueError,validate_record,c)
    def test_claim_unchanged_when_other_flags(self):
        c=copy.deepcopy(self.rows[0]);c['ipd_bytes_verified']=True;self.assertFalse(assess(c)['external_score_eligible'])
    def test_independence_only_insufficient(self):
        c=copy.deepcopy(self.rows[-2]);c['independence']='verified_independent';self.assertFalse(assess(c)['external_score_eligible'])
    def test_all_data_gates_needed(self):
        c=copy.deepcopy(next(r for r in self.rows if r['id']=='FIET1982'))
        for k in ('ipd_bytes_verified','dose_clock_verified','participant_time_series_verified','timing_after_dose_dense'):c[k]=True
        self.assertFalse(assess(c)['external_score_eligible'])
        c['independence']='verified_independent';self.assertTrue(assess(c)['external_score_eligible'])
    def test_not_listed_in_seven_not_external(self):
        c=next(r for r in self.rows if r['id']=='FIET1982');self.assertFalse(assess(c)['external_score_eligible'])
    def test_source_claim_without_actual_file_zero(self):self.assertEqual(run(self.catalog)['verified_original_dataset_count'],0)
    def test_guardrails_populated(self):self.assertGreaterEqual(len(run(self.catalog)['guardrails']),5)
    def test_study_family_uniqueness_not_proof(self):self.assertIn('not_proof_of_independence', next(k for k in run(self.catalog) if 'families' in k))

if __name__=='__main__':unittest.main()
