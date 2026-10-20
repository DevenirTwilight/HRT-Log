"""Check retained final-source LAB-UI-03 local evidence; CI/native execution is a separate gate."""
from pathlib import Path
import hashlib
import json
import struct
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]
receipt = json.loads((root / 'docs/labs/ui03/integration-final-evidence.json').read_text())
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
count = 0
for report in receipt['reports']:
    path = root / report['path']
    assert sha(path) == report['sha256'], path
    suite = ET.parse(path).getroot()
    assert all(int(suite.attrib.get(k, '0')) == 0 for k in ('failures', 'errors', 'skipped')), path
    assert int(suite.attrib['tests']) == report['tests'], path
    count += report['tests']
assert count == receipt['tests'] == 85
assert receipt['captured_pngs'] == len(receipt['captures']) == 96
retained = 0
for capture in receipt['captures']:
    if capture['retained_path'] is None:
        continue
    path = root / capture['retained_path']
    assert sha(path) == capture['sha256'], path
    assert struct.unpack('>II', path.read_bytes()[16:24]) == (capture['width_px'], capture['height_px']), path
    retained += 1
assert retained == receipt['retained_pngs'] == 48
for relative, expected in receipt['source_sha256'].items():
    assert sha(root / relative) == expected, relative
for invariant in receipt['protected_invariants']:
    assert sha(root / invariant['path']) == invariant['sha256'], invariant['path']
assert receipt['physical_phone'] == receipt['talkback'] == 'NOT_RUN'
print('PASS: 85 local tests, 48 retained final PNGs, source fingerprints and 14 protected files; CI/native gates separate')
