import tempfile,unittest,zipfile
from pathlib import Path
from check_attachment_release_isolation import check_apk,FORBIDDEN,RESOURCE_FORBIDDEN
class ReleaseIsolationTest(unittest.TestCase):
    def apk(self,path,dex=b'dex\n035\x00SyntheticAllowed',manifest=b'manifest',second=None):
        with zipfile.ZipFile(path,'w') as z:
            z.writestr('classes.dex',dex);z.writestr('AndroidManifest.xml',manifest)
            if second:z.writestr('classes2.dex',second)
    def test_clean_control(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)/'control.apk';self.apk(p);self.assertEqual(1,check_apk(p))
    def test_injected_markers_in_each_dex_or_manifest_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)/'injected.apk'
            for marker in FORBIDDEN:
                for mode in ('dex','secondary','manifest'):
                    with self.subTest(marker=marker,mode=mode):
                        self.apk(p,dex=marker if mode=='dex' else b'dex',second=marker if mode=='secondary' else None,
                                 manifest=marker.decode().encode('utf-16-le') if mode=='manifest' else b'manifest')
                        with self.assertRaises(AssertionError):check_apk(p)
    def test_demo_resources_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)/'demo.apk'
            for marker in RESOURCE_FORBIDDEN:
                for enc in (marker, marker.decode().encode('utf-16-le')):
                    with self.subTest(marker=marker):
                        self.apk(p)
                        with zipfile.ZipFile(p,'a') as z:z.writestr('resources.arsc',b'arsc'+enc)
                        with self.assertRaises(AssertionError):check_apk(p)
    def test_empty_apk_not_accepted_as_proof(self):
        with tempfile.TemporaryDirectory() as root:
            p=Path(root)/'empty.apk'
            with zipfile.ZipFile(p,'w') as z:z.writestr('AndroidManifest.xml',b'manifest')
            with self.assertRaises(AssertionError):check_apk(p)
    def test_operation_host_is_debug_only_and_public_entry_stays_off(self):
        root=Path(__file__).resolve().parent.parent
        debug=(root/'app/src/debug/AndroidManifest.xml').read_text()
        self.assertIn('AttachmentOperationTestActivity" android:exported="false"',debug)
        for source in ('main','release'):
            for path in (root/f'app/src/{source}').rglob('*.xml'):
                self.assertNotIn('AttachmentOperationTestActivity',path.read_text())
        controller=(root/'app/src/main/java/net/plainnotes/app/attachments/AttachmentFlowController.kt').read_text()
        self.assertIn('PUBLIC_ENTRY_ENABLED=false',controller)
        self.assertFalse((root/'core/data/src/main/java/net/plainnotes/app/data/SyntheticAttachmentDatabase.kt').exists())
        self.assertTrue((root/'core/data/src/debug/java/net/plainnotes/app/data/SyntheticAttachmentDatabase.kt').exists())
