package net.plainnotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit B1: every build carries the commit it was built from, so an installed APK can be traced to its source. */
class BuildProvenanceTest {
    private val sha = BuildConfig.SOURCE_SHA
    private val time = BuildConfig.SOURCE_COMMIT_TIME_UTC
    private val origin = BuildConfig.SOURCE_ORIGIN

    @Test fun shaIsAFullCommitOrExplicitlyUnknown() {
        assertTrue(sha, sha == "unknown" || Regex("[0-9a-f]{40}").matches(sha))
    }

    @Test fun originMatchesWhatIsKnown() {
        assertTrue(origin, origin in setOf("git", "declared-unverified", "unknown"))
        assertEquals(origin == "unknown", sha == "unknown")
        // Only a commit read from the project's own checkout can be compared with the tree, so only it can be dirty.
        if (origin != "git") assertFalse(BuildConfig.SOURCE_DIRTY)
    }

    @Test fun commitTimeIsUtcAndPresentOnlyWhenTheCommitIsKnown() {
        if (sha == "unknown") assertEquals("unknown", time)
        else assertTrue(time, time == "unknown" || Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z").matches(time))
        if (origin == "git") assertTrue(time, Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z").matches(time))
    }

    /** In GitHub Actions the checkout is a clean tree at GITHUB_SHA, so the compiled value must be exactly that commit. */
    @Test fun ciBuildMatchesTheCheckedOutCommit() {
        val expected = System.getenv("GITHUB_SHA") ?: return
        assertEquals(expected, sha)
        assertEquals("git", origin)
        assertFalse("CI checkout must not be reported as modified", BuildConfig.SOURCE_DIRTY)
    }
}
