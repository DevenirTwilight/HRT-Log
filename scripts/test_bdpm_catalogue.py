import hashlib,json,sqlite3,unittest
from pathlib import Path
import build_bdpm_catalogue as build

class FullCatalogueTest(unittest.TestCase):
 def setUp(self):
  self.meta=json.loads((build.ASSETS/'manifest.json').read_text());self.db=sqlite3.connect('file:'+str(build.ASSETS/'catalogue.sqlite')+'?mode=ro',uri=True)
 def tearDown(self):self.db.close()
 def test_official_full_sources_and_output_digest(self):
  import gzip
  for name,(encoding,count) in build.FILES.items():
   raw=gzip.decompress((build.SOURCES/(name+'.gz')).read_bytes())
   self.assertEqual(self.meta['inputs'][name]['sha256'],hashlib.sha256(raw).hexdigest())
   self.assertEqual(self.meta['counts'][name],len(build.rows(name,raw)))
  self.assertEqual(self.meta['database_sha256'],hashlib.sha256((build.ASSETS/'catalogue.sqlite').read_bytes()).hexdigest())
  self.assertGreater(self.db.execute('SELECT count(*) FROM product').fetchone()[0],15000)
  self.assertEqual([],self.db.execute('PRAGMA foreign_key_check').fetchall())
 def test_provames_identity_package_and_component_originals(self):
  self.assertEqual(('PROVAMES 2 mg, comprimé pelliculé',),self.db.execute('SELECT name FROM product WHERE cis=?',('65421374',)).fetchone())
  self.assertEqual(('3547934','plaquette(s) thermoformée(s) PVC-aluminium de 28 comprimé(s)','3400935479341'),self.db.execute('SELECT cip7,description,cip13 FROM package WHERE cis=?',('65421374',)).fetchone())
  self.assertEqual(('01786','ESTRADIOL','2 mg','un comprimé','SA','1'),self.db.execute('SELECT substance_code,name,strength,basis,kind,link FROM composition WHERE cis=?',('65421374',)).fetchone())
 def test_ingredient_and_identifier_queries_and_unicode(self):
  q="SELECT name FROM product WHERE instr(search,?)>0"
  self.assertGreater(len(self.db.execute(q,('estradiol',)).fetchall()),10)
  self.assertEqual(self.db.execute(q,('65421374',)).fetchall(),self.db.execute(q,('3400935479341',)).fetchall())
  self.assertEqual(build.fold('Œstrogène'),build.fold('oestrogene'))
 def test_preserve_orphans_and_non_additive_composition(self):
  self.assertEqual(4,self.db.execute('SELECT count(*) FROM orphan_package').fetchone()[0])
  self.assertEqual(32439,self.db.execute('SELECT count(*) FROM composition').fetchone()[0])
  self.assertEqual({'SA':26915,'FT':5524},dict(self.db.execute('SELECT kind,count(*) FROM composition GROUP BY kind')))
  with self.assertRaises(sqlite3.OperationalError):self.db.execute("DELETE FROM product")
 def test_rebuild_is_deterministic_without_network(self):
  import contextlib,io,tempfile
  old=build.ASSETS
  try:
   with tempfile.TemporaryDirectory() as folder,contextlib.redirect_stdout(io.StringIO()):
    build.ASSETS=Path(folder)
    build.build();first=(build.ASSETS/'catalogue.sqlite').read_bytes()
    build.build();self.assertEqual(first,(build.ASSETS/'catalogue.sqlite').read_bytes())
    rebuilt=sqlite3.connect(build.ASSETS/'catalogue.sqlite')
    try:
     for table in ['product','composition','package','orphan_package','metadata']:
      self.assertEqual(self.db.execute('SELECT * FROM '+table+' ORDER BY rowid').fetchall(),rebuilt.execute('SELECT * FROM '+table+' ORDER BY rowid').fetchall())
    finally:rebuilt.close()
  finally:build.ASSETS=old
if __name__=='__main__':unittest.main()
