# P2-AT: recovered calculation, original lost

**Historical original is unavailable.** P2-AT's historical complete ZIP was `HRT_Log_P2-AT_Legacy与M2直接比较完整包.zip`, 390,066 bytes, SHA-256 `a636beee77525043a69a8393d88d03b20d9ee14adf2e29c6995960b192b4c14a`. That exact ZIP has **not** been found or imported.

A new, independent reimplementation, [P2-AT-RECONSTRUCTED_2026-10-10.zip](P2-AT-RECONSTRUCTED_2026-10-10.zip), was produced on 2026-10-10 from original preserved P2-AS study inputs and frozen M2 15-candidate data. This is a **34,698-byte new ZIP**, SHA-256 `7dc26d75a1e3b3ade8d0c1942f8b03f9c657dc109295f16552230c7e3b08cf50` and Git SHA-1 `d368e126c289b40a30271891bf443862c8aa87f1`.

It includes:
- unchanged P2-AS research implementation (Legacy mathematical transcription and normalized M2 kernel) and its original input JSON snapshots;
- a new transparent `tools/p2at_reconstruct.py` with frozen score-based candidate selection and study-specific error calculations;
- 8 new unit tests (8/8 passed in the local environment), results JSON, SHA-256 manifest and README.

**Reproduced mathematical comparisons:** Price 1997 Legacy RMSE≈0.11665 versus M2 0.00903–0.05313; Rosano 1997 Legacy≈0.29361 versus M2 0.03885–0.10840; Komesaroff incremental ratio observed≈0.21132, Legacy≈0.642, M2≈0.11732–0.22325; hypothetical 1 mg q6h for 30 days then stop for 24 hours Legacy≈0.000894 and M2≈0.000832–0.167938 (dimensionless). These use exposed/previously evaluated human group data and *synthetic* repeat dosing, not a new blind cohort. They cannot establish individual mg→pg/mL calibration or authorize production replacement.

If the original historical ZIP is later found, archive it alongside this reconstruction without overwriting either. See historical [AT summary](../reports/p2-at.md) and [permanent archive policy](../../RESEARCH_ARCHIVAL_POLICY.md).
