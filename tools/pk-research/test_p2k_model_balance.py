import json
import unittest
import p2k_model_balance as p
class CrossSourceBalance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.out=p.audit(*[json.loads(path.read_text()) for path in (p.FIG,p.PRICE,p.PARAMS,p.META)])
    def test_price_all_three_doses_favor_featherline_auc(self):
        rows=self.out["source_table_price_sampled_AUC"]
        self.assertEqual(len(rows),3)
        self.assertTrue(all(r["Featherline_is_closer"] for r in rows))
    def test_price_one_mg_auditable_values(self):
        row=self.out["source_table_price_sampled_AUC"][0]
        self.assertAlmostEqual(row["HRT"],367.54,places=1)
        self.assertAlmostEqual(row["Featherline"],2026.5,places=1)
        self.assertEqual(row["observed_mean"],2109)
    def test_doll_training_does_not_prove_universal_model(self):
        item=self.out["dose1_one_hour_increment_predictions"]
        self.assertAlmostEqual(item["HRT"],144,delta=.1)
        self.assertAlmostEqual(item["Featherline"],480.76,delta=.1)
        self.assertEqual(item["Doll_total_observed"],144)
    def test_baseline_sensitivity_flips_figure_mae(self):
        one,two,three=self.out["price_figure_nine_point_baseline_sensitivity"]
        self.assertLess(one["Featherline_MAE"],one["HRT_MAE"])
        self.assertGreater(two["Featherline_MAE"],two["HRT_MAE"])
        self.assertGreater(three["Featherline_MAE"],three["HRT_MAE"])
    def test_both_fail_rosano_early_rise_ratio(self):
        q=self.out["rosano_increment_40_over_20"]
        self.assertGreater(q["observed_raw_40_over_20"],4)
        self.assertLess(q["HRT"],2)
        self.assertLess(q["Featherline"],2)
    def test_no_blind_validation_claim(self):
        self.assertFalse(self.out["clinical_accuracy_established"])
        self.assertFalse(self.out["model_replacement_authorized"])
        self.assertEqual(self.out["new_independent_locked_external_human_cohorts"],0)
if __name__=="__main__":unittest.main()
