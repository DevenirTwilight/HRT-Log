# P2 research source packages — actual GitHub Git objects

Archived and updated 2026-10-10. This directory holds the real **ZIP bytes**, not just SHA-256 text. Each archive contains its phase-specific scripts, source/result data, tests, figures, reports and audit notes as originally delivered (except the explicitly marked redactions). Git blob SHA-1s were verified against the uploaded package bytes before the commit was constructed.

| Phase | ZIP | Bytes | Integrity |
|---|---|---:|---|
| P2-S | [P2-S-research-source.zip](P2-S-research-source.zip) | 11976 | Original ZIP, byte-identical; SHA-256 `05e550d9e274883f8a3bd549af523ad77b8a4b66da7387d250949783efee855a` |
| P2-AP | [P2-AP-research-source.zip](P2-AP-research-source.zip) | 13387 | Original ZIP, byte-identical; SHA-256 `cc24c3aceb5270d7453d01ad150ae6dd5e17dc985e87a85df91b8ca838904300` |
| P2-AQ | [P2-AQ-research-source.zip](P2-AQ-research-source.zip) | 63852 | Original ZIP, byte-identical; SHA-256 `657d6eb1df8cb694c6e03a321862ae610ea150b13e810a9cf3d9986fbfb6e63a` |
| P2-AS | [P2-AS-research-source.zip](P2-AS-research-source.zip) | 335589 | Original ZIP, byte-identical; SHA-256 `f88b6acdda3e4de27dc1fb0fb8d0b62b2e51ac0b2d85347f3219eb4656662b40` |
| P2-AD | [P2-AD-research-source.zip](P2-AD-research-source.zip) | 470825 | Original ZIP, byte-identical |
| P2-AE | [P2-AE-research-source.zip](P2-AE-research-source.zip) | 933259 | Original ZIP, byte-identical |
| P2-AF | [P2-AF-research-source.zip](P2-AF-research-source.zip) | 357994 | Original ZIP, byte-identical |
| P2-AG | [P2-AG-research-source.zip](P2-AG-research-source.zip) | 327032 | Original ZIP, byte-identical |
| P2-AH | [P2-AH-research-source.zip](P2-AH-research-source.zip) | 207245 | Original ZIP, byte-identical |
| P2-AI | [P2-AI-research-source.zip](P2-AI-research-source.zip) | 240312 | Original ZIP, byte-identical |
| P2-AJ | [P2-AJ-research-source.zip](P2-AJ-research-source.zip) | 205858 | Redistributable copy; see below |
| P2-AK | [P2-AK-research-source.zip](P2-AK-research-source.zip) | 299832 | Redistributable copy; see below |
| P2-AL | [P2-AL-research-source.zip](P2-AL-research-source.zip) | 173980 | Original ZIP, byte-identical |
| P2-AM | [P2-AM-research-source.zip](P2-AM-research-source.zip) | 175045 | Original ZIP, byte-identical |
| P2-AN | [P2-AN-research-source.zip](P2-AN-research-source.zip) | 250415 | Original ZIP, byte-identical |
| P2-AO | [P2-AO-research-source.zip](P2-AO-research-source.zip) | 70351 | Original ZIP, byte-identical |
| P2-T | [P2-T-research-source.zip](P2-T-research-source.zip) | 14059 | Original ZIP, byte-identical |
| P2-U | [P2-U-research-source.zip](P2-U-research-source.zip) | 29119 | Original ZIP, byte-identical |
| P2-V | [P2-V-research-source.zip](P2-V-research-source.zip) | 42989 | Original ZIP, byte-identical |
| P2-W | [P2-W-research-source.zip](P2-W-research-source.zip) | 163480 | Original ZIP, byte-identical |
| P2-X | [P2-X-research-source.zip](P2-X-research-source.zip) | 241911 | Original ZIP, byte-identical |
| P2-Y | [P2-Y-research-source.zip](P2-Y-research-source.zip) | 97627 | Original ZIP, byte-identical |
| P2-Z | [P2-Z-research-source.zip](P2-Z-research-source.zip) | 110723 | Original ZIP, byte-identical |

**21/23** ZIPs are byte-identical to the user-uploaded ZIP and match `../ARCHIVE_MANIFEST.md` SHA-256. **P2-AJ / P2-AK** deliberately exclude `Price_1997_Figure_1_原始扫描裁剪.png`, an image cropped from a published journal scan. Their other source files and results remain; each ZIP includes `REDISTRIBUTION-EXCLUSIONS.md`. The original archived `SHA256.json` still describes the user's full original (including the non-redistributed image) and therefore is not a manifest of the sanitized ZIP. The user retains the original uncensored ZIP locally.

Sanitized copy SHA-256: P2-AJ `e2992e331d84ca9d3a96e170d9d6248bfda37371f72a17cbc86f2c6a430ba51d`; P2-AK `9f724af0c031c631fb0d5c70e9c9010a51b503be6b9c57783faff3ef2d86f05a`.

Git object integrity check (example): P2-X blob SHA-1 = `9272472550dc18319463e71b5d22eca972dfc3ea`; P2-AE blob SHA-1 = `ebd123224770c536b3cddd1f95399622c51200c3`. All 23 original-package Git SHA-1 values were checked before tree attachment.

Not supplied as original complete standalone ZIP in this conversation: **P2-AT only**. The original AT ZIP is lost but its historical summary is archived and a new, non-original [AT reconstruction](../reconstructed/README.md) is committed. P2-AA and P2-AR retrospective analytic backfills are documented separately; P2-C model adoption remains unapproved.

**Status update:** P2-S, AP, AQ, AS are now also present as exact, byte-identical original ZIPs with validated local-vs-remote Git blob hashes. S requires P2-D/P2-E input JSON in the repository for a fully standalone rerun; its isolated 11-test run had 1 missing-dependency error, not a demonstrated numerical disagreement. AP 9/9, AQ 32/32 and AS 49/49 tests passed in a fresh unpacking. **Safety / scientific boundaries:** no OSF VNC54/TCRUW original individual XLSX is archived here; no private patient logs, signing keys, APKs, or production parameters have been imported. These reproducible research packages do not imply clinical validation or authorization to change individual pg/mL predictions.

**Reproducing:** download a named ZIP, verify SHA-256 against `../ARCHIVE_MANIFEST.md` for an unchanged ZIP, extract into a separate folder, and run the package-specific README commands. For AJ/AK, the excluded journal image means image-dependent source verification may require the user's private original archive or lawful source access. GitHub CI for this PR checks repository integration only; older standalone test results must not be presented as having been freshly run by PR CI.
