#!/usr/bin/env python3
"""Collect synthetic timing metadata/method stacks, never a database, backup, APK or health record."""
import json
import hashlib
from pathlib import Path
import re
import subprocess
import uuid

def report_paths(lines):
    return [p.strip() for p in lines if re.fullmatch(r'/sdcard/Android/data/net\.plainnotes\.app\.data(?:\.test)?/files/ansm-performance/large-ten-year\.json', p.strip())]


def log_report(text):
    reports = []
    for line in text.splitlines():
        match = re.search(r'\bI\s+ANSM_PERF\s*: (\{.*\})$', line)
        if match:
            try:
                value = json.loads(match.group(1))
            except json.JSONDecodeError:
                continue  # A split/truncated log line is not a valid report.
            if value.get('environment') == 'Native API26 SQLCipher synthetic fixture; emulator, not physical hardware':
                reports.append(value)
    if not reports:
        raise ValueError('No complete synthetic metadata in crash log')
    return reports[-1]


def budget_pass(report):
    return report.get('status') == 'DIAGNOSTIC_BUDGET_PASS' and report.get('api26_low_memory_acceptance') == 'EMULATOR_BUDGET_PASS'


def trace_path(report_path):
    if report_paths([report_path]) != [report_path]:
        raise ValueError('Not a synthetic report path')
    return report_path.rsplit('/', 1)[0] + '/audit-sampling.trace'


def verify_trace_emulator(execute):
    # Only a fresh API26 emulator is allowed; never inspect arbitrary device files.
    serial = execute(['adb', 'get-serialno'], text=True).strip()
    qemu = execute(['adb', 'shell', 'getprop', 'ro.kernel.qemu'], text=True).strip()
    sdk = execute(['adb', 'shell', 'getprop', 'ro.build.version.sdk'], text=True).strip()
    if not re.fullmatch(r'emulator-[0-9]+', serial) or qemu != '1' or sdk != '26':
        raise ValueError('Trace collection requires the isolated API26 emulator')


