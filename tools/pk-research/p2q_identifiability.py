"""P2-Q: structural symmetries, profiled nuisance and synthetic PK model robustness.

No clinical data fitted. The P2-O candidate pool came from previously EXPOSED
Rosano/Price studies; '75' refers to mathematics, NOT participants. All
measurements, b, gain and assay errors are synthetic. Fixed-reference weighted
SSE is NOT a profiled Gaussian likelihood or a confidence region.
"""
import argparse
import itertools
import json
import math
import random
import statistics
from pathlib import Path

import p2n_transit_convolution as model
import p2o_slow_tail_scan as scan

ROOT=Path(__file__).resolve().parents[2]
CONFIG=ROOT/'docs/pk-research/p2/p2q-identifiability-design.json'
SCAN=ROOT/'docs/pk-research/p2/p2o-slow-tail-parameter-scan.json'


def nonnegative_linear_fit(x,y,sigma,mode,known_b,known_a):
    """Exact positive-orthant WLS for y_i = b + a*x_i; sigma is fixed.

    Includes both boundary projections (b=0, a=0) and the origin. This
    ensures nuisance profiling never increases the optimum misfit.
    """
    if not len(x)==len(y)==len(sigma) or not x:
        raise ValueError('Unequal or empty design')
    values=(*x,*y,*sigma,known_b,known_a)
    if any(not math.isfinite(v) for v in values) or any(v<=0 for v in sigma):
        raise ValueError('Invalid concentration or error')
    if known_b<0 or known_a<0:raise ValueError('Nonnegative model required')
    if mode not in ('known_b_a','unknown_a','unknown_b_a'):
        raise ValueError('Unknown nuisance condition')
    weights=[1/(s*s) for s in sigma]
    sw=math.fsum(weights)
    sx=math.fsum(w*xi for w,xi in zip(weights,x))
    sxx=math.fsum(w*xi*xi for w,xi in zip(weights,x))
    sy=math.fsum(w*yi for w,yi in zip(weights,y))
    sxy=math.fsum(w*xi*yi for w,xi,yi in zip(weights,x,y))
    opts=[]
    if mode=='known_b_a':
        opts=[(known_b,known_a)]
    elif mode=='unknown_a':
        if sxx<=0:raise ValueError('Unidentifiable gain')
        opts=[(known_b,max(0.,(sxy-known_b*sx)/sxx))]
    else:
        opts=[(max(0.,sy/sw),0.)]
        if sxx>0:opts.append((0.,max(0.,sxy/sxx)))
        opts.append((0.,0.))
        det=sw*sxx-sx*sx
        if det>1e-12*sw*sxx:
            b=(sy*sxx-sx*sxy)/det
            a=(sw*sxy-sx*sy)/det
            if b>=0 and a>=0:opts.append((b,a))
    best=None
    for b,a in opts:
        sse=math.fsum(((yi-b-a*xi)/si)**2 for xi,yi,si in zip(x,y,sigma))
        if best is None or sse<best[0]:best=(sse,b,a)
    return {'fixed_reference_weighted_sse':best[0], 'baseline':best[1], 'gain':best[2]}


def selected_exposed_cases(cfg):
    source=json.loads(SCAN.read_text())
    cases=[c for c in scan.scan_inputs(source) if scan.accept(
        scan.raw_case_metrics(c),cfg['screening']['price_assumed_predose_pg_ml'],source['acceptance'])]
    if len(cases)!=cfg['screening']['expected_candidates']:
        raise ValueError('Upstream P2-O candidate membership changed')
    return cases


def normalized_case(case,t):
    return model.unnormalized(t,case)/model.unnormalized(1.,case)


def fixed_error_sigma(y,err):
    return [math.hypot(err['absolute_floor_pg_ml'],err['proportional_fraction']*v) for v in y]


def equivalence_survey(cfg,cases):
    baseline=cfg['synthetic_latent_concentration']['baseline_pg_ml']
    gain=cfg['synthetic_latent_concentration']['one_hour_increment_pg_ml']
    threshold=cfg['toy_ordered_pair_mismatch_squared_cutoff']
    modes=['known_b_a','unknown_a','unknown_b_a']
    grids=cfg['draw_schedules_h']
    all_times=sorted(set(sum(grids.values(),[])))
    profiles=[[normalized_case(c,t) for t in all_times] for c in cases]
    id_by_time={t:i for i,t in enumerate(all_times)}
    scenarios=[]
    for err in cfg['error_scenarios']:
        for key,times in grids.items():
            ix=[id_by_time[t] for t in times]
            profile=[[p[i] for i in ix] for p in profiles]
            count={k:0 for k in modes}; severe={k:0 for k in modes}
            trough=[.5*math.fsum(normalized_case(c,j*6) for j in range(1,121)) for c in cases]
            for i,source in enumerate(profile):
                truth=[baseline+gain*x for x in source]
                sigmas=fixed_error_sigma(truth,err)
                for j,candidate in enumerate(profile):
                    if i==j:continue
                    is_severe=max(trough[i],trough[j])/min(trough[i],trough[j])>=2
                    for k in modes:
                        res=nonnegative_linear_fit(candidate,truth,sigmas,k,baseline,gain)
                        if res['fixed_reference_weighted_sse'] <= threshold:
                            count[k]+=1
                            if is_severe:severe[k]+=1
            scenarios.append({'assumed_assay':err['id'],'schedule':key,
                'n_ordered_different_model_comparisons':len(cases)*(len(cases)-1),
                'unresolved_synthetic_ordered_comparisons':count,
                'of_those_q6_trough_diverges_2x':severe})
    return scenarios


