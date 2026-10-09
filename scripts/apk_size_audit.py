#!/usr/bin/env python3
"""Offline Android APK size audit. Uses only Python's standard library.

This script reads APK ZIP entry metadata; it does not extract contents,
inspect private records, disassemble code, or upload files.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
import zipfile


def group_member(name: str) -> str:
    if name.endswith(('.prof', '.profm')):
        return 'profiles'
    if name.startswith('lib/'):
        p = name.split('/')
        return 'lib/' + (p[1] if len(p) > 2 else 'other')
    if re.fullmatch(r'classes\d*\.dex', name):
        return 'dex'
    if name == 'resources.arsc':
        return 'resources.arsc'
    if name.startswith('res/'):
        return 'res/'
    if name.startswith('assets/'):
        return 'assets/'
    if name.startswith('META-INF/'):
        return 'META-INF/'
    if name.startswith('kotlin/'):
        return 'kotlin/'
    return 'other'


def inspect(path: Path, top: int = 15) -> dict:
    if not path.is_file():
        raise FileNotFoundError(str(path))
    h = hashlib.sha256()
    with path.open('rb') as stream:
        while (chunk := stream.read(1024 * 1024)):
            h.update(chunk)
    total = path.stat().st_size
    groups: dict[str, dict[str, int]] = defaultdict(lambda: {'compressed_bytes': 0, 'uncompressed_bytes': 0, 'file_count': 0})
    members = []
    with zipfile.ZipFile(path) as z:
        bad = z.testzip()
        if bad:
            raise ValueError(f'ZIP CRC failure in {bad}')
        for f in z.infolist():
            if f.is_dir():
                continue
            group = group_member(f.filename)
            bucket = groups[group]
            bucket['compressed_bytes'] += f.compress_size
            bucket['uncompressed_bytes'] += f.file_size
            bucket['file_count'] += 1
            members.append({'name': f.filename, 'category': group,
                            'compressed_bytes': f.compress_size,
                            'uncompressed_bytes': f.file_size,
                            'crc32': f'{f.CRC:08x}',
                            'compression_method': f.compress_type})
    names = [m['name'] for m in members]
    if len(names) != len(set(names)):
        raise ValueError('Duplicate ZIP member names: comparison would be ambiguous')
    native = [m for m in members if re.fullmatch(r'lib/[^/]+/[^/]+\.so', m['name'])]
    sum_compressed = sum(f['compressed_bytes'] for f in members)
    zip_overhead = total - sum_compressed
    if zip_overhead < 0:
        raise ValueError('Invalid ZIP sizes: compressed member sum > file size')
    return {
        'filename': path.name,
        'size_bytes': total,
        'size_mb': round(total / 1_000_000, 6),
        'size_mib': round(total / (1024 * 1024), 3),
        'sha256': h.hexdigest(),
        'zip_crc_valid': True,
        'abis': sorted({m['name'].split('/')[1] for m in native}),
        'native_libraries': [{**m, 'is_sqlcipher': m['name'].split('/')[-1] == 'libsqlcipher.so'} for m in native],
        'members': members,
        'zip_member_count': len(members),
        'sum_member_compressed_bytes': sum_compressed,
        'zip_metadata_alignment_and_signing_overhead_bytes': zip_overhead,
        'note': 'ZIP overhead includes metadata, alignment, signatures and any extra ZIP bytes; it is NOT all signing overhead.',
        'groups': dict(sorted(groups.items(), key=lambda v: v[1]['compressed_bytes'], reverse=True)),
        'top_compressed_members': sorted(members, key=lambda v: v['compressed_bytes'], reverse=True)[:top],
    }


def fmt_size(n: int) -> str:
    return f'{n:,} B ({n / 1_000_000:.3f} MB)'


def markdown(reports: list[dict]) -> str:
    lines = ['# APK size audit', '', 'All packages are compared using their actual byte size; all comparisons require matching source revision, release mode and build tools.', '']
    if len(reports) > 1:
        baseline = reports[0]['size_bytes']
        lines.extend(['## Summary', '', '| APK | Size (MB decimal) | vs first APK | SHA256 prefix |', '|---|---:|---:|---|'])
        for r in reports:
            delta = r['size_bytes'] - baseline
            pct = (100 * delta / baseline) if baseline else 0
            lines.append(f"| `{r['filename']}` | {r['size_bytes']/1_000_000:.3f} | {delta/1_000_000:+.3f} MB ({pct:+.2f}%) | `{r['sha256'][:12]}` |")
        lines.append('')
    for r in reports:
        lines.extend([f"## {r['filename']}", '', f"- Total: {fmt_size(r['size_bytes'])}", f"- SHA256: `{r['sha256']}`", f"- ZIP file entries: {r['zip_member_count']}", '', '| Package component | Compressed (MB) | Uncompressed (MB) | Entries |', '|---|---:|---:|---:|'])
        for group, d in r['groups'].items():
            lines.append(f"| `{group}` | {d['compressed_bytes']/1_000_000:.3f} | {d['uncompressed_bytes']/1_000_000:.3f} | {d['file_count']} |")
        lines.extend([f"| ZIP metadata, alignment and other overhead | {r['zip_metadata_alignment_and_signing_overhead_bytes']/1_000_000:.3f} | — | — |", '', '**Largest entries by ZIP compressed size**', '', '| Member | Compressed MB | Raw MB |', '|---|---:|---:|'])
        for m in r['top_compressed_members']:
            lines.append(f"| `{m['name']}` | {m['compressed_bytes']/1_000_000:.3f} | {m['uncompressed_bytes']/1_000_000:.3f} |")
        lines.extend(['', '**Native libraries (all entries)**', '', '| Member | Compressed bytes | Raw bytes | SQLCipher |', '|---|---:|---:|---|'])
        for m in r['native_libraries']:
            lines.append(f"| `{m['name']}` | {m['compressed_bytes']} | {m['uncompressed_bytes']} | {m['is_sqlcipher']} |")
        lines.extend(['', 'Caution: ZIP component sizes do not measure Android runtime RAM usage, installed storage expansion, cold-start time or clinical/functional correctness.', ''])
    return '\n'.join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', nargs='+', type=Path, help='APK paths (first APK is comparison baseline)')
    parser.add_argument('--out', type=Path, help='Output prefix (default: apk_size_audit), writes .json and .md')
    parser.add_argument('--top', type=int, default=15, help='Top N ZIP members by compressed bytes')
    args = parser.parse_args()
    if args.top < 1:
        parser.error('--top must be positive')
    reports = [inspect(p, args.top) for p in args.apk]
    stem = args.out or Path('apk_size_audit')
    stem.parent.mkdir(parents=True, exist_ok=True)
    stem.with_suffix('.json').write_text(json.dumps(reports, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    stem.with_suffix('.md').write_text(markdown(reports), encoding='utf-8')
    print(markdown(reports))
    print(f'\nSaved {stem.with_suffix(".json")} and {stem.with_suffix(".md")}')


if __name__ == '__main__':
    main()
