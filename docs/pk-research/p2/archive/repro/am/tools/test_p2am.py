import copy
import csv
import json
import math
import shutil
import tempfile
import unittest
from pathlib import Path
import p2am_protocol as p
import p2am_timing as t

ROOT=Path(__file__).resolve().parents[1]

class ProtocolTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.expected=p.load_and_score(ROOT/'synthetic')
        cls.source=json.loads((ROOT/'inputs/p2x-conditional-ensemble-results.json').read_text())
        cls.clock=t.analyze(cls.source)

    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path=Path(self.tmp.name)/'data'
        shutil.copytree(ROOT/'synthetic',self.path)

    def manifest_mutate(self,func):
        path=self.path/'manifest.json';data=json.loads(path.read_text());func(data);path.write_text(json.dumps(data))

    def csv_mutate(self,name,func):
        path=self.path/(name+'.csv')
        with path.open(newline='',encoding='utf-8') as f:r=csv.DictReader(f);fields=r.fieldnames;rows=list(r)
        func(rows)
        with path.open('w',newline='',encoding='utf-8') as f:
            w=csv.DictWriter(f,fields);w.writeheader();w.writerows(rows)

    def assertInvalid(self,part=None):
        with self.assertRaises(ValueError) as c:p.load_and_score(self.path)
        if part:self.assertIn(part,str(c.exception))

    def test_synthetic_accepts(self):
        got=p.load_and_score(self.path)
        self.assertEqual(got['n_subjects'],8)
        self.assertEqual(got['n_studies'],2)
        self.assertEqual(got['n_scored_samples'],40)
        self.assertEqual(got['origin'],'SYNTHETIC')
        self.assertFalse(got['is_clinical_validation'])
        self.assertTrue(got['synthetic_only'])

    def test_separate_paired_subject_loss(self):
        got=p.load_and_score(self.path)
        ds=[x['paired_mae_delta_m2_minus_legacy'] for x in got['participants']]
        self.assertAlmostEqual(got['paired_mae_delta_m2_minus_legacy'],sum(ds)/len(ds))

    def test_demo_winner_not_clinical(self):
        self.assertLess(self.expected['paired_mae_delta_m2_minus_legacy'],0.)
        self.assertFalse(self.expected['is_clinical_validation'])
        self.assertIn('BLOCKED',self.expected['absolute_pgml_gate'])

    def test_reading_order_invariant(self):
        self.csv_mutate('samples',lambda rows: rows.reverse())
        self.csv_mutate('predictions',lambda rows: rows.reverse())
        out=p.load_and_score(self.path)
        self.assertAlmostEqual(out['paired_mae_delta_m2_minus_legacy'],self.expected['paired_mae_delta_m2_minus_legacy'])

    def test_prohibit_false_origin(self):
        self.manifest_mutate(lambda m:m.update({'origin':'real patient evidence'}));self.assertInvalid('origin')
    def test_no_claim_synthetic(self):
        self.manifest_mutate(lambda m:m.update({'eligible_for_external_claim':True}));self.assertInvalid('synthetic')
    def test_no_old_reused_study(self):
        def f(m):m['origin']='EXTERNAL_HUMAN';m['eligible_for_external_claim']=True;m['dataset_study_codes']=['Price1997_figure1','DEMO_CENTER_B']
        self.manifest_mutate(f)
        self.csv_mutate('subjects',lambda rows: [r.update(study_code='Price1997_figure1') for r in rows if r['study_code']=='DEMO_CENTER_A'])
        self.assertInvalid('previously seen')
    def test_missing_independent_permission(self):
        self.manifest_mutate(lambda m:m.update({'origin':'EXTERNAL_HUMAN','eligible_for_external_claim':True}));self.assertInvalid('ethics')
    def test_frozen_after_data_access(self):
        self.manifest_mutate(lambda m:m.update({'protocol_frozen_at_utc':'2025-01-05T00:00:00Z'}));self.assertInvalid('not frozen')
    def test_predictions_not_locked_before_unblind(self):
        self.manifest_mutate(lambda m:m.update({'engine_predictions_locked_at_utc':'2025-01-06T00:00:00Z'}));self.assertInvalid('unblinding')
    def test_missing_timezone(self):
        self.csv_mutate('samples',lambda rows:rows[0].update(collection_utc='2025-02-01T10:00:00'));self.assertInvalid('timezone')
    def test_invalid_numeric_nan(self):
        self.csv_mutate('samples',lambda rows:rows[0].update(e2_pg_ml='NaN'));self.assertInvalid('concentration')
    def test_negative_prediction(self):
        self.csv_mutate('predictions',lambda rows:rows[0].update(relative_increment='-1'));self.assertInvalid('relative increment')
    def test_malformed_model_hash(self):
        self.csv_mutate('predictions',lambda rows:rows[0].update(model_sha256='a'*64));self.assertInvalid('hash mismatch')
    def test_duplicate_subject(self):
        self.csv_mutate('subjects',lambda rows:rows.append(copy.copy(rows[0])));self.assertInvalid('unique')
    def test_duplicate_sample(self):
        self.csv_mutate('samples',lambda rows:rows.append(copy.copy(rows[0])));self.assertInvalid('duplicate sample')
    def test_duplicate_pred_model(self):
        self.csv_mutate('predictions',lambda rows:rows.append(copy.copy(rows[0])));self.assertInvalid('duplicate')
    def test_missing_prediction(self):
        self.csv_mutate('predictions',lambda rows:rows.pop());self.assertInvalid('unpaired predictions')
    def test_oral_record_excluded(self):
        self.csv_mutate('doses',lambda rows:rows[0].update(route='ORAL'));self.assertInvalid('non SL-E2')
    def test_ev_record_excluded(self):
        self.csv_mutate('doses',lambda rows:rows[0].update(molecule='EV'));self.assertInvalid('non SL-E2')
    def test_planned_record_excluded(self):
        self.csv_mutate('doses',lambda rows:rows[0].update(verified='NO'));self.assertInvalid('non-verified')
    def test_multiple_doses_require_separate_protocol(self):
        self.csv_mutate('doses',lambda rows:rows.append({**rows[0], 'event_code': 'another'}));self.assertInvalid('exactly one')
    def test_under_lloq_is_rejected_not_imputed(self):
        self.csv_mutate('samples',lambda rows:rows[0].update(e2_pg_ml='3'));self.assertInvalid('LLOQ')
    def test_unquantified_is_rejected_not_imputed(self):
        self.csv_mutate('samples',lambda rows:rows[0].update(quantified='NO'));self.assertInvalid('censored')
    def test_assay_batch_must_match(self):
        self.csv_mutate('samples',lambda rows:rows[0].update(assay_batch='OTHER'));self.assertInvalid('assay')
    def test_not_verified_prior_exposure(self):
        self.csv_mutate('subjects',lambda rows:rows[0].update(prior_exposure='YES'));self.assertInvalid('previously')
    def test_anchoring_future_sample_fails(self):
        def f(rows):
            for row in rows:
                if row['subject_code']=='DEMO_PERSON_001' and row['role']=='ANCHOR':row['collection_utc']='2025-02-02T15:00:00Z';break
        self.csv_mutate('samples',f);self.assertInvalid('anchor')
    def test_anchor_outside_preregistered_time(self):
        def f(rows):
            for row in rows:
                if row['subject_code']=='DEMO_PERSON_001' and row['role']=='ANCHOR':row['collection_utc']='2025-02-01T13:35:00Z';break
        self.csv_mutate('samples',f);self.assertInvalid('1h anchor')
    def test_too_late_sampling_fails(self):
        def f(rows):
            for row in rows:
                if row['subject_code']=='DEMO_PERSON_001' and row['role']=='EVAL':row['collection_utc']='2025-02-04T12:00:00Z';break
        self.csv_mutate('samples',f);self.assertInvalid('at most 24h')
    def test_poor_anchor_signal_is_rejected(self):
        def f(rows):
            for row in rows:
                if row['subject_code']=='DEMO_PERSON_001' and row['role']=='ANCHOR':row['e2_pg_ml']='20';break
        self.csv_mutate('samples',f);self.assertInvalid('minimum')
    def test_missing_baseline_fails(self):
        self.csv_mutate('samples',lambda rows:rows.__setitem__(slice(None),[r for r in rows if r['sample_code']!='DEMO_PERSON_001-B']))
        self.csv_mutate('predictions',lambda rows:rows.__setitem__(slice(None),[r for r in rows if r['sample_code']!='DEMO_PERSON_001-B']))
        self.assertInvalid('baseline')
    def test_study_metadata_exact(self):
        self.manifest_mutate(lambda m:m.update({'dataset_study_codes':['DEMO_CENTER_A']}));self.assertInvalid('study manifest')

    def test_clock_kernel_15_p2x(self):
        self.assertEqual(self.clock['n_models'],15)
        self.assertEqual(len(set(self.clock['candidate_ids'])),15)
    def test_timing_input_shape_at_1_is_one(self):
        candidate=t.selected_candidates(self.source)[0]['params']
        from p2v_continuous_fit import shape
        self.assertAlmostEqual(float(shape([1.],candidate)[0]),1.)
    def test_freeze_cross_check_against_p2x_exported_curve(self):
        from p2v_continuous_fit import shape
        choices=t.selected_candidates(self.source)
        for model in choices:
            idx=int(model['candidate_id'].split('-')[-1])
            prior=self.source['rows'][idx]['normalized_curve']
            for hour in (0.25,0.5,1.,2.,4.,8.,12.,24.):
                self.assertAlmostEqual(float(shape([hour],model['params'])[0]),
                                       float(prior[str(hour).rstrip('0').rstrip('.') if hour != 1. else '1']),places=6)

    def test_no_negative_t_for_perturb(self):
        with self.assertRaises(ValueError):t.perturb(t.selected_candidates(self.source)[0]['params'],.25,.5)
    def test_shared_anchor_shift_self_equals_zero(self):
        r=[x for x in self.clock['rows'] if x['sample_hour']==1.]
        self.assertEqual(len(r),3)
        self.assertTrue(all(x['deltaH_shared_dose_clock_max_over15']<1e-12 for x in r))
    def test_clock_increases_for_05(self):
        rows=[x for x in self.clock['rows'] if x['sample_hour']==.5]
        self.assertLess(rows[0]['deltaH_sample_clock_max_over15'],rows[1]['deltaH_sample_clock_max_over15'])
        self.assertLess(rows[1]['deltaH_sample_clock_max_over15'],rows[2]['deltaH_sample_clock_max_over15'])
    def test_timing_all_finite(self):
        self.assertTrue(all(math.isfinite(v) and v>=0 for x in self.clock['rows'] for k,v in x.items() if k.startswith('deltaH_')))
    def test_reject_wrong_candidate_count(self):
        data=copy.deepcopy(self.source);data['rows'].pop()
        with self.assertRaises(ValueError):t.selected_candidates(data)
    def test_per_assay_subject_totals(self):
        self.assertEqual(sum(x['n_subjects'] for x in self.expected['by_assay'].values()),8)

if __name__=='__main__':unittest.main()
