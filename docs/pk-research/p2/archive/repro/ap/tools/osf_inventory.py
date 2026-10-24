#!/usr/bin/env python3
"""OSF public API inventory: metadata only; does not download participant data.
Works offline with --fixture (to test parser). Fail-closed on unavailable API.
"""
import argparse,json,time,urllib.request,urllib.error
from pathlib import Path
BASE='https://api.osf.io/v2'
NODES={'vnc54':'Previously exposed Yaish/body-composition cohort; not external PK validation', 'tcruw':'Six-month follow-up cohort; PK timing/overlap unverified'}
def get(url):
    req=urllib.request.Request(url,headers={'User-Agent':'HRT-Log-P2-AP-research-inventory/1.0','Accept':'application/vnd.api+json'})
    with urllib.request.urlopen(req,timeout=12) as r:return json.load(r)
def traverse_pages(first,loader=get,max_pages=25):
    out=[];url=first;seen=set()
    while url:
        if url in seen: raise ValueError('pagination cycle')
        if len(seen)>=max_pages:raise ValueError('pagination limit')
        seen.add(url);j=loader(url)
        if not isinstance(j.get('data'),list):raise ValueError('unexpected JSON-API response')
        out.extend(j['data']);url=(j.get('links') or {}).get('next')
    return out
def extract_files(data,root='',depth=0):
    if depth>8:raise ValueError('folder depth limit')
    out=[]
    for rec in data:
        attr=rec.get('attributes') or {};name=attr.get('name','');kind=attr.get('kind')
        if not name or kind not in ('file','folder'):raise ValueError('missing filename/kind')
        item={'path':root+name,'kind':kind,'size_bytes':attr.get('size'), 'content_type':attr.get('content_type') or attr.get('contentType'),'file_id':rec.get('id'),'download_url':(rec.get('links') or {}).get('download') if kind=='file' else None}
        out.append(item)
        if kind=='folder':
            children=((rec.get('relationships') or {}).get('files') or {}).get('links') or {}
            rel=children.get('related')
            if isinstance(rel,dict):rel=rel.get('href')
            if rel:out.extend(extract_files(traverse_pages(rel),root+name+'/',depth+1))
    return out
def inventory(node,loader=get):
    providers=traverse_pages(BASE+'/nodes/'+node+'/files/',loader)
    files=[]
    for provider in providers:
        links=provider.get('links') or {}
        file_url=links.get('files')
        if file_url:files+=extract_files(traverse_pages(file_url,loader))
    return {'node':node,'status':'metadata_verified','file_count':sum(f['kind']=='file' for f in files),'entries':files,'eligibility':'unassessed_requires_file_and_cohort_review'}
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--out',default='osf-inventory.json');ap.add_argument('--fixture',help='local JSON fixture payload mapping URL to JSON');args=ap.parse_args()
    if args.fixture:
        sample=json.loads(Path(args.fixture).read_text());loader=lambda url:sample[url]
    else:loader=get
    out={'method':'OSF public API metadata only; no IPD downloaded','nodes':[]}
    for node,reason in NODES.items():
        try: item=inventory(node,loader);item['research_note']=reason
        except Exception as e:item={'node':node,'status':'inaccessible_not_verified','error':type(e).__name__+': '+str(e)[:350],'file_count':None,'entries':None,'eligibility':'not_verified','research_note':reason}
        out['nodes'].append(item)
    Path(args.out).write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({x['node']:{'status':x['status'],'files':x['file_count']} for x in out['nodes']},ensure_ascii=False))
if __name__=='__main__':main()
