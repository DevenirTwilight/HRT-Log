import unittest,json,copy
from sublingual_p1c2_compare import ROOT,validate
class DispositionEvidenceTest(unittest.TestCase):
 def data(self):
  p=ROOT/'docs/pk-research/results';a=json.loads((p/'sublingual-p1c2-before.json').read_text());b=json.loads((p/'sublingual-p1c2-after.json').read_text());return a,b
 def test_actual_engine_app_disposition_and_subset_oracle(self):
  a,b=self.data();self.assertTrue(validate(a,b['engine'],b['application']))
 def test_labels_or_disabling_calibration_cannot_fake_acceptance(self):
  for key in ['used','excluded','warnings','count','covariance']:
   a,b=self.data();b=copy.deepcopy(b);row=b['engine']['cases'][0]
   if key=='count':row[key]=0
   elif key=='covariance':row[key][0]+=1
   elif key=='excluded':row[key]=['tail']
   else:row[key]=[]
   with self.assertRaises(AssertionError):validate(a,b['engine'],b['application'])
