#!/usr/bin/env python3
"""Offline documentation audit of the frozen ANSM-01 snapshot; never an APK resource."""
import argparse
from pathlib import Path
import bdpm_reference as bdpm

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/ansm04/product-coverage.json'


def build(directory=bdpm.DATA):
    # Reuse the original verified generator and manifest checks, not a second parser.
    replay = bdpm.generate(directory)['catalog.json']
    bdpm.ensure(replay == (directory / 'catalog.json').read_bytes(), 'catalog differs from frozen replay')
    catalog = bdpm.read_json(directory / 'catalog.json')
    products = []
    for p in catalog['products']:
        products.append({
            'cis': p['cis'], 'name_fr': p['name_fr'], 'identity_source': p['source'],
            'authorization_snapshot': p['authorization']['value'],
            'marketing_snapshot': p['marketing']['value'],
            'live_supply': 'UNVERIFIED',
            'ingredient_sources': [i['source'] for i in p['ingredients']],
            'presentation_sources': [{'cis': x['cis'], 'cip13': x['cip13'], 'source': x['source']}
                                     for x in p['presentations']],
            'document_page_observation': {
                'retrieved_at_utc': p['page_source']['retrieved_at_utc'],
                'page_sha256': p['page_source']['sha256'],
                'url': p['page_source']['url'],
                'entries': {kind: {'observed_panel': d['panel_id'],
                                   'snapshot_entry_observation': d['verification'],
                                   'body_review': 'UNVERIFIED', 'translation_review': 'NOT_RUN'}
                            for kind, d in sorted(p['documents'].items())}},
            'additional_monitoring': 'NOT_COVERED_BY_FROZEN_CATALOG',
            'clinical_fields': {key: 'UNVERIFIED' for key in (
                'excipients', 'storage', 'contraindications', 'interactions', 'adverse_reactions', 'pk_section_5_2')},
            'unverified_fields': p['unverified_fields'], 'conflicts': p['conflicts'],
            'publication_status': 'OFFICIAL_DISTRIBUTION_BLOCKED',
        })
    return {
        'schema_version': 1, 'scope': 'DOCUMENTATION_ONLY_FROZEN_SNAPSHOT_NOT_CURRENT_MEDICAL_REVIEW',
        'reference_version': catalog['reference_version'], 'snapshot_verified_on': catalog['verified_on'],
        'source_sha256': {name: bdpm.sha((directory / name).read_bytes()) for name in
                          ('catalog.json', 'manifest.json', 'source-rows.json', 'page-evidence.json')},
        'counts': {'products': len(products),
                   'ingredient_rows': sum(len(p['ingredient_sources']) for p in products),
                   'presentations': sum(len(p['presentation_sources']) for p in products)},
        'distribution_approved': False, 'clinical_review_complete': False,
        'rights': catalog['license']['verification'],
        'limitations': ['Dates are frozen ANSM-01 observations, not a current re-verification.',
                        'Page/panel existence never verifies body, translation or text reuse.',
                        'Authorization and sale declaration do not establish pharmacy supply.',
                        'Missing monitoring field never means no additional monitoring.',
                        'No clinical text, synthesized summary or health record is included.'],
        'products': products,
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    output = bdpm.encode(build())
    if args.check:
        bdpm.ensure(OUTPUT.read_bytes() == output, 'stale coverage report')
    else:
        OUTPUT.write_bytes(output)
    print('PASS: deterministic 58-product audit; official distribution and clinical review remain OFF')
