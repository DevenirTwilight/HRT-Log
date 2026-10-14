# HRT Log privacy notice

Last updated: 2026-10-14. Applies to the HRT Log Android app (`net.plainnotes.app`, `full` flavor) built from this repository's default branch. This notice describes how the app handles data. It is not a legal opinion or a certification. The same facts are shown inside the app (About → Privacy → *Read the full privacy notice*) in English, French, Simplified Chinese and Traditional Chinese, bundled in the app and readable offline. Every statement below is mapped to code, guards or tests in [`docs/privacy-evidence.md`](docs/privacy-evidence.md).

## What the app is for

HRT Log is a personal record and reminder app for the doses, stock, appointments, lab values and notes you enter. It does not diagnose, choose doses or judge whether a treatment works. Concentration curves are conditional estimates based on published studies, not measurements of your own blood levels.

## No account, no internet

- No account, no analytics, no advertising and no telemetry SDKs.
- The app does not request `android.permission.INTERNET` (checked in CI against the merged release manifest).
- Permissions it does declare: notifications, exact alarms and boot completed (reminders); wake lock, foreground service and network state, declared by the AndroidX WorkManager scheduling library; biometric/fingerprint for optional unlock. Without the internet permission the app cannot open network connections.
- The About screen's external link (a support association's website) only opens your browser when you tap it.

## Storage on this phone

- Records are kept in a SQLCipher-encrypted database. Its random 32-byte key is wrapped with a non-exportable Android Keystore key and stored in the app's no-backup storage.
- Android system backup and device-to-device transfer are disabled for the app (`allowBackup="false"` and exclude-all backup/data-extraction rules).
- The app lock code is stored only as an Argon2id hash, itself encrypted with an Android Keystore key. It controls who can open the app and is separate from any backup password. These are not the backup's parameters.
- Text typed into the Notes disguise screen is stored in plain app preferences, not encrypted. Do not put sensitive information there.

## Exports are not encrypted

CSV, PDF report and visit pack exports are plain files. You choose where to save them in Android's file picker (Storage Access Framework). That place may belong to a cloud service or another app. Once saved, HRT Log cannot control, protect or take back those copies.

## Encrypted backups (`.pnbak`)

- Encrypted with the password you choose: Argon2id (32 MiB memory, 3 passes, parallelism 1) derives a 256-bit key; AES-256-GCM encrypts the backup (format `PNBAK1`, structured record serialization 9).
- Contains your structured records, including recycle-bin items until you purge them.
- Does not contain private notes, Notes disguise text, settings or medical file attachments. The app does not store attachments: that feature is not available to users.
- A forgotten backup password cannot be recovered.
- A future complete medical backup (with attachments) would be a separate, newly reviewed format.

## Imports

Trans Memo and HRT Tracker files are read only after you pick them in Android's file picker. A Trans Memo file is copied into the app's private storage to be read, and that copy is deleted afterwards.

## Notifications

Reminder text is neutral by default. The medication name appears only if you turn on details, and only while the phone is unlocked. Android settings and notification headers can still show the app's real name.

## Limits of disguise mode

Disguise mode changes the launcher icon and name and hides the app behind a calculator or notes screen. It does not protect against root access, a compromised system, someone inspecting the installed app, or Android settings that list the real app. See [`docs/disguise-privacy.md`](docs/disguise-privacy.md).

## Deleting data

"Delete all data" removes the records database and its key on this phone. Uninstalling removes the app's data. Files you exported, backups you saved and copies held by other apps or cloud services are not removed.

## If the app changes

If medical file storage, sync, sharing or advice features are ever added, this notice will be updated and reviewed before they are released, together with the regulatory assessment in [`docs/med-eu-privacy-readiness-v2.md`](docs/med-eu-privacy-readiness-v2.md). That document is an engineering condition matrix. It does not claim that GDPR, MDR, GPSR, CRA, EAA or EHDS are inapplicable.

## Contact

Questions or corrections: open an issue at https://github.com/DevenirTwilight/HRT-Log. Do not post personal health data in issues.
