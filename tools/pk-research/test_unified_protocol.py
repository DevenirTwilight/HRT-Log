"""Negative mutation tests of PR #19's owner-authorized UI deviations, adapted to the two-domain guard (REQUIREMENTS §69).

Since D-13, AppShell.kt is an ordinary product file (its navigation behaviour is pinned by
UnifiedConcentrationNavigationTest), while ConcentrationScreen.kt and the PR #19 M2 display files need the owner
approval chain in display-approvals.json. The recorded deviation entries stay immutable history. Every negative case
asserts the reason it fails, so a sandbox that is incomplete cannot make a test pass for the wrong reason."""
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
        cls=json.loads((frozen/'guard-classification.json').read_text())
        names=[*json.loads((frozen/'production-baseline.json').read_text())['files'],*cls['live_lock_additional'],*cls['display_review_additional']]
        names+=[str(t.relative_to(guard.ROOT)) for g in cls['dependency_scan']['globs'] for t in guard.ROOT.glob(g)]
        for name in names:
            dest=production/name; dest.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(guard.ROOT/name,dest)
        guard.check(frozen,production,historical=False)  # the unmodified sandbox must pass, or negatives prove nothing
        return frozen,production
    def fails(self, frozen, production, reason):
        with self.assertRaises(ValueError) as e: guard.check(frozen,production,historical=False)
        self.assertIn(reason,str(e.exception))
    def mutate_deviation(self, change, reason='deviation'):
        with tempfile.TemporaryDirectory() as directory:
            frozen,production=self.sandbox(directory); file=frozen/'protocol-deviations.json'; data=json.loads(file.read_text()); change(data); file.write_text(json.dumps(data))
            with self.assertRaises(ValueError) as e: guard.check(frozen,production,historical=False)
            self.assertIn(reason,str(e.exception).lower())
    def test_actual_authorization_and_old_protocol_chain_pass(self):
        guard.check()
        self.assertEqual(guard.APPROVED_NAVIGATION,json.loads((guard.ROOT/'docs/design/unified-navigation-authorization.json').read_text()))
    def test_each_navigation_authorization_field_is_exact(self):
        for key in guard.APPROVED_NAVIGATION:
            with self.subTest(key=key):
                self.mutate_deviation(lambda data: next(e for e in data['deviations'] if e['id']==guard.APPROVED_NAVIGATION['id']).__setitem__(key,False if key=='pk_relevant' else 'forged'))
    def test_missing_navigation_entry_or_f1_history_fail(self):
        self.mutate_deviation(lambda data:data['deviations'].pop())
        self.mutate_deviation(lambda data:data['deviations'].pop(0))
    def test_forged_non_pk_appshell_waiver_fails(self):
        self.mutate_deviation(lambda data:next(e for e in data['deviations'] if e['id']==guard.APPROVED_NAVIGATION['id']).__setitem__('pk_relevant',False))
    def test_new_replay_records_real_deviation_chain_without_rewriting_freeze(self):
        import research
        import contextlib
        import io
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            output=Path(directory)/'result';research.run(guard.P2,output)
            result=json.loads((output/'analysis.json').read_text())
            self.assertEqual(json.loads((guard.P2/'protocol-deviations.json').read_text())['deviations'],result['protocol_deviations'])
            self.assertEqual(guard.digest(guard.P2/'protocol-deviations.json'),result['protocol_deviations_sha256'])
            self.assertEqual([],json.loads((guard.P2/'protocol-lock.json').read_text())['protocol_deviations'])
            self.assertFalse(result['clinical_accuracy_established'])
            self.assertFalse(result['model_replacement_approved'])
    def test_appshell_is_ordinary_and_its_navigation_is_pinned_by_behaviour(self):
        # Was test_one_extra_appshell_byte_fails. D-13 (REQUIREMENTS §67/§69) made AppShell.kt ordinary product code;
        # the single concentration destination without model selectors is asserted by the real-navigation Kotlin test.
        name=guard.APPROVED_NAVIGATION['file']; cls=json.loads((guard.P2/'guard-classification.json').read_text())
        self.assertIn(name,cls['history_only'])
        with tempfile.TemporaryDirectory() as directory:
            frozen,production=self.sandbox(directory); file=production/name;file.write_bytes(file.read_bytes()+b'\n')
            guard.check(frozen,production,historical=False)
        nav=(guard.ROOT/'app/src/test/java/net/plainnotes/app/UnifiedConcentrationNavigationTest.kt').read_text()
        for pinned in ('Destination.CONCENTRATION','contains("EXPERIMENTAL")','model-$choice','assertCountEquals(0)','NotesApp('):self.assertIn(pinned,nav)
    def test_protected_kernel_chart_and_params_cannot_use_navigation_exception(self):
        baseline=json.loads((guard.P2/'production-baseline.json').read_text())['files']
        # Files the two-domain guard actually protects (live lock or display approval). Since D-13, Migrations.kt and
        # ScheduleEngine.kt are ordinary product code; the schema itself is guarded by the CI core/data/schemas diff
        # check and the migration tests, not by this research guard.
        cls=json.loads((guard.P2/'guard-classification.json').read_text()); guarded=set(cls['live_lock_baseline'])|set(cls['display_review'])
        protected=[name for name in baseline if name in guarded and name.rsplit('/',1)[-1] in ('Engine.kt','ConcentrationCalculator.kt','ConcentrationScreen.kt','ConcChart.kt','ChartViewport.kt','pk-params.json')]
        self.assertEqual(len(protected),6)
        for name in protected:
            for relevant in [False,True]:
                with self.subTest(file=name,relevant=relevant), tempfile.TemporaryDirectory() as directory:
                    frozen,production=self.sandbox(directory); file=production/name; file.write_bytes(file.read_bytes()+b'\n')
                    data=json.loads((frozen/'protocol-deviations.json').read_text()); entry=copy.deepcopy(guard.APPROVED_NAVIGATION)
                    entry.update(file=name,baseline_sha256=baseline[name],new_sha256=guard.digest(file),pk_relevant=relevant)
                    data['deviations'].append(entry); (frozen/'protocol-deviations.json').write_text(json.dumps(data))
                    # A deviation record never unlocks a locked or display file; the file check itself rejects it.
                    with self.assertRaises(ValueError) as e:guard.check(frozen,production,historical=False)
                    self.assertTrue(any(r in str(e.exception) for r in ('Production or old evidence changed','last approved state','without owner approval')),str(e.exception))
    def test_no_frozen_protocol_or_baseline_was_rewritten(self):
        # The frozen protocol, its inputs and the baseline record are byte-identical; the 164 baseline hashes are still
        # reproduced from git (historical domain). Instead of "every product file unchanged" (pre-D-13), each live-locked
        # file equals its frozen hash and each display file is in its last owner-approved state.
        lock=json.loads((guard.P2/'protocol-lock.json').read_text())
        for name,expected in lock['files'].items():self.assertEqual(expected,guard.digest(guard.P2/name))
        self.assertEqual(guard.historical_baseline(),164)
        baseline=json.loads((guard.P2/'production-baseline.json').read_text())['files']
        cls=json.loads((guard.P2/'guard-classification.json').read_text())
        for name in cls['live_lock_baseline']:self.assertEqual(baseline[name],guard.digest(guard.ROOT/name),name)
        for name,expected in cls['live_lock_additional'].items():self.assertEqual(expected,guard.digest(guard.ROOT/name),name)
        approvals=json.loads((guard.P2/'display-approvals.json').read_text())['approvals']
        for name in cls['display_review']:
            chain=[a for a in approvals if a['file']==name]
            self.assertEqual(chain[-1]['new_sha256'] if chain else baseline[name],guard.digest(guard.ROOT/name),name)

    def test_original_ui_authorization_fields_and_hashes_are_exact(self):
        self.assertEqual(guard.APPROVED_ORIGINAL_UI,json.loads((guard.ROOT/'docs/design/original-concentration-ui-authorization.json').read_text()))
        for entry in guard.APPROVED_ORIGINAL_UI:
            for key in entry:
                with self.subTest(file=entry['file'],key=key):
                    self.mutate_deviation(lambda data:next(e for e in data['deviations'] if e['id']==entry['id']).__setitem__(key,False if key=='pk_relevant' else 'forged'))
            with tempfile.TemporaryDirectory() as directory:
                frozen,production=self.sandbox(directory); file=production/entry['file'];file.write_bytes(file.read_bytes()+b'\n')
                if entry['file'].endswith('AppShell.kt'):guard.check(frozen,production,historical=False)  # ordinary since D-13
                else:self.fails(frozen,production,'last approved state')

    def test_m2_only_authorization_is_exact_and_does_not_waive_extra_bytes(self):
        self.assertEqual(guard.APPROVED_M2_ONLY_UI,json.loads((guard.ROOT/'docs/design/m2-only-ui-authorization.json').read_text()))
        for entry in guard.APPROVED_M2_ONLY_UI:
            for key in entry:
                with self.subTest(file=entry['file'],key=key):
                    self.mutate_deviation(lambda data:next(e for e in data['deviations'] if e['id']==entry['id']).__setitem__(key,False if key=='pk_relevant' else 'forged'))
            with tempfile.TemporaryDirectory() as directory:
                frozen,production=self.sandbox(directory); file=production/entry['file'];file.write_bytes(file.read_bytes()+b'\n')
                if entry['file'].endswith('AppShell.kt'):guard.check(frozen,production,historical=False)  # ordinary since D-13
                else:self.fails(frozen,production,'last approved state')

    def test_m2_only_chain_cannot_be_reordered_or_delete_original_approval(self):
        self.mutate_deviation(lambda data:data['deviations'].reverse())
        self.mutate_deviation(lambda data:data['deviations'].__setitem__(slice(None),[e for e in data['deviations'] if e['id']!=guard.APPROVED_ORIGINAL_UI[-1]['id']]))

