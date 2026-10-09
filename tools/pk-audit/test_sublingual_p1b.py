"""Validate actual archived inputs and reject regressions; no model-generated human evidence."""
import copy
import json
import unittest
from sublingual_p1b_compare import ROOT, validate

class EligibilityReportTest(unittest.TestCase):
    def load(self):
        root=ROOT/'docs/pk-research/results'
        return tuple(json.loads((root/f'sublingual-p1b-{key}.json').read_text()) for key in ['engine','calculator','eligibility'])
    def test_actual_software_report_separates_observation_qualification_and_baseline(self):
        e,c,g=self.load();self.assertLess(validate(e,c,g),1e-6)
        self.assertTrue(g['synthetic_only']);self.assertEqual(g['calculator_version'],2)
    def test_a_fabricated_baseline_or_removed_observation_cannot_pass(self):
        for mutation in ['baseline','observation']:
            e,c,g=self.load();g=copy.deepcopy(g)
            row=next(r for r in g['cases'] if r['id']=='old_treatment_lab')
            if mutation=='baseline':row['baseline_pg_ml']=500
            else:row['original_observations']=0
            with self.assertRaises(AssertionError):validate(e,c,g)
