"""ANSM-02A synthetic distribution boundary and debug-only localization contracts."""
import json
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET
from check_attachment_release_isolation import check_apk
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]

class Ansm02BoundaryTest(unittest.TestCase):
    def test_synthetic_asset_is_not_an_official_catalogue(self):
        p = ROOT / 'app/src/debug/assets/ansm02-synthetic.json'
        value = json.loads(p.read_text())
        self.assertEqual('SYNTHETIC_ONLY', value['kind'])
        self.assertTrue(value['version'].startswith('synthetic-'))
        for product in value['products']:
            self.assertRegex(product['cis'], r'^SYNTH-CIS-[0-9]+$')
            self.assertNotIn('url', product)
            for presentation in product['presentations']:
                self.assertEqual(product['cis'], presentation['cis'])
                self.assertRegex(presentation['cip'], r'^SYNTH-CIP-[0-9]+$')

    def test_no_production_or_private_shell_entry(self):
        for source in ('main', 'full', 'release'):
            for p in (ROOT / f'app/src/{source}').rglob('*'):
                if p.is_file() and p.suffix in ('.kt', '.xml', '.json'):
                    self.assertNotRegex(p.read_text(), r'CatalogPrototype|SYNTH-CIS-|ansm02-synthetic')
        manifest = ET.parse(ROOT / 'app/src/debug/AndroidManifest.xml')
        hosts = [x for x in manifest.findall('.//activity') if x.get('{http://schemas.android.com/apk/res/android}name', '').endswith('CatalogPrototypeActivity')]
        self.assertEqual(1, len(hosts))
        self.assertEqual('false', hosts[0].get('{http://schemas.android.com/apk/res/android}exported'))
        self.assertEqual([], hosts[0].findall('intent-filter'))
        for p in (ROOT / 'app/src/debug/java/net/plainnotes/app/debug/catalog').glob('*.kt'):
            self.assertNotRegex(p.read_text(), r'import net\.plainnotes\.app\.(data|pk|reminder)\.|NotesRepository|MedicationEntity|ProfileEntity|Intent\(|startActivity\(')

    def test_four_debug_languages_have_identical_keys_and_placeholders(self):
        strings = []
        for folder in ('values', 'values-fr', 'values-zh', 'values-b+zh+Hant'):
            root = ET.parse(ROOT / f'app/src/debug/res/{folder}/ansm_catalog.xml').getroot()
            rows = {x.attrib['name']: x.text or '' for x in root}
            self.assertEqual(len(root), len(rows))
            self.assertTrue(all(text.strip() for text in rows.values()))
            strings.append({key: sorted(re.findall(r'%\d+\$[sd]', text)) for key, text in rows.items()})
        for translated in strings[1:]:
            self.assertEqual(strings[0], translated)

    def test_synthetic_asset_is_rejected_even_if_code_was_removed(self):
        with tempfile.TemporaryDirectory() as root:
            p = Path(root) / 'bad.apk'
            with zipfile.ZipFile(p, 'w') as z:
                z.writestr('classes.dex', b'clean')
                z.writestr('AndroidManifest.xml', b'clean')
                z.writestr('assets/ansm02-synthetic.json', b'{}')
            with self.assertRaisesRegex(AssertionError, 'Synthetic catalogue asset'):
                check_apk(p)

if __name__ == '__main__':
    unittest.main()
