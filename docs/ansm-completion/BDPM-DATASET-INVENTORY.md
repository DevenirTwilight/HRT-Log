# BDPM dataset inventory — 2026-10-17

Status: SOURCE_RETRIEVAL_VERIFIED; distribution OWNER_BLOCKED. Metadata only: retrieved source files and PDF bodies are not bundled in Android or committed here.

The download page and actual HTTP responses were observed on the stated retrieval dates. Page update labels are dataset-specific; retrieval time is not a server generation time. A missing row does not establish discontinuation, sale availability, absence of shortage, or absence of additional monitoring.

| Dataset | Page date / generation | Rows | Raw field counts | Retrieval |
|---|---|---:|---|---|
| CIS_bdpm.txt | Fichier des spécialités                                                           (Date de mise à jour : 29/09/2026, 3101 Ko) | 15883 | {'12': 15883} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |
| CIS_CIP_bdpm.txt | Fichier des présentations                                                           (Date de mise à jour : 16/10/2026, 4053 Ko) | 20907 | {'13': 20907} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |
| CIS_HAS_SMR_bdpm.txt | Fichier des avis SMR de la HAS                                                           (Date de mise à jour : 29/09/2026, 4522 Ko) | 15547 | {'6': 15547} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |
| CIS_HAS_ASMR_bdpm.txt | Fichier des avis ASMR de la HAS                                                           (Date de mise à jour : 29/09/2026, 4497 Ko) | 10117 | {'6': 10117} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |
| CIS_GENER_bdpm.txt | Fichier des groupes génèriques                                                           (Date de mise à jour : 29/09/2026, 1191 Ko) | 10737 | {'5': 10737} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |
| CIS_CPD_bdpm.txt | Fichier des conditions de prescription et de délivrance                                                           (Date de mise à jour : 29/09/2026, 1305 Ko) | 28541 | {'2': 28532, '1': 9} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |
| CIS_CIP_Dispo_Spec.txt | Fichier des ruptures de stocks                                                           (Date de mise à jour : 12/10/2026, 151 Ko) | 710 | {'8': 710} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |
| CIS_MITM.txt | Fichier des médicaments d&#039;intérêt thérapeutique majeur                                                           (Date de mise à jour : 13/10/2026, 1581 Ko) | 7855 | {'7': 7855} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |
| CIS_InfoImportantes.txt | Fichier des Informations importantes (génération en direct) | 11279 | {'4': 11279} | RETRIEVED_NOT_DISTRIBUTION_APPROVED |

SHA-256, byte counts, final official URLs and individual retrieval timestamps are in `additional-dataset-metadata.json`. All nine requests returned HTTP 200. The previously fetched composition file is in `downloaded-source-metadata.json`.

## Unresolved source and distribution gates

- Download page footer links Etalab 2.0; the download-linked licence PDF states version 1.0. Both were retrieved anew; this is still an unresolved source inconsistency, not a legal determination of applicable terms. No project MIT reclassification or owner distribution approval is inferred.
- Format PDF uses SA/ST in the composition description and SA/FT in its field table. Actual composition tokens were SA 26915, FT 5524, ST 0. Preserve the literal token; no FT↔ST alias or mass addition is authorized.
- CIS column 12 describes additional monitoring independently of authorization, marketing declaration and shortages. The newly retrieved raw values are Oui 508 / Non 15375 across 15883 rows. These are raw dataset observations, not verification of any particular product or an approved in-app black-triangle claim. Missing values in other snapshots must remain UNKNOWN.
- The legacy catalog does not cover that monitoring field. Its frozen data is unchanged. Any future claim needs product-specific provenance, a reviewed field mapping, date and separately approved distribution.
- Live important-information download has no verified server generation timestamp: `dynamic_generated_at_utc` remains null. Do not substitute retrieval time or claim realtime availability.
- CPD has nine one-field rows among 28541 raw rows. An importer must report and preserve uncertainty rather than silently assuming all rows have two fields.

Software using synthetic fixtures can proceed. Official clinical body text, product-specific statuses, and production data packaging remain OFF until the separate source/reuse/owner gates are resolved.
