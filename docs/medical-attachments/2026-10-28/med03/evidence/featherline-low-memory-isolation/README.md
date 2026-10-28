# Featherline low-memory job isolation (PR #145, 2026-10-28)

Job: `featherline-api26-512m` of workflow `med02-library-low-memory` (NOT a required check; required checks are jvm, android,
device-tests, attachment-poc-compatibility 26/29, science-change-gate). Run 40714236767 on source head 0ab6f0c1.

Observed: `AutomaticImportAndroidTest.featherlineImportRunsAsIsolatedLowMemoryFlow` — `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`
The last memory sample phase is `before-wrong-password-kdf`. There is no Java `FATAL EXCEPTION`; the app process (pid 3852) has no
stack trace. The guest kernel log shows the *app process itself* invoking the kernel OOM killer
(`.plainnotes.app invoked oom-killer`, gfp_mask=0x2084d0) in a 512 MiB x86 guest, after killing other processes
(`kernel-oom-excerpt.txt`), then `Process net.plainnotes.app has died: fore TOP` (`logcat-excerpt.txt`). That is native memory
exhaustion by the Argon2id wrong-password KDF phase of the Featherline import flow, not a MED-03 code path.

Baseline comparison: this PR changes no Featherline/import source (`git diff --name-only` has no `Featherline*`/`imports` main or
test file). The same job concluded `failure` on four earlier MED-02 branch heads before any MED-03 change (runs 40519149572, 40510936297,
40508562874, 40505846101; their individual crash logs were not re-inspected here) and `success` on others (40516380581,
40514382211): the job is intermittent on code that predates MED-03, consistent with a memory-pressure-sensitive 512 MiB guest. The sibling `library-api26-512m` job (the MED medical library,
the code this PR changes) PASSED on all three MED-03 heads and is recorded as PASS.

Decision: isolated, not fixed in this PR; no performance research is opened here. It is not claimed as a pass.
