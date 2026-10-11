"""Negative mutation tests of the exact owner-authorized navigation exception."""
import copy
import json
import shutil
import tempfile
import unittest
from pathlib import Path
import check_protocol as guard

class UnifiedProtocolTest(unittest.TestCase):
    def sandbox(self, path):
        p = Path(path); frozen=p/'p2'; production=p/'production'; frozen.mkdir(); production.mkdir()
        for f in guard.P2.glob('*'):
            if f.is_file(): shutil.copyfile(f, frozen/f.name)
        for name in json.loads((frozen/'production-baseline.json').read_text())['files']:
            dest=production/name; dest.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(guard.ROOT/name,dest)
        return frozen,production
    def mutate_deviation(self, change):
        with tempfile.TemporaryDirectory() as directory:
            frozen,production=self.sandbox(directory); file=frozen/'protocol-deviations.json'; data=json.loads(file.read_text()); change(data); file.write_text(json.dumps(data))
            with self.assertRaises(ValueError): guard.check(frozen,production)
    def test_actual_authorization_and_old_protocol_chain_pass(self):
        guard.check()
        self.assertEqual(guard.APPROVED_NAVIGATION,json.loads((guard.ROOT/'docs/design/unified-navigation-authorization.json').read_text()))
    def test_each_navigation_authorization_field_is_exact(self):
        for key in guard.APPROVED_NAVIGATION:
            with self.subTest(key=key):
                self.mutate_deviation(lambda data: data['deviations'][-1].__setitem__(key,False if key=='pk_relevant' else 'forged'))
    def test_missing_navigation_entry_or_f1_history_fail(self):
        self.mutate_deviation(lambda data:data['deviations'].pop())
        self.mutate_deviation(lambda data:data['deviations'].pop(0))
    def test_forged_non_pk_appshell_waiver_fails(self):
        self.mutate_deviation(lambda data:data['deviations'][-1].__setitem__('pk_relevant',False))
    def test_one_extra_appshell_byte_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            frozen,production=self.sandbox(directory); file=production/guard.APPROVED_NAVIGATION['file'];file.write_bytes(file.read_bytes()+b'\n')
            with self.assertRaises(ValueError):guard.check(frozen,production)
    def test_protected_kernel_chart_and_params_cannot_use_navigation_exception(self):
        baseline=json.loads((guard.P2/'production-baseline.json').read_text())['files']
        protected=[name for name in baseline if any(token in name for token in ('Engine.kt','ConcentrationCalculator.kt','ConcentrationScreen.kt','ConcChart.kt','ChartViewport.kt','pk-params.json','Migrations.kt'))]
        self.assertGreaterEqual(len(protected),6)
        for name in protected:
            for relevant in [False,True]:
                with self.subTest(file=name,relevant=relevant), tempfile.TemporaryDirectory() as directory:
                    frozen,production=self.sandbox(directory); file=production/name; file.write_bytes(file.read_bytes()+b'\n')
                    data=json.loads((frozen/'protocol-deviations.json').read_text()); entry=copy.deepcopy(guard.APPROVED_NAVIGATION)
                    entry.update(file=name,baseline_sha256=baseline[name],new_sha256=guard.digest(file),pk_relevant=relevant)
                    data['deviations'].append(entry); (frozen/'protocol-deviations.json').write_text(json.dumps(data))
                    with self.assertRaises(ValueError):guard.check(frozen,production)
    def test_no_frozen_protocol_or_baseline_was_rewritten(self):
        lock=json.loads((guard.P2/'protocol-lock.json').read_text())
        for name,expected in lock['files'].items():self.assertEqual(expected,guard.digest(guard.P2/name))
        baseline=json.loads((guard.P2/'production-baseline.json').read_text())['files']
        for name,expected in baseline.items():
            if name != guard.APPROVED_NAVIGATION['file']:self.assertEqual(expected,guard.digest(guard.ROOT/name))

if __name__ == '__main__':unittest.main()
