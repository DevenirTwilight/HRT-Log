#!/usr/bin/env python3
"""Reproducible full BDPM identity catalogue; stdlib only. No clinical text or arithmetic.
Run --download to explicitly refresh the ANSM alternative published snapshot, then review dates.
Without --download builds offline from hash-pinned gzip source bytes in tools/bdpm/sources.
"""
import argparse, datetime, gzip, hashlib, json, re, sqlite3, unicodedata, urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCES=ROOT/'tools/bdpm/sources'
ASSETS=ROOT/'core/data/src/main/assets/bdpm'
BASE='https://bdpmt.ansm.sante.fr'
FILES={'CIS_bdpm.txt':('cp1252',12),'CIS_CIP_bdpm.txt':('utf-8',13),'CIS_COMPO_bdpm.txt':('cp1252',8)}
def sha(data):return hashlib.sha256(data).hexdigest()
def fold(s):return ''.join(c for c in unicodedata.normalize('NFD',s.lower().replace('œ','oe').replace('æ','ae')) if not unicodedata.combining(c))
def rows(name,data):
 encoding,count=FILES[name]
 parsed=[line.split('\t') for line in data.decode(encoding,errors='strict').splitlines()]
 assert parsed and all(len(r)==count and re.fullmatch('[0-9]{8}',r[0]) for r in parsed),name+' format mismatch'
 return parsed

def download():
 SOURCES.mkdir(parents=True,exist_ok=True)
 page=urllib.request.urlopen(BASE+'/telechargement',timeout=45).read()
 inputs={}
 for name,(enc,_) in FILES.items():
  url=BASE+'/download/file/'+name
  with urllib.request.urlopen(url,timeout=45) as response:data=response.read()
  parsed=rows(name,data)
  block=page.decode('utf-8').split('href="/download/file/'+name+'"',1)[1].split('</a>',1)[0]
  date=re.search(r'Date de mise à jour\s*:\s*(\d{2}/\d{2}/\d{4})',block)[1]
  updated=datetime.datetime.strptime(date,'%d/%m/%Y').date().isoformat()
  (SOURCES/(name+'.gz')).write_bytes(gzip.compress(data,mtime=0))
  inputs[name]=dict(url=url,encoding=enc,sha256=sha(data),bytes=len(data),rows=len(parsed),source_updated_on=updated)
 meta=dict(downloaded_on=datetime.datetime.now(datetime.timezone.utc).date().isoformat(),
  source='Base de Données Publique des Médicaments (ANSM / HAS / UNCAM)',download_page=BASE+'/telechargement',
  source_environment='ANSM alternative host; download page displays TEST banner; not certified latest production export',
  production_download_attempt='503 from base-donnees-publique.medicaments.gouv.fr on 2026-10-22',
  date_semantics='Dates displayed beside individual files; HTTP Last-Modified/export timestamp not supplied',
  license='Licence Ouverte 1.0 (download contract); data.gouv.fr identifies fr-lo; footer 2.0 link is not substituted for the supplied contract',
  license_url=BASE+'/docs/telechargement/licence_bdpm.pdf',inputs=inputs)
 (SOURCES/'manifest.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')

