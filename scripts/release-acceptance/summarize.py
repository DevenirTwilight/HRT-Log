#!/usr/bin/env python3
"""Condense downloaded P1 acceptance artifacts into one machine-readable summary (no APKs, no logs copied).

Usage: summarize.py --artifacts DIR --run-id N --source-sha SHA --out summary.json
DIR holds p1-acceptance-{A,B,C,D}/ and p1-arm64-probe-*/ exactly as downloaded from the workflow run.
"""
import argparse
import json
import re
from pathlib import Path


def phases(result):
    out = []
    for p in result.get('phases', []):
        item = {k: p[k] for k in ('name', 'status', 'exit_code', 'counts', 'runner_ok', 'reason', 'expectation', 'notification_after_reboot') if k in p}
        if p.get('tests'):
            item['not_passed'] = [f"{t['class']}#{t['test']}: {t['result']}" for t in p['tests'] if t['result'] not in ('PASS',)]
        if p.get('logcat_crash_lines'): item['logcat_crash_lines'] = p['logcat_crash_lines'][:10]
        if p.get('attempts'): item['attempts'] = [{'exit_code': a['exit_code'], 'pid': bool(a['pid']), 'status_ok': 'Status: ok' in a['am_start']} for a in p['attempts']]
        out.append(item)
    return out


def main():
    a = argparse.ArgumentParser()
    a.add_argument('--artifacts', required=True); a.add_argument('--run-id', required=True)
    a.add_argument('--source-sha', required=True); a.add_argument('--out', required=True)
    args = a.parse_args()
    root = Path(args.artifacts)
    summary = {'workflow': '.github/workflows/release-acceptance.yml', 'run_id': int(args.run_id), 'source_sha': args.source_sha, 'scenarios': {}, 'arm64_probes': {}}
    for s in 'ABCD':
        d = root / f'p1-acceptance-{s}'
        if not d.exists(): summary['scenarios'][s] = {'status': 'NOT_RUN', 'reason': 'artifact missing'}; continue
        entry = {'built_from': (d / 'source-sha.txt').read_text().strip() if (d / 'source-sha.txt').exists() else None}
        ident = d / 'apk-identity.txt'
        if ident.exists():
            entry['apks'] = {m.group(2): m.group(1) for m in re.finditer(r'^([0-9a-f]{64})\s+(\S+)$', ident.read_text(), re.M)}
            entry['apk_bytes'] = {m.group(2): int(m.group(1)) for m in re.finditer(r'\s(\d+)\s+\w+\s+\d+\s+[\d:]+\s+(\S+\.apk)$', ident.read_text(), re.M)}
        for mode in ('exact', 'functional'):
            log = d / f'build-{mode}.log'
            if log.exists():
                m = re.search(r'HRT_ACCEPTANCE_CONDITIONS (.*)', log.read_text())
                entry[f'conditions_{mode}'] = m.group(1) if m else None
        res = d / 'device-x86_64-api35' / 'result.json'
        if res.exists():
            r = json.loads(res.read_text())
            entry.update({'device': r.get('device'), 'abi_note': r.get('abi_note'), 'status': r.get('status', 'PARTIAL'), 'phases': phases(r)})
            ev = d / 'device-x86_64-api35'
            for f in sorted(ev.glob('evidence-*/p1-acceptance/*.json')):
                data = json.loads(f.read_text())
                if f.stem == 'strings':
                    data = {tag: {k: v for k, v in val.items() if k != 'resolved'} for tag, val in data.items()}
                entry.setdefault('evidence', {})[f'{f.parent.parent.name.removeprefix("evidence-")}/{f.stem}'] = data
        else: entry['status'] = 'NOT_RUN'
        summary['scenarios'][s] = entry
    for d in sorted(root.glob('p1-arm64-probe-*')):
        texts = [p.read_text() for p in d.glob('*.txt')]
        summary['arm64_probes'][d.name.removeprefix('p1-arm64-probe-')] = texts[0][-6000:] if texts else 'no probe output'
    Path(args.out).write_text(json.dumps(summary, indent=1, ensure_ascii=False))


if __name__ == '__main__':
    main()
