#!/usr/bin/env python3
"""P2-AQ: Privacy-minimizing audit of two researcher-supplied OSF ZIP/XLSX files.

No participant rows, IDs, dates, or hormone values are exported.
Requires artifact_tool to read Excel; Python standard library otherwise.
Use only for research reproduction, not individual treatment.
"""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
import math
import statistics
import tempfile
import zipfile
from pathlib import Path
from artifact_tool import Blob, SpreadsheetFile

SOURCE_NAMES={"TCRUW":"Data for OSF.xlsx","VNC54":"Supplementary table.xlsx"}
MAX_ENTRY=2_000_000

def sha256(data:bytes)->str:
    return hashlib.sha256(data).hexdigest()

def numeric(x):
    if isinstance(x, bool) or not isinstance(x,(float,int)) or not math.isfinite(x):
        return None
    return float(x)

def quantile_linear(arr, frac):
    z=sorted(arr)
    if not z: return None
    f=(len(z)-1)*frac
    a=int(f)
    return z[a]+(z[min(a+1,len(z)-1)]-z[a])*(f-a)

def quartile_excluding_median(arr):
    z=sorted(arr)
    if not z: return [None,None]
    if len(z)<3: return [None,None]
    m=len(z)//2
    return [statistics.median(z[:m]), statistics.median(z[-m:])]

def safe_extract_one_xlsx(path:Path, target:Path, expected:str):
    with zipfile.ZipFile(path) as archive:
        items=archive.infolist()
        if len(items)!=1: raise ValueError('Expected one XLSX only')
        item=items[0]
        if item.filename!=expected or '/' in item.filename or '\\' in item.filename:
            raise ValueError('Unexpected ZIP entry')
        if item.file_size>MAX_ENTRY or item.flag_bits&1 or item.is_dir():
            raise ValueError('Unsafe ZIP entry')
        data=archive.read(item)
        if len(data)!=item.file_size: raise ValueError('XLSX size mismatch')
        with zipfile.ZipFile(__import__('io').BytesIO(data)) as xz:
            names=set(xz.namelist())
            if '[Content_Types].xml' not in names or 'xl/workbook.xml' not in names:
                raise ValueError('Not a standard XLSX')
            if any('vbaproject' in x.lower() or 'externallinks' in x.lower() for x in names):
                raise ValueError('Active/external workbook content not approved')
        target.write_bytes(data)
        return {'archive_sha256':sha256(path.read_bytes()),'xlsx_sha256':sha256(data),'file_size_bytes':len(data),'entry_name':item.filename}

def load_book(path:Path,source:str):
    wb=SpreadsheetFile.import_xlsx(Blob.load(str(path)))
    sheet_count=1 if source=='TCRUW' else 7
    names=[wb.worksheets.get_item_at(i).name for i in range(sheet_count)]
    expected='A1:CR31' if source=='TCRUW' else 'A1:DX23'
    data=wb.worksheets.get_item_at(0).get_range(expected).values
    return names,data

def rows_to_mapping(table):
    h=table[0]
    return [{k:v for k,v in zip(h,row) if k} for row in table[1:]]

def group_counts(groups):
    return {str(k):n for k,n in sorted(collections.Counter(groups).items(),key=lambda t:str(t[0]))}

def summary_values(v):
    z=[x for x in (numeric(x) for x in v) if x is not None]
    return {'n_numeric':len(z), 'n_missing_or_text':len(v)-len(z),
            'min':min(z) if z else None,'median':statistics.median(z) if z else None,
            'max':max(z) if z else None}

def summarize_tcruw(table):
    h=table[0];r=table[1:]
    assert len(h)==96 and len(r)==30
    by=lambda n:[x[h.index(n)] for x in r]
    return {
      'n_rows':len(r),'n_columns':len(h),'sheets':['Sheet1'],
      'group_sizes_unmapped':group_counts(by('treatment group')),
      'timepoints':['treatment baseline','6 month follow-up'],
      'postdose_timed_e2_columns':[],
      'fields':{n:summary_values(by(n)) for n in ['BL-E2 (kupa)','6m-E2']},
      'text_qualifier_count':{n:sum(isinstance(x,str) and x.strip()!='' for x in by(n)) for n in ['BL-E2 (kupa)','6m-E2']},
      'explicit_actual_last_dose_datetime':False,'actual_dose_time_per_sample':False,
      'independent_dense_pk_eligible':False,
    }

