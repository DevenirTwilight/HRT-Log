#!/usr/bin/env python3
"""P2-AI: deterministic, source-graded Price 1997 figure/table constraints.

Does NOT redigitize the original PDF; journal raster unavailable. Runs only
on frozen analyst values from P2-U and a transparently transcribed Table 1.
"""
import argparse, hashlib, json, math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FROZEN = ROOT / 'data' / 'p2u-observed-aggregates-frozen.json'
SOURCES = {
    'primary_article_doi': 'https://doi.org/10.1016/S0029-7844(96)00513-3',
    'primary_article_record': 'https://pubmed.ncbi.nlm.nih.gov/9052581/',
    'body_transcription_not_original_scan': 'https://www.academia.edu/122086991/Single_Dose_Pharmacokinetics_of_Sublingual_Versus_Oral_Administration_of_Micronized_17%CE%B2_Estradiol',
    'secondary_2018_webplotdigitizer_replot_not_primary_or_independent_cohort': 'https://commons.wikimedia.org/wiki/File:Estradiol_levels_with_oral_versus_sublingual_estradiol_in_postmenopausal_women.png',
    'book_secondary_auc': 'https://www.ncbi.nlm.nih.gov/books/NBK396201/',
}
# Values from Price Table 1, article OCR transcription with columns interleaved.
# Paired original journal typeset table must be rechecked prior to any clinical use.
TABLE = [
    {'arm':'E2_SL_1mg','route':'SL','dose_mg':1.0,'cmax_pg_ml':451,'cmax_sd_pg_ml':162,'auc0_24_pg_h_ml':2109,'auc_sd_pg_h_ml':1031},
    {'arm':'E2_SL_0.5mg','route':'SL','dose_mg':0.5,'cmax_pg_ml':245,'cmax_sd_pg_ml':115,'auc0_24_pg_h_ml':970,'auc_sd_pg_h_ml':440},
    {'arm':'E2_SL_0.25mg','route':'SL','dose_mg':0.25,'cmax_pg_ml':294,'cmax_sd_pg_ml':131,'auc0_24_pg_h_ml':825,'auc_sd_pg_h_ml':401},
    {'arm':'E2_PO_1mg','route':'PO','dose_mg':1.0,'cmax_pg_ml':34.0,'cmax_sd_pg_ml':20.4,'auc0_24_pg_h_ml':823,'auc_sd_pg_h_ml':636},
    {'arm':'E2_PO_0.5mg','route':'PO','dose_mg':0.5,'cmax_pg_ml':24.8,'cmax_sd_pg_ml':17.5,'auc0_24_pg_h_ml':403,'auc_sd_pg_h_ml':142},
]

def trapz(t, y):
    assert len(t)==len(y)>=2 and all(b>a for a,b in zip(t,t[1:]))
    return math.fsum((t[i+1]-t[i])*(y[i]+y[i+1])/2 for i in range(len(y)-1))

def trapezoid_weights(t):
    assert len(t)>1
    return [ (t[1]-t[0])/2 if i==0 else (t[-1]-t[-2])/2 if i==len(t)-1
       else (t[i+1]-t[i-1])/2 for i in range(len(t))]

def logmean(x,y):
    if not (x>0 and y>0): return None
    if x==y: return x
    return (x-y) / (math.log(x)-math.log(y))

