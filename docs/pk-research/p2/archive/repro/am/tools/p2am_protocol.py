#!/usr/bin/env python3
"""P2-AM independent human-model validation framework, synthetic test data ONLY.

Official legacy and research M2 are NOT implemented here; input shapes MUST come
from the actual locked engines separately. This module never accepts a score as
proof of clinical validation. All patient data must remain outside this package.
"""
from __future__ import annotations
import argparse
import csv
import datetime as dt
import hashlib
import json
import math
import random
import statistics
from collections import defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
REQUIRED={
 'subjects':('study_code','subject_code','assay_method','assay_batch','prior_exposure'),
 'doses':('subject_code','event_code','admin_utc','molecule','route','dose_mg','verified'),
 'samples':('subject_code','sample_code','collection_utc','role','e2_pg_ml','quantified','lloq_pg_ml','sd_pg_ml','assay_method','assay_batch'),
 'predictions':('subject_code','sample_code','model_id','relative_increment','model_sha256')}
MODEL_IDS=('LEGACY_LOCKED','M2_LOCKED')


def parse_utc(text):
    if not text or not (text.endswith('Z') or '+' in text[10:] or '-' in text[10:]):
        raise ValueError('timestamp must have explicit timezone: '+str(text))
    out=dt.datetime.fromisoformat(text.replace('Z','+00:00'))
    if out.tzinfo is None: raise ValueError('missing timezone')
    return out.astimezone(dt.timezone.utc)


def strict_csv(path,kind):
    with path.open(newline='',encoding='utf-8-sig') as stream:
        rd=csv.DictReader(stream)
        if rd.fieldnames is None or len(set(rd.fieldnames))!=len(rd.fieldnames):
            raise ValueError('duplicate/missing CSV headers')
        if not set(REQUIRED[kind])<=set(rd.fieldnames):raise ValueError('missing '+kind+' columns')
        rows=list(rd)
    if not rows:raise ValueError('no '+kind+' records')
    if any(any(v is None for v in row.values()) for row in rows):
        raise ValueError('ragged CSV row')
    return rows


def finite_number(value,name,nonnegative=False,strict_positive=False):
    x=float(value)
    if not math.isfinite(x) or (nonnegative and x<0) or (strict_positive and x<=0):
        raise ValueError('invalid '+name)
    return x


def validate_manifest(m):
    for key in ('phase','origin','training_studies_seen','dataset_study_codes','model_sha256',
                'protocol_frozen_at_utc','first_dataset_access_at_utc','score_data_unblinded_at_utc',
                'engine_predictions_locked_at_utc','eligible_for_external_claim'):
        if key not in m:raise ValueError('manifest missing '+key)
    if m['phase']!='P2-AM':raise ValueError('incorrect protocol phase')
    if m['origin'] not in ('SYNTHETIC','EXTERNAL_HUMAN'):raise ValueError('invalid origin')
    for model in MODEL_IDS:
        digest=m['model_sha256'].get(model,'')
        if not isinstance(digest,str) or len(digest)!=64 or any(c not in '0123456789abcdef' for c in digest):
            raise ValueError('sha256 pin required for both models')
    frozen=parse_utc(m['protocol_frozen_at_utc'])
    accessed=parse_utc(m['first_dataset_access_at_utc'])
    prediction_locked=parse_utc(m['engine_predictions_locked_at_utc'])
    unblinded=parse_utc(m['score_data_unblinded_at_utc'])
    if not frozen < accessed:raise ValueError('protocol not frozen before data access')
    if not accessed <= prediction_locked < unblinded:
        raise ValueError('predictions must precede unblinding the scored target samples')
    if m['origin']=='EXTERNAL_HUMAN':
        if set(m['dataset_study_codes']) & set(m['training_studies_seen']):
            raise ValueError('external study was previously seen')
        if m['eligible_for_external_claim'] is not True:
            raise ValueError('external human claim requires explicit independent permission')
        if not m.get('ethics_approval_reference') or not m.get('data_permission_reference'):
            raise ValueError('ethics/data permissions missing')
        if not m.get('independent_reviewer_reference'):
            raise ValueError('independent reviewer missing')
    else:
        if m['eligible_for_external_claim'] is True:raise ValueError('synthetic cannot be external')
    return m['origin']


