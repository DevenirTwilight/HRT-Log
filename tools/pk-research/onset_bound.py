"""P2-D independent early-rise test, NOT a population refit."""
import argparse
import json
import math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
PMOL_PER_PG=3.671

def q(t,ka,ke):
    if t<=0:return 0.0
    if not (math.isfinite(t) and math.isfinite(ka) and math.isfinite(ke) and ka>0 and ke>0):
        raise ValueError("invalid rate/time")
    delta=abs(ka-ke)
    ratio=1.0 if delta==0 else -math.expm1(-delta*t)/(delta*t)
    return ka*t*math.exp(-min(ka,ke)*t)*ratio

def fitted(t,p):
    if t<=0:return 0.0
    return math.fsum(x['A_per_mg']*math.exp(-x['lambda_per_h']*t)*
        -math.expm1(-(p['ka_per_h']-x['lambda_per_h'])*t) for x in p['terms'])

def models(params,s,meta):
    sl=params['models']['E2_SL'];oral=params['models']['E2_ORAL'];sw=sl['swallowed_share']
    current=lambda t:fitted(t,sl)+sw*fitted(t,oral)
    k=s['candidate_A']['canonical_k']
    gamma=lambda t:k*k*t*math.exp(-k*t)
    A=lambda t:144*gamma(t)/gamma(1)
    ka,kg,ke,w=s['candidate_B']['canonical']
    both=lambda t:(1-w)*q(t,ka,ke)+w*q(t,kg,ke)
    B=lambda t:144*both(t)/both(1)
    fm=meta['mucosal_fraction'];ff=meta['fast_bioavailability'];fg=meta['oral_bioavailability']
    scale=1e6/(80*meta['volume_l_per_kg'])
    feather=lambda t:scale*(fm*ff*q(t,meta['k_fast_per_h'],meta['k_elimination_per_h'])+
        (1-fm)*fg*q(t,meta['k_oral_per_h'],meta['k_elimination_per_h']))
    return {'HRT_current':current,'Candidate_A':A,'Candidate_B':B,'Featherline_80kg':feather}

def compare(evidence,params,s,meta):
    observations=evidence['observations']
    assert [(o['minutes'],o['mean_pmol_l']) for o in observations]==[(0,89.4),(15,486.6),(30,1969)]
    v={str(o['minutes']):o['mean_pmol_l']/PMOL_PER_PG for o in observations}
    b=v['0'];p15=v['15']-b;p30=v['30']-b;ratio=p30/p15
    assert p15>0 and p30>0
    out={}
    for name,h in models(params,s,meta).items():
        x15=2*h(.25);x30=2*h(.5)
        assert x15>0 and x30>0 and x30/x15<2
        out[name]={'increment_15_pg_ml':x15,'increment_30_pg_ml':x30,
          'ratio_30_over_15':x30/x15,'conditional_total_15_pg_ml':b+x15,'conditional_total_30_pg_ml':b+x30}
    return {'evidence_id':evidence['id'],'converted_group_mean_pg_ml':v,
     'observed_increment_15_pg_ml':p15,'observed_increment_30_pg_ml':p30,
     'observed_group_mean_ratio_30_over_15':ratio,'instantaneous_positive_kernel_bound':2.0,
     'central_group_mean_exceeds_bound':ratio>2,
     'hypothetical_shifted_linear_rise_necessary_lag_min':60*(ratio*.25-.5)/(ratio-1),
     'lag_is_not_estimated_from_human_physiology':True,'paired_covariance_available':False,
     'not_locked_external':True,'statistical_rejection_established':False,
     'clinical_accuracy_established':False,'conditional_model_predictions':out}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',required=True)
    a=parser.parse_args()
    e=json.loads((ROOT/'docs/pk-research/p2/p2d-komesaroff-table1.json').read_text())
    p=json.loads((ROOT/'pk-engine/src/main/resources/pk-params.json').read_text())
    s=json.loads((ROOT/'docs/pk-research/p2/experiment-settings.json').read_text())
    c=json.loads((ROOT/'docs/pk-research/p2/evidence-catalog.json').read_text())
    result=compare(e,p,s,c['comparison_model'])
    out=Path(a.out)
    if out.exists():raise ValueError("Refuse overwrite: "+str(out))
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,sort_keys=True,indent=2,ensure_ascii=False)+'\n')
    print("Research-only mathematical comparison; clinical validation false:",out)
if __name__=='__main__':main()
