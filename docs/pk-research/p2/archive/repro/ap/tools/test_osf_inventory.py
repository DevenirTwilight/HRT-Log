import unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from osf_inventory import traverse_pages, extract_files,inventory,BASE
class Tests(unittest.TestCase):
 def test_two_pages(self):
  a={'a':{'data':[{'id':'a'}],'links':{'next':'b'}},'b':{'data':[{'id':'b'}],'links':{'next':None}}}
  self.assertEqual(len(traverse_pages('a',a.__getitem__)),2)
 def test_cycle_rejected(self):
  with self.assertRaises(ValueError):traverse_pages('a',lambda _: {'data':[],'links':{'next':'a'}})
 def test_missing_data_rejected(self):
  with self.assertRaises(ValueError):traverse_pages('a',lambda _:{'data':{}})
 def test_empty_list(self):
  self.assertEqual(traverse_pages('a',lambda _:{'data':[]}),[])
 def test_missing_name(self):
  with self.assertRaises(ValueError):extract_files([{'attributes':{'kind':'file'}}])
 def test_invalid_kind(self):
  with self.assertRaises(ValueError):extract_files([{'attributes':{'name':'x','kind':'odd'}}])
 def test_file_info(self):
  result=extract_files([{'id':'f','attributes':{'name':'foo.csv','kind':'file','size':100},'links':{'download':'https://x'}}]);self.assertEqual(result[0]['size_bytes'],100);self.assertEqual(result[0]['path'],'foo.csv')
 def test_no_files_does_not_imply_pk_usable(self):
  self.assertEqual(extract_files([]),[])
 def test_metadata_pass(self):
  start=BASE+'/nodes/vnc54/files/';url='https://fake/files/'
  p={start:{'data':[{'links':{'files':url}}]},url:{'data':[{'id':'x','attributes':{'kind':'file','name':'data.csv','size':42}}]}}
  res=inventory('vnc54',p.__getitem__);self.assertEqual(res['file_count'],1);self.assertEqual(res['eligibility'],'unassessed_requires_file_and_cohort_review')
if __name__=='__main__':unittest.main()
