# P2-AR real VNC54 visit audit (2026-10-11)

**Privacy boundary:** this directory intentionally contains *only* aggregated statistics and a full research report. The raw source OSF `.xlsx`/`.zip` contains participant-level medical records and is not republished. No individual row, person code, quasi-identifier or actual health-data sample can be reconstructed from the committed files alone.

- [Research report](P2-AR-REAL-REPORT.md)
- [Anonymized aggregate results](p2ar-real-aggregate.json)
- Code: [`tools/pk-research/p2ar_real_visit_audit.py`](../../../../tools/pk-research/p2ar_real_visit_audit.py)
- Synthetic, no-human-data tests: [`tools/pk-research/test_p2ar_real_visit_audit.py`](../../../../tools/pk-research/test_p2ar_real_visit_audit.py)

### Reproduction, for authorized local source holders only

1. Verify the original VNC54 XLSX SHA-256: `038a935f699ddadc9f0d843e125e5ee8047132773d1d6485085d6302066867dd`.
2. Extract (only locally) the already-committed research JSON from `history-2026-10-10/source-packages/P2-AS-research-source.zip`:

   ```sh
   unzip -p docs/pk-research/p2/history-2026-10-10/source-packages/P2-AS-research-source.zip P2-AS/inputs/p2x-conditional-ensemble-results.json > /tmp/p2x-frozen.json
   unzip -p docs/pk-research/p2/history-2026-10-10/source-packages/P2-AS-research-source.zip P2-AS/inputs/legacy_source_snapshot.json > /tmp/legacy-snapshot.json
   ```

3. In a private environment with `artifact_tool` installed, run:

   ```sh
   python tools/pk-research/p2ar_real_visit_audit.py \
     --vnc54-xlsx /PRIVATE/path/Supplementary\ table.xlsx \
     --p2x-frozen-json /tmp/p2x-frozen.json \
     --legacy-snapshot-json /tmp/legacy-snapshot.json \
     --out-aggregate /tmp/p2ar-aggregate.json
   ```

   The program refuses unknown VNC54 file hashes, mismatched columns/roster, changed 15-candidate selection, bad sign/units or an existing output. The only output is an aggregate JSON without individual records.

4. Run the **separate** public synthetic unit tests with standard-library Python (no original medical files needed):

   ```sh
   python -m unittest discover -s tools/pk-research -p 'test_p2ar_real_visit_audit.py' -v
   ```

### Evidence limitations

This is not a prospective test, cannot establish personal pg/mL concentrations, and does not approve any product substitution or dose change. The original Yaish cohort had previously been seen during the M2 research; actual longitudinal dose histories and exact sampling time are absent. All q6 and longer overnight clocks and ±10/20% measurements are *hypothetical assumptions*, not observations or calibrated uncertainty distributions.
