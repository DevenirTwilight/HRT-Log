# France : instantané BDPM indépendant (ANSM-01)

Version `fr-bdpm-2026-10-15.1`. Source : [Base de Données Publique des Médicaments](https://base-donnees-publique.medicaments.gouv.fr/),
ANSM / HAS / UNCAM. Date affichée par le site : **29/09/2026** ; extraction et vérification
technique : **15/10/2026**. Le PDF de format est révisé le 13/10/2026. La date affichée n'est
pas un horodatage individuel des exports. Les SHA-256 identifient exactement chaque fichier acquis.

Réutilisation : sélection de lignes, décodage explicite, structuration et comptages d'emballages
non ambigus ; un court extrait factuel du RCP 6.5 d'Oestrodose. Aucune caution, certification,
affiliation ou recommandation de l'ANSM, de la HAS ou de l'UNCAM.

**Conflit de version de licence, non résolu :** le pied de page BDPM renvoie à
[Licence Ouverte 2.0](https://github.com/etalab/licence-ouverte/blob/master/LO.md), mais le
[contrat PDF lié au téléchargement](https://base-donnees-publique.medicaments.gouv.fr/docs/telechargement/licence_bdpm.pdf)
indique « Cette licence est une version 1.0 de la Licence Ouverte » (octobre 2011).
Les deux documents ont été lus ; leurs empreintes sont conservées dans `manifest.json`.
Les données officielles ne sont pas réétiquetées MIT. La source et sa date sont conservées.
La version applicable doit être clarifiée avant intégration Android et avant réutilisation
de textes cliniques complets. Ce dépôt n'affirme pas que le conflit est résolu.

## Files / 文件

|File|Purpose|
|---|---|
|manifest.json|Snapshot version, scope, attribution, dates, licence conflict, input SHA-256|
|source-rows.json|Exact decoded official rows, original file encoding, line number, row hash, full download hash|
|page-evidence.json|58 actual HTTP retrievals, response hashes, product headings, observed RCP/Notice panel IDs; one packaging quote|
|catalog.json|Generated offline reference resource: 58 CIS products, 64 composition rows, 96 CIP presentations|
|validation-report.json|Deterministic integrity report, generator/input/output hashes, unknowns/conflicts and release blockers|

This is a **scoped reference resource, not an Android feature or a complete French catalogue**.
No Room entity, user medication, intake, stock transaction, reminder, PK configuration or historical
snapshot is changed. No network is used during generation or validation. The application does not
read these files yet. No clinical RCP/Notice summary or prescribing instruction is included.

## Field mapping / 字段与来源

Column numbers below are 1-based. `source` on each record identifies the exact file/line/hash.
All original names, strengths and denominators remain French. Codes are strings, including leading zeros.

|Source|Columns|Generated meaning|
|---|---|---|
|CIS_bdpm.txt|1/2/3/4|CIS / official product name / form / routes (semicolon separated, never collapsed)|
|CIS_bdpm.txt|5/6/7/8|AMM status / procedure / marketing declaration / initial AMM date (not end date)|
|CIS_bdpm.txt|9/11|Original BDPM flag / holder text|
|CIS_CIP_bdpm.txt|1/2/3/4/5/6/7|Parent CIS / CIP7 / label / presentation status / marketing declaration / declaration date / CIP13|
|CIS_COMPO_bdpm.txt|1–8|CIS / pharmaceutical element / substance code / name / strength / denominator / SA-or-fraction / linking group|
|CIS_CIP_Dispo_Spec.txt|1–8|CIS / optional CIP13 / supply code / label / start / update / restoration date / ANSM link|
|Retrieved product page|h3, RCP/Notice panel IDs|Page identity and separate document entry availability, not verification of all clinical content|
|Oestrodose RCP 6.5|Exact packaging paragraph|64 actuations; 1.25 g gel and 0.75 mg estradiol per actuation, independently typed|

Unused official columns (prices, reimbursement, EU number, monitoring) are retained in source rows
but not exposed as verified application fields. No prescription-level claim is derived here.
`FT` occurs in the actual composition file whereas the format PDF describes `ST`; the mismatch
is reported, not silently corrected. SA and FT are separate related descriptions, **not additive doses**.

`VERIFIED_OFFICIAL_DOWNLOAD` means a field was read from the identified official export;
`VERIFIED` on page identity/documents means the expected heading/panel was observed. Neither
means independent medical review, current pharmacy availability, regulator endorsement or approval to ship.
Clinical content remains `UNVERIFIED` until ANSM-04's separate content review.

Authorization, marketing and supply are independent. ARCHIVED/REPEALED do not erase historical identity.
An active presentation can have a stop-of-marketing declaration. Missing supply rows mean **UNVERIFIED**,
not no shortage. The downloaded scope has zero supply rows; no claim about current availability follows.
The source format's stated general scope does not justify rejecting actual archived entries found in it.
Absence from this selected snapshot never establishes discontinuation. End-of-AMM dates and physical
production status are unknown unless separately sourced.

`package_quantity` is optional and uses decimal strings. TABLET, CAPSULE, PATCH, GEL_G,
PUMP_ACTUATION and ESTRADIOL_MG are distinct units. Only narrow unambiguous labels are parsed;
sachet multiplication is explicitly marked `DERIVED_LABEL_MULTIPLICATION`. Unknown labels stay null.
No tablet splitting permission is inferred from a decimal or from `sécable`.
The three pump quantities describe nominal product packaging, not absorbed dose, usable pump yield,
priming loss or the user's actual consumption. There is **no stock conversion/allocation API** here.

## Reproduce offline / 离线复现

From repository root, Python 3.11+ standard library only:

```sh
python3 scripts/bdpm_reference.py --check
python3 -m unittest discover -s scripts -p test_bdpm_reference.py -v
```

To regenerate both outputs from the committed captured evidence:

```sh
python3 scripts/bdpm_reference.py
git diff --exit-code -- docs/reference/fr/catalog.json docs/reference/fr/validation-report.json
```

Existing Android CI already discovers `scripts/test_*.py`; no workflow/ruleset/Gradle change is needed.
The checksum validates bytes, not the truth or freshness of a source. A maintainer must review official
changes before accepting a new version. Historical resources must remain identifiable; future user references
need their own stable identity + snapshot storage design, not a lookup against a changing current catalogue.

## Acquisition / 重新核实

Networking is a **maintainer-only explicit acquisition step**, never an application/build action.
Follow [the download page](https://base-donnees-publique.medicaments.gouv.fr/telechargement) links to the
four named text files and the format PDF. Keep original bytes outside the repository. Record HTTP results,
final URL, retrieval UTC timestamp, byte count and SHA-256. Download product pages for the selected CIS
and write these observations as `pages.json` beside `<CIS>.html`; fields are the retrieval metadata in
`page-evidence.json` (the capture step independently derives headings/panels/quote).
Also keep `download.html`, `license.pdf` and the linked Etalab text as `etalab.md`.

`python3 scripts/bdpm_reference.py --capture /path/to/downloads` reproduces this **dated v1 capture**
and rejects different displayed update/retrieval dates. Future refreshes require explicit version/date/scope
review and new captured evidence; do not relabel a fresh acquisition as the old snapshot. Fetch failures
must be represented as UNVERIFIED, with no stale document link or inferred clinical field.

Only selected official rows and limited page evidence are committed, not full web pages, PDFs or all market
exports. Offline regeneration therefore verifies the captured subset, not membership in a newly downloaded
full export; full source hashes permit comparison if those dated bytes are available. Official sites may
change; byte-identical re-download is not promised. No personal data, private signing files or APKs are inputs.
