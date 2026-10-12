import copy, json, unittest
from pathlib import Path
from delivery_ledger import GRADLE, LEDGER, check, highest

CERT = "989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1"
BASE = {"application_id": "net.plainnotes.app", "signing_cert_sha256": CERT, "pre_policy": {"max_version_code": 25}, "deliveries": []}


def delivery(code, apk="a" * 64, **change):
    d = {"date": "2026-10-20", "version_code": code, "version_name": "0.2.1", "application_id": "net.plainnotes.app", "source_sha": "0123456789abcdef" * 2 + "01234567",
         "source_origin": "git", "source_dirty": False, "apk_sha256": apk, "signing_cert_sha256": CERT, "file_name": f"hrt-log-{code}.apk", "channel": "private"}
    d.update(change)
    return d


def ledger(*deliveries):
    result = copy.deepcopy(BASE); result["deliveries"] = list(deliveries); return result


GRADLE_26 = "defaultConfig { versionCode = 26; versionName = \"0.2.1\" }"


class DeliveryLedgerTest(unittest.TestCase):
    def test_repository_ledger_and_gradle_pass(self):
        check(json.loads(LEDGER.read_text()), GRADLE.read_text())

    def test_strictly_increasing_deliveries_pass(self):
        self.assertEqual(check(ledger(delivery(26), delivery(27, apk="b" * 64)), "versionCode = 27"), 27)
        self.assertEqual(highest(ledger(delivery(26))), 26)

    def test_reusing_or_lowering_version_code_fails(self):
        for bad in (ledger(delivery(25)), ledger(delivery(26), delivery(26, apk="b" * 64)), ledger(delivery(27), delivery(26, apk="b" * 64))):
            with self.assertRaisesRegex(ValueError, "not greater"):
                check(bad, "versionCode = 30")

    def test_other_application_id_or_certificate_fails(self):
        with self.assertRaisesRegex(ValueError, "applicationId"):
            check(ledger(delivery(26, application_id="net.plainnotes.app.debug")), GRADLE_26)
        with self.assertRaisesRegex(ValueError, "certificate"):
            check(ledger(delivery(26, signing_cert_sha256="f" * 64)), GRADLE_26)

    def test_untrusted_source_identity_fails(self):
        for change in ({"source_sha": "unknown"}, {"source_origin": "declared-unverified"}, {"source_dirty": True}, {"source_sha": "0123456"}):
            with self.assertRaisesRegex(ValueError, "clean git checkout"):
                check(ledger(delivery(26, **change)), GRADLE_26)

    def test_apk_hash_must_be_distinct_from_certificate_and_unique(self):
        with self.assertRaisesRegex(ValueError, "APK SHA-256"):
            check(ledger(delivery(26, apk=CERT)), GRADLE_26)
        with self.assertRaisesRegex(ValueError, "APK SHA-256"):
            check(ledger(delivery(26), delivery(27)), "versionCode = 27")

    def test_missing_field_fails(self):
        d = delivery(26); del d["apk_sha256"]
        with self.assertRaisesRegex(ValueError, "missing"):
            check(ledger(d), GRADLE_26)

    def test_source_version_code_below_last_delivery_fails(self):
        with self.assertRaisesRegex(ValueError, "below the last delivered"):
            check(ledger(delivery(26)), "versionCode = 25")


if __name__ == "__main__":
    unittest.main()
