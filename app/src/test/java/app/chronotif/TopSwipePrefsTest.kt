// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TopSwipePrefsTest {
    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("chronotif", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.init(context)
    }

    @Test fun defaultsAreSafeAndOptIn() {
        assertFalse(Prefs.topSwipeEnabled)
        assertEquals(16, Prefs.topSwipeHeightPx)
    }

    @Test fun togglingDoesNotChangeHandleSetting() {
        Prefs.handleEnabled = true
        Prefs.topSwipeEnabled = true
        assertTrue(Prefs.topSwipeEnabled)
        assertTrue(Prefs.handleEnabled)
        Prefs.topSwipeEnabled = false
        assertFalse(Prefs.topSwipeEnabled)
        assertTrue(Prefs.handleEnabled)
    }

    @Test fun onlyClearDownwardDragsOpenThePanel() {
        assertTrue(TopSwipeTrigger.isDownwardSwipe(3f, 60f, 50f))
        assertFalse(TopSwipeTrigger.isDownwardSwipe(3f, 45f, 50f))
        assertFalse(TopSwipeTrigger.isDownwardSwipe(60f, 55f, 50f))
        assertFalse(TopSwipeTrigger.isDownwardSwipe(0f, -60f, 50f))
    }

    @Test fun heightIsClampedToPhysicalPixelRange() {
        Prefs.topSwipeHeightPx = 13
        assertEquals(13, Prefs.topSwipeHeightPx)
        Prefs.topSwipeHeightPx = 0
        assertEquals(1, Prefs.topSwipeHeightPx)
        Prefs.topSwipeHeightPx = 250
        assertEquals(100, Prefs.topSwipeHeightPx)
    }
}
