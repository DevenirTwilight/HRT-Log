# P2-AU deterministic 64-clock retrospective robustness audit

**Research only.** Real, already-exposed VNC54 paired observations were processed privately. The committed aggregate contains no participant rows or identifiers, and time schedules are hypothetical design points, **not observed behavior or probabilities**.

- [Report (Chinese)](REPORT.md)
- [Aggregate results](aggregate.json)
- [Python analysis](../../../../tools/pk-research/p2au_clock_grid_audit.py) — imports the existing P2-AR-REAL shape kernels and VNC54 loader.
- [Synthetic/public aggregate tests](../../../../tools/pk-research/test_p2au_clock_grid_audit.py)

To rerun the **private** source analysis (authorized local environment only):

```bash
unzip -p docs/pk-research/p2/history-2026-10-10/source-packages/P2-AS-research-source.zip P2-AS/inputs/p2x-conditional-ensemble-results.json > /tmp/p2x.json
unzip -p docs/pk-research/p2/history-2026-10-10/source-packages/P2-AS-research-source.zip P2-AS/inputs/legacy_source_snapshot.json > /tmp/legacy.json
python tools/pk-research/p2au_clock_grid_audit.py \
  --private-vnc54 '/PRIVATE/Supplementary table.xlsx' \
  --p2x-json /tmp/p2x.json \
  --legacy-json /tmp/legacy.json \
  --out-aggregate /tmp/p2au-aggregate.json
cmp docs/pk-research/p2/p2au-clock-grid/aggregate.json /tmp/p2au-aggregate.json
```

Source `Supplementary table.xlsx` SHA-256 must be `038a935f699ddadc9f0d843e125e5ee8047132773d1d6485085d6302066867dd`; private data must **not** be uploaded to GitHub. The script fails closed if input hashes, patient counts or frozen M2 selection change.

Public CI, with no private workbook, runs:

```bash
python -m unittest discover -s tools/pk-research -p 'test_p2ar_real_visit_audit.py' -v
python -m unittest discover -s tools/pk-research -p 'test_p2au_clock_grid_audit.py' -v
```

`P2-AU` does not estimate individual PK curve error and cannot approve clinical predictions or dose changes.
