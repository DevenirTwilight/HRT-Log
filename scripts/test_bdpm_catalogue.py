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
   self.assertEqual(self.meta['source_counts'][name],len(build.rows(name,raw)))
  self.assertEqual(self.meta['database_sha256'],hashlib.sha256((build.ASSETS/'catalogue.sqlite').read_bytes()).hexdigest())
  self.assertEqual(420,self.db.execute('SELECT count(*) FROM product').fetchone()[0])
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
  self.assertEqual(0,self.db.execute('SELECT count(*) FROM orphan_package').fetchone()[0])
  self.assertEqual(702,self.db.execute('SELECT count(*) FROM composition').fetchone()[0])
  self.assertEqual(702,sum(dict(self.db.execute('SELECT kind,count(*) FROM composition GROUP BY kind')).values()))
  with self.assertRaises(sqlite3.OperationalError):self.db.execute("DELETE FROM product")
 def test_exact_active_code_selection_and_all_dependent_rows(self):
  import gzip
  raw={n:build.rows(n,gzip.decompress((build.SOURCES/(n+'.gz')).read_bytes())) for n in build.FILES}
  rules=json.loads((build.ROOT/'tools/bdpm/hrt-selection.json').read_text())
  codes={code for group in rules['groups'].values() for code in group}
  expected={r[0] for r in raw['CIS_COMPO_bdpm.txt'] if r[6]=='SA' and r[2] in codes}
  self.assertEqual(expected,{r[0] for r in self.db.execute('SELECT cis FROM product')})
  self.assertEqual([tuple(r) for r in raw['CIS_COMPO_bdpm.txt'] if r[0] in expected],self.db.execute('SELECT * FROM composition ORDER BY rowid').fetchall())
  self.assertEqual([(r[0],r[1],r[2],r[6]) for r in raw['CIS_CIP_bdpm.txt'] if r[0] in expected],self.db.execute('SELECT * FROM package ORDER BY rowid').fetchall())
  self.assertEqual([(r[0],r[1],r[2],r[3],build.fold(' '.join([r[0],r[1]]+[c[3] for c in raw['CIS_COMPO_bdpm.txt'] if c[0]==r[0]]+[x for p in raw['CIS_CIP_bdpm.txt'] if p[0]==r[0] for x in [p[1],p[6]]]))) for r in raw['CIS_bdpm.txt'] if r[0] in expected],self.db.execute('SELECT * FROM product ORDER BY rowid').fetchall())
 def test_false_name_matches_excluded_and_full_rebuild_preserved(self):
  import contextlib,io,tempfile
  self.assertEqual([],self.db.execute("SELECT name FROM product WHERE search LIKE '%homeopath%' OR search LIKE '%fluoroestradiol%'").fetchall())
  old=build.ASSETS
  try:
   with tempfile.TemporaryDirectory() as folder,contextlib.redirect_stdout(io.StringIO()):
    build.ASSETS=Path(folder);build.build('full')
    with sqlite3.connect(build.ASSETS/'catalogue.sqlite') as full:
     self.assertEqual(15883,full.execute('SELECT count(*) FROM product').fetchone()[0])
     self.assertEqual(4,full.execute('SELECT count(*) FROM orphan_package').fetchone()[0])
     self.assertEqual({'SA':26915,'FT':5524},dict(full.execute('SELECT kind,count(*) FROM composition GROUP BY kind')))
  finally:build.ASSETS=old
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
