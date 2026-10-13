"""Compare both independently recomputed SciPy reports against frozen evidence."""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def compare(actual, expected, path='$'):
    if isinstance(expected, dict):
        if actual.keys() != expected.keys():
            raise AssertionError(f'{path}: keys changed')
        for key in expected:
            compare(actual[key], expected[key], f'{path}.{key}')
    elif isinstance(expected, list):
        if len(actual) != len(expected):
            raise AssertionError(f'{path}: length changed')
        for i, (a, b) in enumerate(zip(actual, expected)):
            compare(a, b, f'{path}[{i}]')
    elif isinstance(expected, float):
        if not math.isclose(actual, expected, rel_tol=1e-9, abs_tol=1e-7):
            raise AssertionError(f'{path}: {actual} != {expected}')
    elif actual != expected:
        raise AssertionError(f'{path}: {actual} != {expected}')


if __name__ == '__main__':
    for actual, expected in zip(sys.argv[1:],
            ['docs/design/pr19-calibration-evidence/reference.json',
             'docs/design/m2-only-evidence/independent-reference.json']):
        compare(json.loads(Path(actual).read_text()), json.loads((ROOT / expected).read_text()))
        print(f'independent reference passed: {expected}')
