#!/usr/bin/env python3
"""Independent nested-grid stress test of P2-AF 48h synthetic identifiability bounds."""
import argparse,json
from pathlib import Path
import numpy as np
import p2af_joint_ke_ks as m


def run(ds):
    rows=m.rows_selected(ds)
    saved_slow=m.SLOW_GRID.copy();saved_central=m.CENTRAL_GRID.copy()
    base=[];dense=[]
    try:
        for r in rows:
            prof,meta=m.sample_grid(r,'to48_pre',3.,.125,True)
            base.append(m.summary_one(r,prof,meta))
        m.SLOW_GRID=np.unique(np.r_[np.geomspace(.025,.8,73),saved_slow])
        m.CENTRAL_GRID=np.unique(np.r_[np.geomspace(.5,2.,45),saved_central])
        for i,r in enumerate(rows):
            prof,meta=m.sample_grid(r,'to48_pre',3.,.125,True)
            new=m.summary_one(r,prof,meta)
            for k in m.NAMES:
                old=base[i]['metric_envelopes'][k]; v=new['metric_envelopes'][k]
                if old['min']+1e-9 < v['min'] or old['max']-1e-9 > v['max']:
                    raise AssertionError(('refined grid lost original feasible model',r['id'],k,old,v))
            assert new['n_feasible']>=base[i]['n_feasible']
            dense.append(new)
    finally:
        m.SLOW_GRID=saved_slow;m.CENTRAL_GRID=saved_central
    out={}
    for metric in ('auc_0_8_h','auc_0_inf_h','tail_area_fraction_after24','q24_predose_index','H24'):
        q=[]
        for a,b in zip(base,dense):
            old=a['metric_envelopes'][metric];new=b['metric_envelopes'][metric]
            q.append(dict(id=a['id'],coarse_ratio=old['spread_ratio'],fine_ratio=new['spread_ratio'],
               coarse_min=old['min'],fine_min=new['min'],coarse_max=old['max'],fine_max=new['max']))
        out[metric]=dict(coarse_median_ratio=float(np.median([x['coarse_ratio'] for x in q])),
            refined_median_ratio=float(np.median([x['fine_ratio'] for x in q])),
            increases_per_15=int(sum(x['fine_ratio']>x['coarse_ratio']*(1+1e-10) for x in q)),rows=q)
    return dict(phase='P2-AF-grid-stress',scenario='synthetic Price anchors; to48_pre; sd=max(3 pg/ml,0.125*y)',
        safe_nested_grid=True,all_coarse_feasible_are_also_fine=True,
        coarse_slow_grid_n=len(saved_slow),refined_slow_grid_n=73,
        coarse_central_grid_n=len(saved_central),refined_central_grid_n=45,
        note='feasible rate spans and conditional output envelopes may expand on refinement; never assume coarse envelopes exhaustive',
        coarse_median_feasible=float(np.median([x['n_feasible'] for x in base])),
        refined_median_feasible=float(np.median([x['n_feasible'] for x in dense])),results=out)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=m.ROOT/'data/p2af-grid-stress.json');a=p.parse_args()
    if a.output.exists():raise FileExistsError('refusing overwrite')
    result=run(json.loads(m.DATA.read_text()))
    a.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
    for k,x in result['results'].items(): print(k,x['coarse_median_ratio'],x['refined_median_ratio'])
