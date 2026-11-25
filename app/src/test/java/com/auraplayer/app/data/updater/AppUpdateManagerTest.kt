package com.auraplayer.app.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun `compareSemanticVersions compares major minor patch accurately`() {
        assertTrue(AppUpdateManager.compareSemanticVersions("v1.3.0", "1.2.0") > 0)
        assertTrue(AppUpdateManager.compareSemanticVersions("1.10.0", "1.2.9") > 0)
        assertTrue(AppUpdateManager.compareSemanticVersions("2.0.0", "1.99.99") > 0)
        assertEquals(0, AppUpdateManager.compareSemanticVersions("v1.3.0", "1.3.0"))
        assertTrue(AppUpdateManager.compareSemanticVersions("1.2.0", "1.3.0") < 0)
    }

    @Test
    fun `ChangelogParser parses multi-release CHANGELOG markdown`() {
        val sample = """
            # Changelog

            ## [1.3.0] - 2026-11-25

            ### Added
            - **Fire Ember** particle visualizer with `5` directions.
            - In-app updater via GitHub Releases.

            ### Fixed
            - Tightened beat detection and AudioTrack sync.

            ## [1.2.0] - 2026-11-18

            ### Added
            - Multi-resolution FFT analyzer.
        """.trimIndent()

        val releases = ChangelogParser.parseChangelog(sample)
        assertEquals(2, releases.size)
        assertEquals("1.3.0", releases[0].version)
        assertEquals("2026-11-25", releases[0].date)
        assertEquals(2, releases[0].sections.size)
        assertEquals("Added", releases[0].sections[0].title)
        assertEquals(
            listOf(
                "Fire Ember particle visualizer with 5 directions.",
                "In-app updater via GitHub Releases."
            ),
            releases[0].sections[0].items
        )
        assertEquals("Fixed", releases[0].sections[1].title)
        assertEquals("1.2.0", releases[1].version)
    }

    @Test
    fun `ChangelogParser wraps GitHub release notes and skips Release Artifact metadata table`() {
        val githubBody = """
            ## Aura Player v1.4.0

            ### Added
            - New spectrum preset.

            ---

            ### Release Artifact
            | Field | Value |
            | --- | --- |
            | **APK** | `aura-player-v1.4.0-arm64-v8a.apk` |
        """.trimIndent()

        val release = ChangelogParser.parseReleaseNotes(
            version = "v1.4.0",
            body = githubBody,
            publishedDate = "2026-12-01T10:00:00Z"
        )

        assertEquals("1.4.0", release.version)
        assertEquals("2026-12-01", release.date)
        assertEquals(1, release.sections.size)
        assertEquals("Added", release.sections[0].title)
        assertEquals(listOf("New spectrum preset."), release.sections[0].items)
    }
}
