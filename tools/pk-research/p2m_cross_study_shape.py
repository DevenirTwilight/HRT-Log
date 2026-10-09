"""P2-M: two-study early PK shape audit, no Doll calibration, no clinical model fit."""
import argparse,json,math
from pathlib import Path
import models as m
ROOT=Path(__file__).resolve().parents[2]
PATHS=[
 ROOT/"docs/pk-research/p2/p2m-study-shape-design.json",
 ROOT/"docs/pk-research/p2/p2e-source-metrics.json",
 ROOT/"docs/pk-research/p2/p2d-komesaroff-table1.json",
 ROOT/"docs/pk-research/p2/p2i-price-figure-points.json",
 ROOT/"pk-engine/src/main/resources/pk-params.json",
 ROOT/"docs/pk-research/p2/evidence-catalog.json"]

def gamma(t,n,k):
    if not isinstance(n,int) or n<2 or not math.isfinite(k) or k<=0:raise ValueError("bad gamma")
    if not math.isfinite(t) or t<0:raise ValueError("bad time")
    return 0. if t==0 else math.exp((n-1)*math.log(t)-k*(t-1))

def sources(ros,kom):
    if ros["Rosano1997"]["n_PK"]!=25 or kom["n"]!=10:raise ValueError("wrong cohort")
    if "SD" not in ros["Rosano1997"]["dispersion"] or kom["reported_dispersion"]!="SEM":
        raise ValueError("invalid dispersion unit")
    a=[(r["minutes"]/60,r["mean_pmol_l"],r["sd_pmol_l"]/5) for r in ros["Rosano1997"]["points"]]
    b=[(r["minutes"]/60,r["mean_pmol_l"],r["sem_pmol_l"]) for r in kom["observations"] if r["minutes"]>0]
    baseline=next(r["mean_pmol_l"] for r in kom["observations"] if r["minutes"]==0)
    return a,b,baseline

def ampfit(obs,baseline,curve):
    if not math.isfinite(baseline) or baseline<0:raise ValueError("baseline")
    pts=[(curve(t),y-baseline,se) for t,y,se in obs]
    if any(not all(map(math.isfinite,p)) or p[2]<=0 for p in pts):
        raise ValueError("invalid values")
    den=math.fsum(v*v/se**2 for v,y,se in pts)
    if den<=0:raise ValueError("unidentifiable")
    amp=max(0.,math.fsum(v*y/se**2 for v,y,se in pts)/den)
    res=[(amp*v-y)/se for v,y,se in pts]
    return {"amplitude":amp,"marginal_SE_pseudoloss":math.fsum(x*x for x in res)/len(res)}

def cross(curve,r,k,kom_baseline,ros_baselines):
    kom=ampfit(k,kom_baseline,curve)
    best=min((dict(ampfit(r,b,curve),assumed_baseline=b) for b in ros_baselines),
             key=lambda x:x["marginal_SE_pseudoloss"])
    return {"score":kom["marginal_SE_pseudoloss"]+best["marginal_SE_pseudoloss"],
            "Rosano":best,"Komesaroff":kom}

def audit(design,ros,kom,price,params,catalog):
    r,k,b=sources(ros,kom)
    grid=design["k_per_h_grid"]
    ks=[grid["min"]+i*grid["step"] for i in range(1+round((grid["max"]-grid["min"])/grid["step"]))]
    winners=[]
    for cap in design["baseline_caps"]:
        bs=[v for v in design["Rosano_baseline_pmol_l"] if v<=cap]
        candidates=[]
        for n in design["orders"]:
            for rate in ks:
                v=cross(lambda t,n=n,rate=rate:gamma(t,n,rate),r,k,b,bs)
                candidates.append(dict(v,order=n,k_h=rate))
        winners.append({"Rosano_baseline_cap_pmol_l":cap,"best":min(candidates,key=lambda x:x["score"])})
    meta=catalog["comparison_model"]
    fixed={"HRT":lambda t:m.current(t,params),
           "Featherline_80kg":lambda t:m.feather(t,meta,80)}
    frozen={}
    for name,f in fixed.items():
        f1=f(1)
        z=lambda t,f=f,f1=f1:f(t)/f1
        frozen[name]={"raw1h_pg_ml_per_mg":f1,"growth_15_to_30":z(.5)/z(.25),
                     "growth_20_to_40":z(2/3)/z(1/3),
                     "study_local_amplitude_shape_fit":cross(z,r,k,b,design["Rosano_baseline_pmol_l"])}
    best=winners[-1]["best"]
    p=price["plot_points"]
    c1=next(v["central"] for v in p if v["t"]==1)
    c2=next(v["central"] for v in p if v["t"]==2)
    se20=r[1][2];se40=r[2][2]
    return {"purpose":"EXPOSED_SHAPE_STRESS_TEST_NO_DOLL_TRAINING",
       "Rosano_40_over_20_total_ratio":r[2][1]/r[1][1],
       "Rosano_marginal_1SE_endpoint_ratio_sensitivity":[(r[2][1]-se40)/(r[1][1]+se20),(r[2][1]+se40)/(r[1][1]-se20)],
       "Komesaroff_30_over_15_baseline_corrected_mean_ratio":(k[1][1]-b)/(k[0][1]-b),
       "winning_shape_by_Rosano_assumed_baseline_cap":winners,
       "frozen_model_shape_checks":frozen,
       "high_baseline_winner_gamma_2h_over_1h_ratio":gamma(2,best["order"],best["k_h"]),
       "Price_figure_2h_over_1h_hypothetical_baselines":[{"baseline_NOT_observed":v,"ratio":(c2-v)/(c1-v)} for v in [0,25,50]],
       "pseudo_loss_is_not_chi_square_or_validated_generalization":True,
       "Price_figure_vs_Table1_AUC_unresolved":True,
       "new_locked_external":0,"clinical_accuracy_established":False,"production_change_authorized":False}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out",required=True)
    a=parser.parse_args()
    inputs=[json.loads(path.read_text()) for path in PATHS]
    res=audit(*inputs)
    out=Path(a.out)
    if out.exists():raise ValueError("No overwrite")
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(res,indent=2,ensure_ascii=False,sort_keys=True)+"\n")
    print("P2-M conditional cross-study shape audit:",out)
if __name__=="__main__":main()
