"""TEST_ONLY PNMEDBK1 manifest candidate vectors. No archive, keys or production adapter.

Independent Python encoder/stdlib inverse; Kotlin uses a separate strict recursive decoder.
Updating these vectors does not freeze or approve the proposed format.
"""
import hashlib
import json
from pathlib import Path
import sys
import uuid

DEST = Path(__file__).resolve().parents[1] / "attachment-poc/src/test/resources/portable-review"
FIELDS = {
    "root": "version dataset epoch logicalVault records documents versions links portableEnvelopes",
    "record": "uuid kind sourceId state trashId evidenceSha256",
    "document": "uuid revision title category state activeVersion deletedUtc",
    "version": "document ordinal context plaintextBytes ciphertextBytes plaintextSha256 ciphertextSha256 mime",
    "context": "logicalVault objectId version attempt dekHandle",
    "link": "uuid record document revision state",
    "envelope": "version wrappedDek",
}


def identity(value):
    parsed = uuid.UUID(value)
    assert str(parsed) == value
    return parsed.bytes  # unsigned network bytes, independent of Java signed UUID.compareTo


def ordered(value, kind):
    names = FIELDS[kind].split()
    assert set(value) == set(names)
    return {name: value[name] for name in names}


def text(value):
    value.encode("utf-8", errors="strict")
    return '"' + "".join(
        '\\"' if c == '"' else '\\\\' if c == "\\" else f"\\u{ord(c):04x}" if ord(c) < 32 else c
        for c in value
    ) + '"'


def scalar(value):
    if value is None:
        return "null"
    if type(value) is int:
        assert -(2**63) <= value < 2**63
        return str(value)
    if type(value) is str:
        return text(value)
    if type(value) is list:
        return "[" + ",".join(map(scalar, value)) + "]"
    if type(value) is dict:
        return "{" + ",".join(text(k) + ":" + scalar(v) for k, v in value.items()) + "}"
    raise ValueError("No float/boolean/implicit coercion")


def encode(source):
    root = ordered(source, "root")
    for name, kind in [("records", "record"), ("documents", "document"), ("links", "link")]:
        root[name] = [ordered(v, kind) for v in sorted(source[name], key=lambda r: identity(r["uuid"]))]
    root["versions"] = []
    for row in sorted(source["versions"], key=lambda r: (identity(r["document"]), r["ordinal"])):
        version = ordered(row, "version")
        version["context"] = ordered(row["context"], "context")
        root["versions"].append(version)
    root["portableEnvelopes"] = [ordered(v, "envelope") for v in sorted(source["portableEnvelopes"], key=lambda r: identity(r["version"]))]
    result = scalar(root).encode("utf-8", errors="strict")
    assert len(result) <= 8 * 1024 * 1024
    return result


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate key")
        result[key] = value
    return result


def no_float(value):
    raise ValueError("Non-integer token: " + value)


def decode(data):
    assert len(data) <= 8 * 1024 * 1024
    result = json.loads(data.decode("utf-8", errors="strict"), object_pairs_hook=unique_pairs,
                        parse_float=no_float, parse_constant=no_float)
    assert encode(result) == data
    return result


def fixture():
    def u(n):
        return str(uuid.UUID(int=n))
    digest = hashlib.sha256(b"synthetic canonical evidence").hexdigest()
    docs = ["7fffffff-0000-0000-0000-000000000001", "80000000-0000-0000-0000-000000000001"]
    versions = []
    for i, size in enumerate([3, 65481, 1]):
        segments = max(1, (size + 40 + 65519) // 65520)
        versions.append(dict(document=docs[0 if i < 2 else 1], ordinal=i + 1 if i < 2 else 1,
                             context=dict(logicalVault=u(90), objectId=u(100 + i), version=u(110 + i), attempt=u(200 + i), dekHandle=u(300 + i)),
                             plaintextBytes=size, ciphertextBytes=size + 40 + 16 * segments,
                             plaintextSha256=digest, ciphertextSha256=digest,
                             mime="application/pdf" if i < 2 else "image/png"))
    root = dict(version=1, dataset=u(80), epoch=u(81), logicalVault=u(90),
                records=[dict(uuid=u(1), kind="APPOINTMENT", sourceId=2**63-1, state="ACTIVE", trashId=None, evidenceSha256=digest),
                         dict(uuid=u(2), kind="DOSE_RECORD", sourceId=1, state="TRASHED", trashId=1, evidenceSha256=digest),
                         dict(uuid=u(3), kind="LAB", sourceId=1, state="TRASHED", trashId=2, evidenceSha256=digest),
                         dict(uuid=u(4), kind="APPOINTMENT", sourceId=2, state="PURGED", trashId=None, evidenceSha256=digest)],
                documents=[dict(uuid=docs[0], revision=2, title='SYNTHÉTIQUE e\u0301 — 🔬 "\\\n', category="REPORT", state="ACTIVE", activeVersion=u(111), deletedUtc=None),
                           dict(uuid=docs[1], revision=2, title="SYNTHETIC trash", category="REPORT", state="TRASHED", activeVersion=u(112), deletedUtc=-(2**63))],
                versions=versions,
                links=[dict(uuid=u(400+i), record=u(r), document=docs[d], revision=1, state=s)
                       for i, (r, d, s) in enumerate([(1, 0, "ACTIVE"), (2, 0, "QUARANTINED"), (3, 1, "QUARANTINED"), (1, 1, "RETRACTED"), (4, 1, "QUARANTINED")])],
                portableEnvelopes=[dict(version=u(110+i), wrappedDek="01"*60) for i in range(3)])
    # Source order deliberately differs from wire order; contexts and arrays are independently sorted.
    for field in ["records", "documents", "versions", "links", "portableEnvelopes"]:
        root[field].reverse()
    return root


def main():
    data = fixture()
    encoded = encode(data)
    assert decode(encoded) == decode(encode(decode(encoded)))
    files = {"source.json": json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8") + b"\n",
             "canonical.bin": encoded,
             "canonical.sha256": hashlib.sha256(encoded).hexdigest().encode("ascii") + b"\n"}
    if sys.argv[1:] == ["--write"]:
        DEST.mkdir(parents=True, exist_ok=True)
        for name, contents in files.items():
            (DEST/name).write_bytes(contents)
    elif sys.argv[1:] == ["--check"]:
        for name, contents in files.items():
            assert (DEST/name).read_bytes() == contents, name
    else:
        raise SystemExit("Use --write or --check (TEST_ONLY)")
    print(f"TEST_ONLY independent Python canonical vector PASS: {len(encoded)}B; NOT_APPROVED")


if __name__ == "__main__":
    main()
