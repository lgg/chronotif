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
 * The notification listener cannot cancel ongoing / FLAG_NO_CLEAR entries, but
 * some media apps expose a deleteIntent for explicit user dismissal. In this
 * experimental fork build, try the app-provided intent on a user swipe while the
 * media session is provably inactive; never hide a notification locally.
 *
 * A successful PendingIntent.send() means only that the command was delivered,
 * not that Android removed the notification. NotifList restores the card if the
 * NotificationListenerService does not observe its removal.
 */
object MediaDismiss {
    /** Only dismiss after a user gesture and a confirmed inactive media state. */
    fun canSwipe(e: Entry, playback: PlaybackState?): Boolean =
        e.kind == Kind.MEDIA &&
            MediaSwipePolicy.canAttempt(playback?.state, e.clearable, e.n.deleteIntent != null)

    /**
     * Playback may have resumed while the swipe animated. Look up the current
     * notification and session again just before asking Android to remove it.
     * A successful return means cancellation was requested, not yet confirmed.
     * The UI restores the card if onNotificationRemoved never arrives.
     */
    fun dismiss(context: Context, entry: Entry): Boolean {
        val live = NotifStore.get(entry.key) ?: return false
        if (live.kind != Kind.MEDIA) return false

        val token = mediaToken(live.n) ?: return false
        // A notification key may be reused when a different media session arrives.
        if (mediaToken(entry.n) != token) return false
        val controller = runCatching {
            MediaController(context.applicationContext, token)
        }.getOrNull() ?: return false
        if (!MediaSwipePolicy.canAttempt(
                controller.playbackState?.state, live.clearable, live.n.deleteIntent != null
            )
        ) return false

        // Require a connected listener to observe real removal before the UI considers
        // the dismissal complete. This never edits the local notification store.
        val listener = ListenerService.instance ?: return false
        return if (live.clearable) {
            runCatching {
                listener.cancelNotification(live.key)
                true
            }.getOrDefault(false)
        } else {
            // The source app (not Chronotif) decides whether this explicit user
            // dismissal removes its ongoing media notification. No stop()/pause().
            val delete = live.n.deleteIntent ?: return false
            runCatching {
                delete.send()
                true
            }.getOrDefault(false)
        }
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
    fun canAttempt(state: Int?, clearable: Boolean, hasDeleteIntent: Boolean): Boolean =
        isInactive(state) && (clearable || hasDeleteIntent)

    fun isInactive(state: Int?): Boolean = when (state) {
        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_ERROR -> true
        else -> false
    }
}
