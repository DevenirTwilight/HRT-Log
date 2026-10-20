import importlib.util
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

class NativePerformanceIsolationTest(unittest.TestCase):
    def test_sql_driver_timing_is_read_only_post_budget_and_never_logs_bindings(self):
        prepare = module('prepare_ansm_native_performance')
        text = prepare.native_source(prepare.SOURCE.read_text())
        start = text.index('val sqlReads=linkedMapOf<String,LongArray>()')
        end = text.index('val trace=File(', start)
        diagnostic = text[start:end]
        self.assertLess(text.index('report.put("budget_exceeded"'), start)
        self.assertLess(text.index('running.set(false);sampler.join(1000)'), start)
        for marker in ('sql_driver_timing_audit_ms', 'SYNTHETIC_POST_BUDGET_DRIVER_CALL_TIMING',
                       'readOnlyTicket {', 'assertEquals(rawProjection,MeasuredConsumptionStore.auditStagedDataset(profiled))',
                       'override fun getCount()', 'override fun moveToNext()', 'override fun getString(',
                       'override fun query(query:androidx.sqlite.db.SupportSQLiteQuery',
                       'sql_sha256', 'sql_prefix_truncated', 'cursor_calls', 'driver_ns',
                       'rankedSql.take(12)', 'sql_driver_omitted_templates', 'sql_driver_omitted_ns',
                       'check(sqlReads.size<256)', 'check(sqlReads.isNotEmpty())'):
            self.assertIn(marker, diagnostic)
        for forbidden in ('execSQL', 'args.toString()', 'args.contentToString()', 'bindArgs.toString()', 'setTransactionSuccessful'):
            self.assertNotIn(forbidden, diagnostic)

    def test_generation_keeps_full_large_contract_and_sqlcipher_only_in_test_source(self):
        prepare = module('prepare_ansm_native_performance')
        text = prepare.native_source(prepare.SOURCE.read_text())
        self.assertIn('src/androidTest/', str(prepare.TARGET))
        self.assertNotIn('robolectric', text.lower())
        self.assertNotIn('Room.inMemoryDatabaseBuilder', text)
        for marker in ('SupportOpenHelperFactory', 'val count=2000', 'val legacy=14600-count',
                       'full_encrypted_restore', 'repo.recordMeasuredUse', '5000.0',
                       '192L*1024*1024', '256L*1024*1024', '80L*1024*1024',
                       'physical_device_acceptance','NOT_RUN'):
            self.assertIn(marker, text)
        self.assertNotIn('setLargeHeap', text)
        self.assertIn(r'val numericPattern="""[0-9]+(?:\.[0-9]+)?"""', text)
        self.assertNotIn('fallbackToDestructiveMigration', text)

    def test_components_follow_original_budget_capture_and_do_not_replace_failure_gate(self):
        prepare = module('prepare_ansm_native_performance')
        text = prepare.native_source(prepare.SOURCE.read_text())
        self.assertLess(text.index('report.put("budget_exceeded"'), text.index('val components=JSONObject()'))
        self.assertLess(text.index('running.set(false);sampler.join(1000)'), text.index('val components=JSONObject()'))
        self.assertLess(text.index('components.put("status","COMPLETE")'), text.index('assertEquals("Diagnostic budgets;'))
        for name in ('raw_full_first_ms', 'cached_full_second_ms', 'cached_full_first_ms',
                     'raw_full_second_ms', 'raw_state_including_references_ms', 'raw_operation_chain_ms',
                     'cached_operations_with_setup_ms', 'cached_projections_with_setup_ms', 'stream_query_plan_audit_ms',
                     'decimal_regex_compile_12000_ms', 'decimal_regex_reuse_12000_ms'):
            self.assertIn(name, text)
        diagnostic = text[text.index('val components=JSONObject()'):text.index('components.put("status","COMPLETE")')]
        self.assertNotIn('execSQL', diagnostic)
        self.assertLess(text.index('val components=JSONObject()'),text.index('android.os.Debug.startMethodTracingSampling'))
        self.assertIn('finally{android.os.Debug.stopMethodTracing()}',text)
        self.assertNotIn('exceeded.clear', text)
        self.assertIn('assertEquals(rawProjection,MeasuredConsumptionStore.auditStagedDataset(raw))', diagnostic)

    def test_write_ticket_diagnostics_are_post_budget_read_only_and_keep_real_validation(self):
        prepare = module('prepare_ansm_native_performance')
        text = prepare.native_source(prepare.SOURCE.read_text())
        start = text.index('val components=JSONObject()')
        for name in ('write_ticket_capture_ms', 'write_ticket_validate_ms',
                     'sampled_write_ticket_capture_ms', 'sampled_write_ticket_validate_ms'):
            self.assertGreater(text.index('component("'+name+'")'), start)
        diagnostic = text[start:text.index('components.put("status","COMPLETE")')]
        for marker in ('SELECT total_changes()', 'PRAGMA schema_version',
                       'MeasuredUseIntegration.ticket(raw,requireNotNull(ReferenceStore.existingScope(raw)),m)',
                       'MeasuredUseIntegration.validate(raw,requireNotNull(diagnosticTicket))',
                       'assertEquals(beforeChanges,scalar("SELECT total_changes()"))',
                       'assertEquals(beforeSchema,scalar("PRAGMA schema_version"))'):
            self.assertIn(marker, diagnostic)
        self.assertNotIn('repo.referenceScope()', diagnostic)
        self.assertNotIn('recordMeasuredUse', diagnostic)
        self.assertLess(text.index('android.os.Debug.startMethodTracingSampling'),
                        text.index('component("sampled_write_ticket_capture_ms")'))
        self.assertLess(text.index('component("sampled_write_ticket_validate_ms")'),
                        text.index('finally{android.os.Debug.stopMethodTracing()}'))

    def test_portable_ticket_diagnostic_uses_identical_calls_and_fences(self):
        prepare = module('prepare_ansm_native_performance')
        text = prepare.native_source(prepare.SOURCE.read_text())
        helper = text[text.index('            // Measure the actual stale-write guard'):
                      text.index('            // Synthetic test only. Time driver calls')]
        fragment = (ROOT / 'docs/ansm-integration/p4/performance/write-ticket-diagnostic.fragment.kt.txt').read_text()
        self.assertTrue(fragment.endswith(helper))

    def test_projection_diagnostic_signature_matches_current_source_and_uses_uncaptured_path(self):
        prepare = module('prepare_ansm_native_performance')
        text = prepare.native_source(prepare.SOURCE.read_text())
        production = (ROOT / 'core/data/src/main/java/net/plainnotes/app/data/MeasuredConsumptionStore.kt').read_text()
        signature = re.search(r'private fun auditStagedProjection\(([^)]*)\)', production).group(1)
        arity = len(signature.split(','))
        selected = re.search(r'it.name=="auditStagedProjection" && it.parameterTypes.size==([0-9]+)', text)
        self.assertEqual(arity, int(selected.group(1)), 'Post-budget reflection must follow the real private signature')
        self.assertIn('projectionAuditor.invoke(MeasuredConsumptionStore,reads,dataset,uuid,presentations,null)', text)
        self.assertLess(text.index('report.put("budget_exceeded"'), text.index('val projectionAuditor='))
        self.assertIn('assertEquals(rawProjection,values)', text)

    def test_template_drift_fails_before_generating_a_misleading_native_test(self):
        prepare = module('prepare_ansm_native_performance')
        with self.assertRaises(ValueError):
            prepare.native_source(prepare.SOURCE.read_text().replace('sampler.join(1000);db.close()', 'other cleanup'))

    def test_collector_accepts_only_the_single_synthetic_report_path(self):
        collect = module('collect_ansm_native_performance')
        valid = '/sdcard/Android/data/net.plainnotes.app.data.test/files/ansm-performance/large-ten-year.json'
        self.assertEqual([valid], collect.report_paths([valid]))
        for invalid in ('/sdcard/health.json', valid+'.backup', valid.replace('data.test','data.attacker'),
                        valid.replace('large-ten-year.json','backup.pnbak'), valid+'/../health.json'):
            self.assertEqual([], collect.report_paths([invalid]))


