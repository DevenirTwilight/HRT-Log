"""Independent public synthetic v3 fixture. NEVER use these fixed keys/nonces for user data."""
from pathlib import Path
import gzip, struct, hashlib
from argon2.low_level import hash_secret_raw, Type
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
root=Path(__file__).resolve().parents[2]
source=root/'core/data/src/test/resources/featherline/clinical-s7.json'
plain=source.read_bytes()
salt=bytes(range(16));nonce=bytes(range(16,28))
header=b'HRTBKP1'+bytes([3,2,1,1])+struct.pack('>qiiiiBB',len(plain),3,65536,1,32,16,12)+salt+nonce
assert len(header)==65
key=hash_secret_raw(b'synthetic-only-password',salt,3,65536,1,32,Type.ID,version=19)
archive=header+AESGCM(key).encrypt(nonce,gzip.compress(plain,mtime=0),header)
for path in (root/'core/data/src/test/resources/featherline/clinical-v3.cipher', root/'app/src/androidTest/assets/imports/featherline.cipher'):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(archive)
print('Synthetic fixture bytes',len(archive),'sha256',hashlib.sha256(archive).hexdigest())
