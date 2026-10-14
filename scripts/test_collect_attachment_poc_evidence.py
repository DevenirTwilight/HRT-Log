import unittest
from collect_attachment_poc_evidence import parse


class ParsePocEvidenceTest(unittest.TestCase):
    def test_observations_preserve_real_numbers_and_distinguish_bounds(self):
        rows = parse('I MED02APOC: large_pdf api=35 cipher_bytes=123 bitmap_bytes=456 scratch_bound=196624 pss_after_kib=12345\nI MED02APOC: image mime=image/png bytes=999\nother unrelated text')
        self.assertEqual('large_pdf', rows[0]['kind'])
        self.assertEqual(12345, rows[0]['pss_after_kib'])
        self.assertEqual(196624, rows[0]['scratch_bound'])
        self.assertEqual('image/png', rows[1]['mime'])
        self.assertNotIn('peak_heap', rows[0])