def family_candidates(cfg,cases):
    groups={}
    for name,space in cfg['synthetic_family_benchmark']['families'].items():
        if name=='dual_slow_input':
            arr=[c for c in cases if c['slow_effective_weight']>0]
        else:
            arr=[{'n_fast':n,'k_fast_h':kf,'k_elim_h':ke,
                  'k_slow_h':ks,'slow_effective_weight':w}
                 for n,kf,ke,w,ks in itertools.product(
                    space['n_fast'],space['k_fast_h'],space['k_elim_h'],
                    space['slow_effective_weight'],space['k_slow_h'])]
        groups[name]=arr
    return groups


def synthetic_truth_cases():
    return {
      'one_input':{'n_fast':1,'k_fast_h':1.8,'k_elim_h':.4,'k_slow_h':.2,'slow_effective_weight':0},
      'transit_only':{'n_fast':5,'k_fast_h':8,'k_elim_h':.85,'k_slow_h':.2,'slow_effective_weight':0},
      'dual_slow_input':{'n_fast':5,'k_fast_h':8,'k_elim_h':.85,'k_slow_h':.05,'slow_effective_weight':.5}
    }


def family_robustness(cfg,cases):
    setup=cfg['synthetic_family_benchmark']
    baseline=cfg['synthetic_latent_concentration']['baseline_pg_ml']
    gain=cfg['synthetic_latent_concentration']['one_hour_increment_pg_ml']
    train=setup['train_times_h'];test=setup['test_times_h']
    groups=family_candidates(cfg,cases)
    dims={'one_input':2,'transit_only':3,'dual_slow_input':5}
    specs=[(name,c,[normalized_case(c,t) for t in train],[normalized_case(c,t) for t in test])
           for name,grp in groups.items() for c in grp]
    truths=synthetic_truth_cases()
    rng=random.Random(setup['random_seed'])
    output=[]
    for err in cfg['error_scenarios']:
        for truth_name,truth_case in truths.items():
            ytrain=[baseline+gain*normalized_case(truth_case,t) for t in train]
            ytest=[baseline+gain*normalized_case(truth_case,t) for t in test]
            train_sigma=fixed_error_sigma(ytrain,err)
            test_sigma=fixed_error_sigma(ytest,err)
            selections={mode:{name:0 for name in groups} for mode in ('no_penalty','penalty')}
            validation={mode:[] for mode in selections}
            for _ in range(setup['replicates_per_truth_per_error']):
                obs=[y+rng.gauss(0,s) for y,s in zip(ytrain,train_sigma)]
                best={mode:None for mode in selections}
                for family,case,x_train,x_test in specs:
                    fit=nonnegative_linear_fit(x_train,obs,train_sigma,'unknown_b_a',baseline,gain)
                    fit_loss=fit['fixed_reference_weighted_sse']
                    scores={'no_penalty':fit_loss,
                        'penalty':fit_loss+max(0,dims[family]-2)*math.log(len(train))}
                    for mode,s in scores.items():
                        if best[mode] is None or s<best[mode][0]:
                            best[mode]=(s,family,fit,x_test)
                for mode,(score,family,fit,x_test) in best.items():
                    selections[mode][family]+=1
                    ypred=[fit['baseline']+fit['gain']*z for z in x_test]
                    hold=math.fsum(((p-y)/s)**2 for p,y,s in zip(ypred,ytest,test_sigma))/len(ytest)
                    validation[mode].append(hold)
            output.append({'synthetic_truth_family':truth_name,'error_scenario':err['id'],
                'replications':setup['replicates_per_truth_per_error'],
                'family_shapes_considered':{k:len(v) for k,v in groups.items()},
                'winner_count':selections,
                'median_oracle_standardized_holdout_mse':{
                    mode:statistics.median(losses) for mode,losses in validation.items()},
                'mean_oracle_standardized_holdout_mse':{
                    mode:statistics.fmean(losses) for mode,losses in validation.items()}})
    return output


def evaluate(cfg):
    cases=selected_exposed_cases(cfg)
    out={'phase':'P2-Q','source_status':'EXPOSED_STUDIES_AND_SYNTHETIC_MEASUREMENTS_ONLY',
        'candidate_count':len(cases),
        'exact_invariance':'Without IV/absolute availability, concentration only sees D*F/V; (F,V)->(cF,cV) gives same C(t) for c>0',
        'degenerate_slow_rate':'When slow_weight=0, k_slow has no effect at any sampling time',
        'two_observation_affine_interpolation':'When b,a free, two noiseless timepoints cannot uniquely determine shape in regions where positive solutions exist',
        'model_comparison_not_official_IC':True,
        'source_assay_errors_are_hypothetical':True,
        'Doll_144_used_for_refit':False,
        'independent_locked_external_human_datasets':0,
        'actual_human_prediction_coverage_established':False,
        'production_change_authorized':False,
        'uncertain_nuisance_survey':equivalence_survey(cfg,cases),
        'synthetic_holdout_family_comparison':family_robustness(cfg,cases)}
    return out


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',required=True)
    args=p.parse_args()
    dest=Path(args.out)
    if dest.exists():raise ValueError('Will not overwrite P2 research report')
    report=evaluate(json.loads(CONFIG.read_text()))
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(report,indent=2,ensure_ascii=False,sort_keys=True)+'\n')
    print('P2-Q synthetic identifiability, NOT clinical PK:',dest)


if __name__=='__main__':main()
