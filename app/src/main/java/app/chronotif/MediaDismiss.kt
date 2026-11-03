// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.app.Notification
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import app.chronotif.core.Kind

/**
 * Dismiss inactive media through Android's real notification listener.
 *
 * Unlike SystemUI's media carousel, a third-party notification listener cannot
 * dismiss an ongoing / FLAG_NO_CLEAR media entry. Never hide only Chronotif's
 * copy: an undisposable media player should remain visible.
 */
object MediaDismiss {
    /** The drag must be rejected when removal is impossible or playback is active. */
    fun canSwipe(e: Entry, playback: PlaybackState?): Boolean =
        e.kind == Kind.MEDIA && e.clearable && MediaSwipePolicy.isInactive(playback?.state)

    /**
     * Playback may have resumed while the swipe animated. Look up the current
     * notification and session again just before asking Android to remove it.
     * A successful return means cancellation was requested, not yet confirmed.
     * The UI restores the card if onNotificationRemoved never arrives.
     */
    fun dismiss(context: Context, entry: Entry): Boolean {
        val live = NotifStore.get(entry.key) ?: return false
        if (live.kind != Kind.MEDIA || !live.clearable) return false

        val token = mediaToken(live.n) ?: return false
        val controller = runCatching {
            MediaController(context.applicationContext, token)
        }.getOrNull() ?: return false
        if (!MediaSwipePolicy.isInactive(controller.playbackState?.state)) return false

        val listener = ListenerService.instance ?: return false
        return runCatching {
            listener.cancelNotification(live.key)
            true
        }.getOrDefault(false)
    }

    private fun mediaToken(notification: Notification): MediaSession.Token? =
        if (Build.VERSION.SDK_INT >= 33) {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }
}

/** Unknown or transitional states are never considered safe to dismiss. */
internal object MediaSwipePolicy {
    fun isInactive(state: Int?): Boolean = when (state) {
        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_ERROR -> true
        else -> false
    }
}
