# P2-AY frozen anchor-ratio observability (research only)

- [Full Chinese scientific report](P2-AY-REPORT.md)
- [Integer-scaled deterministic checkpoint](p2ay-observability.json)
- [Source computation](../../../../tools/pk-research/p2ay_anchor_ratio_observability.py)
- [Synthetic math / provenance tests](../../../../tools/pk-research/test_p2ay_anchor_ratio_observability.py)

Reproduce from the repository root:

```bash
python -m unittest discover -s tools/pk-research -p 'test_p2ay_anchor_ratio_observability.py' -v
python tools/pk-research/p2ay_anchor_ratio_observability.py \
  --frozen-aw docs/pk-research/p2/p2aw-identifiability-design/p2aw-geometry.json \
  --output /tmp/p2ay-recreated.json
cmp docs/pk-research/p2/p2ay-anchor-ratio/p2ay-observability.json /tmp/p2ay-recreated.json
```

The CLI refuses overwriting and rejects even a changed byte in the already committed P2-AW result. Its published output uses time strings and integer micro-ratios / milli-pg/mL numbers to avoid floating-point serializer drift. No private XLSX, identifiable person-level series, publication scans, APK, HRT dose or production E2_SL code is used or changed. **2× hypothetical analytical SD is NOT human PK accuracy, power, real LLOQ, or a dosing instruction.** The tested anchor-ratio depends on a stable baseline, accurate clocks, positive substantial 1h increment, an identical assay and independent equal-SD sampling. Correlation from repeated shared 0h/1h anchors matters. P2-C stays HOLD.
