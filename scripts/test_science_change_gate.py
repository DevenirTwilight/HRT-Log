import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import science_change_gate
from science_change_gate import changed_paths, needs_deep

GATE = Path(__file__).with_name('science_change_gate.py')


class TriggerMatrixTest(unittest.TestCase):
    def test_trigger_matrix(self):
        scenarios = {
            'gate_script': (['scripts/science_change_gate.py'], True),
            'gate_test': (['scripts/test_science_change_gate.py'], True),
            'science_workflow': (['.github/workflows/science-validation.yml'], True),
            'android_workflow': (['.github/workflows/android.yml'], True),
            'pk_core': (['pk-engine/src/main/kotlin/net/plainnotes/app/pk/Engine.kt'], True),
            'model_parameters': (['pk-engine/src/main/kotlin/net/plainnotes/app/pk/PkParams.kt'], True),
            'research_data': (['docs/pk-research/p2/evidence-catalog.json'], True),
            'reference_file': (['docs/design/pr19-calibration-evidence/independent-reference.json'], True),
            'new_pk_engine_source': (['pk-engine/src/main/kotlin/net/plainnotes/app/pk/experimental/NewHelper.kt'], True),
            'new_app_conc_source': (['app/src/main/java/net/plainnotes/app/conc/NewCalculation.kt'], True),
            'science_reference_script': (['scripts/check_unified_concentration.py'], True),
            'research_tool': (['tools/pk-research/new_model.py'], True),
            'ui_spacing': (['app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt'], False),
            'translation': (['app/src/main/res/values-zh-rCN/strings.xml',
                             'app/src/main/res/values/strings_unified_concentration.xml'], False),
            'product_docs': (['docs/HANDOFF.md', 'docs/STATUS.md', 'README.md'], False),
            'product_script': (['scripts/delivery_ledger.py'], False),
            'nothing_changed': ([], False),
            'mixed_ui_and_pk': (['app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt',
                                 'pk-engine/src/main/kotlin/net/plainnotes/app/pk/Engine.kt'], True),
        }
        for name, (files, expected) in scenarios.items():
            with self.subTest(name=name):
                self.assertEqual(needs_deep(files), expected)

    def test_prefixes_are_not_matched_loosely(self):
        self.assertFalse(needs_deep(['pk-engine-notes.md', 'docs/pk-research-summary.md']))