class ConcentrationScreenApprovalChainTest(unittest.TestCase):
    """REQUIREMENTS §69 item 1: unapproved change, tampered hash, forged approval and broken chain must all fail."""
    NAME='app/src/main/java/net/plainnotes/app/ui/ConcentrationScreen.kt'
    def run_with(self, change_approvals=None, change_file=None):
        with tempfile.TemporaryDirectory() as directory:
            frozen,production=UnifiedProtocolTest.sandbox(self,directory)
            if change_approvals:
                path=frozen/'display-approvals.json'; data=json.loads(path.read_text()); change_approvals(data['approvals']); path.write_text(json.dumps(data))
            if change_file:change_file(production/self.NAME)
            guard.check(frozen,production,historical=False)
    def rejects(self, reason, **kw):
        with self.assertRaises(ValueError) as e:self.run_with(**kw)
        self.assertIn(reason,str(e.exception))
    def chain(self, approvals):return [a for a in approvals if a['file']==self.NAME]
    def test_migrated_chain_matches_the_real_file_and_git_history(self):
        self.run_with()
        approvals=json.loads((guard.P2/'display-approvals.json').read_text())['approvals']
        # The three migrated PR #19 changes stay the first, unchanged links; later owner-approved links are appended.
        migrated=[e['id'] for e in guard.APPROVED_ORIGINAL_UI[1:]]+[e['id'] for e in guard.APPROVED_M2_ONLY_UI[1:]]
        self.assertEqual([a['id'] for a in self.chain(approvals)][:3],migrated)
        for a in self.chain(approvals)[:3]:
            blob=guard.git_blobs(a['evidence']['commit'],[self.NAME])[self.NAME]
            self.assertEqual(guard.hashlib.sha256(blob).hexdigest(),a['new_sha256'],a['id'])
            self.assertIn('owner_decision',a);self.assertIn('human_review',a)
    def test_unapproved_modification_fails(self):
        self.rejects('last approved state',change_file=lambda f:f.write_bytes(f.read_bytes()+b' '))
    def test_tampered_hash_fails(self):
        self.rejects('last approved state',change_approvals=lambda a:self.chain(a)[-1].update(new_sha256='0'*64))
        self.rejects('Broken approval chain',change_approvals=lambda a:self.chain(a)[1].update(previous_sha256='1'*64))
    def test_forged_approval_fails(self):
        self.rejects('non-owner',change_approvals=lambda a:self.chain(a)[0].update(approver='someone-else'))
        self.rejects('full commit',change_approvals=lambda a:self.chain(a)[0]['evidence'].update(commit='425a7ab'))
        # Evidence commit that exists but does not contain this id (the frozen start commit predates PR #19).
        self.rejects('not verifiable',change_approvals=lambda a:self.chain(a)[0]['evidence'].update(commit='e89579a02da3c92a9e4976baea729af9d532437e'))
        self.rejects('non-owner',change_approvals=lambda a:self.chain(a)[0].pop('scope'))
    def test_forged_extra_link_for_a_new_change_fails(self):
        def add(a):
            last=self.chain(a)[-1]; a.append(dict(last,id='PD-FORGED-NEXT',previous_sha256=last['new_sha256'],new_sha256='2'*64))
        self.rejects('not verifiable',change_approvals=add)
    def test_broken_chain_fails(self):
        self.rejects('Broken approval chain',change_approvals=lambda a:a.remove(self.chain(a)[1]))
        self.rejects('Broken approval chain',change_approvals=lambda a:a.reverse())
        self.rejects('without owner approval',change_approvals=lambda a:a.clear())

if __name__ == '__main__':unittest.main()
