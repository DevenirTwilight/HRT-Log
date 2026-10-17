#!/usr/bin/env python3
"""Offline evidence pointers only. Never emits or changes a model, parameter or APK resource."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse, parse_qs

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/ansm-pk/evidence-index.json'


def read(relative):
    return json.loads((ROOT / relative).read_text())


def build():
    params = read('pk-engine/src/main/resources/pk-params.json')
    acquired = {r['cis']: r for r in read('docs/ansm-pk/rcp-observations-2026-10-17.json')['observations']}
    references = []
    for r in params['references']:
        uri = urlparse(r['url'])
        if uri.hostname != 'base-donnees-publique.medicaments.gouv.fr':
            continue
        cis = uri.path.split('/')[2] if uri.path.startswith('/medicament/') else parse_qs(uri.query).get('specid', [None])[0]
        reference = {'reference_id': r['id'], 'original_url': r['url'], 'cis': cis,
                     'evidence_class': 'OFFICIAL_PRODUCT_LABEL_NOT_ANSM_MODEL_VALIDATION',
                     'original_reference_title': r['title'],
                     'this_audit': acquired.get(cis, {'retrieval_status': 'NOT_RETRIEVED_THIS_AUDIT',
                                                     'content_review': 'UNVERIFIED'}),
                     'direct_model_basis': [key for key, model in params['models'].items()
                                            if any(r['id'] in basis for basis in model.get('basis', []))]}
        references.append(reference)
    protected = sorted((ROOT / 'pk-engine/src/main').rglob('*'))
    protected += [ROOT / 'app/src/main/java/net/plainnotes/app/conc/ConcentrationCalculator.kt',
                  ROOT / 'app/src/main/java/net/plainnotes/app/experimental/UnifiedConcentrationScreen.kt']
    hashes = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in protected if p.is_file()}
    source_manifest = read('docs/pk-research/p2/source-manifest.json')
    cohort = read('docs/pk-research/p2/p2r-cohort-evidence-ledger.json')
    return {'schema_version': 1, 'baseline': 'f1e9b4de1c97e73966b4af8900b78391eada5c43',
            'status': 'EVIDENCE_ARCHIVED_PARTIAL_REVIEW_MODEL_CHANGE_BLOCKED',
            'current_model_loading': {
                'legacy_top_level_status': params['status'], 'models_note': params['models_note'],
                'actual_loader': 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/FittedModels.kt: PkParams.models reads root.models',
                'actual_consumer': 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/Engine.kt: Engine.choose uses PkParams.models',
                'status': 'LEGACY_STATUS_TEXT_IS_NOT_A_RUNTIME_GATE'},
            'model_change_authorized': False, 'clinical_accuracy_established': False,
            'official_distribution_approved': False, 'raw_clinical_text_redistributed': False,
            'official_label_references': references,
            'human_research_acquisition': {'classification': 'REPOSITORY_RESEARCH_NOT_ANSM_CONCLUSIONS',
                'this_audit_full_text_reverification': 'NOT_RUN',
                'original_policy': source_manifest['acquisition_policy'],
                'requests': source_manifest['requests'],
                'cohort_ledger_source': 'docs/pk-research/p2/p2r-cohort-evidence-ledger.json',
                'existing_locked_external_sl_cohorts': cohort['external_locked_pure_SL_cohort_count'],
                'exposed_cohorts_are_not_new_holdouts': True},
            'scientific_follow_up': [
                {'id': 'PROVAMES_ORAL_PEAK_CONDITIONS', 'status': 'REQUIRES_INDEPENDENT_SCIENTIFIC_REVIEW',
                 'label_range_h': ['3', '6'], 'current_generic_model': 'E2_ORAL',
                 'model_check_tmax_h': params['models']['E2_ORAL']['checks']['tmax_h'],
                 'limitation': 'Different reference product/study conditions; a discrepancy does not identify a replacement parameter or prove model invalidity.'},
                {'id': 'GEL_EXPOSURE_AND_ABSORPTION', 'status': 'PRODUCT_SPECIFIC_VALIDATION_BLOCKED',
                 'limitation': 'Mean plasma concentration is not Cmax. Approximate label absorption is not a patient conversion. Oestrodose CIS must not select a gel model automatically.'},
                {'id': 'PROGYNOVA_METABOLITE_HALF_LIFE', 'status': 'SEMANTIC_BOUNDARY_RECORDED',
                 'limitation': 'Metabolite excretion half-life is not parent estradiol elimination or a complete curve.'}],
            'production_file_sha256': hashes,
            'source_file_sha256': {f: hashlib.sha256((ROOT / f).read_bytes()).hexdigest() for f in
                ['docs/ansm-pk/rcp-observations-2026-10-17.json', 'docs/pk-research/p2/source-manifest.json',
                 'docs/pk-research/p2/p2r-cohort-evidence-ledger.json']},
            'remaining': ['Eight other pre-existing official label references not retrieved in this audit.',
                          'The full58-product RCP5.2 review is incomplete.',
                          'Independent scientific, reuse and translation review remains pending.',
                          'PK_DISPLAY_REVIEW and scientific validation are required before any model/display/frozen-result change.']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    output = (json.dumps(build(), ensure_ascii=False, indent=2, sort_keys=True) + '\n').encode()
    if args.check:
        if OUTPUT.read_bytes() != output:
            raise SystemExit('Evidence index/source fingerprint changed; inspect before regeneration')
    else:
        OUTPUT.write_bytes(output)
    print('PASS: offline evidence index byte replay; scientific review and model change not approved')
