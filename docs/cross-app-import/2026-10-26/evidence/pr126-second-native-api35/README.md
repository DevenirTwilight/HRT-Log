# PR #126 API 35 native failure evidence

- Source SHA: `222b4bd6100bcdd29c42ab46a26215d51c898c00`
- PR-event run: `40293330403`, job `121473313584`
- Artifact: `device-test-results` (`12637262511`)
- Result: FAIL, `AutomaticImportAndroidTest.realEntryAutomaticallyDetectsBothSourcesCommitsAndReopensHistory`
- System SAF opened and returned the synthetic Chrysalide file; earlier HRT Tracker, Trans Memo, and Mona portions passed in the same test.
- At Chrysalide confirmation wait timeout, ViewModel remained `ChrysalideReady` and persisted Chrysalide row count was `0`. No parser, SQL, or application exception appears. This is consistent with the target medication remaining unselected and the confirmation control remaining disabled; the old test tapped a text label that is reused elsewhere and did not assert selection.
- The follow-up test now targets the selectable mapping row (`hasClickAction` + text), asserts it is selected and that the confirm action is enabled before proceeding. This is a test interaction correction; its result is pending a new exact-SHA run.
- This failure is not counted as an application import pass. Do not use it as proof of successful Chrysalide commit.
