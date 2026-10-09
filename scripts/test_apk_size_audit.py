"""Synthetic ZIP checks only; no application or personal data."""
import tempfile
import unittest
import zipfile
from pathlib import Path

from apk_size_audit import inspect, markdown


class AuditTest(unittest.TestCase):
    def test_stored_and_deflated_packages(self):
        with tempfile.TemporaryDirectory() as temp:
            reports = []
            for index, method in enumerate((zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)):
                path = Path(temp) / f'{index}.apk'
                contents = {
                    'classes.dex': b'dex' * 200,
                    'lib/arm64-v8a/libsqlcipher.so': b'sqlcipher' * 100,
                    'lib/x86_64/libother.so': b'other' * 100,
                    'resources.arsc': b'resources' * 50,
                    'res/drawable/icon.xml': b'<icon/>',
                    'assets/dexopt/baseline.prof': b'profile' * 30,
                    'assets/example.json': b'{}',
                }
                with zipfile.ZipFile(path, 'w', compression=method) as archive:
                    archive.writestr('res/', b'')
                    for name, data in contents.items():
                        archive.writestr(name, data)
                report = inspect(path)
                self.assertTrue(report['zip_crc_valid'])
                self.assertEqual(report['abis'], ['arm64-v8a', 'x86_64'])
                self.assertEqual(report['zip_member_count'], len(contents))
                self.assertEqual(report['groups']['profiles']['uncompressed_bytes'], 210)
                self.assertEqual(report['groups']['assets/']['file_count'], 1)
                self.assertEqual(len(report['native_libraries']), 2)
                self.assertEqual(sum(m['is_sqlcipher'] for m in report['native_libraries']), 1)
                self.assertEqual(sum(m['uncompressed_bytes'] for m in report['members']), sum(map(len, contents.values())))
                self.assertEqual(report['sum_member_compressed_bytes'] + report['zip_metadata_alignment_and_signing_overhead_bytes'], path.stat().st_size)
                self.assertEqual(len(report['sha256']), 64)
                reports.append(report)
            self.assertLess(reports[1]['size_bytes'], reports[0]['size_bytes'])
            self.assertIn('libsqlcipher.so', markdown(reports))

    def test_corrupted_crc_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'corrupt.apk'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('classes.dex', b'unique synthetic content')
            path.write_bytes(path.read_bytes().replace(b'unique synthetic content', b'broken synthetic content'))
            with self.assertRaises((ValueError, zipfile.BadZipFile)):
                inspect(path)

    def test_invalid_zip_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'invalid.apk'
            path.write_bytes(b'not a zip')
            with self.assertRaises(zipfile.BadZipFile):
                inspect(path)


if __name__ == '__main__':
    unittest.main()
