"""Verify retained LAB-03 reports offline; GitHub run links establish their external provenance."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
receipt = json.loads((root / 'docs/lab03/ci-403ae960.json').read_text())
assert receipt['implementation_head'] == '403ae96015ea95f3379e4a95ea0b28e7f8d4c846'
assert len(receipt['required_checks_both_events']) == 6
assert all(v == 'PASS' for v in receipt['required_checks_both_events'].values())
native = 0
for item in receipt['reports']:
    path = root / item['path']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256'], path
    if item['kind'] == 'junit-xml':
        report = ET.parse(path).getroot()
        assert int(report.attrib['tests']) > 0
        assert int(report.attrib['failures']) == int(report.attrib['errors']) == int(report.attrib['skipped']) == 0
    else:
        text = path.read_text()
        if item['kind'] == 'native-html':
            native += 1
            for method in item['required_pass_methods']:
                assert re.search(r'<td>' + re.escape(method) + r'</td>\s*<td class="success">passed', text), (path, method)
            assert re.search(r'id="failures">\s*<div class="counter">0</div>', text), path
        else:
            counts = {label: int(number) for number, label in re.findall(
                r'<div class="counter">(\d+)</div>\s*<p>(tests|failures|skipped)</p>', text)}
            assert counts['tests'] > 0 and counts['failures'] == 0, (path, counts)
            expected = 237 if path.name == 'app-summary.html' else 0
            assert counts['skipped'] == expected, (path, counts)
assert native == 6
print('PASS: report hashes, six required CI categories, API26/29/35 methods; 237 existing App skips retained explicitly')
