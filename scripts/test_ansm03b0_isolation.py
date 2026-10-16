import tempfile
import unittest
import zipfile
from pathlib import Path
import check_ansm03b0_isolation as guard


class Ansm03b0IsolationTest(unittest.TestCase):
    def archive(self, name, data, apk=False):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'synthetic.zip'
            with zipfile.ZipFile(p, 'w') as z:
                z.writestr(name, data)
                if apk:
                    z.writestr('AndroidManifest.xml', b'synthetic')
            return guard.check_archive(p, apk)

    def test_frozen_parent_and_source_sets(self):
        guard.check_sources()

    def test_clean_payload_is_checked(self):
        self.assertEqual(1, self.archive('classes.dex', b'ordinary code', True))
        self.assertEqual(1, self.archive('net/plainnotes/Normal.class', b'ordinary code'))

    def test_polluted_class_names_and_obfuscated_payloads_are_rejected(self):
        for marker in guard.MARKERS:
            for apk in (True, False):
                for encoding in ('utf-8', 'utf-16-le'):
                    with self.assertRaises(AssertionError):
                        self.archive('classes.dex' if apk else 'a.class', marker.decode().encode(encoding), apk)
        with self.assertRaises(AssertionError):
            self.archive('net/plainnotes/app/domain/shadow/A.class', b'ordinary code')

    def test_missing_executable_payload_is_not_a_pass(self):
        with self.assertRaises(AssertionError):
            self.archive('readme.txt', b'no classes')