class NativePerformanceExecutionTest(unittest.TestCase):
    def test_failed_test_still_collects_before_shutdown_without_becoming_success(self):
        from tempfile import TemporaryDirectory
        from types import SimpleNamespace
        runner = module('run_ansm_native_performance')
        with TemporaryDirectory() as directory:
            runner.ROOT = Path(directory)
            for test_status, collection_status in [(1,0),(0,1),(1,1),(0,0)]:
                calls=[]
                def execute(command, **kwargs):
                    calls.append(command)
                    return SimpleNamespace(returncode=test_status if len(calls)==1 else collection_status)
                self.assertEqual(test_status or collection_status,runner.run(execute))
                self.assertEqual(2,len(calls))
                self.assertIn('connectedDebugAndroidTest',calls[0][1])
                self.assertIn('-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true',calls[0])
                self.assertTrue(calls[1][1].endswith('collect_ansm_native_performance.py'))


class NativeAvdConfigurationTest(unittest.TestCase):
    def test_correct_vm_heap_property_is_unique_and_other_avd_fields_are_preserved(self):
        configure=module('configure_ansm_low_memory_avd').configure
        source='hw.ramSize=1536\nvm.heapSize=384\nhw.cpu.ncore=2\navd.ini.displayname=SYNTHETIC\nvm.heapSize=256\n'
        result=configure(source)
        self.assertEqual(1,result.count('vm.heapSize='))
        self.assertIn('vm.heapSize=192\n',result)
        self.assertIn('hw.ramSize=512\n',result)
        self.assertIn('hw.cpu.ncore=2\n',result)
        self.assertIn('avd.ini.displayname=SYNTHETIC\n',result)
        self.assertEqual(result,configure(result))


