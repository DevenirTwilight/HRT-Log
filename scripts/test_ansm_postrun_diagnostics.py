import ast
import importlib.util
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest

ROOT=Path(__file__).resolve().parent

def runner():
    spec=importlib.util.spec_from_file_location('runner',ROOT/'run_ansm_native_performance.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

def environment(command,**kwargs):
    return {'get-serialno':'emulator-5554','ro.kernel.qemu':'1','ro.build.version.sdk':'26'}[command[-1]]+'\n'

class PostRunKernelDiagnosticsTest(unittest.TestCase):
    def test_success_and_incomplete_runs_do_not_probe_device_or_create_files(self):
        def forbidden(*args,**kwargs):raise AssertionError('unexpected device read')
        with TemporaryDirectory() as folder:
            for code,status in [(0,'NOT_RUN_SUCCESS'),(None,'NOT_RUN_NO_COMPLETED_EXIT')]:
                result=runner().collect_post_run_kernel(Path(folder),code,execute=forbidden,environment=forbidden)
                self.assertEqual(status,result['status']);self.assertEqual([],list(Path(folder).iterdir()))

    def test_verified_emulator_tail_is_read_only_bounded_and_never_budget_pass(self):
        payload=b'older'*16000+b'SYNTHETIC kernel tail';calls=[]
        def execute(command,**kwargs):
            calls.append(command);self.assertEqual(5,kwargs['timeout'])
            kwargs['stdout'].write(payload);return SimpleNamespace(returncode=0)
        with TemporaryDirectory() as folder:
            result=runner().collect_post_run_kernel(Path(folder),1,execute=execute,environment=environment)
            self.assertEqual([['adb','shell','-T','sh','-c','dmesg | tail -n 80']],calls)
            self.assertEqual(payload[-65536:],(Path(folder)/'post-run-kernel-tail.txt').read_bytes())
            self.assertTrue(result['clipped']);self.assertEqual('CAPTURED_DIAGNOSTIC_ONLY',result['status'])
            self.assertNotIn('budget_pass',result);self.assertNotIn('oom_cause',result)

    def test_real_devices_are_rejected_before_kernel_read(self):
        def forbidden(*args,**kwargs):raise AssertionError('unexpected kernel read')
        def physical(command,**kwargs):return 'real-device\n' if command[-1]=='get-serialno' else environment(command,**kwargs)
        with TemporaryDirectory() as folder:
            result=runner().collect_post_run_kernel(Path(folder),1,execute=forbidden,environment=physical)
            self.assertEqual('UNAVAILABLE',result['status']);self.assertFalse((Path(folder)/'post-run-kernel-tail.txt').exists())

    def test_denied_and_timed_out_kernel_reads_stay_unavailable(self):
        def denied(command,**kwargs):
            kwargs['stderr'].write(b'dmesg: permission denied');return SimpleNamespace(returncode=1)
        def timeout(command,**kwargs):raise subprocess.TimeoutExpired(command,5)
        def empty_success(command,**kwargs):
            kwargs['stderr'].write(b'dmesg: permission denied');return SimpleNamespace(returncode=0)
        for execute in [denied,empty_success,timeout]:
            with TemporaryDirectory() as folder:
                result=runner().collect_post_run_kernel(Path(folder),1,execute=execute,environment=environment)
                self.assertEqual('UNAVAILABLE',result['status']);self.assertFalse((Path(folder)/'post-run-kernel-tail.txt').exists())

    def test_capture_is_only_in_finally_after_the_original_run_returns(self):
        tree=ast.parse((ROOT/'run_ansm_native_performance.py').read_text())
        run=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='run')
        self.assertFalse(any(isinstance(n,ast.Name) and n.id=='collect_post_run_kernel' for n in ast.walk(run)))
        diagnostics=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='diagnostics')
        tries=[n for n in ast.walk(diagnostics) if isinstance(n,ast.Try)]
        self.assertTrue(any(any(isinstance(x,ast.Name) and x.id=='collect_post_run_kernel' for child in n.finalbody for x in ast.walk(child)) for n in tries))

if __name__=='__main__':unittest.main()
