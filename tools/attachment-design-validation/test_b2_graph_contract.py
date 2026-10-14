import unittest
from b2_graph_contract import Graph,Version,Link
class B2GraphTest(unittest.TestCase):
    def graph(self,n):
        records={f'r{i}':('ACTIVE','epoch') for i in range(n)}
        docs={f'd{i}' for i in range(n)}
        versions=[Version('crypto',f'v{i}',(f'plain{i}',f'cipher{i}',i,32)) for i in range(n)]
        return Graph(records,docs,versions,[Link(f'r{i}',f'd{i}') for i in range(n)],'epoch')
    def test_twenty_hundred_thousand_metadata_all_graph_nodes_checked(self):
        for n in (20,100,1000):
            with self.subTest(n=n):
                g=self.graph(n);self.assertTrue(g.validate())
                g.links[-1]=Link('missing',f'd{n-1}')
                with self.assertRaises(ValueError):g.validate()
    def test_same_crypto_uuid_different_commitment_rejected(self):
        g=self.graph(20)
        with self.assertRaises(ValueError):g.validate([Version('crypto','v0',('changed','cipher0',0,32))])
        self.assertTrue(g.validate(g.versions))
    def test_old_backup_fresh_epoch_and_quarantine_never_guess_integer_id(self):
        g=self.graph(20);epoch,links=g.old_record_restore()
        self.assertNotEqual('epoch',epoch);self.assertTrue(all(x.state=='QUARANTINED' for x in links))
        g.links=links;g.epoch=epoch;self.assertTrue(g.validate())
        g.links[0]=Link('r0','d0')
        with self.assertRaises(ValueError):g.validate()
    def test_trash_and_purge_only_historical_quarantine_links(self):
        for state in ('TRASHED','PURGED'):
            g=self.graph(1);g.records['r0']=(state,'epoch')
            with self.assertRaises(ValueError):g.validate()
            g.links=[Link('r0','d0','QUARANTINED')];self.assertTrue(g.validate())
    def test_missing_document_or_unknown_state_rejected(self):
        g=self.graph(1);g.documents.clear()
        with self.assertRaises(ValueError):g.validate()
        g=self.graph(1);g.links=[Link('r0','d0','GUESS')]
        with self.assertRaises(ValueError):g.validate()