class GitRangeTest(unittest.TestCase):
    """Real repositories: push and pull_request ranges, new branches and missing objects."""

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, text=True).strip()

    def commit(self, path, text='x\n'):
        file = Path(self.repo, path)
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)
        self.git('add', path)
        self.git('commit', '-q', '-m', path)
        return self.git('rev-parse', 'HEAD')

    def setUp(self):
        self.repo = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.repo, True)
        self.git('init', '-q', '-b', 'main')
        self.git('config', 'user.email', 'gate@example.invalid')
        self.git('config', 'user.name', 'gate test')
        self.base = self.commit('README.md')
        cwd = os.getcwd()
        os.chdir(self.repo)
        self.addCleanup(os.chdir, cwd)

    def test_push_reports_every_commit_since_before(self):
        self.commit('pk-engine/src/main/kotlin/A.kt')
        self.commit('app/src/main/java/net/plainnotes/app/ui/Screen.kt')
        paths = changed_paths({'GITHUB_EVENT_NAME': 'push', 'GITHUB_EVENT_BEFORE': self.base})
        self.assertEqual(sorted(paths), ['app/src/main/java/net/plainnotes/app/ui/Screen.kt',
                                         'pk-engine/src/main/kotlin/A.kt'])
        self.assertTrue(needs_deep(paths))

    def test_push_of_ui_only_stays_routine(self):
        self.commit('app/src/main/java/net/plainnotes/app/ui/Screen.kt')
        paths = changed_paths({'GITHUB_EVENT_NAME': 'push', 'GITHUB_EVENT_BEFORE': self.base})
        self.assertEqual(paths, ['app/src/main/java/net/plainnotes/app/ui/Screen.kt'])
        self.assertFalse(needs_deep(paths))

    def test_pull_request_uses_merge_base_not_newer_base_commits(self):
        self.git('checkout', '-q', '-b', 'feature')
        self.commit('app/src/main/java/net/plainnotes/app/ui/Screen.kt')
        self.git('checkout', '-q', 'main')
        self.commit('pk-engine/src/main/kotlin/OnBaseOnly.kt')
        self.git('update-ref', 'refs/remotes/origin/main', 'main')
        self.git('checkout', '-q', 'feature')
        paths = changed_paths({'GITHUB_EVENT_NAME': 'pull_request', 'GITHUB_BASE_REF': 'main'})
        self.assertEqual(paths, ['app/src/main/java/net/plainnotes/app/ui/Screen.kt'])
        self.assertFalse(needs_deep(paths))

    def test_pull_request_touching_the_gate_runs_deep(self):
        self.git('update-ref', 'refs/remotes/origin/main', 'main')
        self.git('checkout', '-q', '-b', 'feature')
        self.commit('scripts/science_change_gate.py')
        paths = changed_paths({'GITHUB_EVENT_NAME': 'pull_request', 'GITHUB_BASE_REF': 'main'})
        self.assertTrue(needs_deep(paths))

    def test_unresolvable_ranges_validate_the_whole_tree(self):
        self.commit('docs/HANDOFF.md')
        for env in ({'GITHUB_EVENT_NAME': 'workflow_dispatch'},
                    {'GITHUB_EVENT_NAME': 'push', 'GITHUB_EVENT_BEFORE': '0' * 40},
                    {'GITHUB_EVENT_NAME': 'push', 'GITHUB_EVENT_BEFORE': ''},
                    {'GITHUB_EVENT_NAME': 'push', 'GITHUB_EVENT_BEFORE': 'f' * 40},
                    {'GITHUB_EVENT_NAME': 'pull_request', 'GITHUB_BASE_REF': 'missing-base'},
                    {'GITHUB_EVENT_NAME': 'pull_request'},
                    {'GITHUB_EVENT_NAME': 'schedule'},
                    {}):
            with self.subTest(env=env):
                self.assertIsNone(changed_paths(env))

    def run_gate(self, env, *args):
        output = Path(self.repo, 'github-output')
        output.write_text('')
        full = {k: v for k, v in os.environ.items() if not k.startswith('GITHUB_')}
        full.update(env, GITHUB_OUTPUT=str(output))
        subprocess.run([sys.executable, str(GATE), *args], cwd=self.repo, env=full, check=True,
                       stdout=subprocess.DEVNULL)
        return output.read_text()

    def test_command_writes_the_job_output(self):
        self.assertEqual(self.run_gate({'GITHUB_EVENT_NAME': 'workflow_dispatch'}), 'deep=true\n')
        self.commit('docs/HANDOFF.md')
        self.assertEqual(self.run_gate({'GITHUB_EVENT_NAME': 'push', 'GITHUB_EVENT_BEFORE': self.base}), 'deep=false\n')
        self.commit('.github/workflows/science-validation.yml')
        self.assertEqual(self.run_gate({'GITHUB_EVENT_NAME': 'push', 'GITHUB_EVENT_BEFORE': self.base}), 'deep=true\n')

    def test_failing_diff_fails_instead_of_skipping(self):
        original = science_change_gate.subprocess.check_output

        def broken(*args, **kwargs):
            raise subprocess.CalledProcessError(128, args[0])
        science_change_gate.subprocess.check_output = broken
        self.addCleanup(setattr, science_change_gate.subprocess, 'check_output', original)
        with self.assertRaises(subprocess.CalledProcessError):
            changed_paths({'GITHUB_EVENT_NAME': 'push', 'GITHUB_EVENT_BEFORE': self.base})


if __name__ == '__main__':
    unittest.main()
