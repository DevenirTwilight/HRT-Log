#!/usr/bin/env python3
"""Reuse the existing real-force-stop harness; v1 gates remain mandatory and unchanged."""
from run_library_restore_restart import main, ROOT
if __name__ == "__main__":
    main("LibraryCollectionRestartAndroidTest", ROOT / "core/data/build/reports/med02-collection-restart", "synthetic-collection-restart-ready")
