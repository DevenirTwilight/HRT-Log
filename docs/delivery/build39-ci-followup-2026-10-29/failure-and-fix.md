# Build39 receipt CI follow-up

Original run40936737557/job123669608636, artifact12927745737, source7710d670. Failed `MedicalHomeConfirmFlowTest.confirmThenUndoInDetail` line110:

```
AssertionError: Failed to inject touch input.
Expected exactly 1 node but could not find medical-correction-confirm
```

Original report path in artifact: app/build/reports/tests/testFullDebugUnitTest/net.plainnotes.app.MedicalHomeConfirmFlowTest/confirmThenUndoInDetail.html
Report SHA256: dc65129bc337d4eb30e4b036f885a51ae2c297c0260024ebfa3cb668a468c41a

The test now scrolls the real retract button into view and observes its confirmation Dialog before injecting the confirm click. Both clicks, unchanged visit timestamp, and actual completed_utc clearing remain asserted; no sleep, retry-click or timeout increase. Targeted local1 test PASS/0 skips, AndroidTest Kotlin compilation PASS, BUILD SUCCESSFUL5m48s. This is Robolectric/compiler evidence, not native or physical acceptance. Final commit full CI remains pending. Build39 delivery bytes, certificate and ledger unchanged.
