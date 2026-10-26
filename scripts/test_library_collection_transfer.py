import unittest

from run_library_collection_transfer import normalize_android_id


class GuestIdentityTest(unittest.TestCase):
    def test_hex_identity_is_numeric_and_not_fixed_width(self):
        self.assertEqual("000000000000000a", normalize_android_id("a"))
        self.assertEqual(normalize_android_id("abcdef012345678"),
                         normalize_android_id("0ABCDEF012345678"))
        self.assertNotEqual(normalize_android_id("1"), normalize_android_id("2"))

    def test_missing_zero_malformed_and_out_of_range_remain_rejected(self):
        for value in ("", "null", "0", "0000000000000000", "123456789abcdef00",
                      "not-an-id", "1\n", " 1", "Error: settings unavailable"):
            with self.subTest(value=value), self.assertRaises(AssertionError):
                normalize_android_id(value)
