// SPDX-License-Identifier: GPL-3.0-or-later

package app.chronotif

import android.app.Notification
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.chronotif.core.Kind

/**
 * The system shade can remove media resumption cards that aren't ordinary clearable
 * notifications. NotificationListenerService has no equivalent public SystemUI API.
 *
 * Prefer a real notification cancellation when possible. Otherwise hide only Chronotif's
 * representation of a confirmed inactive media session, and observe the session while
 * hidden so starting playback restores the card even without a notification repost.
 *
 * This intentionally does not send stop(), release(), or any transport command to a
 * third-party player, and it never tries to dismiss actively playing media.
 */
object MediaDismiss {
    private data class Hidden(
        val token: MediaSession.Token,
        val controller: MediaController,
        val callback: MediaController.Callback,
    )

    private val hidden = HashMap<String, Hidden>()

    /** The view uses this policy before accepting a horizontal swipe. */
    fun canSwipe(e: Entry, playback: PlaybackState?): Boolean =
        e.kind == Kind.MEDIA && MediaSwipePolicy.canDismiss(playback?.state, e.clearable)

    /**
     * Called on the main thread after the swipe animation.
     * Returns false if the session became active (or disappeared) during the gesture.
     */
    fun dismiss(context: Context, entry: Entry): Boolean {
        val live = NotifStore.get(entry.key) ?: return false
        if (live.kind != Kind.MEDIA) return false

        val token = mediaToken(live.n)
        val controller = token?.let { runCatching { MediaController(context, it) }.getOrNull() }
        val state = controller?.playbackState
        if (!canSwipe(live, state)) return false

        if (live.clearable) {
            // This actually removes the notification from Android and consequently Chronotif.
            val listener = ListenerService.instance ?: return false
            return runCatching { listener.cancelNotification(live.key); true }.getOrDefault(false)
        }

        // Non-clearable, typically an ongoing media notification: hide locally instead of
        // calling stop() or attempting to cancel a notification Android cannot remove.
        if (token == null || controller == null || !MediaSwipePolicy.isInactive(state?.state)) return false
        forget(live.key)

        val key = live.key
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                // Null means state is unknown; err toward showing, never hiding active playback.
                if (!MediaSwipePolicy.isInactive(state?.state)) forget(key)
            }

            override fun onSessionDestroyed() = forget(key)
        }

        hidden[key] = Hidden(token, controller, callback)
        val registered = runCatching {
            controller.registerCallback(callback, Handler(Looper.getMainLooper()))
            true
        }.getOrDefault(false)
        if (!registered) {
            forget(key)
            return false
        }

        // Close the race between the swipe decision and callback registration.
        if (!MediaSwipePolicy.isInactive(controller.playbackState?.state) ||
            mediaToken(NotifStore.get(key)?.n) != token
        ) {
            forget(key)
            return false
        }

        NotifStore.changed()
        return true
    }

    fun isHidden(key: String): Boolean = hidden.containsKey(key)

    /** A repost from an old session stays hidden; new sessions and resumed ones reappear. */
    fun onPosted(e: Entry) {
        val record = hidden[e.key] ?: return
        if (mediaToken(e.n) != record.token ||
            !MediaSwipePolicy.isInactive(record.controller.playbackState?.state)
        ) forget(e.key)
    }

    fun onRemoved(key: String) = forget(key)

    /** Drop callback registrations on listener disconnect/reconnect; nothing survives a restart. */
    fun clear() {
        hidden.keys.toList().forEach(::forget)
    }

    private fun forget(key: String) {
        val old = hidden.remove(key) ?: return
        runCatching { old.controller.unregisterCallback(old.callback) }
        NotifStore.changed()
    }

    private fun mediaToken(notification: Notification?): MediaSession.Token? {
        notification ?: return null
        return if (Build.VERSION.SDK_INT >= 33) {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }
    }
}

/** Android playback states for which dismissing a stale card cannot interrupt playback. */
internal object MediaSwipePolicy {
    fun isInactive(state: Int?): Boolean = when (state) {
        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_NONE,
        PlaybackState.STATE_ERROR -> true
        else -> false
    }

    fun canDismiss(state: Int?, clearable: Boolean): Boolean =
        if (state == null) clearable else isInactive(state)
}