def build():
 meta=json.loads((SOURCES/'manifest.json').read_text()); parsed={}
 for name in FILES:
  data=gzip.decompress((SOURCES/(name+'.gz')).read_bytes());assert sha(data)==meta['inputs'][name]['sha256'],name+' digest'
  parsed[name]=rows(name,data);assert len(parsed[name])==meta['inputs'][name]['rows']
 identities={r[0] for r in parsed['CIS_bdpm.txt']};assert len(identities)==len(parsed['CIS_bdpm.txt'])
 assert all(r[0] in identities for r in parsed['CIS_COMPO_bdpm.txt']),'orphan composition'
 # Official exports can have different dates. Preserve orphan package facts separately; never invent identities.
 orphans=[r for r in parsed['CIS_CIP_bdpm.txt'] if r[0] not in identities]
 meta['orphan_packages']={'count':len(orphans),'cis':sorted({r[0] for r in orphans}),'handling':'preserved in orphan_package; not presented as identified products'}
 assert all(r[6] in ('SA','ST','FT') for r in parsed['CIS_COMPO_bdpm.txt'])
 meta['composition_kinds']={k:sum(r[6]==k for r in parsed['CIS_COMPO_bdpm.txt']) for k in sorted({r[6] for r in parsed['CIS_COMPO_bdpm.txt']})}
 meta['composition_semantics']='Raw SA/FT/ST codes preserved, never merged or summed; v4 describes ST whereas supplied rows use FT'
 assert all(not r[1] or re.fullmatch('[0-9]{7}',r[1]) for r in parsed['CIS_CIP_bdpm.txt'])
 assert all(not r[6] or re.fullmatch('[0-9]{13}',r[6]) for r in parsed['CIS_CIP_bdpm.txt'])
 ASSETS.mkdir(parents=True,exist_ok=True);path=ASSETS/'catalogue.sqlite';path.unlink(missing_ok=True)
 db=sqlite3.connect(path)
 db.executescript('''PRAGMA page_size=4096; PRAGMA user_version=1;
 CREATE TABLE product(cis TEXT PRIMARY KEY,name TEXT NOT NULL,form TEXT NOT NULL,routes TEXT NOT NULL,search TEXT NOT NULL);
 CREATE TABLE composition(cis TEXT NOT NULL,element TEXT NOT NULL,substance_code TEXT NOT NULL,name TEXT NOT NULL,strength TEXT NOT NULL,basis TEXT NOT NULL,kind TEXT NOT NULL,link TEXT NOT NULL,FOREIGN KEY(cis) REFERENCES product(cis));
 CREATE TABLE package(cis TEXT NOT NULL,cip7 TEXT NOT NULL,description TEXT NOT NULL,cip13 TEXT NOT NULL,FOREIGN KEY(cis) REFERENCES product(cis));
 CREATE TABLE orphan_package(cis TEXT NOT NULL,cip7 TEXT NOT NULL,description TEXT NOT NULL,cip13 TEXT NOT NULL);
 CREATE TABLE metadata(json TEXT NOT NULL);
 CREATE INDEX composition_cis ON composition(cis); CREATE INDEX package_cis ON package(cis); CREATE INDEX package_cip ON package(cip13);
 ''')
 search={cis:[] for cis in identities}
 for r in parsed['CIS_COMPO_bdpm.txt']:search[r[0]].append(r[3])
 for r in parsed['CIS_CIP_bdpm.txt']:
  if r[0] in identities:search[r[0]] += [r[1],r[6]]
 for r in parsed['CIS_bdpm.txt']:
  db.execute('INSERT INTO product VALUES(?,?,?,?,?)',(r[0],r[1],r[2],r[3],fold(' '.join([r[0],r[1]]+search[r[0]]))))
 db.executemany('INSERT INTO composition VALUES(?,?,?,?,?,?,?,?)',parsed['CIS_COMPO_bdpm.txt'])
 db.executemany('INSERT INTO package VALUES(?,?,?,?)',[(r[0],r[1],r[2],r[6]) for r in parsed['CIS_CIP_bdpm.txt'] if r[0] in identities])
 db.executemany('INSERT INTO orphan_package VALUES(?,?,?,?)',[(r[0],r[1],r[2],r[6]) for r in orphans])
 meta['catalogue_version']='bdpm-'+meta['downloaded_on']+'-'+sha(''.join(meta['inputs'][n]['sha256'] for n in FILES).encode())[:16]
 db.execute('INSERT INTO metadata VALUES(?)',(json.dumps(meta,sort_keys=True,ensure_ascii=False,separators=(',',':')),))
 db.commit();assert db.execute('PRAGMA integrity_check').fetchone()==('ok',);assert not db.execute('PRAGMA foreign_key_check').fetchall()
 # Acceptance against downloaded records, never inserted fixtures.
 assert db.execute('SELECT name FROM product WHERE cis=?',('65421374',)).fetchone()==('PROVAMES 2 mg, comprimé pelliculé',)
 assert db.execute('SELECT cip13 FROM package WHERE cis=? AND cip13=?',('65421374','3400935479341')).fetchone()
 assert db.execute("SELECT count(DISTINCT cis) FROM composition WHERE lower(name) LIKE '%estradiol%'").fetchone()[0]>10
 db.execute('VACUUM');db.close()
 receipt=dict(meta,database_sha256=sha(path.read_bytes()),database_bytes=path.stat().st_size,
  counts={n:len(parsed[n]) for n in FILES},generator=1)
 (ASSETS/'manifest.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({k:receipt[k] for k in ['catalogue_version','counts','database_bytes','database_sha256']},indent=2))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--download',action='store_true');args=p.parse_args()
 if args.download:download()
 build()
