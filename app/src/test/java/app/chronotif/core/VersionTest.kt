// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun comparesNumericallyNotLexically() {
        assertTrue(Version.isNewer("v0.10.0", "0.9.0"))
        assertTrue(Version.isNewer("v1.0.0", "0.99.99"))
        assertFalse(Version.isNewer("v0.1.0", "0.1.0"))
        assertFalse(Version.isNewer("v0.0.9", "0.1.0"))
    }

    @Test
    fun missingPartsCountAsZero() {
        assertFalse(Version.isNewer("v1.0", "1.0.0"))
        assertTrue(Version.isNewer("v1.0.1", "1.0"))
    }

    @Test
    fun releaseBeatsItsPreRelease() {
        assertTrue(Version.isNewer("v1.0.0", "1.0.0-rc1"))
        assertFalse(Version.isNewer("v1.0.0-rc1", "1.0.0"))
    }

    @Test
    fun garbageIsNeverNewer() {
        assertNull(Version.parse("nightly"))
        assertFalse(Version.isNewer("nightly", "0.1.0"))
        assertFalse(Version.isNewer("v9.9.9", "dev"))
    }

    @Test
    fun readsSha256sumLine() {
        val h = "a".repeat(64)
        assertEquals(h, Version.sha256Of("$h  chronotif-v0.2.0.apk\n"))
        assertEquals(h, Version.sha256Of(h.uppercase()))
        assertNull(Version.sha256Of("abc  file.apk"))
    }
}
