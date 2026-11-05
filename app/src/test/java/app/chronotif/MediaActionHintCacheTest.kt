// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.app.Notification
import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaActionHintCacheTest {
    @Test
    fun cachesOneResultPerNotificationAndInvalidatesOnRebind() {
        val first = Notification()
        val second = Notification()
        var evaluations = 0
        val cache = MediaActionHintCache { _, _ ->
            evaluations++
            evaluations % 2 == 1
        }

        assertTrue(cache.forNotification(first, null))
        assertTrue(cache.forNotification(first, null))
        assertEquals("Repeated MOVE events must not re-read the package resources", 1, evaluations)

        assertFalse(cache.forNotification(second, null))
        assertFalse(cache.forNotification(second, null))
        assertEquals(2, evaluations)

        // bind() invalidates even when the app reuses the same Notification object.
        cache.invalidate()
        assertTrue(cache.forNotification(second, null))
        assertEquals(3, evaluations)
    }

    @Test
    fun hintIsOnlyNeededForProtectedNotificationsWithUnknownState() {
        val protected = MediaSwipePolicy.canAttempt(
            null, clearable = false, hasDeleteIntent = true, hasPlayAction = true,
        )
        assertTrue(protected)
        assertFalse(MediaSwipePolicy.canAttempt(
            PlaybackState.STATE_PLAYING, clearable = false,
            hasDeleteIntent = true, hasPlayAction = true,
        ))
        assertTrue(MediaSwipePolicy.canAttempt(
            null, clearable = true, hasDeleteIntent = false, hasPlayAction = false,
        ))
        assertTrue(MediaSwipePolicy.canAttempt(
            PlaybackState.STATE_PAUSED, clearable = false,
            hasDeleteIntent = true, hasPlayAction = false,
        ))
    }
}
