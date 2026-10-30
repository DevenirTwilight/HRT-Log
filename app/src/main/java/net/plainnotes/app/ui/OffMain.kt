package net.plainnotes.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Composition coroutines must continue on the main thread after background work (REQUIREMENTS §176, 2026-10-30).
 *
 * In the app, LaunchedEffect, produceState and rememberCoroutineScope run on the main dispatcher, so a suspend call that hops to
 * Default/IO comes back to main by itself. The Compose test environment runs them on an unconfined test dispatcher instead: the caller
 * resumes on the worker that finished the work, and the test's continuation interceptor then sends Compose's snapshot apply
 * notifications from that worker, so recomposition, layout and node changes run off the main thread. That was the intermittent CI
 * failure (CalledFromWrongThreadException, SnapshotStateObserver thread mismatch, SlotTable index errors). These helpers put an explicit
 * main dispatcher around the work, so the return lands on the main thread in both worlds; in the app the cost is one main dispatch.
 */

/** Runs [block] on [dispatcher] and continues the caller on the main thread. Use instead of a bare withContext in composable code. */
suspend fun <T> computeOffMain(dispatcher: CoroutineDispatcher = Dispatchers.Default, block: suspend CoroutineScope.() -> T): T =
    resumeOnMain { withContext(dispatcher, block) }

/** Runs [block] (which may call suspend functions that hop to IO internally) so that every return inside it lands on the main thread. */
suspend fun <T> resumeOnMain(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.Main.immediate, block)

/** A composition-bound scope whose coroutines always continue on the main thread; for controllers that do IO and then set UI state. */
@Composable fun rememberMainThreadScope(): CoroutineScope = rememberCoroutineScope { Dispatchers.Main }
