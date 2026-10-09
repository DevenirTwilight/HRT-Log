import json
import copy
import unittest
from sublingual_p1c1_compare import ROOT,validate
class CausalEvidenceTest(unittest.TestCase):
 def load(self):
  r=ROOT/'docs/pk-research/results'
  return tuple(json.loads((r/f'sublingual-p1c1-{key}.json').read_text()) for key in ['before','after'])
 def test_actual_qualified_future_reproduction_and_repair(self):
  a,b=self.load();self.assertLess(validate(a,b),1e-7)
 def test_disabling_labs_or_hiding_raw_points_cannot_pass(self):
  for key in ['qualified_count','raw_count','after_sample_count','summary_count','values_center_p5_p25_p75_p95']:
   a,b=self.load();b=copy.deepcopy(b);r=next(x for x in b['cases'] if x['id']=='future400')
   if key=='summary_count':r[key]=1
   elif key=='values_center_p5_p25_p75_p95':r[key][4]+=1
   else:r[key]=0
   with self.assertRaises(AssertionError):validate(a,b)
