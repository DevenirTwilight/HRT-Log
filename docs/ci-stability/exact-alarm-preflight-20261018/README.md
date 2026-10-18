# API35 exact-alarm test preparation

Scope: TEST_ONLY. Baseline default417e383ecba8e8482f3cf6c043c62b7be8ee7cbf. The only code file proposed is `app/src/androidTestFull/java/net/plainnotes/app/shell/ReminderTestDeviceTest.kt`. No production reminder, UI, ViewModel, strings, database, backup, stock, PK, manifest, CI, Gradle, version or signing changes.

## Observed failure

Main run39122664759/job117661246072 failed `scheduledTestTravelsTheRealAlarmChain` on API35 at source line52: bare `assertTrue(status.exact)`. This occurs before the90-second delivery wait. Main1ab also failed that test; the same417 code subsequently passed device checks on the Beta documentation branch. These are separate observations; later green does not erase either failure or establish its cause.

The fixture currently closes `executeShellCommand`'s descriptor immediately. It does not read to command completion or verify that AlarmManager actually observes the requested permission. A permission-command completion race is a HYPOTHESIS, not a confirmed production reminder defect.

## Integration plan before editing

1. Work in an independent worktree/PR, based only on merged main. PR70 is merged; no open reminder PR was found, and the original shared worktree was clean. Do not modify P4 or another developer's worktree.
2. Drain the existing AppOps command's pipe to EOF and close it, then require `canScheduleExactAlarms()` on API31+ before starting any mode/delivery assertion. Fail explicitly if preparation did not grant it. Do not poll, extend timeouts, retry failing tests, skip cases, or change any existing assertion.
3. Keep all three test methods,90-second delivery deadline,5/8-second alarm inputs, notification outcome checks and actual notification checks byte-identical. No production permission or notification behavior is changed.
4. Validate source isolation and native compilation through the unchanged six required CI checks, including actual API26/29/35 instrumentation. Preserve both failed main runs. A green build validates the prepared test fixture; it does not alone prove race causality, vendor-ROM delivery or physical-device acceptance.
5. Owner's current main AGENTS/CLAUDE and REQUIREMENTS106 permit protected auto-merge of task PRs after current required checks/no conflicts/no unresolved reviews, excluding draft/research/external-review and release actions. This general instruction supersedes the earlier ANSM-only auto-merge limitation for this ordinary test-preparation fix. No admin bypass or signing/release is authorized here.

## Current evidence status

- Main417 API35: FAIL before delivery wait, `status.exact=false`.
- Proposed fixture source/compilation: NOT_IMPLEMENTED at this plan checkpoint.
- Actual fixed API26/29/35 and final-head six CI: NOT_RUN at this plan checkpoint.
- Race causality, physical/vendor-ROM/Doze/reboot: UNVERIFIED / NOT_RUN.
