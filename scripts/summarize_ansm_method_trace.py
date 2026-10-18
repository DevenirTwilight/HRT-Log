#!/usr/bin/env python3
"""Read bounded synthetic ART sampling traces. Approximate software timings, never clinical evidence."""
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import struct

MAX_TRACE_BYTES = 32 * 1024 * 1024


def summarize(data):
    if not 18 <= len(data) <= MAX_TRACE_BYTES:
        raise ValueError('Trace size outside diagnostic bounds')
    end = data.find(b'*end\n', 0, min(len(data), 4 * 1024 * 1024))
    if end < 0:
        raise ValueError('Missing bounded trace metadata')
    header = data[:end].decode('utf-8')
    binary = data[end + len(b'*end\n'):]
    if len(binary) < 18 or binary[:4] != b'SLOW':
        raise ValueError('Missing ART binary header')
    version, offset = struct.unpack_from('<HH', binary, 4)
    if version != 3 or not 18 <= offset <= len(binary):
        raise ValueError('Unsupported trace version or offset')
    size = struct.unpack_from('<H', binary, 16)[0]
    fields, methods, threads = {}, {}, {}
    section = None
    for line in header.splitlines():
        if line.startswith('*'):
            section = line
        elif section == '*version' and '=' in line:
            key, value = line.split('=', 1)
            fields[key] = value
        elif section == '*threads' and '\t' in line:
            key, name = line.split('\t', 1)
            threads[int(key)] = name
        elif section == '*methods' and '\t' in line:
            tokens = line.split('\t')
            if len(tokens) < 4:
                raise ValueError('Malformed method metadata')
            key = int(tokens[0], 16)
            name = tokens[1] + '.' + tokens[2] + tokens[3]
            if key in methods and methods[key] != name:
                raise ValueError('Ambiguous method identity')
            methods[key] = name
    clock = fields.get('clock')
    expected = 14 if clock == 'dual' else 10 if clock in ('thread-cpu', 'wall') else None
    if expected is None or size != expected or (len(binary) - offset) % size:
        raise ValueError('Unsupported clock/record size or truncated records')
    stacks, last, first = defaultdict(list), {}, {}
    totals = defaultdict(lambda: {'inclusive_sampled_us': 0, 'exclusive_sampled_us': 0, 'sampled_segments': 0})
    unknown = unmatched = clipped = 0
    wall_first = wall_last = None

    def close(thread, timestamp):
        method, start, children = stacks[thread].pop()
        elapsed = timestamp - start
        if elapsed < children or elapsed < 0:
            raise ValueError('Inconsistent sampled frame clocks')
        stat = totals[method]
        stat['inclusive_sampled_us'] += elapsed
        stat['exclusive_sampled_us'] += elapsed - children
        stat['sampled_segments'] += 1
        if stacks[thread]:
            stacks[thread][-1][2] += elapsed

    records = 0
    for pos in range(offset, len(binary), size):
        thread, encoded, timestamp = struct.unpack_from('<HII', binary, pos)
        wall = struct.unpack_from('<I', binary, pos + 10)[0] if clock == 'dual' else timestamp if clock == 'wall' else None
        if wall is not None:
            wall_first = wall if wall_first is None else min(wall_first, wall)
            wall_last = wall if wall_last is None else max(wall_last, wall)
        if timestamp < last.get(thread, timestamp):
            raise ValueError('Clock wrap/regression is not inferred')
        last[thread] = timestamp
        first.setdefault(thread, timestamp)
        method, action = encoded & ~3, encoded & 3
        if action == 3:
            raise ValueError('Unsupported method action')
        records += 1
        if method not in methods:
            unknown += 1
        if action == 0:
            stacks[thread].append([method, timestamp, 0])
        else:
            if not any(frame[0] == method for frame in stacks[thread]):
                unmatched += 1
                continue
            while stacks[thread][-1][0] != method:
                unmatched += 1
                close(thread, timestamp)
            close(thread, timestamp)
    for thread in stacks:
        while stacks[thread]:
            clipped += 1
            close(thread, last[thread])
    rows = [{'method': methods.get(key, 'UNKNOWN:' + hex(key)), **value} for key, value in totals.items()]
    rows.sort(key=lambda value: (-value['exclusive_sampled_us'], value['method']))
    partial = fields.get('data-file-overflow') != 'false' or bool(unknown or unmatched or clipped)
    return {
        'status': 'PARTIAL_DIAGNOSTIC' if partial else 'DIAGNOSTIC_PROFILE',
        'scope': 'SYNTHETIC_POST_BUDGET_SAMPLING_ONLY',
        'trace_sha256': hashlib.sha256(data).hexdigest(), 'trace_bytes': len(data),
        'clock': clock, 'timings': 'approximate sampled thread CPU microseconds' if clock != 'wall' else 'approximate sampled wall microseconds',
        'sampled_segments_are_not_invocation_counts': True,
        'overflow': fields.get('data-file-overflow', 'UNKNOWN'), 'records': records,
        'unknown_method_records': unknown, 'unmatched_exits_or_unwinds': unmatched, 'clipped_open_frames': clipped,
        'observed_wall_span_us': None if wall_first is None else wall_last - wall_first,
        'threads': [{'id': key, 'name': threads.get(key, 'UNKNOWN'), 'observed_span_us': last[key] - first[key]} for key in sorted(last)],
        'top_exclusive': rows[:60],
        'top_application': sorted((row for row in rows if row['method'].startswith(('net/plainnotes/app/', 'net.plainnotes.app.'))), key=lambda row: (-row['inclusive_sampled_us'], row['method']))[:60],
    }


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('trace', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.trace.stat().st_size > MAX_TRACE_BYTES:
        raise SystemExit('Trace exceeds diagnostic bound')
    args.output.write_text(json.dumps(summarize(args.trace.read_bytes()), indent=2) + '\n')
