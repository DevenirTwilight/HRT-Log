#!/usr/bin/env python3
"""Synthetic regressions guarding historical research archival identity."""
import copy
import unittest
from check_historical_research_catalog import CATEGORIES, audit, load_original_manifest

class HistoricalArchiveTests(unittest.TestCase):
    def test_real_50_record_catalog_and_all_archives(self):
        result=audit()
        self.assertEqual(result["catalog_records"], 50)
        self.assertEqual(result["source_zip_sha256_verified"], 23)
        self.assertEqual(result["scientific_gate"], "P2-C HOLD")
    def test_24_historical_source_hash_rows(self):
        self.assertEqual(len(load_original_manifest()), 24)
    def test_backfills_cannot_be_originals(self):
        self.assertEqual(CATEGORIES["BACKFILL_NOT_ORIGINAL"],2)
        self.assertEqual(CATEGORIES["RECONSTRUCTED_NOT_ORIGINAL"],1)
    def test_no_default_external_validation_status(self):
        self.assertEqual(CATEGORIES["DRAFT_PR_NOT_DEFAULT"],1)
        self.assertEqual(CATEGORIES["GATE_HOLD"],1)

if __name__ == "__main__":
    unittest.main()
