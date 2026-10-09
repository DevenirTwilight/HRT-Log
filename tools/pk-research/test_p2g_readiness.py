import unittest
import p2g_readiness as m

def mock(**overrides):
    row={"analyte":"E2","route":"SUBLINGUAL","assay_verified":True,
      "dose_event_history_verified":True,"dose_time_origin_verified":True,
      "sampling_times_verified":True,"time_concentration_series_verified":True,
      "baseline_status":"VERIFIED_OR_EXPLICITLY_MODELED","data_reuse_permission":"PERMITTED",
      "independent_cohort_confirmed":True,"protocol_frozen_before_access":True,
      "previously_exposed":False,"canonical_cohort_id":"synthetic_independent",
      "source_locator":"SYNTHETIC TEST CASE NOT REAL DATA"}
    row.update(overrides)
    return row

class ReadinessTest(unittest.TestCase):
    def test_complete_synthetic_intake_only_allows_review(self):
        r=m.intake_gate(mock())
        self.assertTrue(r["eligible_for_separate_external_review"])
        self.assertFalse(r["clinical_accuracy_established"])
        self.assertFalse(r["does_not_authorize_population_model_update"]==False)
    def test_po_sl_pooled_or_chronic_summary_not_fit(self):
        r=m.intake_gate(mock(route="PO_AND_SUBLINGUAL_POOLED",time_concentration_series_verified=False))
        self.assertIn("route",r["blockers"])
        self.assertIn("time_concentration_series_verified",r["blockers"])
    def test_publication_already_seen_is_not_new_holdout(self):
        r=m.intake_gate(mock(previously_exposed=True))
        self.assertFalse(r["eligible_for_separate_external_review"])
        self.assertIn("previously_exposed",r["blockers"])
    def test_no_permission_no_publishable_data(self):
        self.assertIn("data_reuse_permission",m.intake_gate(mock(data_reuse_permission="UNKNOWN"))["blockers"])
    def test_assay_and_predose_information_cannot_be_inferred(self):
        r=m.intake_gate(mock(assay_verified=False,baseline_status="UNKNOWN",dose_or_time_inferred_from_summary=True))
        self.assertIn("assay_verified",r["blockers"])
        self.assertIn("baseline_status",r["blockers"])
        self.assertIn("dose_or_time_inferred_from_summary",r["blockers"])
    def test_real_registry_study_deduplication(self):
        import json
        j=json.loads(m.DEFAULT.read_text())
        r=m.audit_registry(j)
        self.assertEqual(r["distinct_cohorts"],4)
        self.assertEqual(r["new_independent_locked_external_human_datasets"],0)
        self.assertEqual(len(r["duplicate_publications_by_cohort"]["NCT03652623"]),2)
    def test_duplication_fails_closed(self):
        import json
        j=json.loads(m.DEFAULT.read_text())
        j["studies"].append(j["studies"][0].copy())
        with self.assertRaises(ValueError):m.audit_registry(j)

if __name__=="__main__":unittest.main()
