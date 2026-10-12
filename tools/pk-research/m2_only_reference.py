"""Independent M2-only product audit. Read-only frozen/group data, no personal calibration.
Evaluates every P2-X retained M2 and P2-AZ dual-input grid member plus archived P2-V.
Price amplitudes are projections, not individual pharmacokinetic estimates.
"""
import argparse, hashlib, itertools, json, zipfile, re, math
from collections import Counter
from pathlib import Path
import numpy as np
from scipy.integrate import quad
from scipy.optimize import minimize_scalar
from pr19_calibration_reference import ROOT, response, amplitude, candidates, signature

def metrics(p, b, dose=1.):
    a=amplitude(p,b)
    def value(t):return b+dose*a*response(t,p)
    grid=np.linspace(0,24,8641); y=value(grid)
    options=[(float(y[0]),0.),(float(y[-1]),24.)]
    for i in np.flatnonzero((y[1:-1]>y[:-2]) & (y[1:-1]>=y[2:]))+1:
        r=minimize_scalar(lambda t:-float(value(t)),bounds=(grid[i-1],grid[i+1]),method='bounded',options={'xatol':1e-12})
        options.append((-float(r.fun),float(r.x)))
    peak,tmax=max(options)
    inc={str(h):quad(lambda t:float(value(t)-b),0,h,epsabs=1e-8,epsrel=1e-10)[0] for h in (4,8,24)}
    return {'dose_mg':dose,'background_pgml':b,'amplitude_pgml':a,'cmax_pgml':peak,'tmax_h':tmax,
        'auc_increment_pg_h_ml':inc,'auc_total_pg_h_ml':{h:v+b*float(h) for h,v in inc.items()},
        'values_pgml':{str(t):float(value(t)) for t in (0,.25,.5,1,2,4,8,12,24)},
        'q6_relative_trough':float(sum(response(6*i,p) for i in range(1,121))),
        'auc_infinity_relative_h':float(quad(lambda t:float(response(t,p)),0,np.inf,epsabs=1e-9)[0])}

def run():
    source=ROOT/'docs/design/unified-model-inputs/p2az-design.json'
    folds_file=ROOT/'docs/design/pr19-calibration-evidence/p2az-results.json'
    archive=ROOT/'docs/pk-research/p2/history-2026-10-10/source-packages/P2-V-research-source.zip'
    m2,_=candidates();minimum=min(p['loss'] for p in m2); retained=[p for p in m2 if p['loss']<=minimum+.1]
    selected=min(retained,key=lambda p:(p['loss'],p['id']))
    assert selected['id']=='p2x-39'
    kotlin_file=ROOT/'pk-engine/src/main/kotlin/net/plainnotes/app/pk/experimental/ResearchSublingualV01.kt'
    literals=re.search(r'ExperimentalCandidate\("p2x-39",\s*([^\)]+)\)',kotlin_file.read_text()).group(1).split(',')
    runtime_values=[int(literals[0])]+[float(v) for v in literals[1:]]
    csv_values=[selected[k] for k in ('n','kf','ke','ks','w','loss','b','cap')]
    assert len(runtime_values)==len(csv_values)
    assert all(math.isclose(a,b,rel_tol=1e-14,abs_tol=1e-15) for a,b in zip(runtime_values,csv_values))
    runtime_signature='p2x-39|'+'|'.join(str(v) for v in runtime_values)
    runtime_hash=hashlib.sha256(runtime_signature.encode()).hexdigest()
    product_lock=json.loads((ROOT/'docs/design/pr19-calibration-evidence/product-lock.json').read_text())
    assert runtime_signature==product_lock['M2']['runtime_signature']
    assert runtime_hash==product_lock['M2']['runtime_parameter_sha256']
    params=json.loads(source.read_text())['parameter_grid'];folds=json.loads(folds_file.read_text())['folds']
    modal=Counter(f['scores']['M2']['candidate_index'] for f in folds)
    rows=[]
    for p in retained:
        rows.append({'family':'P2-X','id':p['id'],'parameters':p,'parameter_sha256':hashlib.sha256(signature(p).encode()).hexdigest(),
            'metrics':metrics(p,p['b']), 'background_sensitivity':[metrics(p,b) for b in (0.,12.,24.)]})
    with zipfile.ZipFile(archive) as z:
        file=next(n for n in z.namelist() if n.endswith('p2v-candidate-parameters.json'))
        raw=z.read(file);v=json.loads(raw)
    for name,original in [('primary',v['primary_default_search']['M2']),('expanded',v['expanded_order_search']['M2']['parameters'])]:
        p={'n':original['n_fast'],'kf':original['k_fast_per_h'],'ke':original['k_elim_per_h'],'ks':original['k_slow_per_h'],'w':original['effective_slow_weight']}
        rows.append({'family':'P2-V','id':'p2v-'+name,'parameters':p,'metrics':metrics(p,24.),'note':'new common-B24 Price projection for comparison; archived original study-specific amplitudes not overwritten'})
    grid=itertools.product([n for n in params['n_fast'] if n>1],params['k_fast_per_h'],params['k_elim_per_h'],params['slow_k_per_h'],params['effective_slow_weight'])
    for i,(n,k,e,s,w) in enumerate(grid):
        p={'n':n,'kf':k,'ke':e,'ks':s,'w':w}
        rows.append({'family':'P2-AZ','id':f'p2az-m2-{i:03d}','parameters':p,'modal_54fold_count':modal[i],
            'metrics':metrics(p,24.),'note':'common-B24 Price projection, not a frozen P2-X candidate or a validated absolute prediction'})
    return {'schema':'m2-only-product-audit-v1','selected_candidate':selected['id'],
        'rule':'retain the explicit existing frozen P2-X research representative with minimum exposed pseudo-loss, tie by ID; stability and source reproducibility, not clinical superiority',
        'model_version':'p2x-m2-research-v0.1','display_protocol':'price-figure-weighted-history-v1',
        'parameter_signature':runtime_signature,'parameter_sha256':runtime_hash,
        'frozen_csv_parameter_signature':signature(selected),'frozen_csv_parameter_sha256':hashlib.sha256(signature(selected).encode()).hexdigest(),
        'serialization_note':'CSV and Kotlin source literals have existing last-digit rounding differences; keep distinct exact textual SHA identities and independently verify numeric agreement. No frozen source or tolerance changed.',
        'independent_single_dose':[metrics(selected,selected['b'],d) for d in (1.,2.)],
        'input_hashes':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (source,folds_file,archive,kotlin_file)},
        'p2v_json_inside_archive_sha256':hashlib.sha256(raw).hexdigest(),'compared_rows':rows,
        'limitations':['Three exposed group studies; no independent personal absolute validation.',
            'P2-AZ M2 beats M1 in 48/54 correlated folds, with six Rosano counterexamples; not always superior.',
            'P2-X candidate re-ranking is contaminated diagnostic, not blind leave-study-out.',
            'Price Figure/Table AUC conflict and baseline/8–24h repeated-dose uncertainty retained.',
            'B24 projections for P2-V/AZ are this audit comparisons, not changes to archived fits or product parameters.']}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',required=True);args=p.parse_args()
    result=run();Path(args.out).write_text(json.dumps(result,indent=2)+'\n')
    print(result['selected_candidate'],result['parameter_sha256'])
    for row in result['independent_single_dose']:print(row)
