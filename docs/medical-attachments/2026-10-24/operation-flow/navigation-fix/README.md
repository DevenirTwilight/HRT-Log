# PR #119 navigation readiness correction

Source before correction: `2a47be6a41cfdf79771dbc5e9d17a5d4ec7b1895`.
Run [40109281652](https://github.com/DevenirTwilight/HRT-Log/actions/runs/40109281652), Android job 120917612206, failed at `AppShellBackNavigationTest.kt:112`.

The original HTML reports **two** matching `Appointment` text nodes, not a missing page or failed Back action. The test starts `model.sync()` (a Job, not a synchronous snapshot) and opens VISITS. While the actual appointment is not yet published, both the empty-state action in `VisitsScreen` and the list FAB in `AppShell` legitimately have this label. The exact-one assertion ran before the existing bounded wait for `Synthetic clinic`. No Back event had yet occurred. Those production navigation and refresh files are unchanged from merged baseline `f5f94238`.

Move the existing real-visit wait before the exact-one assertion. Do not change its 10-second bound, select the first arbitrary label, remove any assertion, sleep, or modify navigation. Retain: exact-one list FAB, detail hides it, Back returns to list without calendar/activity exit, second Back returns to calendar. This synchronizes the committed fixture's display, not an assumed amount of time.

The unchanged local class passed 17/17 before correction; this is **not** a deterministic local reproduction of the CI failure. The raw CI failure remains the observed race evidence; the same-source push run also passed. Neither green result erases the failure. `local-before.xml` and the exact failing HTML are retained. Post-correction results and final required CI must be assessed separately; compile is not native execution. See `original-artifact.json` for verified official artifact provenance.
