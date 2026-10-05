# PK reference fixtures

`upstream/` holds unmodified files from
[TransmtfTeam/Transmtf-HRT-Tracker](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker)
at commit `8c9abdde` (MIT License, Copyright (c) 2025 Transmtf Team; see `upstream/LICENSE`).
They are only used to generate test fixtures and are not part of the app.

Regenerate the JSON fixtures consumed by `pk-engine` parity tests:

```sh
cd tools/pk-reference
npx -p typescript@5.8 tsc -p tsconfig.json   # or any local tsc ≥ 5
node build/generate.js
```

Output goes to `pk-engine/src/test/resources/reference/`. The Kotlin tests require a relative
error ≤ 1 % for every sampled point above 1 pg/mL (absolute ≤ 0.01 below it).
