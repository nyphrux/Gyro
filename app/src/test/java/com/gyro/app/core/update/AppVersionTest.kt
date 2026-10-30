package com.gyro.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun newerNumbersWin() {
        assertTrue(AppVersion.isNewer("1.0.1", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.1.0", "1.0.9"))
        assertTrue(AppVersion.isNewer("2.0", "1.9.9"))
        assertTrue(AppVersion.isNewer("1.10.0", "1.9.0")) // numeric, not alphabetical
    }

    @Test
    fun sameOrOlderIsNotAnUpdate() {
        assertFalse(AppVersion.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("0.9.0", "1.0.0"))
    }

    @Test
    fun tagPrefixesAreIgnored() {
        assertTrue(AppVersion.isNewer("v1.2.0", "1.0.0"))
        assertTrue(AppVersion.isNewer("Gyro 1.2.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("V1.0.0", "1.0.0"))
        assertEquals("1.2.0", AppVersion.clean("v1.2.0"))
        assertEquals("1.3.0-beta.2", AppVersion.clean("Gyro v1.3.0-beta.2"))
    }

    @Test
    fun preReleasesSortBeforeTheirRelease() {
        assertFalse(AppVersion.isNewer("1.0.0-beta.1", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.0.0", "1.0.0-beta.1"))
        assertTrue(AppVersion.isNewer("1.0.0-beta.2", "1.0.0-beta.1"))
        assertTrue(AppVersion.isNewer("1.0.0-beta.10", "1.0.0-beta.9"))
        assertTrue(AppVersion.isNewer("1.0.0-rc.1", "1.0.0-beta.3"))
        assertTrue(AppVersion.isNewer("1.1.0-beta.1", "1.0.0"))
    }

    @Test
    fun garbageIsNeverAnUpdate() {
        assertFalse(AppVersion.isNewer("latest", "1.0.0"))
        assertFalse(AppVersion.isNewer("", "1.0.0"))
    }

    @Test
    fun releaseNotesBecomePlainText() {
        val md = "## What's new\n- **Faster** compass\n* Fixed [bug](https://x.y)\n\n\n\nThanks `you`"
        assertEquals("What's new\n• Faster compass\n• Fixed bug\n\nThanks you", ReleaseNotes.toPlainText(md))
    }
}
