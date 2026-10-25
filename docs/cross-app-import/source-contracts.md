# Cross-application format contracts — checked 2026-10-25

These are independently written interoperability observations, not copied application code. No upstream parser, crypto implementation, UI, license text, assets or real medical export is redistributed. Current source module uses existing `org.json`/JDK APIs only; no project dependency was added. This note does not assert official endorsement or turn a compatible file format into an upstream affiliation.

## Exact source and license observations

| Application | Reviewed revision | License observed | Primary wire source |
|---|---|---|---|
| Mona | `8bc965fd340ee0758608be047039e79ea056b2eb` | AGPL-3.0-only source headers and AGPL LICENSE | [backup_service.dart](https://github.com/mona-hrt/mona/blob/8bc965fd340ee0758608be047039e79ea056b2eb/lib/services/backup_service.dart), [current tables](https://github.com/mona-hrt/mona/blob/8bc965fd340ee0758608be047039e79ea056b2eb/lib/services/db/db_tables.dart) |
| Featherline | `c9a417deb2ebee29448899134951e9ddc380b6aa` | GPLv3 LICENSE | [public backup protocol](https://github.com/mkx173/Featherline/blob/c9a417deb2ebee29448899134951e9ddc380b6aa/docs/backup-format.md), [snapshot wire fields](https://github.com/mkx173/Featherline/blob/c9a417deb2ebee29448899134951e9ddc380b6aa/app/src/main/java/com/mkx/hrttracker/data/backup/BackupSnapshot.kt) |
| TransMTF HRT Tracker | `8c9abdde36494719dd8b55be03a0aadc79269ccb` | MIT LICENSE | [ExportModal](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/blob/8c9abdde36494719dd8b55be03a0aadc79269ccb/src/components/ExportModal.tsx), [encryption wire contract](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker/blob/8c9abdde36494719dd8b55be03a0aadc79269ccb/src/utils/dataEncryption.ts) |
| Chrysalide | `73ea9436c037ea966285fd0b46934394f06ef3a0` | MIT LICENSE | [export](https://github.com/kushiemoon-dev/chrysalide/blob/73ea9436c037ea966285fd0b46934394f06ef3a0/src/lib/db-export-import.ts), [types](https://github.com/kushiemoon-dev/chrysalide/blob/73ea9436c037ea966285fd0b46934394f06ef3a0/src/lib/types.ts) |

Facts/field names and original synthetic samples are used to write independent readers; the upstream implementations are not linked or compiled into HRT Log. Compatibility adds no new dependency notice requirement by itself because no upstream code is shipped. Any future source reuse/dependency needs a separate compatibility/NOTICE check; this is not a general license grant for those repositories.

## Mona: reviewed database23 only

**State: typed parser implemented and tested; production user import NOT_IMPLEMENTED.** No selector may label Mona as supported until a transactional writer/provenance/preview/report exists. Upstream's own import range is4..23, but that does not mean HRT Log supports every historical schema. Current wire reader explicitly rejects4..22 and future24+.

`metadata` contains `app_version` string, `database_version` integer23 and `export_date` ISO local/offset timestamp. The export timestamp may be local without an offset; it remains raw provenance and never becomes an actual clinical event time. `data` contains all four raw SQLite row arrays. Row TEXT columns containing JSON remain **JSON-encoded strings**, not nested JSON objects. All source row IDs are preserved as exact integers represented in the typed model as strings; duplicate IDs within a table reject the file. No assumption of a stable installation UUID is possible: the file does not export one. Cross-file ID collision/change handling must therefore detect content conflicts instead of overwriting local edits.

| Source collection/field | Typed reader behavior / migration boundary |
|---|---|
| medication_intakes.id | Stable raw source row identity; not a target database integer ID. |
| takenDateTime, takenTimeZone | Original UTC ISO string, full-precision `Instant`, explicit source zone. Missing actual time is NOT_TAKEN, never fabricated as taken/missed. A zone-less/invalid actual time or unknown zone remains invalid/unknown, never device-local default. Submillisecond precision is preserved in the source model; a future millisecond-only writer must disclose/reject unsupported precision rather than silently round it. |
| takenDose, molecule | Exact decimal source text + bounded BigDecimal; `molecule` is encoded JSON with name, massUnit, optional rateUnit. Name is not an approved PK compound mapping. No conversion or salt/ester equivalence is inferred. |
| dosingBasis | `mass` uses source massUnit; `releaseRate` uses source rateUnit. Rate is not mass or patch count. |
| administrationRoute, ester | Original strings preserved. Known wire enum values recognized; unknown values explicitly reported. Route/ester never come from the current target medication. |
| scheduleId, medicationSupplyItemId | Explicit source relationships retained; unresolved foreign ID is MISSING_REFERENCE. Does not create a target plan/container. |
| scheduledTime, notes, genericSupplyItemIds, placements, wastedAmount, deadSpace, injectionType | Raw source detail retained as typed optional provenance. Wasted/dead-space amounts are not silently added to the actual administered dose. Source detail unsupported by a writer must appear in its report. |
| blood_tests | Original ID/time/zone/notes; E2 and T each preserve reported decimal text + unit, not derived canonical values. E2 units pg/mL or pmol/L; T ng/dL, ng/mL or nmol/L. Unknown units flagged, not guessed. A reported zero remains reported zero, with no claim that detection limit or biological true zero is known. No detection method is invented. |
| medication_schedules | ID/name/dose/start date/molecule/route/ester/dosingBasis/scheduling JSON retained as REFERENCE_ONLY. Not actual intake or an automatically enabled reminder. |
| supply_items | ID/type/name/molecule/route/ester/basis/form and original total/used/per-unit/amount details retained as REFERENCE_ONLY. No exact stock identity/capacity/consume entries are created. |
| unknown table/field | Explicit per-collection count or per-row field issue; never counted as successfully migrated. |

`Mona.Export` exposes metadata/intakes/bloodTests/schedules/supplies/issues; it is not a target write plan or a generic second database. Bounds are checked via PR-A `CrossAppInput.validateJson` before JSONObject construction and for encoded nested objects, with aggregate100000-row cap and finite decimal precision/scale. Invalid row field types reject rather than coercing numbers into dose strings. Parsing emits no logs or files.

**Actual persistence dependency:** existing `SchemaGuards` permit `dose_record.origin` only APP/IMPORT_TM/IMPORT_HT/AUTO_MISSED. Mona must NOT be mislabeled IMPORT_HT, and its identity must not be hidden in an unrelated free-text field. A future PR-B writer needs an explicit non-destructive provenance/origin and ordinary-business-backup compatibility design, coordinated with current Room17 and MED-02. Parser-only success does not authorize or prove such a migration. Local parser testing used the new PR-A `CrossAppInput.kt` as an unstaged compile dependency; this branch intentionally does not duplicate/own that shared file.

Targeted synthetic `MonaTest`:12 tests, zero failures/errors/skips. Covers actual schema23 string-encoded rows, microsecond original time, DST overlap, missing actual time, unknown/invalid zone/route/ester, rate-vs-mass, E2/T reported-unit preservation, source references, read-only plan/stock, invalid decimals, duplicate IDs/version/type rejection, explicit unknown collections/fields and pre-DOM nesting rejection. JVM reader tests are not Android or persisted-import acceptance. Final integration must rerun on its own exact source.

## Featherline: independent protocol implementation inputs

**State: protocol checked and independent synthetic crypto vector validated; HRT Log Featherline reader/import NOT_IMPLEMENTED.** Current writer envelope3/snapshot7. Upstream reader accepts envelope2/3 and snapshots2..7, but that does not automatically authorize claiming all those versions as HRT Log-supported. Future envelopes/snapshots must reject explicitly. Source's cap128MiB JSON is not permission to raise HRT Log's lower safe limits.

Envelope3 starts with ASCII `HRTBKP1`. Byte7 is3;8KDF identifier2(Argon2id);9cipher1(AES256GCM);10compression1(gzip). Big-endian64 original JSON bytes at11; big-endian32 time-cost/memoryKiB/parallelism/hashBytes at19/23/27/31. Bytes35/36 declare salt/nonce lengths16/12. Salt occupies37..52, nonce53..64, ciphertext plus16-byte tag begins65. **All65 header bytes are AAD.** Default KDF3/65536KiB/1/32, Argon2id version1.3. Header parameters are untrusted: validate supported exact tuples/salt/nonce/declared lengths and resource limits before invoking KDF. Do not choose default parameters after an unknown identifier/version, allocate unbounded attacker-requested memory, or expose unverified plaintext.

AES-GCM authentication must complete before gzip parsing or any clinical data is published. Gunzip needs both an absolute output bound and an exact match to authenticated declared JSON length. Wrong password and authenticated-content corruption are not reliably distinguishable: report authentication failure, not a definite password diagnosis. Legacy envelope2 lacks the gzip byte and declared-length field; do not treat arbitrary HRTBKP1 content as current3.

Snapshot has `app.packageName="com.mkx.hrttracker"`, `snapshotVersion`, `exportedAtEpochMillis`, medicines, medicationGroups, medicationLogs, bloodTests/custom analytes, profile/settings and optional journal fields. Medicine UUID relationships must be validated before reading dependent logs. Log UUID/time (`appliedAtEpochMillis`, `appliedAtTimeZoneId`) is distinct from scheduledForIso. **`equivalentE2Mg` is PK-equivalent input, not original administered compound mass.** Preserve actual dose shape (fractions/volume/weight/count, optional actual-amount delta), explicit medicine identity and application type; missing trustworthy conversion evidence means review/refusal. PATCH_OFF is removal, never a new consumption. Do not import upstream calibration dispositions/settings into HRT Log models or alarms.

### Independent synthetic envelope3 vector

This is an original interoperability vector derived from the public byte specification, **not an upstream golden fixture or a valid complete clinical snapshot**. The payload deliberately includes `syntheticVectorOnly`. It tests only password UTF8, default Argon2id, AAD, AES-GCM and bounded gzip. Java21/BouncyCastle1.80.2 generated Argon2id and JCA ciphertext; Python `cryptography`/OpenSSL independently authenticated/decrypted, gunzipped and verified exact declared byte length. Neither test tool is added to the app. Full source-format semantics and final Android KDF memory still need their own tests.

```text
password: synthetic-interoperability-密码
plaintext UTF8: {"snapshotVersion":7,"app":{"packageName":"com.mkx.hrttracker"},"syntheticVectorOnly":true}
header hex: 485254424b503103020101000000000000005b00000003000100000000000100000020100c000102030405060708090a0b0c0d0e0f101112131415161718191a1b
synthetic derived key hex: c81aeaea287c2fa589267ae2fa336fee567a1add0a62a49a9bee599649e79c06
envelope base64: SFJUQktQMQMCAQEAAAAAAAAAWwAAAAMAAQAAAAAAAQAAACAQDAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscylxUB5teeFNmEyGFitWVdtfCISf/+7zYjSUSAm4+Grora9KEByG+z3YuPqkEyUMCBbx8FL5A/GUf34k0vCtkvyh8LTPuzWdH3j1hxf6fmjjADhTDFo44JaXb+9OUKZ+1TVDiEtxz3EtmISucA5iSneppc+zK
envelope bytes:183
SHA256:e707e13fa8ca454303df793aa84fafaf46b18288013940691dbbbc78f9a8ea15
```

All passwords/keys/bytes above are intentionally public synthetic test material, never device credentials. The deterministic salt/nonce must never be reused for real exports.

## Existing HRT Tracker = TransMTF family

The current independent HRT Log `HrtTracker` reader already documents the exact same TransMTF repository/revision. They are not two unrelated source formats. Plain export fields are weight/events/labResults/gelProducts and optional version metadata; older bare-event arrays remain a separately validated legacy shape. One format family should be displayed, not two ambiguous detectors.

TransMTF's optional encrypted JSON bundle is a different envelope: `{encrypted:true,iv,salt,data}` with Base64 fields, PBKDF2-SHA256/100000, AES256GCM,16-byte salt/12-byte IV and UTF8 password. It is neither Trans Memo SQLite nor Featherline Argon2/HRTBKP1. Encrypted TransMTF support remains NOT_IMPLEMENTED until version/structure/authentication/resource and migration acceptance are provided; do not guess a password or pass it as a native HRT Log restore.

## Chrysalide: version5 and a historical-dose gap

Plain exportversion5 has medications/medicationLogs/bloodTests/appointments plus physical progress, reminders, profile, non-private journal, objectives/milestones/treatmentChanges/practitioners/acts/actTodos and exportedAt. Exported Date fields are ISO timestamps. The application is separate from the Chrysalide association/Trans Memo and must not be recognized just by a similar name.

Medication rows contain current dosage/unit and optional route refinements; log rows contain ID/medicationId/timestamp/taken/optional scheduledTime/doseIndex/notes/sideEffects/applicationZone **without a historical actual-dose/unit snapshot**. Do not reconstruct all past actual doses from today's medication row or infer missed from `taken=false`. Such rows require explicit user historical information or remain unsupported/review-only. Blood results carry marker/value/unit and panel date; appointments carry date/optional time/type/practitioner/location/note/reminder detail. User/profile/goals and diary are not diagnoses. Document-photo/base64 medical originals are out of scope and must not activate MED-02.

## Source receipts

SHA256 of raw inspected public files (raw copies remain outside the MIT repository):

| File | SHA256 |
|---|---|
| Mona backup_service.dart |5803d06ccd9716c7a9553ea0c0b901a2400a0b8788d7b1d8726fc078ec8a8ee6|
| Mona db_tables.dart |9d7b3bc0fe211cc99489d19075c08cc4a052b4fe26aa4ec16ea43e25c81cc99d|
| Mona app_database.dart |39455ab07f64be810f603f711787ec6a03d3cffcfed8558e34ece1595896e402|
| Featherline backup-format.md |e3d226175f331620f0ade6c42d3ad9159fed142d780461f6a95b54c8ea0bedbe|
| Featherline BackupSnapshot.kt |b3faa6ca90c9bf62e36ccf18f9b367643e3cb3da8b4a4cd231edeabc7b466370|
| TransMTF ExportModal.tsx |1f79cbf87560278a0b37453fd62c3a22d428e708bf6068e87655a96bdc9ba815|
| TransMTF dataEncryption.ts |127cf99e56582cdf9be6cf1bbb6fa6d31566a0ba72839cbee4f87c97ba3c8671|
| Chrysalide db-export-import.ts |d718ed20590811907bf772916e1d3a1df94592386c04e60595200abef1a9ed2b|
| Chrysalide types.ts |476192ecc74bc957884b8e056de68a16401b6621deea30f33fe6bf4b70331938|
