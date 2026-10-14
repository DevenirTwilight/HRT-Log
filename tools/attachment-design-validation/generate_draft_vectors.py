"""Synthetic transcript generator only; NOT a production object serializer.
Local optional dependency: cryptography==50.0.0. CI verifies outputs independently in JCA.
"""
import hashlib, hmac, json, struct, uuid
from pathlib import Path
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = Path(__file__).resolve().parent
LABEL = b'HRT-MED-DRAFT0\x00'  # Fixed 15-byte label, followed by single-byte role and H0.
def hkdf(ikm, salt, info, length=32):
    prk = hmac.digest(salt, ikm, 'sha256')
    previous = b''; result = b''
    for i in range(1, (length + 31)//32 + 1):
        previous = hmac.digest(prk, previous + info + bytes([i]), 'sha256'); result += previous
    return result[:length]

def draft(size):
    dek = bytes(range(32)); salt = bytes(range(32,64))
    identities = b''.join(uuid.UUID(int=i+(size<<8)).bytes for i in range(1,6))
    header = b'DRAFT-ONLY-OBJ0!' + struct.pack('>HHI',0,1,65536) + identities + salt + struct.pack('>HH',1,0)
    assert len(header) == 140
    h0 = hashlib.sha256(header).digest()
    keys = {role: hkdf(dek,salt,LABEL + bytes([index]) + h0) for index,role in enumerate(('header','body','footer'),1)}
    header_aad = LABEL + b'\x01' + header
    header_tag = AESGCM(keys['header']).encrypt(bytes(12),b'',header_aad)
    plain = bytes((i*31+17)%251 for i in range(size))
    frames = []
    for i, start in enumerate(range(0,size,65536)):
        value = plain[start:start+65536]; nonce = bytes(8) + struct.pack('>I',i)
        aad = LABEL+b'\x02'+h0+header_tag+struct.pack('>II',i,len(value))
        frames.append(dict(index=i,plain_bytes=len(value),nonce=nonce.hex(),aad=aad.hex(),plaintext=value.hex(),sealed=AESGCM(keys['body']).encrypt(nonce,value,aad).hex()))
    footer_plain = struct.pack('>QI',size,len(frames))+hashlib.sha256(plain).digest()
    footer_aad=LABEL+b'\x03'+h0+header_tag
    return dict(size=size,dek=dek.hex(),salt=salt.hex(),header=header.hex(),header_hash=h0.hex(),keys={k:v.hex() for k,v in keys.items()},header_aad=header_aad.hex(),header_tag=header_tag.hex(),frames=frames,footer_plain=footer_plain.hex(),footer_aad=footer_aad.hex(),footer_sealed=AESGCM(keys['footer']).encrypt(bytes(12),footer_plain,footer_aad).hex())

def main():
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--wycheproof',type=Path,required=True);p.add_argument('--check',action='store_true');args=p.parse_args()
    source=args.wycheproof.read_bytes(); assert hashlib.sha256(source).hexdigest()=='985e5ecc172e181eaf49e89508b9470dcf478002eb7e8559c707eb42dc97dfe7'
    public=json.loads(source); selected=[]
    for group in public['testGroups']:
        if (group['keySize'],group['ivSize'],group['tagSize'])==(256,96,128):
            selected += [t for t in group['tests'] if t['tcId'] in (91,92,93,130,131,132)]
    data=dict(status='UNAPPROVED_SYNTHETIC_DRAFT_NOT_PRODUCTION',generator='Python cryptography 50.0.0 AESGCM; stdlib HKDF',source=dict(url='https://raw.githubusercontent.com/C2SP/wycheproof/7a325932231bd7a724c791c8b0d65ea4d6d2f435/testvectors_v1/aes_gcm_test.json',sha256=hashlib.sha256(source).hexdigest()),wycheproof=selected,drafts=[draft(n) for n in (0,1,65536,65537)])
    rendered=json.dumps(data,indent=2)+'\n';target=ROOT/'draft_vectors.json'
    if args.check: assert target.read_text()==rendered
    else: target.write_text(rendered)
if __name__=='__main__': main()
