# P2-AU — background profiling and negative-control audit (2026-10-11)

[Full Chinese research report](P2-AU-REPORT.md) · [Aggregate research result](p2au-aggregates.json)

This is a new **retrospective sensitivity study** expanding P2-AR-REAL. It adds a mathematically trivial zero-carryover negative control, an explicit maximum background fraction constraint, and all-five-clock robustness bounds. Original VNC54 individual rows have **not** been committed. All dose clocks and upper bounds are analyst assumptions, not individual patient records or measured endocrine baselines.

Source: [`tools/pk-research/p2au_background_profile.py`](../../../../tools/pk-research/p2au_background_profile.py). No new PK calibration or candidate selection; it imports the frozen P2-AR shape algorithms. Public synthetic tests: [`test_p2au_background_profile.py`](../../../../tools/pk-research/test_p2au_background_profile.py).

For authorized **private** offline replay, obtain original VNC54 XLSX from the rights holder and frozen P2-X/Legacy source JSONs from archived P2-AS ZIP; check readme paths in [P2-AR-REAL](../p2ar-real/README.md). Then execute:

```bash
python tools/pk-research/p2au_background_profile.py \
  --vnc54-xlsx /PRIVATE/Supplementary\ table.xlsx \
  --p2x-frozen-json /PRIVATE/p2x-frozen.json \
  --legacy-snapshot-json /PRIVATE/legacy-snapshot.json \
  --out-aggregate /PRIVATE/p2au-new-aggregate.json
```

Compare the resulting JSON SHA-256 with the public aggregate. Refuses unknown hashes, unmatched cohort, invalid frozen candidates or overwriting existing output. Public CI executes only the 13 no-human-data tests:

```bash
python -m unittest discover -s tools/pk-research -p 'test_p2au_background_profile.py' -v
```

**Not external validation; not a patient concentration model. No P2-C replacement authorization.**
