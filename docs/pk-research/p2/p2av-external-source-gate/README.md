# P2-AV — audit of usable external PK validation evidence

This directory holds **actual auditable source metadata**, not individual participant blood concentrations. It is not a new blind human validation.

- [Full Chinese research report](P2-AV-REPORT.md)
- [12 publication-level source records, mapped to eight canonical cohorts](p2av-source-registry.json)
- [Recomputed source-entry eligibility result](p2av-gate-results.json)
- [Read-only source gate](../../../../tools/pk-research/p2av_external_source_gate.py)
- [14 synthetic/public metadata tests](../../../../tools/pk-research/test_p2av_external_source_gate.py)
- GitHub workflow: `.github/workflows/p2av-evidence-gate.yml`

Re-run from the repository root:

```sh
python tools/pk-research/p2av_external_source_gate.py \
  --registry docs/pk-research/p2/p2av-external-source-gate/p2av-source-registry.json \
  --out /tmp/p2av-recomputed.json
cmp docs/pk-research/p2/p2av-external-source-gate/p2av-gate-results.json /tmp/p2av-recomputed.json
python -m unittest discover -s tools/pk-research -p 'test_p2av_external_source_gate.py' -v
```

**Scientific boundary**: serial sampling plans and multi-month trough visits are not measured postdose serial E2 data. Repeated abstracts and theses using the same cohort are not independent human cohorts. One accessible E2 peak or digitized *development-exposed* group curve is not a locked, previously untouched human validation. Passing tests only verifies the registry classification algorithm; it does not certify the metadata or a clinical model against new people.

**Privacy boundary**: the audit uses publication metadata and registry links only; no private OSF VNC54/TCRUW spreadsheet, individual E2 record, quasi-identifier or clinical sample is committed.
