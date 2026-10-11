# P2-AX — strict external-source contract (research only)

**2026-10-11. Not real external validation.** This phase adds 9 documented public-source examples, a machine-readable fail-closed gate, a reproducible result, and synthetic-only scoring tests. It does not alter HRT Log's shipped models, doses, APK or database.

- [Chinese full report](P2-AX-REPORT.md)
- [Source metadata registry](p2ax-source-registry.json)
- [Gate results](p2ax-gate-results.json)
- [Python checker](../../../../tools/pk-research/p2ax_evidence_contract.py)
- [Synthetic/public-metadata tests](../../../../tools/pk-research/test_p2ax_evidence_contract.py)

Run from repository root:

    python -m unittest discover -s tools/pk-research -p 'test_p2ax_evidence_contract.py' -v
    python tools/pk-research/p2ax_evidence_contract.py \
      --registry docs/pk-research/p2/p2ax-evidence-contract/p2ax-source-registry.json \
      --output /tmp/p2ax-result-fresh.json
    cmp /tmp/p2ax-result-fresh.json docs/pk-research/p2/p2ax-evidence-contract/p2ax-gate-results.json

P2-AX is not a replacement for P2-AV's separate 12-record / 8-cohort literature catalogue. Data availability flags mean available **to this specific audit with verifiable rights and necessary metadata**, not that the researchers never collected other observations. All numerical examples are synthetic; neither the diagnostic fit nor the fixed-prediction numerical function is a validated clinical PK scorer. No personal records, copyright scans, secret data or OSF participant worksheets are committed. P2-C remains HOLD.
