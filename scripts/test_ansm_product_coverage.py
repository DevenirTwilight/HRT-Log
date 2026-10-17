"""Coverage must not turn frozen document observations into approved clinical content."""
import json
from pathlib import Path
import tempfile
import unittest
import build_ansm_product_coverage as coverage


class ProductCoverageTest(unittest.TestCase):
    def test_all_original_identities_and_rows_remain_accounted_for(self):
        value = coverage.build()
        self.assertEqual({'products': 58, 'ingredient_rows': 64, 'presentations': 96}, value['counts'])
        self.assertEqual(58, len({p['cis'] for p in value['products']}))
        self.assertTrue(all(x['cis'] == p['cis'] for p in value['products'] for x in p['presentation_sources']))

    def test_verified_panel_never_promotes_body_translation_supply_or_rights(self):
        value = coverage.build()
        self.assertFalse(value['distribution_approved'])
        self.assertFalse(value['clinical_review_complete'])
        self.assertEqual('CONFLICT', value['rights'])
        for p in value['products']:
            self.assertEqual('UNVERIFIED', p['live_supply'])
            self.assertEqual('NOT_COVERED_BY_FROZEN_CATALOG', p['additional_monitoring'])
            self.assertEqual({'UNVERIFIED'}, set(p['clinical_fields'].values()))
            self.assertEqual('OFFICIAL_DISTRIBUTION_BLOCKED', p['publication_status'])
            for entry in p['document_page_observation']['entries'].values():
                self.assertEqual('UNVERIFIED', entry['body_review'])
                self.assertEqual('NOT_RUN', entry['translation_review'])

    def test_replay_and_committed_output_are_byte_identical(self):
        self.assertEqual(coverage.bdpm.encode(coverage.build()), coverage.OUTPUT.read_bytes())
        self.assertEqual(coverage.build(), coverage.build())

    def test_changed_snapshot_cannot_silently_become_a_reviewed_source(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder)
            for name in ('catalog.json', 'manifest.json', 'source-rows.json', 'page-evidence.json'):
                (target / name).write_bytes((coverage.bdpm.DATA / name).read_bytes())
            original = (target / 'catalog.json').read_bytes()
            for mutation in ('identity', 'clinical', 'rights'):
                value = json.loads(original)
                if mutation == 'identity':
                    value['products'][0]['presentations'][0]['cis'] = '99999999'
                elif mutation == 'clinical':
                    value['products'][0]['field_verification']['rcp_notice_content'] = 'VERIFIED'
                else:
                    value['license']['verification'] = 'ALLOWED'
                (target / 'catalog.json').write_text(json.dumps(value))
                with self.assertRaises(ValueError):
                    coverage.build(target)


if __name__ == '__main__':
    unittest.main()
