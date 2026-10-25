# Actual roots navigation defect in the test

All three official artifacts are original FAIL, not retroactively PASS. API26 raw job hierarchy shows both the selected SYNTHETIC IMPORT directory and the full Open from root list still on screen at the closed-drawer timeout. Previous tests used the continued presence of a MED provider label as a drawer-state oracle and clicked the first ImageButton as a toggle.

AOSP API26 NavigationViewManager.onNavigationIconClicked calls mDrawer.setOpen(true), not toggle. Confirmed against immutable Android8 tag: https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/android-8.0.0_r1/src/com/android/documentsui/NavigationViewManager.java . DrawerController exposes the real drawer_roots pane: https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/android-8.0.0_r1/src/com/android/documentsui/DrawerController.java . No upstream implementation is copied.

The smallest shared test correction is fresh resource-ID/bounds state: main toolbar only opens roots, on-screen drawer_roots alone authorizes Back to close it, picker must remain active; document must be a fresh child of dir_list and not under drawer_roots before actual finger selection. REPORT_VIEW_IDS is enabled/restored only on test UiAutomation. Final cancellation closes any actual drawer before cancelling the picker. Original data/COMMIT/reopen assertions stay unchanged. Production import/detector/provider bytes are unchanged.

Local Android test source compilation PASS. Actual changed-source API26/29/35 acceptance remains PENDING until CI; no same-source blind rerun. No /dev/kvm in this workspace. Scope now only PR-A closeout, then Mona PR-B; Featherline implementation is deferred.
