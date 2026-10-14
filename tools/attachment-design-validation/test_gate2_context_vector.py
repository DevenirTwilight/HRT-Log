"""Stdlib regeneration of the TEST_ONLY Gate2 context transcript; not a production protocol."""
import json,re,struct,unittest,uuid
from pathlib import Path
HERE=Path(__file__).resolve().parent
DATA=json.loads((HERE/'gate2-context-vector.json').read_text())
CONTRACT=HERE.parents[1]/'tools'/'attachment-poc'/'src'/'contractTest'/'kotlin'/'net'/'plainnotes'/'poc'/'Gate2ContextContract.kt'
MARKER=b'B2-SECURITY-GATE2-TEST-ONLY\x00'
def encode(ids,protocol,suite,fmt):
    if not 0<=fmt<=0xFFFF:raise ValueError('format is u16')
    return MARKER+struct.pack('>HH',protocol,suite)+b''.join(u.bytes for u in ids)+struct.pack('>H',fmt)
class Gate2ContextVector(unittest.TestCase):
    def setUp(self):self.ids=[uuid.UUID(u) for u in DATA['uuid_order']]
    def test_json_vector_regenerates_from_its_inputs(self):
        self.assertEqual(len(self.ids),5)
        out=encode(self.ids,DATA['protocol'],DATA['suite'],DATA['format'])
        self.assertEqual(out.hex(),DATA['hex']);self.assertEqual(len(out),DATA['bytes']);self.assertEqual(len(out),28+4+80+2)
    def test_kotlin_contract_pins_the_same_bytes(self):
        found=re.findall(r'val expected="([0-9a-f]+)"',CONTRACT.read_text())
        self.assertEqual(found,[DATA['hex']])
    def test_field_order_and_u16_bounds_are_significant(self):
        swapped=[self.ids[1],self.ids[0]]+self.ids[2:]
        self.assertNotEqual(encode(swapped,0,1,0),encode(self.ids,0,1,0))
        for bad in (-1,0x10000):
            with self.assertRaises(ValueError):encode(self.ids,0,1,bad)
if __name__=='__main__':unittest.main()
