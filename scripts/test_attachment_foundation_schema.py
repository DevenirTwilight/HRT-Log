"""Production Room export contracts. These complement native SQLCipher migration tests."""
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCHEMAS=ROOT/'core/data/schemas/net.plainnotes.app.data.NotesDatabase'

class AttachmentFoundationSchemaTest(unittest.TestCase):
    def setUp(self):
        self.old=json.loads((SCHEMAS/'9.json').read_text())['database']
        self.new=json.loads((SCHEMAS/'10.json').read_text())['database']
        self.tables={e['tableName']:e for e in self.new['entities']}

    def test_all_29_legacy_entities_columns_foreign_keys_and_indexes_unchanged(self):
        self.assertEqual(29,len(self.old['entities']))
        self.assertEqual(37,len(self.new['entities']))
        for entity in self.old['entities']:
            with self.subTest(table=entity['tableName']):
                self.assertEqual(entity,self.tables[entity['tableName']])

    def test_record_epoch_has_database_level_dataset_foreign_key(self):
        keys=self.tables['attachment_record_identity']['foreignKeys']
        self.assertTrue(any(k['table']=='attachment_store' and k['columns']==['store_uuid','record_epoch']
                            and k['referencedColumns']==['store_uuid','record_epoch'] for k in keys),
                        'A stable identity must not carry another dataset epoch, even while trashed')

    def test_links_and_active_versions_use_composite_scope_foreign_keys(self):
        links=self.tables['attachment_link']['foreignKeys']
        self.assertTrue(any(k['table']=='attachment_record_identity' and
                            k['columns']==['store_uuid','record_epoch','record_uuid'] for k in links))
        self.assertTrue(any(k['table']=='attachment_document' and
                            k['columns']==['store_uuid','document_uuid'] for k in links))
        docs=self.tables['attachment_document']['foreignKeys']
        self.assertTrue(any(k['table']=='attachment_version' and
                            k['columns']==['store_uuid','document_uuid','active_version_uuid'] for k in docs))

    def test_disabled_feature_and_scoped_metadata_primary_keys(self):
        fields=self.tables['attachment_store']['fields']
        flag=next(f for f in fields if f['columnName']=='feature_enabled')
        self.assertEqual('0',flag['defaultValue'])
        self.assertEqual(10,self.new['version'])
        for name,entity in self.tables.items():
            if name.startswith('attachment_'):
                self.assertEqual('store_uuid',entity['primaryKey']['columnNames'][0])

if __name__=='__main__':unittest.main()
