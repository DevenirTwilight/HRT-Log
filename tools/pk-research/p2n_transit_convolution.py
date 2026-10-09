"""P2-N: nonclinical time-response convolution with positive transit + slow input.

Standard library only. NO Doll peak amplitude calibration. EVERY reported
scenario is normalized to its own h(1h). Shapes were selected after seeing
published study results and are NOT human parameter estimates.

An Erlang(n,k) *absorption input* followed by first-order elimination
produces concentration g_n(t) ~ k**n*t**n/n! near t=0, one power higher
than the Erlang input density. This difference matters for onset bounds.
"""
import argparse
import functools
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
CONFIG=ROOT/"docs/pk-research/p2/p2n-transit-convolution-design.json"
PRICE=ROOT/"docs/pk-research/p2/p2i-price-figure-points.json"
ROSANO=ROOT/"docs/pk-research/p2/p2e-source-metrics.json"
KOM=ROOT/"docs/pk-research/p2/p2d-komesaroff-table1.json"

def positive(*values):
    if any(not math.isfinite(x) or x<=0 for x in values):
        raise ValueError("Expected strictly positive finite rate/time")

def first_order_response(t,k,ke):
    positive(k,ke)
    if not math.isfinite(t) or t<0:raise ValueError("Invalid time")
    if t==0:return 0.
    delta=abs(k-ke)
    if delta==0:return k*t*math.exp(-ke*t)
    return k*t*math.exp(-min(k,ke)*t)*(-math.expm1(-delta*t)/(delta*t))

def poisson_upper_tail_at_least(n,z):
    """P(Poisson(z)>=n), stable without 1-CDF cancellation."""
    if not isinstance(n,int) or n<1 or not math.isfinite(z) or z<0:
        raise ValueError("Invalid Poisson parameters")
    if z==0:return 0.
    if z>=60 and n<=6:return 1.0
    term=math.exp(n*math.log(z)-math.lgamma(n+1)-z)
    summation=term
    for j in range(n+1,n+500):
        term*=z/j
        summation+=term
        if term<1e-15*summation:break
    return min(1.,summation)

def simpson_convolution(t,n,k,ke):
    """Fallback when k<=ke or nearly equal; finite nonnegative quadrature."""
    count=max(192,2*math.ceil(t*max(k,ke)*6))
    if count%2:count+=1
    step=t/count
    constant=n*math.log(k)-math.lgamma(n)
    terms=[]
    for i in range(count+1):
        u=i*step
        val=0. if u==0 else math.exp(constant+(n-1)*math.log(u)-k*u-ke*(t-u))
        terms.append(val*(1 if i in (0,count) else 4 if i%2 else 2))
    return step*math.fsum(terms)/3

@functools.lru_cache(maxsize=30000)
def erlang_input_response(t,n,k,ke):
    if not isinstance(n,int) or n<1:raise ValueError("Order is positive integer")
    positive(k,ke)
    if not math.isfinite(t) or t<0:raise ValueError("Invalid time")
    if t==0:return 0.
    if n==1:return first_order_response(t,k,ke)
    diff=k-ke
    if abs(diff)<=0.025*max(k,ke) or diff<0:
        if diff==0:
            return math.exp(n*math.log(k*t)-math.lgamma(n+1)-ke*t)
        return simpson_convolution(t,n,k,ke)
    # k>ke.  g = exp(-ke*t) * (k/(k-ke))**n *
    # regularized lower gamma(n,(k-ke)*t).
    return math.exp(n*math.log(k/diff)-ke*t)*poisson_upper_tail_at_least(n,diff*t)

def unnormalized(t,case):
    weight=case["slow_effective_weight"]
    if not math.isfinite(weight) or not 0<=weight<=1:
        raise ValueError("Effective slow weight must be in [0,1]")
    fast=erlang_input_response(t,case["n_fast"],case["k_fast_h"],case["k_elim_h"])
    slow=first_order_response(t,case["k_slow_h"],case["k_elim_h"])
    return (1-weight)*fast+weight*slow

