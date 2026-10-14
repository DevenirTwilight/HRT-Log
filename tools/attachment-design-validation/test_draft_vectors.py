"""Stdlib independent framing/HKDF checks. AEAD itself is verified by JCA tests."""
import hashlib,hmac,json,struct,unittest
from pathlib import Path
DATA=json.loads((Path(__file__).with_name('draft_vectors.json')).read_text())
LABEL=b'HRT-MED-DRAFT0\x00'
def hkdf(ikm,salt,info):return hmac.digest(hmac.digest(salt,ikm,'sha256'),info+b'\x01','sha256')
class DraftVectors(unittest.TestCase):
    def test_profile_and_canonical_header(self):
        self.assertEqual([d['size'] for d in DATA['drafts']],[0,1,65536,65537])
        self.assertEqual(len({d['keys']['footer'] for d in DATA['drafts']}),4)
        for d in DATA['drafts']:
            h=bytes.fromhex(d['header']);self.assertEqual(len(h),140)
            self.assertEqual(h[:16],b'DRAFT-ONLY-OBJ0!');self.assertEqual(struct.unpack('>HHI',h[16:24]),(0,1,65536))
            digest=hashlib.sha256(h).digest();self.assertEqual(digest.hex(),d['header_hash'])
            self.assertEqual(d['salt'],h[104:136].hex())
            for i,role in enumerate(('header','body','footer'),1):self.assertEqual(hkdf(bytes.fromhex(d['dek']),bytes.fromhex(d['salt']),LABEL+bytes([i])+digest).hex(),d['keys'][role])
    def test_count_length_order_nonce_and_final_hash(self):
        for d in DATA['drafts']:
            plain=b''
            for i,f in enumerate(d['frames']):
                self.assertEqual(f['index'],i);self.assertEqual(bytes.fromhex(f['nonce']),bytes(8)+struct.pack('>I',i))
                p=bytes.fromhex(f['plaintext']);self.assertEqual(len(p),f['plain_bytes']);self.assertLessEqual(len(p),65536)
                self.assertEqual(len(bytes.fromhex(f['sealed'])),len(p)+16)
                self.assertEqual(bytes.fromhex(f['aad']),LABEL+b'\x02'+bytes.fromhex(d['header_hash'])+bytes.fromhex(d['header_tag'])+struct.pack('>II',i,len(p)))
                plain+=p
            self.assertEqual(len(plain),d['size'])
            self.assertEqual(bytes.fromhex(d['footer_plain']),struct.pack('>QI',len(plain),len(d['frames']))+hashlib.sha256(plain).digest())
    def test_public_vector_selection_and_budget(self):
        self.assertEqual([t['tcId'] for t in DATA['wycheproof']],[91,92,93,130,131,132])
        self.assertEqual(DATA['source']['sha256'],'985e5ecc172e181eaf49e89508b9470dcf478002eb7e8559c707eb42dc97dfe7')
        # Conservative GHASH block budget includes payload, AAD and per-invocation length block.
        self.assertLessEqual(256*(4096+64+1),2**21)
if __name__=='__main__':unittest.main()
