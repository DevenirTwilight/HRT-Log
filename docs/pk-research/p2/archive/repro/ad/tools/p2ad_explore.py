import sys,json, numpy as np
from pathlib import Path
sys.path.insert(0,'/mnt/data/p2x_stage/tools/pk-research')
from p2v_continuous_fit import shape
from scipy.optimize import nnls
j=json.load(open('/mnt/data/p2x_stage/docs/pk-research/p2/p2x-conditional-ensemble-results.json'))
rows=[(i,r) for i,r in enumerate(j['rows']) if r['score']<=j['summary']['global_best_loss']+.1+1e-9]
P=[x['parameter'] for _,x in rows]
B=np.array([x['fit']['Price1997_figure1']['baseline'] for _,x in rows]);A=np.array([x['fit']['Price1997_figure1']['amplitude_at_1h'] for _,x in rows]);ids=[i for i,x in rows]
times=np.array([0,1,2,4,6,8,12,18,24,36,48,72]);H=np.array([shape(times,p) for p in P]);C=B[:,None]+A[:,None]*H; R=A[:,None]*H
print('n',len(rows),'ids',ids)
print('times: t min max raw total, min max incremental, max/min incremental')
for k,t in enumerate(times):
 v=C[:,k]; inc=R[:,k];print(t,round(v.min(),2),round(v.max(),2),round(inc.min(),3),round(inc.max(),3),round(inc.max()/inc.min(),1) if inc.min()>0 else 'NA')

def compare(ts):
 ts=np.array(ts,float)
 h=np.array([shape(ts,p) for p in P])
 y=B[:,None]+A[:,None]*h
 sigma=np.maximum(3.0,0.125*y)  # hypo
 md=[]
 by=[]
 for a in range(len(rows)):
  for b in range(a+1,len(rows)):
   # directed, each true source with own sigma
   vals=[]
   for idx_truth,idx_alt in [(a,b),(b,a)]:
    target=y[idx_truth]
    D=np.stack([np.ones_like(ts),h[idx_alt]],axis=1)
    fit=nnls(D/sigma[idx_truth,None].T,target/sigma[idx_truth])[0]
    vals.append(np.linalg.norm((D@fit-target)/sigma[idx_truth]))
   md.append(min(vals))
   by.append((min(vals),ids[a],ids[b]))
 return np.array(md), sorted(by,reverse=True)
configs={
 'early':[1,2,4,8],
 'early_mid':[1,2,4,8,12,24],
 'early_plus_36':[1,2,4,8,12,24,36],
 'early_plus_48':[1,2,4,8,12,24,48],
 'early_plus_36_48':[1,2,4,8,12,24,36,48],
 'baseline_early':[0,1,2,4,8],
 'baseline_full':[0,1,2,4,8,12,24],
 'baseline_late':[0,1,2,4,8,12,24,36,48],
}
for key,tt in configs.items():
 d,b=compare(tt)
 print(key,'median',round(np.median(d),3),'min',round(d.min(),3),'Q10',round(np.quantile(d,.1),3),'max',round(d.max(),3),'n>=2',int((d>=2).sum()),'n>=3',int((d>=3).sum()),'n>=5',int((d>=5).sum()))
