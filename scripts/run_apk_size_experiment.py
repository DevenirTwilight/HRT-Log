#!/usr/bin/env python3
"""Build four unsigned fullRelease packages in detached, external worktrees.

Never signs, installs, uploads, changes production Gradle, or cleans user data.
Stops on the first build/manifest/ABI failure and persists partial evidence.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.parse

from apk_size_audit import inspect, markdown


def run(command, cwd, log):
    with log.open('w') as stream:
        result = subprocess.run(command, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT)
    return {'command': list(map(str, command)), 'exit_code': result.returncode, 'log': log.name}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commit', required=True, help='Full pinned Git commit SHA')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--gradle', default='./gradlew')
    parser.add_argument('--http-proxy', action='store_true', help='Use inherited HTTPS_PROXY for Java network requests')
    args = parser.parse_args()
    repo = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip()).resolve()
    out = args.out.resolve()
    if out == repo or repo in out.parents:
        parser.error('APK experiment output must be outside the source repository')
    commit = subprocess.check_output(['git', 'rev-parse', args.commit + '^{commit}'], cwd=repo, text=True).strip()
    if args.commit != commit:
        parser.error('--commit must be the complete SHA')
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=repo, text=True).strip():
        parser.error('Source working tree must be clean; commit audit changes first')
    out.mkdir(parents=True, exist_ok=True)
    if (out / 'experiment.json').exists():
        parser.error('Use a new output directory; existing evidence will not be overwritten')
    common = []
    if args.http_proxy:
        proxy = urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
        if not proxy.hostname or not proxy.port or proxy.username or proxy.password:
            parser.error('Expected a credential-free inherited proxy URL with port')
        for scheme in ('http', 'https'):
            common += [f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port}']
    common += ['--no-daemon', '--console=plain', '--no-parallel', '--max-workers=2']
    evidence = {'source_commit': commit, 'task': ':app:assembleFullRelease',
                'sdk_root': str(args.sdk.resolve()), 'signing': 'unsigned; no official credentials',
                'scenarios': [], 'status': 'in_progress', 'java_tool_options': os.environ.get('JAVA_TOOL_OPTIONS', ''),
                'gradle_executable': args.gradle}

    def save():
        (out / 'experiment.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')

    save()
    for label, shrink, arm64 in [('A', None, False), ('B', True, False), ('C', None, True), ('D', True, True)]:
        scenario = {'label': label, 'source_commit': commit, 'resource_shrink': shrink,
                    'resource_shrink_note': 'source default' if shrink is None else 'explicit true',
                    'abi_filter': ['arm64-v8a'] if arm64 else [], 'minify': True, 'status': 'pending', 'apk': None}
        evidence['scenarios'].append(scenario)
    save()
    try:
        for scenario in evidence['scenarios']:
            label = scenario['label']
            tree = out / ('worktree-' + label)
            subprocess.run(['git', 'worktree', 'add', '--detach', str(tree), commit], cwd=repo, check=True)
            (tree / 'local.properties').write_text('sdk.dir=' + str(args.sdk.resolve()) + '\n')
            build_file = tree / 'app/build.gradle.kts'
            original = build_file.read_bytes()
            scenario['original_gradle_sha256'] = hashlib.sha256(original).hexdigest()
            additions = []
            if scenario['resource_shrink']:
                additions.append('buildTypes { getByName("release") { isShrinkResources = true } }')
            if scenario['abi_filter']:
                additions.append('defaultConfig { ndk { abiFilters.clear(); abiFilters += "arm64-v8a" } }')
            if additions:
                build_file.write_bytes(original + ('\n// Isolated size experiment only.\nandroid {\n' + '\n'.join(additions) + '\n}\n').encode())
            scenario['experimental_gradle_sha256'] = hashlib.sha256(build_file.read_bytes()).hexdigest()
            scenario['status'] = 'building'
            save()
            scenario['build'] = run([args.gradle, *common, ':app:assembleFullRelease'], tree, out / f'{label}-build.log')
            if scenario['build']['exit_code']:
                scenario['status'] = 'build_failed'
                raise RuntimeError(f'{label}: fullRelease build failed')
            apk = tree / 'app/build/outputs/apk/full/release/app-full-release-unsigned.apk'
            target = out / f'{label}.apk'
            shutil.copy2(apk, target)
            scenario['apk'] = inspect(target)
            if scenario['abi_filter'] and scenario['apk']['abis'] != ['arm64-v8a']:
                scenario['status'] = 'abi_failed'
                raise RuntimeError(f'{label}: unexpected ABI list')
            if 'arm64-v8a' not in scenario['apk']['abis'] or not any(m['name'] == 'lib/arm64-v8a/libsqlcipher.so' for m in scenario['apk']['native_libraries']):
                scenario['status'] = 'native_failed'
                raise RuntimeError(f'{label}: missing arm64 SQLCipher')
            scenario['manifest'] = run([sys.executable, 'scripts/check_release_manifest.py'], tree, out / f'{label}-manifest-check.log')
            aapt = str(args.sdk.resolve() / 'build-tools/37.0.0/aapt2')
            scenario['permissions'] = run([aapt, 'dump', 'permissions', str(target)], tree, out / f'{label}-permissions.log')
            scenario['badging'] = run([aapt, 'dump', 'badging', str(target)], tree, out / f'{label}-badging.log')
            scenario['packaged_manifest'] = run([aapt, 'dump', 'xmltree', str(target), '--file', 'AndroidManifest.xml'], tree, out / f'{label}-packaged-manifest.log')
            if any(scenario[k]['exit_code'] for k in ('manifest', 'permissions', 'badging', 'packaged_manifest')):
                scenario['status'] = 'manifest_failed'
                raise RuntimeError(f'{label}: manifest inspection failed')
            scenario['status'] = 'build_and_static_checks_passed; release_functionality_unverified'
            save()
            print(f'{label}: {scenario["apk"]["size_bytes"]} bytes', flush=True)
        reports = [s['apk'] for s in evidence['scenarios']]
        (out / 'size-report.json').write_text(json.dumps(reports, indent=2) + '\n')
        (out / 'size-report.md').write_text(markdown(reports))
        evidence['status'] = 'four_builds_completed; release_functionality_unverified'
    except Exception as error:
        evidence['status'] = 'stopped'
        evidence['error'] = str(error)
        raise
    finally:
        save()


if __name__ == '__main__':
    main()
