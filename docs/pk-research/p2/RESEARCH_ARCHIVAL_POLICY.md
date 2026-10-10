# Mandatory research-result archival / 科研成果必须实际入库

**Project instruction (effective 2026-10-10):** Every completed HRT Log PK research stage must end in a **real, verifiable GitHub branch/commit and reviewable PR**. Research is **not complete** merely because a ZIP was uploaded to a chat, a report was read, automated tests passed, a summary was written, or a SHA-256 was registered.

## Required before declaring a phase archived
1. Commit the *actual authorized, non-sensitive* source scripts, tests, input/output snapshots, report, figures and version/manifests, either as unpacked files or as an authentic phase-specific archive committed as a Git blob.
2. Confirm the remote tree contains the actual committed files; validate original SHA-256 and remote Git Blob SHA-1 against trusted local bytes. Make the destination path, branch, commit SHA, and PR URL visible.
3. Distinguish stages: `VERIFIED_LOCAL_ONLY`, `GIT_BRANCH_COMMITTED`, `PR_OPEN`, `MERGED_DEFAULT`, and `RECONSTRUCTED_NOT_ORIGINAL`. **Never** call an unmerged draft PR a default-branch merge. State what tests were run and whether source is self-contained.
4. Do not put identifiable patient data, raw OSF individual spreadsheets, confidential health records, credentials, protected third-party scans or other unlicensed content into a public repository. Keep a privacy-preserving audit trail and content hashes instead; provide a secure approved storage plan for original restricted bytes. Report any redaction explicitly.
5. For a lost original archive, retain its historical hash/size and study evidence, reconstruct from authoritative inputs where possible, and label the new work `RECONSTRUCTED_NOT_ORIGINAL`; never silently substitute a new ZIP for the lost one.
6. Keep science and release gates separate: an archived or computationally reproducible exploratory model is **not** clinical external validation and **never** authorizes changing the production E2_SL engine, app units, or HRT doses.

## Status as of 2026-10-10

- **23/24 historical independent research ZIPs** are present in [the GitHub branch](history-2026-10-10/source-packages/) (21 unchanged and two figure-redacted AJ/AK copies).
- P2-AT historical original ZIP is lost. A new reproducible [P2-AT calculation reconstruction](history-2026-10-10/reconstructed/) has been committed **separately** with 8 passing tests. It does not replace the lost original artifact.
- Full late-stage reports for AN/AO/AP/AQ/AS reside in `history-2026-10-10/reports/`.
- These are in the GitHub study branch/PR, **not merged** into the repository default branch as of this record. The production clinical replacement gate remains HOLD.
