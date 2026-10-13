"""No scipy dependency: frozen product/source contract, independent-output provenance."""
import hashlib,json,re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
E=ROOT/'docs/design/pr19-calibration-evidence'
class Pr19CalibrationContract(unittest.TestCase):
 def test_frozen_sources_and_actual_science_catalogs_match(self):
  r=json.loads((E/'reference.json').read_text());lock=json.loads((E/'product-lock.json').read_text())
  import research_archive as archive
  for p,h in r['input_hashes'].items():
   blob=archive.git_blobs(archive.ARCHIVE_COMMIT,[p])[p]
   self.assertIsNotNone(blob,p)
   self.assertEqual(h,hashlib.sha256(blob).hexdigest(),p)
  for family in ('M1','M2'):
   blob=archive.git_blobs(archive.ARCHIVE_COMMIT,[lock[family]['catalog_file']])[lock[family]['catalog_file']]
   self.assertEqual(lock[family]['catalog_sha256'],hashlib.sha256(blob).hexdigest())
   self.assertEqual(lock[family]['runtime_parameter_sha256'],hashlib.sha256(lock[family]['runtime_signature'].encode()).hexdigest())
   self.assertEqual(r['default_'+family.lower()],lock[family]['id'])
 def test_production_preview_is_explicit_literature_protocol_not_common_example(self):
  s=(ROOT/'app/src/main/java/net/plainnotes/app/experimental/ConcentrationPreview.kt').read_text()
  p=(ROOT/'pk-engine/src/main/kotlin/net/plainnotes/app/pk/experimental/ResearchDisplayProtocol.kt').read_text()
  self.assertNotIn('.first()',p);self.assertNotIn('CommonCalibration(',s)
  self.assertIn('ResearchSublingualV01.studyScenario',s);self.assertIn('PriceFigure1997.anchor(projected)',p)
  self.assertIn('times+=it.hour',s);self.assertIn('times+=peak.hour',s)
 def test_independent_data_show_different_absolute_protocols_and_unresolved_auc(self):
  r=json.loads((E/'reference.json').read_text());rows={v['id']:v for v in r['rows']}
  for id in (r['default_m1'],r['default_m2'],'p2x-14'):
   row=rows[id];self.assertGreater(abs(row['price']['cmax_pgml']-row['common']['cmax_pgml']),100)
   self.assertNotEqual(row['price']['at1h_pgml'],row['price']['cmax_pgml'])
   self.assertGreater(abs(row['price']['auc_increment_0_24']-2109),500)
  for row in r['rows']:
   if row['family']=='M2':self.assertAlmostEqual(row['frozen_auc_replay'],row['frozen_auc'],delta=1e-6)
 def test_original_ui_guard_and_all_calibration_boundaries_remain(self):
  import check_protocol
  check_protocol.check()
  s=(ROOT/'app/src/main/java/net/plainnotes/app/experimental/ConcentrationPreview.kt').read_text()
  for forbidden in ('LabValueEntity','CalibrationMode','Scale(','LabFit','result.calibration'):self.assertNotIn(forbidden,s)
if __name__=='__main__':unittest.main()