class NativeMetadataRecoveryTest(unittest.TestCase):
    def test_uninstall_safe_metadata_cannot_turn_incomplete_or_wrong_fixture_into_success(self):
        import json
        collector = module('collect_ansm_native_performance')
        report = {'environment': 'Native API26 SQLCipher synthetic fixture; emulator, not physical hardware',
                  'stage': 'full_audit', 'status': 'IN_PROGRESS'}
        prefix = '10-17 15:00:00.000 123 456 I ANSM_PERF: '
        observed = collector.log_report(prefix + json.dumps(report))
        self.assertFalse(collector.budget_pass(observed))
        report.update(status='DIAGNOSTIC_BUDGET_FAIL',api26_low_memory_acceptance='FAIL')
        self.assertFalse(collector.budget_pass(collector.log_report(prefix+json.dumps(report))))
        report.update(status='DIAGNOSTIC_BUDGET_PASS',api26_low_memory_acceptance='EMULATOR_BUDGET_PASS')
        self.assertTrue(collector.budget_pass(collector.log_report(prefix+json.dumps(report))))
        for text in (prefix+'{"environment":', prefix+json.dumps({'environment':'other fixture','status':'DIAGNOSTIC_BUDGET_PASS'})):
            with self.assertRaises(ValueError):
                collector.log_report(text)
        native = module('prepare_ansm_native_performance').native_source(module('prepare_ansm_native_performance').SOURCE.read_text())
        self.assertIn('android.util.Log.i("ANSM_PERF",report.toString())', native)


class NativeRuntimeConfigurationTest(unittest.TestCase):
    def test_runtime_changes_require_api26_emulator_and_root_before_setting_properties(self):
        runtime = module('configure_ansm_native_runtime')
        for bad in ('physical', 'not_qemu', 'wrong_api', 'not_root'):
            calls=[]
            def execute(command, **kwargs):
                calls.append(command)
                values={'get-serialno':'real-device' if bad=='physical' else 'emulator-5554',
                        'ro.kernel.qemu':'0' if bad=='not_qemu' else '1',
                        'ro.build.version.sdk':'29' if bad=='wrong_api' else '26',
                        '-u':'2000' if bad=='not_root' else '0'}
                return values.get(command[-1], '')
            with self.assertRaises(RuntimeError):
                runtime.configure(execute,lambda _:None)
            self.assertFalse(any('setprop' in c for c in calls))

    def test_real_properties_verified_after_framework_restart_and_budget_is_not_changed(self):
        runtime = module('configure_ansm_native_runtime')
        calls=[]
        def execute(command, **kwargs):
            calls.append(command)
            return {'get-serialno':'emulator-5554','ro.kernel.qemu':'1','ro.build.version.sdk':'26',
                    '-u':'0','sys.boot_completed':'1','dalvik.vm.heapsize':'192m','dalvik.vm.heapgrowthlimit':'192m'}.get(command[-1],'')
        runtime.configure(execute,lambda _:None)
        stop=calls.index(['adb','shell','stop'])
        self.assertLess(calls.index(['adb','shell','setprop','dalvik.vm.heapsize','192m']),stop)
        self.assertLess(stop,calls.index(['adb','shell','start']))
        self.assertIn(['adb','shell','getprop','dalvik.vm.heapsize'], calls[stop+1:])