def load_and_score(path: Path):
    m=json.loads((path/'manifest.json').read_text(encoding='utf-8'))
    origin=validate_manifest(m)
    subjects=strict_csv(path/'subjects.csv','subjects')
    doses=strict_csv(path/'doses.csv','doses')
    samples=strict_csv(path/'samples.csv','samples')
    predictions=strict_csv(path/'predictions.csv','predictions')
    subject_by_id={}
    study_codes=set()
    for row in subjects:
        sid=row['subject_code'];study=row['study_code']
        if not sid or not study or sid in subject_by_id:raise ValueError('subjects must be unique, named')
        if row['prior_exposure']!='NO':raise ValueError('subject or samples used previously')
        if row['assay_method'] not in ('LC-MS/MS','IMMUNOASSAY') or not row['assay_batch']:
            raise ValueError('unknown assay context')
        subject_by_id[sid]=row;study_codes.add(study)
    if study_codes!=set(m['dataset_study_codes']):raise ValueError('study manifest mismatch')
    dose_by_subject=defaultdict(list)
    for row in doses:
        sid=row['subject_code']
        if sid not in subject_by_id:raise ValueError('dose has unknown subject')
        if row['molecule']!='E2' or row['route']!='SUBLINGUAL' or row['verified']!='YES':
            raise ValueError('non-verified non SL-E2 dose not eligible')
        finite_number(row['dose_mg'],'dose',strict_positive=True)
        dose_by_subject[sid].append(parse_utc(row['admin_utc']))
    if any(len(dose_by_subject[sid])!=1 for sid in subject_by_id):
        raise ValueError('single-dose P2-AM validation requires exactly one verified SL-E2 event per subject; repeated administration needs separate preregistered protocol')
    sample_by_subject=defaultdict(dict)
    owner={}
    for row in samples:
        sid=row['subject_code'];sample=row['sample_code']
        if sid not in subject_by_id or not sample:raise ValueError('unidentified sample')
        if sample in owner:raise ValueError('duplicate sample code across subjects')
        owner[sample]=sid
        if row['role'] not in ('BASELINE','ANCHOR','EVAL'):raise ValueError('unknown sample role')
        if row['assay_method']!=subject_by_id[sid]['assay_method'] or row['assay_batch']!=subject_by_id[sid]['assay_batch']:
            raise ValueError('assay methodology/batch must agree, no implicit pooling')
        if row['quantified']!='YES':raise ValueError('below-quantification-limit observations require censored model; do not impute/drop silently')
        c=finite_number(row['e2_pg_ml'],'concentration',nonnegative=True)
        lloq=finite_number(row['lloq_pg_ml'],'lloq',strict_positive=True)
        sd=finite_number(row['sd_pg_ml'],'sd',strict_positive=True)
        if c<lloq:raise ValueError('quantified sample is under reported LLOQ')
        row['_time']=parse_utc(row['collection_utc'])
        row['_conc']=c;row['_sd']=sd
        sample_by_subject[sid][sample]=row
    pred_by_sample=defaultdict(dict)
    for row in predictions:
        sample=row['sample_code'];sid=row['subject_code'];model=row['model_id']
        if sample not in owner or owner[sample]!=sid:raise ValueError('unmatched or wrong subject prediction')
        if model not in MODEL_IDS or model in pred_by_sample[sample]:raise ValueError('duplicate/unknown model prediction')
        if row['model_sha256']!=m['model_sha256'][model]:raise ValueError('model hash mismatch')
        v=finite_number(row['relative_increment'],'relative increment',nonnegative=True)
        pred_by_sample[sample][model]=v
    if set(pred_by_sample)!=set(owner):raise ValueError('every sample must have both models')
    for key,pred in pred_by_sample.items():
        if set(pred)!=set(MODEL_IDS):raise ValueError('unpaired predictions')
    participants=[]
    for sid,s in sorted(subject_by_id.items()):
        sam=list(sample_by_subject[sid].values());dose_time=dose_by_subject[sid][0]
        role=defaultdict(list)
        for row in sam:role[row['role']].append(row)
        if len(role['BASELINE'])!=1 or len(role['ANCHOR'])!=1 or len(role['EVAL'])<2:
            raise ValueError('1 baseline, 1 anchor and at least two evaluation samples needed')
        baseline=role['BASELINE'][0];anchor=role['ANCHOR'][0]
        if not baseline['_time']<dose_time<anchor['_time']:
            raise ValueError('baseline must precede actual administered dose, then anchor')
        anchor_h=(anchor['_time']-dose_time).total_seconds()/3600
        if not .75<=anchor_h<=1.25:
            raise ValueError('preregistered pilot uses observed 1h anchor ±15 min; change requires new protocol')
        if any(x['_time']<=anchor['_time'] for x in role['EVAL']):
            raise ValueError('all held-out evaluation samples must follow anchor')
        if any((x['_time']-dose_time).total_seconds()>24*3600+1e-6 for x in role['EVAL']):
            raise ValueError('this frozen single dose protocol covers at most 24h')
        inc=anchor['_conc']-baseline['_conc']
        # Joint sd from two independent samples is an APPROXIMATION: real lab covariance must be supplied/reviewed.
        anchor_sd=math.hypot(anchor['_sd'],baseline['_sd'])
        if inc<=3*anchor_sd:raise ValueError('anchor lacks minimum illustrative signal/noise; no valid shape ratios')
        for model in MODEL_IDS:
            if pred_by_sample[anchor['sample_code']][model]<=0:
                raise ValueError('model zero anchor response')
        per_model={}
        times=[]
        for row in role['EVAL']:
            t=(row['_time']-dose_time).total_seconds()/3600
            times.append(t)
        if len(set(times))!=len(times):raise ValueError('duplicate heldout timestamps')
        for model in MODEL_IDS:
            base_model=pred_by_sample[anchor['sample_code']][model]
            squared=[];absolute=[];points=[]
            for row in sorted(role['EVAL'],key=lambda z:z['_time']):
                t=(row['_time']-dose_time).total_seconds()/3600
                r_observed=(row['_conc']-baseline['_conc'])/inc
                r_pred=pred_by_sample[row['sample_code']][model]/base_model
                error=r_pred-r_observed
                squared.append(error*error);absolute.append(abs(error))
                points.append({'time_hour':t,'observed_normalized_increment':r_observed,
                               'predicted_normalized_increment':r_pred,'abs_error':abs(error)})
            per_model[model]={'mae':statistics.mean(absolute),'mse':statistics.mean(squared),
                              'rmse':math.sqrt(statistics.mean(squared)),'points':points}
        participants.append({'subject_code':sid,'study_code':s['study_code'],
                             'assay_method':s['assay_method'],
                             'n_scored':len(role['EVAL']),
                             'mean_mae_legacy':per_model['LEGACY_LOCKED']['mae'],
                             'mean_mae_m2':per_model['M2_LOCKED']['mae'],
                             'paired_mae_delta_m2_minus_legacy':per_model['M2_LOCKED']['mae']-per_model['LEGACY_LOCKED']['mae'],
                             'models':per_model})
    diffs=[p['paired_mae_delta_m2_minus_legacy'] for p in participants]
    # Do not over-weight subjects with more frequent samples.
    by_assay={m:{'n_subjects':sum(p['assay_method']==m for p in participants),
                 'paired_delta_mean':statistics.mean([p['paired_mae_delta_m2_minus_legacy'] for p in participants if p['assay_method']==m])}
              for m in sorted({x['assay_method'] for x in participants})}
    by_window={}
    for name,lower,upper in [('early_1to8',1.,8.),('late_8to24',8.,24.)]:
        per_subject=[]
        for person in participants:
            legacy=person['models']['LEGACY_LOCKED']['points']
            new=person['models']['M2_LOCKED']['points']
            matched=[new[i]['abs_error']-legacy[i]['abs_error']
                     for i,point in enumerate(new) if lower<point['time_hour']<=upper]
            if matched:per_subject.append(statistics.mean(matched))
        by_window[name]={'n_subjects':len(per_subject),
                         'mean_delta_mae_m2_minus_legacy':statistics.mean(per_subject) if per_subject else None}
    outcome={'phase':'P2-AM','origin':origin,'is_clinical_validation':False,
             'metric':'subject equally weighted MAE of post-anchor baseline-subtracted response normalized by the same-subject measured baseline-to-anchor increment',
             'one_anchor_is_calibration_NOT_scored':True,'absolute_pgml_gate':'BLOCKED: no pre-locked uncalibrated individual serum concentration model',
             'n_studies':len(study_codes),'n_subjects':len(participants),'n_scored_samples':sum(p['n_scored'] for p in participants),
             'mean_mae_legacy':statistics.mean([p['mean_mae_legacy'] for p in participants]),
             'mean_mae_m2':statistics.mean([p['mean_mae_m2'] for p in participants]),
             'paired_mae_delta_m2_minus_legacy':statistics.mean(diffs),
             'by_assay':by_assay,
             'by_window':by_window,
             'synthetic_only':origin=='SYNTHETIC',
             'participants':participants,
             'limitations':['Calibration uses one observed subject anchor; NOT fully prospective uncalibrated personal prediction.',
                            'Baseline is presumed constant for this descriptive single-dose experiment; not established clinically.',
                            'No confidence, p-value, treatment decision or clinical replacement approval is produced.',
                            'Only single-dose verified E2 SL events supported; future repeated-dose track must be independently pre-registered.',
                            'Participant IDs are in result: never share unredacted output from human data.']}
    return outcome


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--data-dir',type=Path,default=ROOT/'synthetic')
    parser.add_argument('--output',type=Path,default=ROOT/'results'/'p2am-synthetic-evaluation.json')
    a=parser.parse_args()
    out=load_and_score(a.data_dir)
    if out['origin']=='EXTERNAL_HUMAN':
        # Default privacy guard. Full subject-level details are never written by this CLI.
        out.pop('participants',None)
        for method in list(out['by_assay']):
            if out['by_assay'][method]['n_subjects']<5:
                out['by_assay'][method]={'n_subjects':'SUPPRESSED_UNDER_5',
                                        'paired_delta_mean':None}
        out['participant_details_suppressed_on_disk']=True
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(out,sort_keys=True,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps({'origin':out['origin'],'n_subjects':out['n_subjects'],
                     'paired_delta':out['paired_mae_delta_m2_minus_legacy'],
                     'clinical_validation':out['is_clinical_validation']}))

if __name__=='__main__':main()
