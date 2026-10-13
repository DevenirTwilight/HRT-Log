import json
import unittest
from collect_package_history_evidence import collect, LANGUAGES, MARKER


class PackageHistoryEvidenceTest(unittest.TestCase):
    def log(self):
        events = []
        for language in LANGUAGES:
            cases = [("first-page", n, min(n, 20)) for n in (1, 20, 21, 100, 500)]
            cases += [("all-batches", 500, n) for n in range(20, 501, 20)]
            cases += [("completed-history", 500, 500), ("voided-first-page", 100, 20), ("completed-voided", 100, 100)]
            for kind, total, shown in cases:
                event = dict(sdk=35, width_dp=320, font_scale=2, locale=language, kind=kind,
                             total=total, shown=shown, composed_rows=min(shown, 3))
                events.append(MARKER + json.dumps(event))
        return "\n".join(events)

    def test_full_four_language_matrix_and_duplicate_logs(self):
        log = self.log()
        report = collect(log + "\n" + log)
        self.assertEqual(132, len(report["events"]))
        self.assertEqual(3, report["max_attached_package_rows"])

    def test_missing_final_batch_does_not_count_as_complete(self):
        lines = self.log().splitlines()
        with self.assertRaisesRegex(ValueError, "Missing completed evidence"):
            collect("\n".join(lines[:-1]))

    def test_eager_composition_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "bounded"):
            collect(self.log().replace('"composed_rows": 3', '"composed_rows": 500', 1))
