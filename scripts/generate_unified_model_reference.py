#!/usr/bin/env python3
"""Reproduce M1 reference with the independent P2-AW Python kernel; no personal data."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/pk-research'))
from p2aw_sampling_design import m2_normalized

def reference():
    model_path = ROOT / 'docs/design/unified-model-inputs/m1-candidates.json'
    model = json.loads(model_path.read_text())
    design_path = ROOT / 'docs/design/unified-model-inputs/p2az-design.json'
    if hashlib.sha256(design_path.read_bytes()).hexdigest() != model['design_sha256']:
        raise ValueError('Source P2-AZ design changed')
    times = [0, 1e-8, .05, .25, .5, 1, 2, 4, 8, 12, 24, 48, 720]
    rows = []
    for row in model['rows']:
        parameter = dict(n_fast=row['n'], k_fast_per_h=row['kf'], k_elim_per_h=row['ke'], k_slow_per_h=row['ks'], effective_slow_weight=row['w'])
        rows.append(dict(id=row['id'], values=[m2_normalized(t, parameter) for t in times]))
    return dict(source='tools/pk-research/p2aw_sampling_design.py m2_normalized; independent Poisson/gamma evaluation',
        source_sha256=hashlib.sha256((ROOT / 'tools/pk-research/p2aw_sampling_design.py').read_bytes()).hexdigest(),
        parameter_sha256=hashlib.sha256(model_path.read_bytes()).hexdigest(), times=times, reference=rows)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Compare checked-in reference without overwriting it')
    parser.add_argument('--out', type=Path, help='Write a new reference outside the repository')
    args = parser.parse_args()
    data = reference()
    if args.check:
        frozen = json.loads((ROOT / 'pk-engine/src/test/resources/unified-m1-python-reference.json').read_text())
        for key in ('source', 'source_sha256', 'parameter_sha256', 'times'):
            if data[key] != frozen[key]: raise ValueError('Reference identity changed: ' + key)
        if len(data['reference']) != len(frozen['reference']): raise ValueError('Candidate count changed')
        for a, b in zip(data['reference'], frozen['reference']):
            if a['id'] != b['id'] or len(a['values']) != len(b['values']): raise ValueError('Candidate/grid changed')
            if not all(math.isclose(x, y, rel_tol=1e-12, abs_tol=1e-14) for x, y in zip(a['values'], b['values'])):
                raise ValueError('Reference value changed: ' + a['id'])
        print('32 candidates × 13 times: independent reference matches')
    elif args.out:
        target = args.out.resolve()
        if target == ROOT or ROOT in target.parents: parser.error('Write only outside the source repository; review before freezing')
        target.write_text(json.dumps(data, indent=2) + '\n')
    else:
        parser.error('Choose --check or --out')
