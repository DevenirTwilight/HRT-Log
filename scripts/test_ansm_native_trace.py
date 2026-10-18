import importlib.util
import json
from pathlib import Path
import struct
from tempfile import TemporaryDirectory
import unittest

ROOT = Path(__file__).resolve().parent

def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / (name + '.py'))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def trace(events=None, clock='dual', overflow='false'):
    header = ('*version\n3\ndata-file-overflow='+overflow+'\nclock='+clock+'\n*threads\n1\tSYNTHETIC\n*methods\n'
              '0x4\tnet/plainnotes/app/data/Synthetic\touter\t()V\tSynthetic.kt\t1\n'
              '0x8\torg/json/JSONObject\tparse\t()V\tJSON.java\t2\n*end\n').encode()
    events = events if events is not None else [(1,4,0,0),(1,8,10,12),(1,9,30,42),(1,5,50,70)]
    size = 14 if clock == 'dual' else 10
    records = b''.join(struct.pack('<HIII',*row) if size==14 else struct.pack('<HII',*row[:3]) for row in events)
    return header + struct.pack('<4sHHQH', b'SLOW',3,18,0,size) + records


class ArtSamplingSummaryTest(unittest.TestCase):
    def test_nested_dual_clock_has_independently_known_exclusive_and_inclusive_times(self):
        out=module('summarize_ansm_method_trace').summarize(trace())
        self.assertEqual('DIAGNOSTIC_PROFILE',out['status'])
        rows={r['method']:r for r in out['top_exclusive']}
        outer=rows['net/plainnotes/app/data/Synthetic.outer()V']
        self.assertEqual((50,30,1),(outer['inclusive_sampled_us'],outer['exclusive_sampled_us'],outer['sampled_segments']))
        self.assertEqual(20,rows['org/json/JSONObject.parse()V']['exclusive_sampled_us'])
        self.assertEqual(70,out['observed_wall_span_us'])
        self.assertEqual(4,out['records'])
        self.assertTrue(out['sampled_segments_are_not_invocation_counts'])

    def test_recursive_frames_and_wall_only_clock_keep_distinct_semantics(self):
        summarize=module('summarize_ansm_method_trace').summarize
        recursive=summarize(trace([(1,4,0,0),(1,4,10,10),(1,5,20,20),(1,5,30,30)]))['top_exclusive'][0]
        self.assertEqual((40,30,2),(recursive['inclusive_sampled_us'],recursive['exclusive_sampled_us'],recursive['sampled_segments']))
        wall=summarize(trace(clock='wall'))
        self.assertIn('wall microseconds',wall['timings'])
        self.assertEqual(50,wall['observed_wall_span_us'])

    def test_truncated_unsupported_or_regressing_clocks_never_produce_invented_timings(self):
        summarize=module('summarize_ansm_method_trace').summarize
        for data in (trace()[:-1],trace().replace(b'SLOW',b'BAD!'),trace().replace(b'clock=dual',b'clock=other'),
                     trace([(1,4,20,20),(1,5,10,30)]),trace().replace(struct.pack('<4sHH',b'SLOW',3,18),struct.pack('<4sHH',b'SLOW',4,18))):
            with self.assertRaises(ValueError):summarize(data)

    def test_unknown_methods_open_frames_and_overflow_are_explicitly_partial(self):
        summarize=module('summarize_ansm_method_trace').summarize
        for data in (trace(overflow='true'),trace([(1,4,0,0)]),trace().replace(b'0x8\torg/json/JSONObject\tparse\t()V\tJSON.java\t2\n',b'')):
            self.assertEqual('PARTIAL_DIAGNOSTIC',summarize(data)['status'])


