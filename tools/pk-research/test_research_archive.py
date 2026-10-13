"""Archive failures must be explicit, including a real shallow checkout."""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import research_archive as archive


class ArchiveIsolationTest(unittest.TestCase):
    def test_archived_record_tampering_fails(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            shutil.copytree(archive.ROOT / 'docs/pk-research', root / 'docs/pk-research')
            shutil.copytree(archive.ROOT / 'docs/design', root / 'docs/design')
            self.assertGreater(archive.verify_records(root), 164)
            target = root / archive.historical_manifest()['results_dir'] / 'analysis.json'
            data = json.loads(target.read_text())
            data['clinical_accuracy_established'] = True
            target.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, 'Archived record changed or missing: .*analysis.json'):
                archive.verify_records(root)

    def test_shallow_clone_missing_objects_fails(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / 'shallow'
            subprocess.run(['git','clone','--depth=1','--no-local',
                            str(archive.ROOT),str(root)],check=True,capture_output=True)
            self.assertEqual(subprocess.check_output(['git','rev-parse','--is-shallow-repository'],cwd=root,text=True).strip(),'true')
            with patch.object(archive, 'ROOT', root):
                with self.assertRaisesRegex(archive.HistoricalObjectMissing, 'Historical git commit .* unavailable'):
                    archive.historical_baseline()

    def test_history_fetch_has_no_display_approval_dependencies(self):
        self.assertEqual(len(archive.required_commits()), 3)
