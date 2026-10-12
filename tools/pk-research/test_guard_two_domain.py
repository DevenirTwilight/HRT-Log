"""Two-domain P2 guard (audit B0 / owner decision D-13): every negative case must be rejected, not skipped."""
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
import check_protocol as guard

P2 = guard.P2
ROOT = guard.ROOT
CLS = json.loads((P2 / 'guard-classification.json').read_text())
BASELINE = json.loads((P2 / 'production-baseline.json').read_text())
RESULTS = ROOT / CLS['historical']['results_dir']
FILES = CLS['historical']['result_files']
F1_COMMIT = 'd892e9b7ea9d933687c18b5ca2fd3f605b0d1118'  # PD-2026-10-10-F1: AppShell.kt there differs from the frozen baseline


def copy_p2(directory):
    p = Path(directory)
    for f in P2.glob('*'):
        if f.is_file():
            shutil.copy(f, p / f.name)
    return p


def relock(p):
    """Re-hashes a tampered classification so that the check under test is the one that must fail."""
    text = (p / 'guard-classification.json').read_bytes()
    (p / 'guard-classification-lock.json').write_text(json.dumps({'guard_classification_sha256': hashlib.sha256(text).hexdigest()}))


def edit_cls(p, change):
    data = json.loads((p / 'guard-classification.json').read_text()); change(data)
    (p / 'guard-classification.json').write_text(json.dumps(data)); relock(p)


def production_copy(directory):
    """All locked/classified production files under a temporary root, byte-identical to the working tree."""
    root = Path(directory)
    for name in list(BASELINE['files']) + list(CLS['live_lock_additional']):
        target = root / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copy(ROOT / name, target)
    for glob in CLS['dependency_scan']['globs']:
        for tool in ROOT.glob(glob):
            target = root / tool.relative_to(ROOT); target.parent.mkdir(parents=True, exist_ok=True); shutil.copy(tool, target)
    return root


class ClassificationTest(unittest.TestCase):
    def test_partition_is_exact_and_counts_match_the_approved_design(self):
        cls = guard.classification()
        self.assertEqual((len(cls['live_lock_baseline']), len(cls['display_review']), len(cls['history_only'])), (43, 7, 114))
        self.assertEqual(len(BASELINE['files']), 164)
        self.assertEqual(set(cls['live_lock_additional']), {'pk-engine/src/test/resources/non-sl-calibration-v1.json',
                                                           'pk-engine/src/test/resources/sublingual-literature-validation.json'})

    def test_research_inputs_and_evidence_stay_locked(self):
        live = set(CLS['live_lock_baseline'])
        for name in ['pk-engine/src/main/resources/pk-params.json', 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/Engine.kt',
                     'pk-engine/src/main/kotlin/net/plainnotes/app/pk/LabFit.kt', 'app/src/main/java/net/plainnotes/app/conc/ConcentrationCalculator.kt',
                     'app/src/main/java/net/plainnotes/app/conc/LabEstimate.kt']:
            self.assertIn(name, live)
        self.assertTrue(all(n.startswith('docs/pk-research/') for n in live if not n.startswith(('pk-engine/', 'app/src/main/java/net/plainnotes/app/conc/'))))

    def test_current_tree_passes(self):
        guard.check()

    def test_tampered_classification_without_lock_fails(self):  # N9
        with tempfile.TemporaryDirectory() as d:
            p = copy_p2(d); data = json.loads((p / 'guard-classification.json').read_text())
            data['history_only'].append(data['live_lock_baseline'].pop(0))
            (p / 'guard-classification.json').write_text(json.dumps(data))
            with self.assertRaises(ValueError): guard.check(p, historical=False)

    def test_reclassifying_a_research_input_as_history_only_is_caught(self):  # N9 with a relocked classification
        with tempfile.TemporaryDirectory() as d, tempfile.TemporaryDirectory() as r:
            p = copy_p2(d); root = production_copy(r)
            def move(c):
                c['live_lock_baseline'].remove('pk-engine/src/main/resources/pk-params.json'); c['history_only'].append('pk-engine/src/main/resources/pk-params.json')
            edit_cls(p, move)
            with self.assertRaises(ValueError) as e: guard.check(p, production_root=root, historical=False)
            self.assertIn('unlocked production files', str(e.exception))


