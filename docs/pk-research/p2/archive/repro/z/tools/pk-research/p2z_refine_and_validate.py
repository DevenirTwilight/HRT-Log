"""Refine 3D clock ratios by local continuous minimization. Not global proof."""
import json,math,sys
from pathlib import Path
import numpy as np
from scipy.optimize import minimize
sys.path.insert(0,str(Path(__file__).resolve().parent))
import p2z_clock_envelope as z
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'docs/pk-research/p2/p2x-conditional-ensemble-results.json'

def min_ratio(p,gaps,days=120):
    g=np.asarray(gaps,float)
    clocks=np.array([[0.,g[0],g[0]+g[1],np.sum(g)]])
    x,y=z.periodic_response(p,clocks,1.5,days)
    d=y[0]-x[0]
    return float(x[0]/d) if d>0 else 1e10

def refine():
    rows=json.loads(SRC.read_text())['rows'];best=min(r['score'] for r in rows)
    selected=[(i,r) for i,r in enumerate(rows) if r['score']<=best+.1+1e-10]
    result=[]
    for i,r in selected:
        fun=lambda x:min_ratio(r['parameter'],x)
        # Nine prescribed starts; local optimizer, not global interval arithmetic.
        starts=[(a,b,c) for a in (2.5,4.,6.) for b,c in ((2.5,2.5),(4.,4.),(6.,6.))]
        fits=[minimize(fun,np.array(start),method='L-BFGS-B',bounds=[(2.5,6.)]*3,
                       options={'maxiter':120,'ftol':1e-12}) for start in starts]
        bestfit=min(fits,key=lambda x:x.fun)
        thr=z.YAISH['pre_pmol_L']/(z.YAISH['post_pmol_L']-z.YAISH['pre_pmol_L'])
        result.append({'fit_id':i,'lowest_ratio_local':float(bestfit.fun),
                       'local_optimal_day_gaps_hours':[float(x) for x in bestfit.x],
                       'overnight_gap_hours':float(24-np.sum(bestfit.x)),
                       'nominal_threshold':thr,
                       'eligible_at_refined_clock':bool(bestfit.fun<=thr),
                       'global_optimality_certified':False})
    return {'phase':'P2-Z supplementary local clock feasibility','results':result,
      'n_eligible_local':sum(x['eligible_at_refined_clock'] for x in result),
      'not_global_proof':True}

if __name__=='__main__':
    a=refine()
    out=ROOT/'docs/pk-research/p2/p2z-refined-clock-results.json'
    if out.exists():raise FileExistsError('no overwrite')
    out.write_text(json.dumps(a,ensure_ascii=False,indent=2)+'\n')
    print('models',len(a['results']),'eligible local',a['n_eligible_local'])
    for r in a['results']:
        print(r['fit_id'],round(r['lowest_ratio_local'],4),round(r['overnight_gap_hours'],2),r['eligible_at_refined_clock'])
