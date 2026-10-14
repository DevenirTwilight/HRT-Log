"""Model-check failure boundaries. No actual power loss, full archive or new-device acceptance."""
import unittest
from dataclasses import replace
from backup_state_model import Package, Store, Rejected, backup_success

P=Package('archive-1','manifest-1',('o1','o2'),(('r1','o1'),('r2','o2')),('r1','r2'))
class BackupStateModel(unittest.TestCase):
    def test_all_precommit_crash_boundaries_preserve_original_and_reconcile(self):
        for seam in ('reserved','writing','authenticated','file_synced','published_before_dirsync','directory_synced'):
            with self.subTest(seam=seam):
                s=Store()
                with self.assertRaises(Rejected): s.restore(P,'op',1,fail=seam)
                self.assertEqual((s.active,s.records,s.links,s.revision),('old',('old-record',),(('old-record','old-object'),),1))
                s.reconcile(); self.assertEqual(s.durable_files,{'old':('old-object',)})
    def test_commit_ack_loss_restart_and_retry_does_not_repeat(self):
        s=Store()
        with self.assertRaises(Rejected): s.restore(P,'op',1,fail='committed_ack_lost')
        s.reconcile(); self.assertEqual(s.restore(P,'op',1),'new-op'); self.assertEqual(s.revision,2)
    def test_duplicate_restore_after_user_edit_is_not_blind_success(self):
        s=Store();s.restore(P,'op',1);s.revision+=1
        with self.assertRaises(Rejected):s.restore(P,'op',1)
    def test_manifest_auth_eof_missing_duplicate_and_space_fail_before_mutation(self):
        bad=[replace(P,authenticated=False),replace(P,final_manifest=False),replace(P,exact_eof=False),replace(P,objects=('o1','o1')),replace(P,records=('r1','r1')),replace(P,links=(('r1','absent'),)),replace(P,links=(('r1','o1'),('r1','o1'))),replace(P,bytes_needed=999)]
        for p in bad:
            with self.subTest(p=p):
                s=Store()
                with self.assertRaises(Rejected):s.restore(p,'op',1,free=998)
                self.assertEqual(s.journal,{});self.assertEqual(s.active,'old')
    def test_stale_cas_and_operation_collision(self):
        s=Store()
        with self.assertRaises(Rejected):s.restore(P,'op',0)
        s.restore(P,'op',1)
        with self.assertRaises(Rejected):s.restore(replace(P,digest='other'),'op',2)
    def test_gc_honors_read_and_backup_lease(self):
        s=Store();s.leases.add('old');s.restore(P,'op',1)
        with self.assertRaises(Rejected):s.collect('old')
        with self.assertRaises(Rejected):s.collect(s.active)
        s.leases.clear();s.collect('old');self.assertNotIn('old',s.durable_files)
    def test_committed_missing_file_blocks_not_recreates(self):
        s=Store();s.restore(P,'op',1);del s.durable_files[s.active]
        with self.assertRaises(Rejected):s.reconcile()
        self.assertNotIn(s.active,s.durable_files)
    def test_old_backup_rotates_epoch_and_preserves_ciphertext_no_integer_relink(self):
        s=Store();s.old_record_only_restore(('old-record',),'fresh-epoch')
        self.assertEqual(s.links,());self.assertIn('old',s.durable_files);self.assertEqual(s.epoch,'fresh-epoch')
    def test_old_backup_failure_preserves_all_old_state(self):
        s=Store()
        with self.assertRaises(Rejected):s.old_record_only_restore(('new',),'fresh',fail=True)
        self.assertEqual((s.records,s.links,s.epoch),(('old-record',),(('old-record','old-object'),),'old-epoch'))
    def test_backup_final_success_every_condition_required(self):
        good=dict(manifest=True,eof=True,close_ok=True,canceled=False,durable_receipt=True)
        self.assertTrue(backup_success(**good))
        for key in good:
            bad=good|{key:not good[key]};self.assertFalse(backup_success(**bad),key)
if __name__=='__main__':unittest.main()