class CurrentDomainTest(unittest.TestCase):
    def _check_after(self, name, mutate):
        with tempfile.TemporaryDirectory() as d, tempfile.TemporaryDirectory() as r:
            p = copy_p2(d); root = production_copy(r); mutate(root / name)
            return guard.check(p, production_root=root, historical=False)

    def test_ordinary_product_changes_pass(self):  # positive
        for name in ['app/src/main/res/values-zh/strings.xml', 'app/src/main/java/net/plainnotes/app/NotesViewModel.kt',
                     'core/data/src/main/java/net/plainnotes/app/data/NotesRepository.kt', 'app/src/main/java/net/plainnotes/app/ui/SettingsScreen.kt']:
            self.assertIn(name, CLS['history_only'])
            self._check_after(name, lambda f: f.write_bytes(f.read_bytes() + b'\n'))
            self._check_after(name, lambda f: f.unlink())

    def test_pk_parameters_or_kernel_byte_change_fails(self):  # N1 / N10
        for name in ['pk-engine/src/main/resources/pk-params.json', 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/Engine.kt']:
            with self.assertRaises(ValueError): self._check_after(name, lambda f: f.write_bytes(f.read_bytes() + b' '))
            with self.assertRaises(ValueError): self._check_after(name, lambda f: f.unlink())

    def test_archived_evidence_change_fails(self):
        with self.assertRaises(ValueError): self._check_after('docs/pk-research/results/sublingual-p0-report.md', lambda f: f.write_bytes(f.read_bytes() + b'\n'))

    def test_newly_locked_research_resources_fail_on_change(self):  # N9 (indirect research input)
        for name in CLS['live_lock_additional']:
            with self.assertRaises(ValueError): self._check_after(name, lambda f: f.write_bytes(f.read_bytes() + b' '))

    def test_pk_display_change_without_approval_fails(self):  # N11
        with self.assertRaises(ValueError) as e:
            self._check_after('app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt', lambda f: f.write_bytes(f.read_bytes() + b'\n'))
        self.assertIn('owner approval', str(e.exception))

    def test_unverifiable_or_non_owner_approval_fails(self):  # N11
        name = 'app/src/main/java/net/plainnotes/app/ui/ConcChart.kt'
        new = hashlib.sha256((ROOT / name).read_bytes() + b'\n').hexdigest()
        base = {'id': 'PD-TEST-UNVERIFIABLE', 'file': name, 'date': '2026-10-12', 'scope': 'test', 'previous_sha256': BASELINE['files'][name], 'new_sha256': new}
        for record in [dict(base, approver='someone-else', evidence={'commit': 'HEAD', 'path': 'docs/REQUIREMENTS.md'}),
                       dict(base, approver=guard.OWNER),
                       dict(base, approver=guard.OWNER, evidence={'commit': 'HEAD', 'path': 'docs/REQUIREMENTS.md'})]:  # id not in REQUIREMENTS
            with tempfile.TemporaryDirectory() as d, tempfile.TemporaryDirectory() as r:
                p = copy_p2(d); root = production_copy(r)
                (p / 'display-approvals.json').write_text(json.dumps({'approvals': [record]}))
                (root / name).write_bytes((root / name).read_bytes() + b'\n')
                with self.assertRaises(ValueError): guard.check(p, production_root=root, historical=False)

    def test_deviation_chain_tampering_fails(self):  # N12
        def mutate(change):
            with tempfile.TemporaryDirectory() as d:
                p = copy_p2(d); data = json.loads((p / 'protocol-deviations.json').read_text()); change(data)
                (p / 'protocol-deviations.json').write_text(json.dumps(data))
                with self.assertRaises(ValueError): guard.check(p, historical=False)
        mutate(lambda x: x['deviations'].clear())
        mutate(lambda x: x['deviations'][0].update(new_sha256='0' * 64))
        mutate(lambda x: x['deviations'][0].update(baseline_sha256='1' * 64))
        mutate(lambda x: x['deviations'].insert(0, dict(x['deviations'][0], id='PD-FORGED')))
        with tempfile.TemporaryDirectory() as d:
            p = copy_p2(d); (p / 'protocol-deviations.json').unlink()
            with self.assertRaises(ValueError): guard.check(p, historical=False)

    def test_study_split_leak_still_fails(self):  # N5
        with tempfile.TemporaryDirectory() as d:
            p = copy_p2(d); split = json.loads((p / 'split-manifest.json').read_text())
            split['roles']['LOCKED_EXTERNAL'].append(split['roles']['TRAIN'][0])
            (p / 'split-manifest.json').write_text(json.dumps(split))
            with self.assertRaises(ValueError): guard.check(p, historical=False)


class HistoricalDomainTest(unittest.TestCase):
    def test_all_164_baseline_hashes_reproduce_from_git(self):
        self.assertEqual(guard.historical_baseline(), 164)

    def test_missing_historical_object_fails_closed(self):  # N6
        with self.assertRaises(guard.HistoricalObjectMissing): guard.historical_baseline(start_sha='f' * 40)
        with tempfile.TemporaryDirectory() as d:
            p = copy_p2(d)
            with self.assertRaises(guard.HistoricalObjectMissing):
                guard.replay_research(Path(d) / 'out', directory=p, commit='e' * 40)

    def test_wrong_historical_commit_fails(self):  # N7
        with self.assertRaises(ValueError): guard.historical_baseline(start_sha=F1_COMMIT)

    def test_current_source_cannot_stand_in_for_history(self):  # N8
        head = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError): guard.replay_research(Path(d) / 'out', commit=head)


