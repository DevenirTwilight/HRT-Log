package net.plainnotes.app.experimental

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import net.plainnotes.app.data.RecordEntity
import java.time.Instant

/**
 * Host for the approved "Experimental PK model" drawer entry (PD-2026-10-10-M2-ENTRY). It shows the same unified
 * comparison as the concentration page; without the official page embedded, Legacy appears as its relative curve.
 * Reads in-memory records and immutable rule snapshots only; writes nothing.
 */
@Composable fun ExperimentalPkScreen(
    records: List<RecordEntity>,
    ruleSnapshots: Map<Long, String>,
    pad: PaddingValues,
    onBackToConcentration: (() -> Unit)? = null,
    now: () -> Instant = Instant::now,
) = UnifiedConcentrationScreen(records, ruleSnapshots, pad, legacyContent = null, onBack = onBackToConcentration, now = now)
