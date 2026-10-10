#!/usr/bin/env python3
"""Figures derived only from stored P2-AF synthetic mathematical outputs."""
from pathlib import Path
import json
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from p2af_joint_ke_ks import ROOT,DATA,rows_selected,synthetic_total,PROTOCOLS,CENTRAL_GRID,SLOW_GRID
from p2v_continuous_fit import erlang,bateman
from scipy.optimize import nnls

STATS=json.loads((ROOT/'data/p2af-joint-parameter-functions.json').read_text())
GRID=json.loads((ROOT/'data/p2af-grid-stress.json').read_text())
DEST=ROOT/'figures'; DEST.mkdir(exist_ok=True)

def plots():
    labels=['08h nominal','24h nominal','48h nominal','48h high precision']
    keys=['08h_nominal','24h_nominal','48h_nominal','48h_high_precision']
    fig,ax=plt.subplots(figsize=(10.8,5.8))
    x=np.arange(len(labels))
    for n,(metric,label) in enumerate([('auc_0_8_h','AUC 0–8 h'),('auc_0_inf_h','AUC 0–infinity'),('q24_predose_index','q24 predose index')]):
        v=[STATS['aggregate'][k]['metrics'][metric]['median_internal_spread_ratio'] for k in keys]
        ax.bar(x+(n-1)*.26,v,width=.245,label=label)
    ax.set_xticks(x,labels);ax.set_yscale('log');ax.set_ylim(1,2000)
    ax.set_ylabel('Median within-candidate max/min ratio (log scale)')
    ax.set_title('Synthetic compatibility: early AUC vs very late residual shape')
    ax.grid(alpha=.22,axis='y');ax.legend();fig.tight_layout()
    fig.savefig(DEST/'p2af-functional-uncertainty.png',dpi=170);plt.close(fig)

    items=[('48h nominal, ke fixed','48h_nominal_fixed_ke'),('48h nominal, ke free','48h_nominal'),('48h high precision, ke free','48h_high_precision')]
    fig,ax=plt.subplots(figsize=(9.5,5.1));x=np.arange(3)
    for n,(metric,label) in enumerate([('auc_0_8_h','AUC0–8'),('q24_predose_index','q24 predose')]):
        values=[STATS['aggregate'][key]['metrics'][metric]['median_internal_spread_ratio'] for _,key in items]
        ax.bar(x+(n-.5)*.33,values,width=.32,label=label)
    ax.set_xticks(x,[z[0] for z in items]);ax.set_yscale('log');ax.set_ylim(1,100)
    ax.set_ylabel('Median within-candidate spread ratio (log)');ax.grid(alpha=.22,axis='y');ax.legend()
    ax.set_title('Fixing central elimination artificially narrows some output envelopes')
    fig.tight_layout();fig.savefig(DEST/'p2af-fixed-vs-free-elimination.png',dpi=170);plt.close(fig)

    fig,ax=plt.subplots(figsize=(10.5,5.2));labels=['AUC0–8','AUC0–infinity','Tail area after 24h','q24 predose','H(24h)']
    ms=['auc_0_8_h','auc_0_inf_h','tail_area_fraction_after24','q24_predose_index','H24'];x=np.arange(len(ms))
    for n,key in enumerate(['coarse_median_ratio','refined_median_ratio']):
        vals=[GRID['results'][metric][key] for metric in ms]
        ax.bar(x+(n-.5)*.35,vals,width=.33,label=['37x23 grid','73x45 grid'][n])
    ax.set_xticks(x,labels);ax.set_yscale('log');ax.set_ylim(1,200)
    ax.set_title('Nested-grid test: denser search widens long-tail compatibility bounds')
    ax.grid(alpha=.24,axis='y');ax.set_ylabel('Median within-candidate max/min ratio (log)');ax.legend()
    fig.tight_layout();fig.savefig(DEST/'p2af-grid-sensitivity.png',dpi=170);plt.close(fig)

    # A representative rate-residual plane; generated independently of stored envelopes.
    r=next(v for v in rows_selected(json.loads(DATA.read_text())) if v['id']=='p2x-22')
    t=np.asarray(PROTOCOLS['to48_pre']);y=synthetic_total(r,t);sd=np.maximum(3,.125*y)
    ke=np.unique(np.r_[CENTRAL_GRID,r['params']['k_elim_per_h']]);ks=np.unique(np.r_[SLOW_GRID,r['params']['k_slow_per_h']]);z=np.empty((len(ks),len(ke)))
    for j,k in enumerate(ke):
        f=erlang(t,r['params']['n_fast'],r['params']['k_fast_per_h'],float(k))
        for i,s in enumerate(ks):
            mat=np.column_stack((np.ones(len(t)),f,bateman(t,float(s),float(k))))
            u,_=nnls(mat/sd[:,None],y/sd)
            z[i,j]=np.linalg.norm((mat@u-y)/sd)
    fig,ax=plt.subplots(figsize=(8.8,6))
    mesh=ax.contourf(ke,ks,z,levels=[0,.5,1,1.5,2,3,5,10,100],extend='max')
    ax.contour(ke,ks,z,levels=[2],linewidths=2)
    ax.plot([r['params']['k_elim_per_h']],[r['params']['k_slow_per_h']],marker='o',linestyle='None',label='synthetic source rates')
    ax.set_yscale('log');ax.set_xscale('log');ax.set_xlabel('Central elimination rate ke [1/hour]');ax.set_ylabel('Slow absorption input rate ks [1/hour]')
    ax.set_title('p2x-22: two-rate nuisance profile, 48h synthetic samples')
    fig.colorbar(mesh,ax=ax,label='Standardized fit distance (threshold 2 is illustrative)')
    ax.legend(loc='upper right');fig.tight_layout();fig.savefig(DEST/'p2af-rate-feasibility-p2x22.png',dpi=170);plt.close(fig)

if __name__=='__main__':plots()
