# P2-AW sampling design: frozen model geometry, no human measurements

- [Full methodological report (Chinese)](P2-AW-REPORT.md)
- [Deterministic model-only output](p2aw-geometry.json)
- [Source](../../../../tools/pk-research/p2aw_sampling_design.py)
- [20 synthetic/frozen-source unit tests](../../../../tools/pk-research/test_p2aw_sampling_design.py)

Inputs: actual previously archived original [P2-AS ZIP](../history-2026-10-10/source-packages/P2-AS-research-source.zip), SHA-256 `f88b6acdda3e4de27dc1fb0fb8d0b62b2e51ac0b2d85347f3219eb4656662b40`. Internal M2 and Legacy snapshots are separately SHA-256 checked. All files from public version-controlled research; **no original OSF individual XLSX, medical observation rows, patients or keys**.

Recompute in repository checkout:

```bash
python tools/pk-research/p2aw_sampling_design.py \
  --archive docs/pk-research/p2/history-2026-10-10/source-packages/P2-AS-research-source.zip \
  --output /tmp/p2aw-geometry-recomputed.json
cmp docs/pk-research/p2/p2aw-identifiability-design/p2aw-geometry.json /tmp/p2aw-geometry-recomputed.json
python -m unittest discover -s tools/pk-research -p 'test_p2aw_sampling_design.py' -v
```

The CLI refuses to overwrite an existing result. The CI uses a fresh temporary output path, checks the committed reference byte-for-byte and runs all 20 tests. No Android app/production code or personal calibration is involved. The Pearson/affine distance is **not statistical power or clinical prediction accuracy**. A future real-world or trial design requires human subject ethics, approved collection protocol, laboratory constraints and prior locked analysis protocol; this is a mathematical option audit only.
