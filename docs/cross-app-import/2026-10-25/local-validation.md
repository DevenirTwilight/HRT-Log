# PR-A local validation

2026-10-25; synthetic fixtures only. Base1ddcf431, implementation commit41b528b6 plus the following reviewed correction (recorded in this commit): cancel-safe compareAndSet of parsing progress; test qualifier order and precise line geometry; unknown-input regression; native persisted record comparison. No schema/protocol changes.

Final targeted invocation:

```
./gradlew -ProbolectricDir=/workspace/robolectric-jars :importer:test :app:testFullDebugUnitTest --tests '*AutomaticImportReaderTest' --tests '*AutomaticImportAccessibilityTest' --tests '*TransMemoImportFlowTest' --tests '*PeriodStabilityTest' :app:compileFullDebugAndroidTestKotlin :app:compileFullDebugAndroidTestJavaWithJavac --no-daemon --no-parallel --max-workers=2
```

BUILD SUCCESSFUL in1m52s. importer: HrtTracker4 + TransMemo8 + detector4 =16 PASS; app: reader4 + TransMemo flow8 + Period5 + accessibility8 =25 PASS. Every suite has0 failures/errors/skips. Native Kotlin and Java test source compilation PASS. This is local JVM/Robolectric/compilation evidence, not actual native execution.

Preserved findings: first detector tests compared existing non-data-class exports by object identity, fixed to independently compare actual IDs/time/dose/route/ester/extras and SQLite rows. First accessibility setup used invalid Android qualifier order, causing8 setup failures before UI execution. After fixing order, simplified Chinese aggregate hasVisualOverflow reported width overflow. Existing docs/ui-button-consistency.md already identifies CJK aggregate false positives; this new test now uses the established actual per-line width/bottom/full-character/ellipsis audit. All8 pass without changing production layout or removing existing assertions. None of these failures is reported as a production security pass.

Actual native MainActivity + DocumentsUI, API26/29/35 and exact final-source mandatory CI remain PENDING in PR123. Physical device and actual TalkBack PHYSICAL_NOT_RUN. No P4 research/low-memory rerun requested or dispatched. No claims of MED enablement or external security approval.

Final review correction: automatic-entry repeated import now waits for each returned Job to complete, so the prior Imported result cannot satisfy the second wait early. Actual two-execution TransMemo flow8 PASS, native Kotlin/Java compilation PASS (43s targeted invocation). The real MainActivity test and test DocumentsProvider reuse the existing ro.kernel.qemu attestation before changing app state; physical-device execution is prohibited. This changes verification, not import production behavior; final exact-SHA CI remains required.
