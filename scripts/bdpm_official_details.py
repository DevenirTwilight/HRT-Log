#!/usr/bin/env python3
"""Maintainer-only production BDPM acquisition and deterministic read-only detail database.
No patient database, clinical inference, dose arithmetic or runtime network requests.
"""
import argparse,collections,concurrent.futures,datetime,gzip,hashlib,html,json,re,sqlite3,urllib.request
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit,urljoin
import build_bdpm_catalogue as catalogue
ROOT=Path(__file__).resolve().parents[1]
BASE='https://base-donnees-publique.medicaments.gouv.fr'
RAW=ROOT/'tools/bdpm/official-sources'
OUT=ROOT/'core/data/src/main/assets/bdpm'
FILES={'CIS_bdpm.txt':('cp1252',12),'CIS_CIP_bdpm.txt':('utf-8',13),'CIS_COMPO_bdpm.txt':('cp1252',8),
 'CIS_HAS_SMR_bdpm.txt':('cp1252',6),'CIS_HAS_ASMR_bdpm.txt':('cp1252',6),'HAS_LiensPageCT_bdpm.txt':('utf-8',2),
 'CIS_GENER_bdpm.txt':('cp1252',5),'CIS_CPD_bdpm.txt':('cp1252',2),'CIS_CIP_Dispo_Spec.txt':('cp1252',8),
 'CIS_MITM.txt':('cp1252',7),'CIS_InfoImportantes.txt':('utf-8',4)}
