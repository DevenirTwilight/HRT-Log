package net.plainnotes.app.data

/** The row selected before confirmation has already been changed; never follow its replacement. */
class SkipUndoStale : IllegalStateException("Skipped record changed before confirmation")
/** Original schedule evidence is missing or inconsistent; do not invent a historical slot. */
class SkipUndoUnavailable : IllegalStateException("Original skipped slot cannot be safely located")
/** Restoring open demand in another inventory unit has no defined safe conversion. */
class SkipUndoUnitConflict : IllegalStateException("Historical and current inventory units differ")
