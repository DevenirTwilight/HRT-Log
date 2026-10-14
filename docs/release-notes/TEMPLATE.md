<!-- Reusable release-notes template (PRIVACY-P0, 2026-10-14). Copy to docs/release-notes/<version>.md before drafting a
release. Not itself a release: nothing has been published from this file. Keep every privacy statement consistent with
PRIVACY.md and docs/privacy-evidence.md; if a release changes data handling, update those first. -->

# HRT Log <versionName>

<date> · `versionName <x>` · `versionCode <n>` · Android 8.0+ · `net.plainnotes.app` · source `<commit>`

## Changes

- …

## Intended use

Personal record and reminder app. It does not diagnose, choose doses or judge whether a treatment works. Concentration
curves are conditional literature-based estimates, not measurements.

## Privacy

- Data handling unchanged since the previous release / changed as follows: … (if changed, PRIVACY.md and the in-app
  notice were updated in commit `<commit>`).
- No `INTERNET` permission (release manifest check: `<CI run URL>`).
- Exports are plain files; `.pnbak` backups are encrypted (format and parameters as in PRIVACY.md). Medical file
  attachments: not available / <state>.
- Full notice: PRIVACY.md and About → Privacy in the app (offline, four languages).

## Install / upgrade

Signature, upgrade path and backup advice: …

## Verification

CI runs, test counts, NOT_RUN items and checksums: …
