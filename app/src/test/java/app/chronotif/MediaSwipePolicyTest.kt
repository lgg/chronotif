// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSwipePolicyTest {
    @Test
    fun pausedAndStoppedSessionsCanBeDismissedEvenIfNotificationIsOngoing() {
        for (state in listOf(
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_NONE,
            PlaybackState.STATE_ERROR,
        )) {
            assertTrue(MediaSwipePolicy.canDismiss(state, clearable = false))
            assertTrue(MediaSwipePolicy.canDismiss(state, clearable = true))
        }
    }

    @Test
    fun activeAndTransitioningSessionsCannotBeDismissed() {
        for (state in listOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        )) {
            assertFalse(MediaSwipePolicy.canDismiss(state, clearable = false))
            assertFalse(MediaSwipePolicy.canDismiss(state, clearable = true))
        }
    }

    @Test
    fun missingSessionKeepsExistingClearableNotificationFallback() {
        assertTrue(MediaSwipePolicy.canDismiss(state = null, clearable = true))
        assertFalse(MediaSwipePolicy.canDismiss(state = null, clearable = false))
        assertFalse(MediaSwipePolicy.isInactive(null))
    }
}