def normalized(t,case):
    baseline=unnormalized(1.,case)
    if baseline<=0 or not math.isfinite(baseline):raise ValueError("No identifiably positive h(1)")
    return unnormalized(t,case)/baseline

def sampled_auc(case,hours):
    if hours!=[0,1,2,3,4,6,8,12,18,24]:raise ValueError("Wrong study sampling grid")
    return math.fsum((b-a)*(normalized(a,case)+normalized(b,case))/2
                      for a,b in zip(hours,hours[1:]))

def scenario(case,prior_count=24):
    if not isinstance(prior_count,int) or prior_count<1:raise ValueError("Bad scenario")
    # Dimensionless relative to single 1mg at 1h; NOT a prediction in pg/mL.
    pre=0.5*math.fsum(normalized(6*i,case) for i in range(1,prior_count+1))
    after=0.5*math.fsum(normalized(1.5+6*i,case) for i in range(prior_count+1))
    return {"predose_relative_to_1mg_at_1h":pre,
            "90min_after_relative_to_1mg_at_1h":after,
            "older_history_omitted":True,
            "does_not_use_real_patient_dose_times":True}

def report(config,price,ros,kom):
    assert [p["t"] for p in price["plot_points"]]==config["price_auc_discrete_hours"]
    rd=ros["Rosano1997"]
    assert rd["n_PK"]==25 and kom["n"]==10
    k=kom["observations"]
    b=next(r["mean_pmol_l"] for r in k if r["minutes"]==0)
    k15=next(r["mean_pmol_l"] for r in k if r["minutes"]==15)
    k30=next(r["mean_pmol_l"] for r in k if r["minutes"]==30)
    r20=next(r["mean_pmol_l"] for r in rd["points"] if r["minutes"]==20)
    r40=next(r["mean_pmol_l"] for r in rd["points"] if r["minutes"]==40)
    models=[]
    for case in config["models"]:
        f=lambda t:normalized(t,case)
        models.append({"id":case["id"],"n_absorption_input_stages":case["n_fast"],
          "growth_20_to_40":f(2/3)/f(1/3),
          "growth_15_to_30":f(.5)/f(.25),
          "price_shape_2h_over_1h":f(2),
          "price_shape_3h_over_1h":f(3),
          "price_shape_4h_over_1h":f(4),
          "price_0_24h_sampled_AUC_per_1h_increment":sampled_auc(case,config["price_auc_discrete_hours"]),
          "repeat_0_5mg_q6h":scenario(case,config["repeat_scenario"]["prior_dose_count"]),
          "all_parameters_assumed_NOT_ESTIMATED":True})
    return {
      "phase":"P2-N exploratory mechanistic input-output curves",
      "published_Rosano_raw_ratio":r40/r20,
      "published_Kom_baseline_corrected_mean_ratio":(k30-b)/(k15-b),
      "price_1mg_group_manual_2h_over_1h":225/450,
      "price_1mg_group_manual_4h_over_1h":85/450,
      "price_Figure1_vs_Table1_AUC_UNRESOLVED":True,
      "Doll_144_as_amplitude_anchor":False,
      "three_absorption_stages_can_exceed_4fold_at_sufficiently_early_time":True,
      "n_absorption_input_stages_not_a_count_of_real_human_anatomical_compartments":True,
      "repeat_predictions_are_dimensionless_not_pg_ml":True,
      "models":models,
      "independent_locked_external_human_studies":0,
      "human_interval_coverage_established":False,
      "clinical_accuracy_established":False,
      "production_model_change_authorized":False}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out",required=True)
    args=parser.parse_args()
    values=[json.loads(p.read_text()) for p in (CONFIG,PRICE,ROSANO,KOM)]
    result=report(*values)
    out=Path(args.out)
    if out.exists():raise ValueError("Refuse to overwrite prior results")
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,ensure_ascii=False,sort_keys=True)+"\n")
    print("P2-N mathematical feasibility, NOT pharmacokinetic validation:",out)

if __name__=="__main__":main()
