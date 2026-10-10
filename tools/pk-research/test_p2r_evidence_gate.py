import copy,json,math,unittest
import p2r_evidence_gate as g
def fixture(kind="M1_TRANSIT"):
    pol=json.loads(g.POLICY.read_text())
    sources=[]
    scores=[]
    for i in range(2):
        cid="FUTURE_SYNTHETIC_FIXTURE_"+str(i)
        src={"cohort_id":cid,"exposure_status":"LOCKED_EXTERNAL_UNSEEN",
             "independently_enrolled":True,"data_hash":"test-hash-"+str(i),
             "access_log_id":"test-access-"+str(i),
             "protocol_lock_hash":"test-lock-"+str(i)}
        src.update({key:True for key in pol["required_data_fields"]})
        sources.append(src)
        scores.append({"cohort_id":cid,"primary_relative_improvement":.13,
          "primary_improvement_uncertainty_lower":.01,
          "other_target_relative_degradation":.02,
          "subject_clustered_uncertainty_audited":True,
          "baseline_assay_and_BLQ_sensitivity_passed":True,
          "late_AUC_and_real_repeat_trough_each_improved":True})
    return {"from_model":"M0_ONE_INPUT" if kind=="M1_TRANSIT" else "M1_TRANSIT",
      "target":kind,"training_data_subject_level_audited":True,
      "candidate_math_verified":True,"simpler_baseline_model_frozen":True,
      "identifiability_profile_and_symmetries_audited":True,
      "sample_size_precision_reviewed":True,
      "protocol":{key:True for key in pol["required_locked_protocol_fields"]},
      "early_prepeak_postpeak_data_audited":True,
      "M0_noise_and_baseline_alternatives_checked":True,
      "true_late_single_dose_data":True,"actual_repeat_dose_trough_series":True,
      "absorption_vs_elimination_alternatives_tested":True,
      "external_cohorts":sources,"heldout_study_scores":scores,
      "independent_scientific_review_signed":True}
class EvidenceGate(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
    cls.ledger=json.loads(g.LEDGER.read_text())
    cls.policy=json.loads(g.POLICY.read_text())
 def check(self,p):return g.evaluate(self.ledger,self.policy,p)
 def test_real_sources_zero_unseen_and_cohort_dedup(self):
    now=g.current_state(self.ledger)
    self.assertEqual((now["source_reports"],now["distinct_named_cohort_ids"]), (9,8))
    self.assertEqual(now["unseen_verified_heldout_PK_cohorts"],0)
    self.assertFalse(now["production_release_authorized"])
 def test_mock_complete_transit_never_automatically_approved(self):
    o=self.check(fixture())
    self.assertEqual(o["review_readiness"],"EVIDENCE_PACKET_READY_FOR_HUMAN_REVIEW_NOT_APPROVAL")
    self.assertFalse(o["production_release_authorized"])
 def test_mock_complete_dual_never_automatically_approved(self):
    o=self.check(fixture("M2_DUAL_INPUT"))
    self.assertEqual(o["review_readiness"],"EVIDENCE_PACKET_READY_FOR_HUMAN_REVIEW_NOT_APPROVAL")
 def test_duplicate_cohort_blocked(self):
    p=fixture();p["external_cohorts"][1]["cohort_id"]=p["external_cohorts"][0]["cohort_id"]
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
 def test_existing_transprep_cohort_is_not_external(self):
    p=fixture()
    p["external_cohorts"][0]["cohort_id"]="NCT03652623"
    self.assertEqual(self.check(p)["qualified_heldout_cohorts"],1)
 def test_unknown_assay_or_blq_blocks(self):
    p=fixture();p["external_cohorts"][0]["assay_QC_and_LLOQ_traceable"]=False
    self.assertEqual(self.check(p)["qualified_heldout_cohorts"],1)
 def test_baseline_absent_blocks(self):
    p=fixture();p["external_cohorts"][0]["baseline_and_prior_doses_recorded"]=False
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
 def test_protocol_late_lock_blocks(self):
    p=fixture();p["protocol"]["timestamped_lock_before_access"]=False
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
 def test_transit_needs_early_shape_data(self):
    p=fixture();p["early_prepeak_postpeak_data_audited"]=False
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
 def test_dual_needs_actual_trough(self):
    p=fixture("M2_DUAL_INPUT")
    p["heldout_study_scores"][0]["late_AUC_and_real_repeat_trough_each_improved"]=False
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
 def test_negative_ci_or_small_gain_blocks(self):
    p=fixture();p["heldout_study_scores"][0]["primary_improvement_uncertainty_lower"]=-.1
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
    p=fixture();p["heldout_study_scores"][0]["primary_relative_improvement"]=.03
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
 def test_guardrail_worsening_blocks(self):
    p=fixture();p["heldout_study_scores"][1]["other_target_relative_degradation"]=.08
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
 def test_nan_cannot_satisfy_gain(self):
    p=fixture();p["heldout_study_scores"][0]["primary_relative_improvement"]=math.nan
    self.assertEqual(self.check(p)["review_readiness"],"BLOCKED_EXPLORATORY_ONLY")
 def test_no_direct_M0_to_M2_jump(self):
    p=fixture("M2_DUAL_INPUT");p["from_model"]="M0_ONE_INPUT"
    with self.assertRaises(ValueError):self.check(p)
if __name__=="__main__":unittest.main()
