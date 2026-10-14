# Privacy notice evidence table

Each statement in [`PRIVACY.md`](../PRIVACY.md) and the in-app notice (`privacy_notice_*` strings, `ui/PrivacyNotice.kt`) points to the code, guard or test it relies on. Paths are relative to the repository root. The table records engineering facts only; it is not a legal assessment.

| Statement | Source of truth | Guard / test |
| --- | --- | --- |
| No INTERNET permission | `app/src/main/AndroidManifest.xml` (no `<uses-permission>`), `core/reminder/src/main/AndroidManifest.xml` | `scripts/check_release_manifest.py` on the merged fullRelease manifest (CI `android` job); `PrivacyNoticeFactsTest.noInternetPermissionAndSystemBackupOff` |
| Declared permissions (notifications, exact alarm, boot; WorkManager wake lock, foreground service, network state; biometric) | Merged fullRelease manifest (`:app:processFullReleaseMainManifest`, verified 2026-10-14) | Manual check recorded here; INTERNET absence enforced by CI |
| No analytics, ads, telemetry, account | `gradle/libs.versions.toml`, module `build.gradle.kts` (no such dependencies) | Code review; release isolation check `scripts/check_attachment_release_isolation.py` for test-only code |
| SQLCipher database with Keystore-wrapped random key | `core/data/.../DatabaseAccess.kt` (random 32-byte key, AES-GCM wrap with Keystore alias, `noBackupFilesDir`) | Existing data tests (SQLCipher migration/foundation tests) |
| System backup and device transfer off | `AndroidManifest.xml` `allowBackup="false"`; `res/xml/backup_rules.xml`; `res/xml/data_extraction_rules.xml` | `PrivacyNoticeFactsTest.noInternetPermissionAndSystemBackupOff` |
| App lock stored as Argon2id hash under Keystore; separate from backup password | `app/src/main/.../security/AppLock.kt` (Argon2id 19 MiB/2/1, Keystore-encrypted `lock.bin`) | Existing app lock tests; notice does not reuse backup parameters |
| Notes disguise text not encrypted | `app/src/full/.../disguise/Shells.kt` (`shell_notes` SharedPreferences) | `PrivacyNoticeFactsTest.notesShellTextIsDisclosedAsUnencrypted` |
| Exports are plaintext, via SAF | `app/src/main/.../export/Exports.kt`, `ui/DataSection.kt` (`CreateDocument`), `ui/ExportPrivacyNotice.kt` | `ExportNoticeLayoutTest` |
| Backup: Argon2id 32 MiB / 3 / 1, AES-256-GCM, PNBAK1, serialization 9 | `core/data/.../DataTransfer.kt` (`BackupCodec`), `RecordBackupCompatibility.kt` (`SCHEMA = 9`) | `PrivacyNoticeFactsTest.backupKdfAndCipherMatchTheNotice` (fails if parameters change) |
| Backup includes recycle bin until purge; excludes private notes, shell text, settings, attachments | `DataTransfer.kt` `DOMAIN_TABLES` (includes `trash_item`, no `attachment_*`); `docs/disguise-privacy.md` | Backup/restore compatibility tests; attachment store `feature_enabled=0` guard |
| Imports only via file picker; Trans Memo copy deleted | `ui/DataSection.kt` (`OpenDocument`); `privacy/TransMemoImportCopies.kt` | `scripts/run_import_cleanup_restart.py` (device CI) |
| Neutral notifications; details only when enabled and unlocked | `core/reminder/.../ReminderCoordinator.kt`, `NotificationPrefs.kt` | `NotificationPrivacyTest` |
| Disguise limits | `app/src/full/.../disguise/Disguise.kt`; `docs/disguise-privacy.md` | `scripts/run_disguise_restart.py`; `ShellPrivacyBoundaryTest` (shells never show the notice or real name) |
| Delete all data scope | `NotesViewModel.wipeAll`, `ui/DataSection.kt` `WipeDialog` | Existing wipe tests |
| Notice is offline, complete and accessible in four languages | `ui/PrivacyNotice.kt`, `res/values*/strings.xml` | `PrivacyNoticeLayoutTest` (Robolectric API 35: 4 locales × 320/411dp × 1x/2x, light/dark, headings, WCAG AA 4.5:1 text contrast, no clipping, ≥48dp close, no links); `PrivacyNoticeOlderApiTest` (same assertions on API 26 and 29, zh-TW 320dp 2x dark); `TranslationsTest` |
| Not a diagnosis/dosing tool; estimates are conditional | Product contract (`docs/REQUIREMENTS.md`, PK reports) | Wording reviewed; no code change |

Known gaps recorded for follow-up (not claimed by the notice): `THIRD_PARTY_NOTICES.md` and the in-app licence text do not list BouncyCastle (runtime, used for Argon2id); the "Delete all data" action does not clear the `stock_alerts` preferences (dates keyed by internal ids, no medication names); real-device TalkBack runs are NOT_RUN (semantics assertions only); the notice UI tests run on Robolectric (JVM), not on the CI emulators.
