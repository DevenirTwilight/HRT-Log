# HRT Log — original research materials: actual GitHub archive status

**Updated 2026-10-10 after remote Git blob verification.** Research results must be *actually committed* to the repository, not merely uploaded to the conversation or summarized. [Binding project archive policy](../RESEARCH_ARCHIVAL_POLICY.md).

### Recovered original phase archives

**23 of 24** historical ZIP phases in `ARCHIVE_MANIFEST.md` have actual corresponding ZIP blobs under [source-packages](source-packages/). **21 original ZIPs are byte-identical to user uploads** (S–Z, AD–AI, AL–AO, AP, AQ, AS); **two AJ/AK ZIPs are redistributable copies** excluding a scanned copyrighted Figure 1, with preserved source code/results and explicit disclosure.

Actual user-provided phase ZIPs **now committed**: S, T, U, V, W, X, Y, Z, AD, AE, AF, AG, AH, AI, AJ (sanitized), AK (sanitized), AL, AM, AN, AO, AP, AQ, AS. The original historical source files are inside those ZIPs; they have not all been unpacked into separate top-level Git paths.

### Missing original / new reconstruction

**P2-AT original ZIP is lost.** Historical metadata: SHA-256 `a636beee77525043a69a8393d88d03b20d9ee14adf2e29c6995960b192b4c14a`, 390,066 bytes. Its preserved historic summary remains [here](reports/p2-at.md). A **new self-contained calculation reconstruction** was independently implemented from archived P2-AS/P2-X inputs with **8/8 tests passed** and committed [here](reconstructed/). Never identify the reconstruction as the original.

### Engineering / clinical gate

This phase import is on `research/p2-original-reports-and-source-handoff-20261010` (draft PR #7), stacked on PR #5, stacked on PR #4; **not merged**. No APK, production E2_SL parameters, Kotlin engine, database schema, personal medical records or raw OSF patient workbooks have been changed. Scientifically, new/independent person-level absolute E2 accuracy and long-tail validation remain unproven. P2-C is HOLD.

### Research integrity

Tests for newly supplied S/AP/AQ/AS: AP 9/9, AQ 32/32, AS 49/49; S 10/11 in *isolated* ZIP, with one missing GitHub P2-D/P2-E dependency causing an error rather than a numerical model failure. The original S ZIP is preserved exactly and must be tested again in a checked-out repository. Reconstruction P2-AT 8/8. Do not claim fresh Android integration CI was run from this commit without checking GitHub Actions.
