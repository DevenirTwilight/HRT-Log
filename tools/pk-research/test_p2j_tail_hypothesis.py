import json
import math
import unittest
import p2j_tail_hypothesis as t

class TailCounterfactual(unittest.TestCase):
    def setUp(self):self.source=json.loads(t.SOURCE.read_text())
    def test_raw_tail_is_near_gap_but_not_an_author_method(self):
        r=t.audit(self.source)
        self.assertAlmostEqual(r["hypothetical_raw_tail_auc_24_infinity_using_author_mean_half_life"],24*18/math.log(2))
        self.assertAlmostEqual(r["hypothetical_raw_auc0_infinity"],2180.7442576640324)
        self.assertFalse(r["author_used_auc_infinity_proven"])
    def test_background_correction_alters_hypothesis(self):
        r=t.audit(self.source)
        self.assertAlmostEqual(r["baseline_corrected_auc0_24_at_hypothetical_background"],1087.5)
        self.assertAlmostEqual(r["baseline_corrected_auc0_infinity_with_hypothetical_background_and_derived_h"],1195.0540828253893)
        self.assertNotAlmostEqual(r["baseline_corrected_auc0_infinity_with_hypothetical_background_and_derived_h"],2109)
    def test_last_two_group_mean_half_lives(self):
        self.assertAlmostEqual(t.end_slope_half_life(25,24,20),18.637702317032335)
        self.assertAlmostEqual(t.end_slope_half_life(25,24,0),101.87848811003349)
    def test_no_new_evidence_or_clinical_claim(self):
        r=t.audit(self.source)
        self.assertTrue(r["not_external_validation"])
        self.assertFalse(r["clinical_accuracy_established"])
    def test_invalid_tail_rejected(self):
        with self.assertRaises(ValueError):t.tail_area(24,0)
        with self.assertRaises(ValueError):t.tail_area(-1,18)
        with self.assertRaises(ValueError):t.tail_area(24,float("nan"))
    def test_baseline_must_leave_positive_terminal_increments(self):
        with self.assertRaises(ValueError):t.end_slope_half_life(25,24,24)
        with self.assertRaises(ValueError):t.end_slope_half_life(25,25,20)
if __name__=="__main__":unittest.main()
