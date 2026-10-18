# Center saved-state follow-up — 2026-10-18

Baseline: `491a910fce1864bb169cc3b251905a1fbf2168c3`, default `claude/new-session-1959qb`. #82 merged with all six required checks passing on both source and default HEAD. #57/#58 remain independent drafts; no unapproved code copied. PR: https://github.com/DevenirTwilight/HRT-Log/pull/85.

| File | Scope |
|---|---|
| MedicationsScreen.kt | Save only the center-open Boolean with rememberSaveable |
| CenterStateRestorationTest.kt | Actual saved-state restore, explicit close remains closed, zero business callbacks |
| InAppNavigationLockAndroidTest.kt | Two additional native tests, all existing security assertions retained |
| docs/centers/state-restoration* and HANDOFF | RED/GREEN/CI receipts and handoff |

RED reproduced before production edits: restoration loses drug-center-window (line31), one failure; receipt state-restoration-red.json. This is an actual state round trip, not recomposition. The fix does not default to opening the center and does not store reference/product/health data in the saved flag.

Native tests use real MainActivity, real PIN/Keystore and actual Back key events. All twelve future cards must display then dismiss the explanation, retaining the parent center and unlocked session. Blocking ActivityMonitors fail the test if OPEN_DOCUMENT, GET_CONTENT or CREATE_DOCUMENT is attempted. The real existing reference browser must return to the drug center; closing that center returns to plans. Activity recreation retains the open center, but an actual leave with immediate auto-lock must show the lock and no drug-center window. These test synthetic CI state only, never real health records.

No layout/string change: reuse #82 four-language, 320/411dp, fonts/themes/contrast/landscape acceptance and 48 render artifacts with their original hashes; no new screenshots claimed. Manual physical-device/TalkBack: NOT_RUN. No APK signed/delivered or Release modified. Room12/business backup11, production ledger/forecast/PK, permissions and security implementation unchanged.

Current checkpoint: RED verified; GREEN and latest-HEAD six required CI pending. Native compilation is not execution. Final results and protected merge receipt will be recorded in this PR without a documentation-only CI loop.

Local GREEN: 23 tests PASS, zero failures/errors/skips: restoration1, real drug-center navigation1, PeriodStability5, AppShellBackNavigation16 (including disguise). Native Kotlin compilation PASS. Per-suite XML hashes: state-restoration-green.json. Production delta remains the single saveable Boolean. Await final-head native execution and required CI; PR85 will retain final machine receipt.