class ArtSamplingCollectionTest(unittest.TestCase):
    path='/sdcard/Android/data/net.plainnotes.app.data.test/files/ansm-performance/large-ten-year.json'
    def fake(self, calls, *, serial='emulator-5554',qemu='1',sdk='26',size=None,data=None):
        data=trace() if data is None else data
        def execute(command, **kwargs):
            calls.append(command)
            if command[-1]=='get-serialno':return serial+'\n'
            if command[-1]=='ro.kernel.qemu':return qemu+'\n'
            if command[-1]=='ro.build.version.sdk':return sdk+'\n'
            if 'wc' in command:return str(len(data) if size is None else size)+' '+command[-1]+'\n'
            if 'head' in command:return data
            raise AssertionError(command)
        return execute

    def test_only_exact_synthetic_trace_path_is_accepted(self):
        collector=module('collect_ansm_native_performance')
        self.assertEqual(self.path.rsplit('/',1)[0]+'/audit-sampling.trace',collector.trace_path(self.path))
        for path in ('/sdcard/health.json',self.path+'/../health.json',self.path.replace('data.test','other.app')):
            with self.assertRaises(ValueError):collector.trace_path(path)

    def test_real_or_wrong_environment_rejects_before_reading_any_trace(self):
        collector=module('collect_ansm_native_performance')
        with TemporaryDirectory() as directory:
            for kwargs in ({'serial':'physical-device'},{'qemu':'0'},{'sdk':'35'}):
                calls=[]
                with self.assertRaises(ValueError):collector.collect_trace(self.path,Path(directory),self.fake(calls,**kwargs))
                self.assertFalse(any('cat' in c or 'head' in c or 'wc' in c for c in calls))

    def test_size_fence_and_incomplete_transfer_do_not_create_a_profile(self):
        collector=module('collect_ansm_native_performance')
        with TemporaryDirectory() as directory:
            calls=[]
            with self.assertRaises(ValueError):collector.collect_trace(self.path,Path(directory),self.fake(calls,size=33*1024*1024))
            self.assertFalse(any('cat' in c or 'head' in c for c in calls))
            with self.assertRaises(ValueError):collector.collect_trace(self.path,Path(directory),self.fake([],size=len(trace())+1))
            self.assertFalse((Path(directory)/'audit-sampling-summary.json').exists())
            base=self.fake([])
            def empty_size(command, **kwargs):return '' if 'wc' in command else base(command,**kwargs)
            with self.assertRaises(ValueError):collector.collect_trace(self.path,Path(directory),empty_size)

    def test_synthetic_trace_is_hashed_and_summarized_without_affecting_budget_failure(self):
        collector=module('collect_ansm_native_performance')
        with TemporaryDirectory() as directory:
            out=collector.collect_trace(self.path,Path(directory),self.fake([]))
            self.assertEqual('DIAGNOSTIC_PROFILE',out['status'])
            reads=[]
            collector.collect_trace(self.path,Path(directory),self.fake(reads))
            command=next(c for c in reads if 'head' in c)
            self.assertEqual(str(len(trace())+1),command[command.index('-c')+1])
            self.assertEqual(trace(),(Path(directory)/'audit-sampling.trace').read_bytes())
            self.assertEqual(out['sha256'],json.loads((Path(directory)/'audit-sampling-summary.json').read_text())['trace_sha256'])
            self.assertFalse(collector.budget_pass({'status':'FAIL','api26_low_memory_acceptance':'FAIL'}))


class ArtSamplingRunnerIsolationTest(unittest.TestCase):
    def test_read_only_preflight_accepts_the_expected_emulator(self):
        runner=module('run_ansm_native_performance')
        calls=[]
        runner.verify_emulator(ArtSamplingCollectionTest().fake(calls))
        self.assertEqual(3,len(calls))
        self.assertFalse(any('setprop' in c for c in calls))

    def test_runner_rejects_real_device_wrong_api_and_non_qemu_before_gradle(self):
        runner=module('run_ansm_native_performance')
        for kwargs in ({'serial':'physical-device'},{'qemu':'0'},{'sdk':'35'}):
            with self.assertRaises(ValueError):runner.verify_emulator(ArtSamplingCollectionTest().fake([],**kwargs))


