"""P2-K: symmetric evidence audit of HRT and Featherline defaults.

Nonclinical observational comparison of exposed published group statistics,
NOT a blind holdout, not a fitted physiological model. Stdlib only.
"""
import argparse, json, math
from pathlib import Path
import models as m
ROOT=Path(__file__).resolve().parents[2]
FIG=ROOT/"docs/pk-research/p2/p2i-price-figure-points.json"
PRICE=ROOT/"docs/pk-research/p2/p2e-source-metrics.json"
META=ROOT/"docs/pk-research/p2/evidence-catalog.json"
PARAMS=ROOT/"pk-engine/src/main/resources/pk-params.json"
GRID=(0,1,2,3,4,6,8,12,18,24)

def trapezoid(fn):
    return math.fsum((b-a)*(fn(a)+fn(b))/2 for a,b in zip(GRID,GRID[1:]))

def mean_absolute_error(obs,kernel,assumed_baseline):
    assert assumed_baseline>=0
    obs=[x for x in obs if x["t"]>0]
    return math.fsum(abs(kernel(r["t"])+assumed_baseline-r["central"]) for r in obs)/len(obs)

def audit(fig,price,params,metadata):
    p=price["Price1997"]
    assert [z["t"] for z in fig["plot_points"]]==list(GRID)
    assert p["n"]==6 and p["baseline_subtracted_for_pk"] if "baseline_subtracted_for_pk" in p else p["subtracted_pretreatment_baseline"]
    meta=metadata["comparison_model"]
    current=lambda t:m.current(t,params)
    feather=lambda t:m.feather(t,meta,80)
    outcome={"evidence_role":"EXPOSED_CONDITIONAL_COMPARISON_NOT_BLIND_VALIDATION",
             "Featherline_weight_kg_assumed":80,
             "source_table_price_sampled_AUC":[],
             "dose1_one_hour_increment_predictions":{"HRT":current(1),"Featherline":feather(1),"Doll_total_observed":144,"Price_fig_manual_total_approx":450},
             "price_figure_nine_point_baseline_sensitivity":[],
             "rosano_increment_40_over_20":{"HRT":current(2/3)/current(1/3),"Featherline":feather(2/3)/feather(1/3),"observed_raw_40_over_20":1980/468,"human_baseline_unknown":True},
             "new_independent_locked_external_human_cohorts":0,
             "clinical_accuracy_established":False,"model_replacement_authorized":False}
    for row in p["sublingual_E2"]:
        dose=row["dose_mg"];actual=row["auc0_24_mean_pg_h_ml"]
        hc=dose*trapezoid(current);fc=dose*trapezoid(feather)
        outcome["source_table_price_sampled_AUC"].append({"dose_mg":dose,"observed_mean":actual,
          "reported_SD":row["auc0_24_sd_pg_h_ml"],"HRT":hc,"Featherline":fc,
          "Featherline_is_closer":abs(fc-actual)<abs(hc-actual)})
    for b in (0,20,24):
        outcome["price_figure_nine_point_baseline_sensitivity"].append({
          "assumed_baseline_pg_ml_NOT_price_measured":b,
          "Featherline_MAE":mean_absolute_error(fig["plot_points"],feather,b),
          "HRT_MAE":mean_absolute_error(fig["plot_points"],current,b),
          "NOT_an_independent_human_validation":True})
    return outcome

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--out",required=True)
    a=ap.parse_args()
    data=[json.loads(p.read_text()) for p in (FIG,PRICE,PARAMS,META)]
    out=audit(*data)
    dst=Path(a.out)
    if dst.exists():raise ValueError("Do not overwrite reports")
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(json.dumps(out,sort_keys=True,indent=2,ensure_ascii=False)+"\n")
    print("Fair model comparison, exposed endpoints only:",dst)
if __name__=="__main__": main()