def summarize_vnc54(table,sheet_names):
    h=table[0];r=table[1:]
    assert len(h)==128 and len(r)==22 and len(sheet_names)==7
    # repeated ID/arm columns appear in four horizontal data blocks; validate alignment
    for i in [35,77,106]:
        if any(row[i]!=row[0] for row in r): raise ValueError('Repeated patient index does not align')
    for i in [36,78,107]:
        if any(row[i]!=row[2] for row in r): raise ValueError('Repeated treatment group does not align')
    records=[]
    for label,pre,post in [('3_month','3-E2 ','3-E2 post'),('6_month','6-E2 ','6-post E2')]:
        ip=h.index(pre);io=h.index(post)
        pairs=[(numeric(row[ip]),numeric(row[io])) for row in r]
        vals=[v for _,v in pairs if v is not None]
        pair=[(a,b) for a,b in pairs if a is not None and b is not None]
        delta=[b-a for a,b in pair]
        ratios=[b/a for a,b in pair if a>0]
        records.append({
          'visit':label,'n_postdose':len(vals),'n_paired_predose_postdose':len(pair),
          'postdose_group_counts_unmapped':group_counts(row[2] for row in r if numeric(row[io]) is not None),
          'postdose_pmol_l':summary_values(vals),
          'postdose_tukey_halves_iqr_pmol_l':quartile_excluding_median(vals),
          'postdose_type7_iqr_pmol_l':[quantile_linear(vals,q) for q in [.25,.75]],
          'paired_predose_pmol_l':summary_values([a for a,b in pair]),
          'paired_delta_pmol_l':summary_values(delta),
          'paired_postdose_predose_ratio':summary_values(ratios),
        })
    i3=h.index('3-E2 post');i6=h.index('6-post E2')
    both=sum(numeric(row[i3]) is not None and numeric(row[i6]) is not None for row in r)
    # 90min annotation is from published method, not present in raw column names.
    return {
      'n_rows':len(r),'n_columns':len(h),'sheets':sheet_names,
      'group_sizes_unmapped':group_counts(row[2] for row in r),
      'repeated_id_and_arm_alignment':'PASS',
      'published_90_min_method':'Yaish 2023; morning 0.5 mg SL, 90 min; 4 SL doses per day',
      'e2_assay':'Siemens Immulite 2000 chemiluminescence (published)',
      'visit_stats':records,'n_people_with_both_3m_and_6m_postdose':both,
      'unique_people_with_postdose':sum(numeric(row[i6]) is not None or numeric(row[i3]) is not None for row in r),
      'actual_sample_timestamp_per_person':False,'actual_prior_dose_history_per_person':False,
      'independent_dense_pk_eligible':False,'pre_exposed_study':'Yaish 2023/2025',
    }

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--tcruw',type=Path,required=True)
    p.add_argument('--vnc54',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True)
    args=p.parse_args()
    args.out.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='p2aq-sources-') as tmp:
      results={}
      for source,path in [('TCRUW',args.tcruw),('VNC54',args.vnc54)]:
        ext=Path(tmp)/(source+'.xlsx')
        meta=safe_extract_one_xlsx(path,ext,SOURCE_NAMES[source])
        sheets,rows=load_book(ext,source)
        res=summarize_tcruw(rows) if source=='TCRUW' else summarize_vnc54(rows,sheets)
        results[source]={'source':{'zip_name':path.name,**meta},'data':res}
    v=results['VNC54']['data']
    six=v['visit_stats'][1]
    results['conclusions']={
      'osf_workbook_count_verified':2,
      'deidentified_subject_row_count_naively_added':52,
      'confirmed_unique_people_across_two_studies':None,
      'timed_90min_measurements_at_3m':v['visit_stats'][0]['n_postdose'],
      'timed_90min_measurements_at_6m':six['n_postdose'],
      'timed_90min_measurements_total':sum(t['n_postdose'] for t in v['visit_stats']),
      'unique_people_with_timed_90min':v['unique_people_with_postdose'],
      'published_1721_pmol_l_median_reproduced':six['postdose_pmol_l']['median']==1721,
      'published_approx_1000_2432_iqr_reproduced':abs(six['postdose_tukey_halves_iqr_pmol_l'][0]-1000)<1 and six['postdose_tukey_halves_iqr_pmol_l'][1]==2432,
      'unseen_independent_external_pk_cohorts':0,
      'ready_for_absolute_pg_ml_prediction_validation':False,
      'ready_for_0_to_24h_shape_validation':False,
      'eligible_for_retrospective_known_study_90min_benchmark':True,
      'raw_participant_data_exported':False,
    }
    args.out.write_text(json.dumps(results,ensure_ascii=False,indent=2,allow_nan=False,sort_keys=True)+'\n',encoding='utf-8')
    print(f"P2-AQ: TCRUW {results['TCRUW']['data']['n_rows']} rows, VNC54 {v['n_rows']} rows; individual 90-minute samples: 3mo={v['visit_stats'][0]['n_postdose']},6mo={six['n_postdose']}; 6mo median={six['postdose_pmol_l']['median']} pmol/L; new external cohorts=0")

if __name__=='__main__': main()
