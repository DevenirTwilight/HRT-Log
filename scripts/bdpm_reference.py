#!/usr/bin/env python3
"""ANSM-01: offline BDPM snapshot capture, deterministic build and fail-closed validation.

No Android imports, network, patient data, stock allocation or PK parameters.
Acquisition is a separate explicit maintainer action. See docs/reference/fr/README.md.
"""
import argparse
from collections import Counter
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
import hashlib
from html import unescape
from html.parser import HTMLParser
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'docs/reference/fr'
VERSION = 'fr-bdpm-2026-10-15.1'
GENERATOR = 1
BASE = 'https://base-donnees-publique.medicaments.gouv.fr'
FILES = {'CIS_bdpm.txt': ('cp1252', 12), 'CIS_CIP_bdpm.txt': ('utf-8', 13),
         'CIS_COMPO_bdpm.txt': ('cp1252', 8), 'CIS_CIP_Dispo_Spec.txt': ('cp1252', 8)}
PREFIXES = ('PROVAMES ', 'PROGYNOVA ', 'OESTRODOSE ', 'DERMESTRIL ', 'THAISSEPT ',
            'ACETATE DE CYPROTERONE ', 'CYPROTERONE ', 'ANDROCUR ', 'ALDACTONE ',
            'SPIRONOLACTONE ', 'UTROGESTAN ', 'PROGESTERONE ')
AUTH = {'Autorisation active': 'ACTIVE', 'Autorisation archivée': 'ARCHIVED',
        'Autorisation abrogée': 'REPEALED', 'Autorisation suspendue': 'SUSPENDED',
        'Autorisation expirée': 'EXPIRED'}
MARKET = {'Commercialisée': 'MARKETED_DECLARED', 'Non commercialisée': 'NOT_MARKETED'}
PRESENTATION_MARKET = {'Déclaration de commercialisation': 'MARKETED_DECLARED',
                       "Déclaration d'arrêt de commercialisation": 'STOPPED_DECLARED'}
SUPPLY = {'1': 'Rupture de stock', '2': "Tension d'approvisionnement",
          '3': 'Arrêt de commercialisation', '4': 'Remise à disposition'}


def ensure(ok, message):
    if not ok:
        raise ValueError(message)


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + '\n').encode('utf-8')


def sha(value):
    return hashlib.sha256(value).hexdigest()


def read_json(path):
    def unique(pairs):
        result = {}
        for k, v in pairs:
            ensure(k not in result, 'duplicate JSON key: ' + k)
            result[k] = v
        return result
    return json.loads(path.read_bytes(), object_pairs_hook=unique,
                      parse_constant=lambda x: (_ for _ in ()).throw(ValueError('nonfinite JSON: ' + x)))


