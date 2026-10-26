# Cross-app integration checkpoint — 2026-10-26

## Evidence and acceptance boundary

PR #126 final code/docs HEAD `5eed2d7f1e9b715abe5398a96442709aa3f8f89d` remains OPEN, auto-merge enabled. PR-event Android `40304785582` (all five jobs), Science `40304785567`, and low-memory `40304785563` passed. Low-memory attempt 1 included a real foreground LMK before KDF; attempt 2 passed. Earlier failures remain in the existing evidence directories. This is not a first-attempt pass.

A normal protected REST merge returned HTTP405: **5 of 6 required status checks are cancelled**. This proves the blocker is the cancelled duplicate push run `40304779789`, not a demonstrated missing reviewer approval. The earlier claim that an unattributed-author rule specifically caused the block was unproven and is withdrawn. Rerun only that cancelled workflow on the same SHA; do not change rules, authors, assertions, or resource limits. Default branch still `604f69a70a504b17d564a483bd7c3c671a31632a` until protected merge succeeds. No new acceptance is claimed for pending jobs.

## Supported formats

| Format | Version | Imported | Limits / exclusions |
|---|---|---|---|
| HRT Tracker / TransMTF-compatible JSON | official TransMTF meta.version 2 shape | Supported actual doses, ester/route context, supported labs, optional weight using existing wizard | Unix epoch hours; compound doseMG is not equivalent E2. Unsupported routes/removal/incomplete custom products reported. No unique app attribution from isomorphic JSON. |
| Trans Memo SQLite | validated database structure via existing reader | Existing medication/history/inventory/check-in choices and replacement transaction | Not TransMTF; source-specific review remains. |
| Mona | database23 | Supported actual intakes and original E2/T panels with immutable receipts | No scheduled-time substitution; unsupported units/forms, plans and stock reported; no reminders or inventory inference. |
| Featherline | envelope3 / snapshot7 | Supported actual-dose facts and original labs/panels with UUID receipts | Authenticated Argon2id/AES-GCM/gzip bounds unchanged; no equivalentE2Mg substitution, plans, stock, PK configuration. |
| Chrysalide | export5 | taken=true timestamped events, explicit target medication, NULL actual dose | No per-event dose inference; taken=false is not missed. Date-only labs and other collections explicitly not imported. Changed exports with local numeric IDs are conservatively conflicts. |

Existing detector matrix, scoped repository transactions, immutable receipt/trash/tombstone contracts and backup compatibility were exercised by the #126 final CI. Room19→20/business16 are additive and conditional; old backup read paths retained. This review reuses those results, not another execution. MED-02 remains OFF; outer backup cryptography and INTERNET permission unchanged.

## UI and report review

One unified SAF launch remains separate from native HRT Log restore. Each source retains its own preview and options; unsupported/encrypted/wrong-version paths cannot become an empty successful import. Mona/Featherline/Chry receipts report actual committed counts; HT/TM retain existing result summaries. No importer is an automatic synchronization/overwrite service. Source-ID replay and local deletion protections are source-specific; legacy HT/TM behavior is not claimed to have the identical receipt model as new sources.

Found two user-visible gaps: outdated settings description omitted Featherline/Chrysalide; tracker format label implied a unique originating app. Four-language description and compatible-format label corrected without changing persistent identity. Found Chry result OK bound to an empty callback: bind to clearDataJob and extend existing real SAF test to assert Idle after OK. These follow-up changes require their own exact-SHA CI and are not covered by #126 green results.

Existing scrollable dialogs and role-labelled explicit selection are reused. Physical device, manual TalkBack, and a complete manual four-language/320dp/200%-font/dark-mode visual matrix remain NOT_RUN. Automated emulator passes are not substituted for these checks.

## Candidate ranking (public evidence, not user-count estimates)

1. **NoMTF HRT Recorder**: repo commit `28597d90fd073acea27cfe123d8e8f4885699328`, 10 stars, latest releases v2.1.24/v2.1.22 total 279 asset downloads observed. Downloads are neither unique users nor confirmed active users. README documents JSON/CSV/HTML exports; JsonCodec emits meta.version2, events/labResults and native HRT Recorder metadata. Some shape overlaps TransMTF, but extra antiandrogen/record-only/dose/plan semantics need independent audit before any compatibility claim. No LICENSE found and GitHub license is null. Ranked first for further format audit, not production implementation; independent interoperability work must not copy source. New source identity and clinical mappings unresolved.
2. **Oyama HRT Cadence**: `SmirnovaOyama/Oyama-HRT-Cadence`, commit `9ea6a6c5d7b72962d05ced08229be2af9dfedd28`, MIT, 1 star, last push 2026-09-28. Export service confirms CSV/PDF with epoch-hour history; complete JSON/encrypted backup/version policy still needs direct audit. Featherline lists OYAMA, NOMTF and TRANSMTF_COMPATIBLE import sources; that does not establish HRT Log support. No verified active-user count. Defer adapter pending archive and unit/identity evidence.
3. **Dollfin**: `lillelilje/harbour-dollfin`, commit `4ace7cb9f29343f8d2e873444ce63ef2aa4f1a69`, README GPL-3.0-or-later (GitHub reports GPL-3.0), 0 stars, no GitHub releases observed, last push 2026-10-10. `src/core/backup.cpp` emits format=dollfin-backup, version1, schemaVersion, exportedAt and tables. Table schema/version, historical dose identity and unit semantics require audit; export function can omit a failed table query, so collection closure cannot be assumed. Independent parser possible but no demonstrated migration demand yet. Defer.

Evidence URLs: each repository's default branch README; NoMTF `app/src/main/kotlin/com/nanxin/hrtrecorder/JsonCodec.kt`; Dollfin `src/core/backup.cpp`; Oyama `src/services/export.ts`; Featherline `data/importer/ExternalImportModels.kt`. No third-party implementation copied. No new adapter, Room migration, or additional domain introduced. Next deliverable is a bounded NoMTF format/semantics audit with synthetic vectors, only if demand warrants it; no claim of current support.

## Protected merge result

#126 merged normally as `6a197dfb9af26b09e8ef22bfaf143f128358e874` after cancelled push run40304779789 attempt2 passed all five jobs. No review or ruleset bypass was necessary. Follow-up #127 is now based on accepted default branch; final integration-source checks remain pending. Earlier OPEN/PENDING sections above describe the investigation checkpoint, not the current #126 status.
