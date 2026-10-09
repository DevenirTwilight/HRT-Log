"""P2-I arithmetic tests, synthetic examples and public figure approximations."""
import json
import unittest
import math
import p2i_price_figure_audit as p

class FigureAuditTest(unittest.TestCase):
    def test_24h_constant_area(self):
        self.assertAlmostEqual(p.trapezoid([{'t':0,'v':7},{'t':24,'v':7}],'v'),168)
    def test_linearity_of_mean_and_auc(self):
        one=[{'t':0,'v':0},{'t':1,'v':15},{'t':24,'v':5}]
        two=[{'t':0,'v':2},{'t':1,'v':7},{'t':24,'v':10}]
        means=[{'t':a['t'],'v':(a['v']+b['v'])/2} for a,b in zip(one,two)]
        self.assertAlmostEqual(p.trapezoid(means,'v'),(p.trapezoid(one,'v')+p.trapezoid(two,'v'))/2)
    def test_baseline_trapezoid_identity(self):
        raw=[{'t':t,'v':v} for t,v in [(0,30),(1,150),(24,40)]]
        adj=[{'t':r['t'],'v':r['v']-20} for r in raw]
        self.assertAlmostEqual(p.trapezoid(raw,'v')-p.trapezoid(adj,'v'),480)
    def test_chart_comparisons_do_not_claim_validation(self):
        d=json.loads(p.SOURCE.read_text())
        z=p.audit(d)
        self.assertEqual(z['manual_raw_figure_auc_central'],1567.5)
        self.assertEqual(z['manual_raw_figure_auc_sensitivity_upper'],1785.5)
        self.assertEqual(z['commons_same_source_digitized_approx_auc'],1557.5)
        self.assertGreater(z['table_minus_manual_upper'],0)
        self.assertFalse(z['proof_of_paper_error'])
        self.assertFalse(z['clinical_accuracy_established'])
    def test_bad_bounds(self):
        d=json.loads(p.SOURCE.read_text())
        d['plot_points'][2]['lo']=999
        with self.assertRaises(ValueError):p.audit(d)
    def test_no_synthetic_baseline_as_real_source(self):
        d=json.loads(p.SOURCE.read_text())
        self.assertEqual(d['plot_points'][0]['kind'],'UNPLOTTED_PREDOSE_SENSITIVITY_ONLY')
        self.assertFalse(d['wikimedia_2019_redraw']['independent_human_evidence'])
    def test_missing_time_and_duplicate(self):
        with self.assertRaises(ValueError):p.trapezoid([{'t':1,'v':1},{'t':24,'v':2}],'v')
        with self.assertRaises(ValueError):p.trapezoid([{'t':0,'v':1},{'t':0,'v':2},{'t':24,'v':3}],'v')
    def test_nan_rejection(self):
        with self.assertRaises(ValueError):p.trapezoid([{'t':0,'v':2},{'t':24,'v':math.nan}],'v')
if __name__=='__main__':unittest.main()