def run():
    raw = FROZEN.read_bytes()
    j = json.loads(raw)
    price=j['studies']['Price1997_figure1']
    t=[0.0]+[float(v) for v in price['hours']]
    y=[0.0]+[float(v) for v in price['y']] # baseline corrected at t0 only
    widths=[0.0]+[float(v) for v in price['nominal_se_or_read_width']]
    assert price['unit']=='pg/mL' and price['dose_mg']==1 and len(t)==10
    w=trapezoid_weights(t)
    nominal=trapz(t,y)
    width_auc=math.fsum( wi*wiwidth for wi,wiwidth in zip(w,widths))
    width_l2=math.sqrt(math.fsum((wi*wiwidth)**2 for wi,wiwidth in zip(w,widths)))
    table=TABLE[0]['auc0_24_pg_h_ml']
    b_scen=[]
    for b in [0.,3.,6.,12.,18.,24.]:
        corrected=[0.0]+[value-b for value in price['y']]
        auc=trapz(t,corrected)
        gap=table-auc
        # Recover Figure means from all *unobserved* 0h baseline scenarios
        raw_auc=trapz(t,[b]+list(map(float,price['y'])))
        b_scen.append({'baseline_pg_ml':b,'trapezoid_baseline_corrected_auc':round(auc,8),
            'raw_fig_auc_assuming_0h_baseline':round(raw_auc,8),
            'table_minus_figure_auc':round(gap,8),
            'fraction_of_table_unreproduced':round(gap/table,8),
            'min_max_manual_read_width_multipliers_simultaneously':round(gap/width_auc,8),
            'min_euclidean_norm_of_width_scaled_point_offsets':round(gap/width_l2,8),
            'need_one_point_increase_pg_ml_if_only_12h_misread':round(gap/w[t.index(12.)],8),
            'need_one_point_increase_pg_ml_if_only_18h_misread':round(gap/w[t.index(18.)],8),
            'need_one_point_increase_pg_ml_if_only_24h_misread':round(gap/w[t.index(24.)],8),
            'hypothetical_half_hour_unobserved_level_pg_ml':round(2*gap+(float(price['y'][0])-b)/2,8),
        })
        assert abs(auc-(nominal-b*23.5))<1e-8
        assert abs(trapz(t,[corrected[i]+widths[i] for i in range(10)]) - auc-width_auc)<1e-8
    # Predefined deviations are *analyst* reading widths, not sample SE/SD or measurement precision.
    # Linear-slope-preserving perturbations demonstrate how strongly 12-24h contributes.
    bypoint=[{'time_h':ti, 'reported_digitized_pg_ml':float('nan') if i==0 else y[i],
         'auc_weight_h':w[i], 'manual_read_width_pg_ml':widths[i],
         'maximum_read_width_auc_effect':round(w[i]*widths[i],8)} for i,ti in enumerate(t)]
    for q in bypoint:q['reported_digitized_pg_ml'] = None if not math.isfinite(q['reported_digitized_pg_ml']) else q['reported_digitized_pg_ml']
    arms=[]
    for a in TABLE:
        o=a.copy()
        o['cmax_per_mg']=round(o['cmax_pg_ml']/o['dose_mg'],8)
        o['auc_per_mg']=round(o['auc0_24_pg_h_ml']/o['dose_mg'],8)
        o['auc_group_coefficient_variation']=round(o['auc_sd_pg_h_ml']/o['auc0_24_pg_h_ml'],8)
        arms.append(o)
    comparisons=[]
    sl=[x for x in TABLE if x['route']=='SL']
    for a,b in [(sl[0],sl[1]),(sl[1],sl[2]),(sl[0],sl[2])]:
        comparisons.append({'high_dose_arm':a['arm'],'low_dose_arm':b['arm'],
            'dose_ratio':a['dose_mg']/b['dose_mg'],
            'cmax_mean_ratio':round(a['cmax_pg_ml']/b['cmax_pg_ml'],8),
            'auc_mean_ratio':round(a['auc0_24_pg_h_ml']/b['auc0_24_pg_h_ml'],8),
            'auc_ratio_over_linear_dose_ratio':round(a['auc0_24_pg_h_ml']/b['auc0_24_pg_h_ml']/(a['dose_mg']/b['dose_mg']),8)})
    check_formula={'baseline_zero_nominal_trapezoid':nominal,'method':'linear trapezoid using frozen approximate group mean points; excludes original individual observations',
       'influence_weights_hour':w,'max_all_manual_point_width_auc':width_auc,
       'source_0h_predose_unknown':True,
       'journal_original_figure_pixels_available':False,
       'third_party_replot_checked_qualitatively_only':True,
       'no_claim_of_independent_original_figure_digitization':True,
       'pointwise_max_1mg_graph_time_1h':price['y'][0],
       'published_1mg_table_auc':table,
       'mean_auc_standard_deviation_from_table':1031,
       'unreproduced_gap_relative_to_group_auc_sd_at_b0':(table-nominal)/1031,
       'log_trapezoid_note':'For positive consecutive endpoint values, log-mean <= arithmetic mean, so positive log-trapezoids cannot account for an increase over linear trapezoid.',
       'linear_average_commutes_with_linear_trapezoid_iff_same_grid_and_complete_cases':True}
    return {'schema_version':1,'phase':'P2-AI','author_timestamp':'2026-10-10',
       'research_status':'PROVENANCE_CONSTRAINTS_ONLY','no_git_mutation':True,'personal_pk_validated':False,
       'study_source_citations':SOURCES, 'frozen_p2u_input_sha256':hashlib.sha256(raw).hexdigest(),
       'study_n':6, 'source_data_warning':'Price Figure 1 P2-U analyst readings: NOT original journal pixel re-digitization. Table 1 values independently transcribed from online OCR: journal plate verification remains required.',
       'table1_reported_study_arms':arms,
       'table1_sl_dose_ratio_contrasts':comparisons,
       'trapezoid_audit':check_formula,
       'point_contributions':bypoint,
       'baseline_scenarios':b_scen,
       'blocking_conclusion':'Current P2-U digitization cannot reproduce Table1 AUC0-24 under nonnegative common predose scenarios, even after all analyst manual widths are increased simultaneously; actual cause unknown. Do not use Table1 AUC as an independent constraint on P2-X until original figure/individual source agreement resolved.',
       'dose_conclusion':'Table means show imperfect SL dose proportionality (especially 0.25 vs 0.5 mg), but n=6 and large inter-person dispersion cannot establish genuine nonlinear within-person bioavailability or reject linearity without original paired records.'}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--out',default=str(ROOT/'data'/'p2ai-source-constraints.json'))
    args=parser.parse_args()
    data=run();path=Path(args.out);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf-8')
    print('source P2-U sha',data['frozen_p2u_input_sha256'])
    print('b0/24 baseline, gap, required_width_factor',[(x['baseline_pg_ml'],x['trapezoid_baseline_corrected_auc'],x['table_minus_figure_auc'],x['min_max_manual_read_width_multipliers_simultaneously']) for x in data['baseline_scenarios'] if x['baseline_pg_ml'] in [0,24]])
    print('dose ratios',data['table1_sl_dose_ratio_contrasts'])