class HistoricalReplayTest(unittest.TestCase):
    """Runs the recorded research once in an isolated worktree; tampered expectations must then fail."""
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(); cls.out = Path(cls.tmp.name) / 'replay'
        guard.replay_research(cls.out)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_replay_matches_all_recorded_results_byte_for_byte(self):  # positive
        self.assertEqual(guard.compare_results(self.out, RESULTS, FILES), 5)

    def _tampered(self, name, change):
        with tempfile.TemporaryDirectory() as d:
            for f in FILES: shutil.copy(RESULTS / f, Path(d) / f)
            path = Path(d) / name; path.write_bytes(change(path.read_bytes()))
            with self.assertRaises(ValueError): guard.compare_results(self.out, d, FILES)

    def test_tampered_result_summary_fails(self):  # N3
        def change(raw):
            data = json.loads(raw); data['profiles_count'] = data['profiles_count'] + 1
            return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode()
        self._tampered('analysis.json', change)

    def test_extra_newline_in_result_table_fails(self):  # N4
        self._tampered('study-comparisons.csv', lambda raw: raw + b'\n')


class CurrentScienceTest(unittest.TestCase):
    def test_current_tree_reproduces_the_recorded_science(self):
        """Only provenance fields may differ when the current tree reruns the frozen research."""
        with tempfile.TemporaryDirectory() as d:
            subprocess.run([sys.executable, str(ROOT / 'tools/pk-research/research.py'), '--output', d], cwd=ROOT, capture_output=True, check=True)
            new = json.loads((Path(d) / 'analysis.json').read_text()); old = json.loads((RESULTS / 'analysis.json').read_text())
            provenance = {'code_revision', 'code_sha256'}
            self.assertEqual({k: v for k, v in new.items() if k not in provenance}, {k: v for k, v in old.items() if k not in provenance})
            for f in ['candidate-parameters.json', 'study-comparisons.csv']:
                self.assertEqual((Path(d) / f).read_bytes(), (RESULTS / f).read_bytes(), f)


if __name__ == '__main__':
    unittest.main()
