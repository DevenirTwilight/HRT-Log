import contextlib,gzip,hashlib,io,json,sqlite3,tempfile,unittest
from pathlib import Path
import bdpm_official_details as d

class OfficialDetailsTest(unittest.TestCase):
 def setUp(self):
  self.meta=json.loads((d.RAW/'manifest.json').read_text());self.rows=d.source_rows(self.meta)
  self.db=sqlite3.connect('file:'+str(d.OUT/'official.sqlite')+'?mode=ro',uri=True)
 def tearDown(self):self.db.close()
 def product(self,cis):return json.loads(self.db.execute('SELECT json FROM detail WHERE cis=?',(cis,)).fetchone()[0])
 def test_production_source_dates_hashes_encoding_and_blank_cpd_evidence(self):
  self.assertEqual(d.BASE+'/telechargement',self.meta['download_page'])
  for n,m in self.meta['inputs'].items():
   b=gzip.decompress((d.RAW/(n+'.gz')).read_bytes());self.assertEqual(m['sha256'],d.sha(b));self.assertEqual(m['rows'],len(d.parse(n,b)))
  self.assertEqual(9,self.meta['inputs']['CIS_CPD_bdpm.txt']['blank_lines'])
  self.assertEqual('2026-10-28',self.meta['inputs']['CIS_CIP_bdpm.txt']['source_updated_on'])
  self.assertIsNone(self.meta['inputs']['CIS_InfoImportantes.txt']['source_updated_on'])
 def test_provames_original_conditions_has_disagreement_and_mitm(self):
  p=self.product('65421374');self.assertEqual(['liste II'],p['cpd'])
  r=next(r for r in p['assessments'] if r['kind']=='SMR' and r['dossier']=='CT-21206')
  self.assertEqual('Modéré',r['value']);self.assertIn('reste important',r['text']);self.assertEqual('2025-09-24',r['date'])
  self.assertEqual('G03CA03',p['mitm'][0]['atc']);self.assertEqual('2026-06-25',p['important'][0]['start'])
 def test_all_multi_conditions_reviews_and_generic_members_are_retained(self):
  products=[json.loads(r[0]) for r in self.db.execute('SELECT json FROM detail')]
  self.assertTrue(any(len(p['cpd'])>1 for p in products));self.assertTrue(any(len(p['assessments'])>1 for p in products))
  for p in products:
   self.assertEqual([r[1] if len(r)>1 else None for r in self.rows['CIS_CPD_bdpm.txt'] if r[0]==p['cis']],p['cpd'])
   for g in p['groups']:
    actual=[(r[2],r[3],r[4]) for r in self.rows['CIS_GENER_bdpm.txt'] if r[0]==g['id']]
    self.assertEqual(actual,[(m['cis'],m['type'],m['order']) for m in g['members']])
 def test_supply_is_exact_cis_cip_not_pharmacy_stock(self):
  for (payload,) in self.db.execute('SELECT json FROM detail'):
   p=json.loads(payload)
   source=[r for r in self.rows['CIS_CIP_Dispo_Spec.txt'] if r[0]==p['cis']]
   self.assertEqual(len(source),len(p['supply']))
   for raw,record in zip(source,p['supply']):
    self.assertEqual(raw[1] or None,record['cip13']);self.assertEqual(raw[2],record['code']);self.assertEqual(raw[3],record['label'])
    self.assertEqual(d.date(raw[4]),record['start']);self.assertEqual(d.date(raw[5]),record['updated'])
 def test_verified_documents_and_ema_are_product_specific_and_not_body_text(self):
  p=self.product('65421374');docs=p['documents'];self.assertTrue(docs['verified'] and docs['rcp'] and docs['notice']);self.assertEqual(p['cis'],docs['cis'])
  ema=self.product('60073960')['documents'];self.assertFalse(ema['rcp']);self.assertTrue(ema['ema_links'])
  for cis,entry in json.loads((d.RAW/'document-pages.json').read_text()).items():
   if entry['verified']:self.assertEqual('/medicament/'+cis+'/extrait',d.urlsplit(entry['url']).path)
  self.assertNotIn('rcp_body',docs);self.assertNotIn('notice_body',docs)
 def test_untrusted_links_html_and_missing_data_fail_closed(self):
  for u in ['javascript:alert(1)','http://ansm.sante.fr/a','https://ansm.sante.fr.evil.invalid/','https://x@ansm.sante.fr/','https://ansm.sante.fr:8443/']:
   self.assertFalse(d.safe_url(u),u)
  parser=d.Text();parser.feed('<a href="javascript:x">original</a><script>unsafe()</script><br>texte')
  self.assertEqual([],parser.links);self.assertEqual('original\ntexte',d.plain('<a href="javascript:x">original</a><script>unsafe()</script><br>texte'))
  self.assertIsNone(d.date(''));self.assertIsNone(self.db.execute('SELECT json FROM detail WHERE cis=?',('00000000',)).fetchone())
  with self.assertRaises(ValueError):d.date('yesterday')
  with self.assertRaises(AssertionError):d.parse('CIS_CPD_bdpm.txt',b'not-a-CIS\tvalue')
 def test_failed_refresh_cannot_replace_bundled_data_and_redirects_reject_before_fetch(self):
  import shutil,urllib.request
  old=d.RAW;before=(d.OUT/'official.sqlite').read_bytes()
  try:
   with tempfile.TemporaryDirectory() as folder:
    d.RAW=Path(folder);shutil.copytree(old,d.RAW,dirs_exist_ok=True)
    (d.RAW/'CIS_CPD_bdpm.txt.gz').write_bytes(gzip.compress(b'65421374\tSYNTHETIC modified'))
    with self.assertRaises(AssertionError):d.build()
    self.assertEqual(before,(d.OUT/'official.sqlite').read_bytes())
  finally:d.RAW=old
  with self.assertRaises(ValueError):d.OfficialRedirects().redirect_request(urllib.request.Request(d.BASE+'/telechargement'),None,302,'',{},'https://evil.invalid/')
  self.assertEqual([['65421374']],d.parse('CIS_CPD_bdpm.txt',b'65421374'))
 def test_readonly_digest_and_deterministic_rebuild(self):
  meta=json.loads((d.OUT/'official-manifest.json').read_text());self.assertEqual(meta['database_sha256'],d.sha((d.OUT/'official.sqlite').read_bytes()))
  with self.assertRaises(sqlite3.OperationalError):self.db.execute('DELETE FROM detail')
  before=d.OUT
  try:
   with tempfile.TemporaryDirectory() as folder,contextlib.redirect_stdout(io.StringIO()):
    d.OUT=Path(folder);d.build();first=(d.OUT/'official.sqlite').read_bytes();d.build();self.assertEqual(first,(d.OUT/'official.sqlite').read_bytes())
    with sqlite3.connect(d.OUT/'official.sqlite') as regenerated:self.assertEqual(self.db.execute('SELECT * FROM detail ORDER BY cis').fetchall(),regenerated.execute('SELECT * FROM detail ORDER BY cis').fetchall())
  finally:d.OUT=before
if __name__=='__main__':unittest.main()