class ArtSamplingHandshakeTest(unittest.TestCase):
    token='00000000-0000-4000-8000-000000000001'
    path=ArtSamplingCollectionTest.path
    def environment(self, calls, data=None, report_token=None):
        data=trace() if data is None else data
        ready={'scope':'SYNTHETIC_ART_TRACE_READY_V1','token':self.token,'bytes':len(data)}
        report={'environment':'Native API26 SQLCipher synthetic fixture; emulator, not physical hardware','diagnostic_run_token':self.token,
                'post_budget_components':{'sampling_trace':{'token':self.token if report_token is None else report_token,'bytes':len(data),
                    'scope':'TEST_ONLY_POST_BUDGET_METHOD_STACKS_NO_ARGUMENTS'}}}
        base=ArtSamplingCollectionTest().fake(calls,data=data)
        def execute(command, **kwargs):
            if 'head' in command and command[-1].endswith('.json'):
                calls.append(command)
                if '/net.plainnotes.app.data/' in command[-1]:
                    import subprocess
                    raise subprocess.CalledProcessError(1,command)
                return json.dumps(ready if command[-1].endswith('trace-ready.json') else report).encode()
            if 'push' in command:
                calls.append(command)
                self.assertEqual(self.token,Path(command[2]).read_text())
                self.assertEqual(self.path.rsplit('/',1)[0]+'/trace-collected.ack',command[-1])
                return b'1 synthetic file pushed'
            return base(command,**kwargs)
        return execute,report

    def test_pre_cleanup_capture_ack_and_post_cleanup_cache_are_bound_to_same_nonce(self):
        collector=module('collect_ansm_native_performance')
        with TemporaryDirectory() as directory:
            out=Path(directory);calls=[];execute,report=self.environment(calls)
            self.assertTrue(collector.watch_trace_once(out,self.token,execute))
            self.assertTrue(any('push' in c for c in calls))
            cached=collector.cached_trace(report,out)
            self.assertEqual('DIAGNOSTIC_PROFILE',cached['status'])
            report['post_budget_components']['sampling_trace']['token']='00000000-0000-4000-8000-000000000002'
            self.assertIsNone(collector.cached_trace(report,out))

    def test_mismatched_report_nonce_never_reads_binary_or_acknowledges(self):
        collector=module('collect_ansm_native_performance')
        with TemporaryDirectory() as directory:
            calls=[];execute,_=self.environment(calls,report_token='00000000-0000-4000-8000-000000000002')
            self.assertFalse(collector.watch_trace_once(Path(directory),self.token,execute))
            self.assertFalse(any('push' in c or c[-1].endswith('.trace') for c in calls))

    def test_unsupported_format_preserves_raw_trace_but_cannot_claim_parsed_profile(self):
        collector=module('collect_ansm_native_performance')
        with TemporaryDirectory() as directory:
            out=Path(directory);execute,report=self.environment([],data=b'NOT_AN_ART_TRACE'*5)
            self.assertTrue(collector.watch_trace_once(out,self.token,execute))
            self.assertEqual('UNAVAILABLE',collector.cached_trace(report,out)['status'])
            self.assertFalse((out/'audit-sampling-summary.json').exists())
            self.assertEqual(b'NOT_AN_ART_TRACE'*5,(out/'audit-sampling.trace').read_bytes())

    def test_watcher_has_no_adb_activity_before_post_budget_ready_log_signal(self):
        collector=module('collect_ansm_native_performance')
        class Stop:
            stopped=False
            def is_set(self):return self.stopped
            def wait(self,seconds):self.stopped=True
        with TemporaryDirectory() as directory:
            out=Path(directory);(out/'synthetic-crash-log.txt').write_text('I ANSM_PERF: {"stage":"full_audit"}\n')
            def forbidden(*args,**kwargs):raise AssertionError('adb polled during measured budgets')
            collector.watch_trace(out,Stop(),forbidden,expected_token=self.token)
            self.assertFalse((out/'audit-sampling.trace').exists())

    def test_ready_log_signal_drives_capture_before_test_can_exit(self):
        import threading
        collector=module('collect_ansm_native_performance')
        with TemporaryDirectory() as directory:
            out=Path(directory);execute,_=self.environment([])
            message={'scope':'SYNTHETIC_ART_TRACE_READY_V1','token':self.token,'bytes':len(trace())}
            (out/'synthetic-crash-log.txt').write_text('10-18 06:00:00.000 1 2 I ANSM_TRACE: '+json.dumps(message)+'\n')
            collector.watch_trace(out,threading.Event(),execute,expected_token=self.token)
            self.assertEqual(self.token,(out/'trace-collected.ack').read_text())


