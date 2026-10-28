#!/usr/bin/env python3
"""Reproducible BDPM HRT-related identity catalogue (or --profile full); stdlib only. No clinical text or arithmetic.
Run scripts/bdpm_official_details.py --download --pages --build for a reviewed production refresh.
Without --download builds offline from hash-pinned gzip source bytes in tools/bdpm/sources.
"""
import argparse, datetime, gzip, hashlib, json, re, sqlite3, unicodedata, urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCES=ROOT/'tools/bdpm/sources'
ASSETS=ROOT/'core/data/src/main/assets/bdpm'
BASE='https://base-donnees-publique.medicaments.gouv.fr'
FILES={'CIS_bdpm.txt':('cp1252',12),'CIS_CIP_bdpm.txt':('utf-8',13),'CIS_COMPO_bdpm.txt':('cp1252',8)}
def sha(data):return hashlib.sha256(data).hexdigest()
def fold(s):return ''.join(c for c in unicodedata.normalize('NFD',s.lower().replace('œ','oe').replace('æ','ae')) if not unicodedata.combining(c))
def rows(name,data):
 encoding,count=FILES[name]
 parsed=[line.split('\t') for line in data.decode(encoding,errors='strict').splitlines()]
 assert parsed and all(len(r)==count and re.fullmatch('[0-9]{8}',r[0]) for r in parsed),name+' format mismatch'
 return parsed

def download():
 # Production source only. The complete official-details updater also verifies document pages.
 from bdpm_official_details import download as production_download
 production_download()

def build(profile="hrt"):
 assert profile in ("hrt","full")
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
 codes=[r[6] for r in parsed['CIS_CIP_bdpm.txt'] if r[6]]
 assert len(codes)==len(set(codes)),'Conflicting CIP13 identities require review'
 # Selection is an auditable public-reference subset, never a user-data validator.
 source_counts={n:len(parsed[n]) for n in FILES}
 if profile=="hrt":
  rules_bytes=(ROOT/'tools/bdpm/hrt-selection.json').read_bytes()
  rules=json.loads(rules_bytes)
  code_groups={code:group for group,codes in rules['groups'].items() for code in codes}
  selected={r[0] for r in parsed['CIS_COMPO_bdpm.txt'] if r[6]=='SA' and r[2] in code_groups}
  matched=[dict(cis=r[0],substance_code=r[2],name=r[3],group=code_groups[r[2]]) for r in parsed['CIS_COMPO_bdpm.txt'] if r[0] in selected and r[6]=='SA' and r[2] in code_groups]
  observed={r['substance_code'] for r in matched}
  meta['selection']=dict(profile=profile,rules_sha256=sha(rules_bytes),rules=rules,
   unmatched_codes=sorted(set(code_groups)-observed),matches=matched)
  parsed={n:[r for r in data if r[0] in selected] for n,data in parsed.items()}
  identities=selected;orphans=[]
 else:
  meta['selection']={'profile':'full'}
 meta['source_counts']=source_counts
 meta['included_counts']={n:len(parsed[n]) for n in FILES}
 meta['included_composition_kinds']={k:sum(r[6]==k for r in parsed['CIS_COMPO_bdpm.txt']) for k in sorted({r[6] for r in parsed['CIS_COMPO_bdpm.txt']})}
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
 meta['catalogue_version']='bdpm-'+profile+'-'+meta['downloaded_on']+'-'+sha((''.join(meta['inputs'][n]['sha256'] for n in FILES)+meta.get('selection',{}).get('rules_sha256','')).encode())[:16]
 db.execute('INSERT INTO metadata VALUES(?)',(json.dumps(meta,sort_keys=True,ensure_ascii=False,separators=(',',':')),))
 db.commit();assert db.execute('PRAGMA integrity_check').fetchone()==('ok',);assert not db.execute('PRAGMA foreign_key_check').fetchall()
 # Acceptance against downloaded records, never inserted fixtures.
 assert db.execute('SELECT name FROM product WHERE cis=?',('65421374',)).fetchone()==('PROVAMES 2 mg, comprimé pelliculé',)
 assert db.execute('SELECT cip13 FROM package WHERE cis=? AND cip13=?',('65421374','3400935479341')).fetchone()
 assert db.execute("SELECT count(DISTINCT cis) FROM composition WHERE lower(name) LIKE '%estradiol%'").fetchone()[0]>10
 db.execute('VACUUM');db.close()
 receipt=dict(meta,database_sha256=sha(path.read_bytes()),database_bytes=path.stat().st_size,
  counts={n:len(parsed[n]) for n in FILES},generator=2)
 (ASSETS/'manifest.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({k:receipt[k] for k in ['catalogue_version','counts','database_bytes','database_sha256']},indent=2))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--download',action='store_true');p.add_argument('--profile',choices=['hrt','full'],default='hrt');args=p.parse_args()
 if args.download:download()
 build(args.profile)
