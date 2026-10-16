"""Reference-data contracts: official public snapshot plus synthetic corruptions; no patient fixtures."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('bdpm_reference', Path(__file__).with_name('bdpm_reference.py'))
bdpm = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bdpm)


class BdpmReferenceTest(unittest.TestCase):
    def setUp(self):
        self.manifest = bdpm.read_json(bdpm.DATA / 'manifest.json')
        self.rows = bdpm.read_json(bdpm.DATA / 'source-rows.json')
        self.pages = bdpm.read_json(bdpm.DATA / 'page-evidence.json')

    def build(self):
        return bdpm.build(self.manifest, self.rows, self.pages)

    def change(self, file, index, column, value):
        r = self.rows[file]['rows'][index]
        r['fields'][column] = value
        r['row_sha256'] = bdpm.sha('\t'.join(r['fields']).encode(self.rows[file]['encoding']))

    def test_offline_replay_is_byte_identical(self):
        first = bdpm.generate(bdpm.DATA)
        self.assertEqual(first, bdpm.generate(bdpm.DATA))
        for name, content in first.items():
            self.assertEqual((bdpm.DATA / name).read_bytes(), content, name)

    def test_target_coverage_and_distinct_presentations(self):
        catalog = self.build()
        self.assertEqual(58, len(catalog['products']))
        self.assertEqual(96, sum(len(p['presentations']) for p in catalog['products']))
        for prefix in ('PROVAMES ', 'PROGYNOVA ', 'OESTRODOSE ', 'DERMESTRIL ', 'THAISSEPT ',
                       'ANDROCUR ', 'ACETATE DE CYPROTERONE ', 'ALDACTONE ', 'SPIRONOLACTONE ',
                       'UTROGESTAN ', 'PROGESTERONE '):
            self.assertTrue(any(p['name_fr'].startswith(prefix) for p in catalog['products']), prefix)
        self.assertFalse(any('ALTIZIDE' in p['name_fr'] or 'RETARD PHARLON' in p['name_fr'] for p in catalog['products']))

    def test_authorization_and_marketing_are_independent(self):
        p = {p['cis']: p for p in self.build()['products']}
        for cis in ('60940721', '62512491'):
            self.assertEqual('ARCHIVED', p[cis]['authorization']['value'])
            self.assertEqual('NOT_MARKETED', p[cis]['marketing']['value'])
            self.assertEqual('STOPPED_DECLARED', p[cis]['presentations'][0]['marketing']['value'])
            self.assertEqual('Présentation active', p[cis]['presentations'][0]['authorization_raw'])
        self.assertEqual('2022-04-25', p['60940721']['presentations'][0]['marketing']['declared_on'])
        self.assertEqual('2022-02-23', p['62512491']['presentations'][0]['marketing']['declared_on'])
        pfizer = next(x for x in p.values() if x['name_fr'].startswith('SPIRONOLACTONE PFIZER 25'))
        self.assertEqual('ACTIVE', pfizer['authorization']['value'])
        self.assertEqual('NOT_MARKETED', pfizer['marketing']['value'])

    def test_missing_supply_is_unverified_not_normal(self):
        for p in self.build()['products']:
            self.assertEqual([], p['supply_notices'])
            self.assertEqual('UNVERIFIED', p['supply_verification'])
            self.assertIn('production_status', p['unverified_fields'])

    def test_salts_and_therapeutic_fractions_are_kept_separate(self):
        p = next(p for p in self.build()['products'] if p['cis'] == '61614618')
        self.assertEqual({'SA', 'FT'}, {i['component_type_raw'] for i in p['ingredients']})
        self.assertEqual({'2,50 mg', '2,58 mg'}, {i['strength_fr'] for i in p['ingredients']})
        self.assertIn('FORMAT_SPEC_SAYS_ST_SOURCE_USES_FT', p['conflicts'])

    def test_vaginal_and_oral_routes_not_collapsed(self):
        p = {p['cis']: p for p in self.build()['products']}
        self.assertEqual(['orale', 'vaginale'], p['61673424']['routes_fr'])
        self.assertEqual(['vaginale'], p['64358280']['routes_fr'])
        self.assertEqual('2,000 mg', p['60940721']['ingredients'][0]['strength_fr'])
        self.assertEqual("VALÉRATE D'ESTRADIOL", p['60940721']['ingredients'][0]['name_fr'])

    def test_rcp_notice_distinct_panels_same_observed_product_page(self):
        for p in self.build()['products']:
            for key in ('rcp', 'notice'):
                d = p['documents'][key]
                self.assertEqual('VERIFIED', d['verification'])
                self.assertEqual('tabpanel-' + key + '-panel', d['panel_id'])
                self.assertEqual(p['page_source']['url'], d['url'])

    def test_missing_notice_does_not_inherit_rcp(self):
        self.pages[0]['panels'].remove('notice')
        p = next(p for p in self.build()['products'] if p['cis'] == self.pages[0]['cis'])
        self.assertEqual('UNVERIFIED', p['documents']['notice']['verification'])
        self.assertIsNone(p['documents']['notice']['url'])
        self.assertEqual('VERIFIED', p['documents']['rcp']['verification'])

    def test_unreachable_page_does_not_reuse_unverified_fields(self):
        self.pages[0] = {'cis': self.pages[0]['cis'], 'url': self.pages[0]['url'],
                         'verification': 'UNVERIFIED', 'error': 'synthetic timeout'}
        p = next(p for p in self.build()['products'] if p['cis'] == self.pages[0]['cis'])
        self.assertEqual('UNVERIFIED', p['documents']['rcp']['verification'])
        self.assertEqual('ACTIVE', p['authorization']['value'])  # independently verified bulk field

    def test_name_conflict_fails(self):
        self.pages[0]['name'] = 'Synthetic different formulation'
        with self.assertRaisesRegex(ValueError, 'conflicting product name'):
            self.build()

    def test_duplicate_product_fails(self):
        r = copy.deepcopy(self.rows['CIS_bdpm.txt']['rows'][0]); r['line'] = 999999
        self.rows['CIS_bdpm.txt']['rows'].append(r)
        with self.assertRaisesRegex(ValueError, 'duplicate/invalid CIS'):
            self.build()

    def test_duplicate_source_line_fails(self):
        self.rows['CIS_bdpm.txt']['rows'].append(self.rows['CIS_bdpm.txt']['rows'][0])
        with self.assertRaisesRegex(ValueError, 'duplicate/invalid source line'):
            self.build()

    def test_duplicate_cip_fails(self):
        r = copy.deepcopy(self.rows['CIS_CIP_bdpm.txt']['rows'][0]); r['line'] = 999999
        self.rows['CIS_CIP_bdpm.txt']['rows'].append(r)
        with self.assertRaisesRegex(ValueError, 'duplicate CIP13'):
            self.build()

    def test_bad_cip_check_digit_fails(self):
        r = self.rows['CIS_CIP_bdpm.txt']['rows'][0]['fields']
        self.change('CIS_CIP_bdpm.txt', 0, 6, r[6][:-1] + str((int(r[6][-1]) + 1) % 10))
        with self.assertRaisesRegex(ValueError, 'CIP checksum'):
            self.build()

    def test_orphan_presentation_fails(self):
        self.change('CIS_CIP_bdpm.txt', 0, 0, '00000000')
        with self.assertRaisesRegex(ValueError, 'orphan presentation'):
            self.build()

    def test_missing_ingredient_fails(self):
        cis = self.rows['CIS_bdpm.txt']['rows'][0]['fields'][0]
        self.rows['CIS_COMPO_bdpm.txt']['rows'] = [r for r in self.rows['CIS_COMPO_bdpm.txt']['rows'] if r['fields'][0] != cis]
        with self.assertRaisesRegex(ValueError, 'missing composition'):
            self.build()

    def test_duplicate_ingredient_fails(self):
        r = copy.deepcopy(self.rows['CIS_COMPO_bdpm.txt']['rows'][0]); r['line'] = 999999
        self.rows['CIS_COMPO_bdpm.txt']['rows'].append(r)
        with self.assertRaisesRegex(ValueError, 'duplicate ingredient'):
            self.build()

    def test_conflicting_strength_for_same_ingredient_identity_fails(self):
        r = copy.deepcopy(self.rows['CIS_COMPO_bdpm.txt']['rows'][0]); r['line'] = 999999
        r['fields'][4] = '999 mg'
        r['row_sha256'] = bdpm.sha('\t'.join(r['fields']).encode('cp1252'))
        self.rows['CIS_COMPO_bdpm.txt']['rows'].append(r)
        with self.assertRaisesRegex(ValueError, 'conflicting strength'):
            self.build()

    def test_therapeutic_fraction_requires_its_own_active_substance_group(self):
        index = next(i for i, r in enumerate(self.rows['CIS_COMPO_bdpm.txt']['rows']) if r['fields'][6] == 'FT')
        self.change('CIS_COMPO_bdpm.txt', index, 7, '999')
        with self.assertRaisesRegex(ValueError, 'orphan therapeutic fraction'):
            self.build()

    def test_unknown_status_is_unverified_with_conflict(self):
        self.change('CIS_bdpm.txt', 0, 4, 'Synthetic new status')
        cis = self.rows['CIS_bdpm.txt']['rows'][0]['fields'][0]
        p = next(p for p in self.build()['products'] if p['cis'] == cis)
        self.assertEqual('UNVERIFIED', p['authorization']['value'])
        self.assertIn('UNKNOWN_OFFICIAL_STATUS', p['conflicts'])

    def test_expired_authorization_is_not_active(self):
        self.change('CIS_bdpm.txt', 0, 4, 'Autorisation expirée')
        cis = self.rows['CIS_bdpm.txt']['rows'][0]['fields'][0]
        self.assertEqual('EXPIRED', next(p for p in self.build()['products'] if p['cis'] == cis)['authorization']['value'])

    def test_unknown_package_capacity_stays_null(self):
        self.change('CIS_CIP_bdpm.txt', 0, 2, 'Synthetic package, quantity unspecified')
        cis = self.rows['CIS_CIP_bdpm.txt']['rows'][0]['fields'][0]
        p = next(p for p in self.build()['products'] if p['cis'] == cis)
        x = next(x for x in p['presentations'] if x['label_fr'].startswith('Synthetic'))
        self.assertIsNone(x['package_quantity'])
        self.assertIn('package_quantity', x['unverified_fields'])

    def test_quantity_grammar_rejects_ambiguous_and_multi_pack_labels(self):
        for label in ('2 flacon(s) de 30 comprimé(s)', '3 plaquette(s) de 10 comprimé(s)', '30 ml', '', 'de 0 capsule(s)',
                      'plaquette(s) de 2 comprimé(s) et plaquette(s) de 30 comprimé(s)'):
            self.assertIsNone(bdpm.package_quantity(label, {'synthetic': True}), label)
        self.assertEqual('8', bdpm.package_quantity('8 sachet(s) synthetic de 1 dispositif(s)', {'synthetic': True})['value'])

    def test_pump_physical_quantities_are_distinct(self):
        p = next(p for p in self.build()['products'] if p['cis'] == '63071975')
        x = p['presentations'][0]
        self.assertEqual(('80', 'GEL_G'), (x['package_quantity']['value'], x['package_quantity']['unit']))
        self.assertEqual(('64', 'PUMP_ACTUATION'), (x['use_count']['value'], x['use_count']['unit']))
        self.assertEqual('1.25', x['formulation_per_use']['value'])
        self.assertEqual(('0.75', 'ESTRADIOL_MG'), (x['active_ingredient_per_use']['value'], x['active_ingredient_per_use']['unit']))

    def test_pump_source_change_fails_before_interpreting(self):
        p = next(p for p in self.pages if p['cis'] == '63071975')
        p['packaging_quote'] = p['packaging_quote'].replace('0,75', '0,70')
        with self.assertRaisesRegex(ValueError, 'packaging source'):
            self.build()

    def test_missing_pump_rcp_keeps_quantities_unverified(self):
        p = next(p for p in self.pages if p['cis'] == '63071975')
        p.pop('packaging_quote')
        p['panels'].remove('rcp')
        pump = next(p for p in self.build()['products'] if p['cis'] == '63071975')
        x = pump['presentations'][0]
        self.assertEqual('80', x['package_quantity']['value'])  # separate official label
        self.assertNotIn('use_count', x)
        self.assertIn('use_count', x['unverified_fields'])
        self.assertEqual('UNVERIFIED', pump['documents']['rcp']['verification'])

    def test_mixed_dimensions_fail(self):
        c = self.build(); p = next(p for p in c['products'] if p['cis'] == '63071975')
        p['presentations'][0]['formulation_per_use']['unit'] = 'ESTRADIOL_MG'
        with self.assertRaisesRegex(ValueError, 'mixed pump dimensions'):
            bdpm.validate_catalog(c)

    def test_conflicting_pump_capacity_fails(self):
        c = self.build(); p = next(p for p in c['products'] if p['cis'] == '63071975')
        p['presentations'][0]['use_count']['value'] = '60'
        with self.assertRaisesRegex(ValueError, 'pump capacity conflict'):
            bdpm.validate_catalog(c)

    def test_fractional_numbers_do_not_imply_divisibility(self):
        q = bdpm.quantity('0.5', 'TABLET', 'SYNTHETIC', {'synthetic': True})
        self.assertEqual(bdpm.Decimal('0.5'), bdpm.validate_quantity(q))
        self.assertTrue(all('divisibility' in p['unverified_fields'] for p in self.build()['products']))

    def test_nonfinite_negative_and_unitless_values_fail(self):
        for value in ('NaN', 'Infinity', '-0.1', '0', '1/2'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                bdpm.validate_quantity(bdpm.quantity(value, 'TABLET', 'SYNTHETIC', {'synthetic': True}))
        with self.assertRaisesRegex(ValueError, 'unknown quantity unit'):
            bdpm.validate_quantity(bdpm.quantity('1', 'MG_PLUS_ML', 'SYNTHETIC', {'synthetic': True}))

    def test_input_checksum_tampering_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            for name in ('manifest.json', 'source-rows.json', 'page-evidence.json'):
                (d / name).write_bytes((bdpm.DATA / name).read_bytes())
            with (d / 'source-rows.json').open('ab') as f:
                f.write(b' ')
            with self.assertRaisesRegex(ValueError, 'input checksum'):
                bdpm.generate(d)

    def test_raw_row_tampering_fails(self):
        self.rows['CIS_bdpm.txt']['rows'][0]['fields'][1] = 'Synthetic changed name'
        with self.assertRaisesRegex(ValueError, 'raw row mismatch'):
            self.build()

    def test_future_dates_unknown_schema_and_missing_license_fail(self):
        for mutation in ({'data_updated_on': '2099-01-01'}, {'schema_version': 2}, {'license': {'id': 'MIT'}}):
            with self.subTest(mutation=mutation):
                self.manifest = {**self.manifest, **mutation}
                with self.assertRaises(ValueError):
                    self.build()
                self.manifest = bdpm.read_json(bdpm.DATA / 'manifest.json')

    def test_duplicate_json_keys_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'test.json'; p.write_text('{"cis":"11111111","cis":"22222222"}')
            with self.assertRaisesRegex(ValueError, 'duplicate JSON key'):
                bdpm.read_json(p)

    def test_untrusted_page_urls_and_wrong_cis_fail(self):
        for url in ('https://example.com/medicament/60021127/extrait', BASE_URL_WRONG_CIS):
            with self.subTest(url=url):
                self.pages[0]['url'] = url
                with self.assertRaises(ValueError):
                    self.build()
                self.pages = bdpm.read_json(bdpm.DATA / 'page-evidence.json')

    def test_license_conflict_is_a_release_blocker(self):
        self.assertEqual('CONFLICT', self.build()['license']['verification'])
        report = bdpm.read_json(bdpm.DATA / 'validation-report.json')
        self.assertIn('LICENSE_VERSION_CONFLICT', report['release_blockers'])

    def supply_row(self, fields):
        self.rows['CIS_CIP_Dispo_Spec.txt']['rows'].append({
            'line': 999999, 'fields': fields, 'row_sha256': bdpm.sha('\t'.join(fields).encode('cp1252'))})

    def test_supply_event_does_not_change_authorization_or_sales(self):
        self.supply_row(['65421374', '', '2', "Tension d'approvisionnement", '01/10/2026', '10/10/2026', '',
                         'https://ansm.sante.fr/synthetic-test-only'])
        p = next(p for p in self.build()['products'] if p['cis'] == '65421374')
        self.assertEqual('ACTIVE', p['authorization']['value'])
        self.assertEqual('MARKETED_DECLARED', p['marketing']['value'])
        self.assertEqual('NOTICE_PRESENT_IN_SNAPSHOT', p['supply_verification'])
        self.assertIsNone(p['supply_notices'][0]['cip13'])  # whole product, not a guessed package

    def test_supply_cannot_reference_another_products_cip(self):
        self.supply_row(['65421374', '3400933829247', '1', 'Rupture de stock', '01/10/2026', '10/10/2026', '',
                         'https://ansm.sante.fr/synthetic-test-only'])
        with self.assertRaisesRegex(ValueError, 'orphan supply notice'):
            self.build()

    def test_supply_label_and_code_conflict_fails(self):
        self.supply_row(['65421374', '', '1', 'Remise à disposition', '01/10/2026', '10/10/2026', '',
                         'https://ansm.sante.fr/synthetic-test-only'])
        with self.assertRaisesRegex(ValueError, 'conflicting supply status'):
            self.build()


BASE_URL_WRONG_CIS = bdpm.BASE + '/medicament/99999999/extrait'

if __name__ == '__main__':
    unittest.main()