class ArtSamplingRunTokenTest(unittest.TestCase):
    token='00000000-0000-4000-8000-000000000001'
    def test_runner_forwards_one_current_token_to_instrumentation_collector_and_execution_record(self):
        from types import SimpleNamespace
        runner=module('run_ansm_native_performance')
        with TemporaryDirectory() as directory:
            runner.ROOT=Path(directory);calls=[]
            def execute(command,**kwargs):calls.append(command);return SimpleNamespace(returncode=1 if len(calls)==1 else 0)
            self.assertEqual(1,runner.run(execute,trace_token=self.token))
            self.assertIn('-Pandroid.testInstrumentationRunnerArguments.ansmTraceToken='+self.token,calls[0])
            self.assertEqual(['--run-token',self.token],calls[1][-2:])
            self.assertEqual(self.token,json.loads((runner.ROOT/'core/data/build/reports/ansm-native-performance/execution.json').read_text())['run_token'])

    def test_invalid_run_token_rejects_before_starting_gradle(self):
        runner=module('run_ansm_native_performance')
        def forbidden(*args,**kwargs):raise AssertionError('Invalid token reached Gradle')
        for token in ('BAD','00000000-0000-4000-8000-00000000000A'):
            with self.assertRaises(ValueError):runner.run(forbidden,trace_token=token)

    def test_old_ready_signal_cannot_cause_device_polling_during_current_budgets(self):
        collector=module('collect_ansm_native_performance')
        class Stop:
            stopped=False
            def is_set(self):return self.stopped
            def wait(self,seconds):self.stopped=True
        with TemporaryDirectory() as directory:
            out=Path(directory);old={'scope':'SYNTHETIC_ART_TRACE_READY_V1','token':'00000000-0000-4000-8000-000000000002','bytes':len(trace())}
            (out/'synthetic-crash-log.txt').write_text('10-18 06:00:00.000 1 2 I ANSM_TRACE: '+json.dumps(old)+'\n')
            def forbidden(*args,**kwargs):raise AssertionError('Stale marker caused adb polling')
            collector.watch_trace(out,Stop(),forbidden,expected_token=self.token)
            self.assertFalse((out/'audit-sampling.trace').exists())

    def test_metadata_recovery_never_uses_another_runs_green_report(self):
        collector=module('collect_ansm_native_performance')
        current={'environment':'Native API26 SQLCipher synthetic fixture; emulator, not physical hardware','diagnostic_run_token':self.token,'status':'FAIL'}
        stale={**current,'diagnostic_run_token':'00000000-0000-4000-8000-000000000002','status':'DIAGNOSTIC_BUDGET_PASS','api26_low_memory_acceptance':'EMULATOR_BUDGET_PASS'}
        text='\n'.join('10-18 06:00:00.000 1 2 I ANSM_PERF: '+json.dumps(value) for value in (current,stale))
        self.assertEqual(current,collector.log_report(text,expected_token=self.token))
        with self.assertRaises(ValueError):collector.log_report(text,expected_token='00000000-0000-4000-8000-000000000003')
