import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import audit_osf_estradiol as a


def synthetic_vnc():
    h=[f'col{i}' for i in range(128)]
    for ind,name in {0:'Pt #',2:'Tx',35:'Pt #',36:'Tx',77:'Pt #',78:'Tx',106:'Pt #',107:'Tx',75:'3-E2 ',76:'3-E2 post',120:'6-E2 ',121:'6-post E2'}.items():h[ind]=name
    rows=[]
    for j in range(22):
        row=[None]*128
        grp=1 if j<11 else 0
        for ididx in [0,35,77,106]:row[ididx]=j+1
        for txidx in [2,36,78,107]:row[txidx]=grp
        if grp==1:
            row[120]=200.
            row[121]=1000.+j*100
            if j<10:row[76]=750.+j*50
            if j<9:row[75]=190.
        rows.append(row)
    return [h]+rows

class MathematicalTests(unittest.TestCase):
    def test_number_float(self):self.assertEqual(a.numeric(9),9.)
    def test_number_bool_not_numeric(self):self.assertIsNone(a.numeric(True))
    def test_number_string_not_numeric(self):self.assertIsNone(a.numeric('1362*'))
    def test_number_censored_not_numeric(self):self.assertIsNone(a.numeric('<44'))
    def test_number_nan_not_numeric(self):self.assertIsNone(a.numeric(float('nan')))
    def test_number_inf_not_numeric(self):self.assertIsNone(a.numeric(float('inf')))
    def test_q25(self):self.assertEqual(a.quantile_linear([10,20,30,40,50],.25),20)
    def test_q75(self):self.assertEqual(a.quantile_linear([10,20,30,40,50],.75),40)
    def test_q_empty(self):self.assertIsNone(a.quantile_linear([],.75))
    def test_q_single(self):self.assertEqual(a.quantile_linear([42],.25),42)
    def test_tukey_odd(self):self.assertEqual(a.quartile_excluding_median([1,2,3,4,5,6,7,8,9,10,11]),[3,9])
    def test_tukey_even(self):self.assertEqual(a.quartile_excluding_median([1,2,3,4,5,6]),[2,5])
    def test_tukey_empty(self):self.assertEqual(a.quartile_excluding_median([]),[None,None])
    def test_tukey_two(self):self.assertEqual(a.quartile_excluding_median([1,2]),[None,None])
    def test_numeric_summary(self):self.assertEqual(a.summary_values([1,5,2])['median'],2)
    def test_summary_censored(self):self.assertEqual(a.summary_values(['<44',None,2])['n_missing_or_text'],2)
    def test_summary_empty(self):self.assertIsNone(a.summary_values([])['median'])
    def test_group_counts(self):self.assertEqual(a.group_counts([1,1,0]),{'0':1,'1':2})
    def test_hash_determinism(self):self.assertEqual(a.sha256(b'abc'),a.sha256(b'abc'))

class SyntheticTableTests(unittest.TestCase):
    def test_synthetic_head_width(self):self.assertEqual(len(synthetic_vnc()[0]),128)
    def test_synthetic_n(self):self.assertEqual(a.summarize_vnc54(synthetic_vnc(),['Data']+['s']*6)['n_rows'],22)
    def test_synthetic_11_6month_post(self):self.assertEqual(a.summarize_vnc54(synthetic_vnc(),['Data']+['s']*6)['visit_stats'][1]['n_postdose'],11)
    def test_synthetic_10_3month_post(self):self.assertEqual(a.summarize_vnc54(synthetic_vnc(),['Data']+['s']*6)['visit_stats'][0]['n_postdose'],10)
    def test_synthetic_9_3m_pairs(self):self.assertEqual(a.summarize_vnc54(synthetic_vnc(),['Data']+['s']*6)['visit_stats'][0]['n_paired_predose_postdose'],9)
    def test_synthetic_11_unique(self):self.assertEqual(a.summarize_vnc54(synthetic_vnc(),['Data']+['s']*6)['unique_people_with_postdose'],11)
    def test_synthetic_group1_post_only(self):self.assertEqual(a.summarize_vnc54(synthetic_vnc(),['Data']+['s']*6)['visit_stats'][1]['postdose_group_counts_unmapped'],{'1':11})
    def test_synthetic_id_mismatch_rejected(self):
        d=synthetic_vnc();d[1][35]=666
        with self.assertRaises(ValueError):a.summarize_vnc54(d,['Data']+['s']*6)
    def test_synthetic_arm_mismatch_rejected(self):
        d=synthetic_vnc();d[1][36]=0
        with self.assertRaises(ValueError):a.summarize_vnc54(d,['Data']+['s']*6)

class ArchiveTests(unittest.TestCase):
    def make(self, name,data=b'bad'):
        tmp=tempfile.TemporaryDirectory();path=Path(tmp.name)/'x.zip'
        with zipfile.ZipFile(path,'w') as z:z.writestr(name,data)
        return tmp,path
    def test_traversal_rejected(self):
        d,p=self.make('../Data for OSF.xlsx')
        try:
            with self.assertRaises(ValueError):a.safe_extract_one_xlsx(p,Path(d.name)/'out.xlsx','Data for OSF.xlsx')
        finally:d.cleanup()
    def test_wrong_name_rejected(self):
        d,p=self.make('wrong.xlsx')
        try:
            with self.assertRaises(ValueError):a.safe_extract_one_xlsx(p,Path(d.name)/'out.xlsx','Data for OSF.xlsx')
        finally:d.cleanup()
    def test_non_xlsx_rejected(self):
        d,p=self.make('Data for OSF.xlsx')
        try:
            with self.assertRaises(zipfile.BadZipFile):a.safe_extract_one_xlsx(p,Path(d.name)/'out.xlsx','Data for OSF.xlsx')
        finally:d.cleanup()
    def test_multiple_entries_rejected(self):
        d=tempfile.TemporaryDirectory();p=Path(d.name)/'x.zip'
        try:
            with zipfile.ZipFile(p,'w') as z:
                z.writestr('Data for OSF.xlsx',b'a')
                z.writestr('extra',b'b')
            with self.assertRaises(ValueError):a.safe_extract_one_xlsx(p,Path(d.name)/'out.xlsx','Data for OSF.xlsx')
        finally:d.cleanup()

if __name__=='__main__':unittest.main(verbosity=2)
