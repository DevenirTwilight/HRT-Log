# P2 historical study — actual GitHub import state (2026-10-10)

This file deliberately distinguishes **original research uploaded to the conversation**, **source code stored in GitHub**, and **report-only records**. Uploaded files are not GitHub blobs unless the tree explicitly contains them. File integrity checks do not imply repository import or clinical validation.

## What this PR actually adds

- Restores the original full, user-provided Markdown reports for **P2-AN, AO, AP, AQ, AS**. The previous GitHub documents were shorter archival summaries and have now been replaced with the full texts.
- Preserves the chronological distinction: P2-AO/AP originally failed to access OSF, whereas P2-AQ subsequently obtained VNC54/TCRUW spreadsheets. The individual Excel records are **not included** in this repository.
- Does not silently copy restricted human-level data, scanned third-party figures, copyrighted journal PDFs, or unreleased APKs.

## Independently supplied original complete ZIP packages, not yet committed

The user uploaded complete standalone packages for the following **19/24** independent research stages that appear in `ARCHIVE_MANIFEST.md`:

- **T, U, V, W, X, Y, Z** (7 packages)
- **AD, AE, AF, AG, AH, AI, AJ, AK, AL, AM, AN, AO** (12 packages)

All 19 were previously file-integrity checked in the chat against the separately archived SHA-256 manifest. Their original ZIP binary files and their complete embedded program/data/test/figure trees **are not in this Git tree**. Some P2 code predating these independent packages exists at different repository paths and does **not** count as importing the original artifacts.

Other known stages in `ARCHIVE_MANIFEST.md` whose complete ZIP has **not** been supplied in the current uploads: **S, AP, AQ, AS, AT** (5 packages). Individual Markdown reports of S, AP, AQ, and AS have been provided. The current PR includes full Markdown of AP, AQ, and AS; S is already present in the archive index. AT continues to have an earlier report/summary only.

Research **P2-AA** and **P2-AR** have new analytic backfill documents in the parent PR; those are not historical recovered full original archives. **P2-C** remains an unapproved production adoption gate.

## Next import gate for actual complete-source archival

To claim the complete research has been imported, for each standalone ZIP:
1. Extract source scripts, tests, result tables and non-sensitive figures under a stable phase-specific path, and preserve the original file names and evidence SHA-256.
2. Exclude raw individual-level OSF health records, identifiable content, private keys, proprietary publisher PDFs/figure scans and other material without redistribution rights.
3. Commit the approved source tree (not merely links or hashes) and verify GitHub blob content hashes against the archived files.
4. Run the stage-specific tests and, where practical, independent output re-computation on the committed tree.
5. Open the PR separately, without changing production `pk-engine`, models, database, signing material, or packaged app.

**Status: REPORTS_IMPORTED; ORIGINAL_PACKAGE_SOURCE_IMPORT_PENDING; PRODUCTION_REPLACEMENT_NOT_AUTHORIZED.**