class Text(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []

    def handle_data(self, text):
        self.parts.append(text)


def plain(html):
    parser = Text()
    parser.feed(html)
    return ' '.join(' '.join(parser.parts).split())


def official_url(url, host='base-donnees-publique.medicaments.gouv.fr'):
    p = urlsplit(url)
    ensure(p.scheme == 'https' and p.hostname == host and not p.username and not p.password
           and p.port in (None, 443), 'untrusted source URL: ' + url)


def iso_date(value):
    ensure(isinstance(value, str) and re.fullmatch(r'\d{4}-\d{2}-\d{2}', value), 'date must be ISO string')
    date.fromisoformat(value)
    return value


def french_date(value):
    return datetime.strptime(value, '%d/%m/%Y').date().isoformat() if value else None


def capture(downloads, output):
    """Capture only selected rows from previously retrieved official downloads/pages."""
    output.mkdir(parents=True, exist_ok=True)
    pages = read_json(downloads / 'pages.json')
    selected = set()
    parsed = {}
    for name, (encoding, columns) in FILES.items():
        content = (downloads / name).read_bytes()
        rows = []
        for number, raw in enumerate(content.splitlines(), 1):
            fields = raw.decode(encoding).split('\t')
            ensure(len(fields) == columns, f'{name}:{number}: unexpected column count')
            if name == 'CIS_bdpm.txt' and fields[1].startswith(PREFIXES) and not any(
                    x in fields[1] for x in ('ALTIZIDE', 'RETARD PHARLON')):
                selected.add(fields[0])
            if fields[0] in selected:
                rows.append({'line': number, 'fields': fields, 'row_sha256': sha(raw)})
        parsed[name] = {'url': BASE + '/download/file/' + name, 'encoding': encoding,
                        'sha256': sha(content), 'bytes': len(content), 'rows': rows}
    evidence = []
    for p in sorted(pages, key=lambda p: p['cis']):
        ensure(p['cis'] in selected, 'unexpected page')
        if p.get('http_status') != 200:
            evidence.append({**p, 'verification': 'UNVERIFIED'})
            continue
        content = (downloads / (p['cis'] + '.html')).read_bytes()
        ensure(sha(content) == p['sha256'], 'page hash mismatch')
        html = content.decode('utf-8')
        heading = re.search(r'<h3\b[^>]*>(.*?)</h3>', html, re.S)
        update = re.search(r'Dernière mise à jour le\s+(\d{2}/\d{2}/\d{4})', html)
        record = {**p, 'name': plain(heading[1]) if heading else None,
                  'data_updated_on': french_date(update[1]) if update else None,
                  'panels': [key for key in ('rcp', 'notice') if f'id="tabpanel-{key}-panel"' in html],
                  'verification': 'VERIFIED' if heading else 'UNVERIFIED'}
        ensure(record['data_updated_on'] == '2026-09-29', 'snapshot source update changed; review version/dates')
        ensure(p['retrieved_at_utc'][:10] == '2026-10-15', 'capture is specific to the dated v1 snapshot')
        if p['cis'] == '63071975':
            # Exact factual packaging paragraph only; no posology or clinical instructions.
            paragraphs = [plain(t) for t in re.findall(r'<p\b[^>]*>(.*?)</p>', html, re.S)]
            record['packaging_quote'] = next((t for t in paragraphs if t.startswith('La pompe délivre des doses unitaires')), None)
            record['packaging_location'] = 'RCP 6.5'
        evidence.append(record)
    ensure({p['cis'] for p in evidence} == selected, 'missing page attempt')
    (output / 'source-rows.json').write_bytes(encode(parsed))
    (output / 'page-evidence.json').write_bytes(encode(evidence))
    manifest = {'schema_version': 1, 'reference_version': VERSION, 'verified_on': '2026-10-15',
                'data_updated_on': '2026-09-29', 'format_document_updated_on': '2026-10-13',
                'data_update_semantics': 'SITE_DISPLAYED_DATE_NOT_EXPORT_TIMESTAMP',
                'download_page': BASE + '/telechargement',
                'attribution': 'Source : Base de Données Publique des Médicaments (ANSM / HAS / UNCAM). '
                               'Données mises à jour le 29/09/2026, extraites le 15/10/2026. '
                               'Réutilisation structurée par HRT Log, sans caution des autorités.',
                'license': {'id': 'LicenseRef-BDPM-version-conflict', 'verification': 'CONFLICT',
                            'footer_id': 'etalab-2.0',
                            'footer_url': 'https://github.com/etalab/licence-ouverte/blob/master/LO.md',
                            'footer_text_sha256': sha((downloads / 'etalab.md').read_bytes()),
                            'download_pdf_url': BASE + '/docs/telechargement/licence_bdpm.pdf',
                            'download_pdf_sha256': sha((downloads / 'license.pdf').read_bytes()),
                            'download_pdf_version': '1.0',
                            'note': 'Footer links Open Licence 2.0; linked download contract PDF says version 1.0. '
                                    'Attribution retained. Resolve before Android bundling/full text reuse.'},
                'scope': {'prefixes': list(PREFIXES), 'excluded': ['ALTIZIDE', 'RETARD PHARLON'],
                          'exclusion_reason': 'Combination products and the separate RETARD PHARLON formulation require independent review.'},
                'inputs': {n: sha((output / n).read_bytes()) for n in ('source-rows.json', 'page-evidence.json')}}
    for local, key, url in [('download.html', 'download_page', BASE + '/telechargement'),
                            ('Contenu_et_format_des_fichiers_telechargeables_dans_la_BDM_v5.pdf', 'format_document',
                             BASE + '/download/file/Contenu_et_format_des_fichiers_telechargeables_dans_la_BDM_v5.pdf')]:
        b = (downloads / local).read_bytes()
        manifest[key + '_evidence'] = {'url': url, 'sha256': sha(b), 'bytes': len(b)}
    (output / 'manifest.json').write_bytes(encode(manifest))


def quantity(value, unit, method, source):
    return {'value': str(value), 'unit': unit, 'verification': 'VERIFIED',
            'method': method, 'source': source}


def package_quantity(label, source):
    # A deliberately narrow grammar. Unrecognised labels stay unknown, never guessed.
    if len(re.findall(r'\bde \d+', label)) != 1:
        return None
    count = re.fullmatch(r'(?:1 )?(?:plaquette\(s\)|flacon(?:\(s\))?).*? de (\d+)\s+'
                         r'(comprimé\(s\)|comprimés|capsule\(s\)|capsules)'
                         r'(?: avec fermeture de sécurité enfant)?', label)
    if count:
        unit = 'TABLET' if count[2].startswith('comprim') else 'CAPSULE'
        return quantity(count[1], unit, 'EXPLICIT_LABEL_COUNT', source)
    patch = re.fullmatch(r'(\d+) sachet\(s\).* de (\d+)\s+dispositif\(s\)', label)
    if patch:
        return quantity(int(patch[1]) * int(patch[2]), 'PATCH', 'DERIVED_LABEL_MULTIPLICATION', source)
    return None


def build(manifest, rows, pages):
    ensure(manifest['schema_version'] == 1, 'unsupported input schema')
    ensure(re.fullmatch(r'fr-bdpm-\d{4}-\d{2}-\d{2}\.\d+', manifest['reference_version']), 'invalid reference version')
    ensure(manifest['license']['id'] == 'LicenseRef-BDPM-version-conflict' and
           manifest['license']['verification'] == 'CONFLICT' and manifest['attribution'], 'missing attribution/license conflict')
    iso_date(manifest['verified_on'])
    iso_date(manifest['data_updated_on'])
    ensure(manifest['data_updated_on'] <= manifest['verified_on'], 'future source date')
    by_file = {}
    for name, (encoding, columns) in FILES.items():
        f = rows[name]
        official_url(f['url'])
        ensure(f['encoding'] == encoding and re.fullmatch('[a-f0-9]{64}', f['sha256']), 'invalid source identity')
        indexed = []
        seen = set()
        for r in f['rows']:
            ensure(type(r['line']) is int and r['line'] > 0 and r['line'] not in seen, 'duplicate/invalid source line')
            seen.add(r['line'])
            fields = r['fields']
            ensure(len(fields) == columns and all(isinstance(v, str) for v in fields), 'invalid columns')
            ensure(sha('\t'.join(fields).encode(encoding)) == r['row_sha256'], 'raw row mismatch')
            indexed.append((fields, {'file': name, 'line': r['line'], 'row_sha256': r['row_sha256']}))
        by_file[name] = indexed
    page_map = {}
    for p in pages:
        ensure(p['cis'] not in page_map, 'duplicate CIS page')
        official_url(p['url'])
        ensure(urlsplit(p['url']).path == '/medicament/' + p['cis'] + '/extrait', 'page CIS mismatch')
        ensure(p['verification'] in ('VERIFIED', 'UNVERIFIED'), 'unknown page verification')
        if p['verification'] == 'VERIFIED':
            ensure(p.get('http_status') == 200 and re.fullmatch('[a-f0-9]{64}', p['sha256']), 'invalid page proof')
            iso_date(p['data_updated_on'])
        page_map[p['cis']] = p
    products = {}
    for r, source in by_file['CIS_bdpm.txt']:
        cis, name, form, routes, auth, procedure, market, authorized, flag, eu, holders, monitoring = r
        ensure(re.fullmatch(r'\d{8}', cis) and cis not in products, 'duplicate/invalid CIS')
        ensure(all((name, form, routes, auth, market)), 'missing product field')
        page = page_map.get(cis)
        ensure(page is not None, 'missing page attempt')
        verified_page = page['verification'] == 'VERIFIED'
        ensure(not verified_page or page['name'] == name, 'conflicting product name')
        documents = {}
        for key in ('rcp', 'notice'):
            present = verified_page and key in page['panels']
            documents[key] = {'verification': 'VERIFIED' if present else 'UNVERIFIED',
                              'url': page['url'] if present else None,
                              'panel_id': f'tabpanel-{key}-panel' if present else None,
                              'reason': None if present else 'PANEL_NOT_VERIFIED'}
        products[cis] = {'cis': cis, 'name_fr': name, 'form_fr': form, 'routes_fr': routes.split(';'),
                         'field_verification': {'identity_form_routes': 'VERIFIED_OFFICIAL_DOWNLOAD',
                                                'authorization_marketing': 'VERIFIED_OFFICIAL_DOWNLOAD',
                                                'composition': 'VERIFIED_OFFICIAL_DOWNLOAD',
                                                'rcp_notice_content': 'UNVERIFIED',
                                                'page_identity': page['verification']},
                         'authorization': {'value': AUTH.get(auth, 'UNVERIFIED'), 'raw': auth,
                                           'initial_authorization_on': french_date(authorized)},
                         'marketing': {'value': MARKET.get(market, 'UNVERIFIED'), 'raw': market},
                         'procedure_fr': procedure, 'holders_fr': holders, 'bdpm_flag_fr': flag,
                         'documents': documents, 'source': source, 'page_source': page,
                         'ingredients': [], 'presentations': [], 'supply_notices': [],
                         'supply_verification': 'UNVERIFIED',
                         'unverified_fields': ['authorization_end_date', 'production_status', 'live_supply',
                                               'excipients', 'storage', 'clinical_summaries', 'divisibility'],
                         'conflicts': []}
        if auth not in AUTH or market not in MARKET:
            products[cis]['conflicts'].append('UNKNOWN_OFFICIAL_STATUS')
    ensure(set(page_map) == set(products), 'orphan page evidence')
    ensure(products, 'empty catalog')
    comp_keys = set()
    for r, source in by_file['CIS_COMPO_bdpm.txt']:
        cis, element, code, name, dose, denominator, kind, group = r
        ensure(cis in products, 'orphan ingredient')
        key = (cis, element, code, kind, group)
        ensure(key not in comp_keys, 'duplicate ingredient identity or conflicting strength')
        comp_keys.add(key)
        ensure(all((code, name, dose, denominator, group)), 'missing ingredient field')
        ensure(kind in ('SA', 'FT', 'ST'), 'unknown component type')
        products[cis]['ingredients'].append({'substance_code': code, 'name_fr': name,
                                            'element_fr': element, 'strength_fr': dose,
                                            'denominator_fr': denominator, 'component_type_raw': kind,
                                            'group': group, 'source': source})
        if kind == 'FT':
            products[cis]['conflicts'].append('FORMAT_SPEC_SAYS_ST_SOURCE_USES_FT')
    seen_cips, seen_cip7 = set(), set()
    for r, source in by_file['CIS_CIP_bdpm.txt']:
        cis, cip7, label, auth, market, declared, cip13, *_ = r
        ensure(cis in products, 'orphan presentation')
        ensure(re.fullmatch(r'\d{7}', cip7) and re.fullmatch(r'\d{13}', cip13), 'invalid CIP')
        ensure(cip13 not in seen_cips, 'duplicate CIP13')
        ensure(cip7 not in seen_cip7, 'duplicate CIP7')
        # CIP13 is an EAN-13 identifier; do not infer it by concatenating CIS/CIP7.
        ensure(sum(int(d) * (1 if i % 2 == 0 else 3) for i, d in enumerate(cip13[:12])) % 10
               == (-int(cip13[-1])) % 10, 'CIP checksum mismatch')
        seen_cips.add(cip13)
        seen_cip7.add(cip7)
        ensure(auth and label, 'missing presentation field')
        amount = package_quantity(label, source)
        presentation = {'cis': cis, 'cip7': cip7, 'cip13': cip13, 'label_fr': label,
                        'authorization_raw': auth, 'marketing': {
                            'value': PRESENTATION_MARKET.get(market, 'UNVERIFIED'), 'raw': market,
                            'declared_on': french_date(declared)},
                        'package_quantity': amount, 'source': source,
                        'unverified_fields': [] if amount else ['package_quantity']}
        if market not in PRESENTATION_MARKET:
            presentation['unverified_fields'].append('marketing')
        products[cis]['presentations'].append(presentation)
    for r, source in by_file['CIS_CIP_Dispo_Spec.txt']:
        cis, cip13, status, label, start, updated, restored, url = r
        ensure(cis in products and (not cip13 or cip13 in {x['cip13'] for x in products[cis]['presentations']}),
               'orphan supply notice')
        ensure(status in SUPPLY and SUPPLY[status] == label, 'conflicting supply status')
        official_url(url, 'ansm.sante.fr')
        products[cis]['supply_notices'].append({'cis': cis, 'cip13': cip13 or None, 'status_code': status,
                                               'status_fr': label, 'starts_on': french_date(start),
                                               'updated_on': french_date(updated), 'restored_on': french_date(restored),
                                               'url': url, 'source': source})
        products[cis]['supply_verification'] = 'NOTICE_PRESENT_IN_SNAPSHOT'
    for p in products.values():
        ensure(p['ingredients'], 'missing composition')
        active_groups = {(i['element_fr'], i['group']) for i in p['ingredients'] if i['component_type_raw'] == 'SA'}
        ensure(active_groups, 'missing active substance')
        ensure(all((i['element_fr'], i['group']) in active_groups for i in p['ingredients']
                   if i['component_type_raw'] in ('FT', 'ST')), 'orphan therapeutic fraction')
        if not p['presentations']:
            p['unverified_fields'].append('presentations')
        p['ingredients'].sort(key=lambda x: (x['element_fr'], x['group'], x['component_type_raw'], x['substance_code']))
        p['presentations'].sort(key=lambda x: x['cip13'])
        p['supply_notices'].sort(key=lambda x: (x['url'], x['cip13'] or '', x['status_code']))
        p['conflicts'] = sorted(set(p['conflicts']))
    pump = products.get('63071975')
    if pump:
        source = {'url': pump['page_source']['url'], 'location': 'RCP 6.5',
                  'page_sha256': pump['page_source'].get('sha256')}
        quote = pump['page_source'].get('packaging_quote')
        expected = "La pompe délivre des doses unitaires de 1,25 g de gel, correspondant à 0,75 mg d'estradiol. Flacon de 64 doses."
        ensure(not quote or quote == expected, 'pump packaging source not verified')
        for presentation in pump['presentations']:
            ensure(presentation['cip13'] == '3400933829247' and '80 g' in presentation['label_fr'], 'unexpected pump presentation')
            presentation['package_quantity'] = quantity('80', 'GEL_G', 'EXPLICIT_LABEL', presentation['source'])
            presentation['unverified_fields'] = ['use_count', 'formulation_per_use', 'active_ingredient_per_use']
            if not quote or pump['documents']['rcp']['verification'] != 'VERIFIED':
                continue
            presentation['use_count'] = quantity('64', 'PUMP_ACTUATION', 'EXPLICIT_RCP', source)
            presentation['formulation_per_use'] = quantity('1.25', 'GEL_G', 'EXPLICIT_RCP', source)
            presentation['active_ingredient_per_use'] = quantity('0.75', 'ESTRADIOL_MG', 'EXPLICIT_RCP', source)
            presentation['unverified_fields'] = []
    catalog = {'schema_version': 1, 'generator_version': GENERATOR,
               'reference_version': manifest['reference_version'], 'verified_on': manifest['verified_on'],
               'data_updated_on': manifest['data_updated_on'], 'attribution': manifest['attribution'],
               'data_update_semantics': manifest['data_update_semantics'],
               'license': manifest['license'], 'sources': {n: {k: v for k, v in f.items() if k != 'rows'} for n, f in rows.items()},
               'products': sorted(products.values(), key=lambda p: p['cis'])}
    validate_catalog(catalog)
    return catalog


def validate_quantity(q):
    ensure(q['unit'] in ('TABLET', 'CAPSULE', 'PATCH', 'GEL_G', 'PUMP_ACTUATION', 'ESTRADIOL_MG'), 'unknown quantity unit')
    ensure(isinstance(q['value'], str), 'quantity must be decimal string')
    try:
        v = Decimal(q['value'])
    except InvalidOperation as error:
        raise ValueError('invalid quantity') from error
    ensure(v.is_finite() and v > 0, 'nonpositive/nonfinite quantity')
    ensure(q['verification'] == 'VERIFIED' and q['source'] and q['method'], 'quantity without evidence')
    return v


def validate_catalog(catalog):
    ensure(catalog['schema_version'] == 1, 'unknown catalog schema')
    products, cips = set(), set()
    for p in catalog['products']:
        ensure(p['cis'] not in products, 'duplicate catalog CIS')
        products.add(p['cis'])
        ensure(p['authorization']['value'] in {*AUTH.values(), 'UNVERIFIED'}, 'invalid authorization')
        ensure(p['marketing']['value'] in {*MARKET.values(), 'UNVERIFIED'}, 'invalid marketing')
        for x in p['presentations']:
            ensure(x['cis'] == p['cis'] and x['cip13'] not in cips, 'CIP relation/uniqueness')
            cips.add(x['cip13'])
            for key in ('package_quantity', 'use_count', 'formulation_per_use', 'active_ingredient_per_use'):
                if x.get(key) is not None:
                    validate_quantity(x[key])
            if 'use_count' in x:
                ensure(x['use_count']['unit'] == 'PUMP_ACTUATION' and x['package_quantity']['unit'] == 'GEL_G'
                       and x['formulation_per_use']['unit'] == 'GEL_G'
                       and x['active_ingredient_per_use']['unit'] == 'ESTRADIOL_MG', 'mixed pump dimensions')
                ensure(Decimal(x['use_count']['value']) * Decimal(x['formulation_per_use']['value'])
                       == Decimal(x['package_quantity']['value']), 'pump capacity conflict')


def generate(directory):
    manifest = read_json(directory / 'manifest.json')
    ensure(set(manifest['inputs']) == {'source-rows.json', 'page-evidence.json'}, 'unexpected inputs')
    for name, digest in manifest['inputs'].items():
        ensure(sha((directory / name).read_bytes()) == digest, 'input checksum: ' + name)
    catalog = build(manifest, read_json(directory / 'source-rows.json'), read_json(directory / 'page-evidence.json'))
    raw = encode(catalog)
    report = {'result': 'PASS', 'generator_version': GENERATOR, 'reference_version': catalog['reference_version'],
              'generator_sha256': sha(Path(__file__).read_bytes()),
              'catalog_sha256': sha(raw), 'manifest_sha256': sha((directory / 'manifest.json').read_bytes()),
              'input_checksums': manifest['inputs'], 'product_count': len(catalog['products']),
              'presentation_count': sum(len(p['presentations']) for p in catalog['products']),
              'authorization_counts': dict(Counter(p['authorization']['value'] for p in catalog['products'])),
              'release_blockers': ['LICENSE_VERSION_CONFLICT', 'OWNER_ACCEPTANCE_PENDING',
                                   'NOT_INTEGRATED_IN_ANDROID'],
              'products': [{'cis': p['cis'], 'unverified_fields': p['unverified_fields'], 'conflicts': p['conflicts'],
                            'unverified_presentations': [x['cip13'] for x in p['presentations'] if x['unverified_fields']],
                            'documents': {k: v['verification'] for k, v in p['documents'].items()},
                            'supply_verification': p['supply_verification']} for p in catalog['products']],
              'limitations': ['NOT_A_COMPLETE_FRENCH_MARKET_CATALOG', 'DECLARATION_NOT_REALTIME_AVAILABILITY',
                              'NO_NOTICE_DOES_NOT_ESTABLISH_NORMAL_SUPPLY', 'NO_CLINICAL_OR_PK_VALIDATION',
                              'OFFLINE_REPLAY_USES_CAPTURED_EXCERPTS_NOT_CURRENT_WEBSITE']}
    return {'catalog.json': raw, 'validation-report.json': encode(report)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=DATA)
    parser.add_argument('--capture', type=Path, help='Previously downloaded official files/pages; no network')
    parser.add_argument('--check', action='store_true', help='Require committed outputs to match byte for byte')
    args = parser.parse_args()
    ensure(not (args.capture and args.check), 'capture cannot be combined with check')
    if args.capture:
        capture(args.capture, args.directory)
    for name, content in generate(args.directory).items():
        path = args.directory / name
        if args.check:
            ensure(path.read_bytes() == content, 'stale generated artifact: ' + name)
        else:
            path.write_bytes(content)
    print('PASS: BDPM offline checksums, relations and deterministic outputs')


if __name__ == '__main__':
    main()
