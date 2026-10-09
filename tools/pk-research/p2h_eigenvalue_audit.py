"""P2-H reproducible interpretation of published two-compartment Table 3.

Not a human PK model fit; does not infer physiological elimination or validate
a sublingual dose model. Reads only the small P2-H source audit; no APK access.
"""
import argparse
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"docs/pk-research/p2/p2h-primary-pdf-audit.json"

def positive(*values):
    if any(not math.isfinite(v) or v<=0 for v in values):
        raise ValueError("All volumes, rates and clearances must be positive finite")

def rates(clearance_l_h, central_l, intercomp_clearance_l_h, peripheral_l, ka_h):
    positive(clearance_l_h,central_l,intercomp_clearance_l_h,peripheral_l,ka_h)
    k10=clearance_l_h/central_l
    k12=intercomp_clearance_l_h/central_l
    k21=intercomp_clearance_l_h/peripheral_l
    s=k10+k12+k21
    disc=math.sqrt(max(0.0,s*s-4*k10*k21))
    alpha=(s+disc)/2
    beta=2*k10*k21/(s+disc) # product-over-large-root, numerically stable
    return dict(k10=k10,k12=k12,k21=k21,alpha=alpha,beta=beta,ka=ka_h,
        reported_half_life_formula_match_h=math.log(2)*(central_l+peripheral_l)/clearance_l_h,
        conditional_fast_distribution_half_life_h=math.log(2)/alpha,
        conditional_terminal_two_comp_half_life_h=math.log(2)/beta,
        conditional_absorption_exponential_half_life_h=math.log(2)/ka_h,
        characteristic_product_check=alpha*beta-k10*k21,
        interpretation="MATHEMATICAL_ONLY_NOT_OBSERVED_TERMINAL_DATA")

def analyze(source):
    thesis=next(x for x in source["pdf_sources"] if x["id"]=="Abdelmawla2023")
    p=thesis["table3"]
    assert thesis["oral_or_sublingual_individually_disambiguated"] is False
    assert thesis["initial_observations"]==189 and thesis["final_observations"]==168
    assert thesis["missing_24h_replaced_by_predose_for_n"]==2
    doll=next(x for x in source["pdf_sources"] if x["id"]=="Doll2022")
    assert doll["classification"]=="PUBLISHER_WEBPAGE_PRINT_NOT_FULLTEXT"
    price=next(x for x in source["pdf_sources"] if x["id"]=="Price1997")
    assert price["publication_table_not_misread_as_estrone"]
    out=rates(p["cl_over_f_l_per_h"],p["vc_over_f_l"],p["q_over_f_l_per_h"],
              p["vp_over_f_l"],p["ka_per_h"])
    out.update({"author_reported_half_life_h":p["author_reported_half_life_h"],
        "author_formula_not_directly_documented":True,
        "model_equation_assumed_not_full_phoenix_code_verified":True,
        "same_transprep_cohort_as_yager":True,
        "new_locked_external_human_datasets":0,
        "clinical_accuracy_established":False,
        "P2C_authorized":False})
    return out

def main():
    a=argparse.ArgumentParser(description=__doc__)
    a.add_argument("--out",required=True)
    args=a.parse_args()
    result=analyze(json.loads(SOURCE.read_text()))
    out=Path(args.out)
    if out.exists():raise ValueError("Refusing to overwrite a previous research report")
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=False)+"\n")
    print("P2-H conditional two-compartment math only:",out)

if __name__=="__main__":main()
