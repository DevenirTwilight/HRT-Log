import copy
import json
import math
import unittest
import p2j_price_baseline_audit as p
class ReconciliationTests(unittest.TestCase):
    def setUp(self):self.d=json.loads(p.SOURCE.read_text())
    def test_weights(self):
        self.assertEqual(p.weights([0,1,2,3,4,6,8,12,18,24]),[.5,1,1,1,1.5,2,3,5,6,3])
        self.assertEqual(sum(p.weights([0,1,2,3,4,6,8,12,18,24])),24)
    def test_original_manual_scenarios(self):
        a=p.audit(self.d)
        self.assertAlmostEqual(a["manual_figure_auc_if_zero_predose"]["central"],1557.5)
        self.assertAlmostEqual(a["manual_figure_auc_if_zero_predose"]["hi"],1760.5)
        self.assertAlmostEqual(a["same_source_redraw_auc"],1557.5)
    def test_gap(self):
        a=p.audit(self.d)
        self.assertAlmostEqual(a["table_minus_upper_manual_zero_predose"],348.5)
        self.assertAlmostEqual(a["table_minus_central_zero_predose"],551.5)
        self.assertFalse(a["human_source_error_proven"])
    def test_nonnegative_predose_cannot_increase_delta_auc(self):
        rows=self.d["plot_points"]
        self.assertAlmostEqual(p.corrected(rows,"central",0),1557.5)
        self.assertAlmostEqual(p.corrected(rows,"central",20),1087.5)
        self.assertAlmostEqual(p.corrected(rows,"central",0)-p.corrected(rows,"central",20),470)
    def test_required_baseline_is_negative(self):
        a=p.audit(self.d)
        self.assertAlmostEqual(a["predose_needed_to_match_central_if_same_source"],-551.5/23.5)
        self.assertLess(a["predose_needed_to_match_central_if_same_source"],0)
    def test_linearity_of_mean_and_auc(self):
        base=self.d["plot_points"];lo=copy.deepcopy(base);hi=copy.deepcopy(base)
        for r in lo:r["central"]*=.9
        for r in hi:r["central"]*=1.1
        self.assertAlmostEqual((p.corrected(lo,"central",0)+p.corrected(hi,"central",0))/2,p.corrected(base,"central",0))
    def test_invalid_baseline_and_duplicate_time(self):
        with self.assertRaises(ValueError):p.corrected(self.d["plot_points"],"central",-1)
        with self.assertRaises(ValueError):p.weights([0,1,1,24])
    def test_nan_and_broken_manual_interval(self):
        d=copy.deepcopy(self.d);d["plot_points"][4]["central"]=math.nan
        with self.assertRaises(ValueError):p.corrected(d["plot_points"],"central",0)
        d=copy.deepcopy(self.d);d["plot_points"][4]["lo"]=999
        with self.assertRaises(ValueError):p.audit(d)
if __name__=="__main__":unittest.main()
