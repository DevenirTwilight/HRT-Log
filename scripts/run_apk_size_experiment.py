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
import signal
import subprocess
import sys
import time
import urllib.parse
import zipfile

from apk_size_audit import inspect, markdown


def run(command, cwd, log, idle_compiler_java=None):
    with log.open('w') as stream:
        process = subprocess.Popen(command, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT)
        cleaned = False
        while process.poll() is None:
            if idle_compiler_java and not cleaned and '> Task :app:minifyFullReleaseWithR8' in log.read_text():
                # All Kotlin inputs of R8 are already compiled at this DAG boundary.
                # Match the experiment-local JDK, never the machine's shared JDK.
                stopped = []
                for proc in Path('/proc').iterdir():
                    if not proc.name.isdigit():
                        continue
                    try:
                        args = (proc / 'cmdline').read_bytes().split(b'\0')
                        if args[0].decode() == str(idle_compiler_java) and b'KotlinCompileDaemon' in b' '.join(args):
                            os.kill(int(proc.name), signal.SIGTERM)
                            stopped.append(proc.name)
                    except (FileNotFoundError, PermissionError, ProcessLookupError):
                        continue
                stream.write('\nAPK_SIZE_IDLE_COMPILER_CLEANUP=' + json.dumps(stopped) + '\n')
                stream.flush()
                cleaned = True
            time.sleep(1)
    return {'command': list(map(str, command)), 'exit_code': process.returncode, 'log': log.name}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commit', required=True, help='Full pinned Git commit SHA')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--gradle', default='./gradlew')
    parser.add_argument('--http-proxy', action='store_true', help='Use inherited HTTPS_PROXY for Java network requests')
    parser.add_argument('--stop-idle-kotlin-daemon', action='store_true', help='For a dedicated small-memory environment, stop the experiment-local idle compiler when R8 starts')
    args = parser.parse_args()
    repo = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip()).resolve()
    out = args.out.resolve()
    if out == repo or repo in out.parents:
        parser.error('APK experiment output must be outside the source repository')
    compiler_java = None
    if args.stop_idle_kotlin_daemon:
        if 'JAVA_HOME' not in os.environ:
            parser.error('Memory cleanup requires JAVA_HOME to select an experiment-local JDK')
        jdk = Path(os.environ['JAVA_HOME']).resolve()
        if out.parent not in jdk.parents or not Path('/proc').is_dir():
            parser.error('Memory cleanup requires Linux and a dedicated JDK under the experiment parent directory')
        compiler_java = jdk / 'bin/java'
    commit = subprocess.check_output(['git', 'rev-parse', args.commit + '^{commit}'], cwd=repo, text=True).strip()
    if args.commit != commit:
        parser.error('--commit must be the complete SHA')
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=repo, text=True).strip():
        parser.error('Source working tree must be clean; commit audit changes first')
    out.mkdir(parents=True, exist_ok=True)
    if (out / 'experiment.json').exists():
        parser.error('Use a new output directory; existing evidence will not be overwritten')
    conditions = out / 'conditions.gradle'
    conditions.write_text('''gradle.projectsEvaluated {
    def android = gradle.rootProject.project(':app').extensions.getByName('android')
    def release = android.buildTypes.getByName('release')
    println('APK_SIZE_BUILD_CONDITIONS=' + groovy.json.JsonOutput.toJson([
        minify: release.minifyEnabled, shrinkResources: release.shrinkResources,
        abiFilters: android.defaultConfig.ndk.abiFilters.toList().sort(),
        signingConfig: release.signingConfig?.name,
        flavors: android.productFlavors.collect { it.name }.sort(),
        buildTools: android.buildToolsVersion, compileSdk: android.compileSdkVersion
    ]))
}
''')
    common = []
    if args.http_proxy:
        proxy = urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
        if not proxy.hostname or not proxy.port or proxy.username or proxy.password:
            parser.error('Expected a credential-free inherited proxy URL with port')
        for scheme in ('http', 'https'):
            common += [f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port}']
    common += ['--no-daemon', '--console=plain', '--no-parallel', '--max-workers=2', '-I', str(conditions)]
    evidence = {'source_commit': commit, 'task': ':app:assembleFullRelease',
                'sdk_root': str(args.sdk.resolve()), 'signing': 'unsigned; no official credentials',
                'scenarios': [], 'status': 'in_progress', 'java_home': os.environ.get('JAVA_HOME'),
                'gradle_executable': args.gradle}
    evidence['stop_experiment_local_idle_compiler_at_r8'] = args.stop_idle_kotlin_daemon

    def save():
        (out / 'experiment.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')

    save()
    for label, shrink, arm64 in [('A', None, False), ('B', True, False), ('C', None, True), ('D', True, True)]:
        scenario = {'label': label, 'source_commit': commit, 'resource_shrink': shrink,
                    'resource_shrink_note': 'source default' if shrink is None else 'explicit true',
                    'abi_filter': ['arm64-v8a'] if arm64 else [], 'minify': True, 'status': 'pending', 'apk': None}
        evidence['scenarios'].append(scenario)
    save()
    baseline_native = None
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
            scenario['build'] = run([args.gradle, *common, ':app:assembleFullRelease'], tree, out / f'{label}-build.log', compiler_java)
            if scenario['build']['exit_code']:
                scenario['status'] = 'build_failed'
                raise RuntimeError(f'{label}: fullRelease build failed')
            condition_lines = [line.split('=', 1)[1] for line in (out / f'{label}-build.log').read_text().splitlines() if line.startswith('APK_SIZE_BUILD_CONDITIONS=')]
            if len(condition_lines) != 1:
                raise RuntimeError(f'{label}: missing unambiguous effective build conditions')
            scenario['effective_conditions'] = json.loads(condition_lines[0])
            actual = scenario['effective_conditions']
            if not actual['minify'] or actual['abiFilters'] != scenario['abi_filter'] or actual['signingConfig'] is not None or actual['flavors'] != ['full']:
                scenario['status'] = 'conditions_failed'
                raise RuntimeError(f'{label}: unexpected effective build conditions')
            if scenario['resource_shrink'] and not actual['shrinkResources']:
                raise RuntimeError(f'{label}: resource shrinking not enabled')
            apk = tree / 'app/build/outputs/apk/full/release/app-full-release-unsigned.apk'
            target = out / f'{label}.apk'
            shutil.copy2(apk, target)
            scenario['apk'] = inspect(target)
            with zipfile.ZipFile(target) as archive:
                scenario['native_sha256'] = {m['name']: hashlib.sha256(archive.read(m['name'])).hexdigest() for m in scenario['apk']['native_libraries']}
                scenario['named_java_resources'] = {}
                for source in ('pk-engine/src/main/resources/pk-params.json', 'app/src/main/resources/symptom-sources.json', 'app/src/main/resources/wellbeing-translations.json'):
                    name = Path(source).name
                    original_sha = hashlib.sha256((tree / source).read_bytes()).hexdigest()
                    packaged_sha = hashlib.sha256(archive.read(name)).hexdigest()
                    scenario['named_java_resources'][name] = {'source_sha256': original_sha, 'packaged_sha256': packaged_sha, 'matches': original_sha == packaged_sha}
                    if original_sha != packaged_sha:
                        raise RuntimeError(f'{label}: named Java resource changed: {name}')
            if baseline_native is None:
                baseline_native = scenario['native_sha256']
            expected_native = {name: digest for name, digest in baseline_native.items() if not scenario['abi_filter'] or name.startswith('lib/arm64-v8a/')}
            if scenario['native_sha256'] != expected_native:
                scenario['status'] = 'native_failed'
                raise RuntimeError(f'{label}: native library lost, added, or changed unexpectedly')
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
            scenario['resources'] = run([aapt, 'dump', 'resources', str(target)], tree, out / f'{label}-resources.log')
            if any(scenario[k]['exit_code'] for k in ('manifest', 'permissions', 'badging', 'packaged_manifest', 'resources')):
                scenario['status'] = 'manifest_failed'
                raise RuntimeError(f'{label}: manifest inspection failed')
            if 'android.permission.INTERNET' in (out / f'{label}-permissions.log').read_text():
                raise RuntimeError(f'{label}: unexpected INTERNET permission in packaged APK')
            critical = ['mipmap/ic_launcher', 'mipmap/ic_shell_calc', 'mipmap/ic_shell_notes',
                        'drawable/ic_launcher_foreground', 'drawable/ic_launcher_monochrome',
                        'drawable/ic_shell_calc_fg', 'drawable/ic_shell_calc_mono',
                        'drawable/ic_shell_notes_fg', 'drawable/ic_shell_notes_mono', 'xml/locales_config']
            resource_table = (out / f'{label}-resources.log').read_text()
            scenario['critical_resource_names_present'] = {name: name in resource_table for name in critical}
            if not all(scenario['critical_resource_names_present'].values()):
                scenario['status'] = 'resource_failed'
                raise RuntimeError(f'{label}: required launcher/locale resource name missing')
            scenario['status'] = 'build_and_static_checks_passed; release_functionality_unverified'
            save()
            print(f'{label}: {scenario["apk"]["size_bytes"]} bytes', flush=True)
        reports = [s['apk'] for s in evidence['scenarios']]
        (out / 'size-report.json').write_text(json.dumps(reports, indent=2) + '\n')
        (out / 'size-report.md').write_text(markdown(reports))
        evidence['status'] = 'four_builds_completed; release_functionality_unverified'
    except Exception as error:
        if scenario['status'] in ('pending', 'building'):
            scenario['status'] = 'validation_or_execution_failed'
        scenario['error'] = str(error)
        evidence['status'] = 'stopped'
        evidence['error'] = str(error)
        raise
    finally:
        save()


if __name__ == '__main__':
    main()
