# Mona schema audit — read-only preparation, 2026-10-25

Official upstream HEAD rechecked: `8bc965fd340ee0758608be047039e79ea056b2eb`; public AGPL source is inspected outside the MIT repository. No upstream implementation is copied. This branch retains the earlier independently written parser prototype; it is not the production PR-B and is not accepted integration. PR123 must pass and merge first. Current accepted default is1ddcf431 (Room17). Mona production, migrations and native acceptance NOT_IMPLEMENTED.

Historical schema facts from db/historical_schemas.dart and upgrades, used to bound initial support to23 only:

| DB version | Relevant observed boundary |
|---|---|
|4|scheduledDateTime, dose, moleculeJson/administrationRouteName/esterName; lab units not separate columns|
|5|intake supplyItemId added|
|6|supply amount/type replaces quantity|
|7|intake notes; separate blood estradiolUnit/testosteroneUnit|
|8|scheduledTime replaces scheduledDateTime; it is time-of-day, not an absolute planned instant|
|9|same observed columns; no claim of equivalent data semantics|
|10|genericSupplyType|
|11|wastedAmount|
|12|deadSpace|
|13|molecule/administrationRoute/ester/takenDose/scheduling field names|
|14|placements replaces side|
|15|same observed columns; no automatic compatibility claim|
|16|medicationSupplyItemId + genericSupplyItemIds replace supplyItemId|
|17|same observed columns; no automatic compatibility claim|
|18|blood units embedded in encoded value; separate unit columns removed|
|19|supply deliveryForm|
|20|blood notes|
|21|dosingBasis; supply dosePerUnit replaces concentration|
|22|injectionType|
|23|text encoding repair even though column sets match22|

Mona's own4–23 recovery claim is not this app's guarantee. Reject4–22 and24+ until explicitly reviewed/implemented. Current export has metadata.app_version/database_version/export_date and four raw table arrays. Molecule and lab values are JSON-encoded TEXT strings, not nested objects. Clinical timestamps must use actual UTC takenDateTime or blood dateTime plus the explicit source IANA zone. Never derive clinical time from scheduledTime, ID creation milliseconds, export_date or current system zone.

Controller evidence: actual intake takenDose is saved independently before stock usage adds wastedAmount/deadSpace. Do not add those to actual dose or reconstruct historical dose from supply. DosingBasis mass and releaseRate are different quantities; patch load and release rate cannot be substituted. Known raw massUnit is mg, E2 rateUnit µg/day. Unknown/custom units require explicit refusal, not guessed conversion. Esters include cypionateSuspension and must remain distinct from cypionate. Molecule/ester/route reference fields do not establish a complete HRT Log PK profile.

Blood UnitValue uses decimal-string value and serialized unit: E2 pg/mL or pmol/L, T ng/dL/ng/mL/nmol/L. Import one value per analyte, original unit; no false dual-unit duplicate, ranges or captured model estimate. Reject/report precision the target cannot honestly express. Schedules/supply are counted as unsupported; no new active plan, reminder or stock consumption.

Storage preflight: Room17 dose origin only permits APP/IMPORT_TM/IMPORT_HT/AUTO_MISSED, dose source_record_key is available, but labs have no source receipt/hash. Do not impersonate another importer or use PK/needs_review/editable notes as authoritative identity. Before production persistence, resolve a small explicit source-receipt/true-origin compatibility design, migration/version and ordinary/collection-backup preservation. No Schema/backup change is approved or implemented by this read-only note. Keep the earlier prototype's standalone tests distinct from production migration/native acceptance.
