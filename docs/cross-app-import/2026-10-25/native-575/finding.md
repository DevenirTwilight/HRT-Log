# Exact source 57567c91 native checkpoint

PR run 40204515855: API26/29/35 and JVM/android/Science jobs SUCCESS. Independent push run 40204512843: API29/35 SUCCESS, API26 FAIL. This failure remains an acceptance blocker, not overwritten by the passing PR run. No unchanged-source rerun requested.

Failed artifact12603292124, SHA256 `56f723c7b4a30784edbff37e83e1bd1f54472c22b7fa2dfd738205642847720b`; downloaded hash and every ZIP CRC verified. Original XML/logcat included. File selection reached HtImportWizard (JIT log); Compose-Espresso remained busy from15:00:11–36, followed by Activity destruction and SlotWriter.removeGroup ArrayIndexOutOfBoundsException. Root test exception may have been masked by teardown. Not enough evidence to call this an Android infrastructure defect or a production regression.

Targeted test correction: await real host RESUMED and DocumentsUI disappearance after the result, independently of model preview readiness; settle Compose before deliberate Activity close. Record static synthetic phase names and original thrown test error before @After. No production behavior, test assertions, timeouts or dependency versions changed. Native revalidation required at new source.
