"""Source-to-Kotlin evidence gate: frozen exposed-study 40-fit table, no SciPy needed.

Never interprets pseudo-loss as patient likelihood or grants clinical validation.
"""
import csv
import hashlib
import io
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CSV_FILE = ROOT / 'docs/pk-research/p2/p2x-frozen-40-candidates.csv'
KOTLIN = ROOT / 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/experimental/ResearchSublingualV01.kt'
FROZEN_CSV_SHA256 = 'f13aa0a0b27883fe211ee604c7c1a8a413d4e81d7114da2a5c39057db8df0d1b'
CANDIDATE_PATTERN = re.compile(
    r'ExperimentalCandidate\("p2x-(\d+)",\s*(\d+),\s*([\d.eE+-]+),\s*([\d.eE+-]+),'
    r'\s*([\d.eE+-]+),\s*([\d.eE+-]+),\s*([\d.eE+-]+),\s*([\d.eE+-]+),\s*([\d.eE+-]+)\)'
)


def frozen_rows():
    raw = CSV_FILE.read_bytes()
    if hashlib.sha256(raw).hexdigest() != FROZEN_CSV_SHA256:
        raise AssertionError('P2-X frozen 40-row source has been changed; separate review required')
    return list(csv.DictReader(io.StringIO(raw.decode('utf-8'))))


class P2XSourceToKernelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = frozen_rows()
        cls.matches = CANDIDATE_PATTERN.findall(KOTLIN.read_text(encoding='utf-8'))

    def test_40_rows_and_factorial_design(self):
        self.assertEqual(len(self.rows), 40)
        expected = {(cap, b, n) for cap in (100., 225.) for b in (0., 6., 12., 18., 24.) for n in (4, 6, 8, 10)}
        actual = {(float(r['Rosano_baseline_cap']), float(r['Price_baseline']), int(r['n'])) for r in self.rows}
        self.assertEqual(actual, expected)
        self.assertTrue(all(float(r['ke_per_h']) > 0 and float(r['k_fast_per_h']) > float(r['ke_per_h'])
                            and float(r['k_slow_per_h']) > 0 for r in self.rows))

    def test_kotlin_matches_every_selected_row_by_id_and_order(self):
        minimum = min(float(r['pseudo_loss']) for r in self.rows)
        selected = [(i, r) for i, r in enumerate(self.rows) if float(r['pseudo_loss']) <= minimum + .1 + 1e-12]
        self.assertEqual(len(selected), 15)
        self.assertEqual(len(self.matches), len(selected))
        for fields, (index, row) in zip(self.matches, selected):
            actual = [int(fields[0]), int(fields[1])] + [float(s) for s in fields[2:]]
            expected = [index, int(row['n']), float(row['k_fast_per_h']), float(row['ke_per_h']),
                        float(row['k_slow_per_h']), float(row['slow_effective_weight']),
                        float(row['pseudo_loss']), float(row['Price_baseline']), float(row['Rosano_baseline_cap'])]
            for a, b in zip(actual, expected):
                self.assertAlmostEqual(a, b, delta=1e-11, msg=f'P2-X row {index} mismatch')

    def test_hypothetical_baseline_strata_not_probability(self):
        b = [float(row['Price_baseline']) for i, row in enumerate(self.rows)
             if float(row['pseudo_loss']) <= min(float(r['pseudo_loss']) for r in self.rows) + .1 + 1e-12]
        self.assertEqual({v: b.count(v) for v in sorted(set(b))}, {0.:1, 6.:1, 12.:2, 18.:5, 24.:6})

    def test_experimental_only_and_no_clinical_label(self):
        text = KOTLIN.read_text(encoding='utf-8')
        for signal in ('const val productionEnabled = false', 'const val humanCoverageValidated = false',
                       'const val individualPredictionValidated = false'):
            self.assertIn(signal, text)


if __name__ == '__main__':
    unittest.main()