HOSTS={'base-donnees-publique.medicaments.gouv.fr','ansm.sante.fr','www.ansm.sante.fr','www.has-sante.fr','has-sante.fr','www.ema.europa.eu','ema.europa.eu'}
def sha(b):return hashlib.sha256(b).hexdigest()
def encoded(obj):return (json.dumps(obj,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode()
def safe_url(value):
 try:
  u=urlsplit(value)
  return bool(u.scheme=='https' and u.hostname in HOSTS and not u.username and not u.password and u.port in (None,443) and not any(ord(c)<33 for c in value) and '\\' not in value and not re.search(r'%(?:0[0-9a-f]|1[0-9a-f]|7f)',value,re.I))
 except ValueError:return False
class Text(HTMLParser):
 def __init__(self):super().__init__(convert_charrefs=True);self.text=[];self.links=[];self.skip=0
 def handle_starttag(self,t,a):
  if t in ('script','style'):self.skip+=1
  if t in ('br','p','li','div'):self.text.append('\n')
  if t=='a':
   href=dict(a).get('href','')
   if safe_url(href):self.links.append(href)
 def handle_endtag(self,t):
  if t in ('script','style'):self.skip=max(0,self.skip-1)
  if t in ('p','li','div'):self.text.append('\n')
 def handle_data(self,s):
  if not self.skip:self.text.append(s)
def plain(value):
 t=Text();t.feed(value)
 return '\n'.join(' '.join(line.split()) for line in ''.join(t.text).splitlines() if line.strip())
def date(value):
 if not value:return None
 for fmt in ('%Y-%m-%d','%d/%m/%Y','%Y%m%d'):
  try:return datetime.datetime.strptime(value,fmt).date().isoformat()
  except ValueError:pass
 raise ValueError('Unsupported official date '+repr(value))
class OfficialRedirects(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,req,fp,code,msg,headers,newurl):
  if not safe_url(newurl):raise ValueError('redirect outside official hosts')
  return super().redirect_request(req,fp,code,msg,headers,newurl)

def fetch(url,limit=20*1024*1024):
 assert safe_url(url),url
 with urllib.request.build_opener(OfficialRedirects()).open(url,timeout=45) as r:
  assert safe_url(r.url),'redirect outside official hosts'
  b=r.read(limit+1);assert len(b)<=limit,'official response too large'
  return b,{'url':r.url,'sha256':sha(b),'bytes':len(b),'retrieved_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'http_date':r.headers.get('Date'),'last_modified':r.headers.get('Last-Modified')}
def parse(name,b):
 enc,n=FILES[name];rows=[line.split('\t') for line in b.decode(enc,errors='strict').splitlines()]
 assert rows and all(len(r)==n or name=='CIS_CPD_bdpm.txt' and len(r)==1 for r in rows),(name,'field count')
 # Blank CPD separators are preserved in raw bytes / audit counts, never clinical rows.
 rows=[r for r in rows if any(r)]
 column=2 if name=='CIS_GENER_bdpm.txt' else 0
 if name!='HAS_LiensPageCT_bdpm.txt':assert all(re.fullmatch('[0-9]{8}',r[column]) for r in rows),(name,'CIS identity')
 return rows

def download():
 RAW.mkdir(parents=True,exist_ok=True)
 b,pm=fetch(BASE+'/telechargement');page=b.decode('utf-8');assert not re.search(r'>\s*TEST\s*<',page)
 (RAW/'download-page.html.gz').write_bytes(gzip.compress(b,mtime=0))
 anchors={url.rsplit('/',1)[-1]:(url,' '.join(plain(body).split())) for url,body in re.findall(r'<a\b[^>]*href="([^"]+)"[^>]*>(.*?)</a>',page,re.S) if url.startswith(('/download/','/docs/telechargement/'))}
 def one(name):
  url,label=anchors[name];data,m=fetch(BASE+url);rows=parse(name,data)
  published=re.search(r'Date de mise à jour\s*:\s*(\d{2}/\d{2}/\d{4})',label)
  raw=data.decode(FILES[name][0]).splitlines()
  m.update(raw_rows=len(raw),blank_lines=sum(not line for line in raw),encoding=FILES[name][0],source_updated_on=date(published[1]) if published else None,rows=len(rows),field_counts=dict(collections.Counter(len(line.split('\t')) for line in raw)),page_label=label)
  (RAW/(name+'.gz')).write_bytes(gzip.compress(data,mtime=0));return name,m
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:inputs=dict(pool.map(one,FILES))
 documents={}
 for name in ['licence_bdpm.pdf','Contenu_et_format_des_fichiers_telechargeables_dans_la_BDM_v5.pdf']:
  b,m=fetch(BASE+anchors[name][0]);documents[name]=m
  # Format / license kept as provenance evidence outside the application.
  (RAW/(name+'.gz')).write_bytes(gzip.compress(b,mtime=0))
 meta={'downloaded_on':datetime.datetime.now(datetime.timezone.utc).date().isoformat(),'source':'Base de Données Publique des Médicaments (ANSM / HAS / UNCAM)',
  'download_page':BASE+'/telechargement','source_environment':'Official production website; explicit dated offline snapshot, not realtime',
  'license':'Licence Ouverte 1.0 supplied by download contract; footer also links 2.0, not substituted for the supplied contract',
  'license_url':BASE+'/docs/telechargement/licence_bdpm.pdf','date_semantics':'Each dataset has its own page date; dynamic important-information generation timestamp is not supplied. Retrieval is not publication.',
  'download_page_evidence':pm,'documents':documents,'inputs':inputs}
 (RAW/'manifest.json').write_bytes(encoded(meta))
 # Reuse the already verified identity catalogue generator and HRT exact-substance selection.
 catalogue.SOURCES.mkdir(parents=True,exist_ok=True)
 for n in catalogue.FILES:(catalogue.SOURCES/(n+'.gz')).write_bytes((RAW/(n+'.gz')).read_bytes())
 (catalogue.SOURCES/'manifest.json').write_bytes(encoded(meta))
 return meta

def source_rows(meta):
 out={}
 for n in FILES:
  b=gzip.decompress((RAW/(n+'.gz')).read_bytes());assert sha(b)==meta['inputs'][n]['sha256'],n
  out[n]=parse(n,b);assert len(out[n])==meta['inputs'][n]['rows']
 return out

def scope(rows):
 codes={c for group in json.loads((ROOT/'tools/bdpm/hrt-selection.json').read_text())['groups'].values() for c in group}
 selected={r[0] for r in rows['CIS_COMPO_bdpm.txt'] if r[6]=='SA' and r[2] in codes}
 chosen=set(selected)
 # Include all members of a referenced group; no clinical equivalence inference.
 while True:
  groups={r[0] for r in rows['CIS_GENER_bdpm.txt'] if r[2] in chosen}
  more={r[2] for r in rows['CIS_GENER_bdpm.txt'] if r[0] in groups}
  if more<=chosen:break
  chosen|=more
 return selected,chosen

def verify_document_page(cis,name,body,meta):
 text=body.decode('utf-8',errors='strict');head=re.search(r'<h3\b[^>]*>(.*?)</h3>',text,re.S)
 valid=head is not None and catalogue.fold(plain(head[1]))==catalogue.fold(name) and urlsplit(meta['url']).path=='/medicament/'+cis+'/extrait'
 update=re.search(r'Dernière mise à jour le\s+(\d{2}/\d{2}/\d{4})',text)
 ema=Text();ema.feed(text)
 return dict(meta,cis=cis,name=name,verified=valid,source_updated_on=date(update[1]) if update else None,
  rcp=valid and 'id="tabpanel-rcp-panel"' in text,notice=valid and 'id="tabpanel-notice-panel"' in text,
  ema_links=list(dict.fromkeys(u for u in ema.links if urlsplit(u).hostname in ('www.ema.europa.eu','ema.europa.eu'))))
def download_pages(meta):
 rows=source_rows(meta);_,chosen=scope(rows);names={r[0]:r[1] for r in rows['CIS_bdpm.txt']}
 results={}
 def one(cis):
  if cis not in names:return cis,{'cis':cis,'verified':False,'reason':'official identity missing'}
  url=BASE+'/medicament/'+cis+'/extrait'
  try:
   b,m=fetch(url,4*1024*1024);return cis,verify_document_page(cis,names[cis],b,m)
  except Exception as e:return cis,{'cis':cis,'verified':False,'reason':type(e).__name__}
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
  for cis,entry in pool.map(one,sorted(chosen)):results[cis]=entry
 (RAW/'document-pages.json').write_bytes(encoded(results))
 print('Document pages:',len(results),'verified',sum(v['verified'] for v in results.values()))

def build():
 meta=json.loads((RAW/'manifest.json').read_text());rows=source_rows(meta);selected,chosen=scope(rows)
 identities={r[0]:r for r in rows['CIS_bdpm.txt']};pages=json.loads((RAW/'document-pages.json').read_text()) if (RAW/'document-pages.json').exists() else {}
 by={}
 for n in FILES:
  grouped=collections.defaultdict(list)
  if n=='CIS_GENER_bdpm.txt':
   for r in rows[n]:grouped[r[0]].append(r)
  else:
   for r in rows[n]:grouped[r[0]].append(r)
  by[n]=grouped
 assessment_links=by['HAS_LiensPageCT_bdpm.txt'];generics=by['CIS_GENER_bdpm.txt'];groups_by_cis=collections.defaultdict(list)
 for gid,members in generics.items():
  for cis in {r[2] for r in members}:groups_by_cis[cis].append(gid)
 path=OUT/'official.sqlite';path.unlink(missing_ok=True)
 db=sqlite3.connect(path);db.executescript('PRAGMA page_size=4096; PRAGMA user_version=1; CREATE TABLE detail(cis TEXT PRIMARY KEY,json TEXT NOT NULL);')
 unknown={};counts=collections.Counter()
 for cis in sorted(chosen):
  identity=identities.get(cis)
  if identity is None:unknown[cis]='missing identity';continue
  cpd=[r[1] if len(r)>1 and r[1] else None for r in by['CIS_CPD_bdpm.txt'][cis]]
  assessments=[]
  for kind in ('SMR','ASMR'):
   for r in by['CIS_HAS_'+kind+'_bdpm.txt'][cis]:
    assessments.append(dict(kind=kind,dossier=r[1],reason=r[2],date=date(r[3]),value=r[4],text=plain(r[5]),links=[x[1] for x in assessment_links[r[1]] if safe_url(x[1])]))
  groups=[]
  for gid in groups_by_cis[cis]:
   members=generics[gid];groups.append(dict(id=gid,label=members[0][1],members=[dict(cis=r[2],name=identities.get(r[2],[None,None])[1],type=r[3],order=r[4]) for r in members]))
  important=[]
  for r in by['CIS_InfoImportantes.txt'][cis]:
   t=Text();t.feed(r[3]);important.append(dict(start=date(r[1]),end=date(r[2]),text=plain(r[3]),links=list(dict.fromkeys(t.links))))
  supply=[]
  for r in by['CIS_CIP_Dispo_Spec.txt'][cis]:
   assert not r[1] or re.fullmatch('[0-9]{13}',r[1]),'CIP13 format'
   supply.append(dict(cip13=r[1] or None,code=r[2],label=r[3],start=date(r[4]),updated=date(r[5]),available=date(r[6]),url=r[7] if safe_url(r[7]) else None))
  mitm=[dict(atc=r[1],name=r[2],holder=r[3],operator=r[4],date=date(r[5]),source_url=r[6]) for r in by['CIS_MITM.txt'][cis]]
  obj=dict(cis=cis,name=identity[1],monitoring=identity[11] if identity[11] in ('Oui','Non') else None,cpd=cpd,assessments=assessments,groups=groups,important=important,supply=supply,mitm=mitm,documents=pages.get(cis,{'cis':cis,'verified':False,'reason':'not fetched'}))
  db.execute('INSERT INTO detail VALUES(?,?)',(cis,json.dumps(obj,ensure_ascii=False,sort_keys=True,separators=(',',':'))))
  for key in ('cpd','assessments','groups','important','supply','mitm'):counts[key]+=len(obj[key])
 db.commit();assert db.execute('PRAGMA integrity_check').fetchone()==('ok',);db.execute('VACUUM');db.close()
 receipt=dict(meta,format=1,selected_products=len(selected),detail_products=len(chosen)-len(unknown),unknown_identities=unknown,detail_counts=dict(counts),database_sha256=sha(path.read_bytes()),database_bytes=path.stat().st_size,document_index_sha256=sha((RAW/'document-pages.json').read_bytes()))
 (OUT/'official-manifest.json').write_bytes(encoded(receipt));print(json.dumps({'detail_products':receipt['detail_products'],'detail_counts':receipt['detail_counts'],'database_bytes':receipt['database_bytes'],'sha256':receipt['database_sha256']},indent=2))

def main():
 p=argparse.ArgumentParser();p.add_argument('--download',action='store_true');p.add_argument('--pages',action='store_true');p.add_argument('--build',action='store_true');a=p.parse_args()
 if a.download:meta=download()
 else:meta=json.loads((RAW/'manifest.json').read_text())
 if a.pages:download_pages(meta)
 if a.build:catalogue.build('hrt');build()
if __name__=='__main__':main()
