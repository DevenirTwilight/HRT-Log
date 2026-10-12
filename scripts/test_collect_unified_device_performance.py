import copy
import json
import unittest

from collect_unified_device_performance import PREFIX, parse_report


class DeviceMeasurementCollectionTest(unittest.TestCase):
    def setUp(self):
        self.report = dict(fixture='synthetic-60-doses-over-30-days', sdk=35, abis=['x86_64'],
                           chart_points=2001, compute_ms=123.5, process_pss_before_kib=1000,
                           process_pss_after_kib=1200, native_pss_after_kib=100, dalvik_pss_after_kib=200,
                           measurement_limit='Synthetic Debug emulator only; not hardware/FPS/clinical accuracy.')

    def line(self, report):
        return PREFIX + json.dumps(report)

    def test_valid_tagged_report_survives_uninstall_without_reading_private_storage(self):
        self.assertEqual(self.report, parse_report('--------- beginning of main\n' + self.line(self.report) + '\n'))

    def test_missing_duplicate_and_remote_error_are_failures(self):
        for text in ['', 'run-as: unknown package: net.plainnotes.app', self.line(self.report) + '\n' + self.line(self.report)]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse_report(text)

    def test_nonfinite_memory_and_malformed_abi_fail_closed(self):
        for key, value in [('compute_ms', float('nan')), ('compute_ms', float('inf')),
                           ('process_pss_after_kib', 0), ('chart_points', 2002), ('abis', '[x86_64]')]:
            report = copy.deepcopy(self.report); report[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                parse_report(self.line(report))
