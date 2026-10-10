#!/usr/bin/env python3
"""Generate obviously fake, non-person synthetic input to exercise validation code.

The 'legacy' and 'M2' fixture functions are DEMO SHAPES, not real app predictions!
"""
import csv
import hashlib
import json
import math
from datetime import datetime,timedelta,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'synthetic'

def iso(t):return t.isoformat(timespec='seconds').replace('+00:00','Z')
def save(name,columns,rows):
    with (OUT/name).open('w',newline='',encoding='utf-8') as stream:
        writer=csv.writer(stream);writer.writerow(columns);writer.writerows(rows)

def make():
    OUT.mkdir(parents=True,exist_ok=True)
    model_hash={name:hashlib.sha256(('SYNTHETIC-ONLY-FIXTURE-'+name).encode()).hexdigest() for name in ('LEGACY_LOCKED','M2_LOCKED')}
    m={'phase':'P2-AM','origin':'SYNTHETIC','training_studies_seen':['Price1997_figure1','Rosano1997_PK25','Komesaroff1998_n10','Doll2022_previously_reviewed'],
       'dataset_study_codes':['DEMO_CENTER_A','DEMO_CENTER_B'],'model_sha256':model_hash,
       'protocol_frozen_at_utc':'2025-01-01T00:00:00Z','first_dataset_access_at_utc':'2025-01-02T00:00:00Z',
       'engine_predictions_locked_at_utc':'2025-01-03T00:00:00Z','score_data_unblinded_at_utc':'2025-01-04T00:00:00Z',
       'eligible_for_external_claim':False,
       'warning':'ALL RECORDS AND MODEL PREDICTIONS ARE SYNTHETIC. ZERO REAL INDIVIDUAL VALIDATION. SHA PINs ARE SYNTHETIC STRINGS, NOT APP BUILD HASHES.'}
    (OUT/'manifest.json').write_text(json.dumps(m,indent=2,sort_keys=True)+'\n')
    subjects=[];doses=[];samples=[];pred=[]
    times=[('ANCHOR',1.),('EVAL',2.),('EVAL',4.),('EVAL',8.),('EVAL',12.),('EVAL',24.)]
    for n in range(8):
        sid=f'DEMO_PERSON_{n+1:03}';study='DEMO_CENTER_A' if n<4 else 'DEMO_CENTER_B'
        method='LC-MS/MS' if n%2==0 else 'IMMUNOASSAY'
        batch='DEMO_LOT_A' if n<4 else 'DEMO_LOT_B'
        subjects.append([study,sid,method,batch,'NO'])
        start=datetime(2025,2,1+n,12,0,0,tzinfo=timezone.utc)
        doses.append([sid,sid+'-DOSE',iso(start),'E2','SUBLINGUAL','1','YES'])
        base=20+((n%3)-1)*3
        samples.append([sid,sid+'-B',iso(start-timedelta(minutes=10)),'BASELINE',f'{base:.5f}','YES','5','3',method,batch])
        for role,t in times:
            # Structural fixtures, NOT source-study estimates or personal values.
            ref=1.5*t*math.exp(-.6*t)+.12*math.exp(-.08*t)
            anchor_shape=1.5*math.exp(-.6)+.12*math.exp(-.08)
            h=ref/anchor_shape
            amplitude=100+5*n
            measured=max(0.,base+amplitude*h+0.7*math.sin(t+n))
            sample=sid+'-'+str(t).replace('.','p')
            samples.append([sid,sample,iso(start+timedelta(hours=t)),role,f'{measured:.6f}','YES','5','3',method,batch])
            # Two deliberately distinct mock curves to test scoring; NOT old or M2 kernels.
            e=0.03*math.sin(t/2+n*0.4)
            predictions={'LEGACY_LOCKED':max(0.,h+0.13*math.sin(t/4+n)),
                         'M2_LOCKED':max(0.,h+e)}
            for k,v in predictions.items():pred.append([sid,sample,k,f'{v:.9f}',model_hash[k]])
        for k in model_hash:
            pred.append([sid,sid+'-B',k,'0.0',model_hash[k]])
    save('subjects.csv',('study_code','subject_code','assay_method','assay_batch','prior_exposure'),subjects)
    save('doses.csv',('subject_code','event_code','admin_utc','molecule','route','dose_mg','verified'),doses)
    save('samples.csv',('subject_code','sample_code','collection_utc','role','e2_pg_ml','quantified','lloq_pg_ml','sd_pg_ml','assay_method','assay_batch'),samples)
    save('predictions.csv',('subject_code','sample_code','model_id','relative_increment','model_sha256'),pred)
    return m
if __name__=='__main__':make();print('SYNTHETIC fixture written; no human data')