def collect_trace(report_path, out, execute=subprocess.check_output, expected_size=None):
    verify_trace_emulator(execute)
    path = trace_path(report_path)
    words = execute(['adb', 'shell', 'wc', '-c', path], text=True).split()
    if not words:
        raise ValueError('Missing trace size')
    size = words[0]
    from summarize_ansm_method_trace import MAX_TRACE_BYTES, summarize
    if not re.fullmatch(r'[0-9]{1,9}', size) or not 18 <= int(size) <= MAX_TRACE_BYTES:
        raise ValueError('Trace size outside diagnostic bounds')
    if expected_size is not None and int(size) != expected_size:
        raise ValueError('Ready trace length differs from device file')
    data = execute(['adb', 'exec-out', 'head', '-c', str(int(size) + 1), path])
    if len(data) != int(size):
        raise ValueError('Incomplete trace transfer')
    (out / 'audit-sampling.trace').write_bytes(data)
    meta = {'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
    try:
        summary = summarize(data)
    except ValueError as error:
        return {**meta,'status':'UNAVAILABLE','reason':'UnsupportedTrace:'+type(error).__name__}
    (out / 'audit-sampling-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    return {**meta,'status':summary['status']}


def valid_token(value):
    try:
        return isinstance(value, str) and str(uuid.UUID(value)) == value
    except ValueError:
        return False


def device_json(path, execute):
    try:
        data = execute(['adb','exec-out','head','-c','65537',path], stderr=subprocess.DEVNULL, timeout=5)
    except subprocess.CalledProcessError:
        return None
    if len(data) > 65536:
        raise ValueError('Oversized synthetic handshake metadata')
    return json.loads(data) if data else None


def cached_trace(report, out):
    try:
        token = report['post_budget_components']['sampling_trace']['token']
        meta = json.loads((out / 'trace-collection.json').read_text())
        data = out / 'audit-sampling.trace'
        capture = report['post_budget_components']['sampling_trace']
        if (not valid_token(token) or meta.get('source_token') != token
                or meta.get('status') not in ('DIAGNOSTIC_PROFILE','PARTIAL_DIAGNOSTIC','UNAVAILABLE')
                or type(capture.get('bytes')) is not int or data.stat().st_size != capture['bytes']
                or not 18 <= data.stat().st_size <= 32*1024*1024):
            return None
        if meta.get('sha256') != hashlib.sha256(data.read_bytes()).hexdigest():
            return None
        return meta
    except (OSError, ValueError, KeyError, TypeError):
        return None


def watch_trace_once(out, token, execute=subprocess.check_output):
    if not valid_token(token):
        raise ValueError('Invalid synthetic trace token')
    verify_trace_emulator(execute)
    ready = []
    for package in ('net.plainnotes.app.data.test', 'net.plainnotes.app.data'):
        folder = '/sdcard/Android/data/' + package + '/files/ansm-performance/'
        value = device_json(folder+'trace-ready.json', execute)
        if isinstance(value,dict) and value.get('scope') == 'SYNTHETIC_ART_TRACE_READY_V1' and value.get('token') == token:
            if set(value) != {'scope','token','bytes'} or type(value['bytes']) is not int or not 18 <= value['bytes'] <= 32*1024*1024:
                raise ValueError('Invalid synthetic ready metadata')
            ready.append((folder,value))
    if len(ready) > 1:
        raise ValueError('Ambiguous synthetic ready paths')
    if not ready:
        return False
    folder, value = ready[0]
    report = device_json(folder+'large-ten-year.json', execute)
    capture = report.get('post_budget_components',{}).get('sampling_trace',{}) if isinstance(report,dict) else {}
    if (not isinstance(report,dict) or report.get('environment') != 'Native API26 SQLCipher synthetic fixture; emulator, not physical hardware'
            or capture.get('token') != token or capture.get('bytes') != value['bytes']
            or capture.get('scope') != 'TEST_ONLY_POST_BUDGET_METHOD_STACKS_NO_ARGUMENTS'):
        return False
    try:
        meta = collect_trace(folder+'large-ten-year.json', out, execute, expected_size=value['bytes'])
    except (OSError,ValueError,subprocess.CalledProcessError) as error:
        meta = {'status':'UNAVAILABLE','reason':type(error).__name__}
    meta['source_token'] = token
    (out/'trace-collection.json').write_text(json.dumps(meta,indent=2)+'\n')
    if 'sha256' in meta:
        ack=out/'trace-collected.ack'
        ack.write_text(token)
        execute(['adb','push',str(ack),folder+'trace-collected.ack'],stderr=subprocess.DEVNULL,timeout=5)
        return True
    return False


def watch_trace(out, stop, execute=subprocess.check_output):
    # Read the host log only until the post-budget ready signal. No adb polling during budgets.
    position=0
    pending=''
    tokens=set()
    while not stop.is_set():
        try:
            with (out/'synthetic-crash-log.txt').open() as log:
                log.seek(position);pending+=log.read(65536);position=log.tell()
            lines=pending.split('\n');pending=lines.pop()
            for line in lines:
                match=re.search(r'\bI\s+ANSM_TRACE\s*: (\{.*\})$',line)
                if not match:
                    continue
                value=json.loads(match.group(1))
                if value.get('scope') == 'SYNTHETIC_ART_TRACE_READY_V1' and valid_token(value.get('token')):
                    tokens.add(value['token'])
            for token in tokens:
                if watch_trace_once(out,token,execute):
                    return
        except (OSError,ValueError,subprocess.CalledProcessError,subprocess.TimeoutExpired) as error:
            (out/'trace-watch-error.json').write_text(json.dumps({'status':'UNAVAILABLE','reason':type(error).__name__})+'\n')
        stop.wait(0.25)


def collect():
    out = Path('core/data/build/reports/ansm-native-performance')
    out.mkdir(parents=True, exist_ok=True)
    paths = subprocess.check_output(['adb', 'shell', 'find', '/sdcard/Android/data', '-path', '*/files/ansm-performance/large-ten-year.json', '-type', 'f'], text=True).splitlines()
    paths = report_paths(paths)
    if len(paths) > 1:
        raise SystemExit('Ambiguous synthetic reports; refusing to choose one')
    if paths:
        data = subprocess.check_output(['adb', 'exec-out', 'cat', paths[0]])
        report = json.loads(data)
    else:
        try:
            report = log_report((out / 'synthetic-crash-log.txt').read_text())
        except (OSError, ValueError) as error:
            raise SystemExit('Synthetic report unavailable in external files and logcat') from error
        data = (json.dumps(report, indent=2) + '\n').encode()
    (out / 'large-ten-year.json').write_bytes(data)
    # Method stacks contain code names/times only, from the synthetic post-budget phase.
    trace = cached_trace(report, out)
    if trace is None and paths:
        try:
            capture = report['post_budget_components']['sampling_trace']
            if not valid_token(capture.get('token')) or type(capture.get('bytes')) is not int:
                raise ValueError('No complete current trace metadata')
            trace = collect_trace(paths[0], out, expected_size=capture['bytes'])
            trace['source_token'] = capture['token']
        except (OSError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
            trace = {'status': 'UNAVAILABLE', 'reason': type(error).__name__}
    if trace is None:
        trace = {'status':'UNAVAILABLE','reason':'No matching pre-cleanup trace capture'}
    (out / 'trace-collection.json').write_text(json.dumps(trace, indent=2) + '\n')
    if not budget_pass(report):
        raise SystemExit('Native synthetic performance did not pass all predefined budgets; report preserved')


if __name__ == "__main__":
    collect()
