// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSwipePolicyTest {
    @Test
    fun deleteIntentIsOnlyAnAlternativeForKnownInactivePlayback() {
        assertTrue(MediaSwipePolicy.canAttempt(PlaybackState.STATE_PAUSED, false, true))
        assertTrue(MediaSwipePolicy.canAttempt(PlaybackState.STATE_STOPPED, false, true))
        assertTrue(MediaSwipePolicy.canAttempt(PlaybackState.STATE_PAUSED, true, false))
        assertFalse(MediaSwipePolicy.canAttempt(PlaybackState.STATE_PAUSED, false, false))
        assertFalse(MediaSwipePolicy.canAttempt(PlaybackState.STATE_PLAYING, false, true))
        assertFalse(MediaSwipePolicy.canAttempt(PlaybackState.STATE_BUFFERING, false, true))
        assertFalse(MediaSwipePolicy.canAttempt(null, false, true))
        assertTrue(MediaSwipePolicy.canAttempt(null, false, true, hasPlayAction = true))
        assertTrue(MediaSwipePolicy.canAttempt(PlaybackState.STATE_NONE, false, true, hasPlayAction = true))
        assertFalse(MediaSwipePolicy.canAttempt(PlaybackState.STATE_PLAYING, false, true, hasPlayAction = true))
        assertFalse(MediaSwipePolicy.canAttempt(PlaybackState.STATE_BUFFERING, false, true, hasPlayAction = true))
        assertFalse(MediaSwipePolicy.canAttempt(null, false, false, hasPlayAction = true))
    }

    @Test
    fun onlyKnownInactiveStatesAreDismissible() {
        for (state in listOf(
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_ERROR,
        )) assertTrue(MediaSwipePolicy.isInactive(state))
    }

    @Test
    fun playingAndTransitioningStatesStayProtected() {
        for (state in listOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        )) assertFalse(MediaSwipePolicy.isInactive(state))
    }

    @Test
    fun missingAndUnknownStatesAreNotAssumedInactive() {
        assertFalse(MediaSwipePolicy.isInactive(null))
        assertFalse(MediaSwipePolicy.isInactive(PlaybackState.STATE_NONE))
    }
}
