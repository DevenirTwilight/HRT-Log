"""P2-J: baseline/AUC algebra for Price 1997; research-only, NOT a PK fit."""
import argparse
import json
import math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"docs/pk-research/p2/p2i-price-figure-points.json"

def weights(t):
    if len(t)<2 or t[0]!=0 or t[-1]!=24 or any(b<=a for a,b in zip(t,t[1:])):
        raise ValueError("strict 0-24 grid needed")
    return [(t[1]-t[0])/2]+[(t[i+1]-t[i-1])/2 for i in range(1,len(t)-1)]+[(t[-1]-t[-2])/2]

def corrected(rows,key,b):
    if not math.isfinite(b) or b<0:raise ValueError("predose must be nonnegative")
    w=weights([r["t"] for r in rows])
    concentrations=[b]+[r[key] for r in rows[1:]]
    if any(not math.isfinite(c) or c<0 for c in concentrations):
        raise ValueError("invalid concentration")
    return math.fsum(wi*ci for wi,ci in zip(w,concentrations))-24*b

def audit(source):
    rows=source["plot_points"]
    grid=[r["t"] for r in rows]
    if grid!=[0,1,2,3,4,6,8,12,18,24]:raise ValueError("wrong published grid")
    if any(not (0<=r["lo"]<=r["central"]<=r["hi"]) for r in rows):
        raise ValueError("bad manual read range")
    w=weights(grid)
    means={k:corrected(rows,k,0) for k in ("lo","central","hi")}
    table=source["table1_mean_subject_level_baseline_subtracted_auc0_24"]
    redraw=source["wikimedia_2019_redraw"]["numeric_points_by_visual_reading"]
    if [t for t,v in redraw]!=grid:raise ValueError("redraw x-axis mismatch")
    same_source=[{"t":t,"v":v} for t,v in redraw]
    return {
       "status":"UNRESOLVED_CONDITIONAL_SOURCE_RECONCILIATION",
       "table_baseline_corrected_auc":table,
       "manual_figure_auc_if_zero_predose":means,
       "manual_figure_auc_if_predose20_pg_ml":corrected(rows,"central",20),
       "table_minus_central_zero_predose":table-means["central"],
       "table_minus_upper_manual_zero_predose":table-means["hi"],
       "predose_needed_to_match_central_if_same_source":(means["central"]-table)/(24-w[0]),
       "paired_human_data_available":False,
       "same_source_redraw_auc":corrected(same_source,"v",0),
       "trapezoid_weights":dict(zip(map(str,grid),w)),
       "manual_bounds_are_not_CI":True,
       "human_source_error_proven":False,
       "clinical_accuracy_established":False,
       "production_change_authorized":False
    }

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",required=True)
    a=p.parse_args()
    result=audit(json.loads(SOURCE.read_text()))
    path=Path(a.out)
    if path.exists():raise ValueError("output exists, refuse overwrite")
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(result,sort_keys=True,ensure_ascii=False,indent=2)+"\n")
    print("Source consistency, not PK validation:",path)
if __name__=="__main__":main()
