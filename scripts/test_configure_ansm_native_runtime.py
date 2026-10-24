#!/usr/bin/env python3
"""Transport acknowledgement never substitutes for the observed rooted synthetic device."""
import contextlib
import io
import subprocess
import unittest
from configure_ansm_native_runtime import configure


class Executor:
    def __init__(self, uid="0", after="emulator-5554", root_error=False, wait_error=False):
        self.uid, self.after = uid, after
        self.root_error, self.wait_error = root_error, wait_error
        self.calls, self.serial_reads = [], 0
        self.properties = {"dalvik.vm.heapsize": "64m", "dalvik.vm.heapgrowthlimit": "64m", "sys.boot_completed": "1"}
        self.wait_timeout = None

    def __call__(self, command, text=True, timeout=None):
        args = tuple(command[1:])
        self.calls.append(args)
        if args == ("get-serialno",):
            self.serial_reads += 1
            return "emulator-5554" if self.serial_reads == 1 else self.after
        if args == ("root",):
            if self.root_error:
                raise subprocess.CalledProcessError(1, command, output="adb: unable to connect for root: closed")
            return "adbd is already running as root"
        if args == ("wait-for-device",):
            self.wait_timeout = timeout
            if self.wait_error:
                raise subprocess.TimeoutExpired(command, timeout)
            return ""
        if args == ("shell", "id", "-u"):
            return self.uid
        if args[:2] == ("shell", "getprop"):
            return {"ro.kernel.qemu": "1", "ro.build.version.sdk": "35"}.get(args[2], self.properties.get(args[2], ""))
        if args[:2] == ("shell", "setprop"):
            self.properties[args[2]] = args[3]
            return ""
        if args == ("shell", "start"):
            self.properties["sys.boot_completed"] = "1"
            return ""
        if args == ("shell", "stop"):
            return ""
        raise AssertionError("Unexpected command: " + repr(args))


class RuntimeReconnectTest(unittest.TestCase):
    def run_configure(self, fake):
        with contextlib.redirect_stdout(io.StringIO()):
            configure(execute=fake, pause=lambda _: None, expected_api="35")

    def assert_unmodified(self, fake):
        self.assertFalse(any(args[:2] == ("shell", "setprop") for args in fake.calls))

    def test_ack_or_closed_transport_still_requires_observed_same_root(self):
        for failed_ack in (False, True):
            with self.subTest(failed_ack=failed_ack):
                fake = Executor(root_error=failed_ack)
                self.run_configure(fake)
                self.assertEqual(30, fake.wait_timeout)
                self.assertEqual(2, fake.serial_reads)
                self.assertIn(("shell", "id", "-u"), fake.calls)
                for name in ("dalvik.vm.heapsize", "dalvik.vm.heapgrowthlimit"):
                    self.assertEqual("192m", fake.properties[name])

    def test_nonroot_or_unknown_uid_refuses_before_any_property_change(self):
        for uid in ("2000", ""):
            with self.subTest(uid=uid):
                fake = Executor(uid=uid, root_error=True)
                with self.assertRaises(RuntimeError):
                    self.run_configure(fake)
                self.assert_unmodified(fake)

    def test_changed_device_refuses_before_any_property_change(self):
        fake = Executor(after="emulator-5556", root_error=True)
        with self.assertRaises(RuntimeError):
            self.run_configure(fake)
        self.assert_unmodified(fake)

    def test_reconnect_timeout_refuses_before_any_property_change(self):
        fake = Executor(root_error=True, wait_error=True)
        with self.assertRaises(subprocess.TimeoutExpired):
            self.run_configure(fake)
        self.assertEqual(30, fake.wait_timeout)
        self.assert_unmodified(fake)


if __name__ == "__main__":
    unittest.main()
