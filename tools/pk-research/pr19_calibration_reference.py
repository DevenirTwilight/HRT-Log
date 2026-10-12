"""Independent scipy reference, frozen CSV/JSON inputs, not the Kotlin kernel.
No individual data, personal calibration, new clinical evidence or parameter fit.
"""
import csv, hashlib, json, math
from pathlib import Path
import numpy as np
from scipy.special import gammainc
from scipy.optimize import minimize_scalar
from scipy.integrate import quad
ROOT=Path(__file__).resolve().parents[2]
M2_FILE=ROOT/'docs/pk-research/p2/p2x-frozen-40-candidates.csv'
M1_FILE=ROOT/'docs/design/unified-model-inputs/m1-candidates.json'
FOLDS_FILE=ROOT/'docs/design/pr19-calibration-evidence/p2az-results.json'
T=np.array([1,2,3,4,6,8,12,18,24],float)
Y=np.array([450,225,116,85,56,45,34,25,24],float)
SIGMA=np.maximum([15,32.5,12,8.5,7,6.5,6,5.5,5.5],.15*Y)
def response(t,p):
 t=np.maximum(np.asarray(t,dtype=float),0)
 n,k,e,s,w=p['n'],p['kf'],p['ke'],p['ks'],p['w']
 def raw(t):
  f=(k/(k-e))**n*np.exp(-e*t)*gammainc(n,(k-e)*t)
  q=s*t*np.exp(-e*t) if abs(s-e)<1e-9*max(s,e) else s*np.exp(-min(s,e)*t)*(-np.expm1(-abs(s-e)*t))/abs(s-e)
  return (1-w)*f+w*q
 return raw(t)/raw(1.)
def amplitude(p,b):
 h=response(T,p);v=1/SIGMA**2
 return max(0,float(np.sum(v*h*(Y-b))/np.sum(v*h*h)))
def metrics(fn,b=0.):
 # Global bracketing 10-second grid plus independent bounded local searches.
 grid=np.linspace(0,24,8641);y=np.asarray([fn(t) for t in grid]);options=[(float(y[0]),0.),(float(y[-1]),24.)]
 for i in range(1,len(grid)-1):
  if y[i]>y[i-1] and y[i]>=y[i+1]:
   r=minimize_scalar(lambda t:-fn(t),bounds=(grid[i-1],grid[i+1]),method='bounded',options={'xatol':1e-12})
   options.append((float(-r.fun),float(r.x)))
 peak,t=max(options,key=lambda v:v[0]);auc=quad(lambda t:float(fn(t)-b),0,24,epsabs=1e-8,epsrel=1e-10)[0]
 return {'cmax_pgml':peak,'tmax_h':t,'auc_increment_0_24':auc,'auc_total_0_24':auc+24*b,'at1h_pgml':float(fn(1.))}
def candidates():
 m2=[]
 for i,r in enumerate(csv.DictReader(M2_FILE.open())):
  m2.append(dict(id=f'p2x-{i}',n=int(r['n']),kf=float(r['k_fast_per_h']),ke=float(r['ke_per_h']),ks=float(r['k_slow_per_h']),w=float(r['slow_effective_weight']),b=float(r['Price_baseline']),cap=float(r['Rosano_baseline_cap']),loss=float(r['pseudo_loss']),frozen_auc=float(r['Price_model_auc'])))
 m1=[dict(r,b=0.,cap=0.,loss=0.) for r in json.loads(M1_FILE.read_text())['rows']]
 return m2,m1
def signature(p):
 return '|'.join([p['id'],str(p['n']),*[str(float(p[k])) for k in ('kf','ke','ks','w','loss','b','cap')]])
def run():
 m2,m1=candidates();minimum=min(p['loss'] for p in m2);retained=[p for p in m2 if p['loss']<=minimum+.1]
 folds=json.loads(FOLDS_FILE.read_text())['folds'];counts={p['id']:sum(f['scores']['M1']['candidate_index']==i for f in folds) for i,p in enumerate(m1)}
 # Research representative: lowest frozen exposed loss; M1 modal leave-study-out choice,
 # counts not independent humans/trials. Tie ID ascending. No clinical winner claim.
 chosen_m2=min(retained,key=lambda p:(p['loss'],p['id']))
 chosen_m1=min(m1,key=lambda p:(-counts[p['id']],p['id']))
 rows=[]
 for family,ps in [('M2',retained),('M1',m1)]:
  for p in ps:
   b=p['b'] if family=='M2' else chosen_m2['b'];a=amplitude(p,b)
   row=dict(p,family=family,anchor_b=b,anchor_a=a,signature=signature(p),parameter_sha256=hashlib.sha256(signature(p).encode()).hexdigest(),modal_count=counts.get(p['id']),price=metrics(lambda t:b+a*response(t,p),b),common=metrics(lambda t:25+100*response(t,p),25),response_at8=float(response(8,p)),response_at24=float(response(24,p)),values=[float(b+a*response(t,p)) for t in (0,.123,.5,1,1.37,2,4,8,12,24)],background_sensitivity={str(b0):metrics(lambda t:b0+amplitude(p,b0)*response(t,p),b0) for b0 in (0.,12.,24.)})
   if family=='M2':
    # Match historical 1201-point integration explicitly, independently of quad.
    grid=np.linspace(0,24,1201);frozen_replay=float(np.trapezoid(a*response(grid,p),grid))
    if abs(frozen_replay-p['frozen_auc'])>1e-6:raise AssertionError(p['id'])
    row['frozen_auc_replay']=frozen_replay
   rows.append(row)
 params=json.loads((ROOT/'pk-engine/src/main/resources/pk-params.json').read_text());sl=params['models']['E2_SL'];oral=params['models']['E2_ORAL']
 def legacy(t):
  return sum(term['A_per_mg']*(math.exp(-term['lambda_per_h']*t)-math.exp(-m['ka_per_h']*t))*share for m,share in [(sl,1.),(oral,sl['swallowed_share'])] for term in m['terms'])
 return {'schema':'pr19-price-reference-v1','default_m2':chosen_m2['id'],'default_m1':chosen_m1['id'],'m1_rule':'modal unpenalized M1 selection in 54 exposed sensitivity folds; not 54 independent studies','m2_rule':'minimum frozen exposed P2-X pseudo-loss, tie ID ascending; representative not clinical winner','m1_baseline_rule':'same explicit assumed background as selected M2 representative; new Price projection, not original P2-AZ absolute fit','times_h':[0,.123,.5,1,1.37,2,4,8,12,24],'input_hashes':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (M2_FILE,M1_FILE,FOLDS_FILE)},'legacy':dict(version=params['version'],metrics=metrics(legacy)),'rows':rows}
if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser();p.add_argument('--out',required=True);args=p.parse_args();result=run();Path(args.out).write_text(json.dumps(result,indent=2)+'\n')
 print(result['default_m2'],result['default_m1']);print('Legacy',result['legacy'])
 for r in result['rows']:
  if r['id'] in (result['default_m2'],result['default_m1'],'p2x-14','p2az-m1-00'):print(r['id'],r['signature'],r['parameter_sha256'],r['anchor_b'],r['anchor_a'],r['price'],r['common'])
